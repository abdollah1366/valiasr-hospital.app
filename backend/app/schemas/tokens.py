import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class TokenTxOut(BaseModel):
    id: uuid.UUID
    amount: int
    kind: str
    note: str | None
    created_at: datetime


class RewardOut(BaseModel):
    id: uuid.UUID
    title: str
    description: str | None
    cost: int
    is_active: bool = True
    sort_order: int = 0


class VoucherOut(BaseModel):
    code: str
    reward_title: str
    cost: int
    status: str
    created_at: datetime


class TokensOut(BaseModel):
    balance: int
    referral_code: str
    referred: bool
    referral_reward: int
    referred_bonus: int
    history: list[TokenTxOut]
    rewards: list[RewardOut]
    vouchers: list[VoucherOut]


class ReferralIn(BaseModel):
    code: str = Field(min_length=4, max_length=12)


class RedeemIn(BaseModel):
    reward_id: uuid.UUID


class RewardIn(BaseModel):
    title: str = Field(min_length=2, max_length=200)
    description: str | None = Field(None, max_length=500)
    cost: int = Field(ge=1, le=100000)
    is_active: bool = True
    sort_order: int = 0


class RewardPatch(BaseModel):
    title: str | None = Field(None, min_length=2, max_length=200)
    description: str | None = Field(None, max_length=500)
    cost: int | None = Field(None, ge=1, le=100000)
    is_active: bool | None = None
    sort_order: int | None = None


class AwardIn(BaseModel):
    patient_id: uuid.UUID
    amount: int = Field(ge=-100000, le=100000)
    note: str = Field(min_length=2, max_length=300)


class PatientTokenRow(BaseModel):
    id: uuid.UUID
    mobile: str
    full_name: str | None
    balance: int


class ManageVoucherOut(VoucherOut):
    patient_mobile: str
    patient_name: str | None
    used_at: datetime | None
