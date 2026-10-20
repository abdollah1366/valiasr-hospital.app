import uuid
from dataclasses import dataclass, field

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session, selectinload

from app.core.db import get_db
from app.core.security import decode_access_token
from app.models import User

_bearer = HTTPBearer(auto_error=False)


def api_error(status_code: int, code: str, message: str) -> HTTPException:
    return HTTPException(status_code=status_code, detail={"code": code, "message": message})


@dataclass
class CurrentUser:
    user: User
    global_permissions: set[str] = field(default_factory=set)
    unit_permissions: dict[str, set[uuid.UUID]] = field(default_factory=dict)
    role_codes: set[str] = field(default_factory=set)

    @property
    def id(self) -> uuid.UUID:
        return self.user.id

    def has(self, permission: str) -> bool:
        return permission in self.global_permissions or bool(self.unit_permissions.get(permission))

    def has_global(self, permission: str) -> bool:
        return permission in self.global_permissions

    def can_in_unit(self, permission: str, unit_id: uuid.UUID | None) -> bool:
        if permission in self.global_permissions:
            return True
        return unit_id is not None and unit_id in self.unit_permissions.get(permission, set())

    def units_for(self, permission: str) -> set[uuid.UUID]:
        return self.unit_permissions.get(permission, set())

    @property
    def all_permissions(self) -> list[str]:
        return sorted(self.global_permissions | {p for p, units in self.unit_permissions.items() if units})


def build_current_user(user: User) -> CurrentUser:
    cu = CurrentUser(user=user)
    for ur in user.roles:
        cu.role_codes.add(ur.role.code)
        for perm in ur.role.permissions:
            if ur.unit_id is None:
                cu.global_permissions.add(perm)
            else:
                cu.unit_permissions.setdefault(perm, set()).add(ur.unit_id)
    return cu


def get_current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> CurrentUser:
    if creds is None:
        raise api_error(status.HTTP_401_UNAUTHORIZED, "not_authenticated", "ابتدا وارد شوید")
    user_id = decode_access_token(creds.credentials)
    if user_id is None:
        raise api_error(status.HTTP_401_UNAUTHORIZED, "invalid_token", "نشست نامعتبر یا منقضی است")
    user = db.get(User, user_id, options=[selectinload(User.roles)])
    if user is None or not user.is_active:
        raise api_error(status.HTTP_401_UNAUTHORIZED, "invalid_token", "نشست نامعتبر یا منقضی است")
    return build_current_user(user)


def get_active_user(cu: CurrentUser = Depends(get_current_user)) -> CurrentUser:
    """کاربری که رمز موقت را عوض کرده است؛ برای همه endpointها به جز تغییر رمز و /me."""
    if cu.user.must_change_password:
        raise api_error(status.HTTP_403_FORBIDDEN, "password_change_required", "ابتدا رمز عبور را تغییر دهید")
    return cu


def require(*permissions: str):
    """کاربر باید دست‌کم یکی از مجوزهای داده‌شده را داشته باشد."""

    def checker(cu: CurrentUser = Depends(get_active_user)) -> CurrentUser:
        if not any(cu.has(p) for p in permissions):
            raise api_error(status.HTTP_403_FORBIDDEN, "forbidden", "به این بخش دسترسی ندارید")
        return cu

    return checker


def client_ip(request: Request) -> str | None:
    return request.client.host if request.client else None
