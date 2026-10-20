import uuid
from datetime import timedelta
from urllib.parse import urlsplit

from sqlalchemy import select, update

from app.models import AuditLog, ChatMessage, MediaFile, Notification, User
from app.services.training import media_path
from tests.conftest import SAFETY, STAFF, SYSADMIN, TRAINER, UNIT_MANAGER, auth

API = "/api/v1/chat"
SECRET = "بیمار تخت ۵ را بررسی کنید"


def uid(db, code: str) -> str:
    return str(db.scalar(select(User.id).where(User.personnel_code == code)))


def direct(client, h, other: str) -> dict:
    r = client.post(f"{API}/direct", json={"user_id": other}, headers=h)
    assert r.status_code == 200, r.text
    return r.json()


def send(client, h, conv: str, text: str, **extra):
    body = {"client_uuid": str(uuid.uuid4()), "body": text, **extra}
    return client.post(f"{API}/conversations/{conv}/messages", json=body, headers=h)


def convs(client, h) -> dict:
    r = client.get(f"{API}/conversations", headers=h)
    assert r.status_code == 200, r.text
    return r.json()


def find(client, h, conv: str) -> dict | None:
    return next((c for c in convs(client, h)["items"] if c["id"] == conv), None)


def messages(client, h, conv: str, **params) -> dict:
    r = client.get(f"{API}/conversations/{conv}/messages", headers=h, params=params)
    assert r.status_code == 200, r.text
    return r.json()


def chat_notes(db, code: str) -> list[Notification]:
    db.expire_all()
    return list(
        db.scalars(
            select(Notification).where(
                Notification.user_id == uid(db, code), Notification.link_type == "chat"
            )
        )
    )


def test_direct_chat_is_private_and_tracks_reading(client, db):
    s, p, admin = auth(client, STAFF), auth(client, SAFETY), auth(client, SYSADMIN)
    conv = direct(client, s, uid(db, SAFETY))
    assert conv["kind"] == "direct" and conv["can_post"] and conv["title"] == "کارشناس ایمنی بیمار"
    # گفتگوی خالی فقط برای کسی که آن را باز کرده در فهرست است
    assert find(client, p, conv["id"]) is None

    key = str(uuid.uuid4())
    first = client.post(
        f"{API}/conversations/{conv['id']}/messages", json={"client_uuid": key, "body": SECRET}, headers=s
    )
    assert first.status_code == 201, first.text
    # ارسال دوباره از صف آفلاین پیام تکراری نمی‌سازد
    again = client.post(
        f"{API}/conversations/{conv['id']}/messages", json={"client_uuid": key, "body": SECRET}, headers=s
    )
    assert again.json()["id"] == first.json()["id"]
    assert send(client, s, conv["id"], "پیام دوم").status_code == 201

    mine = find(client, p, conv["id"])
    assert mine["title"] == "کارمند نمونه (پرستار اورژانس)" and mine["unread"] == 2
    assert mine["last_message"]["text"] == "پیام دوم"
    assert convs(client, p)["unread_total"] == 2
    assert client.get(f"{API}/unread-count", headers=p).json() == {"count": 2}
    # در زنگوله برای هر گفتگو فقط یک اعلان خوانده‌نشده، با آخرین پیام
    notes = chat_notes(db, SAFETY)
    assert [(n.kind, n.title, n.body) for n in notes] == [
        ("chat.message", "کارمند نمونه (پرستار اورژانس)", "پیام دوم")
    ]
    assert direct(client, p, uid(db, STAFF))["id"] == conv["id"]

    # کسی جز دو طرف، حتی مدیر سیستم، به گفتگو راه ندارد
    for path in ("", "/messages"):
        assert client.get(f"{API}/conversations/{conv['id']}{path}", headers=admin).status_code == 404
    assert send(client, admin, conv["id"], "x").status_code == 404
    assert find(client, admin, conv["id"]) is None

    page = messages(client, p, conv["id"])
    assert [m["body"] for m in page["items"]] == [SECRET, "پیام دوم"]
    assert page["peer_read_at"] >= page["items"][-1]["created_at"]  # فرستنده پیام خودش را خوانده
    assert messages(client, s, conv["id"])["peer_read_at"] < page["items"][0]["created_at"]
    assert client.post(f"{API}/conversations/{conv['id']}/read", headers=p).status_code == 204
    assert find(client, p, conv["id"])["unread"] == 0
    assert all(n.read_at for n in chat_notes(db, SAFETY))
    # «دیده شد» برای فرستنده
    assert messages(client, s, conv["id"])["peer_read_at"] >= page["items"][-1]["created_at"]

    # متن پیام هیچ‌جا در لاگ حسابرسی نیست
    details = " ".join(str(a.details) for a in db.scalars(select(AuditLog)))
    assert "بیمار تخت" not in details and "پیام دوم" not in details
    assert client.post(f"{API}/direct", json={"user_id": uid(db, STAFF)}, headers=s).status_code == 400


def test_groups_need_permission_and_admin(client, db):
    s, m, p, t = (auth(client, c) for c in (STAFF, UNIT_MANAGER, SAFETY, TRAINER))
    body = {"title": "شیفت شب اورژانس", "member_ids": [uid(db, STAFF), uid(db, SAFETY)]}
    assert client.post(f"{API}/groups", json=body, headers=s).status_code == 403
    r = client.post(f"{API}/groups", json=body, headers=m)
    assert r.status_code == 201, r.text
    g = r.json()
    assert g["can_manage"] and g["member_count"] == 3
    assert [x["is_admin"] for x in g["members"]] == [True, False, False]
    assert [n.kind for n in chat_notes(db, STAFF)] == ["chat.added"]

    gid = g["id"]
    assert find(client, s, gid)["can_post"] and not find(client, s, gid)["is_admin"]
    add = f"{API}/conversations/{gid}/members"
    assert client.post(add, json={"user_ids": [uid(db, TRAINER)]}, headers=s).status_code == 403
    assert client.patch(f"{API}/conversations/{gid}", json={"title": "x"}, headers=s).status_code == 403
    assert client.post(add, json={"user_ids": [str(uuid.uuid4())]}, headers=m).status_code == 400
    assert client.post(add, json={"user_ids": [uid(db, TRAINER)]}, headers=m).json()["member_count"] == 4
    assert send(client, t, gid, "سلام").status_code == 201
    note = [n for n in chat_notes(db, STAFF) if n.kind == "chat.message"][0]
    assert note.title == "شیفت شب اورژانس" and note.body == "مسئول آموزش: سلام"

    # عضو عادی فقط خودش را بیرون می‌برد؛ مدیر هر عضوی را
    assert client.delete(f"{API}/conversations/{gid}/members/{uid(db, SAFETY)}", headers=s).status_code == 403
    assert client.delete(f"{API}/conversations/{gid}/members/{uid(db, STAFF)}", headers=s).status_code == 204
    assert client.get(f"{API}/conversations/{gid}", headers=s).status_code == 404
    assert client.delete(f"{API}/conversations/{gid}/members/{uid(db, SAFETY)}", headers=m).status_code == 204
    # مدیر که برود، قدیمی‌ترین عضو باقی‌مانده مدیر می‌شود
    assert (
        client.delete(f"{API}/conversations/{gid}/members/{uid(db, UNIT_MANAGER)}", headers=m).status_code
        == 204
    )
    left = client.get(f"{API}/conversations/{gid}", headers=t).json()
    assert left["can_manage"] and [x["full_name"] for x in left["members"]] == ["مسئول آموزش"]

    actions = [a.action for a in db.scalars(select(AuditLog).where(AuditLog.entity_type == "chat"))]
    assert sorted(actions) == sorted(
        [
            "chat.group.created",
            "chat.members.added",
            "chat.member.left",
            "chat.member.removed",
            "chat.member.left",
        ]
    )
    # مدیر سیستم هم گروه می‌سازد
    assert (
        client.post(f"{API}/groups", json={"title": "IT"}, headers=auth(client, SYSADMIN)).status_code == 201
    )


def test_unit_channel_only_manager_posts_and_tracks_staff(client, db):
    s, m = auth(client, STAFF), auth(client, UNIT_MANAGER)
    channel = next(c for c in convs(client, s)["items"] if c["kind"] == "unit")
    assert channel["title"] == "اورژانس" and not channel["can_post"]
    assert send(client, s, channel["id"], "سلام").status_code == 403

    # کارمند تازه واحد هنوز اپ را باز نکرده، ولی با پیام مسئول واحد عضو می‌شود و اعلان می‌گیرد
    admin = auth(client, SYSADMIN)
    emergency = db.scalar(select(User.unit_id).where(User.personnel_code == STAFF))
    new = client.post(
        "/api/v1/admin/users",
        json={
            "personnel_code": "2001",
            "full_name": "پرستار تازه",
            "unit_id": str(emergency),
            "temporary_password": "Temp12345",
            "roles": [{"role": "staff"}],
        },
        headers=admin,
    )
    assert new.status_code == 201, new.text
    mine = next(c for c in convs(client, m)["items"] if c["kind"] == "unit")
    assert mine["id"] == channel["id"] and mine["can_post"]
    assert send(client, m, channel["id"], "جلسه بخش ساعت ۱۰").status_code == 201
    assert [n.kind for n in chat_notes(db, "2001")] == ["chat.message"]
    assert find(client, s, channel["id"])["unread"] == 1

    # بی‌صدا: اعلان نمی‌آید و در جمع خوانده‌نشده‌ها حساب نمی‌شود
    client.post(f"{API}/conversations/{channel['id']}/read", headers=s)
    assert (
        client.post(f"{API}/conversations/{channel['id']}/mute", json={"muted": True}, headers=s).status_code
        == 204
    )
    send(client, m, channel["id"], "یادآوری جلسه")
    assert len(chat_notes(db, STAFF)) == 1
    assert find(client, s, channel["id"])["unread"] == 1 and convs(client, s)["unread_total"] == 0

    # تغییر واحد یا غیرفعال شدن: همان لحظه از کانال و گفتگوها بیرون می‌رود
    d = direct(client, m, uid(db, STAFF))
    send(client, m, d["id"], "سلام")
    it = db.scalar(select(User.unit_id).where(User.personnel_code == SYSADMIN))
    patch = client.patch(f"/api/v1/admin/users/{uid(db, STAFF)}", json={"unit_id": str(it)}, headers=admin)
    assert patch.status_code == 200, patch.text
    assert client.get(f"{API}/conversations/{channel['id']}", headers=s).status_code == 404
    assert client.get(f"{API}/conversations/{d['id']}", headers=s).status_code == 200
    client.patch(f"/api/v1/admin/users/{uid(db, STAFF)}", json={"is_active": False}, headers=admin)
    dm = client.get(f"{API}/conversations/{d['id']}", headers=m).json()
    assert dm["member_count"] == 1 and not dm["can_post"] and dm["title"] == "کارمند نمونه (پرستار اورژانس)"
    assert send(client, m, d["id"], "هستی؟").status_code == 403
    assert [x["body"] for x in messages(client, m, d["id"])["items"]] == ["سلام"]


def test_edit_delete_reply_and_polling(client, db):
    s, p = auth(client, STAFF), auth(client, SAFETY)
    conv = direct(client, s, uid(db, SAFETY))["id"]
    a = send(client, s, conv, "متن اول").json()
    b = send(client, p, conv, "پاسخ", reply_to_id=a["id"]).json()
    assert b["reply_to"] == {
        "id": a["id"],
        "sender_name": "کارمند نمونه (پرستار اورژانس)",
        "text": "متن اول",
        "has_attachment": False,
        "deleted": False,
    }
    assert a["can_edit"] and not messages(client, p, conv)["items"][0]["can_edit"]
    cursor = messages(client, p, conv)["cursor"]

    url = f"{API}/conversations/{conv}/messages/{a['id']}"
    assert client.patch(url, json={"body": "دستکاری"}, headers=p).status_code == 403
    e = client.patch(url, json={"body": "متن اصلاح‌شده"}, headers=s)
    assert e.status_code == 200 and e.json()["edited"] and e.json()["body"] == "متن اصلاح‌شده"
    # پرسش دوره‌ای با cursor ویرایش را هم برمی‌گرداند
    changed = messages(client, p, conv, since=cursor)["items"]
    assert "متن اصلاح‌شده" in [x["body"] for x in changed]

    assert client.delete(url, headers=s).status_code == 204
    items = messages(client, p, conv)["items"]
    assert items[0]["deleted"] and items[0]["body"] is None
    assert items[1]["reply_to"]["deleted"] and items[1]["reply_to"]["text"] is None
    # پیام حذف‌شده خوانده‌نشده حساب نمی‌شود
    c = send(client, s, conv, "اشتباه فرستادم").json()
    assert find(client, p, conv)["unread"] == 1
    client.delete(f"{API}/conversations/{conv}/messages/{c['id']}", headers=s)
    assert find(client, p, conv)["unread"] == 0

    # پس از ۱۵ دقیقه ویرایش و حذف ممکن نیست
    db.execute(
        update(ChatMessage)
        .where(ChatMessage.id == b["id"])
        .values(created_at=ChatMessage.created_at - timedelta(minutes=16))
    )
    db.commit()
    burl = f"{API}/conversations/{conv}/messages/{b['id']}"
    assert client.patch(burl, json={"body": "دیر"}, headers=p).status_code == 403
    assert client.delete(burl, headers=p).status_code == 403
    other = direct(client, auth(client, TRAINER), uid(db, SAFETY))["id"]
    assert send(client, p, other, "x", reply_to_id=b["id"]).status_code == 400

    # صفحه‌بندی پیام‌های قدیمی‌تر
    conv = direct(client, s, uid(db, TRAINER))["id"]
    for i in range(5):
        send(client, s, conv, f"پیام {i}")
    first = messages(client, s, conv, limit=3)
    assert [x["body"] for x in first["items"]] == ["پیام 2", "پیام 3", "پیام 4"] and first["next_before"]
    older = messages(client, s, conv, limit=3, before=first["next_before"])
    assert [x["body"] for x in older["items"]] == ["پیام 0", "پیام 1"] and older["next_before"] is None


def test_attachments_and_people_search(client, db):
    s, p = auth(client, STAFF), auth(client, SAFETY)
    conv = direct(client, s, uid(db, SAFETY))["id"]
    url = f"{API}/conversations/{conv}/attachments"
    params = {"filename": "a.exe", "client_uuid": str(uuid.uuid4())}
    assert client.post(url, params=params, content=b"x" * 10, headers=s).status_code == 400
    png = b"\x89PNG\r\n\x1a\n" + b"0" * 500
    params = {"filename": "عکس.png", "client_uuid": str(uuid.uuid4())}
    r = client.post(url, params=params, content=png, headers=s)
    assert r.status_code == 201, r.text
    att = r.json()["attachment"]
    assert att["content_type"] == "image/png" and r.json()["body"] is None
    got = client.get(urlsplit(att["url"]).path + "?" + urlsplit(att["url"]).query)
    assert got.status_code == 200 and got.content == png
    assert chat_notes(db, SAFETY)[0].body == "[عکس]"
    assert find(client, p, conv)["last_message"]["has_attachment"]
    # غیرعضو فایل نمی‌فرستد
    assert client.post(url, params=params, content=png, headers=auth(client, TRAINER)).status_code == 404

    media = db.get(MediaFile, uuid.UUID(att["id"]))
    path = media_path(media)
    assert (
        client.delete(f"{API}/conversations/{conv}/messages/{r.json()['id']}", headers=s).status_code == 204
    )
    db.expire_all()
    assert db.get(MediaFile, uuid.UUID(att["id"])) is None and not path.exists()

    found = client.get(f"{API}/people", params={"q": "ایمنی"}, headers=s).json()
    assert [(x["full_name"], x["unit_name"]) for x in found] == [
        ("کارشناس ایمنی بیمار", "دفتر بهبود کیفیت و ایمنی بیمار")
    ]
    assert [
        x["personnel_code"] for x in client.get(f"{API}/people", params={"q": "1001"}, headers=s).json()
    ] == []
    assert (
        client.get(f"{API}/people", params={"q": "1003"}, headers=s).json()[0]["full_name"]
        == "کارشناس ایمنی بیمار"
    )
