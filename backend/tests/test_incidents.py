import uuid
from datetime import date

from sqlalchemy import select

from app.models import AuditLog, Incident, Unit
from tests.conftest import SAFETY, STAFF, SYSADMIN, UNIT_MANAGER, auth

ANSWERS = {
    "report_method": "خوداظهاری",
    "harm_level": "آسیب جزئی",
    "error_types": ["سقوط بیمار"],
    "type_6_items": ["افتادن از تخت"],
    "patient_name": "علی محمدی",
    "file_number": "98765",
    "description": "بیمار هنگام شب از تخت افتاد.",
}


def unit_id(db, name: str) -> str:
    return str(db.scalar(select(Unit.id).where(Unit.name == name)))


def report(client, db, code=STAFF, unit="اورژانس", **overrides):
    body = {
        "client_uuid": str(uuid.uuid4()),
        "kind": "error",
        "unit_id": unit_id(db, unit),
        "occurred_on": date.today().isoformat(),
        "shift": "night",
        "answers": ANSWERS,
        **overrides,
    }
    return client.post("/api/v1/incidents", headers=auth(client, code), json=body), body


def test_create_and_list_mine(client, db):
    r, _ = report(client, db)
    assert r.status_code == 201, r.text
    out = r.json()
    assert out["tracking_code"].startswith("IR-")
    mine = client.get("/api/v1/incidents/mine", headers=auth(client, STAFF)).json()
    assert [m["tracking_code"] for m in mine] == [out["tracking_code"]]


def test_sensitive_answers_encrypted_at_rest(client, db):
    r, _ = report(client, db)
    row = db.get(Incident, uuid.UUID(r.json()["id"]))
    assert "patient_name" not in row.answers
    assert "علی" not in (row.sensitive_answers_encrypted or "")


def test_invalid_answers_return_field_errors(client, db):
    bad = dict(ANSWERS)
    del bad["type_6_items"]
    r, _ = report(client, db, answers=bad)
    assert r.status_code == 422
    assert "type_6_items" in r.json()["detail"]["fields"]


def test_retry_with_same_client_uuid_is_idempotent(client, db):
    r1, body = report(client, db)
    r2 = client.post("/api/v1/incidents", headers=auth(client, STAFF), json=body)
    assert r2.status_code == 201
    assert r2.json()["duplicate"] is True
    assert r2.json()["tracking_code"] == r1.json()["tracking_code"]
    assert db.query(Incident).count() == 1


def test_anonymous_report_has_no_link_to_reporter(client, db):
    r, _ = report(client, db, is_anonymous=True, follow_up_secret="s3cret-long-enough")
    assert r.status_code == 201
    assert r.json()["id"] is None
    row = db.scalar(select(Incident))
    assert row.reporter_id is None
    log = db.scalar(select(AuditLog).where(AuditLog.action == "incident.created"))
    assert log.actor_id is None and log.ip is None
    # در «گزارش‌های من» هم دیده نمی‌شود
    assert client.get("/api/v1/incidents/mine", headers=auth(client, STAFF)).json() == []
    # کارشناس ایمنی گزارش را می‌بیند ولی نام گزارش‌دهنده ندارد
    detail = client.get(f"/api/v1/incidents/{row.id}", headers=auth(client, SAFETY)).json()
    assert detail["reporter_name"] is None and detail["is_anonymous"] is True


def test_anonymous_requires_secret_and_tracking(client, db):
    r, _ = report(client, db, is_anonymous=True)
    assert r.status_code == 400
    r, _ = report(client, db, is_anonymous=True, follow_up_secret="s3cret-long-enough")
    code = r.json()["tracking_code"]
    ok = client.post(
        "/api/v1/public/incidents/track",
        json={"tracking_code": code, "follow_up_secret": "s3cret-long-enough"},
    )
    assert ok.status_code == 200 and ok.json()["status"] == "submitted"
    bad = client.post(
        "/api/v1/public/incidents/track", json={"tracking_code": code, "follow_up_secret": "wrong-secret"}
    )
    assert bad.status_code == 404


def test_patient_safety_sees_sensitive_and_reporter(client, db):
    r, _ = report(client, db)
    detail = client.get(f"/api/v1/incidents/{r.json()['id']}", headers=auth(client, SAFETY)).json()
    assert detail["sensitive_answers"]["patient_name"] == "علی محمدی"
    assert detail["reporter_name"] == "کارمند نمونه (پرستار اورژانس)"
    assert db.query(AuditLog).filter(AuditLog.action == "incident.viewed").count() == 1


def test_unit_manager_scope(client, db):
    in_unit, _ = report(client, db, unit="اورژانس")
    other, _ = report(client, db, unit="ICU")
    h = auth(client, UNIT_MANAGER)
    listed = client.get("/api/v1/incidents", headers=h).json()
    assert [i["tracking_code"] for i in listed["items"]] == [in_unit.json()["tracking_code"]]
    detail = client.get(f"/api/v1/incidents/{in_unit.json()['id']}", headers=h).json()
    # مسئول واحد اطلاعات بیمار و نام گزارش‌دهنده را نمی‌بیند
    assert detail["sensitive_answers"] is None and detail["reporter_name"] is None
    assert client.get(f"/api/v1/incidents/{other.json()['id']}", headers=h).status_code == 404
    # و اجازه تغییر وضعیت ندارد
    r = client.post(
        f"/api/v1/incidents/{in_unit.json()['id']}/status", headers=h, json={"status": "under_review"}
    )
    assert r.status_code == 403


def test_reporter_sees_own_without_sensitive(client, db):
    r, _ = report(client, db)
    detail = client.get(f"/api/v1/incidents/{r.json()['id']}", headers=auth(client, STAFF)).json()
    assert detail["answers"]["description"] and detail["sensitive_answers"] is None


def test_sysadmin_cannot_read_incidents(client, db):
    r, _ = report(client, db)
    h = auth(client, SYSADMIN)
    assert client.get("/api/v1/incidents", headers=h).status_code == 403
    assert client.get(f"/api/v1/incidents/{r.json()['id']}", headers=h).status_code == 404


def test_workflow_assign_action_close(client, db):
    r, _ = report(client, db)
    iid = r.json()["id"]
    h = auth(client, SAFETY)
    assert (
        client.post(f"/api/v1/incidents/{iid}/status", headers=h, json={"status": "closed"}).status_code
        == 409
    )
    a = client.post(f"/api/v1/incidents/{iid}/assign", headers=h, json={"unit_id": unit_id(db, "اورژانس")})
    assert a.json()["status"] == "assigned"
    assert (
        client.post(f"/api/v1/incidents/{iid}/status", headers=h, json={"status": "in_progress"}).status_code
        == 200
    )
    act = client.post(
        f"/api/v1/incidents/{iid}/actions",
        headers=h,
        json={"description": "نصب نرده کنار تخت", "owner_name": "سرپرستار"},
    )
    assert act.status_code == 201
    done = client.patch(f"/api/v1/incidents/{iid}/actions/{act.json()['id']}", headers=h, json={"done": True})
    assert done.json()["done_at"] is not None
    for s in ("action_taken", "closed"):
        assert (
            client.post(f"/api/v1/incidents/{iid}/status", headers=h, json={"status": s}).status_code == 200
        )
    detail = client.get(f"/api/v1/incidents/{iid}", headers=h).json()
    assert [x["to_status"] for x in detail["history"]] == [
        "submitted",
        "assigned",
        "in_progress",
        "action_taken",
        "closed",
    ]
    summary = client.get("/api/v1/dashboard/incidents/summary", headers=h).json()
    assert summary["total"] == 1 and summary["by_status"] == {"closed": 1}
    assert summary["avg_hours_to_close"] is not None


def test_detail_tells_app_what_it_may_do(client, db):
    r, _ = report(client, db)
    iid = r.json()["id"]
    url = f"/api/v1/incidents/{iid}"
    safety = client.get(url, headers=auth(client, SAFETY)).json()
    assert safety["can_manage"] is True
    assert safety["next_statuses"] == ["assigned", "rejected", "under_review"]
    for code in (UNIT_MANAGER, STAFF):
        d = client.get(url, headers=auth(client, code)).json()
        assert d["can_manage"] is False and d["next_statuses"] == []
    h = auth(client, SAFETY)
    client.post(f"{url}/assign", headers=h, json={"unit_id": unit_id(db, "ICU")})
    d = client.get(url, headers=h).json()
    assert d["assigned_unit_name"] == "ICU"
    assert d["next_statuses"] == ["in_progress", "under_review"]


def test_form_endpoint(client):
    form = client.get("/api/v1/forms/incident", headers=auth(client, STAFF)).json()
    assert form["version"] == 1 and form["sections"]
