"""اعلان‌های درون‌برنامه‌ای: ساخت در همان تراکنش رویداد اصلی، بدون commit.

متن اعلان کوتاه است و هرگز داده بیمار یا پاسخ فرم گزارش خطا ندارد؛ ساخت و خواندن اعلان هم در لاگ حسابرسی ثبت نمی‌شود.
"""

import uuid
from collections.abc import Iterable
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, select, update
from sqlalchemy.orm import Session

from app.models import Notification

KEEP_READ_FOR = timedelta(days=90)


def _short(text: str | None, limit: int) -> str | None:
    text = " ".join((text or "").split())
    if not text:
        return None
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def notify(
    db: Session,
    user_ids: Iterable[uuid.UUID | None],
    kind: str,
    title: str,
    *,
    body: str | None = None,
    link_type: str | None = None,
    link_id: uuid.UUID | None = None,
    important: bool = False,
    at: datetime | None = None,
    actor_id: uuid.UUID | None = None,
    once: bool = False,
) -> int:
    """برای هر گیرنده (به جز خود انجام‌دهنده) یک اعلان می‌سازد و تعداد را برمی‌گرداند.

    once: کسی که قبلاً همین اعلان (همان kind و link_id) را گرفته دوباره نمی‌گیرد؛ مثلاً وقتی مخاطب دوره بعداً گسترش می‌یابد.
    """
    recipients = {u for u in user_ids if u is not None and u != actor_id}
    if once and recipients:
        recipients -= set(
            db.scalars(
                select(Notification.user_id).where(Notification.kind == kind, Notification.link_id == link_id)
            )
        )
    when = at or datetime.now(UTC)
    db.add_all(
        Notification(
            user_id=u,
            kind=kind,
            title=_short(title, 300) or "",
            body=_short(body, 200),
            link_type=link_type,
            link_id=link_id,
            important=important,
            created_at=when,
        )
        for u in recipients
    )
    return len(recipients)


def reschedule(db: Session, kind: str, link_id: uuid.UUID, at: datetime, now: datetime) -> None:
    """اعلان زمان‌داری که هنوز نرسیده به زمان تازه منتقل می‌شود (مثلاً تغییر زمان باز شدن آزمون)."""
    db.execute(
        update(Notification)
        .where(Notification.kind == kind, Notification.link_id == link_id, Notification.created_at > now)
        .values(created_at=at)
    )


def retract(
    db: Session, link_id: uuid.UUID, *, only_future: bool = False, now: datetime | None = None
) -> None:
    """اعلان‌های خوانده‌نشده یک مورد حذف می‌شوند (مثلاً اطلاعیه منقضی‌شده یا آزمون برگشته به پیش‌نویس)."""
    stmt = delete(Notification).where(Notification.link_id == link_id, Notification.read_at.is_(None))
    if only_future:
        stmt = stmt.where(Notification.created_at > (now or datetime.now(UTC)))
    db.execute(stmt)


def purge_old(db: Session, now: datetime) -> None:
    db.execute(
        delete(Notification).where(
            Notification.read_at.is_not(None), Notification.read_at < now - KEEP_READ_FOR
        )
    )
