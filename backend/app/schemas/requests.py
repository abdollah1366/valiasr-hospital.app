import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator

Category = Literal["it", "equipment", "facilities", "other"]
Priority = Literal["normal", "urgent", "critical"]
Status = Literal["submitted", "accepted", "in_progress", "on_hold", "done", "closed", "rejected"]


class TypeField(BaseModel):
    id: str = Field(min_length=1, max_length=40, pattern=r"^[a-z][a-z0-9_]*$")
    label: str = Field(min_length=1, max_length=200)
    kind: Literal["text", "number", "select"] = "text"
    options: list[str] = []
    required: bool = False

    @model_validator(mode="after")
    def _check(self):
        if self.kind == "select" and len(self.options) < 2:
            raise ValueError("فیلد انتخابی دست‌کم دو گزینه لازم دارد")
        return self


class RequestTypeIn(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    category: Category
    description: str | None = None
    handler_unit_id: uuid.UUID
    fields: list[TypeField] = []
    is_active: bool = True
    sort_order: int = 0

    @model_validator(mode="after")
    def _unique(self):
        ids = [f.id for f in self.fields]
        if len(ids) != len(set(ids)):
            raise ValueError("شناسه فیلدها باید یکتا باشد")
        return self


class RequestTypeUpdateIn(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    category: Category | None = None
    description: str | None = None
    handler_unit_id: uuid.UUID | None = None
    fields: list[TypeField] | None = None
    is_active: bool | None = None
    sort_order: int | None = None


class RequestTypeOut(BaseModel):
    id: uuid.UUID
    name: str
    category: Category
    description: str | None
    handler_unit_id: uuid.UUID
    handler_unit_name: str | None
    fields: list[dict]
    is_active: bool
    sort_order: int
    request_count: int | None = None


class RequestCreateIn(BaseModel):
    # شناسه‌ای که اپ می‌سازد تا ارسال دوباره از صف آفلاین درخواست تکراری نسازد
    client_uuid: uuid.UUID
    type_id: uuid.UUID
    description: str = Field(min_length=3, max_length=4000)
    location: str | None = Field(default=None, max_length=300)
    priority: Priority = "normal"
    answers: dict = {}


class RequestCreatedOut(BaseModel):
    id: uuid.UUID
    tracking_code: str
    status: Status
    duplicate: bool = False


class PersonOut(BaseModel):
    id: uuid.UUID
    full_name: str
    personnel_code: str
    job_title: str | None = None


class RequestSummaryOut(BaseModel):
    id: uuid.UUID
    tracking_code: str
    type_id: uuid.UUID
    type_name: str
    category: Category
    description: str
    priority: Priority
    status: Status
    requester: PersonOut
    requester_unit_name: str | None
    handler_unit_id: uuid.UUID
    handler_unit_name: str | None
    assigned_to: PersonOut | None
    created_at: datetime
    updated_at: datetime
    satisfaction: int | None


class RequestPage(BaseModel):
    items: list[RequestSummaryOut]
    total: int
    page: int
    size: int


class AttachmentOut(BaseModel):
    id: uuid.UUID
    original_name: str
    content_type: str
    size: int
    url: str


class EventOut(BaseModel):
    id: uuid.UUID
    at: datetime
    kind: str
    actor: PersonOut | None
    from_status: str | None
    to_status: str | None
    note: str | None
    attachment: AttachmentOut | None


class RequestDetailOut(RequestSummaryOut):
    location: str | None
    answers: list[dict]  # [{"label", "value"}] به ترتیب فیلدهای نوع درخواست
    resolution: str | None
    accepted_at: datetime | None
    done_at: datetime | None
    closed_at: datetime | None
    satisfaction_comment: str | None
    reopen_until: datetime | None
    events: list[EventOut]
    actions: list[str]


class StatusIn(BaseModel):
    status: Status
    note: str | None = Field(default=None, max_length=2000)
    resolution: str | None = Field(default=None, max_length=4000)


class AssignIn(BaseModel):
    user_id: uuid.UUID | None


class TransferIn(BaseModel):
    unit_id: uuid.UUID
    note: str = Field(min_length=1, max_length=2000)


class CommentIn(BaseModel):
    text: str = Field(min_length=1, max_length=2000)


class RateIn(BaseModel):
    score: int = Field(ge=1, le=5)
    comment: str | None = Field(default=None, max_length=2000)


class ReopenIn(BaseModel):
    note: str = Field(min_length=1, max_length=2000)


class CountRow(BaseModel):
    key: str
    label: str
    count: int


class RequestStatsOut(BaseModel):
    total: int
    open: int
    critical_open: int
    done: int
    avg_response_hours: float | None
    avg_resolution_hours: float | None
    avg_satisfaction: float | None
    rated: int
    by_status: list[CountRow]
    by_type: list[CountRow]
    by_handler_unit: list[CountRow]
    by_requester_unit: list[CountRow]
    daily: list[CountRow]  # تعداد ثبت در هر روز (key = YYYY-MM-DD)
