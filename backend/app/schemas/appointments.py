import uuid
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

from app.schemas.reviews import MyReviewOut

# کلید آیکون تصویری هر گروه تخصصی؛ اپ و پنل برای هر کلید تصویر خودشان را دارند.
SPECIALTY_ICONS = (
    "stethoscope",
    "heart",
    "bone",
    "brain",
    "psychology",
    "baby",
    "female",
    "child",
    "eye",
    "ear",
    "tooth",
    "lungs",
    "stomach",
    "kidney",
    "skin",
    "scalpel",
    "blood",
    "ribbon",
    "radiology",
    "lab",
)
SpecialtyIcon = Literal[SPECIALTY_ICONS]  # type: ignore[valid-type]
AppointmentStatus = Literal["booked", "attended", "no_show", "cancelled"]

_HHMM = r"^([01]\d|2[0-3]):[0-5]\d$"


class SpecialtyIn(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=5000)
    icon: SpecialtyIcon = "stethoscope"
    sort_order: int = 0
    is_active: bool = True


class SpecialtyOut(BaseModel):
    id: uuid.UUID
    name: str
    description: str | None
    icon: str
    sort_order: int
    is_active: bool
    doctor_count: int = 0


class DoctorIn(BaseModel):
    full_name: str = Field(min_length=1, max_length=200)
    specialty_id: uuid.UUID
    title: str | None = Field(default=None, max_length=200)
    bio: str | None = Field(default=None, max_length=5000)
    medical_code: str | None = Field(default=None, max_length=30)
    clinic_unit_id: uuid.UUID | None = None
    # کد پرسنلی حساب کارکنان خود پزشک (برای دیدن فهرست بیماران در اپ و پنل)
    user_personnel_code: str | None = Field(default=None, max_length=50)
    visit_minutes: int = Field(default=15, ge=5, le=240)
    is_featured: bool = False
    booking_enabled: bool = True
    is_active: bool = True
    sort_order: int = 0
    secretary_codes: list[str] = Field(default_factory=list, max_length=20)


class StaffBrief(BaseModel):
    id: uuid.UUID
    personnel_code: str
    full_name: str


class DoctorOut(BaseModel):
    """نمای مدیر پزشکان و منشی."""

    id: uuid.UUID
    full_name: str
    specialty_id: uuid.UUID
    specialty_name: str
    title: str | None
    bio: str | None
    medical_code: str | None
    clinic_unit_id: uuid.UUID | None
    user: StaffBrief | None
    visit_minutes: int
    is_featured: bool
    booking_enabled: bool
    is_active: bool
    sort_order: int
    has_photo: bool
    secretaries: list[StaffBrief]


class PublicDoctorOut(BaseModel):
    id: uuid.UUID
    full_name: str
    title: str | None
    bio: str | None
    specialty_id: uuid.UUID
    specialty_name: str
    specialty_icon: str
    clinic_name: str | None
    clinic_location: str | None
    has_photo: bool
    is_featured: bool
    booking_enabled: bool
    next_free_at: datetime | None
    rating_average: float | None = None
    rating_count: int = 0


class PublicSlotOut(BaseModel):
    id: uuid.UUID
    starts_at: datetime
    minutes: int
    free: int


class SessionIn(BaseModel):
    """یک نوبت کاری پزشک (مثلاً شنبه ۹ تا ۱۲) که به وقت‌های پشت‌سرهم شکسته می‌شود."""

    date: date
    start: str = Field(pattern=_HHMM)
    end: str = Field(pattern=_HHMM)
    minutes: int | None = Field(default=None, ge=5, le=240)
    capacity: int = Field(default=1, ge=1, le=50)
    # تکرار در همان روز هفته‌های بعد
    repeat_weeks: int = Field(default=1, ge=1, le=26)

    @field_validator("end")
    @classmethod
    def _after_start(cls, v: str, info) -> str:
        start = info.data.get("start")
        if start and v <= start:
            raise ValueError("ساعت پایان باید بعد از ساعت شروع باشد")
        return v


class SessionOut(BaseModel):
    created: int
    skipped: int


class SlotUpdateIn(BaseModel):
    is_blocked: bool | None = None
    capacity: int | None = Field(default=None, ge=1, le=50)


class ManageAppointmentOut(BaseModel):
    id: uuid.UUID
    tracking_code: str
    status: str
    patient_name: str
    patient_mobile: str
    note: str | None
    source: str
    slot_id: uuid.UUID
    starts_at: datetime
    minutes: int
    doctor_id: uuid.UUID
    doctor_name: str
    created_at: datetime


class ManageSlotOut(BaseModel):
    id: uuid.UUID
    starts_at: datetime
    minutes: int
    capacity: int
    is_blocked: bool
    appointments: list[ManageAppointmentOut]


class StaffBookIn(BaseModel):
    slot_id: uuid.UUID
    patient_name: str = Field(min_length=2, max_length=200)
    patient_mobile: str = Field(min_length=8, max_length=20)
    note: str | None = Field(default=None, max_length=500)


class PatientBookIn(BaseModel):
    slot_id: uuid.UUID
    patient_name: str | None = Field(default=None, min_length=2, max_length=200)
    note: str | None = Field(default=None, max_length=500)


class MoveIn(BaseModel):
    slot_id: uuid.UUID


class StatusIn(BaseModel):
    status: Literal["booked", "attended", "no_show"]


class CancelDayIn(BaseModel):
    date: date


class CancelDayOut(BaseModel):
    cancelled: int
    blocked_slots: int


class PatientAppointmentOut(BaseModel):
    id: uuid.UUID
    tracking_code: str
    status: str
    starts_at: datetime
    minutes: int
    doctor_id: uuid.UUID
    doctor_name: str
    doctor_title: str | None
    specialty_name: str
    clinic_name: str | None
    clinic_location: str | None
    note: str | None
    can_cancel: bool
    cancelled_by: str | None
    # بعد از ویزیت: می‌تواند ستاره و نظر بدهد؛ پس از ثبت، نظر خودش اینجا می‌آید
    can_review: bool = False
    my_review: MyReviewOut | None = None
