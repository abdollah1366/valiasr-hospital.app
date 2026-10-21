"""امتیاز و نظر مراجعان درباره پزشک.

ستاره بلافاصله در میانگین حساب می‌شود؛ متن نظر فقط بعد از تأیید مدیر پزشکان عمومی می‌شود.
نام و موبایل مراجع هرگز در خروجی عمومی یا لاگ حسابرسی نمی‌آید.
"""

import uuid

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import DoctorReview

# حداقل تعداد ستاره برای نمایش میانگین؛ با یک رأی میانگین گمراه‌کننده است.
MIN_VOTES_FOR_AVERAGE = 1


def rating(db: Session, doctor_id: uuid.UUID) -> tuple[float | None, int]:
    avg, count = db.execute(
        select(func.avg(DoctorReview.stars), func.count()).where(DoctorReview.doctor_id == doctor_id)
    ).one()
    if not count or count < MIN_VOTES_FOR_AVERAGE:
        return None, int(count or 0)
    return round(float(avg), 1), int(count)


def ratings(db: Session, doctor_ids: list[uuid.UUID]) -> dict[uuid.UUID, tuple[float | None, int]]:
    """میانگین چند پزشک با یک پرس‌وجو (برای فهرست پزشکان)."""
    if not doctor_ids:
        return {}
    rows = db.execute(
        select(DoctorReview.doctor_id, func.avg(DoctorReview.stars), func.count())
        .where(DoctorReview.doctor_id.in_(doctor_ids))
        .group_by(DoctorReview.doctor_id)
    )
    return {d: (round(float(a), 1), int(c)) for d, a, c in rows.all()}


def initial_status(comment: str | None) -> str:
    """نظر بدون متن چیزی برای بازبینی ندارد."""
    return "pending" if comment and comment.strip() else "approved"
