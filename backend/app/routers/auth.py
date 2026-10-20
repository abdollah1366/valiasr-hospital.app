import uuid
from datetime import UTC, datetime, timedelta

from fastapi import APIRouter, Depends, Request, status
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, get_current_user
from app.core.security import (
    create_access_token,
    hash_password,
    new_opaque_token,
    sha256,
    verify_password,
)
from app.models import RefreshToken, Unit, User
from app.schemas import ChangePasswordIn, LoginIn, MeOut, RefreshIn, TokenOut

router = APIRouter(prefix="/auth", tags=["auth"])

# برای اینکه زمان پاسخ برای کد پرسنلی ناموجود و رمز اشتباه یکسان باشد.
_DUMMY_HASH = hash_password("dummy-password-for-timing")


def _issue_tokens(db: Session, user: User, family_id: uuid.UUID, device: str | None) -> TokenOut:
    s = get_settings()
    refresh = new_opaque_token()
    db.add(
        RefreshToken(
            user_id=user.id,
            token_hash=sha256(refresh),
            family_id=family_id,
            expires_at=datetime.now(UTC) + timedelta(days=s.refresh_token_days),
            device=device,
        )
    )
    return TokenOut(
        access_token=create_access_token(user.id),
        refresh_token=refresh,
        expires_in=s.access_token_minutes * 60,
        must_change_password=user.must_change_password,
    )


@router.post("/login", response_model=TokenOut)
def login(body: LoginIn, request: Request, db: Session = Depends(get_db)) -> TokenOut:
    s = get_settings()
    now = datetime.now(UTC)
    ip = client_ip(request)
    user = db.scalar(select(User).where(User.personnel_code == body.personnel_code.strip()))
    invalid = api_error(
        status.HTTP_401_UNAUTHORIZED, "invalid_credentials", "کد پرسنلی یا رمز عبور اشتباه است"
    )

    if user is None:
        verify_password(body.password, _DUMMY_HASH)
        audit(db, "auth.login_failed", ip=ip, personnel_code=body.personnel_code[:50])
        db.commit()
        raise invalid
    if user.locked_until and user.locked_until > now:
        audit(db, "auth.login_locked", actor_id=user.id, ip=ip)
        db.commit()
        raise api_error(
            status.HTTP_423_LOCKED, "account_locked", "حساب به دلیل تلاش‌های ناموفق موقتاً قفل شده است"
        )
    if not verify_password(body.password, user.password_hash) or not user.is_active:
        user.failed_attempts += 1
        if user.failed_attempts >= s.max_failed_logins:
            user.locked_until = now + timedelta(minutes=s.lockout_minutes)
            user.failed_attempts = 0
        audit(db, "auth.login_failed", actor_id=user.id, ip=ip)
        db.commit()
        raise invalid

    user.failed_attempts = 0
    user.locked_until = None
    user.last_login_at = now
    tokens = _issue_tokens(db, user, uuid.uuid4(), body.device)
    audit(db, "auth.login", actor_id=user.id, ip=ip)
    db.commit()
    return tokens


@router.post("/refresh", response_model=TokenOut)
def refresh(body: RefreshIn, request: Request, db: Session = Depends(get_db)) -> TokenOut:
    now = datetime.now(UTC)
    token = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == sha256(body.refresh_token)))
    invalid = api_error(
        status.HTTP_401_UNAUTHORIZED, "invalid_refresh", "نشست منقضی شده است؛ دوباره وارد شوید"
    )
    if token is None:
        raise invalid
    if token.revoked_at is not None:
        # استفاده دوباره از توکن مصرف‌شده: احتمال سرقت؛ همه نشست‌های این زنجیره باطل می‌شوند.
        db.execute(
            update(RefreshToken)
            .where(RefreshToken.family_id == token.family_id, RefreshToken.revoked_at.is_(None))
            .values(revoked_at=now)
        )
        audit(db, "auth.refresh_reuse", actor_id=token.user_id, ip=client_ip(request))
        db.commit()
        raise invalid
    if token.expires_at <= now:
        raise invalid
    user = db.get(User, token.user_id)
    if user is None or not user.is_active:
        raise invalid
    token.revoked_at = now
    tokens = _issue_tokens(db, user, token.family_id, token.device)
    db.commit()
    return tokens


@router.post("/logout", status_code=204)
def logout(body: RefreshIn, db: Session = Depends(get_db)) -> None:
    token = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == sha256(body.refresh_token)))
    if token is not None and token.revoked_at is None:
        token.revoked_at = datetime.now(UTC)
        audit(db, "auth.logout", actor_id=token.user_id)
        db.commit()


@router.post("/change-password", status_code=204)
def change_password(
    body: ChangePasswordIn,
    request: Request,
    cu: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> None:
    user = db.get(User, cu.id)
    if not verify_password(body.current_password, user.password_hash):
        raise api_error(status.HTTP_400_BAD_REQUEST, "wrong_password", "رمز فعلی اشتباه است")
    if body.new_password == body.current_password:
        raise api_error(status.HTTP_400_BAD_REQUEST, "same_password", "رمز جدید باید با رمز فعلی متفاوت باشد")
    if body.new_password.isdigit() or body.new_password.isalpha():
        raise api_error(status.HTTP_400_BAD_REQUEST, "weak_password", "رمز باید ترکیبی از حرف و عدد باشد")
    user.password_hash = hash_password(body.new_password)
    user.must_change_password = False
    audit(db, "auth.password_changed", actor_id=user.id, ip=client_ip(request))
    db.commit()


@router.get("/me", response_model=MeOut)
def me(cu: CurrentUser = Depends(get_current_user), db: Session = Depends(get_db)) -> MeOut:
    u = cu.user
    unit = db.get(Unit, u.unit_id) if u.unit_id else None
    return MeOut(
        id=u.id,
        personnel_code=u.personnel_code,
        full_name=u.full_name,
        unit_id=u.unit_id,
        unit_name=unit.name if unit else None,
        job_title=u.job_title,
        roles=sorted(cu.role_codes),
        permissions=cu.all_permissions,
        must_change_password=u.must_change_password,
    )
