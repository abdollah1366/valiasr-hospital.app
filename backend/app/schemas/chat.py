import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from app.schemas.requests import AttachmentOut


class ChatPersonOut(BaseModel):
    id: uuid.UUID
    full_name: str
    personnel_code: str
    job_title: str | None = None
    unit_name: str | None = None


class MemberOut(ChatPersonOut):
    is_admin: bool


class ReplyPreviewOut(BaseModel):
    id: uuid.UUID
    sender_name: str | None
    text: str | None
    has_attachment: bool
    deleted: bool


class MessageOut(BaseModel):
    id: uuid.UUID
    client_uuid: uuid.UUID
    sender_id: uuid.UUID | None
    sender_name: str | None
    body: str | None
    attachment: AttachmentOut | None
    reply_to: ReplyPreviewOut | None
    created_at: datetime
    edited: bool
    deleted: bool
    can_edit: bool


class LastMessageOut(BaseModel):
    sender_name: str | None
    text: str | None  # متن کوتاه، یا null برای پیوست بدون متن
    has_attachment: bool
    deleted: bool
    at: datetime
    mine: bool


class ConversationOut(BaseModel):
    id: uuid.UUID
    kind: str  # direct | group | unit
    title: str
    peer: ChatPersonOut | None = None  # طرف دیگر گفتگوی دونفره
    unit_id: uuid.UUID | None = None
    member_count: int
    last_message: LastMessageOut | None
    unread: int
    muted: bool
    can_post: bool
    is_admin: bool


class ConversationListOut(BaseModel):
    items: list[ConversationOut]
    unread_total: int


class ConversationDetailOut(ConversationOut):
    members: list[MemberOut]
    # فقط در گفتگوی دونفره: طرف مقابل پیام‌های تا این لحظه را دیده است.
    peer_read_at: datetime | None = None
    can_manage: bool


class MessagePage(BaseModel):
    items: list[MessageOut]  # قدیمی‌ترین اول
    next_before: datetime | None  # برای بارگذاری پیام‌های قدیمی‌تر
    # این مقدار را در پرسش بعدی به‌عنوان since بفرستید (کمی هم‌پوشانی دارد؛ پیام‌ها را با id جایگزین کنید).
    cursor: datetime
    peer_read_at: datetime | None = None


class DirectIn(BaseModel):
    user_id: uuid.UUID


class GroupIn(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    member_ids: list[uuid.UUID] = Field(default_factory=list, max_length=500)


class GroupUpdateIn(BaseModel):
    title: str = Field(min_length=1, max_length=200)


class MembersIn(BaseModel):
    user_ids: list[uuid.UUID] = Field(min_length=1, max_length=500)


class MessageIn(BaseModel):
    client_uuid: uuid.UUID
    body: str = Field(min_length=1, max_length=4000)
    reply_to_id: uuid.UUID | None = None


class MessageEditIn(BaseModel):
    body: str = Field(min_length=1, max_length=4000)


class MuteIn(BaseModel):
    muted: bool


class UnreadOut(BaseModel):
    count: int
