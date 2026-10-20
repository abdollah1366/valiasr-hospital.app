"""اعتبارسنجی پاسخ‌های فرم پویا.

اپ اندروید همین قواعد را در FormLogic.kt پیاده می‌کند؛ هر تغییری اینجا باید آنجا هم اعمال شود.

ساختار قالب:
  sections[]: {id, title, show_if?, fields[]}
  field: {id, label, type: text|textarea|date|single|multi, required?, options?, sensitive?, show_if?}
  show_if: {"kind_in": [...]} | {"field": id, "contains": value} | {"field": id, "equals": value}
"""

from dataclasses import dataclass
from datetime import date
from typing import Any

MAX_TEXT = 4000


@dataclass
class FormResult:
    answers: dict[str, Any]
    sensitive: dict[str, Any]
    errors: dict[str, str]


def condition_met(cond: dict | None, kind: str, answers: dict[str, Any]) -> bool:
    if not cond:
        return True
    if "kind_in" in cond:
        return kind in cond["kind_in"]
    value = answers.get(cond.get("field", ""))
    if "contains" in cond:
        if isinstance(value, list):
            return cond["contains"] in value
        return value == cond["contains"]
    if "equals" in cond:
        return value == cond["equals"]
    return False


def visible_fields(schema: dict, kind: str, answers: dict[str, Any]) -> list[dict]:
    result = []
    for section in schema.get("sections", []):
        if not condition_met(section.get("show_if"), kind, answers):
            continue
        for f in section.get("fields", []):
            if condition_met(f.get("show_if"), kind, answers):
                result.append(f)
    return result


def _is_empty(value: Any) -> bool:
    return value is None or value == "" or value == []


def validate_answers(schema: dict, kind: str, raw: dict[str, Any]) -> FormResult:
    known = {f["id"] for s in schema.get("sections", []) for f in s.get("fields", [])}
    errors: dict[str, str] = {}
    for key in raw:
        if key not in known:
            errors[key] = "فیلد ناشناخته"

    # شرط‌ها به پاسخ‌ها وابسته‌اند؛ تا رسیدن به حالت پایدار تکرار می‌کنیم
    # تا پاسخ فیلدهای پنهان روی نمایش فیلدهای دیگر اثر نگذارد.
    current = {k: v for k, v in raw.items() if k in known}
    for _ in range(10):
        fields = visible_fields(schema, kind, current)
        ids = {f["id"] for f in fields}
        pruned = {k: v for k, v in current.items() if k in ids}
        if pruned == current:
            break
        current = pruned

    answers: dict[str, Any] = {}
    sensitive: dict[str, Any] = {}
    for f in visible_fields(schema, kind, current):
        fid, ftype = f["id"], f["type"]
        value = current.get(fid)
        if _is_empty(value):
            if f.get("required"):
                errors[fid] = "پاسخ این سؤال الزامی است"
            continue
        if ftype in ("text", "textarea"):
            if not isinstance(value, str):
                errors[fid] = "مقدار باید متن باشد"
                continue
            value = value.strip()
            if len(value) > MAX_TEXT:
                errors[fid] = "متن بیش از حد طولانی است"
                continue
            if not value:
                if f.get("required"):
                    errors[fid] = "پاسخ این سؤال الزامی است"
                continue
        elif ftype == "date":
            try:
                date.fromisoformat(value)
            except (TypeError, ValueError):
                errors[fid] = "تاریخ نامعتبر است"
                continue
        elif ftype == "single":
            if value not in f.get("options", []):
                errors[fid] = "گزینه نامعتبر است"
                continue
        elif ftype == "multi":
            if not isinstance(value, list) or any(v not in f.get("options", []) for v in value):
                errors[fid] = "گزینه نامعتبر است"
                continue
            value = list(dict.fromkeys(value))
        (sensitive if f.get("sensitive") else answers)[fid] = value
    return FormResult(answers=answers, sensitive=sensitive, errors=errors)
