"""باشگاه مراجعان: ژتون معرفی، اهدا از طرف مدیریت و خرج کردن برای دریافت کد استفاده.

موجودی ثبت جداگانه ندارد و از جمع دفتر [TokenTransaction] می‌آید تا با دفتر همیشه یکی باشد.
"""

import secrets

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.deps import api_error
from app.models import Appointment, Patient, TokenRedemption, TokenReward, TokenTransaction

# حروف و ارقام بدون نویسه‌های شبیه هم (0/O و 1/I)
_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


def _new_code(n: int) -> str:
    return "".join(secrets.choice(_ALPHABET) for _ in range(n))


def balance(db: Session, patient_id) -> int:
    return db.scalar(
        select(func.coalesce(func.sum(TokenTransaction.amount), 0)).where(
            TokenTransaction.patient_id == patient_id
        )
    )


def _lock(db: Session, patient: Patient) -> None:
    """قفل ردیف مراجع تا دو خرج همزمان از یک موجودی نگذرد."""
    db.execute(select(Patient.id).where(Patient.id == patient.id).with_for_update())


def add(db: Session, patient: Patient, amount: int, kind: str, note: str | None = None, actor_id=None) -> int:
    _lock(db, patient)
    new_balance = balance(db, patient.id) + amount
    if new_balance < 0:
        raise api_error(409, "insufficient_tokens", "موجودی ژتون کافی نیست")
    db.add(TokenTransaction(patient_id=patient.id, amount=amount, kind=kind, note=note, actor_id=actor_id))
    db.flush()
    return new_balance


def ensure_referral_code(db: Session, patient: Patient) -> str:
    if patient.referral_code is None:
        while True:
            code = _new_code(6)
            if db.scalar(select(Patient.id).where(Patient.referral_code == code)) is None:
                break
        patient.referral_code = code
        db.flush()
    return patient.referral_code


def apply_referral(db: Session, patient: Patient, code: str) -> None:
    if patient.referred_by_id is not None:
        raise api_error(409, "already_referred", "کد معرف قبلاً ثبت شده است")
    referrer = db.scalar(select(Patient).where(Patient.referral_code == code.strip().upper()))
    if referrer is None:
        raise api_error(404, "invalid_code", "کد معرف درست نیست")
    if referrer.id == patient.id:
        raise api_error(409, "own_code", "کد معرف خودتان را نمی‌توانید وارد کنید")
    patient.referred_by_id = referrer.id


def on_attended(db: Session, appt: Appointment) -> None:
    """اولین ویزیت انجام‌شده مراجع معرفی‌شده: هم معرف و هم معرفی‌شده ژتون می‌گیرند (فقط یک بار)."""
    if appt.patient_id is None:
        return
    patient = db.get(Patient, appt.patient_id)
    if patient is None or patient.referred_by_id is None or patient.referral_rewarded:
        return
    referrer = db.get(Patient, patient.referred_by_id)
    patient.referral_rewarded = True
    s = get_settings()
    if referrer is not None and s.token_referral_reward > 0:
        add(db, referrer, s.token_referral_reward, "referral", "معرفی اپ به یک مراجع جدید")
    if s.token_referred_bonus > 0:
        add(db, patient, s.token_referred_bonus, "referred_bonus", "هدیه ورود با کد معرف")


def redeem(db: Session, patient: Patient, reward: TokenReward) -> TokenRedemption:
    if not reward.is_active:
        raise api_error(404, "not_found", "این امتیاز فعال نیست")
    add(db, patient, -reward.cost, "spend", reward.title)
    while True:
        code = _new_code(8)
        if db.scalar(select(TokenRedemption.id).where(TokenRedemption.code == code)) is None:
            break
    r = TokenRedemption(patient_id=patient.id, reward_title=reward.title, cost=reward.cost, code=code)
    db.add(r)
    db.flush()
    return r
