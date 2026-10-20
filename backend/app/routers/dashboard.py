from datetime import date, timedelta

from fastapi import APIRouter, Depends, Query
from sqlalchemy import extract, func, select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.deps import CurrentUser, require
from app.models import Incident, Unit

router = APIRouter(prefix="/dashboard", tags=["dashboard"])


@router.get("/incidents/summary")
def incident_summary(
    days: int = Query(365, ge=1, le=3650),
    _: CurrentUser = Depends(require("dashboard.read")),
    db: Session = Depends(get_db),
):
    """آمار تجمیعی؛ هیچ اطلاعات هویتی بیمار یا گزارش‌دهنده‌ای برنمی‌گرداند."""
    since = date.today() - timedelta(days=days)
    base = select(Incident).where(Incident.occurred_on >= since).subquery()

    def grouped(col):
        return {str(k): v for k, v in db.execute(select(col, func.count()).group_by(col)).all()}

    by_unit = [
        {"unit_id": str(uid), "unit_name": name, "count": n}
        for uid, name, n in db.execute(
            select(base.c.unit_id, Unit.name, func.count())
            .join(Unit, Unit.id == base.c.unit_id)
            .group_by(base.c.unit_id, Unit.name)
            .order_by(func.count().desc())
        ).all()
    ]
    month = func.to_char(base.c.occurred_on, "YYYY-MM")
    by_month = {
        m: n for m, n in db.execute(select(month, func.count()).group_by(month).order_by(month)).all()
    }
    avg_hours = db.scalar(
        select(func.avg(extract("epoch", base.c.closed_at - base.c.created_at) / 3600)).where(
            base.c.status == "closed"
        )
    )
    return {
        "since": since,
        "total": db.scalar(select(func.count()).select_from(base)),
        "by_kind": grouped(base.c.kind),
        "by_status": grouped(base.c.status),
        "by_unit": by_unit,
        "by_month": by_month,
        "avg_hours_to_close": round(float(avg_hours), 1) if avg_hours is not None else None,
    }
