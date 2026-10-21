"""نظرات و امتیاز مراجعان درباره پزشکان.

- عمومی: میانگین ستاره و نظرهای تأییدشده (بدون نام مراجع)
- مراجع: بعد از ویزیت (نوبت «مراجعه شد») ستاره و نظر می‌دهد، یک بار برای هر نوبت
- مدیر پزشکان (review.moderate): نظرهای در انتظار را تأیید یا رد می‌کند و پاسخ می‌دهد
- مدیر سیستم (review.reply): پاسخ می‌دهد
- پزشک (حساب وصل به پرونده): نظرهای خودش را می‌بیند و پاسخ می‌دهد
"""

import uuid

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, get_active_user, require
from app.models import Appointment, Doctor, DoctorReview, Patient
from app.routers.patient import get_current_patient
from app.schemas.reviews import (
    ManageReviewOut,
    ModerateIn,
    MyReviewOut,
    PublicReviewOut,
    RatingOut,
    ReplyIn,
    ReviewIn,
)
from app.services import appointments as svc
from app.services import notifications as notes
from app.services import reviews as rv

router = APIRouter(tags=["reviews"])

PUBLIC_LIMIT = 50


def manage_out(r: DoctorReview) -> ManageReviewOut:
    return ManageReviewOut(
        id=r.id,
        doctor_id=r.doctor_id,
        doctor_name=r.doctor.full_name,
        stars=r.stars,
        comment=r.comment,
        status=r.status,
        created_at=r.created_at,
        visit_at=r.appointment.slot.starts_at,
        doctor_reply=r.doctor_reply,
        doctor_reply_at=r.doctor_reply_at,
        admin_reply=r.admin_reply,
        admin_reply_at=r.admin_reply_at,
    )


def my_review_out(r: DoctorReview) -> MyReviewOut:
    return MyReviewOut(id=r.id, stars=r.stars, comment=r.comment, status=r.status)


# ---------------------------------------------------------------- عمومی


@router.get("/public/doctors/{doctor_id}/reviews", response_model=dict)
def public_reviews(doctor_id: uuid.UUID, db: Session = Depends(get_db)):
    d = db.get(Doctor, doctor_id)
    if d is None or not d.is_active:
        raise api_error(404, "not_found", "پزشک پیدا نشد")
    avg, count = rv.rating(db, doctor_id)
    rows = db.scalars(
        select(DoctorReview)
        .where(
            DoctorReview.doctor_id == doctor_id,
            DoctorReview.status == "approved",
            DoctorReview.comment.is_not(None),
        )
        .order_by(DoctorReview.created_at.desc())
        .limit(PUBLIC_LIMIT)
    ).unique()
    return {
        "rating": RatingOut(average=avg, count=count).model_dump(),
        "items": [
            PublicReviewOut(
                id=r.id,
                stars=r.stars,
                comment=r.comment,
                created_at=r.created_at,
                doctor_reply=r.doctor_reply,
                admin_reply=r.admin_reply,
            ).model_dump(mode="json")
            for r in rows
        ],
    }


# ---------------------------------------------------------------- مراجع


@router.post("/patient/appointments/{appointment_id}/review", response_model=MyReviewOut, status_code=201)
def patient_review(
    appointment_id: uuid.UUID,
    body: ReviewIn,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    a = db.get(Appointment, appointment_id)
    if a is None or a.patient_id != patient.id:
        raise api_error(404, "not_found", "نوبت پیدا نشد")
    if a.status != "attended":
        raise api_error(409, "not_attended", "نظر را فقط پس از ویزیت می‌توان ثبت کرد")
    if db.scalar(select(DoctorReview.id).where(DoctorReview.appointment_id == a.id)) is not None:
        raise api_error(409, "already_reviewed", "برای این نوبت قبلاً نظر داده‌اید")
    comment = (body.comment or "").strip() or None
    r = DoctorReview(
        appointment_id=a.id,
        doctor_id=a.doctor_id,
        patient_id=patient.id,
        stars=body.stars,
        comment=comment,
        status=rv.initial_status(comment),
    )
    db.add(r)
    db.commit()
    db.refresh(r)
    return my_review_out(r)


# ---------------------------------------------------------------- مدیر پزشکان و مدیر سیستم


@router.get("/manage/reviews", response_model=list[ManageReviewOut])
def manage_reviews(
    status: str | None = Query(default=None, pattern="^(pending|approved|rejected)$"),
    doctor_id: uuid.UUID | None = None,
    _: CurrentUser = Depends(require("review.moderate", "review.reply")),
    db: Session = Depends(get_db),
):
    q = select(DoctorReview).order_by(DoctorReview.created_at.desc()).limit(200)
    if status:
        q = q.where(DoctorReview.status == status)
    if doctor_id:
        q = q.where(DoctorReview.doctor_id == doctor_id)
    return [manage_out(r) for r in db.scalars(q).unique()]


def _review_or_404(db: Session, review_id: uuid.UUID) -> DoctorReview:
    r = db.get(DoctorReview, review_id)
    if r is None:
        raise api_error(404, "not_found", "نظر پیدا نشد")
    return r


@router.post("/manage/reviews/{review_id}/moderate", response_model=ManageReviewOut)
def moderate(
    review_id: uuid.UUID,
    body: ModerateIn,
    request: Request,
    cu: CurrentUser = Depends(require("review.moderate")),
    db: Session = Depends(get_db),
):
    r = _review_or_404(db, review_id)
    r.status = body.status
    r.moderated_by = cu.id
    r.moderated_at = svc.now()
    audit(db, "review.moderate", cu.id, "doctor_review", r.id, client_ip(request), status=body.status)
    if body.status == "approved" and r.comment and r.doctor.user_id:
        notes.notify(
            db,
            [r.doctor.user_id],
            "review",
            "نظر تازه‌ای درباره شما منتشر شد",
            body=f"{r.stars} ستاره",
            link_type="review",
            link_id=r.id,
        )
    db.commit()
    return manage_out(r)


@router.post("/manage/reviews/{review_id}/admin-reply", response_model=ManageReviewOut)
def admin_reply(
    review_id: uuid.UUID,
    body: ReplyIn,
    request: Request,
    cu: CurrentUser = Depends(require("review.moderate", "review.reply")),
    db: Session = Depends(get_db),
):
    r = _review_or_404(db, review_id)
    r.admin_reply = body.text.strip()
    r.admin_reply_at = svc.now()
    r.admin_reply_by = cu.id
    audit(db, "review.admin_reply", cu.id, "doctor_review", r.id, client_ip(request))
    db.commit()
    return manage_out(r)


# ---------------------------------------------------------------- پزشک


def _my_doctor(db: Session, cu: CurrentUser) -> Doctor:
    d = db.scalar(select(Doctor).where(Doctor.user_id == cu.id))
    if d is None:
        raise api_error(404, "not_doctor", "حساب شما به پرونده پزشکی وصل نیست")
    return d


@router.get("/doctor/me/reviews", response_model=list[ManageReviewOut])
def doctor_reviews(cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    """نظرهای خود پزشک: ستاره‌ها و نظرهای تأییدشده (نظر در انتظار تأیید هنوز دیده نمی‌شود)."""
    d = _my_doctor(db, cu)
    rows = db.scalars(
        select(DoctorReview)
        .where(
            DoctorReview.doctor_id == d.id,
            DoctorReview.status == "approved",
        )
        .order_by(DoctorReview.created_at.desc())
        .limit(200)
    ).unique()
    return [manage_out(r) for r in rows]


@router.post("/doctor/me/reviews/{review_id}/reply", response_model=ManageReviewOut)
def doctor_reply(
    review_id: uuid.UUID,
    body: ReplyIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    d = _my_doctor(db, cu)
    r = _review_or_404(db, review_id)
    if r.doctor_id != d.id or r.status != "approved":
        raise api_error(404, "not_found", "نظر پیدا نشد")
    r.doctor_reply = body.text.strip()
    r.doctor_reply_at = svc.now()
    audit(db, "review.doctor_reply", cu.id, "doctor_review", r.id, client_ip(request))
    db.commit()
    return manage_out(r)
