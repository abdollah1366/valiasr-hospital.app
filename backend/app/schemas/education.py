import uuid
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, HttpUrl

EducationKind = Literal["article", "image", "video", "audio", "pdf"]
CareType = Literal[
    "prevention",
    "before_procedure",
    "after_procedure",
    "medication",
    "nutrition",
    "exercise",
    "home_care",
    "warning_signs",
    "other",
]


class EducationIn(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    summary: str | None = Field(default=None, max_length=1000)
    body: str | None = Field(default=None, max_length=50000)
    kind: EducationKind
    condition: str | None = Field(default=None, max_length=150)
    care_type: CareType = "other"
    specialty_id: uuid.UUID | None = None
    external_url: HttpUrl | None = None
    source: str | None = Field(default=None, max_length=2000)
    reviewed_on: date | None = None
    is_published: bool = False


class EducationPatch(BaseModel):
    """به‌روزرسانی بخشی؛ فقط فیلدهای فرستاده‌شده عوض می‌شوند."""

    model_config = ConfigDict(extra="forbid")
    title: str | None = Field(default=None, min_length=1, max_length=300)
    summary: str | None = Field(default=None, max_length=1000)
    body: str | None = Field(default=None, max_length=50000)
    kind: EducationKind | None = None
    condition: str | None = Field(default=None, max_length=150)
    care_type: CareType | None = None
    specialty_id: uuid.UUID | None = None
    external_url: HttpUrl | None = None
    source: str | None = Field(default=None, max_length=2000)
    reviewed_on: date | None = None
    is_published: bool | None = None


class EducationListOut(BaseModel):
    id: uuid.UUID
    title: str
    summary: str | None
    kind: EducationKind
    condition: str | None
    care_type: str
    specialty_id: uuid.UUID | None
    specialty_name: str | None
    source: str | None
    reviewed_on: date | None
    has_file: bool
    file_version: str | None = None
    rating_average: float | None = None
    rating_count: int = 0


class EducationDetailOut(EducationListOut):
    body: str | None
    external_url: str | None
    content_type: str | None  # نوع فایل بارگذاری‌شده (برای پخش در اپ)


class EducationManageOut(EducationDetailOut):
    is_published: bool
    file_name: str | None
    assigned_count: int = 0
    seen_count: int = 0
    created_at: datetime
    updated_at: datetime


class EducationFiltersOut(BaseModel):
    conditions: list[str]
    care_types: list[str]
    kinds: list[str]
    specialties: list["EducationSpecialtyOut"]


class EducationSpecialtyOut(BaseModel):
    id: uuid.UUID
    name: str


EducationFiltersOut.model_rebuild()


# ---------------------------------------------------------------- ارسال به مراجع، امتیاز، تیکت


class AssignIn(BaseModel):
    patient_id: uuid.UUID
    item_ids: list[uuid.UUID] = Field(min_length=1, max_length=50)
    note: str | None = Field(default=None, max_length=300)


class AssignResultOut(BaseModel):
    added: int
    already: int


class EducationPatientRow(BaseModel):
    id: uuid.UUID
    mobile: str
    full_name: str | None
    assigned_count: int
    unseen_count: int


class AssignmentRow(BaseModel):
    id: uuid.UUID
    patient_id: uuid.UUID
    patient_mobile: str
    patient_name: str | None
    item_id: uuid.UUID
    item_title: str
    note: str | None
    assigned_at: datetime
    first_seen_at: datetime | None
    last_seen_at: datetime | None
    view_count: int


class MyEducationOut(EducationListOut):
    """آموزش فرستاده‌شده برای خود مراجع."""

    assigned_at: datetime
    assignment_note: str | None
    seen: bool


class RatingIn(BaseModel):
    stars: int = Field(ge=1, le=5)


class MyRatingOut(BaseModel):
    stars: int | None
    rating_average: float | None
    rating_count: int


TicketStatus = Literal["open", "closed"]


class TicketCreateIn(BaseModel):
    subject: str = Field(min_length=1, max_length=200)
    body: str = Field(min_length=1, max_length=4000)


class TicketMessageIn(BaseModel):
    body: str = Field(min_length=1, max_length=4000)


class TicketAttachmentOut(BaseModel):
    original_name: str
    content_type: str
    size: int
    url: str  # پیوند امضاشده و موقت


class TicketMessageOut(BaseModel):
    id: uuid.UUID
    from_staff: bool
    staff_name: str | None = None
    body: str | None
    attachment: TicketAttachmentOut | None
    created_at: datetime


class TicketOut(BaseModel):
    id: uuid.UUID
    subject: str
    status: TicketStatus
    unread: bool  # برای مراجع پاسخ ندیده، برای کارشناس پیام ندیده
    created_at: datetime
    updated_at: datetime
    last_message: str | None = None
    patient_mobile: str | None = None  # فقط در خروجی کارشناس
    patient_name: str | None = None


class TicketDetailOut(TicketOut):
    messages: list[TicketMessageOut]
