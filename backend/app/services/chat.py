"""گفتگوی داخلی کارکنان: عضویت، کانال خودکار واحدها، شمارنده خوانده‌نشده و اعلان پیام تازه.

متن پیام فقط به اعضای همان گفتگو برگردانده می‌شود؛ در لاگ حسابرسی و لاگ سرور نوشته نمی‌شود.
توابع این ماژول commit نمی‌کنند.
"""

import uuid
from collections.abc import Iterable
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, func, or_, select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.core.deps import CurrentUser
from app.models import ChatConversation, ChatMember, ChatMessage, Notification, Role, User, UserRole
from app.services import notifications as notes

EDIT_WINDOW = timedelta(minutes=15)
UNIT_POST = "chat.unit.post"
NOTE_KIND = "chat.message"


def now() -> datetime:
    return datetime.now(UTC)


def direct_key(a: uuid.UUID, b: uuid.UUID) -> str:
    return ":".join(sorted((str(a), str(b))))


def direct_peer_id(conv: ChatConversation, me: uuid.UUID) -> uuid.UUID | None:
    if not conv.direct_key:
        return None
    ids = [uuid.UUID(x) for x in conv.direct_key.split(":")]
    others = [i for i in ids if i != me]
    return others[0] if others else None


# ---------------------------------------------------------------- عضویت


def add_members(db: Session, conv_id: uuid.UUID, user_ids: Iterable[uuid.UUID], admin: bool = False) -> None:
    """عضو تازه پیام‌های قبلی را خوانده‌نشده نمی‌بیند. عضو موجود دست نمی‌خورد."""
    rows = [
        {"id": uuid.uuid4(), "conversation_id": conv_id, "user_id": u, "is_admin": admin, "muted": False}
        | {"joined_at": now(), "last_read_at": now()}
        for u in set(user_ids)
    ]
    if rows:
        db.execute(insert(ChatMember).values(rows).on_conflict_do_nothing())


def member(db: Session, conv_id: uuid.UUID, user_id: uuid.UUID) -> ChatMember | None:
    return db.scalar(
        select(ChatMember).where(ChatMember.conversation_id == conv_id, ChatMember.user_id == user_id)
    )


def _post_units(db: Session, user_id: uuid.UUID) -> set[uuid.UUID]:
    """واحدهایی که کاربر با نقشِ وابسته به واحد اجازه نوشتن در کانالشان را دارد (مثلاً مسئول واحد)."""
    return set(
        db.scalars(
            select(UserRole.unit_id)
            .join(Role, Role.id == UserRole.role_id)
            .where(
                UserRole.user_id == user_id,
                UserRole.unit_id.is_not(None),
                Role.permissions.contains([UNIT_POST]),
            )
        )
    )


def unit_channel(db: Session, unit_id: uuid.UUID) -> ChatConversation:
    db.execute(
        insert(ChatConversation)
        .values(id=uuid.uuid4(), kind="unit", unit_id=unit_id, created_at=now())
        .on_conflict_do_nothing(index_elements=["unit_id"])
    )
    return db.scalar(select(ChatConversation).where(ChatConversation.unit_id == unit_id))


def sync_user(db: Session, user: User) -> None:
    """کانال واحد کاربر و واحدهایی که مسئول آن است؛ کاربر غیرفعال از همه گفتگوها بیرون می‌رود."""
    if not user.is_active:
        db.execute(delete(ChatMember).where(ChatMember.user_id == user.id))
        return
    wanted = _post_units(db, user.id)
    if user.unit_id:
        wanted.add(user.unit_id)
    channels = {unit_id: unit_channel(db, unit_id).id for unit_id in wanted}
    for conv_id in channels.values():
        add_members(db, conv_id, [user.id])
    stale = select(ChatConversation.id).where(
        ChatConversation.kind == "unit", ChatConversation.id.not_in(list(channels.values()))
    )
    db.execute(delete(ChatMember).where(ChatMember.user_id == user.id, ChatMember.conversation_id.in_(stale)))


def sync_unit(db: Session, conv: ChatConversation) -> None:
    """پیش از ارسال در کانال واحد همه کارکنان فعلی واحد عضو می‌شوند تا اعلان را بگیرند."""
    staff = set(db.scalars(select(User.id).where(User.unit_id == conv.unit_id, User.is_active.is_(True))))
    managers = set(
        db.scalars(
            select(UserRole.user_id)
            .join(Role, Role.id == UserRole.role_id)
            .join(User, User.id == UserRole.user_id)
            .where(
                UserRole.unit_id == conv.unit_id,
                Role.permissions.contains([UNIT_POST]),
                User.is_active.is_(True),
            )
        )
    )
    wanted = staff | managers
    add_members(db, conv.id, wanted)
    db.execute(
        delete(ChatMember).where(
            ChatMember.conversation_id == conv.id, ChatMember.user_id.not_in(list(wanted))
        )
    )


def can_post(cu: CurrentUser, conv: ChatConversation, member_count: int) -> bool:
    if conv.kind == "unit":
        return cu.can_in_unit(UNIT_POST, conv.unit_id)
    if conv.kind == "direct":
        return member_count == 2  # طرف مقابل غیرفعال شده است
    return True


# ---------------------------------------------------------------- خوانده‌نشده‌ها


def _not_mine(user_id: uuid.UUID):
    return or_(ChatMessage.sender_id.is_(None), ChatMessage.sender_id != user_id)


def unread_by_conversation(db: Session, user_id: uuid.UUID) -> dict[uuid.UUID, int]:
    rows = db.execute(
        select(ChatMessage.conversation_id, func.count())
        .join(
            ChatMember,
            (ChatMember.conversation_id == ChatMessage.conversation_id) & (ChatMember.user_id == user_id),
        )
        .where(
            ChatMessage.created_at > ChatMember.last_read_at,
            ChatMessage.deleted_at.is_(None),
            _not_mine(user_id),
        )
        .group_by(ChatMessage.conversation_id)
    )
    return dict(rows.all())


def mark_read(db: Session, m: ChatMember, at: datetime) -> None:
    m.last_read_at = max(m.last_read_at, at)
    db.execute(
        update(Notification)
        .where(
            Notification.user_id == m.user_id,
            Notification.kind == NOTE_KIND,
            Notification.link_id == m.conversation_id,
            Notification.read_at.is_(None),
        )
        .values(read_at=at)
    )


# ---------------------------------------------------------------- اعلان


def preview(msg: ChatMessage, limit: int = 120) -> str:
    if msg.body:
        text = " ".join(msg.body.split())
        return text if len(text) <= limit else text[: limit - 1] + "…"
    if msg.media and msg.media.content_type.startswith("image/"):
        return "[عکس]"
    return "[فایل]"


def notify_message(db: Session, conv: ChatConversation, title: str, msg: ChatMessage, sender: User) -> None:
    """هر گفتگو در زنگوله هر نفر حداکثر یک اعلان خوانده‌نشده دارد که با پیام تازه جایگزین می‌شود."""
    recipients = set(
        db.scalars(
            select(ChatMember.user_id).where(
                ChatMember.conversation_id == conv.id,
                ChatMember.muted.is_(False),
                ChatMember.user_id != sender.id,
            )
        )
    )
    if not recipients:
        return
    db.execute(
        delete(Notification).where(
            Notification.kind == NOTE_KIND,
            Notification.link_id == conv.id,
            Notification.read_at.is_(None),
            Notification.user_id.in_(recipients),
        )
    )
    text = preview(msg)
    notes.notify(
        db,
        recipients,
        NOTE_KIND,
        title,
        body=text if conv.kind == "direct" else f"{sender.full_name}: {text}",
        link_type="chat",
        link_id=conv.id,
    )


def can_edit(msg: ChatMessage, user_id: uuid.UUID, at: datetime) -> bool:
    return msg.sender_id == user_id and msg.deleted_at is None and at - msg.created_at <= EDIT_WINDOW
