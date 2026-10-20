from functools import lru_cache

from cryptography.fernet import Fernet
from pydantic_settings import BaseSettings, SettingsConfigDict

_DEV_JWT_SECRET = "dev-only-change-me-dev-only-change-me"
# کلید Fernet فقط برای توسعه؛ در محیط عملیاتی باید از متغیر محیطی خوانده شود.
_DEV_DATA_KEY = "ZGV2LW9ubHktZGF0YS1rZXktMzItYnl0ZXMtbG9uZyE="


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="RAZI_", env_file=".env", extra="ignore")

    env: str = "development"
    database_url: str = "postgresql+psycopg://razi:razi@localhost:5432/razi"
    jwt_secret: str = _DEV_JWT_SECRET
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 15
    refresh_token_days: int = 30
    data_encryption_key: str = _DEV_DATA_KEY
    cors_origins: list[str] = []
    max_failed_logins: int = 5
    lockout_minutes: int = 15
    bootstrap_admin_code: str | None = None
    bootstrap_admin_password: str | None = None
    # فقط برای env=staging: رمز کاربران نمایشی (به جای رمز پیش‌فرض عمومی)
    demo_password: str | None = None
    # فایل‌های آموزشی؛ مسیر نسبی نسبت به پوشه اجرای سرور (backend)
    media_dir: str = "media"
    max_upload_mb: int = 500
    # ورود مراجعان با کد پیامکی
    otp_ttl_seconds: int = 120
    otp_resend_seconds: int = 60
    otp_max_per_hour: int = 5
    otp_max_attempts: int = 5
    # console: کد فقط در لاگ سرور نوشته می‌شود (توسعه). kavenegar: ارسال با سامانه کاوه‌نگار (قالب verify/lookup).
    sms_provider: str = "console"
    sms_api_key: str | None = None
    sms_template: str | None = None
    # کد در پاسخ API هم برگردانده شود؛ فقط برای سرور آزمایشی. در production نادیده گرفته می‌شود.
    otp_debug: bool = False

    @property
    def otp_debug_enabled(self) -> bool:
        return self.env in ("development", "test") or (self.otp_debug and self.env != "production")

    @property
    def is_exposed(self) -> bool:
        """سرورهایی که از اینترنت در دسترس‌اند: staging (آزمایشی) و production (عملیاتی)."""
        return self.env in ("staging", "production")

    def check_production(self) -> None:
        if not self.is_exposed:
            return
        if self.jwt_secret == _DEV_JWT_SECRET or len(self.jwt_secret) < 32:
            raise RuntimeError("RAZI_JWT_SECRET باید تنظیم شود (حداقل ۳۲ نویسه)")
        if self.data_encryption_key == _DEV_DATA_KEY:
            raise RuntimeError("RAZI_DATA_ENCRYPTION_KEY باید تنظیم شود")
        try:
            Fernet(self.data_encryption_key.encode())
        except ValueError:
            raise RuntimeError("RAZI_DATA_ENCRYPTION_KEY کلید Fernet معتبر نیست") from None
        if self.env == "production" and self.sms_provider == "console":
            raise RuntimeError("RAZI_SMS_PROVIDER باید برای ارسال کد ورود مراجعان تنظیم شود")
        if self.sms_provider == "kavenegar" and not (self.sms_api_key and self.sms_template):
            raise RuntimeError("RAZI_SMS_API_KEY و RAZI_SMS_TEMPLATE برای کاوه‌نگار لازم است")


@lru_cache
def get_settings() -> Settings:
    return Settings()
