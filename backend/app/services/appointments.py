"""منطق نوبت‌دهی: ساخت وقت‌ها از نوبت کاری، ظرفیت، دسترسی منشی و یادآور پیامکی.

نام و موبایل مراجع در لاگ حسابرسی و اعلان کارکنان نوشته نمی‌شود؛ فقط کد پیگیری نوبت.
"""

import asyncio
import logging
import secrets
import uuid
from datetime import UTC, date, datetime, time, timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core import jalali
from app.core.config import get_settings
from app.core.db import SessionLocal
from app.core.deps import CurrentUser, api_error
from app.models import Appointment, AppointmentSlot, Doctor, DoctorSecretary
from app.services import sms
from app.services.schedule_logic import LOCAL_TZ

log = logging.getLogger(__name__)

ACTIVE = ("booked", "attended")
REMINDER_INTERVAL_SECONDS = 300
_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"


def now() -> datetime:
    return datetime.now(UTC)


def new_tracking_code(db: Session) -> str:
    while True:
        code = "".join(secrets.choice(_CODE_ALPHABET) for _ in range(8))
        if db.scalar(select(Appointment.id).where(Appointment.tracking_code == code)) is None:
            return code


def local_day_bounds(day: date) -> tuple[datetime, datetime]:
    start = datetime.combine(day, time(0), LOCAL_TZ)
    return start, start + timedelta(days=1)


def session_starts(day: date, start: str, end: str, minutes: int) -> list[datetime]:
    """وقت‌های پشت‌سرهم یک نوبت کاری به ساعت محلی؛ وقتی که از پایان بیرون بزند ساخته نمی‌شود."""
    sh, sm = map(int, start.split(":"))
    eh, em = map(int, end.split(":"))
    t = datetime.combine(day, time(sh, sm), LOCAL_TZ)
    stop = datetime.combine(day, time(eh, em), LOCAL_TZ)
    out = []
    while t + timedelta(minutes=minutes) <= stop:
        out.append(t)
        t += timedelta(minutes=minutes)
    return out


def booked_counts(db: Session, slot_ids: list[uuid.UUID]) -> dict[uuid.UUID, int]:
    if not slot_ids:
        return {}
    rows = db.execute(
        select(Appointment.slot_id, func.count())
        .where(Appointment.slot_id.in_(slot_ids), Appointment.status.in_(ACTIVE))
        .group_by(Appointment.slot_id)
    )
    return dict(rows.all())


def free_places(db: Session, slot: AppointmentSlot) -> int:
    used = db.scalar(
        select(func.count()).where(Appointment.slot_id == slot.id, Appointment.status.in_(ACTIVE))
    )
    return max(0, slot.capacity - (used or 0))


def lock_slot_for_booking(
    db: Session, slot_id: uuid.UUID, doctor_id: uuid.UUID | None = None
) -> AppointmentSlot:
    """وقت را قفل می‌کند تا دو نفر هم‌زمان آخرین جای خالی را نگیرند."""
    slot = db.scalar(select(AppointmentSlot).where(AppointmentSlot.id == slot_id).with_for_update())
    if slot is None or (doctor_id is not None and slot.doctor_id != doctor_id):
        raise api_error(404, "slot_not_found", "این وقت پیدا نشد")
    if slot.is_blocked:
        raise api_error(409, "slot_blocked", "این وقت بسته شده است")
    if slot.starts_at <= now():
        raise api_error(409, "slot_past", "زمان این وقت گذشته است")
    if free_places(db, slot) <= 0:
        raise api_error(409, "slot_full", "این وقت پر شده است؛ وقت دیگری انتخاب کنید")
    return slot


def next_free_at(db: Session, doctor_id: uuid.UUID, horizon_days: int) -> datetime | None:
    t = now()
    slots = db.scalars(
        select(AppointmentSlot)
        .where(
            AppointmentSlot.doctor_id == doctor_id,
            AppointmentSlot.is_blocked.is_(False),
            AppointmentSlot.starts_at > t,
            AppointmentSlot.starts_at < t + timedelta(days=horizon_days),
        )
        .order_by(AppointmentSlot.starts_at)
        .limit(200)
    ).all()
    counts = booked_counts(db, [s.id for s in slots])
    for s in slots:
        if counts.get(s.id, 0) < s.capacity:
            return s.starts_at
    return None


def can_manage(db: Session, cu: CurrentUser, doctor_id: uuid.UUID) -> bool:
    if cu.has_global("doctor.manage"):
        return True
    if not cu.has("appointment.manage"):
        return False
    return (
        db.scalar(
            select(DoctorSecretary.id).where(
                DoctorSecretary.doctor_id == doctor_id, DoctorSecretary.user_id == cu.id
            )
        )
        is not None
    )


def managed_doctor(db: Session, cu: CurrentUser, doctor_id: uuid.UUID) -> Doctor:
    doctor = db.get(Doctor, doctor_id)
    if doctor is None:
        raise api_error(404, "not_found", "پزشک پیدا نشد")
    if not can_manage(db, cu, doctor_id):
        raise api_error(403, "forbidden", "این پزشک به شما سپرده نشده است")
    return doctor


def staff_recipients(doctor: Doctor) -> list[uuid.UUID]:
    """پزشک و منشی‌هایش؛ برای اعلان نوبت تازه یا لغو."""
    return [u for u in [doctor.user_id, *(s.user_id for s in doctor.secretaries)] if u]


def local_parts(at: datetime) -> tuple[str, str]:
    local = at.astimezone(LOCAL_TZ)
    return str(jalali.from_gregorian(local.date())), local.strftime("%H:%M")


def send_due_reminders(db: Session, at: datetime | None = None) -> int:
    """یادآور پیامکی نوبت‌هایی که تا appointment_reminder_hours دیگر می‌رسند؛ هر نوبت فقط یک بار."""
    if not sms.reminders_enabled():
        return 0
    t = at or now()
    horizon = t + timedelta(hours=get_settings().appointment_reminder_hours)
    due = db.scalars(
        select(Appointment)
        .join(AppointmentSlot, Appointment.slot_id == AppointmentSlot.id)
        .where(
            Appointment.status == "booked",
            Appointment.reminder_sent_at.is_(None),
            AppointmentSlot.starts_at > t,
            AppointmentSlot.starts_at <= horizon,
        )
        .with_for_update(of=Appointment, skip_locked=True)
        .limit(200)
    ).all()
    sent = 0
    for a in due:
        day, hhmm = local_parts(a.slot.starts_at)
        try:
            sms.send_appointment_reminder(a.patient_mobile, day, hhmm, a.doctor.full_name)
        except sms.SmsError:
            log.warning("appointment reminder failed for %s", a.tracking_code)
            continue
        a.reminder_sent_at = t
        sent += 1
    db.commit()
    return sent


async def reminder_loop() -> None:
    """هر چند دقیقه یادآورهای رسیده را می‌فرستد؛ خطا حلقه را متوقف نمی‌کند."""
    while True:
        try:
            await asyncio.to_thread(_run_reminders_once)
        except Exception:  # noqa: BLE001
            log.exception("appointment reminder loop failed")
        await asyncio.sleep(REMINDER_INTERVAL_SECONDS)


def _run_reminders_once() -> None:
    with SessionLocal() as db:
        send_due_reminders(db)
