import uuid
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import or_, select
from sqlalchemy.orm import Session, selectinload

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, build_current_user, client_ip, require
from app.models import Announcement, AnnouncementRead, User
from app.schemas import AnnouncementIn, AnnouncementOut, ReadStatsOut
from app.services import notifications as notes
from app.services.translations import content_language, localize

router = APIRouter(tags=["announcements"])


def _published(stmt, now: datetime):
    return stmt.where(
        Announcement.published_at <= now,
        or_(Announcement.expires_at.is_(None), Announcement.expires_at > now),
    )


def _targets(a: Announcement, role_codes: set[str], unit_id: uuid.UUID | None) -> bool:
    if a.audience == "public":
        return True
    if a.target_role_codes and not role_codes.intersection(a.target_role_codes):
        return False
    if a.target_unit_ids and (unit_id is None or str(unit_id) not in a.target_unit_ids):
        return False
    return True


def _staff(db: Session) -> list[tuple[User, CurrentUser]]:
    """کاربران فعالی که اطلاعیه داخلی می‌خوانند."""
    users = db.scalars(select(User).where(User.is_active.is_(True)).options(selectinload(User.roles)))
    return [(u, cu) for u in users if (cu := build_current_user(u)).has("announcement.read")]


def _out(a: Announcement, read: AnnouncementRead | None = None, with_state: bool = False) -> AnnouncementOut:
    out = AnnouncementOut(
        id=a.id,
        title=a.title,
        body=a.body,
        category=a.category,
        audience=a.audience,
        is_important=a.is_important,
        requires_ack=a.requires_ack,
        published_at=a.published_at,
        expires_at=a.expires_at,
    )
    if with_state:
        out.read = read is not None
        out.acknowledged = read is not None and read.acknowledged_at is not None
    return out


@router.get("/public/announcements", response_model=list[AnnouncementOut], tags=["public"])
def public_announcements(
    limit: int = Query(30, ge=1, le=100),
    language: str | None = Depends(content_language),
    db: Session = Depends(get_db),
):
    stmt = _published(select(Announcement).where(Announcement.audience == "public"), datetime.now(UTC))
    items = [_out(a) for a in db.scalars(stmt.order_by(Announcement.published_at.desc()).limit(limit))]
    return localize(db, "announcement", items, language)


@router.get("/announcements", response_model=list[AnnouncementOut])
def staff_announcements(
    only_pending_ack: bool = False,
    cu: CurrentUser = Depends(require("announcement.read")),
    db: Session = Depends(get_db),
):
    stmt = _published(select(Announcement), datetime.now(UTC)).order_by(
        Announcement.is_important.desc(), Announcement.published_at.desc()
    )
    reads = {
        r.announcement_id: r
        for r in db.scalars(select(AnnouncementRead).where(AnnouncementRead.user_id == cu.id))
    }
    result = []
    for a in db.scalars(stmt.limit(300)):
        if not _targets(a, cu.role_codes, cu.user.unit_id):
            continue
        r = reads.get(a.id)
        if only_pending_ack and (not a.requires_ack or (r and r.acknowledged_at)):
            continue
        result.append(_out(a, r, with_state=True))
    return result


def _visible_for(db: Session, cu: CurrentUser, announcement_id: uuid.UUID) -> Announcement:
    a = db.get(Announcement, announcement_id)
    now = datetime.now(UTC)
    if a is None or a.published_at > now or not _targets(a, cu.role_codes, cu.user.unit_id):
        raise api_error(404, "not_found", "اطلاعیه پیدا نشد")
    return a


def _mark(db: Session, cu: CurrentUser, announcement_id: uuid.UUID, ack: bool) -> AnnouncementOut:
    a = _visible_for(db, cu, announcement_id)
    r = db.scalar(
        select(AnnouncementRead).where(
            AnnouncementRead.announcement_id == a.id, AnnouncementRead.user_id == cu.id
        )
    )
    if r is None:
        r = AnnouncementRead(announcement_id=a.id, user_id=cu.id)
        db.add(r)
    if ack and r.acknowledged_at is None:
        r.acknowledged_at = datetime.now(UTC)
    db.commit()
    return _out(a, r, with_state=True)


@router.post("/announcements/{announcement_id}/read", response_model=AnnouncementOut)
def mark_read(
    announcement_id: uuid.UUID,
    cu: CurrentUser = Depends(require("announcement.read")),
    db: Session = Depends(get_db),
):
    return _mark(db, cu, announcement_id, ack=False)


@router.post("/announcements/{announcement_id}/ack", response_model=AnnouncementOut)
def acknowledge(
    announcement_id: uuid.UUID,
    cu: CurrentUser = Depends(require("announcement.read")),
    db: Session = Depends(get_db),
):
    return _mark(db, cu, announcement_id, ack=True)


@router.post("/admin/announcements", response_model=AnnouncementOut, status_code=201)
def create_announcement(
    body: AnnouncementIn,
    request: Request,
    cu: CurrentUser = Depends(require("announcement.manage")),
    db: Session = Depends(get_db),
):
    data = body.model_dump()
    data["target_unit_ids"] = [str(u) for u in body.target_unit_ids]
    if data["published_at"] is None:
        data["published_at"] = datetime.now(UTC)
    if body.audience == "public" and (body.target_role_codes or body.target_unit_ids or body.requires_ack):
        raise api_error(
            400, "invalid_target", "اطلاعیه عمومی نمی‌تواند هدف‌گذاری داخلی یا تأیید مطالعه داشته باشد"
        )
    a = Announcement(author_id=cu.id, **data)
    db.add(a)
    db.flush()
    if a.audience == "staff":
        notes.notify(
            db,
            [u.id for u, ucu in _staff(db) if _targets(a, ucu.role_codes, u.unit_id)],
            "announcement",
            a.title,
            body=a.body,
            link_type="announcement",
            link_id=a.id,
            important=a.is_important or a.requires_ack,
            at=a.published_at,
            actor_id=cu.id,
        )
    audit(db, "announcement.created", cu.id, "announcement", a.id, client_ip(request))
    db.commit()
    return _out(a)


@router.get("/admin/announcements", response_model=list[AnnouncementOut])
def admin_announcements(
    limit: int = Query(200, ge=1, le=500),
    _: CurrentUser = Depends(require("announcement.manage")),
    db: Session = Depends(get_db),
):
    """همه اطلاعیه‌ها (عمومی و داخلی، منتشرشده، زمان‌بندی‌شده و منقضی) برای پنل مدیریت."""
    stmt = select(Announcement).order_by(Announcement.published_at.desc()).limit(limit)
    return [_out(a) for a in db.scalars(stmt)]


@router.delete("/admin/announcements/{announcement_id}", status_code=204)
def expire_announcement(
    announcement_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("announcement.manage")),
    db: Session = Depends(get_db),
):
    """اطلاعیه حذف نمی‌شود تا سابقه تأیید مطالعه بماند؛ فقط منقضی می‌شود."""
    a = db.get(Announcement, announcement_id)
    if a is None:
        raise api_error(404, "not_found", "اطلاعیه پیدا نشد")
    a.expires_at = datetime.now(UTC)
    notes.retract(db, a.id)
    audit(db, "announcement.expired", cu.id, "announcement", a.id, client_ip(request))
    db.commit()


@router.get("/admin/announcements/{announcement_id}/reads", response_model=ReadStatsOut)
def read_stats(
    announcement_id: uuid.UUID,
    _: CurrentUser = Depends(require("announcement.manage")),
    db: Session = Depends(get_db),
):
    a = db.get(Announcement, announcement_id)
    if a is None:
        raise api_error(404, "not_found", "اطلاعیه پیدا نشد")
    if a.audience == "public":
        raise api_error(400, "public_announcement", "برای اطلاعیه عمومی آمار مطالعه ثبت نمی‌شود")
    reads = {
        r.user_id: r
        for r in db.scalars(select(AnnouncementRead).where(AnnouncementRead.announcement_id == a.id))
    }
    targeted = read = acked = 0
    missing: list[str] = []
    for u, cu in _staff(db):
        if not _targets(a, cu.role_codes, u.unit_id):
            continue
        targeted += 1
        r = reads.get(u.id)
        if r:
            read += 1
        if r and r.acknowledged_at:
            acked += 1
        elif a.requires_ack:
            missing.append(u.full_name)
    return ReadStatsOut(targeted=targeted, read=read, acknowledged=acked, not_acknowledged=sorted(missing))
