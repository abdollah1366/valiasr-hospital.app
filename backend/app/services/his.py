"""منبع جواب آزمایش و تصویربرداری.

حالت sample (پیش‌فرض) داده نمونه برمی‌گرداند. حالت http جواب را از سرور بیمارستان می‌خواند:
GET {RAZI_HIS_BASE_URL}/patients/results?mobile=<شماره موبایل مراجع، مثل +989121234567>
با هدر Authorization: Bearer <RAZI_HIS_API_KEY> و پاسخ JSON آرایه‌ای از ResultOut (app/schemas/results.py).
هیچ جوابی در پایگاه داده ما ذخیره نمی‌شود.
"""

import json
import urllib.error
import urllib.parse
import urllib.request
from datetime import UTC, datetime, timedelta

from pydantic import TypeAdapter, ValidationError

from app.core.config import get_settings
from app.core.deps import api_error
from app.schemas.results import ResultItemOut, ResultOut


def _item(name: str, value: str, unit: str, ref: str, flag: str = "normal") -> ResultItemOut:
    return ResultItemOut(name=name, value=value, unit=unit, reference=ref, flag=flag)


def sample_results(now: datetime | None = None) -> list[ResultOut]:
    now = now or datetime.now(UTC)
    day = timedelta(days=1)
    return [
        ResultOut(
            id="lab-cbc",
            kind="lab",
            title="شمارش کامل سلول‌های خون (CBC)",
            performed_at=now - 3 * day,
            department="آزمایشگاه",
            doctor_name="دکتر نمونه یکم",
            summary="نمونه آزمایشی؛ یک مورد کمی خارج از محدوده",
            items=[
                _item("WBC", "6.8", "هزار/میکرولیتر", "4.0 - 11.0"),
                _item("RBC", "4.9", "میلیون/میکرولیتر", "4.2 - 5.9"),
                _item("Hemoglobin", "11.9", "g/dL", "12.0 - 16.0", "low"),
                _item("Platelet", "250", "هزار/میکرولیتر", "150 - 450"),
            ],
        ),
        ResultOut(
            id="lab-biochem",
            kind="lab",
            title="بیوشیمی خون",
            performed_at=now - 10 * day,
            department="آزمایشگاه",
            summary="نمونه آزمایشی",
            items=[
                _item("FBS", "92", "mg/dL", "70 - 100"),
                _item("Creatinine", "0.9", "mg/dL", "0.6 - 1.2"),
                _item("Cholesterol", "214", "mg/dL", "< 200", "high"),
            ],
        ),
        ResultOut(
            id="lab-urine",
            kind="lab",
            title="آزمایش ادرار",
            performed_at=now - 1 * day,
            status="pending",
            department="آزمایشگاه",
        ),
        ResultOut(
            id="img-chest",
            kind="imaging",
            title="رادیوگرافی قفسه سینه",
            performed_at=now - 14 * day,
            department="تصویربرداری",
            doctor_name="دکتر نمونه یکم",
            summary="نمونه آزمایشی؛ یافته غیرطبیعی ندارد",
            report="ریه‌ها هوادار و بدون کدورت کانونی است. سایه قلب و مدیاستن در حد طبیعی است.",
        ),
        ResultOut(
            id="img-sono",
            kind="imaging",
            title="سونوگرافی شکم و لگن",
            performed_at=now - 30 * day,
            department="تصویربرداری",
            summary="نمونه آزمایشی",
            report="کبد، کیسه صفرا، طحال و کلیه‌ها از نظر اندازه و اکوی پارانشیم طبیعی‌اند.",
        ),
    ]


def _fetch_http(mobile: str) -> list[ResultOut]:
    s = get_settings()
    url = f"{s.his_base_url.rstrip('/')}/patients/results?{urllib.parse.urlencode({'mobile': mobile})}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    if s.his_api_key:
        req.add_header("Authorization", f"Bearer {s.his_api_key}")
    try:
        with urllib.request.urlopen(req, timeout=s.his_timeout_seconds) as r:  # noqa: S310
            data = json.load(r)
        return TypeAdapter(list[ResultOut]).validate_python(data)
    except (urllib.error.URLError, TimeoutError, ValueError, ValidationError):
        raise api_error(502, "his_unavailable", "اتصال به سامانه بیمارستان برقرار نشد") from None


def patient_results(mobile: str, kind: str | None = None) -> list[ResultOut]:
    s = get_settings()
    rows = _fetch_http(mobile) if s.his_provider == "http" else sample_results()
    if kind:
        rows = [r for r in rows if r.kind == kind]
    return sorted(rows, key=lambda r: r.performed_at, reverse=True)
