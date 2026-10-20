from sqlalchemy import select

from app.models import Unit
from tests.conftest import CONTENT, SAFETY, STAFF, SYSADMIN, auth


def test_public_endpoints_without_login(client):
    assert client.get("/api/v1/public/units").status_code == 200
    names = [u["name"] for u in client.get("/api/v1/public/units").json()]
    assert "ICU" not in names and "اورژانس" in names
    assert len(client.get("/api/v1/public/faqs").json()) == 2
    public = client.get("/api/v1/public/announcements").json()
    assert all(a["audience"] == "public" for a in public) and len(public) == 1


def test_staff_announcements_and_ack(client):
    h = auth(client, STAFF)
    items = client.get("/api/v1/announcements", headers=h).json()
    circular = next(a for a in items if a["requires_ack"])
    assert circular["acknowledged"] is False
    pending = client.get("/api/v1/announcements?only_pending_ack=true", headers=h).json()
    assert [a["id"] for a in pending] == [circular["id"]]
    assert (
        client.post(f"/api/v1/announcements/{circular['id']}/ack", headers=h).json()["acknowledged"] is True
    )
    assert client.get("/api/v1/announcements?only_pending_ack=true", headers=h).json() == []
    stats = client.get(
        f"/api/v1/admin/announcements/{circular['id']}/reads", headers=auth(client, CONTENT)
    ).json()
    assert stats["acknowledged"] == 1 and stats["targeted"] >= 2
    assert "کارمند نمونه (پرستار اورژانس)" not in stats["not_acknowledged"]


def test_targeting_by_role(client):
    c = auth(client, CONTENT)
    r = client.post(
        "/api/v1/admin/announcements",
        headers=c,
        json={
            "title": "فقط ایمنی بیمار",
            "body": "متن",
            "audience": "staff",
            "target_role_codes": ["patient_safety"],
        },
    )
    assert r.status_code == 201
    aid = r.json()["id"]
    assert aid not in [
        a["id"] for a in client.get("/api/v1/announcements", headers=auth(client, STAFF)).json()
    ]
    assert aid in [a["id"] for a in client.get("/api/v1/announcements", headers=auth(client, SAFETY)).json()]
    assert client.post(f"/api/v1/announcements/{aid}/read", headers=auth(client, STAFF)).status_code == 404


def test_targeting_by_unit(client, db):
    icu = str(db.scalar(select(Unit.id).where(Unit.name == "ICU")))
    r = client.post(
        "/api/v1/admin/announcements",
        headers=auth(client, CONTENT),
        json={"title": "فقط ICU", "body": "متن", "audience": "staff", "target_unit_ids": [icu]},
    )
    aid = r.json()["id"]
    assert aid not in [
        a["id"] for a in client.get("/api/v1/announcements", headers=auth(client, STAFF)).json()
    ]


def test_only_content_manager_publishes(client):
    body = {"title": "x", "body": "y"}
    assert (
        client.post("/api/v1/admin/announcements", headers=auth(client, STAFF), json=body).status_code == 403
    )
    assert (
        client.post("/api/v1/admin/announcements", headers=auth(client, SYSADMIN), json=body).status_code
        == 403
    )


def test_public_announcement_cannot_require_ack(client):
    r = client.post(
        "/api/v1/admin/announcements",
        headers=auth(client, CONTENT),
        json={"title": "x", "body": "y", "audience": "public", "requires_ack": True},
    )
    assert r.status_code == 400


def test_feedback_flow(client):
    r = client.post(
        "/api/v1/public/feedback",
        json={"kind": "complaint", "body": "زمان انتظار طولانی بود", "mobile": "09121234567"},
    )
    assert r.status_code == 201
    code = r.json()["tracking_code"]
    t = client.get(f"/api/v1/public/feedback/{code}").json()
    assert t["status"] == "received" and "mobile" not in t
    c = auth(client, CONTENT)
    fid = client.get("/api/v1/admin/feedback", headers=c).json()[0]["id"]
    assert (
        client.post(
            f"/api/v1/admin/feedback/{fid}/respond", headers=c, json={"status": "answered"}
        ).status_code
        == 400
    )
    ok = client.post(
        f"/api/v1/admin/feedback/{fid}/respond",
        headers=c,
        json={"status": "answered", "response": "پیگیری شد"},
    )
    assert ok.status_code == 200
    assert client.get(f"/api/v1/public/feedback/{code}").json()["response"] == "پیگیری شد"


def test_feedback_validation(client):
    r = client.post("/api/v1/public/feedback", json={"kind": "complaint", "body": "کوتاه", "mobile": "123"})
    assert r.status_code == 422 and "mobile" in r.json()["detail"]["fields"]


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}
