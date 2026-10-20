# اجرای سرور آزمایشی روی VPS ویندوزی (بدون Docker)

برای VPSهایی که مجازی‌سازی ندارند و Docker Desktop روی آن‌ها نصب نمی‌شود.
**فقط داده ساختگی** وارد کنید؛ ارتباط HTTP است و رمزنگاری نمی‌شود.

## ۱. نصب پیش‌نیازها (یک بار)

- **Python 3.11 یا 3.12** از python.org (با 3.14 بسته psycopg نصب نمی‌شود). اگر نسخه دیگری هم نصب است اشکالی ندارد؛ در مرحله ۲ با `py -3.11` یا `py -3.12` انتخاب می‌شود (فهرست نسخه‌ها: `py -0`).
- **PostgreSQL 16** از سایت EDB (نصب‌کننده ویندوز). برای کاربر `postgres` یک رمز بگذارید و یادداشت کنید. Stack Builder لازم نیست.
- کد پروژه: در صفحه GitHub مخزن، Code ← Download ZIP، و آن را در `C:\razi` باز کنید (طوری که `C:\razi\backend` وجود داشته باشد).

## ۲. نصب بک‌اند و ساخت رمزها

در PowerShell عادی:

```powershell
cd C:\razi\backend
py -3.11 -m venv .venv
.venv\Scripts\python -m pip install .
.venv\Scripts\python scripts\make_staging_env.py
```

دستور آخر فایل `backend\.env` را با رمزهای تصادفی می‌سازد و دو چیز چاپ می‌کند: دو خط SQL برای ساخت پایگاه داده، و رمز ورود کاربران نمایشی. خروجی را نگه دارید.

## ۳. ساخت پایگاه داده

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres
```

رمز کاربر `postgres` را بدهید، دو خط `CREATE ...` چاپ‌شده در مرحله قبل را paste کنید، سپس `\q`.

## ۴. ساخت جدول‌ها و داده نمایشی

```powershell
.venv\Scripts\alembic upgrade head
.venv\Scripts\python -m app.seed --demo
```

## ۵. اجرای سرور

```powershell
.venv\Scripts\uvicorn app.main:app --host 0.0.0.0 --port 8000
```

تا وقتی این پنجره باز است سرور کار می‌کند.

## ۶. باز کردن پورت 8000

در PowerShell **Administrator**:

```powershell
New-NetFirewallRule -DisplayName "Razi API 8000" -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow
```

اگر پنل شرکت VPS فایروال جداگانه دارد، پورت 8000 (TCP) را آنجا هم باز کنید. پورت 5432 پایگاه داده را **باز نکنید**.

## ۷. آزمایش

- در مرورگر گوشی: `http://IP-سرور:8000/health` باید `{"status":"ok"}` نشان دهد.
- در اپ (نسخه debug) در صفحه ورود «تغییر آدرس سرور» ← `http://IP-سرور:8000`، سپس ورود با کد 1001 و رمز نمایشی مرحله ۲.
- پنل مدیریت وب در مرورگر: `http://IP-سرور:8000/admin` (کاربران و واحدها با 1005، اطلاعیه‌ها با 1004، داشبورد با 1003). اتصال HTTP رمزنگاری نشده است؛ فقط با داده آزمایشی کار کنید.

## ۸. اجرای خودکار با روشن شدن سرور

برای اینکه بعد از ری‌استارت VPS لازم نباشد سرور را دستی اجرا کنید، یک بار PowerShell را با **Run as administrator** باز کنید. اگر سرور در پنجره دیگری در حال اجراست، اول آنجا `Ctrl+C` بزنید. بعد این دستورها را اجرا کنید:

```powershell
cd C:\razi\backend
.venv\Scripts\python scripts\autostart.py install
```

از این به بعد سرور در هر بار روشن شدن ویندوز، حدود یک دقیقه بعد (تا PostgreSQL بالا بیاید) خودکار اجرا می‌شود. لازم نیست کسی وارد ویندوز شود. اگر سرور به هر دلیلی بسته شود، بعد از ۵ ثانیه دوباره اجرا می‌شود. گزارش اجرا در `C:\razi\backend\logs\server.log` نوشته می‌شود.

دستورهای دیگر (همه در PowerShell مدیر و در پوشه `C:\razi\backend`):

| کار | دستور |
|---|---|
| وضعیت | `.venv\Scripts\python scripts\autostart.py status` |
| توقف | `.venv\Scripts\python scripts\autostart.py stop` |
| اجرای دوباره | `.venv\Scripts\python scripts\autostart.py start` |
| حذف اجرای خودکار | `.venv\Scripts\python scripts\autostart.py remove` |

## به‌روزرسانی کد

ZIP جدید را باز کنید، فایل `backend\.env` قبلی را به پوشه جدید کپی کنید، سپس مرحله‌های ۲ (به جز دستور آخر)، ۴ و ۵ را دوباره اجرا کنید.

فایل‌های آموزشی بارگذاری‌شده در `C:\razi\backend\media` نگهداری می‌شوند؛ هنگام به‌روزرسانی این پوشه را پاک نکنید و از آن پشتیبان بگیرید.

اگر اجرای خودکار (مرحله ۸) فعال است، به جای مرحله ۵ قبل از کپی فایل‌ها `autostart.py stop` و بعد از نصب `autostart.py start` را اجرا کنید.
