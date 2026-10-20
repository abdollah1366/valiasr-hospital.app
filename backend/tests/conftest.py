import os

os.environ.setdefault("RAZI_DATABASE_URL", "postgresql+psycopg://razi:razi@localhost:5432/razi_test")
os.environ["RAZI_ENV"] = "test"
os.environ["RAZI_MEDIA_DIR"] = __import__("tempfile").mkdtemp(prefix="razi-media-")

from pathlib import Path  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import text  # noqa: E402

from alembic import command  # noqa: E402
from alembic.config import Config  # noqa: E402
from app import seed  # noqa: E402
from app.core.db import Base, SessionLocal, engine  # noqa: E402
from app.main import app  # noqa: E402

BACKEND = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="session", autouse=True)
def _schema():
    # جدول‌ها با همان مایگریشن عملیاتی ساخته می‌شوند تا خود مایگریشن هم آزموده شود.
    with engine.begin() as conn:
        conn.execute(text("DROP SCHEMA public CASCADE; CREATE SCHEMA public"))
    cfg = Config(str(BACKEND / "alembic.ini"))
    cfg.set_main_option("script_location", str(BACKEND / "alembic"))
    command.upgrade(cfg, "head")
    yield


@pytest.fixture(autouse=True)
def _clean_db():
    tables = ", ".join(t.name for t in Base.metadata.sorted_tables)
    with engine.begin() as conn:
        conn.execute(text(f"TRUNCATE {tables} CASCADE"))
    seed.run(demo=True)
    yield


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def db():
    with SessionLocal() as s:
        yield s


def login(client, code: str, password: str = seed.DEMO_PASSWORD) -> dict:
    r = client.post("/api/v1/auth/login", json={"personnel_code": code, "password": password})
    assert r.status_code == 200, r.text
    return r.json()


def auth(client, code: str) -> dict:
    return {"Authorization": f"Bearer {login(client, code)['access_token']}"}


STAFF, UNIT_MANAGER, SAFETY, CONTENT, SYSADMIN, TRAINER = "1001", "1002", "1003", "1004", "1005", "1006"
