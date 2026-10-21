from datetime import UTC, date, datetime, timedelta

from sqlalchemy import select, update

from app.models import Appointment, AppointmentSlot, Doctor, Notification, User
from app.services import appointments as svc
from app.services.schedule_logic import LOCAL_TZ
from tests.conftest import STAFF, SYSADMIN, auth
from tests.test_patient_auth import sign_in

API = "/api/v1"
DOCTOR_ADMIN, SECRETARY, DOCTOR = "1012", "1013", "1014"
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 64


def patient_headers(client, mobile="09121234567"):
    return {"Authorization": f"Bearer {sign_in(client, mobile)['access_token']}"}


def first_doctor(client):
    doctors = client.get(f"{API}/public/doctors").json()
    return next(d for d in doctors if d["full_name"] == "دکتر نمونه یکم")


def free_slot(client, doctor_id, index=0):
    return client.get(f"{API}/public/doctors/{doctor_id}/slots").json()[index]


def book(client, h, slot_id, name="بیمار آزمایشی"):
    return client.post(
        f"{API}/patient/appointments", json={"slot_id": slot_id, "patient_name": name}, headers=h
    )


def test_public_specialties_and_doctors(client):
    specs = client.get(f"{API}/public/specialties").json()
    heart = next(s for s in specs if s["name"] == "قلب و عروق")
    assert heart["icon"] == "heart" and heart["doctor_count"] == 1
    doctors = client.get(f"{API}/public/doctors", params={"specialty_id": heart["id"]}).json()
    assert [d["full_name"] for d in doctors] == ["دکتر نمونه یکم"]
    assert doctors[0]["next_free_at"] is not None
    featured = client.get(f"{API}/public/doctors", params={"featured": True}).json()
    assert len(featured) == 2
    found = client.get(f"{API}/public/doctors", params={"q": "ارتوپدی"}).json()
    assert [d["full_name"] for d in found] == ["دکتر نمونه دوم"]


def test_patient_books_and_slot_fills(client, db):
    doctor = first_doctor(client)
    slot = free_slot(client, doctor["id"])
    h = patient_headers(client)
    r = book(client, h, slot["id"])
    assert r.status_code == 201, r.text
    appt = r.json()
    assert appt["status"] == "booked" and appt["can_cancel"] and len(appt["tracking_code"]) == 8
    assert slot["id"] not in [
        s["id"] for s in client.get(f"{API}/public/doctors/{doctor['id']}/slots").json()
    ]

    # پزشک و منشی اعلان می‌گیرند؛ نام بیمار در اعلان نیست.
    notes = db.scalars(select(Notification).where(Notification.kind == "appointment")).all()
    assert len(notes) == 2 and all("بیمار آزمایشی" not in (n.title + (n.body or "")) for n in notes)

    other = patient_headers(client, "09121234568")
    assert book(client, other, slot["id"]).json()["detail"]["code"] == "slot_full"
    # یک نوبت فعال برای هر پزشک
    next_slot = free_slot(client, doctor["id"])
    assert book(client, h, next_slot["id"]).json()["detail"]["code"] == "already_booked"

    mine = client.get(f"{API}/patient/appointments", headers=h).json()
    assert [a["tracking_code"] for a in mine] == [appt["tracking_code"]]


def test_patient_cancel_window(client, db):
    doctor = first_doctor(client)
    h = patient_headers(client)
    appt = book(client, h, free_slot(client, doctor["id"])["id"]).json()
    r = client.post(f"{API}/patient/appointments/{appt['id']}/cancel", headers=h)
    assert r.status_code == 200 and r.json()["status"] == "cancelled"
    # بعد از لغو، وقت دوباره آزاد است
    assert free_slot(client, doctor["id"])["id"] == appt_slot(db, appt["id"])

    appt2 = book(client, h, free_slot(client, doctor["id"])["id"]).json()
    soon = datetime.now(UTC) + timedelta(minutes=30)
    db.execute(
        update(AppointmentSlot).where(AppointmentSlot.id == appt_slot(db, appt2["id"])).values(starts_at=soon)
    )
    db.commit()
    r = client.post(f"{API}/patient/appointments/{appt2['id']}/cancel", headers=h)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "cannot_cancel"


def appt_slot(db, appointment_id):
    db.expire_all()
    return str(db.get(Appointment, appointment_id).slot_id)


def test_other_patient_cannot_cancel(client):
    doctor = first_doctor(client)
    appt = book(client, patient_headers(client), free_slot(client, doctor["id"])["id"]).json()
    other = patient_headers(client, "09121234568")
    assert client.post(f"{API}/patient/appointments/{appt['id']}/cancel", headers=other).status_code == 404


def test_doctor_admin_manages_doctors_and_secretaries(client):
    h = auth(client, DOCTOR_ADMIN)
    spec = client.post(f"{API}/admin/specialties", json={"name": "پوست و مو", "icon": "skin"}, headers=h)
    assert spec.status_code == 201
    dup = client.post(f"{API}/admin/specialties", json={"name": "پوست و مو"}, headers=h)
    assert dup.status_code == 409
    body = {
        "full_name": "دکتر آزمایشی",
        "specialty_id": spec.json()["id"],
        "title": "متخصص پوست",
        "bio": "معرفی",
        "user_personnel_code": STAFF,
        "secretary_codes": [SECRETARY],
    }
    r = client.post(f"{API}/admin/doctors", json=body, headers=h)
    assert r.status_code == 201, r.text
    d = r.json()
    assert d["user"]["personnel_code"] == STAFF and [s["personnel_code"] for s in d["secretaries"]] == [
        SECRETARY
    ]
    photo = client.post(
        f"{API}/admin/doctors/{d['id']}/photo", params={"filename": "a.png"}, content=PNG, headers=h
    )
    assert photo.json()["has_photo"] is True
    assert client.get(f"{API}/public/doctors/{d['id']}/photo").headers["content-type"] == "image/png"
    # با هر عکس جدید نسخه عکس عوض می‌شود تا اپ عکس قدیمی کش‌شده را نشان ندهد
    v1 = client.get(f"{API}/public/doctors/{d['id']}").json()["photo_version"]
    assert v1
    client.post(f"{API}/admin/doctors/{d['id']}/photo", params={"filename": "b.png"}, content=PNG, headers=h)
    v2 = client.get(f"{API}/public/doctors/{d['id']}").json()["photo_version"]
    assert v2 and v2 != v1

    r = client.put(f"{API}/admin/doctors/{d['id']}", json={**body, "secretary_codes": []}, headers=h)
    assert r.json()["secretaries"] == []
    # حساب کارکنان فقط به یک پزشک وصل می‌شود
    taken = client.post(f"{API}/admin/doctors", json={**body, "user_personnel_code": DOCTOR}, headers=h)
    assert taken.status_code == 409
    assert client.delete(f"{API}/admin/specialties/{spec.json()['id']}", headers=h).status_code == 409


def test_staff_and_secretary_cannot_edit_doctors(client):
    for code in (STAFF, SECRETARY, SYSADMIN):
        h = auth(client, code)
        r = client.post(f"{API}/admin/specialties", json={"name": "x"}, headers=h)
        assert r.status_code == 403, code


def test_secretary_sees_only_assigned_doctors(client, db):
    h = auth(client, SECRETARY)
    names = {d["full_name"] for d in client.get(f"{API}/admin/doctors", headers=h).json()}
    assert names == {"دکتر نمونه یکم", "دکتر نمونه دوم", "دکتر نمونه سوم"}
    admin = auth(client, DOCTOR_ADMIN)
    d = first_doctor(client)
    full = next(x for x in client.get(f"{API}/admin/doctors", headers=admin).json() if x["id"] == d["id"])
    body = {
        **{k: full[k] for k in ("full_name", "specialty_id", "title", "bio", "visit_minutes", "is_featured")},
        "user_personnel_code": DOCTOR,
        "secretary_codes": [],
    }
    assert client.put(f"{API}/admin/doctors/{d['id']}", json=body, headers=admin).status_code == 200
    assert d["id"] not in [x["id"] for x in client.get(f"{API}/admin/doctors", headers=h).json()]
    start = date.today().isoformat()
    assert (
        client.get(f"{API}/manage/doctors/{d['id']}/slots", params={"start": start}, headers=h).status_code
        == 403
    )
    assert (
        client.get(
            f"{API}/manage/doctors/{d['id']}/slots", params={"start": start}, headers=auth(client, STAFF)
        ).status_code
        == 403
    )


def test_secretary_defines_session_and_repeats(client):
    h = auth(client, SECRETARY)
    d = first_doctor(client)
    day = date.today() + timedelta(days=10)
    body = {"date": day.isoformat(), "start": "16:00", "end": "18:00", "minutes": 20, "repeat_weeks": 2}
    r = client.post(f"{API}/manage/doctors/{d['id']}/sessions", json=body, headers=h)
    assert r.status_code == 201 and r.json() == {"created": 12, "skipped": 0}
    again = client.post(f"{API}/manage/doctors/{d['id']}/sessions", json=body, headers=h)
    assert again.json() == {"created": 0, "skipped": 12}
    slots = client.get(
        f"{API}/manage/doctors/{d['id']}/slots", params={"start": day.isoformat(), "days": 1}, headers=h
    ).json()
    assert len(slots) == 6
    first = datetime.fromisoformat(slots[0]["starts_at"]).astimezone(LOCAL_TZ)
    assert (first.hour, first.minute) == (16, 0)
    bad = client.post(f"{API}/manage/doctors/{d['id']}/sessions", json={**body, "end": "15:00"}, headers=h)
    assert bad.status_code == 422


def test_secretary_books_moves_cancels(client, db):
    h = auth(client, SECRETARY)
    d = first_doctor(client)
    s1, s2 = free_slot(client, d["id"], 0), free_slot(client, d["id"], 1)
    # موبایلی که حساب مراجع دارد، نوبت را در اپ هم می‌بیند
    ph = patient_headers(client, "09121230000")
    r = client.post(
        f"{API}/manage/appointments",
        json={"slot_id": s1["id"], "patient_name": "مراجع تلفنی", "patient_mobile": "0912 123 0000"},
        headers=h,
    )
    assert r.status_code == 201, r.text
    appt = r.json()
    assert appt["source"] == "secretary" and appt["patient_mobile"] == "+989121230000"
    assert [a["id"] for a in client.get(f"{API}/patient/appointments", headers=ph).json()] == [appt["id"]]

    moved = client.post(f"{API}/manage/appointments/{appt['id']}/move", json={"slot_id": s2["id"]}, headers=h)
    assert moved.status_code == 200 and moved.json()["slot_id"] == s2["id"]
    found = client.get(f"{API}/manage/appointments", params={"q": appt["tracking_code"]}, headers=h).json()
    assert [a["id"] for a in found] == [appt["id"]]
    assert client.get(f"{API}/manage/appointments", params={"q": "09121230000"}, headers=h).json()

    # وقت پر را نمی‌شود حذف کرد؛ وقت خالی را می‌شود
    assert client.delete(f"{API}/manage/slots/{s2['id']}", headers=h).status_code == 409
    assert client.delete(f"{API}/manage/slots/{s1['id']}", headers=h).status_code == 204
    cancelled = client.post(f"{API}/manage/appointments/{appt['id']}/cancel", headers=h)
    assert cancelled.json()["status"] == "cancelled"
    mine = client.get(f"{API}/patient/appointments", headers=ph).json()[0]
    assert mine["status"] == "cancelled" and mine["cancelled_by"] == "staff"


def test_block_slot_and_cancel_day(client):
    h = auth(client, SECRETARY)
    d = first_doctor(client)
    slot = free_slot(client, d["id"])
    r = client.patch(f"{API}/manage/slots/{slot['id']}", json={"is_blocked": True}, headers=h)
    assert r.json()["is_blocked"] is True
    assert slot["id"] not in [s["id"] for s in client.get(f"{API}/public/doctors/{d['id']}/slots").json()]

    nxt = free_slot(client, d["id"])
    ph = patient_headers(client)
    appt = book(client, ph, nxt["id"]).json()
    day = datetime.fromisoformat(nxt["starts_at"]).astimezone(LOCAL_TZ).date()
    r = client.post(f"{API}/manage/doctors/{d['id']}/cancel-day", json={"date": day.isoformat()}, headers=h)
    assert r.json()["cancelled"] == 1 and r.json()["blocked_slots"] == 12
    assert client.get(f"{API}/patient/appointments", headers=ph).json()[0]["id"] == appt["id"]
    assert client.get(f"{API}/patient/appointments", headers=ph).json()[0]["status"] == "cancelled"


def test_doctor_sees_own_patients_and_marks_attendance(client):
    d = first_doctor(client)
    slot = free_slot(client, d["id"])
    appt = book(client, patient_headers(client), slot["id"]).json()
    h = auth(client, DOCTOR)
    assert client.get(f"{API}/doctor/me", headers=h).json()["id"] == d["id"]
    day = datetime.fromisoformat(slot["starts_at"]).astimezone(LOCAL_TZ).date()
    rows = client.get(f"{API}/doctor/me/appointments", params={"start": day.isoformat()}, headers=h).json()
    assert [(r["id"], r["patient_name"]) for r in rows] == [(appt["id"], "بیمار آزمایشی")]
    r = client.post(
        f"{API}/doctor/me/appointments/{appt['id']}/status", json={"status": "attended"}, headers=h
    )
    assert r.json()["status"] == "attended"
    # کارمندی که پزشک نیست
    assert client.get(f"{API}/doctor/me", headers=auth(client, STAFF)).status_code == 404


def test_reminders_sent_once_for_upcoming(client, db):
    d = first_doctor(client)
    slot = free_slot(client, d["id"])
    appt = book(client, patient_headers(client), slot["id"]).json()
    starts = datetime.fromisoformat(slot["starts_at"])
    assert svc.send_due_reminders(db, at=starts - timedelta(hours=30)) == 0
    assert svc.send_due_reminders(db, at=starts - timedelta(hours=5)) == 1
    assert svc.send_due_reminders(db, at=starts - timedelta(hours=4)) == 0
    db.expire_all()
    assert db.get(Appointment, appt["id"]).reminder_sent_at is not None


def test_session_slots_respect_end():
    day = date(2026, 11, 1)
    starts = svc.session_starts(day, "09:00", "10:10", 30)
    assert [t.astimezone(LOCAL_TZ).strftime("%H:%M") for t in starts] == ["09:00", "09:30"]


def test_demo_doctor_linked_to_staff_account(db):
    u = db.scalar(select(User).where(User.personnel_code == DOCTOR))
    assert db.scalar(select(Doctor).where(Doctor.user_id == u.id)).full_name == "دکتر نمونه یکم"
