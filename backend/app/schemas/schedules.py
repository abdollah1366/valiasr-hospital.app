import uuid
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator, model_validator

from app.services.schedule_logic import PERIODS, SHIFT_GROUPS

Kind = Literal["work", "off", "leave"]
Status = Literal["draft", "submitted", "published"]
SwapStatus = Literal["pending_partner", "pending_manager", "approved", "rejected", "cancelled"]


# ---------------------------------------------------------------- کدهای شیفت


class SegmentIO(BaseModel):
    start: str = Field(pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    start_day: int = Field(default=0, ge=0, le=3)
    end: str = Field(pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    end_day: int = Field(default=0, ge=0, le=3)
    oncall: bool = False

    @model_validator(mode="after")
    def _order(self):
        if (self.end_day, self.end) <= (self.start_day, self.start):
            raise ValueError("پایان بازه باید بعد از شروع آن باشد")
        return self


class ShiftTypeBase(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    label: str = Field(min_length=1, max_length=30)
    kind: Kind = "work"
    group: str = "general"
    segments: list[SegmentIO] = []
    hours: float | None = Field(default=None, ge=0, le=96)
    productivity: bool = False
    overtime: bool | None = None
    weekday: int | None = Field(default=None, ge=0, le=6)
    is_active: bool = True
    replaced_by: int | None = None
    review_note: str | None = Field(default=None, max_length=1000)

    @field_validator("group")
    @classmethod
    def _group(cls, v: str) -> str:
        if v not in SHIFT_GROUPS:
            raise ValueError("گروه کد نامعتبر است")
        return v

    @model_validator(mode="after")
    def _work_has_hours(self):
        if self.kind == "work" and self.is_active and not self.segments:
            raise ValueError("کد کاری باید دست‌کم یک بازه ساعت داشته باشد")
        return self


class ShiftTypeIn(ShiftTypeBase):
    code: int = Field(ge=1, le=9999)


class ShiftTypeUpdateIn(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=300)
    label: str | None = Field(default=None, min_length=1, max_length=30)
    kind: Kind | None = None
    group: str | None = None
    segments: list[SegmentIO] | None = None
    hours: float | None = Field(default=None, ge=0, le=96)
    productivity: bool | None = None
    overtime: bool | None = None
    weekday: int | None = Field(default=None, ge=0, le=6)
    is_active: bool | None = None
    replaced_by: int | None = None
    review_note: str | None = Field(default=None, max_length=1000)


class ShiftTypeOut(BaseModel):
    code: int
    title: str
    label: str
    kind: str
    group: str
    group_name: str
    segments: list[SegmentIO]
    hours: float | None
    work_hours: float
    oncall_hours: float
    span_days: int
    productivity: bool
    overtime: bool | None
    weekday: int | None
    is_active: bool
    replaced_by: int | None
    review_note: str | None


# ---------------------------------------------------------------- تنظیمات و تعطیلات


class UnitSettingsIn(BaseModel):
    shift_groups: list[str] = Field(min_length=1)
    min_staff: dict[str, int] = {}
    max_consecutive: int = Field(default=6, ge=1, le=31)
    min_rest_hours: int = Field(default=8, ge=0, le=24)
    required_monthly_hours: int | None = Field(default=None, ge=0, le=744)
    max_monthly_hours: int | None = Field(default=None, ge=0, le=744)

    @field_validator("shift_groups")
    @classmethod
    def _groups(cls, v: list[str]) -> list[str]:
        if any(g not in SHIFT_GROUPS for g in v):
            raise ValueError("گروه کد نامعتبر است")
        return list(dict.fromkeys(v))

    @field_validator("min_staff")
    @classmethod
    def _min_staff(cls, v: dict[str, int]) -> dict[str, int]:
        if any(p not in PERIODS or not 0 <= n <= 200 for p, n in v.items()):
            raise ValueError("حداقل نیرو فقط برای morning، evening و night و میان ۰ تا ۲۰۰")
        return v


class UnitSettingsOut(UnitSettingsIn):
    unit_id: uuid.UUID
    unit_name: str
    is_default: bool


class HolidayIn(BaseModel):
    day: date
    title: str = Field(min_length=1, max_length=200)


class HolidayOut(HolidayIn):
    jalali: str


# ---------------------------------------------------------------- برنامه


class ScheduleCreateIn(BaseModel):
    unit_id: uuid.UUID
    jyear: int = Field(ge=1400, le=1500)
    jmonth: int = Field(ge=1, le=12)
    copy_previous: bool = False


class ScheduleSummaryOut(BaseModel):
    id: uuid.UUID
    unit_id: uuid.UUID
    unit_name: str
    jyear: int
    jmonth: int
    title: str
    status: str
    member_count: int
    return_note: str | None
    submitted_at: datetime | None
    published_at: datetime | None
    updated_at: datetime


class UnitScheduleRow(BaseModel):
    unit_id: uuid.UUID
    unit_name: str
    unit_type: str
    schedule: ScheduleSummaryOut | None
    can_create: bool


class DayOut(BaseModel):
    date: date
    jalali: str
    day: int
    weekday: int
    weekday_name: str
    is_holiday: bool
    holiday_title: str | None


class CellOut(BaseModel):
    day: date
    code: int | None  # برای همکاران، نوع مرخصی پنهان است
    label: str
    kind: str
    span_days: int


class MemberOut(BaseModel):
    user_id: uuid.UUID
    full_name: str
    personnel_code: str
    job_title: str | None
    is_guest: bool
    cells: list[CellOut]
    work_hours: float
    oncall_hours: float
    overtime_hours: float | None


class CoverageOut(BaseModel):
    date: date
    morning: int
    evening: int
    night: int


class WarningOut(BaseModel):
    code: str
    message: str
    user_id: uuid.UUID | None = None
    day: date | None = None
    period: str | None = None


class ScheduleDetailOut(ScheduleSummaryOut):
    days: list[DayOut]
    members: list[MemberOut]
    coverage: list[CoverageOut]
    warnings: list[WarningOut]
    settings: UnitSettingsIn
    actions: list[str]


class CellIn(BaseModel):
    user_id: uuid.UUID
    day: date
    code: int | None  # خالی یعنی پاک کردن خانه


class EntriesIn(BaseModel):
    cells: list[CellIn] = Field(min_length=1, max_length=3000)
    reason: str | None = Field(default=None, max_length=300)  # پس از انتشار لازم است


class MemberIn(BaseModel):
    user_id: uuid.UUID


class ReturnIn(BaseModel):
    note: str = Field(min_length=1, max_length=500)


class PersonOut(BaseModel):
    id: uuid.UUID
    full_name: str
    personnel_code: str
    job_title: str | None


class ChangeOut(BaseModel):
    id: uuid.UUID
    at: datetime
    person: PersonOut
    day: date
    jalali: str
    old_code: int | None
    new_code: int | None
    old_label: str | None
    new_label: str | None
    reason: str
    changed_by: PersonOut | None


# ---------------------------------------------------------------- برنامه من و کشیک


class MyShiftOut(BaseModel):
    day: date
    jalali: str
    code: int
    label: str
    title: str
    kind: str
    unit_id: uuid.UUID
    unit_name: str
    schedule_id: uuid.UUID
    starts_at: datetime | None
    ends_at: datetime | None
    work_hours: float


class MyScheduleOut(BaseModel):
    start: date
    end: date
    items: list[MyShiftOut]
    next_shift: MyShiftOut | None


class OnDutyPerson(BaseModel):
    person: PersonOut
    mobile: str | None
    code: int
    label: str
    starts_at: datetime
    ends_at: datetime
    periods: list[str]  # نوبت‌های همان روز که حاضر است
    on_duty_now: bool
    oncall_now: bool


class OnDutyUnit(BaseModel):
    unit_id: uuid.UUID
    unit_name: str
    published: bool
    people: list[OnDutyPerson]


class OnDutyOut(BaseModel):
    day: date
    jalali: str
    now: datetime | None
    units: list[OnDutyUnit]


# ---------------------------------------------------------------- جابجایی


class SwapIn(BaseModel):
    schedule_id: uuid.UUID
    my_day: date
    partner_id: uuid.UUID
    partner_day: date
    note: str | None = Field(default=None, max_length=300)


class SwapAnswerIn(BaseModel):
    note: str | None = Field(default=None, max_length=300)


class SwapCellOut(BaseModel):
    day: date
    jalali: str
    code: int | None
    label: str | None


class SwapOut(BaseModel):
    id: uuid.UUID
    schedule_id: uuid.UUID
    unit_name: str
    month_title: str
    requester: PersonOut
    partner: PersonOut
    requester_day: SwapCellOut
    partner_day: SwapCellOut
    note: str | None
    status: SwapStatus
    created_at: datetime
    decided_by: PersonOut | None
    decided_at: datetime | None
    decision_note: str | None
    actions: list[str]
