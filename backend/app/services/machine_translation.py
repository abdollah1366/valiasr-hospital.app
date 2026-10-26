"""ترجمه ماشینی محتوای عمومی برای فیلدهایی که ترجمه دستی ندارند.

ترجمه یک بار ساخته و در content_translations با وضعیت «machine» ذخیره می‌شود (هزینه فقط یک بار)؛
بیمارستان در پنل آن را بازبینی و تأیید یا اصلاح می‌کند. ترجمه دستی (پیش‌نویس یا تأییدشده) هرگز
با ترجمه ماشینی جایگزین نمی‌شود، مگر متن فارسی‌اش عوض شده و ترجمه کهنه شده باشد.
"""

import logging
import threading
import uuid
from concurrent.futures import Future, ThreadPoolExecutor

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.db import SessionLocal
from app.models import ContentTranslation
from app.services.translations import FIELDS, MODELS, field_status, source_hash

log = logging.getLogger(__name__)

SOURCE_LANGUAGE = "fa"
# بیشینه نویسه هر درخواست (توصیه Google برای v2 حدود ۵۰۰۰ نویسه است)
MAX_REQUEST_CHARS = 4500


class TranslationUnavailable(Exception):
    pass


def enabled() -> bool:
    return get_settings().mt_provider in ("google", "libretranslate", "fake")


def _google(texts: list[str], target: str) -> list[str]:
    s = get_settings()
    r = httpx.post(
        "https://translation.googleapis.com/language/translate/v2",
        params={"key": s.mt_api_key},
        json={"q": texts, "source": SOURCE_LANGUAGE, "target": target, "format": "text"},
        timeout=s.mt_timeout_seconds,
    )
    r.raise_for_status()
    return [t["translatedText"] for t in r.json()["data"]["translations"]]


def _libretranslate(texts: list[str], target: str) -> list[str]:
    s = get_settings()
    body = {"q": texts, "source": SOURCE_LANGUAGE, "target": target, "format": "text"}
    if s.mt_api_key:
        body["api_key"] = s.mt_api_key
    r = httpx.post(f"{s.mt_base_url.rstrip('/')}/translate", json=body, timeout=s.mt_timeout_seconds)
    r.raise_for_status()
    out = r.json()["translatedText"]
    return out if isinstance(out, list) else [out]


def _fake(texts: list[str], target: str) -> list[str]:
    return [f"[{target}] {t}" for t in texts]


_PROVIDERS = {"google": _google, "libretranslate": _libretranslate, "fake": _fake}


def _pieces(text: str) -> list[str]:
    """متن بلند (مثل مقاله آموزشی) در مرز خط‌ها به تکه‌های کوچک‌تر از سقف درخواست شکسته می‌شود."""
    if len(text) <= MAX_REQUEST_CHARS:
        return [text]
    pieces, current = [], ""
    for line in text.split("\n"):
        while len(line) > MAX_REQUEST_CHARS:
            pieces.append(line[:MAX_REQUEST_CHARS])
            line = line[MAX_REQUEST_CHARS:]
        if current and len(current) + len(line) + 1 > MAX_REQUEST_CHARS:
            pieces.append(current)
            current = line
        else:
            current = f"{current}\n{line}" if current else line
    pieces.append(current)
    return pieces


def translate(texts: list[str], target: str) -> list[str]:
    """ترجمه گروهی از فارسی به زبان مقصد؛ هر درخواست زیر سقف نویسه می‌ماند."""
    provider = _PROVIDERS.get(get_settings().mt_provider)
    if provider is None:
        raise TranslationUnavailable("ترجمه ماشینی تنظیم نشده است")
    # هر متن به تکه‌ها؛ تکه‌ها در درخواست‌هایی زیر سقف دسته می‌شوند و دوباره کنار هم گذاشته می‌شوند.
    owners: list[int] = []
    pieces: list[str] = []
    for i, t in enumerate(texts):
        for p in _pieces(t):
            owners.append(i)
            pieces.append(p)
    results: list[str] = []
    batch: list[str] = []
    size = 0
    try:
        for p in pieces:
            if batch and size + len(p) > MAX_REQUEST_CHARS:
                results += provider(batch, target)
                batch, size = [], 0
            batch.append(p)
            size += len(p)
        if batch:
            results += provider(batch, target)
    except (httpx.HTTPError, KeyError, ValueError, TypeError) as e:
        raise TranslationUnavailable(str(e)) from e
    joined = [""] * len(texts)
    for i, r in zip(owners, results, strict=True):
        joined[i] = f"{joined[i]}\n{r}" if joined[i] else r
    return joined


def fill(db: Session, entity_type: str, objs: list, language: str) -> int:
    """فیلدهای بدون ترجمه (یا با ترجمه کهنه) را ماشینی ترجمه و ذخیره می‌کند؛ تعداد فیلدهای ترجمه‌شده."""
    if not objs:
        return 0
    rows = db.scalars(
        select(ContentTranslation).where(
            ContentTranslation.entity_type == entity_type,
            ContentTranslation.language == language,
            ContentTranslation.entity_id.in_([o.id for o in objs]),
        )
    )
    existing = {(r.entity_id, r.field): r for r in rows}
    todo: list[tuple[object, str, str, ContentTranslation | None]] = []
    for o in objs:
        for f in FIELDS[entity_type]:
            source = getattr(o, f, None)
            if not source or not source.strip():
                continue
            tr = existing.get((o.id, f))
            if tr is None or field_status(source, tr) == "outdated":
                todo.append((o, f, source, tr))
    if not todo:
        return 0
    texts = translate([t[2] for t in todo], language)
    for (o, f, source, tr), text in zip(todo, texts, strict=True):
        if tr is None:
            tr = ContentTranslation(entity_type=entity_type, entity_id=o.id, language=language, field=f)
            db.add(tr)
        tr.text = text.strip()
        tr.source_hash = source_hash(source)
        tr.status = "machine"
        tr.updated_by = None
        tr.approved_by = None
        tr.approved_at = None
    db.commit()
    return len(todo)


# ---------------------------------------------------------------- ترجمه در پس‌زمینه هنگام نمایش

_executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="mt")
_lock = threading.Lock()
_pending: dict[tuple[str, str, uuid.UUID], Future] = {}


def _fill_one(entity_type: str, language: str, entity_id: uuid.UUID) -> None:
    try:
        with SessionLocal() as db:
            obj = db.get(MODELS[entity_type], entity_id)
            if obj is not None:
                fill(db, entity_type, [obj], language)
    except TranslationUnavailable as e:
        log.warning("machine translation failed for %s %s: %s", entity_type, entity_id, e)
    except Exception:
        log.exception("machine translation failed for %s %s", entity_type, entity_id)
    finally:
        with _lock:
            _pending.pop((entity_type, language, entity_id), None)


def schedule(entity_type: str, ids, language: str) -> None:
    """ترجمه ماشینی محتوایی که مراجع الان بدون ترجمه دیده، برای دفعه بعد (بدون کند کردن پاسخ)."""
    s = get_settings()
    if not s.mt_auto or not enabled():
        return
    with _lock:
        for i in ids:
            key = (entity_type, language, i)
            if key not in _pending:
                _pending[key] = _executor.submit(_fill_one, entity_type, language, i)


def drain() -> None:
    """منتظر ماندن تا ترجمه‌های در صف تمام شوند (برای آزمون و فرمان خط فرمان)."""
    while True:
        with _lock:
            futures = list(_pending.values())
        if not futures:
            return
        for f in futures:
            f.result()
