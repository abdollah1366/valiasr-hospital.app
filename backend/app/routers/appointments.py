"""پزشکان و نوبت‌دهی.

- عمومی: گروه‌های تخصصی، پزشکان، وقت‌های خالی (بدون ورود)
- مراجع: گرفتن، دیدن و لغو نوبت خود (توکن مراجع)
- مدیر پزشکان (doctor.manage): تعریف تخصص و پزشک، عکس، سپردن پزشک به منشی
- منشی (appointment.manage + سپرده‌شدن پزشک): تعریف وقت، بستن، نوبت دادن، جابجایی، لغو
- پزشک (حساب کارکنان وصل به پرونده پزشک): فهرست بیماران هر روز
"""

import uuid
from datetime import date, timedelta

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import FileResponse
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, get_active_user, require
from app.models import (
    Appointment,
    AppointmentSlot,
    Doctor,
    DoctorReview,
    DoctorSecretary,
    MediaFile,
    Patient,
    Specialty,
    Unit,
    User,
)
from app.routers.admin import UNIT_IMAGE_MAX_MB, UNIT_IMAGE_TYPES
from app.routers.patient import get_current_patient
from app.routers.reviews import my_review_out
from app.schemas.appointments import (
    CancelDayIn,
    CancelDayOut,
    DoctorIn,
    DoctorOut,
    ManageAppointmentOut,
    ManageSlotOut,
    MoveIn,
    PatientAppointmentOut,
    PatientBookIn,
    PublicDoctorOut,
    PublicSlotOut,
    SessionIn,
    SessionOut,
    SlotUpdateIn,
    SpecialtyIn,
    SpecialtyOut,
    StaffBookIn,
    StaffBrief,
    StatusIn,
)
from app.services import appointments as svc
from app.services import notifications as notes
from app.services import reviews as reviews_svc
from app.services import tokens as tokens_svc
from app.services.media import store_upload
from app.services.phone import normalize_mobile
from app.services.training import media_path

router = APIRouter(tags=["appointments"])

MAX_RANGE_DAYS = 62


# ---------------------------------------------------------------- خروجی‌ها


def _brief(u: User | None) -> StaffBrief | None:
    return StaffBrief(id=u.id, personnel_code=u.personnel_code, full_name=u.full_name) if u else None


def _doctor_out(db: Session, d: Doctor) -> DoctorOut:
    secretaries = [db.get(User, s.user_id) for s in d.secretaries]
    return DoctorOut(
        id=d.id,
        full_name=d.full_name,
        specialty_id=d.specialty_id,
        specialty_name=d.specialty.name,
        title=d.title,
        bio=d.bio,
        medical_code=d.medical_code,
        clinic_unit_id=d.clinic_unit_id,
        user=_brief(db.get(User, d.user_id) if d.user_id else None),
        visit_minutes=d.visit_minutes,
        is_featured=d.is_featured,
        booking_enabled=d.booking_enabled,
        is_active=d.is_active,
        sort_order=d.sort_order,
        has_photo=d.has_photo,
        secretaries=[b for b in (_brief(u) for u in secretaries) if b],
    )


def _public_doctor(db: Session, d: Doctor, with_next: bool = True) -> PublicDoctorOut:
    clinic = db.get(Unit, d.clinic_unit_id) if d.clinic_unit_id else None
    horizon = get_settings().appointment_booking_days
    average, count = reviews_svc.rating(db, d.id)
    return PublicDoctorOut(
        id=d.id,
        full_name=d.full_name,
        title=d.title,
        bio=d.bio,
        specialty_id=d.specialty_id,
        specialty_name=d.specialty.name,
        specialty_icon=d.specialty.icon,
        clinic_name=clinic.name if clinic else None,
        clinic_location=clinic.location if clinic else None,
        has_photo=d.has_photo,
        is_featured=d.is_featured,
        booking_enabled=d.booking_enabled,
        next_free_at=svc.next_free_at(db, d.id, horizon) if with_next and d.booking_enabled else None,
        rating_average=average,
        rating_count=count,
    )


def _manage_appt(a: Appointment) -> ManageAppointmentOut:
    return ManageAppointmentOut(
        id=a.id,
        tracking_code=a.tracking_code,
        status=a.status,
        patient_name=a.patient_name,
        patient_mobile=a.patient_mobile,
        note=a.note,
        source=a.source,
        slot_id=a.slot_id,
        starts_at=a.slot.starts_at,
        minutes=a.slot.minutes,
        doctor_id=a.doctor_id,
        doctor_name=a.doctor.full_name,
        created_at=a.created_at,
    )


def _patient_appt(db: Session, a: Appointment) -> PatientAppointmentOut:
    d = a.doctor
    clinic = db.get(Unit, d.clinic_unit_id) if d.clinic_unit_id else None
    cancel_until = a.slot.starts_at - timedelta(hours=get_settings().appointment_cancel_hours)
    review = db.scalar(select(DoctorReview).where(DoctorReview.appointment_id == a.id))
    return PatientAppointmentOut(
        id=a.id,
        tracking_code=a.tracking_code,
        status=a.status,
        starts_at=a.slot.starts_at,
        minutes=a.slot.minutes,
        doctor_id=d.id,
        doctor_name=d.full_name,
        doctor_title=d.title,
        specialty_name=d.specialty.name,
        clinic_name=clinic.name if clinic else None,
        clinic_location=clinic.location if clinic else None,
        note=a.note,
        can_cancel=a.status == "booked" and svc.now() < cancel_until,
        cancelled_by=a.cancelled_by,
        can_review=a.status == "attended" and review is None,
        my_review=my_review_out(review) if review else None,
    )


def _public_doctor_or_404(db: Session, doctor_id: uuid.UUID) -> Doctor:
    d = db.get(Doctor, doctor_id)
    if d is None or not d.is_active or not d.specialty.is_active:
        raise api_error(404, "not_found", "پزشک پیدا نشد")
    return d


def _check_range(start: date, days: int) -> tuple:
    if days < 1 or days > MAX_RANGE_DAYS:
        raise api_error(422, "bad_range", f"بازه باید بین ۱ تا {MAX_RANGE_DAYS} روز باشد")
    lo, _ = svc.local_day_bounds(start)
    return lo, lo + timedelta(days=days)


# ---------------------------------------------------------------- عمومی


@router.get("/public/specialties", response_model=list[SpecialtyOut])
def public_specialties(db: Session = Depends(get_db)):
    counts = dict(
        db.execute(
            select(Doctor.specialty_id, func.count())
            .where(Doctor.is_active.is_(True))
            .group_by(Doctor.specialty_id)
        ).all()
    )
    rows = db.scalars(
        select(Specialty).where(Specialty.is_active.is_(True)).order_by(Specialty.sort_order, Specialty.name)
    )
    return [
        SpecialtyOut(
            id=s.id,
            name=s.name,
            description=s.description,
            icon=s.icon,
            sort_order=s.sort_order,
            is_active=s.is_active,
            doctor_count=counts.get(s.id, 0),
        )
        for s in rows
    ]


@router.get("/public/doctors", response_model=list[PublicDoctorOut])
def public_doctors(
    specialty_id: uuid.UUID | None = None,
    q: str | None = Query(default=None, max_length=100),
    featured: bool = False,
    db: Session = Depends(get_db),
):
    stmt = (
        select(Doctor)
        .join(Specialty, Doctor.specialty_id == Specialty.id)
        .where(Doctor.is_active.is_(True), Specialty.is_active.is_(True))
    )
    if specialty_id:
        stmt = stmt.where(Doctor.specialty_id == specialty_id)
    if featured:
        stmt = stmt.where(Doctor.is_featured.is_(True))
    if q and q.strip():
        like = f"%{q.strip()}%"
        stmt = stmt.where(
            Doctor.full_name.ilike(like) | Specialty.name.ilike(like) | Doctor.title.ilike(like)
        )
    doctors = db.scalars(stmt.order_by(Doctor.sort_order, Doctor.full_name).limit(300)).unique()
    return [_public_doctor(db, d) for d in doctors]


@router.get("/public/doctors/{doctor_id}", response_model=PublicDoctorOut)
def public_doctor(doctor_id: uuid.UUID, db: Session = Depends(get_db)):
    return _public_doctor(db, _public_doctor_or_404(db, doctor_id))


@router.get("/public/doctors/{doctor_id}/photo", include_in_schema=False)
def public_doctor_photo(doctor_id: uuid.UUID, db: Session = Depends(get_db)):
    d = db.get(Doctor, doctor_id)
    m = db.get(MediaFile, d.photo_media_id) if d and d.is_active and d.photo_media_id else None
    if m is None:
        raise api_error(404, "not_found", "عکسی برای این پزشک نیست")
    return FileResponse(
        media_path(m),
        media_type=m.content_type,
        headers={"X-Content-Type-Options": "nosniff", "Cache-Control": "public, max-age=3600"},
    )


@router.get("/public/doctors/{doctor_id}/slots", response_model=list[PublicSlotOut])
def public_slots(doctor_id: uuid.UUID, db: Session = Depends(get_db)):
    """وقت‌های خالی آینده در بازه مجاز نوبت‌گیری."""
    d = _public_doctor_or_404(db, doctor_id)
    if not d.booking_enabled:
        return []
    t = svc.now()
    slots = db.scalars(
        select(AppointmentSlot)
        .where(
            AppointmentSlot.doctor_id == d.id,
            AppointmentSlot.is_blocked.is_(False),
            AppointmentSlot.starts_at > t,
            AppointmentSlot.starts_at < t + timedelta(days=get_settings().appointment_booking_days),
        )
        .order_by(AppointmentSlot.starts_at)
        .limit(1000)
    ).all()
    counts = svc.booked_counts(db, [s.id for s in slots])
    return [
        PublicSlotOut(
            id=s.id, starts_at=s.starts_at, minutes=s.minutes, free=s.capacity - counts.get(s.id, 0)
        )
        for s in slots
        if counts.get(s.id, 0) < s.capacity
    ]


# ---------------------------------------------------------------- مراجع


@router.post("/patient/appointments", response_model=PatientAppointmentOut, status_code=201)
def patient_book(
    body: PatientBookIn,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    slot = svc.lock_slot_for_booking(db, body.slot_id)
    doctor = db.get(Doctor, slot.doctor_id)
    if not doctor.is_active or not doctor.booking_enabled:
        raise api_error(409, "booking_closed", "نوبت‌دهی اینترنتی این پزشک فعلاً بسته است")
    if slot.starts_at > svc.now() + timedelta(days=get_settings().appointment_booking_days):
        raise api_error(409, "slot_too_far", "این وقت هنوز برای نوبت‌گیری باز نشده است")
    existing = db.scalar(
        select(Appointment.id)
        .join(AppointmentSlot, Appointment.slot_id == AppointmentSlot.id)
        .where(
            Appointment.patient_id == patient.id,
            Appointment.doctor_id == doctor.id,
            Appointment.status == "booked",
            AppointmentSlot.starts_at > svc.now(),
        )
    )
    if existing is not None:
        raise api_error(409, "already_booked", "شما نوبت فعالی نزد این پزشک دارید")
    name = (body.patient_name or patient.full_name or "").strip()
    if len(name) < 2:
        raise api_error(422, "name_required", "نام و نام خانوادگی بیمار را وارد کنید")
    if not patient.full_name:
        patient.full_name = name
    a = Appointment(
        slot_id=slot.id,
        doctor_id=doctor.id,
        patient_id=patient.id,
        patient_name=name,
        patient_mobile=patient.mobile,
        note=(body.note or "").strip() or None,
        tracking_code=svc.new_tracking_code(db),
        source="app",
    )
    db.add(a)
    db.flush()
    day, hhmm = svc.local_parts(slot.starts_at)
    notes.notify(
        db,
        svc.staff_recipients(doctor),
        "appointment",
        f"نوبت تازه: {day} ساعت {hhmm}",
        body=f"کد پیگیری {a.tracking_code}",
        link_type="appointment",
        link_id=a.id,
    )
    db.commit()
    db.refresh(a)
    return _patient_appt(db, a)


@router.get("/patient/appointments", response_model=list[PatientAppointmentOut])
def patient_appointments(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    rows = db.scalars(
        select(Appointment)
        .join(AppointmentSlot, Appointment.slot_id == AppointmentSlot.id)
        .where(Appointment.patient_id == patient.id)
        .order_by(AppointmentSlot.starts_at.desc())
        .limit(100)
    ).unique()
    return [_patient_appt(db, a) for a in rows]


@router.post("/patient/appointments/{appointment_id}/cancel", response_model=PatientAppointmentOut)
def patient_cancel(
    appointment_id: uuid.UUID,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    a = db.get(Appointment, appointment_id)
    if a is None or a.patient_id != patient.id:
        raise api_error(404, "not_found", "نوبت پیدا نشد")
    if not _patient_appt(db, a).can_cancel:
        hours = get_settings().appointment_cancel_hours
        raise api_error(409, "cannot_cancel", f"لغو نوبت فقط تا {hours} ساعت پیش از آن ممکن است")
    a.status, a.cancelled_at, a.cancelled_by = "cancelled", svc.now(), "patient"
    day, hhmm = svc.local_parts(a.slot.starts_at)
    notes.notify(
        db,
        svc.staff_recipients(a.doctor),
        "appointment",
        f"لغو نوبت {day} ساعت {hhmm}",
        body=f"کد پیگیری {a.tracking_code}",
        link_type="appointment",
        link_id=a.id,
    )
    db.commit()
    return _patient_appt(db, a)


# ---------------------------------------------------------------- مدیر پزشکان


@router.get("/admin/specialties", response_model=list[SpecialtyOut])
def admin_specialties(
    _: CurrentUser = Depends(require("doctor.manage", "appointment.manage")), db: Session = Depends(get_db)
):
    counts = dict(db.execute(select(Doctor.specialty_id, func.count()).group_by(Doctor.specialty_id)).all())
    return [
        SpecialtyOut(
            id=s.id,
            name=s.name,
            description=s.description,
            icon=s.icon,
            sort_order=s.sort_order,
            is_active=s.is_active,
            doctor_count=counts.get(s.id, 0),
        )
        for s in db.scalars(select(Specialty).order_by(Specialty.sort_order, Specialty.name))
    ]


def _save_specialty(db: Session, s: Specialty) -> None:
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise api_error(409, "duplicate_name", "گروه تخصصی با این نام وجود دارد") from None


@router.post("/admin/specialties", response_model=SpecialtyOut, status_code=201)
def create_specialty(
    body: SpecialtyIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage")),
    db: Session = Depends(get_db),
):
    s = Specialty(**body.model_dump())
    db.add(s)
    _save_specialty(db, s)
    audit(db, "specialty.created", cu.id, "specialty", s.id, client_ip(request))
    db.commit()
    return SpecialtyOut(**body.model_dump(), id=s.id)


@router.put("/admin/specialties/{specialty_id}", response_model=SpecialtyOut)
def update_specialty(
    specialty_id: uuid.UUID,
    body: SpecialtyIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage")),
    db: Session = Depends(get_db),
):
    s = db.get(Specialty, specialty_id)
    if s is None:
        raise api_error(404, "not_found", "گروه تخصصی پیدا نشد")
    for k, v in body.model_dump().items():
        setattr(s, k, v)
    _save_specialty(db, s)
    audit(db, "specialty.updated", cu.id, "specialty", s.id, client_ip(request))
    db.commit()
    count = db.scalar(select(func.count()).where(Doctor.specialty_id == s.id)) or 0
    return SpecialtyOut(**body.model_dump(), id=s.id, doctor_count=count)


@router.delete("/admin/specialties/{specialty_id}", status_code=204)
def delete_specialty(
    specialty_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage")),
    db: Session = Depends(get_db),
):
    s = db.get(Specialty, specialty_id)
    if s is None:
        raise api_error(404, "not_found", "گروه تخصصی پیدا نشد")
    if db.scalar(select(func.count()).where(Doctor.specialty_id == s.id)):
        raise api_error(409, "in_use", "این گروه پزشک دارد؛ به جای حذف آن را غیرفعال کنید")
    db.delete(s)
    audit(db, "specialty.deleted", cu.id, "specialty", specialty_id, client_ip(request))
    db.commit()


@router.get("/admin/doctors", response_model=list[DoctorOut])
def admin_doctors(
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")), db: Session = Depends(get_db)
):
    """مدیر همه پزشکان را می‌بیند و منشی فقط پزشکان سپرده‌شده به خودش را."""
    stmt = select(Doctor).order_by(Doctor.sort_order, Doctor.full_name)
    if not cu.has_global("doctor.manage"):
        stmt = stmt.join(DoctorSecretary, DoctorSecretary.doctor_id == Doctor.id).where(
            DoctorSecretary.user_id == cu.id
        )
    return [_doctor_out(db, d) for d in db.scalars(stmt).unique()]


def _user_by_code(db: Session, code: str) -> User:
    u = db.scalar(select(User).where(User.personnel_code == code.strip()))
    if u is None or not u.is_active:
        raise api_error(422, "unknown_user", f"کاربری با کد پرسنلی {code} پیدا نشد")
    return u


def _apply_doctor(db: Session, d: Doctor, body: DoctorIn) -> None:
    if db.get(Specialty, body.specialty_id) is None:
        raise api_error(422, "unknown_specialty", "گروه تخصصی نامعتبر است")
    if body.clinic_unit_id and db.get(Unit, body.clinic_unit_id) is None:
        raise api_error(422, "unknown_unit", "درمانگاه نامعتبر است")
    data = body.model_dump(exclude={"user_personnel_code", "secretary_codes"})
    for k, v in data.items():
        setattr(d, k, v)
    if body.user_personnel_code and body.user_personnel_code.strip():
        u = _user_by_code(db, body.user_personnel_code)
        other = db.scalar(select(Doctor).where(Doctor.user_id == u.id, Doctor.id != d.id))
        if other is not None:
            raise api_error(409, "user_taken", "این حساب به پزشک دیگری وصل است")
        d.user_id = u.id
    else:
        d.user_id = None
    wanted = {_user_by_code(db, c).id for c in body.secretary_codes if c.strip()}
    current = {s.user_id: s for s in d.secretaries}
    for uid, link in current.items():
        if uid not in wanted:
            d.secretaries.remove(link)
    for uid in wanted - current.keys():
        d.secretaries.append(DoctorSecretary(user_id=uid))


@router.post("/admin/doctors", response_model=DoctorOut, status_code=201)
def create_doctor(
    body: DoctorIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage")),
    db: Session = Depends(get_db),
):
    d = Doctor(full_name=body.full_name, specialty_id=body.specialty_id)
    db.add(d)
    _apply_doctor(db, d, body)
    db.flush()
    audit(db, "doctor.created", cu.id, "doctor", d.id, client_ip(request))
    db.commit()
    db.refresh(d)
    return _doctor_out(db, d)


@router.put("/admin/doctors/{doctor_id}", response_model=DoctorOut)
def update_doctor(
    doctor_id: uuid.UUID,
    body: DoctorIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage")),
    db: Session = Depends(get_db),
):
    d = db.get(Doctor, doctor_id)
    if d is None:
        raise api_error(404, "not_found", "پزشک پیدا نشد")
    _apply_doctor(db, d, body)
    audit(db, "doctor.updated", cu.id, "doctor", d.id, client_ip(request))
    db.commit()
    db.refresh(d)
    return _doctor_out(db, d)


@router.post("/admin/doctors/{doctor_id}/photo", response_model=DoctorOut, status_code=201)
async def upload_doctor_photo(
    doctor_id: uuid.UUID,
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    cu: CurrentUser = Depends(require("doctor.manage")),
    db: Session = Depends(get_db),
):
    """عکس پزشک؛ بدنه درخواست خود فایل است."""
    d = db.get(Doctor, doctor_id)
    if d is None:
        raise api_error(404, "not_found", "پزشک پیدا نشد")
    m = await store_upload(request, filename, UNIT_IMAGE_TYPES, UNIT_IMAGE_MAX_MB, cu.id)
    db.add(m)
    db.flush()
    d.photo_media_id = m.id
    audit(db, "doctor.photo_updated", cu.id, "doctor", d.id, client_ip(request))
    db.commit()
    db.refresh(d)
    return _doctor_out(db, d)


@router.delete("/admin/doctors/{doctor_id}", status_code=204)
def delete_doctor(
    doctor_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage")),
    db: Session = Depends(get_db),
):
    d = db.get(Doctor, doctor_id)
    if d is None:
        raise api_error(404, "not_found", "پزشک پیدا نشد")
    if db.scalar(select(func.count()).where(Appointment.doctor_id == d.id)):
        raise api_error(409, "in_use", "این پزشک نوبت ثبت‌شده دارد؛ به جای حذف آن را غیرفعال کنید")
    db.delete(d)
    audit(db, "doctor.deleted", cu.id, "doctor", doctor_id, client_ip(request))
    db.commit()


# ---------------------------------------------------------------- منشی: وقت‌ها و نوبت‌ها


@router.get("/manage/doctors/{doctor_id}/slots", response_model=list[ManageSlotOut])
def manage_slots(
    doctor_id: uuid.UUID,
    start: date,
    days: int = 7,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    svc.managed_doctor(db, cu, doctor_id)
    lo, hi = _check_range(start, days)
    slots = db.scalars(
        select(AppointmentSlot)
        .where(
            AppointmentSlot.doctor_id == doctor_id,
            AppointmentSlot.starts_at >= lo,
            AppointmentSlot.starts_at < hi,
        )
        .order_by(AppointmentSlot.starts_at)
    ).all()
    by_slot: dict[uuid.UUID, list[Appointment]] = {}
    if slots:
        for a in db.scalars(
            select(Appointment)
            .where(Appointment.slot_id.in_([s.id for s in slots]))
            .order_by(Appointment.created_at)
        ).unique():
            by_slot.setdefault(a.slot_id, []).append(a)
    return [
        ManageSlotOut(
            id=s.id,
            starts_at=s.starts_at,
            minutes=s.minutes,
            capacity=s.capacity,
            is_blocked=s.is_blocked,
            appointments=[_manage_appt(a) for a in by_slot.get(s.id, [])],
        )
        for s in slots
    ]


@router.post("/manage/doctors/{doctor_id}/sessions", response_model=SessionOut, status_code=201)
def create_session(
    doctor_id: uuid.UUID,
    body: SessionIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    """وقت‌های یک نوبت کاری (و تکرار آن در هفته‌های بعد). وقت‌هایی که از قبل هست دوباره ساخته نمی‌شود."""
    d = svc.managed_doctor(db, cu, doctor_id)
    minutes = body.minutes or d.visit_minutes
    starts = [
        t
        for w in range(body.repeat_weeks)
        for t in svc.session_starts(body.date + timedelta(weeks=w), body.start, body.end, minutes)
        if t > svc.now()
    ]
    if not starts:
        raise api_error(422, "empty_session", "در این بازه وقتی در آینده ساخته نمی‌شود")
    existing = set(
        db.scalars(
            select(AppointmentSlot.starts_at).where(
                AppointmentSlot.doctor_id == d.id, AppointmentSlot.starts_at.in_(starts)
            )
        )
    )
    new = [t for t in starts if t not in existing]
    db.add_all(
        AppointmentSlot(
            doctor_id=d.id, starts_at=t, minutes=minutes, capacity=body.capacity, created_by=cu.id
        )
        for t in new
    )
    audit(db, "appointment.session_created", cu.id, "doctor", d.id, client_ip(request), slots=len(new))
    db.commit()
    return SessionOut(created=len(new), skipped=len(starts) - len(new))


def _managed_slot(db: Session, cu: CurrentUser, slot_id: uuid.UUID) -> AppointmentSlot:
    slot = db.get(AppointmentSlot, slot_id)
    if slot is None:
        raise api_error(404, "slot_not_found", "این وقت پیدا نشد")
    svc.managed_doctor(db, cu, slot.doctor_id)
    return slot


@router.patch("/manage/slots/{slot_id}", response_model=ManageSlotOut)
def update_slot(
    slot_id: uuid.UUID,
    body: SlotUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    slot = _managed_slot(db, cu, slot_id)
    if body.capacity is not None:
        used = slot.capacity - svc.free_places(db, slot)
        if body.capacity < used:
            raise api_error(409, "capacity_too_low", f"این وقت {used} نوبت دارد؛ ظرفیت کمتر از آن نمی‌شود")
        slot.capacity = body.capacity
    if body.is_blocked is not None:
        slot.is_blocked = body.is_blocked
    audit(db, "appointment.slot_updated", cu.id, "slot", slot.id, client_ip(request))
    db.commit()
    appts = db.scalars(select(Appointment).where(Appointment.slot_id == slot.id)).unique()
    return ManageSlotOut(
        id=slot.id,
        starts_at=slot.starts_at,
        minutes=slot.minutes,
        capacity=slot.capacity,
        is_blocked=slot.is_blocked,
        appointments=[_manage_appt(a) for a in appts],
    )


@router.delete("/manage/slots/{slot_id}", status_code=204)
def delete_slot(
    slot_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    slot = _managed_slot(db, cu, slot_id)
    if db.scalar(select(func.count()).where(Appointment.slot_id == slot.id)):
        raise api_error(409, "slot_in_use", "این وقت نوبت دارد؛ آن را ببندید یا نوبت‌ها را جابجا کنید")
    db.delete(slot)
    audit(db, "appointment.slot_deleted", cu.id, "slot", slot_id, client_ip(request))
    db.commit()


@router.post("/manage/doctors/{doctor_id}/cancel-day", response_model=CancelDayOut)
def cancel_day(
    doctor_id: uuid.UUID,
    body: CancelDayIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    """غیبت پزشک: همه وقت‌های آن روز بسته و نوبت‌ها لغو می‌شوند (مراجع لغو را در اپ می‌بیند)."""
    svc.managed_doctor(db, cu, doctor_id)
    lo, hi = svc.local_day_bounds(body.date)
    slots = db.scalars(
        select(AppointmentSlot).where(
            AppointmentSlot.doctor_id == doctor_id,
            AppointmentSlot.starts_at >= lo,
            AppointmentSlot.starts_at < hi,
        )
    ).all()
    cancelled = 0
    t = svc.now()
    for s in slots:
        s.is_blocked = True
    if slots:
        for a in db.scalars(
            select(Appointment).where(
                Appointment.slot_id.in_([s.id for s in slots]), Appointment.status == "booked"
            )
        ).unique():
            a.status, a.cancelled_at, a.cancelled_by = "cancelled", t, "staff"
            cancelled += 1
    audit(
        db, "appointment.day_cancelled", cu.id, "doctor", doctor_id, client_ip(request), cancelled=cancelled
    )
    db.commit()
    return CancelDayOut(cancelled=cancelled, blocked_slots=len(slots))


@router.post("/manage/appointments", response_model=ManageAppointmentOut, status_code=201)
def staff_book(
    body: StaffBookIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    """نوبت دادن منشی برای مراجع تلفنی یا حضوری. اگر آن موبایل حساب مراجع داشته باشد، نوبت در اپ او هم دیده می‌شود."""
    slot = _managed_slot(db, cu, body.slot_id)
    slot = svc.lock_slot_for_booking(db, slot.id)
    mobile = normalize_mobile(body.patient_mobile)
    if mobile is None:
        raise api_error(422, "invalid_mobile", "شماره موبایل نامعتبر است")
    patient = db.scalar(select(Patient).where(Patient.mobile == mobile))
    a = Appointment(
        slot_id=slot.id,
        doctor_id=slot.doctor_id,
        patient_id=patient.id if patient else None,
        patient_name=body.patient_name.strip(),
        patient_mobile=mobile,
        note=(body.note or "").strip() or None,
        tracking_code=svc.new_tracking_code(db),
        source="secretary",
        created_by=cu.id,
    )
    db.add(a)
    db.flush()
    audit(db, "appointment.booked", cu.id, "appointment", a.id, client_ip(request))
    db.commit()
    db.refresh(a)
    return _manage_appt(a)


def _managed_appt(db: Session, cu: CurrentUser, appointment_id: uuid.UUID) -> Appointment:
    a = db.get(Appointment, appointment_id)
    if a is None:
        raise api_error(404, "not_found", "نوبت پیدا نشد")
    svc.managed_doctor(db, cu, a.doctor_id)
    return a


@router.get("/manage/appointments", response_model=list[ManageAppointmentOut])
def search_appointments(
    q: str = Query(min_length=3, max_length=50),
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    """جستجو با کد پیگیری، موبایل یا نام بیمار در پزشکان قابل مدیریت."""
    term = q.strip()
    mobile = normalize_mobile(term)
    cond = Appointment.tracking_code == term.upper()
    cond = cond | Appointment.patient_name.ilike(f"%{term}%")
    if mobile:
        cond = cond | (Appointment.patient_mobile == mobile)
    stmt = (
        select(Appointment)
        .join(AppointmentSlot, Appointment.slot_id == AppointmentSlot.id)
        .where(cond)
        .order_by(AppointmentSlot.starts_at.desc())
        .limit(50)
    )
    if not cu.has_global("doctor.manage"):
        stmt = stmt.join(DoctorSecretary, DoctorSecretary.doctor_id == Appointment.doctor_id).where(
            DoctorSecretary.user_id == cu.id
        )
    return [_manage_appt(a) for a in db.scalars(stmt).unique()]


@router.post("/manage/appointments/{appointment_id}/move", response_model=ManageAppointmentOut)
def move_appointment(
    appointment_id: uuid.UUID,
    body: MoveIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    """جابجایی نوبت به وقت دیگر همان پزشک یا پزشک دیگری که منشی آن را هم دارد."""
    a = _managed_appt(db, cu, appointment_id)
    if a.status != "booked":
        raise api_error(409, "not_booked", "فقط نوبت فعال جابجا می‌شود")
    target = _managed_slot(db, cu, body.slot_id)
    if target.id == a.slot_id:
        return _manage_appt(a)
    target = svc.lock_slot_for_booking(db, target.id)
    a.slot_id, a.doctor_id, a.reminder_sent_at = target.id, target.doctor_id, None
    audit(db, "appointment.moved", cu.id, "appointment", a.id, client_ip(request))
    db.commit()
    db.refresh(a)
    return _manage_appt(a)


@router.post("/manage/appointments/{appointment_id}/cancel", response_model=ManageAppointmentOut)
def staff_cancel(
    appointment_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    a = _managed_appt(db, cu, appointment_id)
    if a.status != "booked":
        raise api_error(409, "not_booked", "این نوبت فعال نیست")
    a.status, a.cancelled_at, a.cancelled_by = "cancelled", svc.now(), "staff"
    audit(db, "appointment.cancelled", cu.id, "appointment", a.id, client_ip(request))
    db.commit()
    return _manage_appt(a)


def _set_status(db: Session, a: Appointment, status: str) -> None:
    if a.status == "cancelled":
        raise api_error(409, "cancelled", "نوبت لغوشده را نمی‌توان تغییر داد")
    a.status = status
    if status == "attended":
        tokens_svc.on_attended(db, a)


@router.post("/manage/appointments/{appointment_id}/status", response_model=ManageAppointmentOut)
def staff_status(
    appointment_id: uuid.UUID,
    body: StatusIn,
    request: Request,
    cu: CurrentUser = Depends(require("doctor.manage", "appointment.manage")),
    db: Session = Depends(get_db),
):
    """ثبت حضور یا عدم حضور بیمار."""
    a = _managed_appt(db, cu, appointment_id)
    _set_status(db, a, body.status)
    audit(db, "appointment.status", cu.id, "appointment", a.id, client_ip(request), status=body.status)
    db.commit()
    return _manage_appt(a)


# ---------------------------------------------------------------- پزشک


def _my_doctor(db: Session, cu: CurrentUser) -> Doctor:
    d = db.scalar(select(Doctor).where(Doctor.user_id == cu.id))
    if d is None:
        raise api_error(404, "not_doctor", "حساب شما به پرونده پزشکی وصل نیست")
    return d


@router.get("/doctor/me", response_model=DoctorOut)
def doctor_me(cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    return _doctor_out(db, _my_doctor(db, cu))


@router.get("/doctor/me/appointments", response_model=list[ManageAppointmentOut])
def doctor_appointments(
    start: date,
    days: int = 1,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    """فهرست بیماران پزشک در بازه (پیش‌فرض یک روز)، به ترتیب ساعت. نوبت‌های لغوشده هم با وضعیت می‌آیند."""
    d = _my_doctor(db, cu)
    lo, hi = _check_range(start, days)
    rows = db.scalars(
        select(Appointment)
        .join(AppointmentSlot, Appointment.slot_id == AppointmentSlot.id)
        .where(Appointment.doctor_id == d.id, AppointmentSlot.starts_at >= lo, AppointmentSlot.starts_at < hi)
        .order_by(AppointmentSlot.starts_at, Appointment.created_at)
    ).unique()
    return [_manage_appt(a) for a in rows]


@router.post("/doctor/me/appointments/{appointment_id}/status", response_model=ManageAppointmentOut)
def doctor_status(
    appointment_id: uuid.UUID,
    body: StatusIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    d = _my_doctor(db, cu)
    a = db.get(Appointment, appointment_id)
    if a is None or a.doctor_id != d.id:
        raise api_error(404, "not_found", "نوبت پیدا نشد")
    _set_status(db, a, body.status)
    audit(db, "appointment.status", cu.id, "appointment", a.id, client_ip(request), status=body.status)
    db.commit()
    return _manage_appt(a)
