import hmac
import json
import uuid
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, get_active_user, require
from app.core.security import decrypt_text, encrypt_text, new_tracking_code, sha256
from app.models import FormTemplate, Incident, IncidentAction, IncidentStatusChange, Unit, User
from app.schemas import (
    ActionIn,
    ActionOut,
    ActionUpdateIn,
    AssignIn,
    IncidentCreatedOut,
    IncidentCreateIn,
    IncidentDetailOut,
    IncidentPage,
    IncidentSummaryOut,
    StatusChangeIn,
    StatusChangeOut,
    TrackIn,
    TrackOut,
)
from app.services.forms import validate_answers
from app.services.notifications import notify

router = APIRouter(tags=["incidents"])

TRANSITIONS: dict[str, set[str]] = {
    "submitted": {"under_review", "assigned", "rejected"},
    "under_review": {"assigned", "rejected", "closed"},
    "assigned": {"in_progress", "under_review"},
    "in_progress": {"action_taken", "assigned"},
    "action_taken": {"closed", "in_progress"},
    "closed": set(),
    "rejected": {"under_review"},
}

STATUS_LABELS = {
    "submitted": "ثبت‌شده",
    "under_review": "در حال بررسی",
    "assigned": "ارجاع‌شده",
    "in_progress": "در حال اقدام",
    "action_taken": "اقدام اصلاحی انجام شد",
    "closed": "بسته‌شده",
    "rejected": "رد شده",
}


def active_form(db: Session, code: str, version: int | None = None) -> FormTemplate | None:
    stmt = select(FormTemplate).where(FormTemplate.code == code)
    if version is not None:
        stmt = stmt.where(FormTemplate.version == version)
    else:
        stmt = stmt.where(FormTemplate.is_active.is_(True))
    return db.scalar(stmt.order_by(FormTemplate.version.desc()).limit(1))


@router.get("/forms/{code}")
def get_form(code: str, _: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    form = active_form(db, code)
    if form is None:
        raise api_error(404, "not_found", "فرم پیدا نشد")
    return {**form.schema, "code": form.code, "version": form.version}


@router.post("/admin/forms/{code}", status_code=201)
def publish_form(
    code: str,
    schema: dict,
    request: Request,
    cu: CurrentUser = Depends(require("form.manage")),
    db: Session = Depends(get_db),
):
    sections = schema.get("sections")
    if not isinstance(sections, list) or not sections:
        raise api_error(400, "invalid_form", "قالب فرم باید حداقل یک بخش داشته باشد")
    ids = [f.get("id") for s in sections for f in s.get("fields", [])]
    if None in ids or len(ids) != len(set(ids)):
        raise api_error(400, "invalid_form", "شناسه فیلدها باید یکتا و غیرخالی باشد")
    last = db.scalar(select(func.max(FormTemplate.version)).where(FormTemplate.code == code)) or 0
    for old in db.scalars(select(FormTemplate).where(FormTemplate.code == code)):
        old.is_active = False
    form = FormTemplate(code=code, version=last + 1, schema=schema, is_active=True)
    db.add(form)
    audit(db, "form.published", cu.id, "form", f"{code}:{last + 1}", client_ip(request))
    db.commit()
    return {"code": code, "version": form.version}


def _unit_names(db: Session, ids: set[uuid.UUID]) -> dict[uuid.UUID, str]:
    if not ids:
        return {}
    return {u.id: u.name for u in db.scalars(select(Unit).where(Unit.id.in_(ids)))}


def _summary(i: Incident, names: dict[uuid.UUID, str]) -> IncidentSummaryOut:
    return IncidentSummaryOut(
        id=i.id,
        tracking_code=i.tracking_code,
        kind=i.kind,
        unit_id=i.unit_id,
        unit_name=names.get(i.unit_id),
        occurred_on=i.occurred_on,
        status=i.status,
        is_anonymous=i.is_anonymous,
        created_at=i.created_at,
    )


def _can(cu: CurrentUser, perm: str, i: Incident) -> bool:
    return cu.can_in_unit(perm, i.unit_id) or cu.can_in_unit(perm, i.assigned_unit_id)


@router.post("/incidents", response_model=IncidentCreatedOut, status_code=201)
def create_incident(
    body: IncidentCreateIn,
    request: Request,
    cu: CurrentUser = Depends(require("incident.create")),
    db: Session = Depends(get_db),
):
    if body.is_anonymous and not body.follow_up_secret:
        raise api_error(400, "secret_required", "برای گزارش ناشناس رمز پیگیری لازم است")

    existing = db.scalar(select(Incident).where(Incident.client_uuid == body.client_uuid))
    if existing is not None:
        same_sender = (
            hmac.compare_digest(existing.follow_up_secret_hash or "", sha256(body.follow_up_secret))
            if existing.is_anonymous and body.follow_up_secret
            else existing.reporter_id == cu.id
        )
        if not same_sender:
            raise api_error(409, "duplicate", "این گزارش قبلاً ثبت شده است")
        return IncidentCreatedOut(
            id=None if existing.is_anonymous else existing.id,
            tracking_code=existing.tracking_code,
            status=existing.status,
            duplicate=True,
        )

    if db.get(Unit, body.unit_id) is None:
        raise api_error(400, "unknown_unit", "واحد انتخاب‌شده نامعتبر است")
    form = active_form(db, "incident", body.form_version) or active_form(db, "incident")
    if form is None:
        raise api_error(500, "form_missing", "قالب فرم گزارش خطا تعریف نشده است")
    result = validate_answers(form.schema, body.kind, body.answers)
    if result.errors:
        raise HTTPException(
            422, {"code": "invalid_answers", "message": "برخی پاسخ‌ها نامعتبرند", "fields": result.errors}
        )

    incident = Incident(
        tracking_code=new_tracking_code("IR"),
        client_uuid=body.client_uuid,
        kind=body.kind,
        form_template_id=form.id,
        answers=result.answers,
        sensitive_answers_encrypted=(
            encrypt_text(json.dumps(result.sensitive, ensure_ascii=False)) if result.sensitive else None
        ),
        unit_id=body.unit_id,
        occurred_on=body.occurred_on,
        shift=body.shift,
        is_anonymous=body.is_anonymous,
        reporter_id=None if body.is_anonymous else cu.id,
        follow_up_secret_hash=sha256(body.follow_up_secret) if body.is_anonymous else None,
    )
    incident.history.append(IncidentStatusChange(from_status=None, to_status="submitted"))
    db.add(incident)
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise api_error(409, "duplicate", "این گزارش قبلاً ثبت شده است") from None
    # در گزارش ناشناس، شناسه کاربر در لاگ حسابرسی هم ثبت نمی‌شود.
    audit(
        db,
        "incident.created",
        None if body.is_anonymous else cu.id,
        "incident",
        incident.id,
        None if body.is_anonymous else client_ip(request),
    )
    db.commit()
    return IncidentCreatedOut(
        id=None if body.is_anonymous else incident.id,
        tracking_code=incident.tracking_code,
        status=incident.status,
    )


@router.get("/incidents/mine", response_model=list[IncidentSummaryOut])
def my_incidents(cu: CurrentUser = Depends(require("incident.create")), db: Session = Depends(get_db)):
    rows = list(
        db.scalars(select(Incident).where(Incident.reporter_id == cu.id).order_by(Incident.created_at.desc()))
    )
    names = _unit_names(db, {i.unit_id for i in rows})
    return [_summary(i, names) for i in rows]


@router.get("/incidents", response_model=IncidentPage)
def list_incidents(
    status_: str | None = Query(None, alias="status"),
    kind: str | None = None,
    unit_id: uuid.UUID | None = None,
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=100),
    cu: CurrentUser = Depends(require("incident.read")),
    db: Session = Depends(get_db),
):
    stmt = select(Incident)
    if not cu.has_global("incident.read"):
        units = cu.units_for("incident.read")
        stmt = stmt.where(or_(Incident.unit_id.in_(units), Incident.assigned_unit_id.in_(units)))
    if status_:
        stmt = stmt.where(Incident.status == status_)
    if kind:
        stmt = stmt.where(Incident.kind == kind)
    if unit_id:
        stmt = stmt.where(Incident.unit_id == unit_id)
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = list(db.scalars(stmt.order_by(Incident.created_at.desc()).offset((page - 1) * size).limit(size)))
    names = _unit_names(db, {i.unit_id for i in rows})
    return IncidentPage(items=[_summary(i, names) for i in rows], total=total, page=page, size=size)


def _load(db: Session, incident_id: uuid.UUID) -> Incident:
    incident = db.get(Incident, incident_id)
    if incident is None:
        raise api_error(404, "not_found", "گزارش پیدا نشد")
    return incident


@router.get("/incidents/{incident_id}", response_model=IncidentDetailOut)
def get_incident(
    incident_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    i = _load(db, incident_id)
    is_reader = _can(cu, "incident.read", i)
    is_own = i.reporter_id is not None and i.reporter_id == cu.id
    if not is_reader and not is_own:
        # 404 به جای 403 تا وجود گزارش برای افراد غیرمجاز آشکار نشود.
        raise api_error(404, "not_found", "گزارش پیدا نشد")

    names = _unit_names(db, {i.unit_id, i.assigned_unit_id} - {None})
    out = IncidentDetailOut(
        **_summary(i, names).model_dump(),
        shift=i.shift,
        answers=i.answers,
        assigned_unit_id=i.assigned_unit_id,
        assigned_unit_name=names.get(i.assigned_unit_id),
        assigned_user_id=i.assigned_user_id,
        history=[StatusChangeOut.model_validate(h) for h in i.history],
        actions=[ActionOut.model_validate(a) for a in i.actions],
    )
    if is_reader:
        if _can(cu, "incident.manage", i):
            out.can_manage = True
            out.next_statuses = sorted(TRANSITIONS.get(i.status, set()))
        if i.sensitive_answers_encrypted and _can(cu, "incident.sensitive.view", i):
            out.sensitive_answers = json.loads(decrypt_text(i.sensitive_answers_encrypted))
        if i.reporter_id and _can(cu, "incident.reporter.view", i):
            reporter = db.get(User, i.reporter_id)
            out.reporter_name = reporter.full_name if reporter else None
        audit(
            db,
            "incident.viewed",
            cu.id,
            "incident",
            i.id,
            client_ip(request),
            sensitive=out.sensitive_answers is not None,
            reporter=out.reporter_name is not None,
        )
        db.commit()
    return out


def _require_manage(cu: CurrentUser, i: Incident) -> None:
    if not _can(cu, "incident.manage", i):
        raise api_error(status.HTTP_403_FORBIDDEN, "forbidden", "اجازه مدیریت این گزارش را ندارید")


def _change_status(db: Session, i: Incident, to: str, by: uuid.UUID, note: str | None) -> None:
    if to not in TRANSITIONS.get(i.status, set()):
        raise api_error(409, "invalid_transition", f"تغییر وضعیت از «{i.status}» به «{to}» مجاز نیست")
    i.history.append(IncidentStatusChange(from_status=i.status, to_status=to, by_user_id=by, note=note))
    i.status = to
    i.closed_at = datetime.now(UTC) if to in ("closed", "rejected") else None
    # فقط کد پیگیری و وضعیت؛ یادداشت بررسی ممکن است اطلاعات بیمار داشته باشد. گزارش ناشناس گیرنده‌ای ندارد.
    if not i.is_anonymous:
        notify(
            db,
            [i.reporter_id],
            "incident.status",
            f"گزارش {i.tracking_code}: {STATUS_LABELS[to]}",
            link_type="incident",
            link_id=i.id,
            actor_id=by,
        )


@router.post("/incidents/{incident_id}/status", response_model=IncidentSummaryOut)
def change_status(
    incident_id: uuid.UUID,
    body: StatusChangeIn,
    request: Request,
    cu: CurrentUser = Depends(require("incident.manage")),
    db: Session = Depends(get_db),
):
    i = _load(db, incident_id)
    _require_manage(cu, i)
    old = i.status
    _change_status(db, i, body.status, cu.id, body.note)
    audit(db, "incident.status", cu.id, "incident", i.id, client_ip(request), old=old, new=body.status)
    db.commit()
    return _summary(i, _unit_names(db, {i.unit_id}))


@router.post("/incidents/{incident_id}/assign", response_model=IncidentSummaryOut)
def assign(
    incident_id: uuid.UUID,
    body: AssignIn,
    request: Request,
    cu: CurrentUser = Depends(require("incident.manage")),
    db: Session = Depends(get_db),
):
    i = _load(db, incident_id)
    _require_manage(cu, i)
    if body.unit_id is None and body.user_id is None:
        raise api_error(400, "assign_target", "واحد یا فرد مسئول را مشخص کنید")
    if body.unit_id and db.get(Unit, body.unit_id) is None:
        raise api_error(400, "unknown_unit", "واحد نامعتبر است")
    if body.user_id and db.get(User, body.user_id) is None:
        raise api_error(400, "unknown_user", "کاربر نامعتبر است")
    previous_user = i.assigned_user_id
    i.assigned_unit_id = body.unit_id or i.assigned_unit_id
    i.assigned_user_id = body.user_id or i.assigned_user_id
    if i.status in ("submitted", "under_review"):
        _change_status(db, i, "assigned", cu.id, body.note)
    notify(
        db,
        [body.user_id] if body.user_id != previous_user else [],
        "incident.assigned",
        f"گزارش {i.tracking_code} برای پیگیری به شما ارجاع شد",
        link_type="incident",
        link_id=i.id,
        important=True,
        actor_id=cu.id,
    )
    audit(db, "incident.assigned", cu.id, "incident", i.id, client_ip(request))
    db.commit()
    return _summary(i, _unit_names(db, {i.unit_id}))


@router.post("/incidents/{incident_id}/actions", response_model=ActionOut, status_code=201)
def add_action(
    incident_id: uuid.UUID,
    body: ActionIn,
    request: Request,
    cu: CurrentUser = Depends(require("incident.manage")),
    db: Session = Depends(get_db),
):
    i = _load(db, incident_id)
    _require_manage(cu, i)
    action = IncidentAction(incident_id=i.id, created_by=cu.id, **body.model_dump())
    db.add(action)
    db.flush()
    audit(db, "incident.action_added", cu.id, "incident", i.id, client_ip(request))
    db.commit()
    return action


@router.patch("/incidents/{incident_id}/actions/{action_id}", response_model=ActionOut)
def update_action(
    incident_id: uuid.UUID,
    action_id: uuid.UUID,
    body: ActionUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(require("incident.manage")),
    db: Session = Depends(get_db),
):
    i = _load(db, incident_id)
    _require_manage(cu, i)
    action = db.get(IncidentAction, action_id)
    if action is None or action.incident_id != i.id:
        raise api_error(404, "not_found", "اقدام پیدا نشد")
    if body.done is not None:
        action.done_at = datetime.now(UTC) if body.done else None
    if body.result is not None:
        action.result = body.result
    audit(db, "incident.action_updated", cu.id, "incident", i.id, client_ip(request))
    db.commit()
    return action


@router.post("/public/incidents/track", response_model=TrackOut, tags=["public"])
def track_incident(body: TrackIn, db: Session = Depends(get_db)):
    """پیگیری گزارش ناشناس با کد پیگیری و رمزی که هنگام ثبت ساخته شده است."""
    i = db.scalar(select(Incident).where(Incident.tracking_code == body.tracking_code.strip().upper()))
    if (
        i is None
        or not i.follow_up_secret_hash
        or not hmac.compare_digest(i.follow_up_secret_hash, sha256(body.follow_up_secret))
    ):
        raise api_error(404, "not_found", "گزارشی با این مشخصات پیدا نشد")
    return TrackOut(
        tracking_code=i.tracking_code,
        status=i.status,
        created_at=i.created_at,
        # یادداشت‌های داخلی کارشناسان برای گزارش‌دهنده ناشناس نمایش داده نمی‌شود.
        history=[
            StatusChangeOut(from_status=h.from_status, to_status=h.to_status, note=None, at=h.at)
            for h in i.history
        ],
    )
