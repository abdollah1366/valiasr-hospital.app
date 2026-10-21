import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class ReviewIn(BaseModel):
    stars: int = Field(ge=1, le=5)
    comment: str | None = Field(default=None, max_length=1500)


class ReplyIn(BaseModel):
    text: str = Field(min_length=1, max_length=1500)


class ModerateIn(BaseModel):
    status: Literal["approved", "rejected"]


class PublicReviewOut(BaseModel):
    """نظر منتشرشده؛ نام مراجع نمایش داده نمی‌شود."""

    id: uuid.UUID
    stars: int
    comment: str | None
    created_at: datetime
    doctor_reply: str | None
    admin_reply: str | None


class MyReviewOut(BaseModel):
    id: uuid.UUID
    stars: int
    comment: str | None
    status: str


class ManageReviewOut(BaseModel):
    """نمای مدیر پزشکان، مدیر سیستم و خود پزشک."""

    id: uuid.UUID
    doctor_id: uuid.UUID
    doctor_name: str
    stars: int
    comment: str | None
    status: str
    created_at: datetime
    visit_at: datetime
    doctor_reply: str | None
    doctor_reply_at: datetime | None
    admin_reply: str | None
    admin_reply_at: datetime | None


class RatingOut(BaseModel):
    average: float | None
    count: int
