from tests.conftest import STAFF, SYSADMIN, auth
from tests.test_appointments import (
    DOCTOR,
    DOCTOR_ADMIN,
    book,
    first_doctor,
    free_slot,
    patient_headers,
)

API = "/api/v1"


def attended_visit(client, mobile="09121234567", index=0):
    d = first_doctor(client)
    h = patient_headers(client, mobile)
    appt = book(client, h, free_slot(client, d["id"], index)["id"]).json()
    client.post(
        f"{API}/doctor/me/appointments/{appt['id']}/status",
        json={"status": "attended"},
        headers=auth(client, DOCTOR),
    )
    return d, h, appt


def test_only_attended_and_only_once(client):
    d = first_doctor(client)
    h = patient_headers(client)
    appt = book(client, h, free_slot(client, d["id"])["id"]).json()
    r = client.post(f"{API}/patient/appointments/{appt['id']}/review", json={"stars": 5}, headers=h)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "not_attended"
    client.post(
        f"{API}/doctor/me/appointments/{appt['id']}/status",
        json={"status": "attended"},
        headers=auth(client, DOCTOR),
    )
    mine = client.get(f"{API}/patient/appointments", headers=h).json()
    assert mine[0]["can_review"] is True
    r = client.post(f"{API}/patient/appointments/{appt['id']}/review", json={"stars": 4}, headers=h)
    assert r.status_code == 201 and r.json()["status"] == "approved"
    again = client.post(f"{API}/patient/appointments/{appt['id']}/review", json={"stars": 1}, headers=h)
    assert again.status_code == 409 and again.json()["detail"]["code"] == "already_reviewed"
    assert (
        client.post(
            f"{API}/patient/appointments/{appt['id']}/review", json={"stars": 9}, headers=h
        ).status_code
        == 422
    )


def test_other_patient_cannot_review(client):
    d, h, appt = attended_visit(client)
    other = patient_headers(client, "09129999999")
    r = client.post(f"{API}/patient/appointments/{appt['id']}/review", json={"stars": 5}, headers=other)
    assert r.status_code == 404


def test_stars_count_at_once_comment_waits_for_approval(client):
    d, h, appt = attended_visit(client)
    r = client.post(
        f"{API}/patient/appointments/{appt['id']}/review",
        json={"stars": 4, "comment": "خیلی خوب بود"},
        headers=h,
    )
    assert r.json()["status"] == "pending"
    doc = first_doctor(client)
    assert doc["rating_count"] == 1 and doc["rating_average"] == 4
    public = client.get(f"{API}/public/doctors/{d['id']}/reviews").json()
    assert public["rating"]["count"] == 1 and public["items"] == []

    admin = auth(client, DOCTOR_ADMIN)
    pending = client.get(f"{API}/manage/reviews", params={"status": "pending"}, headers=admin).json()
    assert [p["comment"] for p in pending] == ["خیلی خوب بود"]
    rid = pending[0]["id"]
    # پزشک تا قبل از تایید چیزی نمی‌بیند و پاسخ نمی‌دهد
    dh = auth(client, DOCTOR)
    assert client.get(f"{API}/doctor/me/reviews", headers=dh).json() == []
    assert client.post(
        f"{API}/doctor/me/reviews/{rid}/reply", json={"text": "ممنون"}, headers=dh
    ).status_code in (
        404,
        409,
    )

    assert (
        client.post(
            f"{API}/manage/reviews/{rid}/moderate", json={"status": "approved"}, headers=admin
        ).status_code
        == 200
    )
    public = client.get(f"{API}/public/doctors/{d['id']}/reviews").json()
    assert [i["comment"] for i in public["items"]] == ["خیلی خوب بود"]
    assert "patient_name" not in public["items"][0] and "patient_id" not in public["items"][0]

    mine = client.get(f"{API}/doctor/me/reviews", headers=dh).json()
    assert len(mine) == 1
    r = client.post(f"{API}/doctor/me/reviews/{rid}/reply", json={"text": "ممنون از شما"}, headers=dh)
    assert r.status_code == 200 and r.json()["doctor_reply"] == "ممنون از شما"
    r = client.post(
        f"{API}/manage/reviews/{rid}/admin-reply", json={"text": "سپاس"}, headers=auth(client, SYSADMIN)
    )
    assert r.status_code == 200 and r.json()["admin_reply"] == "سپاس"
    public = client.get(f"{API}/public/doctors/{d['id']}/reviews").json()
    assert public["items"][0]["doctor_reply"] == "ممنون از شما"
    assert public["items"][0]["admin_reply"] == "سپاس"


def test_rejected_comment_stays_hidden(client):
    d, h, appt = attended_visit(client)
    client.post(
        f"{API}/patient/appointments/{appt['id']}/review",
        json={"stars": 2, "comment": "نامناسب"},
        headers=h,
    )
    admin = auth(client, DOCTOR_ADMIN)
    rid = client.get(f"{API}/manage/reviews", headers=admin).json()[0]["id"]
    client.post(f"{API}/manage/reviews/{rid}/moderate", json={"status": "rejected"}, headers=admin)
    assert client.get(f"{API}/public/doctors/{d['id']}/reviews").json()["items"] == []


def test_permissions(client):
    d, h, appt = attended_visit(client)
    client.post(
        f"{API}/patient/appointments/{appt['id']}/review", json={"stars": 3, "comment": "متن"}, headers=h
    )
    staff = auth(client, STAFF)
    assert client.get(f"{API}/manage/reviews", headers=staff).status_code == 403
    rid = client.get(f"{API}/manage/reviews", headers=auth(client, DOCTOR_ADMIN)).json()[0]["id"]
    assert (
        client.post(
            f"{API}/manage/reviews/{rid}/moderate", json={"status": "approved"}, headers=staff
        ).status_code
        == 403
    )
    # مدیر سیستم پاسخ می‌دهد ولی تایید و رد نمی‌کند
    sysadmin = auth(client, SYSADMIN)
    assert (
        client.post(
            f"{API}/manage/reviews/{rid}/moderate", json={"status": "approved"}, headers=sysadmin
        ).status_code
        == 403
    )
    assert client.get(f"{API}/doctor/me/reviews", headers=staff).status_code == 404
