# سامانه جامع بیمارستان بین‌المللی ولیعصر تبریز

اپلیکیشن اندروید (Kotlin + Jetpack Compose) و بک‌اند (FastAPI + PostgreSQL)، فارسی و راست‌چین.
طرح کامل و نقشه راه: [docs/execution-plan.md](docs/execution-plan.md)

## وضعیت فعلی (نسخه 0.1.0)

| بخش | آماده است |
|---|---|
| ورود کارکنان | ورود با کد پرسنلی، تغییر اجباری رمز موقت، قفل پس از ۵ تلاش ناموفق، نشست امن با تمدید خودکار |
| نقش‌ها و دسترسی | ۷ نقش پیش‌فرض، دسترسی محدود به واحد، کنترل همه‌چیز در سرور، لاگ حسابرسی |
| گزارش خطا | خطا، حادثه، شبه‌خطا و تجهیزات؛ فرم شرطی (فقط سؤال‌های مرتبط)، ارسال ناشناس با کد و رمز پیگیری، ذخیره آفلاین و ارسال خودکار، رمزنگاری اطلاعات هویتی بیمار، گردش کار رسیدگی و اقدام اصلاحی (API) |
| اطلاعیه‌ها | اخبار و بخشنامه با هدف‌گذاری نقش/واحد و «تأیید مطالعه» |
| بخش عمومی | راهنمای بخش‌ها، پرسش‌های متداول، اطلاعیه‌ها، ثبت و پیگیری پیشنهاد/شکایت |
| داشبورد | آمار تجمیعی گزارش‌ها (API) |

هنوز ساخته نشده: پنل مدیریت وب (فعلاً مدیریت از طریق API و صفحه `/docs`)، کارتابل رسیدگی در اپ، درخواست‌های پشتیبانی، آموزش و آزمون، پژوهش، گفتگو، Push Notification.

## باز کردن اپ در Android Studio

1. در Android Studio: **File → Open** و پوشه اصلی همین مخزن (همان‌جا که `settings.gradle.kts` هست) را انتخاب کنید.
2. صبر کنید Gradle Sync تمام شود (بار اول کتابخانه‌ها دانلود می‌شوند). نسخه پیشنهادی Android Studio: Ladybug (2024.2) یا جدیدتر؛ JDK 17.
3. اپ را روی شبیه‌ساز یا گوشی اجرا کنید (پیکربندی `app`).

نسخه debug به آدرس `http://10.0.2.2:8000` وصل می‌شود که از داخل **شبیه‌ساز** همان کامپیوتر خودتان است.
برای گوشی واقعی: در صفحه «ورود کارکنان» گزینه «تغییر آدرس سرور» را بزنید و IP کامپیوتر را وارد کنید (مثلاً `http://192.168.1.10:8000`). گوشی و کامپیوتر باید در یک شبکه باشند.
این گزینه در نسخه release وجود ندارد و نسخه release فقط با HTTPS کار می‌کند.

## اجرای بک‌اند روی کامپیوتر

ساده‌ترین راه با Docker:

```bash
docker compose up --build
```

سپس `http://localhost:8000/docs` را باز کنید. پنل مدیریت وب در `http://localhost:8000/admin` است (کاربران و واحدها با 1005، اطلاعیه‌ها و پرسش‌ها و پیشنهادها با 1004، داشبورد با 1003). داده نمایشی ساخته می‌شود:

| کد پرسنلی | نقش | رمز |
|---|---|---|
| 1001 | کارمند (پرستار اورژانس) | `Razi1234` |
| 1002 | مسئول واحد اورژانس | `Razi1234` |
| 1003 | کارشناس ایمنی بیمار | `Razi1234` |
| 1004 | مدیر محتوا | `Razi1234` |
| 1005 | مدیر سیستم و حسابرس | `Razi1234` |
| 1006 | مسئول آموزش | `Razi1234` |

بدون Docker (نیاز به Python 3.11+ و PostgreSQL):

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env        # آدرس پایگاه داده را تنظیم کنید
alembic upgrade head
python -m app.seed --demo
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## سرور آزمایشی روی اینترنت (برای گوشی)

اگر سرور Docker ندارد (مثلاً VPS ویندوزی بدون مجازی‌سازی)، راهنمای [docs/windows-vps.md](docs/windows-vps.md) را ببینید. روش Docker:

برای وقتی که گوشی با اینترنت موبایل یا از بیرون به سرور وصل می‌شود. **فقط داده ساختگی** وارد کنید؛ ارتباط HTTP است و رمزنگاری نمی‌شود.

۱. ساخت رمزهای تصادفی (در PowerShell، داخل پوشه پروژه):

```powershell
docker run --rm python:3.12-slim python -c "import secrets,base64,os;print('POSTGRES_PASSWORD='+secrets.token_hex(16));print('RAZI_JWT_SECRET='+secrets.token_hex(32));print('RAZI_DATA_ENCRYPTION_KEY='+base64.urlsafe_b64encode(os.urandom(32)).decode());print('RAZI_DEMO_PASSWORD=Rz'+secrets.token_hex(5))" | Out-File -Encoding ascii .env.public
```

فایل `.env.public` در git ثبت نمی‌شود (نمونه: `.env.public.example`). رمز ورود کاربران 1001 تا 1006 همان `RAZI_DEMO_PASSWORD` داخل این فایل است.

۲. اجرا:

```powershell
docker compose -f docker-compose.public.yml --env-file .env.public up -d --build
```

در این حالت (`RAZI_ENV=staging`): سرور بدون رمزهای واقعی بالا نمی‌آید، صفحه `/docs` بسته است، پورت پایگاه داده بیرون داده نمی‌شود و رمز `Razi1234` کار نمی‌کند. بررسی سلامت: `http://IP:8000/health`.

۳. در مودم، پورت 8000 را به IP داخلی سرور forward کنید و در فایروال ویندوز پورت 8000 (TCP) را باز کنید.

۴. در اپ (نسخه debug) در صفحه ورود «تغییر آدرس سرور» را بزنید و `http://IP-عمومی:8000` را وارد کنید.

## تست‌ها

```bash
cd backend && pytest            # نیاز به PostgreSQL؛ پایگاه razi_test
./gradlew :app:testDebugUnitTest # تست‌های اپ
```

با هر push، GitHub Actions هر دو را اجرا می‌کند و فایل APK نسخه debug را در بخش Artifacts همان اجرا قرار می‌دهد.

## ساختار

```
app/        اپ اندروید (core: شبکه، داده، فرم، تاریخ شمسی | feature: صفحه‌ها | ui: تم و اجزای مشترک)
backend/    FastAPI (routers، models، services/forms.py، alembic، tests)
docs/       طرح اجرایی
```

فرم گزارش خطا از `backend/app/data/incident_form.json` ساخته می‌شود و یک کپی از آن در `app/src/main/assets/forms/` برای حالت آفلاین هست. اگر فرم را عوض کردید، هر دو را یکسان نگه دارید (تست `FormLogicTest` یکسان بودن را بررسی می‌کند).

## نکات امنیتی پیش از استقرار واقعی

- `RAZI_JWT_SECRET` و `RAZI_DATA_ENCRYPTION_KEY` را تنظیم کنید؛ با `RAZI_ENV=production` یا `staging` سرور بدون آن‌ها اجرا نمی‌شود.
- سرور را پشت Nginx با گواهی TLS قرار دهید و آدرس آن را هنگام build با `-Prazi.apiBaseUrl=https://.../api/v1/` بدهید.
- داده نمایشی (`--demo`) را در محیط عملیاتی اجرا نکنید.
