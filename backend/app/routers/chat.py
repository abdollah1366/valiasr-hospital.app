"""گفتگوی داخلی کارکنان: دونفره، گروه و کانال واحد.

فقط اعضای هر گفتگو آن را می‌بینند (برای دیگران ۴۰۴)؛ مدیر سیستم هم راهی به متن پیام‌ها ندارد.
در لاگ حسابرسی فقط ساخت گروه و تغییر اعضا ثبت می‌شود، هرگز متن پیام.
"""

import uuid
from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, Query, Request, Response
from sqlalchemy import func, or_, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, get_active_user, require
from app.models import ChatConversation, ChatMember, ChatMessage, MediaFile, Unit, User
from app.schemas.chat import (
    ChatPersonOut,
    ConversationDetailOut,
    ConversationListOut,
    ConversationOut,
    DirectIn,
    GroupIn,
    GroupUpdateIn,
    LastMessageOut,
    MemberOut,
    MembersIn,
    MessageEditIn,
    MessageIn,
    MessageOut,
    MessagePage,
    MuteIn,
    ReplyPreviewOut,
    UnreadOut,
)
from app.schemas.requests import AttachmentOut
from app.services import chat as svc
from app.services.media import store_upload
from app.services.training import media_path, sign_media

router = APIRouter(prefix="/chat", tags=["chat"])

CREATE_GROUP = require("chat.group.create")

ATTACHMENT_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
    ".pdf": "application/pdf",
}
ATTACHMENT_MAX_MB = 15
# پاسخ پرسش دوره‌ای کمی عقب‌تر از اکنون است تا پیامی که هم‌زمان ثبت می‌شد از قلم نیفتد.
CURSOR_OVERLAP = timedelta(seconds=10)


def _not_found():
    return api_error(404, "not_found", "گفتگو پیدا نشد")


def _person(u: User, units: dict) -> ChatPersonOut:
    return ChatPersonOut(
        id=u.id,
        full_name=u.full_name,
        personnel_code=u.personnel_code,
        job_title=u.job_title,
        unit_name=units.get(u.unit_id),
    )


def _units(db: Session) -> dict[uuid.UUID, str]:
    return dict(db.execute(select(Unit.id, Unit.name)).all())


def _users(db: Session, ids: set) -> dict[uuid.UUID, User]:
    ids = {i for i in ids if i is not None}
    if not ids:
        return {}
    return {u.id: u for u in db.scalars(select(User).where(User.id.in_(ids)))}


def _load(db: Session, cu: CurrentUser, conv_id: uuid.UUID) -> tuple[ChatConversation, ChatMember]:
    conv = db.get(ChatConversation, conv_id)
    m = svc.member(db, conv_id, cu.id) if conv else None
    if m is None:
        raise _not_found()
    return conv, m


def _title(conv: ChatConversation, peer: User | None, units: dict) -> str:
    if conv.kind == "unit":
        return units.get(conv.unit_id, "کانال واحد")
    if conv.kind == "direct":
        return peer.full_name if peer else "کاربر حذف‌شده"
    return conv.title or "گروه"


def _counts(db: Session, conv_ids: list[uuid.UUID]) -> dict[uuid.UUID, int]:
    if not conv_ids:
        return {}
    return dict(
        db.execute(
            select(ChatMember.conversation_id, func.count())
            .where(ChatMember.conversation_id.in_(conv_ids))
            .group_by(ChatMember.conversation_id)
        ).all()
    )


def _last_messages(db: Session, conv_ids: list[uuid.UUID]) -> dict[uuid.UUID, ChatMessage]:
    if not conv_ids:
        return {}
    rows = db.scalars(
        select(ChatMessage)
        .where(ChatMessage.conversation_id.in_(conv_ids))
        .distinct(ChatMessage.conversation_id)
        .order_by(ChatMessage.conversation_id, ChatMessage.created_at.desc())
    )
    return {m.conversation_id: m for m in rows}


def _summaries(
    db: Session, cu: CurrentUser, pairs: list[tuple[ChatConversation, ChatMember]]
) -> list[ConversationOut]:
    ids = [c.id for c, _ in pairs]
    units = _units(db)
    counts = _counts(db, ids)
    unread = svc.unread_by_conversation(db, cu.id)
    lasts = _last_messages(db, ids)
    peers = {c.id: svc.direct_peer_id(c, cu.id) for c, _ in pairs}
    people = _users(db, set(peers.values()) | {m.sender_id for m in lasts.values()})
    out = []
    for c, m in pairs:
        peer = people.get(peers[c.id])
        last = lasts.get(c.id)
        out.append(
            ConversationOut(
                id=c.id,
                kind=c.kind,
                title=_title(c, peer, units),
                peer=_person(peer, units) if peer else None,
                unit_id=c.unit_id,
                member_count=counts.get(c.id, 0),
                last_message=(
                    LastMessageOut(
                        sender_name=people[last.sender_id].full_name if last.sender_id in people else None,
                        text=None if last.deleted_at else (svc.preview(last) if last.body else None),
                        has_attachment=last.media_id is not None,
                        deleted=last.deleted_at is not None,
                        at=last.created_at,
                        mine=last.sender_id == cu.id,
                    )
                    if last
                    else None
                ),
                unread=unread.get(c.id, 0),
                muted=m.muted,
                can_post=svc.can_post(cu, c, counts.get(c.id, 0)),
                is_admin=m.is_admin,
            )
        )
    return out


def _detail(db: Session, cu: CurrentUser, conv: ChatConversation, m: ChatMember) -> ConversationDetailOut:
    base = _summaries(db, cu, [(conv, m)])[0]
    units = _units(db)
    rows = db.execute(
        select(User, ChatMember.is_admin)
        .join(ChatMember, ChatMember.user_id == User.id)
        .where(ChatMember.conversation_id == conv.id)
        .order_by(ChatMember.is_admin.desc(), User.full_name)
    ).all()
    members = [MemberOut(**_person(u, units).model_dump(), is_admin=admin) for u, admin in rows]
    return ConversationDetailOut(
        **base.model_dump(),
        members=members,
        peer_read_at=_peer_read_at(db, conv, cu),
        can_manage=conv.kind == "group" and m.is_admin,
    )


def _peer_read_at(db: Session, conv: ChatConversation, cu: CurrentUser) -> datetime | None:
    if conv.kind != "direct":
        return None
    return db.scalar(
        select(ChatMember.last_read_at).where(
            ChatMember.conversation_id == conv.id, ChatMember.user_id != cu.id
        )
    )


def _messages_out(db: Session, cu: CurrentUser, rows: list[ChatMessage]) -> list[MessageOut]:
    reply_ids = {r.reply_to_id for r in rows if r.reply_to_id}
    replies = (
        {r.id: r for r in db.scalars(select(ChatMessage).where(ChatMessage.id.in_(reply_ids)))}
        if reply_ids
        else {}
    )
    people = _users(db, {r.sender_id for r in rows} | {r.sender_id for r in replies.values()})
    now = svc.now()

    def name(uid):
        return people[uid].full_name if uid in people else None

    out = []
    for r in rows:
        rep = replies.get(r.reply_to_id)
        out.append(
            MessageOut(
                id=r.id,
                client_uuid=r.client_uuid,
                sender_id=r.sender_id,
                sender_name=name(r.sender_id),
                body=r.body,
                attachment=(
                    AttachmentOut(
                        id=r.media.id,
                        original_name=r.media.original_name,
                        content_type=r.media.content_type,
                        size=r.media.size,
                        url=sign_media(r.media.id),
                    )
                    if r.media
                    else None
                ),
                reply_to=(
                    ReplyPreviewOut(
                        id=rep.id,
                        sender_name=name(rep.sender_id),
                        text=svc.preview(rep, 100) if rep.body else None,
                        has_attachment=rep.media_id is not None,
                        deleted=rep.deleted_at is not None,
                    )
                    if rep
                    else None
                ),
                created_at=r.created_at,
                edited=r.edited_at is not None,
                deleted=r.deleted_at is not None,
                can_edit=svc.can_edit(r, cu.id, now),
            )
        )
    return out


def _active_users(db: Session, ids: set[uuid.UUID]) -> set[uuid.UUID]:
    found = set(db.scalars(select(User.id).where(User.id.in_(ids), User.is_active.is_(True))))
    if found != ids:
        raise api_error(400, "unknown_user", "برخی کاربران انتخاب‌شده پیدا نشدند یا غیرفعال‌اند")
    return found


# ---------------------------------------------------------------- فهرست و جستجوی همکاران


@router.get("/conversations", response_model=ConversationListOut)
def list_conversations(cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    svc.sync_user(db, cu.user)
    db.commit()
    pairs = list(
        db.execute(
            select(ChatConversation, ChatMember)
            .join(ChatMember, ChatMember.conversation_id == ChatConversation.id)
            .where(ChatMember.user_id == cu.id)
            .order_by(func.coalesce(ChatConversation.last_message_at, ChatConversation.created_at).desc())
        )
        .tuples()
        .all()
    )
    items = _summaries(db, cu, pairs)
    # گفتگوی دونفره خالی (باز شده ولی بی‌پیام) فقط برای سازنده‌اش نمایش داده می‌شود
    items = [
        i
        for i, (c, _) in zip(items, pairs, strict=True)
        if i.last_message or c.kind != "direct" or c.created_by == cu.id
    ]
    return ConversationListOut(items=items, unread_total=sum(i.unread for i in items if not i.muted))


@router.get("/unread-count", response_model=UnreadOut)
def unread_count(cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    muted = set(
        db.scalars(select(ChatMember.conversation_id).where(ChatMember.user_id == cu.id, ChatMember.muted))
    )
    counts = svc.unread_by_conversation(db, cu.id)
    return UnreadOut(count=sum(n for c, n in counts.items() if c not in muted))


@router.get("/people", response_model=list[ChatPersonOut])
def search_people(
    q: str = Query(min_length=1, max_length=100),
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    text = q.strip()
    rows = db.scalars(
        select(User)
        .where(
            User.is_active.is_(True),
            User.id != cu.id,
            or_(User.full_name.ilike(f"%{text}%"), User.personnel_code.startswith(text)),
        )
        .order_by(User.full_name)
        .limit(20)
    )
    units = _units(db)
    return [_person(u, units) for u in rows]


# ---------------------------------------------------------------- ساختن گفتگو


@router.post("/direct", response_model=ConversationDetailOut)
def open_direct(body: DirectIn, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    if body.user_id == cu.id:
        raise api_error(400, "self", "با خودتان نمی‌توانید گفتگو کنید")
    _active_users(db, {body.user_id})
    key = svc.direct_key(cu.id, body.user_id)
    db.execute(
        insert(ChatConversation)
        .values(id=uuid.uuid4(), kind="direct", direct_key=key, created_by=cu.id, created_at=svc.now())
        .on_conflict_do_nothing(index_elements=["direct_key"])
    )
    conv = db.scalar(select(ChatConversation).where(ChatConversation.direct_key == key))
    # اگر یکی از دو نفر قبلاً غیرفعال و دوباره فعال شده باشد، دوباره عضو می‌شود
    svc.add_members(db, conv.id, [cu.id, body.user_id])
    db.commit()
    return _detail(db, cu, conv, svc.member(db, conv.id, cu.id))


@router.post("/groups", response_model=ConversationDetailOut, status_code=201)
def create_group(
    body: GroupIn, request: Request, cu: CurrentUser = Depends(CREATE_GROUP), db: Session = Depends(get_db)
):
    members = _active_users(db, set(body.member_ids) - {cu.id})
    conv = ChatConversation(kind="group", title=body.title.strip(), created_by=cu.id)
    db.add(conv)
    db.flush()
    svc.add_members(db, conv.id, [cu.id], admin=True)
    svc.add_members(db, conv.id, members)
    _notify_added(db, conv, members, cu)
    audit(db, "chat.group.created", cu.id, "chat", conv.id, client_ip(request), members=len(members) + 1)
    db.commit()
    return _detail(db, cu, conv, svc.member(db, conv.id, cu.id))


def _notify_added(db: Session, conv: ChatConversation, users: set[uuid.UUID], cu: CurrentUser) -> None:
    svc.notes.notify(
        db,
        users,
        "chat.added",
        f"شما به گروه «{conv.title}» اضافه شدید",
        body=f"توسط {cu.user.full_name}",
        link_type="chat",
        link_id=conv.id,
        actor_id=cu.id,
    )


def _manageable(db: Session, cu: CurrentUser, conv_id: uuid.UUID) -> tuple[ChatConversation, ChatMember]:
    conv, m = _load(db, cu, conv_id)
    if conv.kind != "group" or not m.is_admin:
        raise api_error(403, "forbidden", "فقط مدیر گروه می‌تواند این کار را انجام دهد")
    return conv, m


@router.patch("/conversations/{conv_id}", response_model=ConversationDetailOut)
def rename_group(
    conv_id: uuid.UUID,
    body: GroupUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    conv, m = _manageable(db, cu, conv_id)
    conv.title = body.title.strip()
    audit(db, "chat.group.renamed", cu.id, "chat", conv.id, client_ip(request))
    db.commit()
    return _detail(db, cu, conv, m)


@router.post("/conversations/{conv_id}/members", response_model=ConversationDetailOut)
def add_group_members(
    conv_id: uuid.UUID,
    body: MembersIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    conv, m = _manageable(db, cu, conv_id)
    existing = set(db.scalars(select(ChatMember.user_id).where(ChatMember.conversation_id == conv.id)))
    new = _active_users(db, set(body.user_ids)) - existing
    svc.add_members(db, conv.id, new)
    _notify_added(db, conv, new, cu)
    if new:
        audit(
            db, "chat.members.added", cu.id, "chat", conv.id, client_ip(request), users=sorted(map(str, new))
        )
    db.commit()
    return _detail(db, cu, conv, m)


@router.delete("/conversations/{conv_id}/members/{user_id}", status_code=204)
def remove_member(
    conv_id: uuid.UUID,
    user_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    """مدیر گروه عضوی را بیرون می‌کند، یا هر عضو خودش از گروه بیرون می‌رود."""
    conv, m = _load(db, cu, conv_id)
    if conv.kind != "group":
        raise api_error(400, "not_group", "فقط از گروه می‌توان بیرون رفت")
    if user_id != cu.id and not m.is_admin:
        raise api_error(403, "forbidden", "فقط مدیر گروه می‌تواند عضو را حذف کند")
    target = svc.member(db, conv.id, user_id)
    if target is None:
        raise api_error(404, "not_found", "این کاربر عضو گروه نیست")
    db.delete(target)
    db.flush()
    # گروه بی‌مدیر نمی‌ماند: قدیمی‌ترین عضو باقی‌مانده مدیر می‌شود
    remaining = db.scalars(
        select(ChatMember).where(ChatMember.conversation_id == conv.id).order_by(ChatMember.joined_at)
    ).all()
    if remaining and not any(r.is_admin for r in remaining):
        remaining[0].is_admin = True
    action = "chat.member.left" if user_id == cu.id else "chat.member.removed"
    audit(db, action, cu.id, "chat", conv.id, client_ip(request), user=str(user_id))
    db.commit()
    return Response(status_code=204)


@router.post("/conversations/{conv_id}/mute", status_code=204)
def mute(
    conv_id: uuid.UUID,
    body: MuteIn,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    _, m = _load(db, cu, conv_id)
    m.muted = body.muted
    db.commit()
    return Response(status_code=204)


# ---------------------------------------------------------------- پیام‌ها


@router.get("/conversations/{conv_id}", response_model=ConversationDetailOut)
def get_conversation(
    conv_id: uuid.UUID, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)
):
    conv, m = _load(db, cu, conv_id)
    return _detail(db, cu, conv, m)


@router.get("/conversations/{conv_id}/messages", response_model=MessagePage)
def list_messages(
    conv_id: uuid.UUID,
    before: datetime | None = None,
    since: datetime | None = None,
    limit: int = Query(50, ge=1, le=200),
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    """بدون since: آخرین پیام‌ها (و با before، قدیمی‌ترها). با since: پیام‌های تازه یا ویرایش/حذف‌شده از آن لحظه."""
    conv, _ = _load(db, cu, conv_id)
    cursor = svc.now() - CURSOR_OVERLAP
    stmt = select(ChatMessage).where(ChatMessage.conversation_id == conv.id)
    next_before = None
    if since is not None:
        rows = list(
            db.scalars(stmt.where(ChatMessage.updated_at > since).order_by(ChatMessage.created_at).limit(500))
        )
    else:
        if before is not None:
            stmt = stmt.where(ChatMessage.created_at < before)
        rows = list(db.scalars(stmt.order_by(ChatMessage.created_at.desc()).limit(limit + 1)))
        if len(rows) > limit:
            rows = rows[:limit]
            next_before = rows[-1].created_at
        rows.reverse()
    return MessagePage(
        items=_messages_out(db, cu, rows),
        next_before=next_before,
        cursor=cursor,
        peer_read_at=_peer_read_at(db, conv, cu),
    )


def _existing(db: Session, cu: CurrentUser, conv: ChatConversation, client_uuid: uuid.UUID):
    """ارسال دوباره از صف آفلاین همان پیام قبلی را برمی‌گرداند."""
    old = db.scalar(select(ChatMessage).where(ChatMessage.client_uuid == client_uuid))
    if old is not None and (old.sender_id != cu.id or old.conversation_id != conv.id):
        raise api_error(409, "duplicate", "شناسه پیام تکراری است")
    return old


def _check_send(db: Session, cu: CurrentUser, conv: ChatConversation, reply_to_id: uuid.UUID | None) -> None:
    count = db.scalar(select(func.count()).where(ChatMember.conversation_id == conv.id))
    if not svc.can_post(cu, conv, count):
        message = (
            "در کانال واحد فقط مسئول واحد پیام می‌فرستد"
            if conv.kind == "unit"
            else "این همکار دیگر در سامانه فعال نیست"
        )
        raise api_error(403, "cannot_post", message)
    if reply_to_id is not None:
        target = db.get(ChatMessage, reply_to_id)
        if target is None or target.conversation_id != conv.id:
            raise api_error(400, "bad_reply", "پیامی که به آن پاسخ می‌دهید پیدا نشد")


def _finish_send(
    db: Session, cu: CurrentUser, conv: ChatConversation, m: ChatMember, msg: ChatMessage
) -> MessageOut:
    db.add(msg)
    db.flush()
    conv.last_message_at = msg.created_at
    svc.mark_read(db, m, msg.created_at)
    units = _units(db)
    peer = _users(db, {svc.direct_peer_id(conv, cu.id)}).get(svc.direct_peer_id(conv, cu.id))
    title = cu.user.full_name if conv.kind == "direct" else _title(conv, peer, units)
    svc.notify_message(db, conv, title, msg, cu.user)
    db.commit()
    db.refresh(msg)
    return _messages_out(db, cu, [msg])[0]


@router.post("/conversations/{conv_id}/messages", response_model=MessageOut, status_code=201)
def send_message(
    conv_id: uuid.UUID,
    body: MessageIn,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    conv, m = _load(db, cu, conv_id)
    old = _existing(db, cu, conv, body.client_uuid)
    if old is not None:
        return _messages_out(db, cu, [old])[0]
    _check_send(db, cu, conv, body.reply_to_id)
    if conv.kind == "unit":
        svc.sync_unit(db, conv)
    text = body.body.strip()
    if not text:
        raise api_error(422, "empty", "متن پیام خالی است")
    msg = ChatMessage(
        conversation_id=conv.id,
        sender_id=cu.id,
        client_uuid=body.client_uuid,
        body=text,
        reply_to_id=body.reply_to_id,
    )
    return _finish_send(db, cu, conv, m, msg)


@router.post("/conversations/{conv_id}/attachments", response_model=MessageOut, status_code=201)
async def send_attachment(
    conv_id: uuid.UUID,
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    client_uuid: uuid.UUID = Query(),
    reply_to_id: uuid.UUID | None = None,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    """بدنه درخواست خود فایل است (عکس یا PDF تا ۱۵ مگابایت)."""
    conv, m = _load(db, cu, conv_id)
    old = _existing(db, cu, conv, client_uuid)
    if old is not None:
        return _messages_out(db, cu, [old])[0]
    _check_send(db, cu, conv, reply_to_id)
    if conv.kind == "unit":
        svc.sync_unit(db, conv)
    media: MediaFile = await store_upload(request, filename, ATTACHMENT_TYPES, ATTACHMENT_MAX_MB, cu.id)
    db.add(media)
    db.flush()
    msg = ChatMessage(
        conversation_id=conv.id,
        sender_id=cu.id,
        client_uuid=client_uuid,
        media_id=media.id,
        reply_to_id=reply_to_id,
    )
    return _finish_send(db, cu, conv, m, msg)


def _own_message(db: Session, cu: CurrentUser, conv_id: uuid.UUID, msg_id: uuid.UUID) -> ChatMessage:
    _load(db, cu, conv_id)
    msg = db.get(ChatMessage, msg_id)
    if msg is None or msg.conversation_id != conv_id:
        raise api_error(404, "not_found", "پیام پیدا نشد")
    if not svc.can_edit(msg, cu.id, svc.now()):
        raise api_error(403, "edit_window", "فقط پیام خودتان را تا ۱۵ دقیقه پس از ارسال می‌توانید تغییر دهید")
    return msg


@router.patch("/conversations/{conv_id}/messages/{msg_id}", response_model=MessageOut)
def edit_message(
    conv_id: uuid.UUID,
    msg_id: uuid.UUID,
    body: MessageEditIn,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    msg = _own_message(db, cu, conv_id, msg_id)
    text = body.body.strip()
    if msg.body is None or not text:
        raise api_error(400, "not_editable", "این پیام متن ندارد")
    msg.body = text
    msg.edited_at = msg.updated_at = svc.now()
    db.commit()
    return _messages_out(db, cu, [msg])[0]


@router.delete("/conversations/{conv_id}/messages/{msg_id}", status_code=204)
def delete_message(
    conv_id: uuid.UUID,
    msg_id: uuid.UUID,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    """متن و فایل واقعاً پاک می‌شوند؛ به جای پیام «این پیام حذف شد» نمایش داده می‌شود."""
    msg = _own_message(db, cu, conv_id, msg_id)
    media = msg.media
    msg.body = None
    msg.media = None
    msg.deleted_at = msg.updated_at = svc.now()
    db.flush()
    path = media_path(media) if media is not None else None
    if media is not None:
        db.delete(media)
    db.commit()
    if path is not None:
        path.unlink(missing_ok=True)
    return Response(status_code=204)


@router.post("/conversations/{conv_id}/read", status_code=204)
def mark_read(conv_id: uuid.UUID, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    _, m = _load(db, cu, conv_id)
    svc.mark_read(db, m, svc.now())
    db.commit()
    return Response(status_code=204)
