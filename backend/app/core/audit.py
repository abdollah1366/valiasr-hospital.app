import uuid

from sqlalchemy.orm import Session

from app.models import AuditLog


def audit(
    db: Session,
    action: str,
    actor_id: uuid.UUID | None = None,
    entity_type: str | None = None,
    entity_id: object | None = None,
    ip: str | None = None,
    **details: object,
) -> None:
    """ثبت رویداد حسابرسی. commit با فراخواننده است تا با تغییر اصلی در یک تراکنش بماند."""
    db.add(
        AuditLog(
            action=action,
            actor_id=actor_id,
            entity_type=entity_type,
            entity_id=str(entity_id) if entity_id is not None else None,
            ip=ip,
            details={k: v for k, v in details.items() if v is not None},
        )
    )
