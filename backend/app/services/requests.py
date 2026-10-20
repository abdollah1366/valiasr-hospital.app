"""منطق درخواست‌های پشتیبانی: گردش وضعیت، اعتبارسنجی فیلدها، کارهای مجاز هر کاربر."""

from datetime import datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.deps import CurrentUser
from app.models import ServiceRequest, ServiceRequestEvent
from app.services.notifications import notify

CATEGORIES = {"it": "فناوری اطلاعات", "equipment": "تجهیزات پزشکی", "facilities": "تأسیسات", "other": "سایر"}
PRIORITIES = {"normal": "عادی", "urgent": "فوری", "critical": "بحرانی"}
STATUSES = {
    "submitted": "ثبت‌شده",
    "accepted": "پذیرفته‌شده",
    "in_progress": "در حال انجام",
    "on_hold": "در انتظار (قطعه یا پاسخ شما)",
    "done": "انجام شد",
    "closed": "بسته",
    "rejected": "رد شده",
}
OPEN_STATUSES = {"submitted", "accepted", "in_progress", "on_hold"}

# تغییر وضعیت‌هایی که کارشناس واحد مسئول می‌تواند انجام دهد
TRANSITIONS: dict[str, set[str]] = {
    "submitted": {"accepted", "rejected"},
    "accepted": {"in_progress", "on_hold", "done", "rejected"},
    "in_progress": {"on_hold", "done"},
    "on_hold": {"in_progress", "done"},
    "done": set(),
    "closed": set(),
    "rejected": set(),
}

# «انجام شد» اگر درخواست‌کننده تأیید یا اعتراض نکند، پس از این مدت خودکار بسته می‌شود؛
# اعتراض (بازگشایی) هم فقط تا همین مدت پس از انجام ممکن است.
AUTO_CLOSE_AFTER = timedelta(days=3)

PRIORITY_RANK = {"critical": 0, "urgent": 1, "normal": 2}


def validate_answers(fields: list[dict], answers: dict) -> tuple[dict, dict[str, str]]:
    """پاسخ فیلدهای نوع درخواست را بررسی می‌کند؛ فیلدهای ناشناخته دور ریخته می‌شوند."""
    clean: dict = {}
    errors: dict[str, str] = {}
    for f in fields:
        fid = f["id"]
        value = answers.get(fid)
        if isinstance(value, str):
            value = value.strip()
        if value in (None, ""):
            if f.get("required"):
                errors[fid] = "این فیلد لازم است"
            continue
        kind = f.get("kind", "text")
        if kind == "number":
            try:
                value = float(value)
            except (TypeError, ValueError):
                errors[fid] = "عدد وارد کنید"
                continue
        elif kind == "select":
            if value not in f.get("options", []):
                errors[fid] = "گزینه نامعتبر است"
                continue
        else:
            value = str(value)[:1000]
        clean[fid] = value
    return clean, errors


def can_view(cu: CurrentUser, r: ServiceRequest) -> bool:
    return (
        r.requester_id == cu.id
        or cu.can_in_unit("request.handle", r.handler_unit_id)
        or cu.can_in_unit("request.read", r.requester_unit_id)
    )


def can_handle(cu: CurrentUser, r: ServiceRequest) -> bool:
    return cu.can_in_unit("request.handle", r.handler_unit_id)


def reopen_open_until(r: ServiceRequest) -> datetime | None:
    return r.done_at + AUTO_CLOSE_AFTER if r.done_at else None


def actions_for(cu: CurrentUser, r: ServiceRequest, now: datetime) -> list[str]:
    """کارهایی که این کاربر الان روی درخواست می‌تواند انجام دهد (برای نمایش دکمه‌ها در اپ و پنل)."""
    actions: list[str] = []
    if can_handle(cu, r):
        actions += [f"status:{s}" for s in sorted(TRANSITIONS[r.status])]
        if r.status in OPEN_STATUSES:
            actions += ["assign", "transfer"]
    mine = r.requester_id == cu.id
    if mine and r.status in ("done", "closed") and r.satisfaction is None:
        actions.append("rate")
    if mine and r.status in ("done", "closed") and r.done_at and now <= r.done_at + AUTO_CLOSE_AFTER:
        actions.append("reopen")
    if (mine or can_handle(cu, r)) and r.status not in ("closed", "rejected"):
        actions += ["comment", "attach"]
    return actions


def close_stale(db: Session, now: datetime) -> None:
    """درخواست‌هایی که بیش از ۳ روز در «انجام شد» مانده‌اند بسته می‌شوند (بدون commit)."""
    stale = db.scalars(
        select(ServiceRequest).where(
            ServiceRequest.status == "done", ServiceRequest.done_at < now - AUTO_CLOSE_AFTER
        )
    ).all()
    for r in stale:
        r.status = "closed"
        r.closed_at = r.done_at + AUTO_CLOSE_AFTER
        db.add(
            ServiceRequestEvent(
                request_id=r.id,
                at=r.closed_at,
                kind="status",
                from_status="done",
                to_status="closed",
                note="بسته شدن خودکار پس از ۳ روز",
            )
        )
        notify(
            db,
            [r.requester_id],
            "request.status",
            f"درخواست {r.tracking_code}: {STATUSES['closed']}",
            body="بسته شدن خودکار پس از ۳ روز",
            link_type="request",
            link_id=r.id,
            at=r.closed_at,
        )
