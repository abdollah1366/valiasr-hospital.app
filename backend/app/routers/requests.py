"""درخواست‌های پشتیبانی کارکنان (فناوری اطلاعات، تجهیزات پزشکی، تأسیسات)."""

import csv
import io
import uuid
from collections import Counter
from datetime import UTC, datetime, timedelta

from fastapi import APIRouter, Depends, Query, Request, Response
from sqlalchemy import case, func, or_, select
from sqlalchemy.orm import Session, selectinload

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, build_current_user, client_ip, require
from app.core.security import new_tracking_code
from app.models import MediaFile, RequestType, ServiceRequest, ServiceRequestEvent, Unit, User
from app.schemas.requests import (
    AssignIn,
    AttachmentOut,
    CommentIn,
    CountRow,
    EventOut,
    PersonOut,
    RateIn,
    ReopenIn,
    RequestCreatedOut,
    RequestCreateIn,
    RequestDetailOut,
    RequestPage,
    RequestStatsOut,
    RequestSummaryOut,
    RequestTypeIn,
    RequestTypeOut,
    RequestTypeUpdateIn,
    StatusIn,
    TransferIn,
)
from app.services import notifications as notes
from app.services import requests as svc
from app.services.media import store_upload
from app.services.training import sign_media

router = APIRouter(tags=["requests"])

CREATE = require("request.create")
VIEW = require("request.create", "request.handle", "request.read")
MANAGE_TYPES = require("request.type.manage")

# پیوست درخواست: فقط عکس و PDF، حداکثر ۱۵ مگابایت
ATTACHMENT_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
    ".pdf": "application/pdf",
}
ATTACHMENT_MAX_MB = 15


def _now() -> datetime:
    return datetime.now(UTC)


# ---------------------------------------------------------------- خروجی‌ها


def _unit_names(db: Session) -> dict[uuid.UUID, str]:
    return dict(db.execute(select(Unit.id, Unit.name)).all())


def _people(db: Session, ids: set) -> dict[uuid.UUID, User]:
    ids = {i for i in ids if i is not None}
    if not ids:
        return {}
    return {u.id: u for u in db.scalars(select(User).where(User.id.in_(ids)))}


def _person(u: User | None) -> PersonOut | None:
    if u is None:
        return None
    return PersonOut(id=u.id, full_name=u.full_name, personnel_code=u.personnel_code, job_title=u.job_title)


def _summary(
    r: ServiceRequest, types: dict[uuid.UUID, RequestType], units: dict, people: dict
) -> RequestSummaryOut:
    t = types[r.type_id]
    return RequestSummaryOut(
        id=r.id,
        tracking_code=r.tracking_code,
        type_id=r.type_id,
        type_name=t.name,
        category=t.category,
        description=r.description,
        priority=r.priority,
        status=r.status,
        requester=_person(people[r.requester_id]),
        requester_unit_name=units.get(r.requester_unit_id),
        handler_unit_id=r.handler_unit_id,
        handler_unit_name=units.get(r.handler_unit_id),
        assigned_to=_person(people.get(r.assigned_to_id)),
        created_at=r.created_at,
        updated_at=r.updated_at,
        satisfaction=r.satisfaction,
    )


def _summaries(db: Session, rows: list[ServiceRequest]) -> list[RequestSummaryOut]:
    types = {t.id: t for t in db.scalars(select(RequestType))}
    units = _unit_names(db)
    people = _people(db, {r.requester_id for r in rows} | {r.assigned_to_id for r in rows})
    return [_summary(r, types, units, people) for r in rows]


def _detail(db: Session, cu: CurrentUser, r: ServiceRequest, now: datetime) -> RequestDetailOut:
    t = db.get(RequestType, r.type_id)
    units = _unit_names(db)
    people = _people(db, {r.requester_id, r.assigned_to_id} | {e.actor_id for e in r.events})
    base = _summary(r, {t.id: t}, units, people)
    answers = [
        {"label": f["label"], "value": r.answers[f["id"]]} for f in t.fields if f["id"] in (r.answers or {})
    ]
    events = [
        EventOut(
            id=e.id,
            at=e.at,
            kind=e.kind,
            actor=_person(people.get(e.actor_id)),
            from_status=e.from_status,
            to_status=e.to_status,
            note=e.note,
            attachment=(
                AttachmentOut(
                    id=e.media.id,
                    original_name=e.media.original_name,
                    content_type=e.media.content_type,
                    size=e.media.size,
                    url=sign_media(e.media.id),
                )
                if e.media
                else None
            ),
        )
        for e in r.events
    ]
    return RequestDetailOut(
        **base.model_dump(),
        location=r.location,
        answers=answers,
        resolution=r.resolution,
        accepted_at=r.accepted_at,
        done_at=r.done_at,
        closed_at=r.closed_at,
        satisfaction_comment=r.satisfaction_comment,
        reopen_until=svc.reopen_open_until(r) if r.status in ("done", "closed") else None,
        events=events,
        actions=svc.actions_for(cu, r, now),
    )


def _type_out(t: RequestType, units: dict, count: int | None = None) -> RequestTypeOut:
    return RequestTypeOut(
        id=t.id,
        name=t.name,
        category=t.category,
        description=t.description,
        handler_unit_id=t.handler_unit_id,
        handler_unit_name=units.get(t.handler_unit_id),
        fields=t.fields or [],
        is_active=t.is_active,
        sort_order=t.sort_order,
        request_count=count,
    )


# ---------------------------------------------------------------- انواع درخواست


@router.get("/request-types", response_model=list[RequestTypeOut])
def request_types(_: CurrentUser = Depends(CREATE), db: Session = Depends(get_db)):
    units = _unit_names(db)
    rows = db.scalars(
        select(RequestType)
        .where(RequestType.is_active.is_(True))
        .order_by(RequestType.sort_order, RequestType.name)
    )
    return [_type_out(t, units) for t in rows]


@router.get("/admin/request-types", response_model=list[RequestTypeOut])
def admin_request_types(_: CurrentUser = Depends(MANAGE_TYPES), db: Session = Depends(get_db)):
    units = _unit_names(db)
    counts = dict(
        db.execute(select(ServiceRequest.type_id, func.count()).group_by(ServiceRequest.type_id)).all()
    )
    rows = db.scalars(select(RequestType).order_by(RequestType.sort_order, RequestType.name))
    return [_type_out(t, units, counts.get(t.id, 0)) for t in rows]


def _check_unit(db: Session, unit_id: uuid.UUID) -> None:
    if db.get(Unit, unit_id) is None:
        raise api_error(400, "unknown_unit", "واحد انتخاب‌شده نامعتبر است")


@router.post("/admin/request-types", response_model=RequestTypeOut, status_code=201)
def create_request_type(
    body: RequestTypeIn,
    request: Request,
    cu: CurrentUser = Depends(MANAGE_TYPES),
    db: Session = Depends(get_db),
):
    _check_unit(db, body.handler_unit_id)
    data = body.model_dump()
    data["fields"] = [f.model_dump() for f in body.fields]
    t = RequestType(**data)
    db.add(t)
    db.flush()
    audit(db, "request_type.created", cu.id, "request_type", t.id, client_ip(request))
    db.commit()
    return _type_out(t, _unit_names(db), 0)


@router.patch("/admin/request-types/{type_id}", response_model=RequestTypeOut)
def update_request_type(
    type_id: uuid.UUID,
    body: RequestTypeUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(MANAGE_TYPES),
    db: Session = Depends(get_db),
):
    t = db.get(RequestType, type_id)
    if t is None:
        raise api_error(404, "not_found", "نوع درخواست پیدا نشد")
    data = body.model_dump(exclude_unset=True)
    if data.get("handler_unit_id"):
        _check_unit(db, data["handler_unit_id"])
    if "fields" in data:
        ids = [f["id"] for f in data["fields"]]
        if len(ids) != len(set(ids)):
            raise api_error(400, "invalid_fields", "شناسه فیلدها باید یکتا باشد")
    for key, value in data.items():
        if value is not None or key == "description":
            setattr(t, key, value)
    audit(db, "request_type.updated", cu.id, "request_type", t.id, client_ip(request))
    db.commit()
    count = db.scalar(select(func.count()).where(ServiceRequest.type_id == t.id))
    return _type_out(t, _unit_names(db), count)


@router.delete("/admin/request-types/{type_id}", status_code=204)
def delete_request_type(
    type_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(MANAGE_TYPES),
    db: Session = Depends(get_db),
):
    t = db.get(RequestType, type_id)
    if t is None:
        raise api_error(404, "not_found", "نوع درخواست پیدا نشد")
    if db.scalar(select(func.count()).where(ServiceRequest.type_id == t.id)):
        raise api_error(409, "in_use", "برای این نوع درخواست ثبت شده است؛ به جای حذف، آن را غیرفعال کنید")
    db.delete(t)
    audit(db, "request_type.deleted", cu.id, "request_type", type_id, client_ip(request))
    db.commit()
    return Response(status_code=204)


# ---------------------------------------------------------------- ثبت و فهرست


@router.post("/requests", response_model=RequestCreatedOut, status_code=201)
def create_request(
    body: RequestCreateIn, request: Request, cu: CurrentUser = Depends(CREATE), db: Session = Depends(get_db)
):
    existing = db.scalar(select(ServiceRequest).where(ServiceRequest.client_uuid == body.client_uuid))
    if existing is not None:
        if existing.requester_id != cu.id:
            raise api_error(409, "duplicate", "این درخواست قبلاً ثبت شده است")
        return RequestCreatedOut(
            id=existing.id, tracking_code=existing.tracking_code, status=existing.status, duplicate=True
        )
    t = db.get(RequestType, body.type_id)
    if t is None or not t.is_active:
        raise api_error(400, "unknown_type", "نوع درخواست نامعتبر است")
    answers, errors = svc.validate_answers(t.fields or [], body.answers)
    if errors:
        raise api_error(422, "invalid_answers", "برخی فیلدها کامل نیست")
    r = ServiceRequest(
        tracking_code=new_tracking_code("R"),
        client_uuid=body.client_uuid,
        type_id=t.id,
        requester_id=cu.id,
        requester_unit_id=cu.user.unit_id,
        handler_unit_id=t.handler_unit_id,
        description=body.description.strip(),
        location=(body.location or "").strip() or None,
        answers=answers,
        priority=body.priority,
        status="submitted",
    )
    db.add(r)
    db.flush()
    db.add(ServiceRequestEvent(request_id=r.id, actor_id=cu.id, kind="created", to_status="submitted"))
    _notify(
        db,
        r,
        cu,
        [u.id for u in _agents(db, r.handler_unit_id)],
        "request.new",
        f"درخواست جدید: {t.name}",
        body=f"{r.tracking_code} · {r.description}",
        important=r.priority != "normal",
    )
    audit(db, "request.created", cu.id, "service_request", r.id, client_ip(request), priority=r.priority)
    db.commit()
    return RequestCreatedOut(id=r.id, tracking_code=r.tracking_code, status=r.status)


@router.get("/requests/mine", response_model=list[RequestSummaryOut])
def my_requests(cu: CurrentUser = Depends(CREATE), db: Session = Depends(get_db)):
    svc.close_stale(db, _now())
    db.commit()
    rows = db.scalars(
        select(ServiceRequest)
        .where(ServiceRequest.requester_id == cu.id)
        .order_by(ServiceRequest.created_at.desc())
    ).all()
    return _summaries(db, list(rows))


def _visible(cu: CurrentUser):
    """شرط درخواست‌هایی که کاربر به‌عنوان کارشناس یا مسئول واحد می‌بیند (نه درخواست‌های خودش)."""
    conds = []
    if cu.has_global("request.handle") or cu.has_global("request.read"):
        return None
    if cu.units_for("request.handle"):
        conds.append(ServiceRequest.handler_unit_id.in_(cu.units_for("request.handle")))
    if cu.units_for("request.read"):
        conds.append(ServiceRequest.requester_unit_id.in_(cu.units_for("request.read")))
    return or_(*conds) if conds else ServiceRequest.id.is_(None)


def _filtered(
    cu: CurrentUser,
    status: str | None,
    priority: str | None,
    type_id: uuid.UUID | None,
    unit_id: uuid.UUID | None,
    assigned: str | None,
    q: str | None,
    since: datetime | None = None,
):
    stmt = select(ServiceRequest)
    visible = _visible(cu)
    if visible is not None:
        stmt = stmt.where(visible)
    if status == "open":
        stmt = stmt.where(ServiceRequest.status.in_(svc.OPEN_STATUSES))
    elif status:
        stmt = stmt.where(ServiceRequest.status == status)
    if priority:
        stmt = stmt.where(ServiceRequest.priority == priority)
    if type_id:
        stmt = stmt.where(ServiceRequest.type_id == type_id)
    if unit_id:
        stmt = stmt.where(
            or_(ServiceRequest.handler_unit_id == unit_id, ServiceRequest.requester_unit_id == unit_id)
        )
    if assigned == "me":
        stmt = stmt.where(ServiceRequest.assigned_to_id == cu.id)
    elif assigned == "none":
        stmt = stmt.where(ServiceRequest.assigned_to_id.is_(None))
    if q:
        like = f"%{q.strip()}%"
        stmt = stmt.where(
            or_(ServiceRequest.tracking_code.ilike(like), ServiceRequest.description.ilike(like))
        )
    if since:
        stmt = stmt.where(ServiceRequest.created_at >= since)
    return stmt


QUEUE = require("request.handle", "request.read")


@router.get("/requests", response_model=RequestPage)
def list_requests(
    status: str | None = None,
    priority: str | None = None,
    type_id: uuid.UUID | None = None,
    unit_id: uuid.UUID | None = None,
    assigned: str | None = Query(default=None, pattern="^(me|none)$"),
    q: str | None = Query(default=None, max_length=100),
    page: int = Query(default=1, ge=1),
    size: int = Query(default=50, ge=1, le=200),
    cu: CurrentUser = Depends(QUEUE),
    db: Session = Depends(get_db),
):
    """کارتابل: درخواست‌های واحد کارشناس، یا درخواست‌های کارکنان واحد برای مسئول واحد."""
    svc.close_stale(db, _now())
    db.commit()
    stmt = _filtered(cu, status, priority, type_id, unit_id, assigned, q)
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    # درخواست‌های باز بحرانی و فوری بالاتر، سپس قدیمی‌تر جلوتر
    rank = case(svc.PRIORITY_RANK, value=ServiceRequest.priority, else_=9)
    is_open = ServiceRequest.status.in_(svc.OPEN_STATUSES)
    rows = db.scalars(
        stmt.order_by(is_open.desc(), rank, ServiceRequest.created_at.desc())
        .offset((page - 1) * size)
        .limit(size)
    ).all()
    return RequestPage(items=_summaries(db, list(rows)), total=total, page=page, size=size)


@router.get("/requests/export.csv")
def export_requests(
    status: str | None = None,
    priority: str | None = None,
    type_id: uuid.UUID | None = None,
    unit_id: uuid.UUID | None = None,
    days: int = Query(default=90, ge=1, le=3650),
    cu: CurrentUser = Depends(QUEUE),
    db: Session = Depends(get_db),
):
    stmt = _filtered(cu, status, priority, type_id, unit_id, None, None, _now() - timedelta(days=days))
    rows = list(db.scalars(stmt.order_by(ServiceRequest.created_at.desc())).all())
    summaries = _summaries(db, rows)
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(
        [
            "کد پیگیری",
            "نوع",
            "اولویت",
            "وضعیت",
            "درخواست‌کننده",
            "واحد درخواست‌کننده",
            "واحد مسئول",
            "کارشناس",
            "ثبت",
            "پذیرش",
            "انجام",
            "رضایت",
            "شرح",
        ]
    )
    for r, s in zip(rows, summaries, strict=True):
        w.writerow(
            [
                r.tracking_code,
                s.type_name,
                svc.PRIORITIES[r.priority],
                svc.STATUSES[r.status],
                s.requester.full_name,
                s.requester_unit_name or "",
                s.handler_unit_name or "",
                s.assigned_to.full_name if s.assigned_to else "",
                r.created_at.isoformat(timespec="minutes"),
                r.accepted_at.isoformat(timespec="minutes") if r.accepted_at else "",
                r.done_at.isoformat(timespec="minutes") if r.done_at else "",
                r.satisfaction or "",
                r.description,
            ]
        )
    return Response(
        "﻿" + buf.getvalue(),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": 'attachment; filename="requests.csv"'},
    )


@router.get("/requests/stats", response_model=RequestStatsOut)
def request_stats(
    days: int = Query(default=30, ge=1, le=3650),
    cu: CurrentUser = Depends(QUEUE),
    db: Session = Depends(get_db),
):
    now = _now()
    svc.close_stale(db, now)
    db.commit()
    rows = list(
        db.scalars(_filtered(cu, None, None, None, None, None, None, now - timedelta(days=days))).all()
    )
    types = {t.id: t.name for t in db.scalars(select(RequestType))}
    units = _unit_names(db)

    def hours(pairs):
        values = [(b - a).total_seconds() / 3600 for a, b in pairs if a and b]
        return round(sum(values) / len(values), 1) if values else None

    def rows_of(counter: Counter, labels) -> list[CountRow]:
        return [CountRow(key=str(k), label=labels(k), count=n) for k, n in counter.most_common()]

    rated = [r.satisfaction for r in rows if r.satisfaction]
    daily = Counter(r.created_at.date().isoformat() for r in rows)
    return RequestStatsOut(
        total=len(rows),
        open=sum(r.status in svc.OPEN_STATUSES for r in rows),
        critical_open=sum(r.status in svc.OPEN_STATUSES and r.priority == "critical" for r in rows),
        done=sum(r.status in ("done", "closed") for r in rows),
        avg_response_hours=hours((r.created_at, r.accepted_at) for r in rows),
        avg_resolution_hours=hours((r.created_at, r.done_at) for r in rows),
        avg_satisfaction=round(sum(rated) / len(rated), 2) if rated else None,
        rated=len(rated),
        by_status=rows_of(Counter(r.status for r in rows), lambda k: svc.STATUSES.get(k, k)),
        by_type=rows_of(Counter(r.type_id for r in rows), lambda k: types.get(k, "?")),
        by_handler_unit=rows_of(Counter(r.handler_unit_id for r in rows), lambda k: units.get(k, "?")),
        by_requester_unit=rows_of(
            Counter(r.requester_unit_id for r in rows),
            lambda k: units.get(k, "بدون واحد") if k else "بدون واحد",
        ),
        daily=[CountRow(key=d, label=d, count=daily[d]) for d in sorted(daily)],
    )


# ---------------------------------------------------------------- جزئیات و رسیدگی


def _load(db: Session, request_id: uuid.UUID, cu: CurrentUser) -> ServiceRequest:
    r = db.get(ServiceRequest, request_id, options=[selectinload(ServiceRequest.events)])
    if r is None or not svc.can_view(cu, r):
        raise api_error(404, "not_found", "درخواست پیدا نشد")
    return r


def _handle(db: Session, request_id: uuid.UUID, cu: CurrentUser) -> ServiceRequest:
    r = _load(db, request_id, cu)
    if not svc.can_handle(cu, r):
        raise api_error(403, "forbidden", "رسیدگی به این درخواست با واحد شما نیست")
    return r


def _event(r: ServiceRequest, cu: CurrentUser, kind: str, **kw) -> None:
    r.events.append(ServiceRequestEvent(actor_id=cu.id, kind=kind, at=_now(), **kw))
    r.updated_at = _now()


def _notify(db: Session, r: ServiceRequest, cu: CurrentUser, to: list, kind: str, title: str, **kw) -> None:
    notes.notify(db, to, kind, title, link_type="request", link_id=r.id, actor_id=cu.id, **kw)


def _handlers(db: Session, r: ServiceRequest) -> list[uuid.UUID]:
    """کارشناس مسئول؛ اگر هنوز کسی برنداشته، همه کارشناسان واحد مسئول."""
    if r.assigned_to_id:
        return [r.assigned_to_id]
    return [u.id for u in _agents(db, r.handler_unit_id)]


def _other_side(db: Session, r: ServiceRequest, cu: CurrentUser) -> list[uuid.UUID]:
    if cu.id == r.requester_id:
        return _handlers(db, r)
    return [r.requester_id, r.assigned_to_id]


@router.get("/requests/{request_id}", response_model=RequestDetailOut)
def get_request(request_id: uuid.UUID, cu: CurrentUser = Depends(VIEW), db: Session = Depends(get_db)):
    now = _now()
    svc.close_stale(db, now)
    db.commit()
    return _detail(db, cu, _load(db, request_id, cu), now)


@router.post("/requests/{request_id}/status", response_model=RequestDetailOut)
def change_status(
    request_id: uuid.UUID,
    body: StatusIn,
    request: Request,
    cu: CurrentUser = Depends(VIEW),
    db: Session = Depends(get_db),
):
    r = _handle(db, request_id, cu)
    if body.status not in svc.TRANSITIONS[r.status]:
        raise api_error(409, "invalid_transition", "این تغییر وضعیت مجاز نیست")
    now = _now()
    if body.status == "done" and not (body.resolution or "").strip():
        raise api_error(400, "resolution_required", "نتیجه رسیدگی را بنویسید")
    if body.status == "rejected" and not (body.note or "").strip():
        raise api_error(400, "note_required", "دلیل رد درخواست را بنویسید")
    old = r.status
    r.status = body.status
    if body.status == "accepted":
        r.accepted_at = r.accepted_at or now
        r.assigned_to_id = r.assigned_to_id or cu.id
    if body.status == "done":
        r.done_at = now
        r.resolution = body.resolution.strip()
    if body.status == "rejected":
        r.closed_at = now
    _event(r, cu, "status", from_status=old, to_status=body.status, note=(body.note or "").strip() or None)
    _notify(
        db,
        r,
        cu,
        [r.requester_id],
        "request.status",
        f"درخواست {r.tracking_code}: {svc.STATUSES[body.status]}",
        body=r.resolution if body.status == "done" else body.note,
    )
    audit(db, "request.status", cu.id, "service_request", r.id, client_ip(request), status=body.status)
    db.commit()
    return _detail(db, cu, r, now)


@router.get("/requests/{request_id}/agents", response_model=list[PersonOut])
def request_agents(request_id: uuid.UUID, cu: CurrentUser = Depends(VIEW), db: Session = Depends(get_db)):
    """کارشناسانی که می‌توانند به این درخواست رسیدگی کنند (برای ارجاع درون واحد)."""
    r = _handle(db, request_id, cu)
    return [_person(u) for u in _agents(db, r.handler_unit_id)]


def _agents(db: Session, unit_id: uuid.UUID) -> list[User]:
    users = db.scalars(
        select(User)
        .where(User.is_active.is_(True))
        .options(selectinload(User.roles))
        .order_by(User.full_name)
    )
    return [u for u in users if build_current_user(u).can_in_unit("request.handle", unit_id)]


@router.post("/requests/{request_id}/assign", response_model=RequestDetailOut)
def assign_request(
    request_id: uuid.UUID,
    body: AssignIn,
    request: Request,
    cu: CurrentUser = Depends(VIEW),
    db: Session = Depends(get_db),
):
    r = _handle(db, request_id, cu)
    if r.status not in svc.OPEN_STATUSES:
        raise api_error(409, "not_open", "این درخواست باز نیست")
    if body.user_id is not None and body.user_id not in {u.id for u in _agents(db, r.handler_unit_id)}:
        raise api_error(400, "not_agent", "این کاربر کارشناس واحد مسئول نیست")
    r.assigned_to_id = body.user_id
    target = db.get(User, body.user_id) if body.user_id else None
    _event(r, cu, "assign", note=target.full_name if target else "بدون کارشناس")
    _notify(
        db,
        r,
        cu,
        [body.user_id],
        "request.assigned",
        f"درخواست {r.tracking_code} به شما ارجاع شد",
        body=r.description,
    )
    audit(db, "request.assigned", cu.id, "service_request", r.id, client_ip(request))
    db.commit()
    return _detail(db, cu, r, _now())


@router.post("/requests/{request_id}/transfer", response_model=RequestDetailOut)
def transfer_request(
    request_id: uuid.UUID,
    body: TransferIn,
    request: Request,
    cu: CurrentUser = Depends(VIEW),
    db: Session = Depends(get_db),
):
    """ارجاع به واحد پشتیبانی دیگر (مثلاً از فناوری اطلاعات به تجهیزات پزشکی)."""
    r = _handle(db, request_id, cu)
    if r.status not in svc.OPEN_STATUSES:
        raise api_error(409, "not_open", "این درخواست باز نیست")
    target = db.get(Unit, body.unit_id)
    if target is None or target.id == r.handler_unit_id:
        raise api_error(400, "unknown_unit", "واحد مقصد نامعتبر است")
    old = r.status
    r.handler_unit_id = target.id
    r.assigned_to_id = None
    r.status = "submitted"
    _event(
        r, cu, "transfer", from_status=old, to_status="submitted", note=f"{target.name}: {body.note.strip()}"
    )
    _notify(
        db,
        r,
        cu,
        [u.id for u in _agents(db, target.id)],
        "request.transferred",
        f"درخواست ارجاعی {r.tracking_code} به واحد شما",
        body=body.note,
        important=r.priority != "normal",
    )
    audit(db, "request.transferred", cu.id, "service_request", r.id, client_ip(request), unit=str(target.id))
    db.commit()
    # کارشناس واحد قبلی ممکن است دیگر دسترسی نداشته باشد؛ خلاصه برمی‌گردد تا اپ صفحه را ببندد.
    if not svc.can_view(cu, r):
        return Response(status_code=204)
    return _detail(db, cu, r, _now())


@router.post("/requests/{request_id}/comments", response_model=RequestDetailOut)
def comment_request(
    request_id: uuid.UUID, body: CommentIn, cu: CurrentUser = Depends(VIEW), db: Session = Depends(get_db)
):
    r = _load(db, request_id, cu)
    if "comment" not in svc.actions_for(cu, r, _now()):
        raise api_error(403, "forbidden", "امکان نوشتن یادداشت روی این درخواست نیست")
    _event(r, cu, "comment", note=body.text.strip())
    _notify(
        db,
        r,
        cu,
        _other_side(db, r, cu),
        "request.comment",
        f"پیام تازه در درخواست {r.tracking_code}",
        body=body.text,
    )
    db.commit()
    return _detail(db, cu, r, _now())


@router.post("/requests/{request_id}/attachments", response_model=RequestDetailOut, status_code=201)
async def attach_to_request(
    request_id: uuid.UUID,
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    cu: CurrentUser = Depends(VIEW),
    db: Session = Depends(get_db),
):
    r = _load(db, request_id, cu)
    if "attach" not in svc.actions_for(cu, r, _now()):
        raise api_error(403, "forbidden", "امکان افزودن پیوست به این درخواست نیست")
    m: MediaFile = await store_upload(request, filename, ATTACHMENT_TYPES, ATTACHMENT_MAX_MB, cu.id)
    db.add(m)
    db.flush()
    _event(r, cu, "attachment", media_id=m.id)
    _notify(
        db, r, cu, _other_side(db, r, cu), "request.attachment", f"پیوست تازه در درخواست {r.tracking_code}"
    )
    audit(db, "request.attachment", cu.id, "service_request", r.id, client_ip(request), size=m.size)
    db.commit()
    db.refresh(r)
    return _detail(db, cu, r, _now())


@router.post("/requests/{request_id}/rate", response_model=RequestDetailOut)
def rate_request(
    request_id: uuid.UUID,
    body: RateIn,
    request: Request,
    cu: CurrentUser = Depends(VIEW),
    db: Session = Depends(get_db),
):
    r = _load(db, request_id, cu)
    now = _now()
    if "rate" not in svc.actions_for(cu, r, now):
        raise api_error(409, "cannot_rate", "امتیاز فقط پس از انجام درخواست و یک بار ثبت می‌شود")
    r.satisfaction = body.score
    r.satisfaction_comment = (body.comment or "").strip() or None
    old = r.status
    if r.status == "done":
        r.status = "closed"
        r.closed_at = now
    _event(r, cu, "rated", from_status=old, to_status=r.status, note=r.satisfaction_comment)
    _notify(
        db,
        r,
        cu,
        [r.assigned_to_id],
        "request.rated",
        f"امتیاز درخواست {r.tracking_code}: {'★' * body.score}{'☆' * (5 - body.score)}",
        body=r.satisfaction_comment,
    )
    audit(db, "request.rated", cu.id, "service_request", r.id, client_ip(request), score=body.score)
    db.commit()
    return _detail(db, cu, r, now)


@router.post("/requests/{request_id}/reopen", response_model=RequestDetailOut)
def reopen_request(
    request_id: uuid.UUID,
    body: ReopenIn,
    request: Request,
    cu: CurrentUser = Depends(VIEW),
    db: Session = Depends(get_db),
):
    r = _load(db, request_id, cu)
    now = _now()
    if "reopen" not in svc.actions_for(cu, r, now):
        raise api_error(409, "cannot_reopen", "فقط تا ۳ روز پس از انجام، درخواست‌کننده می‌تواند اعتراض کند")
    old = r.status
    r.status = "in_progress" if r.assigned_to_id else "submitted"
    r.done_at = None
    r.closed_at = None
    r.satisfaction = None
    r.satisfaction_comment = None
    _event(r, cu, "reopened", from_status=old, to_status=r.status, note=body.note.strip())
    _notify(
        db,
        r,
        cu,
        _handlers(db, r),
        "request.reopened",
        f"اعتراض به نتیجه درخواست {r.tracking_code}",
        body=body.note,
        important=True,
    )
    audit(db, "request.reopened", cu.id, "service_request", r.id, client_ip(request))
    db.commit()
    return _detail(db, cu, r, now)
