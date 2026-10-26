import uuid
from datetime import date, datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

IncidentKind = Literal["error", "adverse_event", "near_miss", "equipment"]
IncidentStatus = Literal[
    "submitted", "under_review", "assigned", "in_progress", "action_taken", "closed", "rejected"
]
Shift = Literal["morning", "evening", "night", "unknown"]


class ORM(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# ---------------------------------------------------------------- auth


class LoginIn(BaseModel):
    personnel_code: str = Field(min_length=1, max_length=50)
    password: str = Field(min_length=1, max_length=200)
    device: str | None = Field(default=None, max_length=200)


class RefreshIn(BaseModel):
    refresh_token: str = Field(min_length=10, max_length=200)


class TokenOut(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    must_change_password: bool


class ChangePasswordIn(BaseModel):
    current_password: str = Field(min_length=1, max_length=200)
    new_password: str = Field(min_length=8, max_length=200)


class MeOut(BaseModel):
    id: uuid.UUID
    personnel_code: str
    full_name: str
    unit_id: uuid.UUID | None
    unit_name: str | None
    job_title: str | None = None
    roles: list[str]
    permissions: list[str]
    must_change_password: bool
    # حساب به پرونده یک پزشک وصل است (فهرست بیماران من در اپ و پنل)
    doctor_id: uuid.UUID | None = None


# ---------------------------------------------------------------- users / units


class UnitIn(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    type: Literal["ward", "clinic", "support", "admin"]
    parent_id: uuid.UUID | None = None
    is_public: bool = False
    location: str | None = None
    phone: str | None = None
    hours: str | None = None
    description: str | None = None


class UnitOut(ORM):
    id: uuid.UUID
    name: str
    type: str
    parent_id: uuid.UUID | None
    is_public: bool
    location: str | None
    phone: str | None
    hours: str | None
    description: str | None
    has_image: bool = False


class RoleAssignment(BaseModel):
    role: str
    unit_id: uuid.UUID | None = None


class UserCreateIn(BaseModel):
    personnel_code: str = Field(min_length=1, max_length=50)
    full_name: str = Field(min_length=1, max_length=200)
    mobile: str | None = None
    unit_id: uuid.UUID | None = None
    job_title: str | None = Field(default=None, max_length=100)
    temporary_password: str = Field(min_length=8, max_length=200)
    roles: list[RoleAssignment] = []


class UserUpdateIn(BaseModel):
    full_name: str | None = None
    mobile: str | None = None
    unit_id: uuid.UUID | None = None
    job_title: str | None = Field(default=None, max_length=100)
    is_active: bool | None = None
    roles: list[RoleAssignment] | None = None


class ResetPasswordIn(BaseModel):
    temporary_password: str = Field(min_length=8, max_length=200)


class UserOut(BaseModel):
    id: uuid.UUID
    personnel_code: str
    full_name: str
    mobile: str | None
    unit_id: uuid.UUID | None
    job_title: str | None = None
    is_active: bool
    must_change_password: bool
    roles: list[RoleAssignment]


class RoleOut(BaseModel):
    code: str
    name: str
    permissions: list[str]


# ---------------------------------------------------------------- incidents


class IncidentCreateIn(BaseModel):
    client_uuid: uuid.UUID
    kind: IncidentKind
    unit_id: uuid.UUID
    occurred_on: date
    shift: Shift = "unknown"
    is_anonymous: bool = False
    # فقط برای گزارش ناشناس: رمز پیگیری را خود اپ می‌سازد تا ارسال مجدد (پس از قطعی شبکه) امن باشد.
    follow_up_secret: str | None = Field(default=None, min_length=8, max_length=100)
    form_version: int | None = None
    answers: dict[str, Any]


class IncidentCreatedOut(BaseModel):
    id: uuid.UUID | None
    tracking_code: str
    status: str
    duplicate: bool = False


class StatusChangeOut(ORM):
    from_status: str | None
    to_status: str
    note: str | None
    at: datetime


class ActionOut(ORM):
    id: uuid.UUID
    description: str
    owner_name: str | None
    due_date: date | None
    done_at: datetime | None
    result: str | None
    created_at: datetime


class IncidentSummaryOut(BaseModel):
    id: uuid.UUID
    tracking_code: str
    kind: str
    unit_id: uuid.UUID
    unit_name: str | None
    occurred_on: date
    status: str
    is_anonymous: bool
    created_at: datetime


class IncidentDetailOut(IncidentSummaryOut):
    shift: str | None
    answers: dict[str, Any]
    sensitive_answers: dict[str, Any] | None = None
    reporter_name: str | None = None
    assigned_unit_id: uuid.UUID | None
    assigned_unit_name: str | None = None
    assigned_user_id: uuid.UUID | None
    history: list[StatusChangeOut]
    actions: list[ActionOut]
    # فقط برای کاربرِ مجاز به مدیریت: اپ بر اساس این‌ها دکمه‌ها را نشان می‌دهد و خودش قواعد را تکرار نمی‌کند.
    can_manage: bool = False
    next_statuses: list[str] = []


class IncidentPage(BaseModel):
    items: list[IncidentSummaryOut]
    total: int
    page: int
    size: int


class StatusChangeIn(BaseModel):
    status: IncidentStatus
    note: str | None = Field(default=None, max_length=4000)


class AssignIn(BaseModel):
    unit_id: uuid.UUID | None = None
    user_id: uuid.UUID | None = None
    note: str | None = Field(default=None, max_length=4000)


class ActionIn(BaseModel):
    description: str = Field(min_length=1, max_length=4000)
    owner_name: str | None = Field(default=None, max_length=200)
    due_date: date | None = None


class ActionUpdateIn(BaseModel):
    done: bool | None = None
    result: str | None = Field(default=None, max_length=4000)


class TrackIn(BaseModel):
    tracking_code: str = Field(min_length=4, max_length=20)
    follow_up_secret: str = Field(min_length=8, max_length=100)


class TrackOut(BaseModel):
    tracking_code: str
    status: str
    created_at: datetime
    history: list[StatusChangeOut]


# ---------------------------------------------------------------- announcements


class AnnouncementIn(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    body: str = Field(min_length=1, max_length=20000)
    category: Literal["news", "circular", "guideline", "notice"] = "news"
    audience: Literal["public", "staff"] = "staff"
    target_role_codes: list[str] = []
    target_unit_ids: list[uuid.UUID] = []
    is_important: bool = False
    requires_ack: bool = False
    published_at: datetime | None = None
    expires_at: datetime | None = None


class AnnouncementOut(BaseModel):
    id: uuid.UUID
    title: str
    body: str
    category: str
    audience: str
    is_important: bool
    requires_ack: bool
    published_at: datetime
    expires_at: datetime | None
    read: bool | None = None
    acknowledged: bool | None = None


class ReadStatsOut(BaseModel):
    targeted: int
    read: int
    acknowledged: int
    not_acknowledged: list[str]


# ---------------------------------------------------------------- public


class FaqIn(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    answer: str = Field(min_length=1, max_length=8000)
    category: str | None = None
    sort_order: int = 0


class FaqOut(ORM):
    id: uuid.UUID
    question: str
    answer: str
    category: str | None
    sort_order: int


class FeedbackIn(BaseModel):
    kind: Literal["suggestion", "criticism", "complaint", "praise"]
    body: str = Field(min_length=5, max_length=4000)
    name: str | None = Field(default=None, max_length=200)
    mobile: str | None = Field(default=None, pattern=r"^09\d{9}$")
    unit_id: uuid.UUID | None = None


class FeedbackCreatedOut(BaseModel):
    tracking_code: str


class FeedbackPublicOut(BaseModel):
    tracking_code: str
    kind: str
    status: str
    response: str | None
    created_at: datetime


class FeedbackOut(ORM):
    id: uuid.UUID
    tracking_code: str
    kind: str
    name: str | None
    mobile: str | None
    unit_id: uuid.UUID | None
    body: str
    status: str
    response: str | None
    created_at: datetime


class FeedbackRespondIn(BaseModel):
    status: Literal["reviewing", "answered"]
    response: str | None = Field(default=None, max_length=4000)


# ---------------------------------------------------------------- مراجعان

PatientLanguage = Literal["fa", "az", "en", "ar", "tr"]


class OtpRequestIn(BaseModel):
    mobile: str = Field(min_length=8, max_length=30)


class OtpRequestOut(BaseModel):
    mobile: str  # شماره یکسان‌شده که کد به آن فرستاده شد
    expires_in: int
    resend_after: int
    suggested_language: PatientLanguage
    debug_code: str | None = None  # فقط در توسعه و سرور آزمایشی


class OtpVerifyIn(BaseModel):
    mobile: str = Field(min_length=8, max_length=30)
    code: str = Field(min_length=4, max_length=10)
    language: PatientLanguage | None = None
    device: str | None = Field(default=None, max_length=200)


class PatientOut(ORM):
    id: uuid.UUID
    mobile: str
    language: str
    full_name: str | None


class PatientTokenOut(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    # برای سازگاری با پاسخ تمدید توکن کارکنان در اپ
    must_change_password: bool = False
    patient: PatientOut
    is_new: bool = False


class PatientUpdateIn(BaseModel):
    language: PatientLanguage | None = None
    full_name: str | None = Field(default=None, max_length=200)


# ---------------------------------------------------------------- ترجمه محتوای عمومی

TranslationEntity = Literal["unit", "faq", "announcement", "education", "specialty", "doctor", "reward"]
TranslationLanguage = Literal["az", "en", "ar", "tr"]


class TranslationKey(BaseModel):
    entity_type: TranslationEntity
    entity_id: uuid.UUID
    language: TranslationLanguage
    field: str = Field(max_length=30)


class TranslationIn(TranslationKey):
    text: str = Field(min_length=1, max_length=8000)


class TranslationFieldOut(BaseModel):
    field: str
    source: str | None
    text: str | None
    status: Literal["missing", "draft", "approved", "outdated"]


class TranslationItemOut(BaseModel):
    entity_type: str
    entity_id: uuid.UUID
    label: str
    fields: list[TranslationFieldOut]
