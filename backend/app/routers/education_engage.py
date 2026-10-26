"""آموزش به بیمار، بخش تعاملی: فرستادن آموزش برای مراجع مشخص، امتیاز و تیکت با مسئول آموزش.

- مراجع (بیمار یا همراه، با موبایل خودش): آموزش‌های فرستاده‌شده، ثبت دیده شدن، امتیاز، تیکت با پیوست
- مسئول آموزش (education.assign): جست‌وجوی مراجع، فرستادن چند آموزش، دیدن دیده‌شدن و آمار
- پاسخ‌گو (education.ticket): فهرست و پاسخ تیکت‌ها
"""

import uuid
from datetime import UTC, datetime
from pathlib import Path

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, require
from app.models import (
    EducationAssignment,
    EducationItem,
    EducationRating,
    EducationTicket,
    EducationTicketMessage,
    Patient,
    User,
)
from app.routers.education import _list_out, _published, _specialty_names, rating_map
from app.routers.patient import get_current_patient
from app.schemas.education import (
    AssignIn,
    AssignmentRow,
    AssignResultOut,
    EducationPatientRow,
    MyEducationOut,
    MyRatingOut,
    RatingIn,
    TicketAttachmentOut,
    TicketCreateIn,
    TicketDetailOut,
    TicketMessageIn,
    TicketMessageOut,
    TicketOut,
)
from app.services.media import store_upload
from app.services.training import sign_media
from app.services.translations import content_language, localize

router = APIRouter(tags=["education"])

# پیوست تیکت: نوع از پسوند تعیین می‌شود و هر نوع سقف حجم خودش را دارد.
ATTACHMENT_TYPES: dict[str, tuple[str, int]] = {
    ".jpg": ("image/jpeg", 10),
    ".jpeg": ("image/jpeg", 10),
    ".png": ("image/png", 10),
    ".webp": ("image/webp", 10),
    ".mp3": ("audio/mpeg", 20),
    ".m4a": ("audio/mp4", 20),
    ".aac": ("audio/aac", 20),
    ".ogg": ("audio/ogg", 20),
    ".mp4": ("video/mp4", 100),
    ".webm": ("video/webm", 100),
    ".3gp": ("video/3gpp", 100),
}
MAX_OPEN_TICKETS = 10


def _now() -> datetime:
    return datetime.now(UTC)


# ---------------------------------------------------------------- مراجع: آموزش‌های من، دیده شدن، امتیاز


@router.get("/patient/education/mine", response_model=list[MyEducationOut])
def my_education(
    language: str | None = Depends(content_language),
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    rows = db.execute(
        select(EducationAssignment, EducationItem)
        .join(EducationItem, EducationItem.id == EducationAssignment.item_id)
        .where(EducationAssignment.patient_id == patient.id, EducationItem.is_published.is_(True))
        .order_by(EducationAssignment.assigned_at.desc())
    ).all()
    items = [i for _, i in rows]
    names = _specialty_names(db, items, language)
    ratings = rating_map(db, [i.id for i in items])
    out = [
        MyEducationOut(
            **_list_out(i, names, ratings),
            assigned_at=a.assigned_at,
            assignment_note=a.note,
            seen=a.first_seen_at is not None,
        )
        for a, i in rows
    ]
    return localize(db, "education", out, language)


@router.post("/patient/education/{item_id}/seen", status_code=204)
def mark_seen(
    item_id: uuid.UUID,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    """مراجع آموزش فرستاده‌شده را باز کرده؛ برای آموزشی که برایش نفرستاده‌اند کاری نمی‌کند."""
    a = db.scalar(
        select(EducationAssignment).where(
            EducationAssignment.patient_id == patient.id, EducationAssignment.item_id == item_id
        )
    )
    if a is None:
        return
    now = _now()
    a.first_seen_at = a.first_seen_at or now
    a.last_seen_at = now
    a.view_count += 1
    db.commit()


@router.get("/patient/education/{item_id}/rating", response_model=MyRatingOut)
def my_rating(
    item_id: uuid.UUID,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    _published(db, item_id)
    mine = db.scalar(
        select(EducationRating.stars).where(
            EducationRating.item_id == item_id, EducationRating.patient_id == patient.id
        )
    )
    avg, count = rating_map(db, [item_id]).get(item_id, (None, 0))
    return MyRatingOut(stars=mine, rating_average=avg, rating_count=count)


@router.put("/patient/education/{item_id}/rating", response_model=MyRatingOut)
def rate(
    item_id: uuid.UUID,
    body: RatingIn,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    _published(db, item_id)
    r = db.scalar(
        select(EducationRating).where(
            EducationRating.item_id == item_id, EducationRating.patient_id == patient.id
        )
    )
    if r is None:
        r = EducationRating(item_id=item_id, patient_id=patient.id, stars=body.stars)
        db.add(r)
    else:
        r.stars = body.stars
    db.commit()
    avg, count = rating_map(db, [item_id]).get(item_id, (None, 0))
    return MyRatingOut(stars=body.stars, rating_average=avg, rating_count=count)


# ---------------------------------------------------------------- مسئول آموزش: ارسال به مراجع


@router.get("/admin/education/patients", response_model=list[EducationPatientRow])
def search_patients(
    q: str = Query(min_length=3, max_length=20),
    _: CurrentUser = Depends(require("education.assign")),
    db: Session = Depends(get_db),
):
    digits = "".join(c for c in q if c.isdigit()).lstrip("0")
    patients = db.scalars(
        select(Patient).where(Patient.mobile.contains(digits or q)).order_by(Patient.mobile).limit(20)
    ).all()
    out = []
    for p in patients:
        total, seen = db.execute(
            select(func.count(), func.count(EducationAssignment.first_seen_at)).where(
                EducationAssignment.patient_id == p.id
            )
        ).one()
        out.append(
            EducationPatientRow(
                id=p.id,
                mobile=p.mobile,
                full_name=p.full_name,
                assigned_count=total,
                unseen_count=total - seen,
            )
        )
    return out


@router.post("/admin/education/assignments", response_model=AssignResultOut, status_code=201)
def assign(
    body: AssignIn,
    request: Request,
    cu: CurrentUser = Depends(require("education.assign")),
    db: Session = Depends(get_db),
):
    patient = db.get(Patient, body.patient_id)
    if patient is None or not patient.is_active:
        raise api_error(404, "not_found", "مراجع پیدا نشد")
    ids = set(body.item_ids)
    published = set(
        db.scalars(
            select(EducationItem.id).where(EducationItem.id.in_(ids), EducationItem.is_published.is_(True))
        )
    )
    if published != ids:
        raise api_error(400, "not_published", "فقط آموزش‌های منتشرشده را می‌شود فرستاد")
    existing = set(
        db.scalars(
            select(EducationAssignment.item_id).where(
                EducationAssignment.patient_id == patient.id, EducationAssignment.item_id.in_(ids)
            )
        )
    )
    note = (body.note or "").strip() or None
    for item_id in ids - existing:
        db.add(EducationAssignment(patient_id=patient.id, item_id=item_id, assigned_by=cu.id, note=note))
    audit(
        db, "education.assigned", cu.id, "patient", patient.id, client_ip(request), count=len(ids - existing)
    )
    db.commit()
    return AssignResultOut(added=len(ids - existing), already=len(existing))


def _assignment_rows(db: Session, *conds) -> list[AssignmentRow]:
    rows = db.execute(
        select(EducationAssignment, Patient, EducationItem)
        .join(Patient, Patient.id == EducationAssignment.patient_id)
        .join(EducationItem, EducationItem.id == EducationAssignment.item_id)
        .where(*conds)
        .order_by(EducationAssignment.assigned_at.desc())
        .limit(500)
    ).all()
    return [
        AssignmentRow(
            id=a.id,
            patient_id=p.id,
            patient_mobile=p.mobile,
            patient_name=p.full_name,
            item_id=i.id,
            item_title=i.title,
            note=a.note,
            assigned_at=a.assigned_at,
            first_seen_at=a.first_seen_at,
            last_seen_at=a.last_seen_at,
            view_count=a.view_count,
        )
        for a, p, i in rows
    ]


@router.get("/admin/education/assignments", response_model=list[AssignmentRow])
def list_assignments(
    patient_id: uuid.UUID | None = None,
    item_id: uuid.UUID | None = None,
    only_unseen: bool = False,
    _: CurrentUser = Depends(require("education.assign")),
    db: Session = Depends(get_db),
):
    conds = []
    if patient_id:
        conds.append(EducationAssignment.patient_id == patient_id)
    if item_id:
        conds.append(EducationAssignment.item_id == item_id)
    if only_unseen:
        conds.append(EducationAssignment.first_seen_at.is_(None))
    return _assignment_rows(db, *conds)


@router.delete("/admin/education/assignments/{assignment_id}", status_code=204)
def delete_assignment(
    assignment_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("education.assign")),
    db: Session = Depends(get_db),
):
    a = db.get(EducationAssignment, assignment_id)
    if a is None:
        raise api_error(404, "not_found", "این ارسال پیدا نشد")
    audit(db, "education.unassigned", cu.id, "patient", a.patient_id, client_ip(request))
    db.delete(a)
    db.commit()


# ---------------------------------------------------------------- تیکت


def _attachment(msg: EducationTicketMessage) -> TicketAttachmentOut | None:
    m = msg.media
    if m is None:
        return None
    return TicketAttachmentOut(
        original_name=m.original_name, content_type=m.content_type, size=m.size, url=sign_media(m.id)
    )


def _message_out(msg: EducationTicketMessage, staff_names: dict[uuid.UUID, str]) -> TicketMessageOut:
    return TicketMessageOut(
        id=msg.id,
        from_staff=msg.from_staff,
        staff_name=staff_names.get(msg.staff_user_id) if msg.staff_user_id else None,
        body=msg.body,
        attachment=_attachment(msg),
        created_at=msg.created_at,
    )


def _last_text(t: EducationTicket) -> str | None:
    if not t.messages:
        return None
    last = t.messages[-1]
    return (last.body or "").strip()[:80] or None


def _ticket_out(t: EducationTicket, *, staff_view: bool, patient: Patient | None = None) -> TicketOut:
    return TicketOut(
        id=t.id,
        subject=t.subject,
        status=t.status,
        unread=t.staff_unread if staff_view else t.patient_unread,
        created_at=t.created_at,
        updated_at=t.updated_at,
        last_message=_last_text(t),
        patient_mobile=patient.mobile if staff_view and patient else None,
        patient_name=patient.full_name if staff_view and patient else None,
    )


def _ticket_detail(
    db: Session, t: EducationTicket, *, staff_view: bool, patient: Patient | None
) -> TicketDetailOut:
    ids = {m.staff_user_id for m in t.messages if m.staff_user_id}
    names = {u.id: u.full_name for u in db.scalars(select(User).where(User.id.in_(ids)))} if ids else {}
    return TicketDetailOut(
        **_ticket_out(t, staff_view=staff_view, patient=patient).model_dump(),
        messages=[_message_out(m, names) for m in t.messages],
    )


def _own_ticket(db: Session, patient: Patient, ticket_id: uuid.UUID) -> EducationTicket:
    t = db.get(EducationTicket, ticket_id)
    if t is None or t.patient_id != patient.id:
        raise api_error(404, "not_found", "تیکت پیدا نشد")
    return t


def _any_ticket(db: Session, ticket_id: uuid.UUID) -> EducationTicket:
    t = db.get(EducationTicket, ticket_id)
    if t is None:
        raise api_error(404, "not_found", "تیکت پیدا نشد")
    return t


def _check_attachment_name(filename: str) -> tuple[dict[str, str], int]:
    ext = Path(filename.replace("\\", "/")).suffix.lower()
    entry = ATTACHMENT_TYPES.get(ext)
    if entry is None:
        raise api_error(400, "file_type", "فقط عکس، صوت و فیلم پذیرفته می‌شود")
    return {ext: entry[0]}, entry[1]


@router.get("/patient/education/tickets", response_model=list[TicketOut])
def my_tickets(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    tickets = db.scalars(
        select(EducationTicket)
        .where(EducationTicket.patient_id == patient.id)
        .order_by(EducationTicket.updated_at.desc())
    ).all()
    return [_ticket_out(t, staff_view=False) for t in tickets]


@router.post("/patient/education/tickets", response_model=TicketDetailOut, status_code=201)
def create_ticket(
    body: TicketCreateIn,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    open_count = db.scalar(
        select(func.count()).where(EducationTicket.patient_id == patient.id, EducationTicket.status == "open")
    )
    if open_count >= MAX_OPEN_TICKETS:
        raise api_error(409, "too_many_open", "چند تیکت باز دارید؛ ابتدا منتظر پاسخ بمانید")
    now = _now()
    t = EducationTicket(patient_id=patient.id, subject=body.subject.strip(), created_at=now, updated_at=now)
    t.messages.append(EducationTicketMessage(body=body.body.strip(), created_at=now))
    db.add(t)
    db.commit()
    db.refresh(t)
    return _ticket_detail(db, t, staff_view=False, patient=patient)


@router.get("/patient/education/tickets/{ticket_id}", response_model=TicketDetailOut)
def my_ticket(
    ticket_id: uuid.UUID, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)
):
    t = _own_ticket(db, patient, ticket_id)
    if t.patient_unread:
        t.patient_unread = False
        db.commit()
        db.refresh(t)
    return _ticket_detail(db, t, staff_view=False, patient=patient)


def _patient_posts(t: EducationTicket, msg: EducationTicketMessage) -> None:
    now = _now()
    msg.created_at = now
    t.messages.append(msg)
    t.status = "open"
    t.staff_unread = True
    t.updated_at = now


@router.post(
    "/patient/education/tickets/{ticket_id}/messages", response_model=TicketDetailOut, status_code=201
)
def patient_message(
    ticket_id: uuid.UUID,
    body: TicketMessageIn,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    t = _own_ticket(db, patient, ticket_id)
    _patient_posts(t, EducationTicketMessage(body=body.body.strip()))
    db.commit()
    db.refresh(t)
    return _ticket_detail(db, t, staff_view=False, patient=patient)


@router.post(
    "/patient/education/tickets/{ticket_id}/attachment", response_model=TicketDetailOut, status_code=201
)
async def patient_attachment(
    ticket_id: uuid.UUID,
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    caption: str | None = Query(None, max_length=1000),
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    """عکس، صوت یا فیلم؛ بدنه درخواست خود فایل است."""
    t = _own_ticket(db, patient, ticket_id)
    allowed, max_mb = _check_attachment_name(filename)
    media = await store_upload(request, filename, allowed, max_mb, None)
    db.add(media)
    db.flush()
    _patient_posts(t, EducationTicketMessage(body=(caption or "").strip() or None, media_id=media.id))
    db.commit()
    db.refresh(t)
    return _ticket_detail(db, t, staff_view=False, patient=patient)


# ---- کارشناس


@router.get("/admin/education/tickets", response_model=list[TicketOut])
def staff_tickets(
    status: str | None = Query(None, pattern="^(open|closed)$"),
    _: CurrentUser = Depends(require("education.ticket")),
    db: Session = Depends(get_db),
):
    stmt = select(EducationTicket, Patient).join(Patient, Patient.id == EducationTicket.patient_id)
    if status:
        stmt = stmt.where(EducationTicket.status == status)
    rows = db.execute(
        stmt.order_by(EducationTicket.staff_unread.desc(), EducationTicket.updated_at.desc()).limit(300)
    ).all()
    return [_ticket_out(t, staff_view=True, patient=p) for t, p in rows]


@router.get("/admin/education/tickets/{ticket_id}", response_model=TicketDetailOut)
def staff_ticket(
    ticket_id: uuid.UUID,
    _: CurrentUser = Depends(require("education.ticket")),
    db: Session = Depends(get_db),
):
    t = _any_ticket(db, ticket_id)
    if t.staff_unread:
        t.staff_unread = False
        db.commit()
        db.refresh(t)
    return _ticket_detail(db, t, staff_view=True, patient=db.get(Patient, t.patient_id))


def _staff_posts(t: EducationTicket, msg: EducationTicketMessage, user_id: uuid.UUID) -> None:
    now = _now()
    msg.from_staff = True
    msg.staff_user_id = user_id
    msg.created_at = now
    t.messages.append(msg)
    t.patient_unread = True
    t.staff_unread = False
    t.updated_at = now


@router.post("/admin/education/tickets/{ticket_id}/messages", response_model=TicketDetailOut, status_code=201)
def staff_message(
    ticket_id: uuid.UUID,
    body: TicketMessageIn,
    cu: CurrentUser = Depends(require("education.ticket")),
    db: Session = Depends(get_db),
):
    t = _any_ticket(db, ticket_id)
    _staff_posts(t, EducationTicketMessage(body=body.body.strip()), cu.id)
    db.commit()
    db.refresh(t)
    return _ticket_detail(db, t, staff_view=True, patient=db.get(Patient, t.patient_id))


@router.post(
    "/admin/education/tickets/{ticket_id}/attachment", response_model=TicketDetailOut, status_code=201
)
async def staff_attachment(
    ticket_id: uuid.UUID,
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    caption: str | None = Query(None, max_length=1000),
    cu: CurrentUser = Depends(require("education.ticket")),
    db: Session = Depends(get_db),
):
    t = _any_ticket(db, ticket_id)
    allowed, max_mb = _check_attachment_name(filename)
    media = await store_upload(request, filename, allowed, max_mb, cu.id)
    db.add(media)
    db.flush()
    _staff_posts(t, EducationTicketMessage(body=(caption or "").strip() or None, media_id=media.id), cu.id)
    db.commit()
    db.refresh(t)
    return _ticket_detail(db, t, staff_view=True, patient=db.get(Patient, t.patient_id))


@router.post("/admin/education/tickets/{ticket_id}/{action}", response_model=TicketOut)
def staff_ticket_status(
    ticket_id: uuid.UUID,
    action: str,
    request: Request,
    cu: CurrentUser = Depends(require("education.ticket")),
    db: Session = Depends(get_db),
):
    if action not in ("close", "reopen"):
        raise api_error(404, "not_found", "کار نامعتبر است")
    t = _any_ticket(db, ticket_id)
    t.status = "closed" if action == "close" else "open"
    audit(db, f"education.ticket_{action}", cu.id, "education_ticket", t.id, client_ip(request))
    db.commit()
    db.refresh(t)
    return _ticket_out(t, staff_view=True, patient=db.get(Patient, t.patient_id))
