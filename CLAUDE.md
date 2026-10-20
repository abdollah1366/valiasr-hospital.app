# راهنمای توسعه (برای Claude و توسعه‌دهندگان)

- زبان گفتگو با مالک پروژه: فارسی. رابط کاربری کاملاً فارسی و راست‌چین.
- هیچ ادعایی درباره build یا تست بدون اجرای واقعی نکنید. در محیط ابری Claude، دانلود Android SDK (dl.google.com) بسته است؛ build اندروید در GitHub Actions اجرا می‌شود.
- تغییرات کوچک و مرحله‌ای؛ قبل از تغییرات بزرگ تأیید مالک لازم است.

## دستورها

- بک‌اند: `cd backend && pip install -e ".[dev]" && ruff check . && ruff format --check . && pytest -q` (PostgreSQL در `RAZI_DATABASE_URL`، پیش‌فرض `razi_test` روی localhost)
- اپ: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- مایگریشن جدید: `cd backend && alembic revision --autogenerate -m "..."` و بررسی دستی فایل تولیدشده

## قواعد

- دسترسی‌ها فقط در سرور کنترل می‌شوند (`app/core/permissions.py`، `require(...)`). مدیر سیستم عمداً به محتوای گزارش خطا دسترسی ندارد.
- اطلاعات هویتی بیمار فقط در فیلدهای `sensitive` فرم و رمزنگاری‌شده ذخیره می‌شود. هرگز در لاگ یا لاگ حسابرسی ننویسید.
- گزارش ناشناس هیچ پیوندی به کاربر ندارد (نه در incidents، نه در audit_logs).
- منطق فرم در `backend/app/services/forms.py` و `app/.../core/form/FormLogic.kt` باید یکسان بماند؛ تست‌های هر دو طرف همان سناریوها را دارند.
- هر تغییر در مدل‌ها با مایگریشن Alembic همراه باشد.
- APK، فایل‌های keystore و `.env` commit نشوند.
