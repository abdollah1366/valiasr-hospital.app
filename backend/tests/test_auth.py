from app.models import AuditLog
from tests.conftest import STAFF, SYSADMIN, auth, login


def test_login_and_me(client):
    tokens = login(client, STAFF)
    r = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {tokens['access_token']}"})
    assert r.status_code == 200
    me = r.json()
    assert me["personnel_code"] == STAFF
    assert me["roles"] == ["staff"]
    assert "incident.create" in me["permissions"]
    assert "incident.read" not in me["permissions"]


def test_wrong_password_and_unknown_user_give_same_error(client):
    a = client.post("/api/v1/auth/login", json={"personnel_code": STAFF, "password": "wrong"})
    b = client.post("/api/v1/auth/login", json={"personnel_code": "nope", "password": "wrong"})
    assert a.status_code == b.status_code == 401
    assert a.json()["detail"]["code"] == b.json()["detail"]["code"] == "invalid_credentials"


def test_lockout_after_failed_attempts(client):
    for _ in range(5):
        client.post("/api/v1/auth/login", json={"personnel_code": STAFF, "password": "wrong"})
    r = client.post("/api/v1/auth/login", json={"personnel_code": STAFF, "password": "Razi1234"})
    assert r.status_code == 423


def test_no_token_and_bad_token(client):
    assert client.get("/api/v1/auth/me").status_code == 401
    r = client.get("/api/v1/auth/me", headers={"Authorization": "Bearer garbage"})
    assert r.status_code == 401


def test_refresh_rotation_and_reuse_detection(client):
    tokens = login(client, STAFF)
    r1 = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r1.status_code == 200
    new = r1.json()
    # استفاده دوباره از توکن قدیمی رد می‌شود و توکن جدید همان زنجیره هم باطل می‌شود
    assert (
        client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}).status_code
        == 401
    )
    assert (
        client.post("/api/v1/auth/refresh", json={"refresh_token": new["refresh_token"]}).status_code == 401
    )


def test_logout_revokes_refresh(client):
    tokens = login(client, STAFF)
    assert (
        client.post("/api/v1/auth/logout", json={"refresh_token": tokens["refresh_token"]}).status_code == 204
    )
    assert (
        client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}).status_code
        == 401
    )


def test_new_user_must_change_password(client):
    admin = auth(client, SYSADMIN)
    r = client.post(
        "/api/v1/admin/users",
        headers=admin,
        json={
            "personnel_code": "2001",
            "full_name": "کاربر جدید",
            "temporary_password": "Temp12345",
            "roles": [{"role": "staff"}],
        },
    )
    assert r.status_code == 201, r.text
    tokens = login(client, "2001", "Temp12345")
    assert tokens["must_change_password"] is True
    h = {"Authorization": f"Bearer {tokens['access_token']}"}
    # تا رمز عوض نشود، endpointهای دیگر بسته‌اند
    r = client.get("/api/v1/incidents/mine", headers=h)
    assert r.status_code == 403 and r.json()["detail"]["code"] == "password_change_required"
    weak = client.post(
        "/api/v1/auth/change-password",
        headers=h,
        json={"current_password": "Temp12345", "new_password": "12345678"},
    )
    assert weak.status_code == 400
    ok = client.post(
        "/api/v1/auth/change-password",
        headers=h,
        json={"current_password": "Temp12345", "new_password": "NewPass123"},
    )
    assert ok.status_code == 204
    assert client.get("/api/v1/incidents/mine", headers=h).status_code == 200


def test_duplicate_personnel_code(client):
    admin = auth(client, SYSADMIN)
    body = {"personnel_code": STAFF, "full_name": "x", "temporary_password": "Temp12345"}
    assert client.post("/api/v1/admin/users", headers=admin, json=body).status_code == 409


def test_staff_cannot_manage_users(client):
    r = client.get("/api/v1/admin/users", headers=auth(client, STAFF))
    assert r.status_code == 403


def test_login_is_audited(client, db):
    login(client, STAFF)
    assert db.query(AuditLog).filter(AuditLog.action == "auth.login").count() == 1
