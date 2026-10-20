from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import update

from app.core.config import Settings
from app.models import OtpCode
from app.services.phone import language_for_mobile, normalize_mobile
from tests.conftest import auth

API = "/api/v1/patient"


def request_otp(client, mobile="09121234567"):
    r = client.post(f"{API}/auth/otp", json={"mobile": mobile})
    assert r.status_code == 202, r.text
    return r.json()


def sign_in(client, mobile="09121234567", **extra):
    code = request_otp(client, mobile)["debug_code"]
    r = client.post(f"{API}/auth/verify", json={"mobile": mobile, "code": code, **extra})
    assert r.status_code == 200, r.text
    return r.json()


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("09121234567", "+989121234567"),
        ("۰۹۱۲ ۱۲۳ ۴۵۶۷", "+989121234567"),
        ("+98 912 123 4567", "+989121234567"),
        ("0098-912-123-4567", "+989121234567"),
        ("+994 50 123 45 67", "+994501234567"),
        ("+90 532 123 45 67", "+905321234567"),
        ("+98411234567", None),  # تلفن ثابت ایران
        ("12345", None),
        ("+0123456789", None),
    ],
)
def test_normalize_mobile(raw, expected):
    assert normalize_mobile(raw) == expected


def test_language_from_prefix_matches_app():
    assert language_for_mobile("+989121234567") == "fa"
    assert language_for_mobile("+994501234567") == "az"
    assert language_for_mobile("+905321234567") == "tr"
    assert language_for_mobile("+9647701234567") == "ar"
    assert language_for_mobile("+201001234567") == "ar"
    assert language_for_mobile("+37491123456") == "en"


def test_sign_up_with_otp_sets_language_from_prefix(client):
    sent = request_otp(client, "+994 50 123 45 67")
    assert sent["mobile"] == "+994501234567"
    assert sent["suggested_language"] == "az"
    assert len(sent["debug_code"]) == 6

    r = client.post(f"{API}/auth/verify", json={"mobile": "00994501234567", "code": sent["debug_code"]})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["is_new"] is True
    assert body["patient"]["language"] == "az"

    me = client.get(f"{API}/me", headers={"Authorization": f"Bearer {body['access_token']}"})
    assert me.json()["mobile"] == "+994501234567"


def test_second_sign_in_keeps_account_and_can_change_language(client, monkeypatch):
    first = sign_in(client)
    monkeypatch.setattr("app.routers.patient.get_settings", lambda: Settings(otp_resend_seconds=0))
    second = sign_in(client, language="en")
    assert second["is_new"] is False
    assert second["patient"]["id"] == first["patient"]["id"]
    assert second["patient"]["language"] == "en"

    h = {"Authorization": f"Bearer {second['access_token']}"}
    r = client.patch(f"{API}/me", json={"language": "ar", "full_name": " Ali "}, headers=h)
    assert r.json()["language"] == "ar" and r.json()["full_name"] == "Ali"
    assert client.patch(f"{API}/me", json={"language": "de"}, headers=h).status_code == 422


def test_wrong_code_and_attempt_limit(client):
    sent = request_otp(client)
    wrong = "000000" if sent["debug_code"] != "000000" else "111111"
    for _ in range(4):
        r = client.post(f"{API}/auth/verify", json={"mobile": "09121234567", "code": wrong})
        assert r.status_code == 400
        assert r.json()["detail"]["code"] == "otp_invalid"
    # پنجمین تلاش اشتباه کد را باطل می‌کند؛ حتی کد درست دیگر پذیرفته نمی‌شود.
    assert client.post(f"{API}/auth/verify", json={"mobile": "09121234567", "code": wrong}).status_code == 400
    r = client.post(f"{API}/auth/verify", json={"mobile": "09121234567", "code": sent["debug_code"]})
    assert r.status_code == 400


def test_code_is_single_use_and_expires(client, db):
    code = request_otp(client)["debug_code"]
    assert client.post(f"{API}/auth/verify", json={"mobile": "09121234567", "code": code}).status_code == 200
    assert client.post(f"{API}/auth/verify", json={"mobile": "09121234567", "code": code}).status_code == 400

    db.execute(update(OtpCode).values(created_at=datetime.now(UTC) - timedelta(minutes=5)))
    db.commit()
    code = request_otp(client)["debug_code"]
    db.execute(update(OtpCode).values(expires_at=datetime.now(UTC) - timedelta(seconds=1)))
    db.commit()
    assert client.post(f"{API}/auth/verify", json={"mobile": "09121234567", "code": code}).status_code == 400


def test_resend_and_hourly_limits(client, db):
    request_otp(client)
    r = client.post(f"{API}/auth/otp", json={"mobile": "09121234567"})
    assert r.status_code == 429 and r.json()["detail"]["code"] == "otp_too_soon"
    for _ in range(4):
        db.execute(update(OtpCode).values(created_at=OtpCode.created_at - timedelta(minutes=2)))
        db.commit()
        request_otp(client)
    db.execute(update(OtpCode).values(created_at=OtpCode.created_at - timedelta(minutes=2)))
    db.commit()
    r = client.post(f"{API}/auth/otp", json={"mobile": "09121234567"})
    assert r.status_code == 429 and r.json()["detail"]["code"] == "otp_limit"


def test_invalid_mobile_rejected(client):
    r = client.post(f"{API}/auth/otp", json={"mobile": "02133334444"})
    assert r.status_code == 422 and r.json()["detail"]["code"] == "invalid_mobile"


def test_refresh_rotation_and_logout(client):
    t = sign_in(client)
    r = client.post(f"{API}/auth/refresh", json={"refresh_token": t["refresh_token"]})
    assert r.status_code == 200
    new = r.json()
    assert new["must_change_password"] is False
    # استفاده دوباره از توکن مصرف‌شده کل زنجیره را باطل می‌کند.
    assert client.post(f"{API}/auth/refresh", json={"refresh_token": t["refresh_token"]}).status_code == 401
    assert client.post(f"{API}/auth/refresh", json={"refresh_token": new["refresh_token"]}).status_code == 401

    t = sign_in(client, "+905321234567")
    assert client.post(f"{API}/auth/logout", json={"refresh_token": t["refresh_token"]}).status_code == 204
    assert client.post(f"{API}/auth/refresh", json={"refresh_token": t["refresh_token"]}).status_code == 401


def test_patient_and_staff_tokens_are_not_interchangeable(client):
    patient = {"Authorization": f"Bearer {sign_in(client)['access_token']}"}
    assert client.get("/api/v1/auth/me", headers=patient).status_code == 401
    assert client.get(f"{API}/me", headers=auth(client, "1001")).status_code == 401
    assert client.get(f"{API}/me").status_code == 401


def test_fixed_test_code_skips_sms(client, monkeypatch):
    sent = []
    monkeypatch.setattr("app.routers.patient.sms.send_otp", lambda m, c: sent.append(c))
    monkeypatch.setattr("app.routers.patient.get_settings", lambda: Settings(otp_fixed_code="1234"))
    request_otp(client)
    assert sent == []
    r = client.post(f"{API}/auth/verify", json={"mobile": "09121234567", "code": "۱۲۳۴"})
    assert r.status_code == 200, r.text
    monkeypatch.setattr(
        "app.routers.patient.get_settings",
        lambda: Settings(env="production", otp_fixed_code="1234", otp_resend_seconds=0),
    )
    assert request_otp(client)["debug_code"] is None
    # در production کد ثابت نادیده گرفته می‌شود و کد تصادفی پیامک می‌شود.
    assert len(sent) == 1 and len(sent[0]) == 6


def test_production_requires_sms_provider():
    key = "ZGV2LW9ubHktZGF0YS1rZXktMzItYnl0ZXMtbG9uZyE="
    base = {"jwt_secret": "x" * 40, "data_encryption_key": key.replace("ZGV2", "cHJv")}
    with pytest.raises(RuntimeError, match="OTP_FIXED_CODE"):
        Settings(env="production", otp_fixed_code="1234", sms_provider="kavenegar", **base).check_production()
    with pytest.raises(RuntimeError, match="SMS_PROVIDER"):
        Settings(env="production", **base).check_production()
    with pytest.raises(RuntimeError, match="SMS_API_KEY"):
        Settings(env="production", sms_provider="kavenegar", **base).check_production()
    Settings(
        env="production", sms_provider="kavenegar", sms_api_key="k", sms_template="otp", **base
    ).check_production()
    assert not Settings(env="production", otp_debug=True).otp_debug_enabled
    assert Settings(env="staging", otp_debug=True).otp_debug_enabled
    assert not Settings(env="staging").otp_debug_enabled
