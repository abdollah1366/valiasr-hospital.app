"""ثبت و تأیید ترجمه محتوای عمومی در پنل مدیریت."""

from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, require
from app.models import Announcement, ContentTranslation, Faq, Unit
from app.schemas import (
    TranslationEntity,
    TranslationFieldOut,
    TranslationIn,
    TranslationItemOut,
    TranslationKey,
    TranslationLanguage,
)
from app.services.translations import FIELDS, MODELS, field_status, source_hash

router = APIRouter(prefix="/admin/translations", tags=["translations"])


def _entities(db: Session, entity_type: str) -> list:
    """فقط محتوایی که مراجعان می‌بینند ترجمه می‌شود."""
    if entity_type == "unit":
        return list(db.scalars(select(Unit).where(Unit.is_public.is_(True)).order_by(Unit.name)))
    if entity_type == "faq":
        return list(db.scalars(select(Faq).order_by(Faq.sort_order, Faq.question)))
    return list(
        db.scalars(
            select(Announcement)
            .where(Announcement.audience == "public")
            .order_by(Announcement.published_at.desc())
        )
    )


_LABEL_FIELD = {"unit": "name", "faq": "question", "announcement": "title"}


def _source(db: Session, key: TranslationKey) -> str | None:
    if key.field not in FIELDS[key.entity_type]:
        raise api_error(400, "invalid_field", "این فیلد ترجمه‌پذیر نیست")
    obj = db.get(MODELS[key.entity_type], key.entity_id)
    if obj is None:
        raise api_error(404, "not_found", "محتوا پیدا نشد")
    return getattr(obj, key.field)


def _row(db: Session, key: TranslationKey) -> ContentTranslation | None:
    return db.scalar(
        select(ContentTranslation).where(
            ContentTranslation.entity_type == key.entity_type,
            ContentTranslation.entity_id == key.entity_id,
            ContentTranslation.language == key.language,
            ContentTranslation.field == key.field,
        )
    )


@router.get("", response_model=list[TranslationItemOut])
def list_translations(
    entity_type: TranslationEntity,
    language: TranslationLanguage,
    _: CurrentUser = Depends(require("translation.manage")),
    db: Session = Depends(get_db),
):
    objs = _entities(db, entity_type)
    rows = db.scalars(
        select(ContentTranslation).where(
            ContentTranslation.entity_type == entity_type,
            ContentTranslation.language == language,
            ContentTranslation.entity_id.in_([o.id for o in objs]),
        )
    )
    by_key = {(r.entity_id, r.field): r for r in rows}
    items = []
    for o in objs:
        fields = []
        for f in FIELDS[entity_type]:
            source = getattr(o, f)
            if not source:
                continue
            tr = by_key.get((o.id, f))
            fields.append(
                TranslationFieldOut(
                    field=f, source=source, text=tr.text if tr else None, status=field_status(source, tr)
                )
            )
        items.append(
            TranslationItemOut(
                entity_type=entity_type,
                entity_id=o.id,
                label=getattr(o, _LABEL_FIELD[entity_type]),
                fields=fields,
            )
        )
    return items


@router.put("", response_model=TranslationFieldOut)
def save_translation(
    body: TranslationIn,
    request: Request,
    cu: CurrentUser = Depends(require("translation.manage")),
    db: Session = Depends(get_db),
):
    """ذخیره پیش‌نویس؛ هر ویرایش ترجمه را دوباره به تأیید نیاز می‌اندازد."""
    source = _source(db, body)
    if not source:
        raise api_error(400, "empty_source", "متن فارسی این فیلد خالی است")
    tr = _row(db, body)
    if tr is None:
        tr = ContentTranslation(
            entity_type=body.entity_type, entity_id=body.entity_id, language=body.language, field=body.field
        )
        db.add(tr)
    tr.text = body.text.strip()
    tr.source_hash = source_hash(source)
    tr.status = "draft"
    tr.updated_by = cu.id
    tr.approved_by = None
    tr.approved_at = None
    audit(
        db,
        "translation.saved",
        cu.id,
        body.entity_type,
        body.entity_id,
        client_ip(request),
        language=body.language,
        field=body.field,
    )
    db.commit()
    return TranslationFieldOut(field=body.field, source=source, text=tr.text, status="draft")


@router.post("/approve", response_model=TranslationFieldOut)
def approve_translation(
    body: TranslationKey,
    request: Request,
    cu: CurrentUser = Depends(require("translation.manage")),
    db: Session = Depends(get_db),
):
    source = _source(db, body)
    tr = _row(db, body)
    if tr is None:
        raise api_error(404, "not_found", "ترجمه‌ای برای تأیید ثبت نشده است")
    if field_status(source, tr) == "outdated":
        # متن فارسی بعد از ترجمه عوض شده؛ تأیید ترجمه قدیمی یعنی نشان دادن متن نادرست.
        raise api_error(
            409, "source_changed", "متن فارسی تغییر کرده است؛ ترجمه را بازبینی و دوباره ذخیره کنید"
        )
    tr.status = "approved"
    tr.approved_by = cu.id
    tr.approved_at = datetime.now(UTC)
    audit(
        db,
        "translation.approved",
        cu.id,
        body.entity_type,
        body.entity_id,
        client_ip(request),
        language=body.language,
        field=body.field,
    )
    db.commit()
    return TranslationFieldOut(field=body.field, source=source, text=tr.text, status="approved")
