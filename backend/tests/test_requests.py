import uuid
from datetime import UTC, datetime, timedelta
from urllib.parse import urlsplit

from sqlalchemy import select

from app.models import AuditLog, RequestType, ServiceRequest, Unit
from tests.conftest import STAFF, SYSADMIN, TRAINER, UNIT_MANAGER, auth

API = "/api/v1"
IT_AGENT, FACILITIES_AGENT = "1007", "1008"


def type_id(db, name: str) -> str:
    return str(db.scalar(select(RequestType.id).where(RequestType.name == name)))


def unit_id(db, name: str) -> str:
    return str(db.scalar(select(Unit.id).where(Unit.name == name)))


PRINTER = "خرابی رایانه یا پرینتر"
FACILITIES = "تأسیسات (برق، آب، سرمایش و گرمایش)"


def create(client, h, tid: str, **extra):
    body = {
        "client_uuid": str(uuid.uuid4()),
        "type_id": tid,
        "description": "پرینتر ایستگاه پرستاری کاغذ را گیر می‌اندازد",
        "location": "اورژانس، ایستگاه ۲",
        "answers": {"device": "پرینتر"},
        **extra,
    }
    return client.post(f"{API}/requests", json=body, headers=h)


def status(client, h, rid: str, new: str, **extra):
    return client.post(f"{API}/requests/{rid}/status", json={"status": new, **extra}, headers=h)


def test_create_validates_answers_and_deduplicates(client, db):
    s = auth(client, STAFF)
    tid = type_id(db, PRINTER)
    assert create(client, s, tid, answers={}).status_code == 422
    assert create(client, s, tid, answers={"device": "تلویزیون"}).status_code == 422
    assert create(client, s, str(uuid.uuid4())).status_code == 400

    cid = str(uuid.uuid4())
    r = create(client, s, tid, client_uuid=cid, priority="urgent", answers={"device": "پرینتر", "x": 1})
    assert r.status_code == 201, r.text
    first = r.json()
    assert first["tracking_code"].startswith("R-") and first["status"] == "submitted"
    again = create(client, s, tid, client_uuid=cid).json()
    assert again["id"] == first["id"] and again["duplicate"] is True
    # همان شناسه از کاربر دیگر پذیرفته نمی‌شود
    assert create(client, auth(client, UNIT_MANAGER), tid, client_uuid=cid).status_code == 409

    d = client.get(f"{API}/requests/{first['id']}", headers=s).json()
    assert d["answers"] == [{"label": "نوع دستگاه", "value": "پرینتر"}]  # فیلد ناشناخته دور ریخته شد
    assert d["handler_unit_name"] == "فناوری اطلاعات" and d["requester_unit_name"] == "اورژانس"
    assert d["actions"] == ["comment", "attach"]
    assert [e["kind"] for e in d["events"]] == ["created"]
    mine = client.get(f"{API}/requests/mine", headers=s).json()
    assert [m["id"] for m in mine] == [first["id"]]

    # غیرفعال کردن نوع، آن را از فهرست اپ برمی‌دارد
    admin = auth(client, SYSADMIN)
    assert client.patch(
        f"{API}/admin/request-types/{tid}", json={"is_active": False}, headers=admin
    ).is_success
    names = [t["name"] for t in client.get(f"{API}/request-types", headers=s).json()]
    assert PRINTER not in names and FACILITIES in names
    assert create(client, s, tid).status_code == 400


def test_queue_visibility_is_scoped_to_units(client, db):
    s = auth(client, STAFF)
    it_req = create(client, s, type_id(db, PRINTER), priority="critical").json()
    fac_req = create(client, s, type_id(db, FACILITIES), answers={"kind": "برق"}).json()
    other = create(client, auth(client, TRAINER), type_id(db, FACILITIES), answers={"kind": "آب و فاضلاب"})
    other = other.json()

    it = auth(client, IT_AGENT)
    q = client.get(f"{API}/requests", headers=it).json()
    assert [r["id"] for r in q["items"]] == [it_req["id"]]
    assert client.get(f"{API}/requests/{fac_req['id']}", headers=it).status_code == 404

    fac = auth(client, FACILITIES_AGENT)
    ids = {r["id"] for r in client.get(f"{API}/requests", headers=fac).json()["items"]}
    assert ids == {fac_req["id"], other["id"]}

    # مسئول واحد درخواست‌های کارکنان واحد خودش را می‌بیند، نه بقیه را؛ ولی رسیدگی نمی‌کند
    m = auth(client, UNIT_MANAGER)
    ids = {r["id"] for r in client.get(f"{API}/requests", headers=m).json()["items"]}
    assert ids == {it_req["id"], fac_req["id"]}
    d = client.get(f"{API}/requests/{it_req['id']}", headers=m).json()
    assert d["actions"] == []
    assert status(client, m, it_req["id"], "accepted").status_code == 403

    # کارمند عادی کارتابل ندارد و درخواست دیگران را نمی‌بیند
    assert client.get(f"{API}/requests", headers=s).status_code == 403
    assert client.get(f"{API}/requests/{other['id']}", headers=s).status_code == 404
    # مدیر سیستم انواع را تعریف می‌کند ولی کارتابل درخواست‌ها را ندارد
    assert client.get(f"{API}/requests", headers=auth(client, SYSADMIN)).status_code == 403

    # فیلتر و ترتیب: بحرانی جلوتر
    client.post(f"{API}/requests", headers=s, json={})  # بدنه نامعتبر چیزی نمی‌سازد
    create(client, s, type_id(db, PRINTER), priority="normal")
    items = client.get(f"{API}/requests", headers=it).json()["items"]
    assert [i["priority"] for i in items] == ["critical", "normal"]
    code = it_req["tracking_code"]
    found = client.get(f"{API}/requests", params={"q": code[-4:]}, headers=it).json()["items"]
    assert [i["id"] for i in found] == [it_req["id"]]


def test_workflow_assign_transfer_comment_rate(client, db):
    s = auth(client, STAFF)
    it = auth(client, IT_AGENT)
    r = create(client, s, type_id(db, PRINTER)).json()
    rid = r["id"]

    assert status(client, it, rid, "done", resolution="x").status_code == 409  # از ثبت‌شده مستقیم نه
    d = status(client, it, rid, "accepted").json()
    assert d["status"] == "accepted" and d["assigned_to"]["personnel_code"] == IT_AGENT
    assert d["accepted_at"] is not None
    assert "status:in_progress" in d["actions"] and "assign" in d["actions"]

    agents = client.get(f"{API}/requests/{rid}/agents", headers=it).json()
    assert [a["personnel_code"] for a in agents] == [IT_AGENT]
    bad = client.post(f"{API}/requests/{rid}/assign", json={"user_id": d["requester"]["id"]}, headers=it)
    assert bad.status_code == 400

    # یادداشت دوطرفه
    assert client.post(f"{API}/requests/{rid}/comments", json={"text": "کی می‌آیید؟"}, headers=s).is_success
    d = client.post(f"{API}/requests/{rid}/comments", json={"text": "تا ظهر"}, headers=it).json()
    assert [e["kind"] for e in d["events"]][-2:] == ["comment", "comment"]

    # ارجاع به تأسیسات: کارشناس IT دیگر دسترسی ندارد
    t = client.post(
        f"{API}/requests/{rid}/transfer",
        json={"unit_id": unit_id(db, "تأسیسات"), "note": "مشکل از پریز برق است"},
        headers=it,
    )
    assert t.status_code == 204
    assert client.get(f"{API}/requests/{rid}", headers=it).status_code == 404
    fac = auth(client, FACILITIES_AGENT)
    d = client.get(f"{API}/requests/{rid}", headers=fac).json()
    assert d["status"] == "submitted" and d["assigned_to"] is None and d["handler_unit_name"] == "تأسیسات"

    status(client, fac, rid, "accepted")
    status(client, fac, rid, "in_progress")
    status(client, fac, rid, "on_hold", note="منتظر قطعه")
    assert status(client, fac, rid, "done").status_code == 400  # نتیجه لازم است
    d = status(client, fac, rid, "done", resolution="پریز تعویض شد").json()
    assert d["status"] == "done" and d["reopen_until"] is not None
    assert d["actions"] == ["comment", "attach"]  # کارشناس پس از انجام فقط یادداشت و پیوست دارد

    mine = client.get(f"{API}/requests/{rid}", headers=s).json()
    assert {"rate", "reopen"} <= set(mine["actions"])
    assert client.post(f"{API}/requests/{rid}/rate", json={"score": 6}, headers=s).status_code == 422
    assert client.post(f"{API}/requests/{rid}/rate", json={"score": 5}, headers=fac).status_code == 409
    d = client.post(f"{API}/requests/{rid}/rate", json={"score": 5, "comment": "عالی"}, headers=s).json()
    assert d["status"] == "closed" and d["satisfaction"] == 5
    assert client.post(f"{API}/requests/{rid}/rate", json={"score": 4}, headers=s).status_code == 409
    assert client.post(f"{API}/requests/{rid}/comments", json={"text": "?"}, headers=s).status_code == 403

    kinds = [e["kind"] for e in d["events"]]
    assert kinds[0] == "created" and "transfer" in kinds and kinds[-1] == "rated"
    actions = set(db.scalars(select(AuditLog.action).where(AuditLog.entity_id == rid)))
    assert {"request.created", "request.transferred", "request.rated"} <= actions


def test_reject_needs_reason_and_reopen_window(client, db):
    s = auth(client, STAFF)
    it = auth(client, IT_AGENT)
    a = create(client, s, type_id(db, PRINTER)).json()["id"]
    assert status(client, it, a, "rejected").status_code == 400
    d = status(client, it, a, "rejected", note="تکراری است").json()
    assert d["status"] == "rejected" and d["actions"] == []

    b = create(client, s, type_id(db, PRINTER)).json()["id"]
    status(client, it, b, "accepted")
    status(client, it, b, "done", resolution="درایور نصب شد")
    d = client.post(f"{API}/requests/{b}/reopen", json={"note": "باز هم چاپ نمی‌کند"}, headers=s).json()
    assert d["status"] == "in_progress" and d["done_at"] is None

    # پس از ۳ روز در «انجام شد»، خودکار بسته می‌شود و دیگر اعتراض ممکن نیست
    status(client, it, b, "done", resolution="کارتریج عوض شد")
    r = db.get(ServiceRequest, uuid.UUID(b))
    r.done_at = datetime.now(UTC) - timedelta(days=4)
    db.commit()
    d = client.get(f"{API}/requests/{b}", headers=s).json()
    assert d["status"] == "closed" and "reopen" not in d["actions"] and "rate" in d["actions"]
    auto = [e for e in d["events"] if e["to_status"] == "closed"]
    assert [e["note"] for e in auto] == ["بسته شدن خودکار پس از ۳ روز"] and auto[0]["actor"] is None
    assert client.post(f"{API}/requests/{b}/reopen", json={"note": "x"}, headers=s).status_code == 409


def test_attachments(client, db):
    s = auth(client, STAFF)
    rid = create(client, s, type_id(db, PRINTER)).json()["id"]
    url = f"{API}/requests/{rid}/attachments"
    assert client.post(url, params={"filename": "a.exe"}, content=b"x" * 10, headers=s).status_code == 400
    png = b"\x89PNG\r\n\x1a\n" + b"0" * 500
    r = client.post(url, params={"filename": "عکس.png"}, content=png, headers=s)
    assert r.status_code == 201, r.text
    att = r.json()["events"][-1]["attachment"]
    assert att["original_name"] == "عکس.png" and att["content_type"] == "image/png"
    got = client.get(urlsplit(att["url"]).path + "?" + urlsplit(att["url"]).query)
    assert got.status_code == 200 and got.content == png
    # کسی که درخواست را نمی‌بیند پیوست هم نمی‌گذارد
    fac = auth(client, FACILITIES_AGENT)
    assert client.post(url, params={"filename": "b.png"}, content=png, headers=fac).status_code == 404


def test_stats_and_csv(client, db):
    s = auth(client, STAFF)
    it = auth(client, IT_AGENT)
    a = create(client, s, type_id(db, PRINTER), priority="critical").json()["id"]
    create(client, s, type_id(db, FACILITIES), answers={"kind": "برق"})
    status(client, it, a, "accepted")
    status(client, it, a, "done", resolution="حل شد")
    client.post(f"{API}/requests/{a}/rate", json={"score": 4}, headers=s)

    st = client.get(f"{API}/requests/stats", headers=it).json()
    assert st["total"] == 1 and st["done"] == 1 and st["avg_satisfaction"] == 4 and st["rated"] == 1
    st = client.get(f"{API}/requests/stats", headers=auth(client, UNIT_MANAGER)).json()
    assert st["total"] == 2 and st["open"] == 1
    assert {r["label"] for r in st["by_handler_unit"]} == {"فناوری اطلاعات", "تأسیسات"}

    csv = client.get(f"{API}/requests/export.csv", headers=it)
    assert csv.status_code == 200 and csv.text.startswith("﻿کد پیگیری")
    assert len(csv.text.strip().splitlines()) == 2 and "بحرانی" in csv.text


def test_request_type_admin(client, db):
    admin = auth(client, SYSADMIN)
    body = {
        "name": "درخواست تلفن داخلی",
        "category": "it",
        "handler_unit_id": unit_id(db, "فناوری اطلاعات"),
        "fields": [{"id": "ext", "label": "شماره داخلی", "kind": "number"}],
    }
    assert (
        client.post(f"{API}/admin/request-types", json=body, headers=auth(client, STAFF)).status_code == 403
    )
    assert client.get(f"{API}/admin/request-types", headers=auth(client, IT_AGENT)).status_code == 403
    bad = {**body, "fields": [{"id": "x", "label": "انتخاب", "kind": "select", "options": ["یک"]}]}
    assert client.post(f"{API}/admin/request-types", json=bad, headers=admin).status_code == 422
    dup = {**body, "fields": body["fields"] * 2}
    assert client.post(f"{API}/admin/request-types", json=dup, headers=admin).status_code == 422
    unknown = {**body, "handler_unit_id": str(uuid.uuid4())}
    assert client.post(f"{API}/admin/request-types", json=unknown, headers=admin).status_code == 400

    t = client.post(f"{API}/admin/request-types", json=body, headers=admin).json()
    assert t["handler_unit_name"] == "فناوری اطلاعات" and t["request_count"] == 0
    s = auth(client, STAFF)
    assert create(client, s, t["id"], answers={"ext": "abc"}).status_code == 422
    assert create(client, s, t["id"], answers={"ext": "214"}).status_code == 201
    rows = {r["name"]: r for r in client.get(f"{API}/admin/request-types", headers=admin).json()}
    assert rows["درخواست تلفن داخلی"]["request_count"] == 1
    assert client.delete(f"{API}/admin/request-types/{t['id']}", headers=admin).status_code == 409
    empty = client.post(f"{API}/admin/request-types", json={**body, "name": "موقت"}, headers=admin).json()
    assert client.delete(f"{API}/admin/request-types/{empty['id']}", headers=admin).status_code == 204
