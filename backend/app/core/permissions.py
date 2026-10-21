"""تعریف مجوزها و نقش‌های پیش‌فرض.

دامنه مجوز از نحوه انتساب نقش می‌آید: اگر نقشی همراه با یک واحد به کاربر داده شود،
مجوزهای آن فقط برای داده‌های همان واحد معتبر است؛ بدون واحد، برای همه واحدها.
"""

PERMISSIONS: dict[str, str] = {
    "incident.create": "ثبت گزارش خطا",
    "incident.read": "مشاهده گزارش‌های خطا",
    "incident.manage": "ارجاع، تغییر وضعیت و ثبت اقدام اصلاحی",
    "incident.reporter.view": "مشاهده هویت گزارش‌دهنده (گزارش‌های غیرناشناس)",
    "incident.sensitive.view": "مشاهده اطلاعات هویتی بیمار در گزارش",
    "announcement.read": "مشاهده اطلاعیه‌های داخلی",
    "announcement.manage": "انتشار و مدیریت اطلاعیه‌ها",
    "faq.manage": "مدیریت پرسش‌های متداول",
    "education.manage": "مدیریت کتابخانه آموزش به بیمار (مقاله، ویدئو، صوت، PDF)",
    "translation.manage": "ثبت و تأیید ترجمه محتوای عمومی برای مراجعان",
    "feedback.manage": "رسیدگی به پیشنهادها و شکایات",
    "dashboard.read": "مشاهده داشبورد آماری",
    "audit.read": "مشاهده لاگ حسابرسی",
    "user.manage": "مدیریت کاربران و نقش‌ها",
    "unit.manage": "مدیریت واحدها",
    "form.manage": "مدیریت قالب فرم‌ها",
    "training.read": "شرکت در دوره‌ها و آزمون‌های آموزشی",
    "training.manage": "مدیریت دوره، محتوا و آزمون و دیدن نتایج و آمار آموزش",
    "request.create": "ثبت درخواست پشتیبانی (فناوری اطلاعات، تجهیزات، تأسیسات)",
    "request.handle": "رسیدگی به درخواست‌های پشتیبانی واحد",
    "request.read": "مشاهده درخواست‌های پشتیبانی کارکنان واحد و آمار آن",
    "request.type.manage": "تعریف انواع درخواست و واحد مسئول",
    # خود گفتگو برای همه کاربران فعال است؛ این دو فقط ساخت گروه و نوشتن در کانال واحد را باز می‌کنند.
    "chat.group.create": "ساخت گروه گفتگو",
    "chat.unit.post": "نوشتن در کانال گفتگوی واحد",
    # دیدن برنامه خود و برنامه منتشرشده بخش خود برای همه کاربران فعال است.
    "schedule.write": "نوشتن و تغییر برنامه شیفت واحد و تأیید جابجایی‌ها",
    "schedule.approve": "تأیید یا برگرداندن برنامه شیفت واحدها",
    "schedule.read.all": "دیدن برنامه شیفت همه واحدها و کشیک امروز",
    "schedule.config": "تعریف کدهای شیفت، تعطیلات و تنظیمات برنامه واحدها",
    # نوبت‌دهی: مدیر پزشکان همه پزشکان و نوبت‌ها را دارد؛ منشی فقط پزشکانی که به او سپرده شده‌اند.
    # پزشکی که حسابش به پرونده پزشک وصل است، فهرست بیماران خودش را بدون مجوز جدا می‌بیند.
    "doctor.manage": "تعریف تخصص‌ها، معرفی پزشکان و سپردن پزشک به منشی",
    "appointment.manage": "تعریف و ویرایش وقت‌ها و نوبت‌های پزشکان سپرده‌شده (منشی)",
    # نظرات مراجعان درباره پزشکان: متن نظر پس از تأیید مدیر پزشکان منتشر می‌شود؛ پزشک (حساب وصل به پرونده)
    # بدون مجوز جدا به نظرهای خودش پاسخ می‌دهد.
    "review.moderate": "تأیید یا رد نظرهای مراجعان درباره پزشکان و پاسخ به آن‌ها",
    "review.reply": "پاسخ مدیر سیستم به نظرهای مراجعان درباره پزشکان",
    # باشگاه مراجعان (ژتون)
    "token.manage": "اهدای ژتون به مراجعان و تعریف امتیازهای قابل دریافت",
    "token.redeem": "بررسی و تحویل کد استفاده از ژتون مراجعان (پذیرش و نوبت‌دهی)",
}

DEFAULT_ROLES: dict[str, tuple[str, list[str]]] = {
    "staff": ("کارمند", ["incident.create", "announcement.read", "training.read", "request.create"]),
    "unit_manager": (
        "مسئول واحد",
        [
            "incident.create",
            "incident.read",
            "announcement.read",
            "training.read",
            "request.create",
            "request.read",
            "chat.group.create",
            "chat.unit.post",
            "schedule.write",
        ],
    ),
    "patient_safety": (
        "کارشناس ایمنی بیمار",
        [
            "incident.create",
            "incident.read",
            "incident.manage",
            "incident.reporter.view",
            "incident.sensitive.view",
            "announcement.read",
            "dashboard.read",
            "training.read",
            "request.create",
        ],
    ),
    "content_manager": (
        "مدیر محتوا",
        [
            "announcement.read",
            "announcement.manage",
            "faq.manage",
            "education.manage",
            "translation.manage",
            "feedback.manage",
            "training.read",
            "request.create",
        ],
    ),
    "training_manager": (
        "مسئول آموزش",
        ["announcement.read", "training.read", "training.manage", "request.create"],
    ),
    # با واحد انتساب داده شود (مثلاً فناوری اطلاعات) تا فقط صف همان واحد را ببیند.
    "support_agent": (
        "کارشناس پشتیبانی",
        ["announcement.read", "training.read", "request.create", "request.handle"],
    ),
    "executive": (
        "مدیر ارشد",
        ["announcement.read", "dashboard.read", "training.read", "request.create", "request.read"],
    ),
    # مترون (دفتر پرستاری). اگر با واحد انتساب داده شود، فقط برنامه همان واحد را تأیید می‌کند.
    "matron": (
        "مترون",
        [
            "announcement.read",
            "training.read",
            "request.create",
            "schedule.approve",
            "schedule.read.all",
            "schedule.config",
        ],
    ),
    "supervisor": (
        "سوپروایزر",
        ["announcement.read", "training.read", "request.create", "schedule.read.all"],
    ),
    # ایمنی، اعتبارسنجی و روابط عمومی: ویرایش بخش‌ها و متن‌ها و ترجمه‌های عمومی.
    "quality_pr": (
        "مسئول ایمنی، اعتبارسنجی و روابط عمومی",
        ["announcement.read", "training.read", "request.create", "unit.manage", "translation.manage"],
    ),
    "doctor_admin": (
        "مدیر معرفی پزشکان و نوبت‌دهی",
        [
            "announcement.read",
            "training.read",
            "request.create",
            "doctor.manage",
            "appointment.manage",
            "review.moderate",
            "token.manage",
            "token.redeem",
            "education.manage",
        ],
    ),
    "secretary": (
        "منشی پزشک",
        ["announcement.read", "training.read", "request.create", "appointment.manage", "token.redeem"],
    ),
    "auditor": ("حسابرس", ["audit.read"]),
    # مدیر سیستم عمداً به محتوای گزارش‌های خطا دسترسی ندارد.
    "sysadmin": (
        "مدیر سیستم",
        [
            "user.manage",
            "unit.manage",
            "form.manage",
            "announcement.read",
            "request.type.manage",
            "chat.group.create",
            "schedule.config",
            "review.reply",
            "token.manage",
            "token.redeem",
        ],
    ),
}
