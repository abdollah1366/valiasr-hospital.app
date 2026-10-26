"""ترجمه محتوای عمومی (بخش‌ها، پرسش‌ها، اطلاعیه‌ها، تخصص‌ها، پزشکان، ...) برای زبان‌های مراجعان.

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

from app.models import (
    Announcement,
    ContentTranslation,
    Doctor,
    EducationItem,
    Faq,
    Specialty,
    TokenReward,
    Unit,
)

LANGUAGES = ("az", "en", "ar", "tr")
FIELDS: dict[str, tuple[str, ...]] = {
    "unit": ("name", "location", "hours", "description"),
    "faq": ("question", "answer"),
    "announcement": ("title", "body"),
    "education": ("title", "summary", "body", "condition", "source"),
    "specialty": ("name", "description"),
    "doctor": ("full_name", "title", "bio"),
    "reward": ("title", "description"),
}
MODELS = {
    "unit": Unit,
    "faq": Faq,
    "announcement": Announcement,
    "education": EducationItem,
    "specialty": Specialty,
    "doctor": Doctor,
    "reward": TokenReward,
}

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


def _approved(
    db: Session, entity_type: str, ids: list[uuid.UUID], language: str
) -> dict[uuid.UUID, dict[str, ContentTranslation]]:
    found: dict[uuid.UUID, dict[str, ContentTranslation]] = {}
    if not ids:
        return found
    rows = db.scalars(
        select(ContentTranslation).where(
            ContentTranslation.entity_type == entity_type,
            ContentTranslation.language == language,
            ContentTranslation.status == "approved",
            ContentTranslation.entity_id.in_(ids),
        )
    )
    for r in rows:
        found.setdefault(r.entity_id, {})[r.field] = r
    return found


def _pick(source: str | None, tr: ContentTranslation | None) -> str | None:
    if source and tr is not None and tr.source_hash == source_hash(source):
        return tr.text
    return source


def localize(db: Session, entity_type: str, items: list[T], language: str | None) -> list[T]:
    """فیلدهای ترجمه‌پذیر هر مورد را با ترجمه تأییدشده و به‌روز جایگزین می‌کند."""
    if not language or not items:
        return items
    found = _approved(db, entity_type, [i.id for i in items], language)
    result = []
    for item in items:
        update = {}
        for field, tr in found.get(item.id, {}).items():
            source = getattr(item, field, None)
            text = _pick(source, tr)
            if text != source:
                update[field] = text
        result.append(item.model_copy(update=update) if update else item)
    return result


class Localizer:
    """متن ترجمه‌شده یک فیلد از یک شیء پایگاه داده (مثلاً نام تخصصِ پزشک در کارت پزشک).

    ترجمه‌های هر شیء یک بار خوانده می‌شوند؛ با prefetch می‌توان یک فهرست را با یک پرس‌وجو آماده کرد.
    """

    def __init__(self, db: Session, language: str | None):
        self.db = db
        self.language = language
        self._cache: dict[tuple[str, uuid.UUID], dict[str, ContentTranslation]] = {}

    def prefetch(self, entity_type: str, ids) -> None:
        if not self.language:
            return
        missing = list({i for i in ids if i is not None and (entity_type, i) not in self._cache})
        found = _approved(self.db, entity_type, missing, self.language)
        for i in missing:
            self._cache[(entity_type, i)] = found.get(i, {})

    def text(self, entity_type: str, obj, field: str) -> str | None:
        source = getattr(obj, field, None) if obj is not None else None
        if not self.language or not source:
            return source
        self.prefetch(entity_type, [obj.id])
        return _pick(source, self._cache[(entity_type, obj.id)].get(field))


def field_status(source: str | None, tr: ContentTranslation | None) -> str:
    if tr is None:
        return "missing"
    if source is None or tr.source_hash != source_hash(source):
        return "outdated"
    return tr.status
