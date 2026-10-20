import uuid
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator

ItemKind = Literal["text", "image", "video", "audio", "pdf", "pptx", "file", "link"]
CourseStatus = Literal["draft", "published", "archived"]
QuestionKind = Literal["single", "multiple", "true_false"]
ManualState = Literal["auto", "open", "closed"]
ShowAnswers = Literal["never", "after_submit", "after_close"]
ExamState = Literal["draft", "upcoming", "open", "closed"]


class Audience(BaseModel):
    target_unit_ids: list[uuid.UUID] = []
    target_role_codes: list[str] = []
    target_job_titles: list[str] = []
    target_user_ids: list[uuid.UUID] = []


class MediaOut(BaseModel):
    id: uuid.UUID
    original_name: str
    content_type: str
    size: int
    url: str


# ---------------------------------------------------------------- دوره


class CourseIn(Audience):
    title: str = Field(min_length=1, max_length=300)
    description: str | None = None
    status: CourseStatus = "draft"
    is_mandatory: bool = False
    due_date: date | None = None
    cover_media_id: uuid.UUID | None = None


class CourseUpdateIn(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=300)
    description: str | None = None
    status: CourseStatus | None = None
    is_mandatory: bool | None = None
    due_date: date | None = None
    cover_media_id: uuid.UUID | None = None
    target_unit_ids: list[uuid.UUID] | None = None
    target_role_codes: list[str] | None = None
    target_job_titles: list[str] | None = None
    target_user_ids: list[uuid.UUID] | None = None


class ItemIn(BaseModel):
    kind: ItemKind
    title: str = Field(min_length=1, max_length=300)
    body: str | None = None
    media_id: uuid.UUID | None = None

    @model_validator(mode="after")
    def _check(self):
        if self.kind == "text" and not (self.body or "").strip():
            raise ValueError("متن محتوا خالی است")
        if self.kind == "link" and not (self.body or "").startswith(("https://", "http://")):
            raise ValueError("نشانی لینک باید با http:// یا https:// شروع شود")
        if self.kind not in ("text", "link") and self.media_id is None:
            raise ValueError("برای این نوع محتوا فایل لازم است")
        return self


class ItemUpdateIn(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=300)
    body: str | None = None
    media_id: uuid.UUID | None = None


class ItemOut(BaseModel):
    id: uuid.UUID
    kind: ItemKind
    title: str
    body: str | None
    media: MediaOut | None
    sort_order: int
    # فقط در نمای کارکنان
    viewed: bool = False
    completed: bool = False


class CourseOut(Audience):
    id: uuid.UUID
    title: str
    description: str | None
    status: CourseStatus
    is_mandatory: bool
    due_date: date | None
    cover_url: str | None
    created_at: datetime
    item_count: int
    # فقط در نمای کارکنان
    completed_count: int = 0
    # فقط در نمای مسئول آموزش
    audience_size: int | None = None


class CourseDetailOut(CourseOut):
    items: list[ItemOut]
    exams: list["ExamOut"] = []


class ViewStartOut(BaseModel):
    view_id: uuid.UUID


class ViewProgressIn(BaseModel):
    seconds: int = Field(ge=0, le=24 * 3600)
    progress: int = Field(ge=0, le=100)


class OrderIn(BaseModel):
    ids: list[uuid.UUID]


# ---------------------------------------------------------------- آزمون


class OptionIn(BaseModel):
    text: str = Field(min_length=1, max_length=1000)
    correct: bool = False


class QuestionIn(BaseModel):
    kind: QuestionKind
    text: str = Field(min_length=1, max_length=4000)
    options: list[OptionIn] = []
    points: int = Field(default=1, ge=1, le=100)
    explanation: str | None = None

    @model_validator(mode="after")
    def _check(self):
        if self.kind == "true_false":
            if len(self.options) != 2:
                # پیش‌فرض: «درست» و «غلط»؛ پاسخ درست از گزینه‌ای که correct دارد
                raise ValueError("سؤال درست/غلط باید دقیقاً دو گزینه داشته باشد")
        if len(self.options) < 2:
            raise ValueError("هر سؤال دست‌کم دو گزینه لازم دارد")
        if len(self.options) > 10:
            raise ValueError("حداکثر ۱۰ گزینه مجاز است")
        n = sum(o.correct for o in self.options)
        if n == 0:
            raise ValueError("دست‌کم یک گزینه باید درست باشد")
        if self.kind in ("single", "true_false") and n != 1:
            raise ValueError("در سؤال تک‌گزینه‌ای فقط یک گزینه درست است")
        return self


class QuestionOut(BaseModel):
    id: uuid.UUID
    kind: QuestionKind
    text: str
    options: list[dict]
    points: int
    explanation: str | None


class ExamIn(Audience):
    title: str = Field(min_length=1, max_length=300)
    description: str | None = None
    course_id: uuid.UUID | None = None
    status: Literal["draft", "published"] = "draft"
    manual_state: ManualState = "auto"
    opens_at: datetime | None = None
    closes_at: datetime | None = None
    time_limit_minutes: int | None = Field(default=None, ge=1, le=600)
    max_attempts: int = Field(default=1, ge=1, le=20)
    pass_score: int = Field(default=60, ge=0, le=100)
    shuffle_questions: bool = True
    shuffle_options: bool = True
    show_answers: ShowAnswers = "after_close"

    @model_validator(mode="after")
    def _dates(self):
        if self.opens_at and self.closes_at and self.closes_at <= self.opens_at:
            raise ValueError("زمان بسته شدن باید بعد از زمان باز شدن باشد")
        return self


class ExamUpdateIn(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=300)
    description: str | None = None
    course_id: uuid.UUID | None = None
    status: Literal["draft", "published"] | None = None
    manual_state: ManualState | None = None
    opens_at: datetime | None = None
    closes_at: datetime | None = None
    time_limit_minutes: int | None = Field(default=None, ge=1, le=600)
    max_attempts: int | None = Field(default=None, ge=1, le=20)
    pass_score: int | None = Field(default=None, ge=0, le=100)
    shuffle_questions: bool | None = None
    shuffle_options: bool | None = None
    show_answers: ShowAnswers | None = None
    target_unit_ids: list[uuid.UUID] | None = None
    target_role_codes: list[str] | None = None
    target_job_titles: list[str] | None = None
    target_user_ids: list[uuid.UUID] | None = None


class ExamOut(Audience):
    id: uuid.UUID
    title: str
    description: str | None
    course_id: uuid.UUID | None
    status: Literal["draft", "published"]
    manual_state: ManualState
    state: ExamState
    opens_at: datetime | None
    closes_at: datetime | None
    time_limit_minutes: int | None
    max_attempts: int
    pass_score: int
    shuffle_questions: bool
    shuffle_options: bool
    show_answers: ShowAnswers
    question_count: int
    total_points: int
    # فقط در نمای کارکنان
    attempts_used: int = 0
    best_score: float | None = None
    passed: bool | None = None
    in_progress_attempt_id: uuid.UUID | None = None
    # برای دیدن دوباره نتیجه (و پاسخ‌ها پس از بسته شدن آزمون)
    last_attempt_id: uuid.UUID | None = None
    # فقط در نمای مسئول آموزش
    audience_size: int | None = None
    attempt_count: int | None = None


class ExamDetailOut(ExamOut):
    questions: list[QuestionOut]


class AttemptQuestionOut(BaseModel):
    id: uuid.UUID
    kind: QuestionKind
    text: str
    points: int
    options: list[dict]  # [{"id","text"}] و پس از نمایش پاسخ‌ها: correct
    selected: list[str] = []
    correct: bool | None = None
    explanation: str | None = None


class AttemptOut(BaseModel):
    id: uuid.UUID
    exam_id: uuid.UUID
    exam_title: str
    started_at: datetime
    deadline_at: datetime | None
    submitted_at: datetime | None
    server_time: datetime
    finished: bool
    score: float | None = None
    passed: bool | None = None
    pass_score: int
    answers_visible: bool = False
    questions: list[AttemptQuestionOut]


class AnswersIn(BaseModel):
    answers: dict[uuid.UUID, list[str]] = {}


# ---------------------------------------------------------------- گزارش‌ها


class PersonRef(BaseModel):
    user_id: uuid.UUID
    personnel_code: str
    full_name: str
    unit_name: str | None
    job_title: str | None
    roles: list[str]


class Breakdown(BaseModel):
    """آمار تفکیکی (بر اساس واحد، سمت یا نقش)."""

    label: str
    audience: int
    participated: int
    completed: int


class ItemStat(BaseModel):
    item_id: uuid.UUID
    title: str
    kind: str
    viewers: int
    views: int
    completed: int
    total_seconds: int


class CourseLearnerRow(PersonRef):
    in_audience: bool
    views: int
    items_viewed: int
    items_completed: int
    total_seconds: int
    last_view_at: datetime | None
    completed: bool


class CourseReportOut(BaseModel):
    course_id: uuid.UUID
    title: str
    item_count: int
    audience_size: int
    participants: int
    completed: int
    total_views: int
    items: list[ItemStat]
    learners: list[CourseLearnerRow]
    not_started: list[PersonRef]
    by_unit: list[Breakdown]
    by_job_title: list[Breakdown]
    by_role: list[Breakdown]


class ItemViewerRow(PersonRef):
    views: int
    total_seconds: int
    max_progress: int
    completed: bool
    first_view_at: datetime
    last_view_at: datetime


class ExamTakerRow(PersonRef):
    in_audience: bool
    attempts: int
    best_score: float | None
    last_score: float | None
    passed: bool
    last_submitted_at: datetime | None


class QuestionStat(BaseModel):
    question_id: uuid.UUID
    text: str
    answered: int
    correct: int
    correct_percent: float | None
    # تعداد انتخاب هر گزینه: {option_id: count}
    option_counts: dict[str, int]


class ExamReportOut(BaseModel):
    exam_id: uuid.UUID
    title: str
    audience_size: int
    participants: int
    passed: int
    average_score: float | None
    histogram: list[int]  # ۱۰ بازه: ۰-۱۰، ...، ۹۰-۱۰۰
    takers: list[ExamTakerRow]
    not_attempted: list[PersonRef]
    questions: list[QuestionStat]
    by_unit: list[Breakdown]
    by_job_title: list[Breakdown]


class PeopleCatalogOut(BaseModel):
    users: list[PersonRef]
    job_titles: list[str]


CourseDetailOut.model_rebuild()
