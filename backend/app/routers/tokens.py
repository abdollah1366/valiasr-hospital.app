"""باشگاه مراجعان: ژتون معرفی اپ، اهدا از طرف مدیریت و تحویل کد استفاده در پذیرش.

- مراجع: موجودی و سابقه، کد معرف خودش، ثبت کد معرف دیگری (یک بار)، خرج ژتون برای دریافت کد استفاده
- مدیریت (token.manage): اهدا یا کم کردن ژتون، تعریف امتیازها، برگرداندن کد استفاده با بازپرداخت
- پذیرش و منشی (token.redeem): دیدن کد مراجع و ثبت تحویل
"""

import uuid

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, require
from app.models import Patient, TokenRedemption, TokenReward, TokenTransaction
from app.models import utcnow as now
from app.routers.patient import get_current_patient
from app.schemas.tokens import (
    AwardIn,
    ManageVoucherOut,
    PatientTokenRow,
    RedeemIn,
    ReferralIn,
    RewardIn,
    RewardOut,
    RewardPatch,
    TokensOut,
    TokenTxOut,
    VoucherOut,
)
from app.services import tokens as svc

router = APIRouter(tags=["tokens"])

HISTORY_LIMIT = 50


def _voucher(r: TokenRedemption) -> VoucherOut:
    return VoucherOut(
        code=r.code, reward_title=r.reward_title, cost=r.cost, status=r.status, created_at=r.created_at
    )


def _reward(r: TokenReward) -> RewardOut:
    return RewardOut(
        id=r.id,
        title=r.title,
        description=r.description,
        cost=r.cost,
        is_active=r.is_active,
        sort_order=r.sort_order,
    )


def _tokens_out(db: Session, patient: Patient) -> TokensOut:
    s = get_settings()
    code = svc.ensure_referral_code(db, patient)
    db.commit()
    history = db.scalars(
        select(TokenTransaction)
        .where(TokenTransaction.patient_id == patient.id)
        .order_by(TokenTransaction.created_at.desc())
        .limit(HISTORY_LIMIT)
    ).all()
    rewards = db.scalars(
        select(TokenReward)
        .where(TokenReward.is_active.is_(True))
        .order_by(TokenReward.sort_order, TokenReward.cost)
    ).all()
    vouchers = db.scalars(
        select(TokenRedemption)
        .where(TokenRedemption.patient_id == patient.id)
        .order_by(TokenRedemption.created_at.desc())
        .limit(HISTORY_LIMIT)
    ).all()
    return TokensOut(
        balance=svc.balance(db, patient.id),
        referral_code=code,
        referred=patient.referred_by_id is not None,
        referral_reward=s.token_referral_reward,
        referred_bonus=s.token_referred_bonus,
        history=[
            TokenTxOut(id=t.id, amount=t.amount, kind=t.kind, note=t.note, created_at=t.created_at)
            for t in history
        ],
        rewards=[_reward(r) for r in rewards],
        vouchers=[_voucher(v) for v in vouchers],
    )


# ---------------------------------------------------------------- مراجع


@router.get("/patient/tokens", response_model=TokensOut)
def my_tokens(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    return _tokens_out(db, patient)


@router.post("/patient/referral", response_model=TokensOut)
def enter_referral(
    body: ReferralIn, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)
):
    svc.apply_referral(db, patient, body.code)
    db.commit()
    return _tokens_out(db, patient)


@router.post("/patient/tokens/redeem", response_model=VoucherOut, status_code=201)
def redeem(body: RedeemIn, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    reward = db.get(TokenReward, body.reward_id)
    if reward is None:
        raise api_error(404, "not_found", "امتیاز پیدا نشد")
    r = svc.redeem(db, patient, reward)
    db.commit()
    return _voucher(r)


# ---------------------------------------------------------------- مدیریت


@router.get("/manage/tokens/patients", response_model=list[PatientTokenRow])
def search_patients(
    q: str = Query(min_length=3, max_length=20),
    cu: CurrentUser = Depends(require("token.manage")),
    db: Session = Depends(get_db),
):
    digits = "".join(c for c in q if c.isdigit()).lstrip("0")
    rows = db.scalars(
        select(Patient).where(Patient.mobile.contains(digits or q)).order_by(Patient.mobile).limit(20)
    ).all()
    return [
        PatientTokenRow(id=p.id, mobile=p.mobile, full_name=p.full_name, balance=svc.balance(db, p.id))
        for p in rows
    ]


@router.post("/manage/tokens/award", response_model=PatientTokenRow)
def award(
    body: AwardIn,
    request: Request,
    cu: CurrentUser = Depends(require("token.manage")),
    db: Session = Depends(get_db),
):
    p = db.get(Patient, body.patient_id)
    if p is None:
        raise api_error(404, "not_found", "مراجع پیدا نشد")
    if body.amount == 0:
        raise api_error(422, "zero_amount", "مبلغ نباید صفر باشد")
    new_balance = svc.add(db, p, body.amount, "admin", body.note.strip(), cu.id)
    audit(db, "token.award", cu.id, "patient", p.id, client_ip(request), amount=body.amount)
    db.commit()
    return PatientTokenRow(id=p.id, mobile=p.mobile, full_name=p.full_name, balance=new_balance)


@router.get("/manage/tokens/rewards", response_model=list[RewardOut])
def list_rewards(cu: CurrentUser = Depends(require("token.manage")), db: Session = Depends(get_db)):
    return [
        _reward(r) for r in db.scalars(select(TokenReward).order_by(TokenReward.sort_order, TokenReward.cost))
    ]


@router.post("/manage/tokens/rewards", response_model=RewardOut, status_code=201)
def create_reward(
    body: RewardIn,
    request: Request,
    cu: CurrentUser = Depends(require("token.manage")),
    db: Session = Depends(get_db),
):
    r = TokenReward(**body.model_dump())
    db.add(r)
    db.flush()
    audit(db, "token.reward.create", cu.id, "token_reward", r.id, client_ip(request))
    db.commit()
    return _reward(r)


@router.patch("/manage/tokens/rewards/{reward_id}", response_model=RewardOut)
def update_reward(
    reward_id: uuid.UUID,
    body: RewardPatch,
    request: Request,
    cu: CurrentUser = Depends(require("token.manage")),
    db: Session = Depends(get_db),
):
    r = db.get(TokenReward, reward_id)
    if r is None:
        raise api_error(404, "not_found", "امتیاز پیدا نشد")
    for k, v in body.model_dump(exclude_unset=True).items():
        setattr(r, k, v)
    audit(db, "token.reward.update", cu.id, "token_reward", r.id, client_ip(request))
    db.commit()
    return _reward(r)


def _voucher_or_404(db: Session, code: str) -> tuple[TokenRedemption, Patient]:
    r = db.scalar(select(TokenRedemption).where(TokenRedemption.code == code.strip().upper()))
    if r is None:
        raise api_error(404, "not_found", "کد استفاده پیدا نشد")
    return r, db.get(Patient, r.patient_id)


def _manage_voucher(r: TokenRedemption, p: Patient) -> ManageVoucherOut:
    return ManageVoucherOut(
        **_voucher(r).model_dump(), patient_mobile=p.mobile, patient_name=p.full_name, used_at=r.used_at
    )


@router.get("/manage/tokens/vouchers/{code}", response_model=ManageVoucherOut)
def get_voucher(
    code: str,
    cu: CurrentUser = Depends(require("token.redeem", "token.manage")),
    db: Session = Depends(get_db),
):
    return _manage_voucher(*_voucher_or_404(db, code))


@router.post("/manage/tokens/vouchers/{code}/use", response_model=ManageVoucherOut)
def use_voucher(
    code: str,
    request: Request,
    cu: CurrentUser = Depends(require("token.redeem", "token.manage")),
    db: Session = Depends(get_db),
):
    r, p = _voucher_or_404(db, code)
    if r.status != "issued":
        raise api_error(409, "voucher_not_active", "این کد قبلاً استفاده یا باطل شده است")
    r.status, r.used_at, r.used_by = "used", now(), cu.id
    audit(db, "token.voucher.use", cu.id, "token_redemption", r.id, client_ip(request))
    db.commit()
    return _manage_voucher(r, p)


@router.post("/manage/tokens/vouchers/{code}/cancel", response_model=ManageVoucherOut)
def cancel_voucher(
    code: str,
    request: Request,
    cu: CurrentUser = Depends(require("token.manage")),
    db: Session = Depends(get_db),
):
    r, p = _voucher_or_404(db, code)
    if r.status != "issued":
        raise api_error(409, "voucher_not_active", "این کد قبلاً استفاده یا باطل شده است")
    r.status = "cancelled"
    svc.add(db, p, r.cost, "refund", r.reward_title, cu.id)
    audit(db, "token.voucher.cancel", cu.id, "token_redemption", r.id, client_ip(request))
    db.commit()
    return _manage_voucher(r, p)
