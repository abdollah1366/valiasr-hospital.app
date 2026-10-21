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


class EducationDetailOut(EducationListOut):
    body: str | None
    external_url: str | None
    content_type: str | None  # نوع فایل بارگذاری‌شده (برای پخش در اپ)


class EducationManageOut(EducationDetailOut):
    is_published: bool
    file_name: str | None
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
