"""ورود مراجعان با موبایل و کد پیامکی و نمایه آن‌ها. کاملاً جدا از حساب و توکن کارکنان."""

import uuid
from datetime import UTC, datetime, timedelta

from fastapi import APIRouter, Depends, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import api_error
from app.core.security import (
    create_access_token,
    decode_access_token,
    new_opaque_token,
    new_otp_code,
    otp_hash,
    sha256,
)
from app.models import OtpCode, Patient, PatientRefreshToken
from app.schemas import (
    OtpRequestIn,
    OtpRequestOut,
    OtpVerifyIn,
    PatientOut,
    PatientTokenOut,
    PatientUpdateIn,
    RefreshIn,
)
from app.services import sms
from app.services.phone import language_for_mobile, normalize_mobile

router = APIRouter(prefix="/patient", tags=["patient"])
_bearer = HTTPBearer(auto_error=False)

PATIENT_TOKEN = "patient"


def _mobile_or_error(raw: str) -> str:
    mobile = normalize_mobile(raw)
    if mobile is None:
        raise api_error(422, "invalid_mobile", "شماره موبایل نامعتبر است")
    return mobile


def _issue_tokens(db: Session, patient: Patient, family_id: uuid.UUID, device: str | None, is_new=False):
    s = get_settings()
    refresh = new_opaque_token()
    db.add(
        PatientRefreshToken(
            patient_id=patient.id,
            token_hash=sha256(refresh),
            family_id=family_id,
            expires_at=datetime.now(UTC) + timedelta(days=s.refresh_token_days),
            device=device,
        )
    )
    return PatientTokenOut(
        access_token=create_access_token(patient.id, PATIENT_TOKEN),
        refresh_token=refresh,
        expires_in=s.access_token_minutes * 60,
        patient=PatientOut.model_validate(patient),
        is_new=is_new,
    )


@router.post("/auth/otp", response_model=OtpRequestOut, status_code=202)
def request_otp(body: OtpRequestIn, db: Session = Depends(get_db)) -> OtpRequestOut:
    s = get_settings()
    mobile = _mobile_or_error(body.mobile)
    now = datetime.now(UTC)
    last = db.scalar(select(func.max(OtpCode.created_at)).where(OtpCode.mobile == mobile))
    if last is not None and last > now - timedelta(seconds=s.otp_resend_seconds):
        wait = int((last + timedelta(seconds=s.otp_resend_seconds) - now).total_seconds()) + 1
        raise api_error(429, "otp_too_soon", f"لطفاً {wait} ثانیه دیگر دوباره تلاش کنید")
    sent_last_hour = db.scalar(
        select(func.count()).where(OtpCode.mobile == mobile, OtpCode.created_at > now - timedelta(hours=1))
    )
    if sent_last_hour >= s.otp_max_per_hour:
        raise api_error(429, "otp_limit", "تعداد درخواست کد زیاد است؛ یک ساعت دیگر تلاش کنید")

    # کدهای قبلی این شماره باطل می‌شوند تا فقط آخرین کد معتبر باشد.
    db.execute(
        update(OtpCode).where(OtpCode.mobile == mobile, OtpCode.consumed_at.is_(None)).values(consumed_at=now)
    )
    code = new_otp_code()
    db.add(
        OtpCode(
            mobile=mobile,
            code_hash=otp_hash(mobile, code),
            created_at=now,
            expires_at=now + timedelta(seconds=s.otp_ttl_seconds),
        )
    )
    try:
        sms.send_otp(mobile, code)
    except sms.SmsError:
        db.rollback()
        raise api_error(503, "sms_failed", "ارسال پیامک ممکن نشد؛ کمی بعد دوباره تلاش کنید") from None
    db.commit()
    return OtpRequestOut(
        mobile=mobile,
        expires_in=s.otp_ttl_seconds,
        resend_after=s.otp_resend_seconds,
        suggested_language=language_for_mobile(mobile),
        debug_code=code if s.otp_debug_enabled else None,
    )


@router.post("/auth/verify", response_model=PatientTokenOut)
def verify_otp(body: OtpVerifyIn, db: Session = Depends(get_db)) -> PatientTokenOut:
    s = get_settings()
    mobile = _mobile_or_error(body.mobile)
    now = datetime.now(UTC)
    invalid = api_error(400, "otp_invalid", "کد واردشده اشتباه یا منقضی است")
    otp = db.scalar(
        select(OtpCode)
        .where(OtpCode.mobile == mobile, OtpCode.consumed_at.is_(None), OtpCode.expires_at > now)
        .order_by(OtpCode.created_at.desc())
        .limit(1)
        .with_for_update()
    )
    if otp is None:
        raise invalid
    otp.attempts += 1
    code = body.code.strip().translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹", "0123456789"))
    if otp.code_hash != otp_hash(mobile, code):
        if otp.attempts >= s.otp_max_attempts:
            otp.consumed_at = now
        db.commit()
        raise invalid
    otp.consumed_at = now

    patient = db.scalar(select(Patient).where(Patient.mobile == mobile))
    is_new = patient is None
    if patient is None:
        patient = Patient(mobile=mobile, language=body.language or language_for_mobile(mobile))
        db.add(patient)
        db.flush()
    elif not patient.is_active:
        db.commit()
        raise api_error(403, "patient_inactive", "این حساب غیرفعال شده است")
    elif body.language:
        patient.language = body.language
    patient.last_login_at = now
    tokens = _issue_tokens(db, patient, uuid.uuid4(), body.device, is_new)
    db.commit()
    return tokens


@router.post("/auth/refresh", response_model=PatientTokenOut)
def refresh(body: RefreshIn, db: Session = Depends(get_db)) -> PatientTokenOut:
    now = datetime.now(UTC)
    token = db.scalar(
        select(PatientRefreshToken).where(PatientRefreshToken.token_hash == sha256(body.refresh_token))
    )
    invalid = api_error(
        status.HTTP_401_UNAUTHORIZED, "invalid_refresh", "نشست منقضی شده است؛ دوباره وارد شوید"
    )
    if token is None:
        raise invalid
    if token.revoked_at is not None:
        db.execute(
            update(PatientRefreshToken)
            .where(PatientRefreshToken.family_id == token.family_id, PatientRefreshToken.revoked_at.is_(None))
            .values(revoked_at=now)
        )
        db.commit()
        raise invalid
    if token.expires_at <= now:
        raise invalid
    patient = db.get(Patient, token.patient_id)
    if patient is None or not patient.is_active:
        raise invalid
    token.revoked_at = now
    tokens = _issue_tokens(db, patient, token.family_id, token.device)
    db.commit()
    return tokens


@router.post("/auth/logout", status_code=204)
def logout(body: RefreshIn, db: Session = Depends(get_db)) -> None:
    token = db.scalar(
        select(PatientRefreshToken).where(PatientRefreshToken.token_hash == sha256(body.refresh_token))
    )
    if token is not None and token.revoked_at is None:
        token.revoked_at = datetime.now(UTC)
        db.commit()


def get_current_patient(
    creds: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> Patient:
    patient_id = decode_access_token(creds.credentials, PATIENT_TOKEN) if creds else None
    patient = db.get(Patient, patient_id) if patient_id else None
    if patient is None or not patient.is_active:
        raise api_error(status.HTTP_401_UNAUTHORIZED, "invalid_token", "نشست نامعتبر یا منقضی است")
    return patient


@router.get("/me", response_model=PatientOut)
def me(patient: Patient = Depends(get_current_patient)) -> Patient:
    return patient


@router.patch("/me", response_model=PatientOut)
def update_me(
    body: PatientUpdateIn,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
) -> Patient:
    if body.language is not None:
        patient.language = body.language
    if body.full_name is not None:
        patient.full_name = body.full_name.strip() or None
    db.commit()
    return patient
