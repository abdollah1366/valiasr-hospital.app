import uuid
from datetime import UTC, datetime, timedelta

from sqlalchemy import select

from app.models import AuditLog, Incident, Notification, ServiceRequest, User
from tests.conftest import CONTENT, SAFETY, STAFF, TRAINER, UNIT_MANAGER, auth
from tests.test_incidents import report
from tests.test_incidents import unit_id as incident_unit
from tests.test_requests import FACILITIES_AGENT, IT_AGENT, PRINTER, create, status, type_id, unit_id

API = "/api/v1"


def inbox(client, h, **params):
    r = client.get(f"{API}/notifications", headers=h, params=params)
    assert r.status_code == 200, r.text
    return r.json()


def kinds(client, h) -> list[str]:
    return [n["kind"] for n in inbox(client, h)["items"]]


def test_request_lifecycle_notifies_the_other_side(client, db):
    s, it = auth(client, STAFF), auth(client, IT_AGENT)
    fac = auth(client, FACILITIES_AGENT)
    req = create(client, s, type_id(db, PRINTER), priority="urgent").json()

    # کارشناس واحد مسئول از درخواست جدید باخبر می‌شود؛ کارشناس واحد دیگر و خود درخواست‌کننده نه
    new = inbox(client, it)["items"]
    assert [(n["kind"], n["link_type"], n["link_id"], n["important"]) for n in new] == [
        ("request.new", "request", req["id"], True)
    ]
    assert req["tracking_code"] in new[0]["body"]
    assert kinds(client, fac) == [] and kinds(client, s) == []

    assert status(client, it, req["id"], "accepted").is_success
    client.post(f"{API}/requests/{req['id']}/comments", json={"text": "کارتریج را عوض کنید؟"}, headers=s)
    assert status(client, it, req["id"], "done", resolution="غلتک تعویض شد").is_success
    mine = inbox(client, s)["items"]
    assert [n["kind"] for n in mine] == ["request.status", "request.status"]
    assert "انجام شد" in mine[0]["title"] and mine[0]["body"] == "غلتک تعویض شد"
    # پیام درخواست‌کننده فقط به کارشناس برداشته‌شده می‌رسد
    assert kinds(client, it) == ["request.comment", "request.new"]

    client.post(f"{API}/requests/{req['id']}/reopen", json={"note": "هنوز گیر می‌کند"}, headers=s)
    reopened = inbox(client, it)["items"][0]
    assert reopened["kind"] == "request.reopened" and reopened["important"] is True

    # ارجاع به واحد دیگر به کارشناسان آن واحد خبر می‌دهد
    t = client.post(
        f"{API}/requests/{req['id']}/transfer",
        json={"unit_id": unit_id(db, "تأسیسات"), "note": "مشکل برق پریز است"},
        headers=it,
    )
    assert t.status_code in (200, 204)
    assert kinds(client, fac) == ["request.transferred"]


def test_read_state_paging_and_isolation(client, db):
    s, it = auth(client, STAFF), auth(client, IT_AGENT)
    tid = type_id(db, PRINTER)
    for _ in range(3):
        create(client, s, tid)
    assert client.get(f"{API}/notifications/unread-count", headers=it).json() == {"count": 3, "important": 0}

    page = inbox(client, it, limit=2)
    assert len(page["items"]) == 2 and page["unread"] == 3 and page["next_before"]
    rest = inbox(client, it, limit=2, before=page["next_before"])
    assert len(rest["items"]) == 1 and rest["next_before"] is None
    ids = [n["id"] for n in page["items"] + rest["items"]]
    assert len(set(ids)) == 3

    # اعلان دیگران خوانده یا علامت‌گذاری نمی‌شود
    assert client.post(f"{API}/notifications/{ids[0]}/read", headers=s).status_code == 404
    assert client.post(f"{API}/notifications/{ids[0]}/read", headers=it).status_code == 204
    assert inbox(client, it, unread_only=True)["unread"] == 2
    assert len(inbox(client, it, unread_only=True)["items"]) == 2
    assert client.post(f"{API}/notifications/read-all", headers=it).status_code == 204
    assert client.get(f"{API}/notifications/unread-count", headers=it).json()["count"] == 0
    assert client.get(f"{API}/notifications").status_code == 401

    # اعلان‌ها در لاگ حسابرسی ثبت نمی‌شوند
    assert db.scalar(select(AuditLog).where(AuditLog.action.like("notification%"))) is None


def test_incident_notifications_never_carry_patient_data(client, db):
    h = auth(client, SAFETY)
    r, _ = report(client, db)
    iid = r.json()["id"]
    manager = db.scalar(select(User).where(User.personnel_code == UNIT_MANAGER))
    a = client.post(
        f"{API}/incidents/{iid}/assign",
        headers=h,
        json={"unit_id": incident_unit(db, "اورژانس"), "user_id": str(manager.id), "note": "بیمار علی محمدی"},
    )
    assert a.status_code == 200, a.text

    to_manager = inbox(client, auth(client, UNIT_MANAGER))["items"]
    assert [(n["kind"], n["link_id"], n["important"]) for n in to_manager] == [
        ("incident.assigned", iid, True)
    ]
    to_reporter = inbox(client, auth(client, STAFF))["items"]
    assert [n["kind"] for n in to_reporter] == ["incident.status"]
    assert "ارجاع‌شده" in to_reporter[0]["title"]
    for n in db.scalars(select(Notification)):
        text = f"{n.title} {n.body or ''}"
        assert "علی" not in text and "98765" not in text and "تخت" not in text


def test_anonymous_reporter_gets_no_notification(client, db):
    r, _ = report(client, db, is_anonymous=True, follow_up_secret="s3cret-long-enough")
    iid = db.scalar(select(Incident.id))
    h = auth(client, SAFETY)
    assert client.post(f"{API}/incidents/{iid}/status", headers=h, json={"status": "under_review"}).is_success
    assert db.scalar(select(Notification)) is None


def test_announcement_reaches_its_audience_and_can_be_scheduled(client, db):
    h = auth(client, CONTENT)
    body = {"title": "بخشنامه بهداشت دست", "body": "متن کامل", "requires_ack": True}
    body["target_unit_ids"] = [incident_unit(db, "اورژانس")]
    a = client.post(f"{API}/admin/announcements", json=body, headers=h).json()
    staff = inbox(client, auth(client, STAFF))["items"]
    assert [(n["kind"], n["link_id"], n["important"]) for n in staff] == [("announcement", a["id"], True)]
    assert kinds(client, auth(client, SAFETY)) == []  # خارج از واحد هدف
    assert kinds(client, h) == []  # نویسنده

    # اطلاعیه عمومی اعلان نمی‌سازد؛ زمان‌دار تا زمان انتشار دیده نمی‌شود
    client.post(
        f"{API}/admin/announcements", json={"title": "عمومی", "body": "x", "audience": "public"}, headers=h
    )
    later = (datetime.now(UTC) + timedelta(days=1)).isoformat()
    sched = client.post(
        f"{API}/admin/announcements", json={"title": "فردا", "body": "x", "published_at": later}, headers=h
    ).json()
    assert kinds(client, auth(client, STAFF)) == ["announcement"]
    assert db.scalar(select(Notification).where(Notification.link_id == uuid.UUID(sched["id"]))) is not None

    # منقضی کردن، اعلان خوانده‌نشده را برمی‌دارد
    assert client.delete(f"{API}/admin/announcements/{a['id']}", headers=h).status_code == 204
    assert kinds(client, auth(client, STAFF)) == []


def test_course_and_exam_notifications_follow_publishing(client, db):
    t = auth(client, TRAINER)
    c = client.post(
        f"{API}/admin/training/courses", json={"title": "احیا", "is_mandatory": True}, headers=t
    ).json()
    assert kinds(client, auth(client, STAFF)) == []  # پیش‌نویس
    client.patch(f"{API}/admin/training/courses/{c['id']}", json={"status": "published"}, headers=t)
    client.patch(f"{API}/admin/training/courses/{c['id']}", json={"description": "ویرایش"}, headers=t)
    got = inbox(client, auth(client, STAFF))["items"]
    assert [(n["kind"], n["link_id"], n["important"]) for n in got] == [("training.course", c["id"], True)]
    assert kinds(client, t) == []  # خود مسئول آموزش

    opens = datetime.now(UTC) + timedelta(hours=2)
    e = client.post(
        f"{API}/admin/training/exams",
        json={"title": "آزمون احیا", "status": "published", "opens_at": opens.isoformat()},
        headers=t,
    ).json()
    assert kinds(client, auth(client, STAFF)) == ["training.course"]  # هنوز باز نشده
    row = db.scalar(select(Notification).where(Notification.link_id == uuid.UUID(e["id"])))
    assert abs((row.created_at - opens).total_seconds()) < 1

    # باز کردن دستی، اعلان را همین حالا نشان می‌دهد و تکراری نمی‌سازد
    client.patch(f"{API}/admin/training/exams/{e['id']}", json={"manual_state": "open"}, headers=t)
    assert kinds(client, auth(client, STAFF)) == ["training.exam", "training.course"]
    db.expire_all()
    rows = db.scalars(select(Notification).where(Notification.link_id == uuid.UUID(e["id"]))).all()
    assert len({r.user_id for r in rows}) == len(rows)

    # برگشت به پیش‌نویس اعلان خوانده‌نشده را برمی‌دارد
    client.patch(f"{API}/admin/training/exams/{e['id']}", json={"status": "draft"}, headers=t)
    assert kinds(client, auth(client, STAFF)) == ["training.course"]


def test_auto_close_and_old_read_notifications(client, db):
    s, it = auth(client, STAFF), auth(client, IT_AGENT)
    req = create(client, s, type_id(db, PRINTER)).json()
    status(client, it, req["id"], "accepted")
    status(client, it, req["id"], "done", resolution="انجام شد")
    r = db.get(ServiceRequest, uuid.UUID(req["id"]))
    r.done_at = datetime.now(UTC) - timedelta(days=4)
    old = db.scalars(select(Notification).where(Notification.user_id == r.assigned_to_id)).first()
    old.read_at = datetime.now(UTC) - timedelta(days=91)
    old_id = old.id
    db.commit()

    client.get(f"{API}/requests/mine", headers=s)  # بسته شدن خودکار
    titles = [n["title"] for n in inbox(client, s)["items"]]
    assert any("بسته" in x for x in titles)
    inbox(client, it)  # پاک‌سازی اعلان‌های خوانده‌شده قدیمی
    db.expire_all()
    assert db.get(Notification, old_id) is None
