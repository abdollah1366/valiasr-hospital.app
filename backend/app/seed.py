"""داده‌های پایه: نقش‌ها، قالب فرم گزارش خطا و (اختیاری) مدیر اولیه و داده نمایشی.

اجرا:
    python -m app.seed            # نقش‌ها، فرم و کدهای شیفت؛ مدیر اولیه از RAZI_BOOTSTRAP_ADMIN_CODE/PASSWORD
    python -m app.seed --demo     # به‌علاوه واحدها و کاربران نمایشی (توسعه، یا staging با RAZI_DEMO_PASSWORD)
"""

import json
import sys
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.db import SessionLocal
from app.core.permissions import DEFAULT_ROLES
from app.core.security import hash_password
from app.models import (
    Announcement,
    AppointmentSlot,
    Doctor,
    DoctorSecretary,
    Faq,
    FormTemplate,
    RequestType,
    Role,
    ShiftType,
    Specialty,
    Unit,
    User,
    UserRole,
)

INCIDENT_FORM = Path(__file__).parent / "data" / "incident_form.json"
SHIFT_CODES = Path(__file__).parent / "data" / "shift_codes.json"
DEMO_PASSWORD = "Razi1234"


def seed_roles(db: Session) -> None:
    existing = {r.code: r for r in db.scalars(select(Role))}
    for code, (name, perms) in DEFAULT_ROLES.items():
        role = existing.get(code)
        if role is None:
            db.add(Role(code=code, name=name, permissions=perms))
        else:
            role.name, role.permissions = name, perms


# گروه‌های تخصصی بیمارستان (معرفی رسمی بیمارستان)؛ فقط وقتی هیچ گروهی تعریف نشده افزوده می‌شوند.
SPECIALTIES = [
    ("قلب و عروق", "heart"),
    ("زنان و زایمان", "female"),
    ("مغز و اعصاب", "brain"),
    ("گوارش و کبد", "stomach"),
    ("ارتوپدی", "bone"),
    ("اورولوژی", "kidney"),
    ("نفرولوژی", "kidney"),
    ("آنکولوژی", "ribbon"),
    ("عفونی", "stethoscope"),
    ("ریه", "lungs"),
    ("گوش و حلق و بینی", "ear"),
    ("ناباروری و IVF", "baby"),
    ("کودکان", "child"),
    ("جراحی عمومی", "scalpel"),
    ("دندانپزشکی", "tooth"),
]


def seed_specialties(db: Session) -> None:
    if db.scalar(select(Specialty.id).limit(1)) is not None:
        return
    db.add_all(Specialty(name=n, icon=i, sort_order=k) for k, (n, i) in enumerate(SPECIALTIES))


def seed_forms(db: Session) -> None:
    if db.scalar(select(FormTemplate).where(FormTemplate.code == "incident")) is None:
        schema = json.loads(INCIDENT_FORM.read_text(encoding="utf-8"))
        db.add(FormTemplate(code="incident", version=schema.get("version", 1), schema=schema, is_active=True))


def seed_shift_types(db: Session) -> None:
    """کدهای شیفت فهرست رسمی بیمارستان؛ فقط کدهای تازه افزوده می‌شوند تا ویرایش‌های پنل از بین نروند."""
    existing = set(db.scalars(select(ShiftType.code)))
    for c in json.loads(SHIFT_CODES.read_text(encoding="utf-8"))["codes"]:
        if c["code"] in existing:
            continue
        db.add(
            ShiftType(
                code=c["code"],
                title=c["title"],
                label=c["label"],
                kind=c["kind"],
                group=c["group"],
                segments=c["segments"],
                hours=c["hours"],
                productivity=c["productivity"],
                overtime=c["overtime"],
                weekday=c["weekday"],
                is_active=c["active"],
                replaced_by=c["replaced_by"],
                review_note=c["review_note"],
            )
        )


def _user(
    db: Session, code: str, name: str, unit: Unit | None, roles: list[tuple[str, Unit | None]], password: str
):
    if db.scalar(select(User).where(User.personnel_code == code)) is not None:
        return
    by_code = {r.code: r for r in db.scalars(select(Role))}
    user = User(
        personnel_code=code,
        full_name=name,
        unit_id=unit.id if unit else None,
        password_hash=hash_password(password),
        must_change_password=False,
    )
    user.roles = [UserRole(role=by_code[r], unit_id=u.id if u else None) for r, u in roles]
    db.add(user)


def seed_bootstrap_admin(db: Session) -> None:
    s = get_settings()
    if not (s.bootstrap_admin_code and s.bootstrap_admin_password):
        return
    if db.scalar(select(User).where(User.personnel_code == s.bootstrap_admin_code)) is not None:
        return
    admin_role = db.scalar(select(Role).where(Role.code == "sysadmin"))
    user = User(
        personnel_code=s.bootstrap_admin_code,
        full_name="مدیر سیستم",
        password_hash=hash_password(s.bootstrap_admin_password),
        must_change_password=True,
    )
    user.roles = [UserRole(role=admin_role)]
    db.add(user)


def seed_demo(db: Session) -> None:
    s = get_settings()
    if s.env == "production":
        raise RuntimeError("داده نمایشی در محیط عملیاتی مجاز نیست")
    password = DEMO_PASSWORD
    if s.env == "staging":
        # سرور آزمایشی روی اینترنت نباید رمز عمومیِ README را داشته باشد.
        if not s.demo_password or len(s.demo_password) < 10 or s.demo_password == DEMO_PASSWORD:
            raise RuntimeError("در staging باید RAZI_DEMO_PASSWORD (حداقل ۱۰ نویسه) تنظیم شود")
        password = s.demo_password
    if db.scalar(select(Unit)) is not None:
        # پایگاه داده قبلاً داده نمایشی دارد؛ فقط موارد تازه (مثل بخش آموزش) افزوده می‌شوند.
        seed_sample_departments(db)
        seed_demo_training(db, password)
        seed_demo_requests(db, password)
        seed_demo_schedules(db, password)
        seed_demo_doctors(db, password)
        return
    units = {
        "emergency": Unit(
            name="اورژانس",
            type="ward",
            is_public=True,
            location="طبقه همکف، ورودی اصلی",
            phone="داخلی ۱۱۰",
            hours="شبانه‌روزی",
        ),
        "icu": Unit(name="ICU", type="ward", is_public=False, location="طبقه دوم"),
        "internal": Unit(name="بخش داخلی", type="ward", is_public=True, location="طبقه سوم"),
        "clinic": Unit(
            name="درمانگاه تخصصی",
            type="clinic",
            is_public=True,
            location="ساختمان درمانگاه",
            hours="شنبه تا چهارشنبه، ۸ تا ۱۴",
        ),
        "it": Unit(name="فناوری اطلاعات", type="support"),
        "quality": Unit(name="دفتر بهبود کیفیت و ایمنی بیمار", type="admin"),
    }
    db.add_all(units.values())
    db.flush()
    _user(db, "1001", "کارمند نمونه (پرستار اورژانس)", units["emergency"], [("staff", None)], password)
    _user(
        db,
        "1002",
        "سرپرستار اورژانس",
        units["emergency"],
        [("unit_manager", units["emergency"])],
        password,
    )
    _user(db, "1003", "کارشناس ایمنی بیمار", units["quality"], [("patient_safety", None)], password)
    _user(
        db,
        "1004",
        "مدیر محتوا",
        units["quality"],
        [("content_manager", None), ("staff", None)],
        password,
    )
    _user(db, "1005", "مدیر سیستم", units["it"], [("sysadmin", None), ("auditor", None)], password)
    db.flush()
    seed_sample_departments(db)
    seed_demo_training(db, password)
    seed_demo_requests(db, password)
    seed_demo_schedules(db, password)
    seed_demo_doctors(db, password)
    db.add_all(
        [
            Faq(
                question="ساعت ملاقات بیماران چه زمانی است؟",
                answer="این متن نمونه است؛ ساعات واقعی را واحد روابط عمومی وارد می‌کند.",
                sort_order=1,
            ),
            Faq(
                question="برای پذیرش چه مدارکی لازم است؟",
                answer="این متن نمونه است؛ فهرست واقعی مدارک را واحد پذیرش وارد می‌کند.",
                sort_order=2,
            ),
            Announcement(
                title="به نسخه آزمایشی اپ بیمارستان ولیعصر خوش آمدید",
                body="این یک اطلاعیه نمونه است.",
                category="news",
                audience="public",
            ),
            Announcement(
                title="بخشنامه نمونه: شناسایی صحیح بیمار",
                body="متن نمونه بخشنامه برای آزمایش تأیید مطالعه.",
                category="circular",
                audience="staff",
                is_important=True,
                requires_ack=True,
            ),
        ]
    )


DEMO_JOB_TITLES = {
    "1001": "پرستار",
    "1002": "سرپرستار",
    "1003": "کارشناس ایمنی بیمار",
    "1004": "کارشناس روابط عمومی",
    "1006": "کارشناس آموزش",
    "1007": "کارشناس شبکه و سخت‌افزار",
    "1008": "تکنسین تأسیسات و تجهیزات",
    "1009": "مترون",
    "1010": "سوپروایزر",
    "1011": "بهیار",
}


def seed_demo_training(db: Session, password: str) -> None:
    """مسئول آموزش نمایشی (1006) و سمت کاربران نمایشی؛ اجرای دوباره چیزی را تکرار نمی‌کند."""
    name = "واحد آموزش"
    unit = db.scalar(select(Unit).where(Unit.name == name))
    if unit is None:
        unit = Unit(name=name, type="admin")
        db.add(unit)
        db.flush()
    _user(db, "1006", "مسئول آموزش", unit, [("training_manager", None), ("staff", None)], password)
    db.flush()
    for u in db.scalars(select(User).where(User.personnel_code.in_(DEMO_JOB_TITLES))):
        if not u.job_title:
            u.job_title = DEMO_JOB_TITLES[u.personnel_code]


# بخش‌های بیمارستان از فهرست سایت (۲۲ بخش). ساعت کار و شرح نمونه‌اند و واحد ایمنی آن‌ها را ویرایش می‌کند.
SAMPLE_DEPARTMENTS: list[tuple[str, str, bool]] = [
    # (نام، نوع، عمومی)
    ("خدمات بستری", "ward", True),
    ("خدمات پاراکلینیک", "support", True),
    ("مراقبت‌های ویژه", "ward", False),
    ("داخلی ۱", "ward", True),
    ("داخلی ۲", "ward", True),
    ("داخلی ۳", "ward", True),
    ("همودیالیز", "clinic", True),
    ("دندانپزشکی", "clinic", True),
    ("IVF", "clinic", True),
    ("درمانگاه‌های تخصصی", "clinic", True),
    ("ICU5 جنرال", "ward", False),
    ("طب هسته‌ای", "support", True),
    ("ICU جراحی", "ward", False),
    ("فیزیوتراپی", "clinic", True),
    ("کودکان", "ward", True),
    ("جراحی ۱", "ward", True),
    ("جراحی ۲", "ward", True),
    ("جراحی ۴", "ward", True),
    ("جراحی VIP", "ward", True),
    ("اورژانس", "ward", True),
    ("خواب", "clinic", True),
    ("زنان", "ward", True),
]
SAMPLE_HOURS = "نمونه؛ ساعت کار به‌زودی ثبت می‌شود"
SAMPLE_DESCRIPTION = "نمونه؛ شرح این بخش به‌زودی توسط واحد ایمنی و روابط عمومی تکمیل می‌شود."


def seed_sample_departments(db: Session) -> None:
    """بخش‌های نمونه؛ بخشی که از قبل با همین نام وجود دارد دست‌نخورده می‌ماند."""
    for name, type_, public in SAMPLE_DEPARTMENTS:
        if db.scalar(select(Unit).where(Unit.name == name)) is None:
            db.add(
                Unit(
                    name=name,
                    type=type_,
                    is_public=public,
                    hours=SAMPLE_HOURS,
                    description=SAMPLE_DESCRIPTION,
                )
            )
    db.flush()


def _unit(db: Session, name: str, type_: str) -> Unit:
    unit = db.scalar(select(Unit).where(Unit.name == name))
    if unit is None:
        unit = Unit(name=name, type=type_)
        db.add(unit)
        db.flush()
    return unit


def seed_demo_requests(db: Session, password: str) -> None:
    """واحدهای پشتیبانی، کارشناس‌های نمایشی (1007 و 1008) و چند نوع درخواست نمونه."""
    it = _unit(db, "فناوری اطلاعات", "support")
    equipment = _unit(db, "تجهیزات پزشکی", "support")
    facilities = _unit(db, "تأسیسات", "support")
    _user(db, "1007", "کارشناس فناوری اطلاعات", it, [("support_agent", it)], password)
    _user(
        db,
        "1008",
        "تکنسین تأسیسات",
        facilities,
        [("support_agent", facilities), ("support_agent", equipment)],
        password,
    )
    db.flush()
    for u in db.scalars(select(User).where(User.personnel_code.in_(["1007", "1008"]))):
        if not u.job_title:
            u.job_title = DEMO_JOB_TITLES[u.personnel_code]
    if db.scalar(select(RequestType)) is not None:
        return
    db.add_all(
        [
            RequestType(
                name="خرابی رایانه یا پرینتر",
                category="it",
                handler_unit_id=it.id,
                sort_order=1,
                fields=[
                    {
                        "id": "device",
                        "label": "نوع دستگاه",
                        "kind": "select",
                        "options": ["رایانه", "پرینتر", "اسکنر", "سایر"],
                        "required": True,
                    },
                    {
                        "id": "asset_no",
                        "label": "شماره اموال",
                        "kind": "text",
                        "options": [],
                        "required": False,
                    },
                ],
            ),
            RequestType(
                name="مشکل شبکه یا سامانه HIS",
                category="it",
                handler_unit_id=it.id,
                sort_order=2,
            ),
            RequestType(
                name="خرابی تجهیزات پزشکی",
                category="equipment",
                handler_unit_id=equipment.id,
                sort_order=3,
                fields=[
                    {"id": "device", "label": "نام دستگاه", "kind": "text", "options": [], "required": True},
                    {
                        "id": "asset_no",
                        "label": "شماره اموال",
                        "kind": "text",
                        "options": [],
                        "required": False,
                    },
                ],
            ),
            RequestType(
                name="تأسیسات (برق، آب، سرمایش و گرمایش)",
                category="facilities",
                handler_unit_id=facilities.id,
                sort_order=4,
                fields=[
                    {
                        "id": "kind",
                        "label": "موضوع",
                        "kind": "select",
                        "options": ["برق", "آب و فاضلاب", "سرمایش و گرمایش", "در و پنجره", "سایر"],
                        "required": True,
                    }
                ],
            ),
        ]
    )


def seed_demo_schedules(db: Session, password: str) -> None:
    """مترون (1009)، سوپروایزر (1010) و یک بهیار ICU (1011) برای آزمایش برنامه شیفت و نیروی قرضی."""
    office = _unit(db, "دفتر پرستاری", "admin")
    icu = db.scalar(select(Unit).where(Unit.name == "ICU"))
    _user(db, "1009", "مترون", office, [("matron", None), ("staff", None)], password)
    _user(db, "1010", "سوپروایزر", office, [("supervisor", None), ("staff", None)], password)
    _user(db, "1011", "بهیار ICU", icu, [("staff", None)], password)
    db.flush()
    for u in db.scalars(select(User).where(User.personnel_code.in_(["1009", "1010", "1011"]))):
        if not u.job_title:
            u.job_title = DEMO_JOB_TITLES[u.personnel_code]


DEMO_DOCTORS = [
    ("دکتر نمونه یکم", "قلب و عروق", "متخصص قلب و عروق (نمونه)", True),
    ("دکتر نمونه دوم", "ارتوپدی", "جراح و متخصص ارتوپدی (نمونه)", True),
    ("دکتر نمونه سوم", "زنان و زایمان", "متخصص زنان و زایمان (نمونه)", False),
]


def seed_demo_doctors(db: Session, password: str) -> None:
    """مدیر نوبت‌دهی (1012)، منشی (1013)، پزشک با حساب کارکنان (1014) و سه پزشک نمونه با وقت‌های هفته آینده."""
    from datetime import date, timedelta

    from app.services.appointments import session_starts

    if db.scalar(select(Doctor.id).limit(1)) is not None:
        return
    seed_specialties(db)
    db.flush()
    clinic = _unit(db, "درمانگاه تخصصی", "clinic")
    _user(db, "1012", "مدیر نوبت‌دهی", clinic, [("doctor_admin", None)], password)
    _user(db, "1013", "منشی درمانگاه", clinic, [("secretary", None)], password)
    _user(db, "1014", "دکتر نمونه یکم", clinic, [("staff", None)], password)
    db.flush()
    users = {
        u.personnel_code: u for u in db.scalars(select(User).where(User.personnel_code.in_(["1013", "1014"])))
    }
    by_name = {s.name: s for s in db.scalars(select(Specialty))}
    tomorrow = date.today() + timedelta(days=1)
    for k, (name, spec, title, featured) in enumerate(DEMO_DOCTORS):
        d = Doctor(
            full_name=name,
            specialty_id=by_name[spec].id,
            title=title,
            bio="این متن نمونه است؛ معرفی واقعی پزشک را مدیر نوبت‌دهی در پنل وارد می‌کند.",
            clinic_unit_id=clinic.id,
            is_featured=featured,
            sort_order=k,
            user_id=users["1014"].id if k == 0 else None,
        )
        d.secretaries = [DoctorSecretary(user_id=users["1013"].id)]
        db.add(d)
        db.flush()
        for day in range(7):
            for t in session_starts(tomorrow + timedelta(days=day), "09:00", "12:00", d.visit_minutes):
                db.add(AppointmentSlot(doctor_id=d.id, starts_at=t, minutes=d.visit_minutes))


def run(demo: bool = False) -> None:
    with SessionLocal() as db:
        seed_roles(db)
        seed_forms(db)
        seed_shift_types(db)
        seed_specialties(db)
        db.flush()
        seed_bootstrap_admin(db)
        if demo:
            seed_demo(db)
        db.commit()


if __name__ == "__main__":
    run(demo="--demo" in sys.argv)
    print("seed done")
