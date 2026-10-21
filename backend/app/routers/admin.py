import uuid

from fastapi import APIRouter, Depends, Query, Request, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, selectinload

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, require
from app.core.security import hash_password
from app.models import AuditLog, Base, MediaFile, Role, Unit, User, UserRole
from app.schemas import (
    ResetPasswordIn,
    RoleAssignment,
    RoleOut,
    UnitIn,
    UnitOut,
    UserCreateIn,
    UserOut,
    UserUpdateIn,
)
from app.services import chat
from app.services.media import store_upload

router = APIRouter(prefix="/admin", tags=["admin"])


def _user_out(u: User) -> UserOut:
    return UserOut(
        id=u.id,
        personnel_code=u.personnel_code,
        full_name=u.full_name,
        mobile=u.mobile,
        unit_id=u.unit_id,
        job_title=u.job_title,
        is_active=u.is_active,
        must_change_password=u.must_change_password,
        roles=[RoleAssignment(role=r.role.code, unit_id=r.unit_id) for r in u.roles],
    )


def _set_roles(db: Session, user: User, roles: list[RoleAssignment]) -> None:
    by_code = {r.code: r for r in db.scalars(select(Role))}
    seen = set()
    new_roles = []
    for a in roles:
        role = by_code.get(a.role)
        if role is None:
            raise api_error(status.HTTP_400_BAD_REQUEST, "unknown_role", f"نقش ناشناخته: {a.role}")
        if a.unit_id is not None and db.get(Unit, a.unit_id) is None:
            raise api_error(status.HTTP_400_BAD_REQUEST, "unknown_unit", "واحد نامعتبر است")
        key = (role.id, a.unit_id)
        if key not in seen:
            seen.add(key)
            new_roles.append(UserRole(role=role, unit_id=a.unit_id))
    user.roles.clear()
    db.flush()
    user.roles.extend(new_roles)


# فهرست نقش‌ها و واحدها برای فرم‌های پنل (انتساب نقش، هدف‌گذاری اطلاعیه، نمایش نام واحد) هم لازم است.
_CATALOG_READERS = (
    "user.manage",
    "unit.manage",
    "announcement.manage",
    "feedback.manage",
    "training.manage",
    "doctor.manage",
)


@router.get("/roles", response_model=list[RoleOut])
def list_roles(_: CurrentUser = Depends(require(*_CATALOG_READERS)), db: Session = Depends(get_db)):
    return [RoleOut(code=r.code, name=r.name, permissions=r.permissions) for r in db.scalars(select(Role))]


@router.get("/users", response_model=list[UserOut])
def list_users(
    q: str | None = None,
    _: CurrentUser = Depends(require("user.manage")),
    db: Session = Depends(get_db),
):
    stmt = select(User).options(selectinload(User.roles)).order_by(User.full_name)
    if q:
        stmt = stmt.where((User.full_name.ilike(f"%{q}%")) | (User.personnel_code == q))
    return [_user_out(u) for u in db.scalars(stmt)]


@router.post("/users", response_model=UserOut, status_code=201)
def create_user(
    body: UserCreateIn,
    request: Request,
    cu: CurrentUser = Depends(require("user.manage")),
    db: Session = Depends(get_db),
):
    code = body.personnel_code.strip()
    if db.scalar(select(User.id).where(User.personnel_code == code)) is not None:
        raise api_error(status.HTTP_409_CONFLICT, "duplicate_user", "این کد پرسنلی قبلاً ثبت شده است")
    user = User(
        personnel_code=code,
        full_name=body.full_name.strip(),
        mobile=body.mobile,
        unit_id=body.unit_id,
        job_title=(body.job_title or "").strip() or None,
        password_hash=hash_password(body.temporary_password),
        must_change_password=True,
    )
    db.add(user)
    _set_roles(db, user, body.roles)
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise api_error(
            status.HTTP_409_CONFLICT, "duplicate_user", "این کد پرسنلی قبلاً ثبت شده است"
        ) from None
    audit(db, "user.created", cu.id, "user", user.id, client_ip(request), roles=[r.role for r in body.roles])
    db.commit()
    return _user_out(user)


@router.patch("/users/{user_id}", response_model=UserOut)
def update_user(
    user_id: uuid.UUID,
    body: UserUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(require("user.manage")),
    db: Session = Depends(get_db),
):
    user = db.get(User, user_id, options=[selectinload(User.roles)])
    if user is None:
        raise api_error(404, "not_found", "کاربر پیدا نشد")
    changes = body.model_dump(exclude_unset=True, exclude={"roles"})
    if "job_title" in changes:
        changes["job_title"] = (changes["job_title"] or "").strip() or None
    for k, v in changes.items():
        setattr(user, k, v)
    if body.roles is not None:
        _set_roles(db, user, body.roles)
    db.flush()
    # غیرفعال شدن، تغییر واحد یا نقش: عضویت در گفتگوها و کانال‌های واحد همان لحظه به‌روز می‌شود
    chat.sync_user(db, user)
    audit(
        db,
        "user.updated",
        cu.id,
        "user",
        user.id,
        client_ip(request),
        fields=sorted(changes),
        roles=[r.role for r in body.roles] if body.roles is not None else None,
    )
    db.commit()
    return _user_out(user)


@router.post("/users/{user_id}/reset-password", status_code=204)
def reset_password(
    user_id: uuid.UUID,
    body: ResetPasswordIn,
    request: Request,
    cu: CurrentUser = Depends(require("user.manage")),
    db: Session = Depends(get_db),
):
    user = db.get(User, user_id)
    if user is None:
        raise api_error(404, "not_found", "کاربر پیدا نشد")
    user.password_hash = hash_password(body.temporary_password)
    user.must_change_password = True
    user.failed_attempts = 0
    user.locked_until = None
    audit(db, "user.password_reset", cu.id, "user", user.id, client_ip(request))
    db.commit()


@router.get("/units", response_model=list[UnitOut])
def list_units(_: CurrentUser = Depends(require(*_CATALOG_READERS)), db: Session = Depends(get_db)):
    return db.scalars(select(Unit).order_by(Unit.name)).all()


@router.post("/units", response_model=UnitOut, status_code=201)
def create_unit(
    body: UnitIn,
    request: Request,
    cu: CurrentUser = Depends(require("unit.manage")),
    db: Session = Depends(get_db),
):
    unit = Unit(**body.model_dump())
    db.add(unit)
    db.flush()
    audit(db, "unit.created", cu.id, "unit", unit.id, client_ip(request))
    db.commit()
    return unit


@router.put("/units/{unit_id}", response_model=UnitOut)
def update_unit(
    unit_id: uuid.UUID,
    body: UnitIn,
    request: Request,
    cu: CurrentUser = Depends(require("unit.manage")),
    db: Session = Depends(get_db),
):
    unit = db.get(Unit, unit_id)
    if unit is None:
        raise api_error(404, "not_found", "واحد پیدا نشد")
    for k, v in body.model_dump().items():
        setattr(unit, k, v)
    audit(db, "unit.updated", cu.id, "unit", unit.id, client_ip(request))
    db.commit()
    return unit


UNIT_IMAGE_TYPES: dict[str, str] = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
}
UNIT_IMAGE_MAX_MB = 5


@router.post("/units/{unit_id}/image", response_model=UnitOut, status_code=201)
async def upload_unit_image(
    unit_id: uuid.UUID,
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    cu: CurrentUser = Depends(require("unit.manage")),
    db: Session = Depends(get_db),
):
    """تصویر معرفی واحد؛ بدنه درخواست خود فایل است. تصویر قبلی فقط از واحد جدا می‌شود."""
    unit = db.get(Unit, unit_id)
    if unit is None:
        raise api_error(404, "not_found", "واحد پیدا نشد")
    m: MediaFile = await store_upload(request, filename, UNIT_IMAGE_TYPES, UNIT_IMAGE_MAX_MB, cu.id)
    db.add(m)
    db.flush()
    unit.image_media_id = m.id
    audit(db, "unit.image_updated", cu.id, "unit", unit.id, client_ip(request))
    db.commit()
    return unit


def _unit_dependents(db: Session, unit: Unit) -> bool:
    """آیا جدولی (کاربر، درخواست، برنامه، گفتگو، والد و...) به این واحد ارجاع دارد و ردیفی دارد؟"""
    for table in Base.metadata.tables.values():
        for fk in table.foreign_keys:
            if fk.column.table.name != Unit.__tablename__:
                continue
            if db.scalar(select(func.count()).select_from(table).where(fk.parent == unit.id)):
                return True
    return False


@router.delete("/units/{unit_id}", status_code=204)
def delete_unit(
    unit_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("unit.manage")),
    db: Session = Depends(get_db),
):
    """حذف واحد فقط وقتی ممکن است که هیچ داده‌ای به آن وصل نباشد؛ حذف آبشاری داده‌های برنامه را پاک می‌کند."""
    unit = db.get(Unit, unit_id)
    if unit is None:
        raise api_error(404, "not_found", "واحد پیدا نشد")
    if _unit_dependents(db, unit):
        raise api_error(
            409,
            "unit_in_use",
            "این واحد به کاربر، درخواست، برنامه یا گفتگو وصل است و حذف نمی‌شود؛ به‌جای آن از راهنمای عمومی خارجش کنید",
        )
    audit(db, "unit.deleted", cu.id, "unit", unit.id, client_ip(request))
    db.delete(unit)
    db.commit()


@router.get("/audit")
def list_audit(
    action: str | None = None,
    entity_id: str | None = None,
    page: int = Query(1, ge=1),
    size: int = Query(50, ge=1, le=200),
    _: CurrentUser = Depends(require("audit.read")),
    db: Session = Depends(get_db),
):
    stmt = select(AuditLog)
    if action:
        stmt = stmt.where(AuditLog.action == action)
    if entity_id:
        stmt = stmt.where(AuditLog.entity_id == entity_id)
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.scalars(stmt.order_by(AuditLog.at.desc()).offset((page - 1) * size).limit(size))
    return {
        "total": total,
        "items": [
            {
                "at": r.at,
                "actor_id": r.actor_id,
                "action": r.action,
                "entity_type": r.entity_type,
                "entity_id": r.entity_id,
                "ip": r.ip,
                "details": r.details,
            }
            for r in rows
        ],
    }
