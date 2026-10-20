import base64

import pytest
from cryptography.fernet import Fernet
from sqlalchemy import text

from app import main, seed
from app.core.config import Settings
from app.core.db import Base, SessionLocal, engine
from tests.conftest import STAFF

JWT = "x" * 40
KEY = Fernet.generate_key().decode()


def staging(**kw) -> Settings:
    return Settings(env="staging", **{"jwt_secret": JWT, "data_encryption_key": KEY, **kw})


def test_staging_requires_real_secrets():
    staging().check_production()
    with pytest.raises(RuntimeError, match="RAZI_JWT_SECRET"):
        Settings(env="staging", data_encryption_key=KEY).check_production()
    with pytest.raises(RuntimeError, match="RAZI_JWT_SECRET"):
        staging(jwt_secret="short").check_production()
    with pytest.raises(RuntimeError, match="RAZI_DATA_ENCRYPTION_KEY"):
        Settings(env="staging", jwt_secret=JWT).check_production()
    with pytest.raises(RuntimeError, match="Fernet"):
        staging(data_encryption_key="not-a-key").check_production()
    # کلید ساخته‌شده با base64 استاندارد ۳۲ بایت (همان که راهنمای README می‌سازد) معتبر است.
    staging(data_encryption_key=base64.urlsafe_b64encode(b"k" * 32).decode()).check_production()


def test_docs_closed_on_exposed_server(monkeypatch):
    monkeypatch.setattr(main, "get_settings", lambda: staging())
    paths = {r.path for r in main.create_app().routes}
    assert "/docs" not in paths


def _empty_db():
    tables = ", ".join(t.name for t in Base.metadata.sorted_tables)
    with engine.begin() as conn:
        conn.execute(text(f"TRUNCATE {tables} CASCADE"))


@pytest.mark.parametrize("pw", [None, "short", seed.DEMO_PASSWORD])
def test_staging_demo_rejects_public_or_weak_password(monkeypatch, pw):
    monkeypatch.setattr(seed, "get_settings", lambda: staging(demo_password=pw))
    _empty_db()
    with SessionLocal() as db, pytest.raises(RuntimeError, match="RAZI_DEMO_PASSWORD"):
        seed.seed_demo(db)


def test_staging_demo_uses_configured_password(monkeypatch, client):
    monkeypatch.setattr(seed, "get_settings", lambda: staging(demo_password="Test-Staging-77"))
    _empty_db()
    with SessionLocal() as db:
        seed.seed_roles(db)
        seed.seed_demo(db)
        db.commit()
    url = "/api/v1/auth/login"
    assert client.post(url, json={"personnel_code": STAFF, "password": seed.DEMO_PASSWORD}).status_code != 200
    r = client.post(url, json={"personnel_code": STAFF, "password": "Test-Staging-77"})
    assert r.status_code == 200
