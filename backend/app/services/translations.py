"""ترجمه محتوای عمومی (بخش‌ها، پرسش‌ها، اطلاعیه‌ها) برای زبان‌های مراجعان.

متن اصلی همیشه فارسی است و در جدول خود محتوا می‌ماند. ترجمه فقط وقتی به مراجع نشان داده می‌شود که
تأیید شده باشد و متن فارسی از زمان ترجمه تغییر نکرده باشد؛ در غیر این صورت همان فارسی برمی‌گردد.
"""

import hashlib
import uuid
from typing import TypeVar

from fastapi import Header, Query
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Announcement, ContentTranslation, EducationItem, Faq, Unit

LANGUAGES = ("az", "en", "ar", "tr")
FIELDS: dict[str, tuple[str, ...]] = {
    "unit": ("name", "location", "hours", "description"),
    "faq": ("question", "answer"),
    "announcement": ("title", "body"),
    "education": ("title", "summary", "body", "condition", "source"),
}
MODELS = {"unit": Unit, "faq": Faq, "announcement": Announcement, "education": EducationItem}

T = TypeVar("T", bound=BaseModel)


def source_hash(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def request_language(lang: str | None, accept_language: str | None) -> str | None:
    """زبان درخواست از پارامتر lang یا سرآیند Accept-Language؛ None یعنی فارسی (متن اصلی)."""
    candidates = [lang] if lang else [part.split(";")[0] for part in (accept_language or "").split(",")]
    for c in candidates:
        base = c.strip().lower().split("-")[0]
        if base in LANGUAGES:
            return base
        if base == "fa":
            return None
    return None


def content_language(
    lang: str | None = Query(None, max_length=10, description="az | en | ar | tr؛ خالی یعنی فارسی"),
    accept_language: str | None = Header(None),
) -> str | None:
    """وابستگی FastAPI برای نقطه‌های عمومی: lang در نشانی بر Accept-Language مقدم است."""
    return request_language(lang, accept_language)


def localize(db: Session, entity_type: str, items: list[T], language: str | None) -> list[T]:
    """فیلدهای ترجمه‌پذیر هر مورد را با ترجمه تأییدشده و به‌روز جایگزین می‌کند."""
    if not language or not items:
        return items
    rows = db.scalars(
        select(ContentTranslation).where(
            ContentTranslation.entity_type == entity_type,
            ContentTranslation.language == language,
            ContentTranslation.status == "approved",
            ContentTranslation.entity_id.in_([i.id for i in items]),
        )
    )
    found: dict[uuid.UUID, dict[str, ContentTranslation]] = {}
    for r in rows:
        found.setdefault(r.entity_id, {})[r.field] = r
    result = []
    for item in items:
        update = {}
        for field, tr in found.get(item.id, {}).items():
            source = getattr(item, field, None)
            if source and tr.source_hash == source_hash(source):
                update[field] = tr.text
        result.append(item.model_copy(update=update) if update else item)
    return result


def field_status(source: str | None, tr: ContentTranslation | None) -> str:
    if tr is None:
        return "missing"
    if source is None or tr.source_hash != source_hash(source):
        return "outdated"
    return tr.status
