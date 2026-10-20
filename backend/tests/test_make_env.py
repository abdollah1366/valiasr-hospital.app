from app.core.config import Settings
from scripts.make_staging_env import build


def test_generated_env_passes_staging_checks(tmp_path, monkeypatch):
    # متغیرهای محیطیِ تست بر فایل .env اولویت دارند؛ برای این آزمون برداشته می‌شوند.
    monkeypatch.delenv("RAZI_ENV")
    monkeypatch.delenv("RAZI_DATABASE_URL", raising=False)
    content, db_password, demo_password = build()
    f = tmp_path / ".env"
    f.write_text(content, encoding="ascii")
    s = Settings(_env_file=f)
    assert s.env == "staging"
    s.check_production()
    assert db_password in s.database_url
    assert s.demo_password == demo_password and len(demo_password) >= 10
