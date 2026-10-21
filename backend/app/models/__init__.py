import uuid
from datetime import UTC, date, datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    Date,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.db import Base


def utcnow() -> datetime:
    return datetime.now(UTC)


class TimestampMixin:
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)


def pk() -> Mapped[uuid.UUID]:
    return mapped_column(primary_key=True, default=uuid.uuid4)


# ---------------------------------------------------------------- هسته


class Unit(TimestampMixin, Base):
    """بخش، درمانگاه یا واحد اداری/پشتیبانی (ساختار درختی)."""

    __tablename__ = "units"
    id: Mapped[uuid.UUID] = pk()
    name: Mapped[str] = mapped_column(String(200))
    type: Mapped[str] = mapped_column(String(30))  # ward | clinic | support | admin
    parent_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id"))
    is_public: Mapped[bool] = mapped_column(Boolean, default=False)
    location: Mapped[str | None] = mapped_column(String(300))
    phone: Mapped[str | None] = mapped_column(String(50))
    hours: Mapped[str | None] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(Text)
    image_media_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("media_files.id", ondelete="SET NULL")
    )

    @property
    def has_image(self) -> bool:
        return self.image_media_id is not None


class User(TimestampMixin, Base):
    __tablename__ = "users"
    id: Mapped[uuid.UUID] = pk()
    personnel_code: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    full_name: Mapped[str] = mapped_column(String(200))
    mobile: Mapped[str | None] = mapped_column(String(20))
    unit_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id"))
    job_title: Mapped[str | None] = mapped_column(String(100), index=True)  # سمت؛ مثلاً پرستار
    password_hash: Mapped[str] = mapped_column(String(300))
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    must_change_password: Mapped[bool] = mapped_column(Boolean, default=True)
    failed_attempts: Mapped[int] = mapped_column(Integer, default=0)
    locked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_login_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    roles: Mapped[list["UserRole"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class Role(Base):
    __tablename__ = "roles"
    id: Mapped[uuid.UUID] = pk()
    code: Mapped[str] = mapped_column(String(50), unique=True)
    name: Mapped[str] = mapped_column(String(100))
    permissions: Mapped[list[str]] = mapped_column(JSONB, default=list)


class UserRole(Base):
    """نقش کاربر؛ اگر unit_id داشته باشد، مجوزهای «.unit» فقط برای همان واحد معتبرند."""

    __tablename__ = "user_roles"
    __table_args__ = (UniqueConstraint("user_id", "role_id", "unit_id"),)
    id: Mapped[uuid.UUID] = pk()
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    role_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("roles.id"))
    unit_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id"))

    user: Mapped[User] = relationship(back_populates="roles")
    role: Mapped[Role] = relationship(lazy="joined")


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"
    id: Mapped[uuid.UUID] = pk()
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    family_id: Mapped[uuid.UUID] = mapped_column(index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    device: Mapped[str | None] = mapped_column(String(200))


class AuditLog(Base):
    """فقط درج؛ هیچ endpointی برای ویرایش یا حذف آن وجود ندارد."""

    __tablename__ = "audit_logs"
    id: Mapped[uuid.UUID] = pk()
    at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, index=True)
    actor_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    action: Mapped[str] = mapped_column(String(100), index=True)
    entity_type: Mapped[str | None] = mapped_column(String(50))
    entity_id: Mapped[str | None] = mapped_column(String(64))
    ip: Mapped[str | None] = mapped_column(String(64))
    details: Mapped[dict] = mapped_column(JSONB, default=dict)


class FormTemplate(Base):
    __tablename__ = "form_templates"
    __table_args__ = (UniqueConstraint("code", "version"),)
    id: Mapped[uuid.UUID] = pk()
    code: Mapped[str] = mapped_column(String(50), index=True)
    version: Mapped[int] = mapped_column(Integer)
    schema: Mapped[dict] = mapped_column(JSONB)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


# ---------------------------------------------------------------- گزارش خطا


class Incident(TimestampMixin, Base):
    __tablename__ = "incidents"
    id: Mapped[uuid.UUID] = pk()
    tracking_code: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    client_uuid: Mapped[uuid.UUID] = mapped_column(unique=True)
    kind: Mapped[str] = mapped_column(String(30), index=True)
    form_template_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("form_templates.id"))
    answers: Mapped[dict] = mapped_column(JSONB, default=dict)
    sensitive_answers_encrypted: Mapped[str | None] = mapped_column(Text)
    unit_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("units.id"), index=True)
    occurred_on: Mapped[date] = mapped_column(Date)
    shift: Mapped[str | None] = mapped_column(String(20))
    status: Mapped[str] = mapped_column(String(30), default="submitted", index=True)
    assigned_unit_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id"))
    assigned_user_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    is_anonymous: Mapped[bool] = mapped_column(Boolean, default=False)
    # در گزارش ناشناس null می‌ماند؛ هیچ ستون دیگری هم هویت گزارش‌دهنده را نگه نمی‌دارد.
    reporter_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    follow_up_secret_hash: Mapped[str | None] = mapped_column(String(64))
    closed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    history: Mapped[list["IncidentStatusChange"]] = relationship(
        order_by="IncidentStatusChange.at", cascade="all, delete-orphan"
    )
    actions: Mapped[list["IncidentAction"]] = relationship(
        order_by="IncidentAction.created_at", cascade="all, delete-orphan"
    )


class IncidentStatusChange(Base):
    __tablename__ = "incident_status_history"
    id: Mapped[uuid.UUID] = pk()
    incident_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("incidents.id", ondelete="CASCADE"), index=True)
    from_status: Mapped[str | None] = mapped_column(String(30))
    to_status: Mapped[str] = mapped_column(String(30))
    by_user_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    note: Mapped[str | None] = mapped_column(Text)
    at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class IncidentAction(Base):
    """اقدام اصلاحی."""

    __tablename__ = "incident_actions"
    id: Mapped[uuid.UUID] = pk()
    incident_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("incidents.id", ondelete="CASCADE"), index=True)
    description: Mapped[str] = mapped_column(Text)
    owner_name: Mapped[str | None] = mapped_column(String(200))
    due_date: Mapped[date | None] = mapped_column(Date)
    done_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    result: Mapped[str | None] = mapped_column(Text)
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


# ---------------------------------------------------------------- اطلاع‌رسانی


class Announcement(TimestampMixin, Base):
    __tablename__ = "announcements"
    id: Mapped[uuid.UUID] = pk()
    title: Mapped[str] = mapped_column(String(300))
    body: Mapped[str] = mapped_column(Text)
    category: Mapped[str] = mapped_column(String(30))  # news | circular | guideline | notice
    audience: Mapped[str] = mapped_column(String(10), index=True)  # public | staff
    target_role_codes: Mapped[list[str]] = mapped_column(JSONB, default=list)
    target_unit_ids: Mapped[list[str]] = mapped_column(JSONB, default=list)
    is_important: Mapped[bool] = mapped_column(Boolean, default=False)
    requires_ack: Mapped[bool] = mapped_column(Boolean, default=False)
    published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, index=True)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    author_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))


class AnnouncementRead(Base):
    __tablename__ = "announcement_reads"
    __table_args__ = (UniqueConstraint("announcement_id", "user_id"),)
    id: Mapped[uuid.UUID] = pk()
    announcement_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("announcements.id", ondelete="CASCADE"))
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    read_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    acknowledged_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


# ---------------------------------------------------------------- بخش عمومی


class Faq(Base):
    __tablename__ = "faqs"
    id: Mapped[uuid.UUID] = pk()
    question: Mapped[str] = mapped_column(Text)
    answer: Mapped[str] = mapped_column(Text)
    category: Mapped[str | None] = mapped_column(String(100))
    sort_order: Mapped[int] = mapped_column(Integer, default=0)


class Feedback(TimestampMixin, Base):
    """پیشنهاد، انتقاد، شکایت یا قدردانی مراجعان."""

    __tablename__ = "feedback"
    id: Mapped[uuid.UUID] = pk()
    tracking_code: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    kind: Mapped[str] = mapped_column(String(20))  # suggestion | criticism | complaint | praise
    name: Mapped[str | None] = mapped_column(String(200))
    mobile: Mapped[str | None] = mapped_column(String(20))
    unit_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id"))
    body: Mapped[str] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(20), default="received")  # received | reviewing | answered
    response: Mapped[str | None] = mapped_column(Text)


# ---------------------------------------------------------------- آموزش


class AudienceMixin:
    """مخاطب: افراد نام‌برده همیشه؛ بقیه باید در همه فهرست‌های پرشده بگنجند. همه خالی = همه کارکنان."""

    target_unit_ids: Mapped[list[str]] = mapped_column(JSONB, default=list)
    target_role_codes: Mapped[list[str]] = mapped_column(JSONB, default=list)
    target_job_titles: Mapped[list[str]] = mapped_column(JSONB, default=list)
    target_user_ids: Mapped[list[str]] = mapped_column(JSONB, default=list)


class MediaFile(Base):
    """فایل بارگذاری‌شده؛ خود فایل روی دیسک (media_dir) و فقط از راه API با بررسی دسترسی."""

    __tablename__ = "media_files"
    id: Mapped[uuid.UUID] = pk()
    original_name: Mapped[str] = mapped_column(String(300))
    content_type: Mapped[str] = mapped_column(String(100))
    size: Mapped[int] = mapped_column(BigInteger)
    sha256: Mapped[str] = mapped_column(String(64))
    storage_name: Mapped[str] = mapped_column(String(100), unique=True)
    uploaded_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Course(AudienceMixin, TimestampMixin, Base):
    __tablename__ = "courses"
    id: Mapped[uuid.UUID] = pk()
    title: Mapped[str] = mapped_column(String(300))
    description: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(
        String(20), default="draft", index=True
    )  # draft | published | archived
    is_mandatory: Mapped[bool] = mapped_column(Boolean, default=False)
    due_date: Mapped[date | None] = mapped_column(Date)
    cover_media_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("media_files.id"))
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))

    items: Mapped[list["CourseItem"]] = relationship(
        order_by="CourseItem.sort_order", cascade="all, delete-orphan", back_populates="course"
    )


class CourseItem(Base):
    """یک محتوای دوره: متن، عکس، فیلم، صوت، PDF، پاورپوینت، فایل یا لینک."""

    __tablename__ = "course_items"
    id: Mapped[uuid.UUID] = pk()
    course_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("courses.id", ondelete="CASCADE"), index=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)
    kind: Mapped[str] = mapped_column(String(20))
    title: Mapped[str] = mapped_column(String(300))
    body: Mapped[str | None] = mapped_column(Text)  # متن، یا نشانی لینک
    media_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("media_files.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    course: Mapped[Course] = relationship(back_populates="items")
    media: Mapped[MediaFile | None] = relationship(lazy="joined")


class ContentView(Base):
    """هر بار باز کردن یک محتوا یک ردیف است؛ مدت و درصد تماشا را اپ به‌روز می‌کند."""

    __tablename__ = "content_views"
    id: Mapped[uuid.UUID] = pk()
    item_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("course_items.id", ondelete="CASCADE"), index=True)
    course_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("courses.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    seconds: Mapped[int] = mapped_column(Integer, default=0)
    progress: Mapped[int] = mapped_column(Integer, default=0)  # درصد، برای فیلم و صوت
    completed: Mapped[bool] = mapped_column(Boolean, default=False)


class Exam(AudienceMixin, TimestampMixin, Base):
    __tablename__ = "exams"
    id: Mapped[uuid.UUID] = pk()
    course_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("courses.id", ondelete="SET NULL"), index=True
    )
    title: Mapped[str] = mapped_column(String(300))
    description: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(20), default="draft")  # draft | published
    # auto: طبق opens_at/closes_at؛ open/closed: باز یا بسته به دست مسئول آموزش
    manual_state: Mapped[str] = mapped_column(String(10), default="auto")
    opens_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    closes_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    time_limit_minutes: Mapped[int | None] = mapped_column(Integer)
    max_attempts: Mapped[int] = mapped_column(Integer, default=1)
    pass_score: Mapped[int] = mapped_column(Integer, default=60)  # درصد
    shuffle_questions: Mapped[bool] = mapped_column(Boolean, default=True)
    shuffle_options: Mapped[bool] = mapped_column(Boolean, default=True)
    show_answers: Mapped[str] = mapped_column(
        String(20), default="after_close"
    )  # never|after_submit|after_close
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))

    questions: Mapped[list["ExamQuestion"]] = relationship(
        order_by="ExamQuestion.sort_order", cascade="all, delete-orphan"
    )


class ExamQuestion(Base):
    __tablename__ = "exam_questions"
    id: Mapped[uuid.UUID] = pk()
    exam_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("exams.id", ondelete="CASCADE"), index=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)
    kind: Mapped[str] = mapped_column(String(20))  # single | multiple | true_false
    text: Mapped[str] = mapped_column(Text)
    # [{"id": "a", "text": "...", "correct": true}]
    options: Mapped[list[dict]] = mapped_column(JSONB, default=list)
    points: Mapped[int] = mapped_column(Integer, default=1)
    explanation: Mapped[str | None] = mapped_column(Text)


class ExamAttempt(Base):
    __tablename__ = "exam_attempts"
    id: Mapped[uuid.UUID] = pk()
    exam_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("exams.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    deadline_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    submitted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    # ترتیب سؤال‌ها و گزینه‌ها برای همین شرکت‌کننده: [{"q": id, "o": [option ids]}]
    layout: Mapped[list[dict]] = mapped_column(JSONB, default=list)
    answers: Mapped[dict] = mapped_column(JSONB, default=dict)  # {question_id: [option ids]}
    points: Mapped[float | None] = mapped_column(Float)
    max_points: Mapped[float | None] = mapped_column(Float)
    score: Mapped[float | None] = mapped_column(Float)  # درصد
    passed: Mapped[bool | None] = mapped_column(Boolean)


# ---------------------------------------------------------------- درخواست‌های پشتیبانی


class RequestType(TimestampMixin, Base):
    """نوع درخواست (مثلاً «خرابی پرینتر») با واحد مسئول پیش‌فرض و فیلدهای اختیاری."""

    __tablename__ = "request_types"
    id: Mapped[uuid.UUID] = pk()
    name: Mapped[str] = mapped_column(String(200))
    category: Mapped[str] = mapped_column(String(20))  # it | equipment | facilities | other
    description: Mapped[str | None] = mapped_column(Text)
    handler_unit_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("units.id"))
    # [{"id","label","kind": text|number|select,"options": [...],"required": bool}]
    fields: Mapped[list] = mapped_column(JSONB, default=list)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)


class ServiceRequest(TimestampMixin, Base):
    __tablename__ = "service_requests"
    id: Mapped[uuid.UUID] = pk()
    tracking_code: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    client_uuid: Mapped[uuid.UUID] = mapped_column(unique=True)
    type_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("request_types.id"), index=True)
    requester_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id"), index=True)
    # واحد درخواست‌کننده هنگام ثبت (برای مسئول واحد و آمار)
    requester_unit_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id"), index=True)
    # واحد پشتیبانی که الان مسئول است (با ارجاع عوض می‌شود)
    handler_unit_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("units.id"), index=True)
    assigned_to_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"), index=True)
    description: Mapped[str] = mapped_column(Text)
    location: Mapped[str | None] = mapped_column(String(300))
    answers: Mapped[dict] = mapped_column(JSONB, default=dict)
    priority: Mapped[str] = mapped_column(
        String(20), default="normal", index=True
    )  # normal | urgent | critical
    status: Mapped[str] = mapped_column(String(20), default="submitted", index=True)
    resolution: Mapped[str | None] = mapped_column(Text)
    accepted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    done_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    closed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    satisfaction: Mapped[int | None] = mapped_column(Integer)  # ۱ تا ۵
    satisfaction_comment: Mapped[str | None] = mapped_column(Text)

    events: Mapped[list["ServiceRequestEvent"]] = relationship(
        order_by="ServiceRequestEvent.at", cascade="all, delete-orphan"
    )


class ServiceRequestEvent(Base):
    """تاریخچه و گفتگوی درخواست: ثبت، تغییر وضعیت، ارجاع، یادداشت، پیوست، امتیاز."""

    __tablename__ = "service_request_events"
    id: Mapped[uuid.UUID] = pk()
    request_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("service_requests.id", ondelete="CASCADE"), index=True
    )
    at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    actor_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    kind: Mapped[str] = mapped_column(String(20))
    from_status: Mapped[str | None] = mapped_column(String(20))
    to_status: Mapped[str | None] = mapped_column(String(20))
    note: Mapped[str | None] = mapped_column(Text)
    media_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("media_files.id"))

    media: Mapped[MediaFile | None] = relationship(lazy="joined")


# ---------------------------------------------------------------- اعلان‌ها


class Notification(Base):
    """اعلان درون‌برنامه‌ای یک کاربر. created_at آینده یعنی اعلان زمان‌دار که تا آن لحظه دیده نمی‌شود."""

    __tablename__ = "notifications"
    id: Mapped[uuid.UUID] = pk()
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    kind: Mapped[str] = mapped_column(String(40))
    title: Mapped[str] = mapped_column(String(300))
    body: Mapped[str | None] = mapped_column(String(500))
    # request | incident | announcement | course | exam | chat | schedule
    link_type: Mapped[str | None] = mapped_column(String(20))
    link_id: Mapped[uuid.UUID | None] = mapped_column(index=True)
    important: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    read_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (Index("ix_notifications_user_created", "user_id", "created_at"),)


# ---------------------------------------------------------------- گفتگوی داخلی


class ChatConversation(Base):
    """گفتگوی دونفره (direct)، گروه (group) یا کانال خودکار یک واحد (unit)."""

    __tablename__ = "chat_conversations"
    id: Mapped[uuid.UUID] = pk()
    kind: Mapped[str] = mapped_column(String(10))  # direct | group | unit
    title: Mapped[str | None] = mapped_column(String(200))  # فقط گروه
    unit_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id", ondelete="CASCADE"), unique=True)
    # شناسه دو طرف به ترتیب؛ از ساخته شدن دو گفتگوی دونفره میان دو نفر جلوگیری می‌کند.
    direct_key: Mapped[str | None] = mapped_column(String(80), unique=True)
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    last_message_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), index=True)


class ChatMember(Base):
    __tablename__ = "chat_members"
    __table_args__ = (UniqueConstraint("conversation_id", "user_id"),)
    id: Mapped[uuid.UUID] = pk()
    conversation_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("chat_conversations.id", ondelete="CASCADE"), index=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    is_admin: Mapped[bool] = mapped_column(Boolean, default=False)  # مدیر گروه
    muted: Mapped[bool] = mapped_column(Boolean, default=False)
    joined_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    last_read_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class ChatMessage(Base):
    """متن پیام فقط برای اعضای گفتگو برگردانده می‌شود و در هیچ لاگی نوشته نمی‌شود."""

    __tablename__ = "chat_messages"
    __table_args__ = (
        Index("ix_chat_messages_conv_created", "conversation_id", "created_at"),
        Index("ix_chat_messages_conv_updated", "conversation_id", "updated_at"),
    )
    id: Mapped[uuid.UUID] = pk()
    conversation_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("chat_conversations.id", ondelete="CASCADE")
    )
    sender_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    # شناسه‌ای که اپ می‌سازد تا ارسال دوباره از صف آفلاین پیام تکراری نسازد.
    client_uuid: Mapped[uuid.UUID] = mapped_column(unique=True)
    body: Mapped[str | None] = mapped_column(Text)
    media_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("media_files.id"))
    reply_to_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("chat_messages.id", ondelete="SET NULL"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    # با هر ویرایش یا حذف جلو می‌رود تا اپ تغییرها را هم با پرسش دوره‌ای بگیرد.
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    edited_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    media: Mapped[MediaFile | None] = relationship(lazy="joined")


# ---------------------------------------------------------------- برنامه شیفت بخش‌ها


class ShiftType(Base):
    """کد شیفت بیمارستان (همان شماره فهرست رسمی). کد قدیمی غیرفعال است و replaced_by جایگزینش را می‌گوید."""

    __tablename__ = "shift_types"
    code: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)
    title: Mapped[str] = mapped_column(String(300))
    label: Mapped[str] = mapped_column(String(30))  # برچسب کوتاه جدول، مثلاً «ص ع ش»
    kind: Mapped[str] = mapped_column(String(10))  # work | off | leave
    group: Mapped[str] = mapped_column(String(20))  # general | nursing | physician | ...
    # [{"start": "07:30", "start_day": 0, "end": "08:00", "end_day": 1, "oncall": false}]
    segments: Mapped[list] = mapped_column(JSONB, default=list)
    hours: Mapped[float | None] = mapped_column(Float)  # ساعت مصوب، اگر با بازه‌ها فرق دارد
    productivity: Mapped[bool] = mapped_column(Boolean, default=False)  # بهره‌وری
    overtime: Mapped[bool | None] = mapped_column(Boolean)  # با یا بدون اضافه‌کار
    weekday: Mapped[int | None] = mapped_column(Integer)  # فقط یک روز هفته؛ date.weekday()
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    replaced_by: Mapped[int | None] = mapped_column(Integer)
    review_note: Mapped[str | None] = mapped_column(Text)  # ابهامی که باید بازبینی شود
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)


class ScheduleUnitSettings(Base):
    """تنظیمات برنامه یک واحد؛ اگر ردیفی نباشد مقدارهای پیش‌فرض به کار می‌روند."""

    __tablename__ = "schedule_unit_settings"
    unit_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("units.id", ondelete="CASCADE"), primary_key=True)
    shift_groups: Mapped[list[str]] = mapped_column(JSONB, default=list)
    min_staff: Mapped[dict] = mapped_column(JSONB, default=dict)  # {"morning": 3, "evening": 2, "night": 2}
    max_consecutive: Mapped[int] = mapped_column(Integer, default=6)
    min_rest_hours: Mapped[int] = mapped_column(Integer, default=8)
    required_monthly_hours: Mapped[int | None] = mapped_column(Integer)
    max_monthly_hours: Mapped[int | None] = mapped_column(Integer)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)


class Holiday(Base):
    """تعطیل رسمی (جمعه‌ها خودکار تعطیل حساب می‌شوند)."""

    __tablename__ = "holidays"
    day: Mapped[date] = mapped_column(Date, primary_key=True)
    title: Mapped[str] = mapped_column(String(200))


class Schedule(TimestampMixin, Base):
    """برنامه ماهانه یک واحد: draft → submitted → published (یا برگشت به draft)."""

    __tablename__ = "schedules"
    __table_args__ = (UniqueConstraint("unit_id", "jyear", "jmonth"),)
    id: Mapped[uuid.UUID] = pk()
    unit_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("units.id", ondelete="CASCADE"), index=True)
    jyear: Mapped[int] = mapped_column(Integer)
    jmonth: Mapped[int] = mapped_column(Integer)
    status: Mapped[str] = mapped_column(String(12), default="draft", index=True)
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    submitted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    submitted_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    reviewed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    reviewed_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    return_note: Mapped[str | None] = mapped_column(String(500))
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class ScheduleMember(Base):
    __tablename__ = "schedule_members"
    __table_args__ = (UniqueConstraint("schedule_id", "user_id"),)
    id: Mapped[uuid.UUID] = pk()
    schedule_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("schedules.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    is_guest: Mapped[bool] = mapped_column(Boolean, default=False)  # نیروی قرضی از واحد دیگر
    sort_order: Mapped[int] = mapped_column(Integer, default=0)


class ScheduleEntry(Base):
    """کد شیفت یک نفر در یک روز (تاریخ میلادی)."""

    __tablename__ = "schedule_entries"
    __table_args__ = (
        UniqueConstraint("schedule_id", "user_id", "day"),
        Index("ix_schedule_entries_user_day", "user_id", "day"),
    )
    id: Mapped[uuid.UUID] = pk()
    schedule_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("schedules.id", ondelete="CASCADE"))
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    day: Mapped[date] = mapped_column(Date, index=True)
    code: Mapped[int] = mapped_column(ForeignKey("shift_types.code"))


class ScheduleChange(Base):
    """تغییر خانه‌ها پس از انتشار برنامه."""

    __tablename__ = "schedule_changes"
    id: Mapped[uuid.UUID] = pk()
    schedule_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("schedules.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    day: Mapped[date] = mapped_column(Date)
    old_code: Mapped[int | None] = mapped_column(Integer)
    new_code: Mapped[int | None] = mapped_column(Integer)
    reason: Mapped[str] = mapped_column(String(300))
    changed_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class ShiftSwap(Base):
    """جابجایی خانه‌های دو همکار در دو روز: pending_partner → pending_manager → approved، یا rejected/cancelled."""

    __tablename__ = "shift_swaps"
    id: Mapped[uuid.UUID] = pk()
    schedule_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("schedules.id", ondelete="CASCADE"), index=True)
    requester_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    partner_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    requester_day: Mapped[date] = mapped_column(Date)
    partner_day: Mapped[date] = mapped_column(Date)
    # کدهای دو نفر در دو روز هنگام درخواست؛ اگر تا تأیید عوض شوند، جابجایی کهنه است.
    snapshot: Mapped[dict] = mapped_column(JSONB, default=dict)
    note: Mapped[str | None] = mapped_column(String(300))
    status: Mapped[str] = mapped_column(String(20), default="pending_partner", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    partner_answered_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    decided_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    decided_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    decision_note: Mapped[str | None] = mapped_column(String(300))


# ---------------------------------------------------------------- مراجعان


class Patient(TimestampMixin, Base):
    """مراجع (بیمار یا همراه) که با موبایل و کد پیامکی وارد می‌شود؛ جدا از حساب کارکنان."""

    __tablename__ = "patients"
    id: Mapped[uuid.UUID] = pk()
    mobile: Mapped[str] = mapped_column(String(20), unique=True, index=True)  # E.164، مثل +989121234567
    language: Mapped[str] = mapped_column(String(5), default="fa")  # fa | az | en | ar | tr
    full_name: Mapped[str | None] = mapped_column(String(200))
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    last_login_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    # باشگاه مراجعان: کد معرفی خود مراجع (در اولین نیاز ساخته می‌شود) و معرف او
    referral_code: Mapped[str | None] = mapped_column(String(12), unique=True)
    referred_by_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("patients.id", ondelete="SET NULL"))
    referral_rewarded: Mapped[bool] = mapped_column(Boolean, default=False)


class PatientRefreshToken(Base):
    __tablename__ = "patient_refresh_tokens"
    id: Mapped[uuid.UUID] = pk()
    patient_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("patients.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    family_id: Mapped[uuid.UUID] = mapped_column(index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    device: Mapped[str | None] = mapped_column(String(200))


class OtpCode(Base):
    """کد یک‌بارمصرف پیامکی؛ خود کد ذخیره نمی‌شود، فقط HMAC آن."""

    __tablename__ = "otp_codes"
    id: Mapped[uuid.UUID] = pk()
    mobile: Mapped[str] = mapped_column(String(20), index=True)
    code_hash: Mapped[str] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    consumed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


# ---------------------------------------------------------------- ترجمه محتوای عمومی


class ContentTranslation(Base):
    """ترجمه یک فیلد از محتوای عمومی (بخش، پرسش، اطلاعیه) به یکی از زبان‌های مراجعان.

    فقط ترجمه تأییدشده‌ای به مراجع نشان داده می‌شود که متن فارسی‌اش از زمان ترجمه تغییر نکرده باشد
    (source_hash همان هش متن فارسی هنگام ذخیره است).
    """

    __tablename__ = "content_translations"
    __table_args__ = (UniqueConstraint("entity_type", "entity_id", "language", "field"),)
    id: Mapped[uuid.UUID] = pk()
    entity_type: Mapped[str] = mapped_column(String(20))  # unit | faq | announcement
    entity_id: Mapped[uuid.UUID] = mapped_column(index=True)
    language: Mapped[str] = mapped_column(String(5))  # az | en | ar | tr
    field: Mapped[str] = mapped_column(String(30))
    text: Mapped[str] = mapped_column(Text)
    source_hash: Mapped[str] = mapped_column(String(64))
    status: Mapped[str] = mapped_column(String(10), default="draft")  # draft | approved
    updated_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)
    approved_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    approved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


# ---------------------------------------------------------------- پزشکان و نوبت‌دهی


class Specialty(TimestampMixin, Base):
    """گروه تخصصی (مثلاً قلب و عروق) که مراجع پزشکان را با آن پیدا می‌کند."""

    __tablename__ = "specialties"
    id: Mapped[uuid.UUID] = pk()
    name: Mapped[str] = mapped_column(String(200), unique=True)
    description: Mapped[str | None] = mapped_column(Text)
    # کلید آیکون تصویری در اپ و پنل (heart, bone, brain, ...)
    icon: Mapped[str] = mapped_column(String(40), default="stethoscope")
    sort_order: Mapped[int] = mapped_column(Integer, default=0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)


class Doctor(TimestampMixin, Base):
    """پزشک معرفی‌شده به مراجعان. user_id حساب کارکنان خود پزشک است تا فهرست بیمارانش را ببیند."""

    __tablename__ = "doctors"
    id: Mapped[uuid.UUID] = pk()
    full_name: Mapped[str] = mapped_column(String(200))
    specialty_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("specialties.id"), index=True)
    title: Mapped[str | None] = mapped_column(String(200))  # مثلاً «فوق تخصص قلب و عروق»
    bio: Mapped[str | None] = mapped_column(Text)
    medical_code: Mapped[str | None] = mapped_column(String(30))  # شماره نظام پزشکی
    clinic_unit_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("units.id", ondelete="SET NULL"))
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), unique=True
    )
    photo_media_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("media_files.id", ondelete="SET NULL")
    )
    visit_minutes: Mapped[int] = mapped_column(Integer, default=15)
    is_featured: Mapped[bool] = mapped_column(Boolean, default=False)
    booking_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)

    specialty: Mapped[Specialty] = relationship(lazy="joined")
    secretaries: Mapped[list["DoctorSecretary"]] = relationship(cascade="all, delete-orphan")

    @property
    def has_photo(self) -> bool:
        return self.photo_media_id is not None


class DoctorSecretary(Base):
    """منشی (کاربر کارکنان) که نوبت‌های این پزشک را تعریف و جابجا می‌کند."""

    __tablename__ = "doctor_secretaries"
    __table_args__ = (UniqueConstraint("doctor_id", "user_id"),)
    id: Mapped[uuid.UUID] = pk()
    doctor_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("doctors.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)


class AppointmentSlot(Base):
    """یک وقت ویزیت. capacity بیش از یک برای نوبت‌های گروهی/بدون ساعت دقیق."""

    __tablename__ = "appointment_slots"
    __table_args__ = (UniqueConstraint("doctor_id", "starts_at"),)
    id: Mapped[uuid.UUID] = pk()
    doctor_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("doctors.id", ondelete="CASCADE"), index=True)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    minutes: Mapped[int] = mapped_column(Integer)
    capacity: Mapped[int] = mapped_column(Integer, default=1)
    is_blocked: Mapped[bool] = mapped_column(Boolean, default=False)
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))


class Appointment(TimestampMixin, Base):
    """نوبت یک مراجع. نام و موبایل همین‌جا ذخیره می‌شود تا منشی بتواند برای مراجع بدون اپ هم نوبت بدهد."""

    __tablename__ = "appointments"
    id: Mapped[uuid.UUID] = pk()
    slot_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("appointment_slots.id"), index=True)
    doctor_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("doctors.id"), index=True)
    patient_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("patients.id", ondelete="SET NULL"), index=True
    )
    patient_name: Mapped[str] = mapped_column(String(200))
    patient_mobile: Mapped[str] = mapped_column(String(20))
    note: Mapped[str | None] = mapped_column(String(500))
    # booked | attended | no_show | cancelled
    status: Mapped[str] = mapped_column(String(20), default="booked", index=True)
    tracking_code: Mapped[str] = mapped_column(String(12), unique=True)
    source: Mapped[str] = mapped_column(String(20), default="app")  # app | secretary
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    cancelled_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    cancelled_by: Mapped[str | None] = mapped_column(String(20))  # patient | staff
    reminder_sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    slot: Mapped[AppointmentSlot] = relationship(lazy="joined")
    doctor: Mapped[Doctor] = relationship(lazy="joined")


class DoctorReview(TimestampMixin, Base):
    """امتیاز و نظر مراجع پس از ویزیت (یک نظر برای هر نوبت).

    ستاره بلافاصله در امتیاز پزشک حساب می‌شود؛ متن نظر پس از تأیید مدیر پزشکان منتشر می‌شود.
    پاسخ پزشک و پاسخ مدیر سیستم همراه نظر نگه داشته می‌شود. مراجع در خروجی عمومی نام‌برده نمی‌شود.
    """

    __tablename__ = "doctor_reviews"
    id: Mapped[uuid.UUID] = pk()
    appointment_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("appointments.id", ondelete="CASCADE"), unique=True
    )
    doctor_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("doctors.id", ondelete="CASCADE"), index=True)
    patient_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("patients.id", ondelete="SET NULL"))
    stars: Mapped[int] = mapped_column(Integer)
    comment: Mapped[str | None] = mapped_column(String(1500))
    # pending | approved | rejected (بدون متن نظر: approved)
    status: Mapped[str] = mapped_column(String(20), default="pending", index=True)
    moderated_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    moderated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    doctor_reply: Mapped[str | None] = mapped_column(String(1500))
    doctor_reply_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    admin_reply: Mapped[str | None] = mapped_column(String(1500))
    admin_reply_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    admin_reply_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))

    appointment: Mapped[Appointment] = relationship(lazy="joined")
    doctor: Mapped[Doctor] = relationship(lazy="joined")


class TokenTransaction(TimestampMixin, Base):
    """دفتر ژتون مراجع؛ موجودی مجموع مبلغ‌هاست (مثبت: دریافت، منفی: مصرف)."""

    __tablename__ = "token_transactions"
    id: Mapped[uuid.UUID] = pk()
    patient_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("patients.id", ondelete="CASCADE"), index=True)
    amount: Mapped[int] = mapped_column(Integer)
    # referral | referred_bonus | admin | spend | refund
    kind: Mapped[str] = mapped_column(String(30))
    note: Mapped[str | None] = mapped_column(String(300))
    actor_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))


class TokenReward(TimestampMixin, Base):
    """امتیاز قابل دریافت با ژتون (مثلاً تخفیف پذیرش) که بیمارستان تعریف می‌کند."""

    __tablename__ = "token_rewards"
    id: Mapped[uuid.UUID] = pk()
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(String(500))
    cost: Mapped[int] = mapped_column(Integer)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)


class TokenRedemption(TimestampMixin, Base):
    """کد استفاده‌ای که مراجع با خرج ژتون می‌گیرد و در پذیرش یا نوبت‌دهی نشان می‌دهد."""

    __tablename__ = "token_redemptions"
    id: Mapped[uuid.UUID] = pk()
    patient_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("patients.id", ondelete="CASCADE"), index=True)
    reward_title: Mapped[str] = mapped_column(String(200))
    cost: Mapped[int] = mapped_column(Integer)
    code: Mapped[str] = mapped_column(String(12), unique=True)
    # issued | used | cancelled
    status: Mapped[str] = mapped_column(String(20), default="issued", index=True)
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    used_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))


class EducationItem(TimestampMixin, Base):
    """محتوای آموزش به بیمار: مقاله، تصویر، ویدئو، صوت یا PDF با دسته‌بندی بیماری، تخصص و نوع مراقبت.

    متن اصلی فارسی است؛ ترجمه‌ها در ContentTranslation (entity_type = education) می‌مانند.
    """

    __tablename__ = "education_items"
    id: Mapped[uuid.UUID] = pk()
    title: Mapped[str] = mapped_column(String(300))
    summary: Mapped[str | None] = mapped_column(Text)
    body: Mapped[str | None] = mapped_column(Text)  # متن مقاله
    kind: Mapped[str] = mapped_column(String(10), index=True)  # article | image | video | audio | pdf
    condition: Mapped[str | None] = mapped_column(String(150), index=True)  # بیماری یا موضوع، مثلاً «دیابت»
    care_type: Mapped[str] = mapped_column(String(20), default="other", index=True)
    specialty_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("specialties.id", ondelete="SET NULL"), index=True
    )
    media_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("media_files.id", ondelete="SET NULL"))
    external_url: Mapped[str | None] = mapped_column(String(500))  # پیوند ویدئو یا فایل بیرونی
    source: Mapped[str | None] = mapped_column(Text)  # منبع علمی
    reviewed_on: Mapped[date | None] = mapped_column(Date)  # تاریخ آخرین بازبینی علمی
    is_published: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    created_by: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
