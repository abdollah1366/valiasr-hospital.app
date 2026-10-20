import uuid

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import FileResponse
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, require
from app.core.security import new_tracking_code
from app.models import ContentTranslation, Faq, Feedback, MediaFile, Unit
from app.schemas import (
    FaqIn,
    FaqOut,
    FeedbackCreatedOut,
    FeedbackIn,
    FeedbackOut,
    FeedbackPublicOut,
    FeedbackRespondIn,
    UnitOut,
)
from app.services.training import media_path
from app.services.translations import content_language, localize

router = APIRouter(tags=["public"])


@router.get("/public/units", response_model=list[UnitOut])
def public_units(language: str | None = Depends(content_language), db: Session = Depends(get_db)):
    units = db.scalars(select(Unit).where(Unit.is_public.is_(True)).order_by(Unit.type, Unit.name))
    return localize(db, "unit", [UnitOut.model_validate(u) for u in units], language)


@router.get("/public/units/{unit_id}/image", include_in_schema=False)
def public_unit_image(unit_id: uuid.UUID, db: Session = Depends(get_db)):
    unit = db.get(Unit, unit_id)
    m = db.get(MediaFile, unit.image_media_id) if unit and unit.is_public and unit.image_media_id else None
    if m is None:
        raise api_error(404, "not_found", "تصویری برای این واحد نیست")
    return FileResponse(
        media_path(m),
        media_type=m.content_type,
        headers={"X-Content-Type-Options": "nosniff", "Cache-Control": "public, max-age=3600"},
    )


@router.get("/units", response_model=list[UnitOut], tags=["units"])
def all_units(_: CurrentUser = Depends(require("incident.create")), db: Session = Depends(get_db)):
    """فهرست همه واحدها برای انتخاب در فرم‌های کارکنان."""
    return db.scalars(select(Unit).order_by(Unit.name)).all()


@router.get("/public/faqs", response_model=list[FaqOut])
def public_faqs(language: str | None = Depends(content_language), db: Session = Depends(get_db)):
    faqs = db.scalars(select(Faq).order_by(Faq.sort_order, Faq.question))
    return localize(db, "faq", [FaqOut.model_validate(f) for f in faqs], language)


@router.post("/admin/faqs", response_model=FaqOut, status_code=201)
def create_faq(
    body: FaqIn,
    request: Request,
    cu: CurrentUser = Depends(require("faq.manage")),
    db: Session = Depends(get_db),
):
    faq = Faq(**body.model_dump())
    db.add(faq)
    db.flush()
    audit(db, "faq.created", cu.id, "faq", faq.id, client_ip(request))
    db.commit()
    return faq


@router.delete("/admin/faqs/{faq_id}", status_code=204)
def delete_faq(
    faq_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("faq.manage")),
    db: Session = Depends(get_db),
):
    faq = db.get(Faq, faq_id)
    if faq is None:
        raise api_error(404, "not_found", "پرسش پیدا نشد")
    db.delete(faq)
    db.execute(
        delete(ContentTranslation).where(
            ContentTranslation.entity_type == "faq", ContentTranslation.entity_id == faq_id
        )
    )
    audit(db, "faq.deleted", cu.id, "faq", faq_id, client_ip(request))
    db.commit()


@router.post("/public/feedback", response_model=FeedbackCreatedOut, status_code=201)
def submit_feedback(body: FeedbackIn, db: Session = Depends(get_db)):
    if body.unit_id and db.get(Unit, body.unit_id) is None:
        raise api_error(400, "unknown_unit", "بخش انتخاب‌شده نامعتبر است")
    fb = Feedback(tracking_code=new_tracking_code("FB"), **body.model_dump())
    db.add(fb)
    db.commit()
    return FeedbackCreatedOut(tracking_code=fb.tracking_code)


@router.get("/public/feedback/{tracking_code}", response_model=FeedbackPublicOut)
def track_feedback(tracking_code: str, db: Session = Depends(get_db)):
    fb = db.scalar(select(Feedback).where(Feedback.tracking_code == tracking_code.strip().upper()))
    if fb is None:
        raise api_error(404, "not_found", "موردی با این کد پیگیری پیدا نشد")
    return FeedbackPublicOut(
        tracking_code=fb.tracking_code,
        kind=fb.kind,
        status=fb.status,
        response=fb.response,
        created_at=fb.created_at,
    )


@router.get("/admin/feedback", response_model=list[FeedbackOut])
def list_feedback(
    status: str | None = None,
    limit: int = Query(100, ge=1, le=500),
    _: CurrentUser = Depends(require("feedback.manage")),
    db: Session = Depends(get_db),
):
    stmt = select(Feedback)
    if status:
        stmt = stmt.where(Feedback.status == status)
    return db.scalars(stmt.order_by(Feedback.created_at.desc()).limit(limit)).all()


@router.post("/admin/feedback/{feedback_id}/respond", response_model=FeedbackOut)
def respond_feedback(
    feedback_id: uuid.UUID,
    body: FeedbackRespondIn,
    request: Request,
    cu: CurrentUser = Depends(require("feedback.manage")),
    db: Session = Depends(get_db),
):
    fb = db.get(Feedback, feedback_id)
    if fb is None:
        raise api_error(404, "not_found", "مورد پیدا نشد")
    if body.status == "answered" and not body.response:
        raise api_error(400, "response_required", "برای پاسخ‌دادن، متن پاسخ لازم است")
    fb.status = body.status
    if body.response is not None:
        fb.response = body.response
    audit(db, "feedback.responded", cu.id, "feedback", fb.id, client_ip(request), status=body.status)
    db.commit()
    return fb
