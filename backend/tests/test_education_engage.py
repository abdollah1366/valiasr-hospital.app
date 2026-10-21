from tests.conftest import CONTENT, STAFF, auth
from tests.test_appointments import patient_headers

API = "/api/v1"
EDUCATOR = "1015"
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 20


def publish(client, h, title="رژیم دیابت"):
    r = client.post(
        f"{API}/admin/education",
        json={"title": title, "kind": "article", "body": "متن", "condition": "دیابت", "is_published": True},
        headers=h,
    )
    assert r.status_code == 201, r.text
    return r.json()


def find_patient(client, h, mobile_part):
    rows = client.get(f"{API}/admin/education/patients", params={"q": mobile_part}, headers=h).json()
    assert len(rows) == 1
    return rows[0]


def test_educator_role_permissions(client):
    e = auth(client, EDUCATOR)
    assert client.get(f"{API}/admin/education", headers=e).status_code == 200
    assert client.get(f"{API}/admin/education/tickets", headers=e).status_code == 200
    assert client.get(f"{API}/admin/education/tickets", headers=auth(client, CONTENT)).status_code == 403
    assert (
        client.get(f"{API}/admin/education/patients?q=0912", headers=auth(client, STAFF)).status_code == 403
    )


def test_assign_to_patient_tracks_seen(client):
    e = auth(client, EDUCATOR)
    a, b = publish(client, e, "الف"), publish(client, e, "ب")
    p = patient_headers(client, "09125550001")
    other = patient_headers(client, "09125550002")
    row = find_patient(client, e, "5550001")

    draft = client.post(
        f"{API}/admin/education",
        json={"title": "پیش‌نویس", "kind": "article", "body": "x"},
        headers=e,
    ).json()
    bad = client.post(
        f"{API}/admin/education/assignments",
        json={"patient_id": row["id"], "item_ids": [draft["id"]]},
        headers=e,
    )
    assert bad.status_code == 400

    body = {"patient_id": row["id"], "item_ids": [a["id"], b["id"]], "note": "قبل از عمل بخوانید"}
    r = client.post(f"{API}/admin/education/assignments", json=body, headers=e)
    assert r.status_code == 201 and r.json() == {"added": 2, "already": 0}
    assert client.post(f"{API}/admin/education/assignments", json=body, headers=e).json() == {
        "added": 0,
        "already": 2,
    }

    mine = client.get(f"{API}/patient/education/mine", headers=p).json()
    assert {m["title"] for m in mine} == {"الف", "ب"} and not any(m["seen"] for m in mine)
    assert mine[0]["assignment_note"] == "قبل از عمل بخوانید"
    # مراجع دیگر چیزی ندارد
    assert client.get(f"{API}/patient/education/mine", headers=other).json() == []

    assert find_patient(client, e, "5550001")["unseen_count"] == 2
    assert client.post(f"{API}/patient/education/{a['id']}/seen", headers=p).status_code == 204
    assert client.post(f"{API}/patient/education/{a['id']}/seen", headers=p).status_code == 204
    # آموزشی که برایش فرستاده نشده، بی‌اثر است
    assert client.post(f"{API}/patient/education/{draft['id']}/seen", headers=p).status_code == 204

    rows = client.get(
        f"{API}/admin/education/assignments", params={"patient_id": row["id"]}, headers=e
    ).json()
    seen = next(x for x in rows if x["item_id"] == a["id"])
    assert seen["first_seen_at"] and seen["view_count"] == 2
    assert next(x for x in rows if x["item_id"] == b["id"])["first_seen_at"] is None
    only = client.get(f"{API}/admin/education/assignments", params={"only_unseen": True}, headers=e).json()
    assert [x["item_id"] for x in only] == [b["id"]]
    listed = next(i for i in client.get(f"{API}/admin/education", headers=e).json() if i["id"] == a["id"])
    assert listed["assigned_count"] == 1 and listed["seen_count"] == 1

    assert client.delete(f"{API}/admin/education/assignments/{seen['id']}", headers=e).status_code == 204
    assert len(client.get(f"{API}/patient/education/mine", headers=p).json()) == 1


def test_rating_upsert_and_average(client):
    e = auth(client, EDUCATOR)
    item = publish(client, e)
    p1 = patient_headers(client, "09125550011")
    p2 = patient_headers(client, "09125550012")
    url = f"{API}/patient/education/{item['id']}/rating"
    assert client.put(url, json={"stars": 6}, headers=p1).status_code == 422
    assert client.put(url, json={"stars": 4}, headers=p1).json()["stars"] == 4
    r = client.put(url, json={"stars": 2}, headers=p2).json()
    assert r["rating_average"] == 3.0 and r["rating_count"] == 2
    assert client.put(url, json={"stars": 5}, headers=p1).json()["rating_average"] == 3.5
    assert client.get(url, headers=p1).json()["stars"] == 5
    pub = client.get(f"{API}/public/education/{item['id']}").json()
    assert pub["rating_average"] == 3.5 and pub["rating_count"] == 2
    assert client.put(url, json={"stars": 3}).status_code == 401


def test_ticket_conversation_with_attachment(client):
    e = auth(client, EDUCATOR)
    p = patient_headers(client, "09125550021")
    other = patient_headers(client, "09125550022")

    t = client.post(
        f"{API}/patient/education/tickets", json={"subject": "رژیم غذایی", "body": "سؤالم این است"}, headers=p
    )
    assert t.status_code == 201
    tid = t.json()["id"]
    assert client.get(f"{API}/patient/education/tickets/{tid}", headers=other).status_code == 404

    att = client.post(
        f"{API}/patient/education/tickets/{tid}/attachment?filename=a.png&caption=عکس", content=PNG, headers=p
    )
    assert att.status_code == 201
    last = att.json()["messages"][-1]
    assert last["attachment"]["content_type"] == "image/png" and last["body"] == "عکس"
    f = client.get(last["attachment"]["url"])
    assert f.status_code == 200 and f.content == PNG
    assert (
        client.post(
            f"{API}/patient/education/tickets/{tid}/attachment?filename=a.exe", content=b"x", headers=p
        ).status_code
        == 400
    )

    queue = client.get(f"{API}/admin/education/tickets", headers=e).json()
    mine = next(x for x in queue if x["id"] == tid)
    assert mine["unread"] is True and mine["patient_mobile"].endswith("5550021")
    detail = client.get(f"{API}/admin/education/tickets/{tid}", headers=e).json()
    assert len(detail["messages"]) == 2
    assert (
        next(x for x in client.get(f"{API}/admin/education/tickets", headers=e).json() if x["id"] == tid)[
            "unread"
        ]
        is False
    )

    assert client.get(f"{API}/patient/education/tickets", headers=p).json()[0]["unread"] is False
    rep = client.post(f"{API}/admin/education/tickets/{tid}/messages", json={"body": "پاسخ شما"}, headers=e)
    assert rep.status_code == 201 and rep.json()["messages"][-1]["from_staff"] is True
    assert client.get(f"{API}/patient/education/tickets", headers=p).json()[0]["unread"] is True
    d = client.get(f"{API}/patient/education/tickets/{tid}", headers=p).json()
    assert d["unread"] is False and d["messages"][-1]["staff_name"] == "مسئول آموزش به بیمار"

    closed = client.post(f"{API}/admin/education/tickets/{tid}/close", headers=e).json()
    assert closed["status"] == "closed"
    assert client.post(f"{API}/admin/education/tickets/{tid}/explode", headers=e).status_code == 404
    # پیام تازه مراجع، تیکت بسته را باز می‌کند
    r = client.post(f"{API}/patient/education/tickets/{tid}/messages", json={"body": "ممنون"}, headers=p)
    assert r.json()["status"] == "open"
