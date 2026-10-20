"""اعلان‌های هر کاربر؛ هر کس فقط اعلان‌های خودش را می‌بیند. دسترسی به خود مورد هنگام باز کردن دوباره بررسی می‌شود."""

import uuid
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, get_active_user
from app.models import Notification
from app.schemas.notifications import NotificationOut, NotificationPage, UnreadCountOut
from app.services import notifications as svc

router = APIRouter(tags=["notifications"])


def _mine(cu: CurrentUser, now: datetime):
    return (Notification.user_id == cu.id, Notification.created_at <= now)


def _out(n: Notification) -> NotificationOut:
    return NotificationOut(
        id=n.id,
        kind=n.kind,
        title=n.title,
        body=n.body,
        link_type=n.link_type,
        link_id=n.link_id,
        important=n.important,
        created_at=n.created_at,
        read=n.read_at is not None,
    )


def _unread(db: Session, cu: CurrentUser, now: datetime, important_only: bool = False) -> int:
    stmt = (
        select(func.count()).select_from(Notification).where(*_mine(cu, now), Notification.read_at.is_(None))
    )
    if important_only:
        stmt = stmt.where(Notification.important.is_(True))
    return db.scalar(stmt) or 0


@router.get("/notifications", response_model=NotificationPage)
def list_notifications(
    before: datetime | None = None,
    unread_only: bool = False,
    limit: int = Query(30, ge=1, le=100),
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    now = datetime.now(UTC)
    svc.purge_old(db, now)
    db.commit()
    stmt = select(Notification).where(*_mine(cu, now))
    if before is not None:
        stmt = stmt.where(Notification.created_at < before)
    if unread_only:
        stmt = stmt.where(Notification.read_at.is_(None))
    rows = list(db.scalars(stmt.order_by(Notification.created_at.desc(), Notification.id).limit(limit + 1)))
    more = len(rows) > limit
    rows = rows[:limit]
    return NotificationPage(
        items=[_out(n) for n in rows],
        unread=_unread(db, cu, now),
        next_before=rows[-1].created_at if more else None,
    )


@router.get("/notifications/unread-count", response_model=UnreadCountOut)
def unread_count(cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    now = datetime.now(UTC)
    return UnreadCountOut(count=_unread(db, cu, now), important=_unread(db, cu, now, important_only=True))


@router.post("/notifications/{notification_id}/read", status_code=204)
def mark_read(
    notification_id: uuid.UUID, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)
):
    n = db.get(Notification, notification_id)
    if n is None or n.user_id != cu.id or n.created_at > datetime.now(UTC):
        raise api_error(404, "not_found", "اعلان پیدا نشد")
    n.read_at = n.read_at or datetime.now(UTC)
    db.commit()
    return Response(status_code=204)


@router.post("/notifications/read-all", status_code=204)
def mark_all_read(cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    now = datetime.now(UTC)
    db.execute(
        update(Notification).where(*_mine(cu, now), Notification.read_at.is_(None)).values(read_at=now)
    )
    db.commit()
    return Response(status_code=204)
