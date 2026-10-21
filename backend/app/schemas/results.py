from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class ResultItemOut(BaseModel):
    """یک ردیف آزمایش (مثلاً هموگلوبین)؛ برای تصویربرداری خالی است."""

    name: str
    value: str
    unit: str | None = None
    reference: str | None = None
    flag: Literal["normal", "high", "low"] | None = None


class ResultOut(BaseModel):
    """جواب آزمایش یا تصویربرداری. همین قالب قرارداد اتصال به سرور بیمارستان است."""

    id: str = Field(max_length=100)
    kind: Literal["lab", "imaging"]
    title: str
    performed_at: datetime
    status: Literal["ready", "pending"] = "ready"
    department: str | None = None
    doctor_name: str | None = None
    summary: str | None = None
    report: str | None = None
    items: list[ResultItemOut] = []
    # فقط تصویربرداری: نشانی فایل تصویر یا گزارش PDF روی سرور بیمارستان (اختیاری)
    file_url: str | None = None
