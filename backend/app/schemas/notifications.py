import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class NotificationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    kind: str
    title: str
    body: str | None
    link_type: str | None
    link_id: uuid.UUID | None
    important: bool
    created_at: datetime
    read: bool


class NotificationPage(BaseModel):
    items: list[NotificationOut]
    unread: int
    # برای صفحه بعد: created_at آخرین مورد این صفحه، یا null اگر صفحه دیگری نیست
    next_before: datetime | None


class UnreadCountOut(BaseModel):
    count: int
    important: int
