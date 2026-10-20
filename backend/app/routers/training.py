"""آموزش کارکنان: دوره و محتوا، ثبت بازدید، آزمون و گزارش‌های مسئول آموزش."""

import csv
import io
import uuid
from collections import defaultdict
from datetime import UTC, datetime, timedelta
from urllib.parse import quote

from fastapi import APIRouter, Depends, Query, Request, Response
from fastapi.responses import FileResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from app.core.audit import audit
from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, require
from app.models import (
    ContentView,
    Course,
    CourseItem,
    Exam,
    ExamAttempt,
    ExamQuestion,
    MediaFile,
    Role,
    Unit,
    User,
)
from app.schemas.training import (
    AnswersIn,
    AttemptOut,
    AttemptQuestionOut,
    Breakdown,
    CourseDetailOut,
    CourseIn,
    CourseLearnerRow,
    CourseOut,
    CourseReportOut,
    CourseUpdateIn,
    ExamDetailOut,
    ExamIn,
    ExamOut,
    ExamReportOut,
    ExamTakerRow,
    ExamUpdateIn,
    ItemIn,
    ItemOut,
    ItemStat,
    ItemUpdateIn,
    ItemViewerRow,
    MediaOut,
    OrderIn,
    PeopleCatalogOut,
    PersonRef,
    QuestionIn,
    QuestionOut,
    QuestionStat,
    ViewProgressIn,
    ViewStartOut,
)
from app.services import notifications as notes
from app.services import training as svc
from app.services.media import store_upload

router = APIRouter(tags=["training"])

READ = require("training.read")
MANAGE = require("training.manage")
SUBMIT_GRACE = timedelta(seconds=30)  # تأخیر شبکه هنگام ارسال پاسخ در آخرین لحظه
TIMED_KINDS = ("video", "audio")
COMPLETE_AT = 90  # درصد تماشای فیلم/صوت که «کامل دیده شده» حساب می‌شود


def _now() -> datetime:
    return datetime.now(UTC)


def _str_ids(ids) -> list[str]:
    return [str(i) for i in ids]


# ---------------------------------------------------------------- خروجی‌ها


def _media_out(m: MediaFile | None) -> MediaOut | None:
    if m is None:
        return None
    return MediaOut(
        id=m.id,
        original_name=m.original_name,
        content_type=m.content_type,
        size=m.size,
        url=svc.sign_media(m.id),
    )


def _audience_fields(t) -> dict:
    return {
        "target_unit_ids": t.target_unit_ids or [],
        "target_role_codes": t.target_role_codes or [],
        "target_job_titles": t.target_job_titles or [],
        "target_user_ids": t.target_user_ids or [],
    }


def _course_out(c: Course, cls=CourseOut, **extra) -> CourseOut:
    return cls(
        id=c.id,
        title=c.title,
        description=c.description,
        status=c.status,
        is_mandatory=c.is_mandatory,
        due_date=c.due_date,
        cover_url=svc.sign_media(c.cover_media_id) if c.cover_media_id else None,
        created_at=c.created_at,
        item_count=len(c.items),
        **_audience_fields(c),
        **extra,
    )


def _item_out(i: CourseItem, viewed: bool = False, completed: bool = False) -> ItemOut:
    return ItemOut(
        id=i.id,
        kind=i.kind,
        title=i.title,
        body=i.body,
        media=_media_out(i.media),
        sort_order=i.sort_order,
        viewed=viewed,
        completed=completed,
    )


def _exam_out(e: Exam, cls=ExamOut, **extra) -> ExamOut:
    return cls(
        id=e.id,
        title=e.title,
        description=e.description,
        course_id=e.course_id,
        status=e.status,
        manual_state=e.manual_state,
        state=svc.exam_state(e),
        opens_at=e.opens_at,
        closes_at=e.closes_at,
        time_limit_minutes=e.time_limit_minutes,
        max_attempts=e.max_attempts,
        pass_score=e.pass_score,
        shuffle_questions=e.shuffle_questions,
        shuffle_options=e.shuffle_options,
        show_answers=e.show_answers,
        question_count=len(e.questions),
        total_points=sum(q.points for q in e.questions),
        **_audience_fields(e),
        **extra,
    )


def _question_out(q: ExamQuestion) -> QuestionOut:
    return QuestionOut(
        id=q.id, kind=q.kind, text=q.text, options=q.options, points=q.points, explanation=q.explanation
    )


def _apply_audience(target, body) -> None:
    for field in ("target_unit_ids", "target_user_ids"):
        value = getattr(body, field)
        if value is not None:
            setattr(target, field, _str_ids(value))
    for field in ("target_role_codes", "target_job_titles"):
        value = getattr(body, field)
        if value is not None:
            setattr(target, field, sorted({v.strip() for v in value if v.strip()}))


# ---------------------------------------------------------------- دسترسی کارکنان


def _course_for_learner(db: Session, course_id: uuid.UUID, cu: CurrentUser) -> Course:
    c = db.get(Course, course_id, options=[selectinload(Course.items)])
    if c is None or c.status != "published" or not svc.in_audience(c, cu.user, cu.role_codes):
        raise api_error(404, "not_found", "دوره پیدا نشد")
    return c


def _exam_for_learner(db: Session, exam_id: uuid.UUID, cu: CurrentUser) -> Exam:
    e = db.get(Exam, exam_id, options=[selectinload(Exam.questions)])
    if e is None or e.status != "published" or not svc.in_audience(e, cu.user, cu.role_codes):
        raise api_error(404, "not_found", "آزمون پیدا نشد")
    return e


def _my_progress(db: Session, user_id: uuid.UUID, course_ids: list[uuid.UUID]):
    """{item_id: (viewed, completed)} برای این کاربر."""
    out: dict[uuid.UUID, tuple[bool, bool]] = {}
    if not course_ids:
        return out
    rows = db.execute(
        select(ContentView.item_id, func.bool_or(ContentView.completed))
        .where(ContentView.user_id == user_id, ContentView.course_id.in_(course_ids))
        .group_by(ContentView.item_id)
    )
    for item_id, completed in rows:
        out[item_id] = (True, bool(completed))
    return out


def _my_exam_fields(db: Session, e: Exam, user_id: uuid.UUID, now: datetime) -> dict:
    attempts = list(
        db.scalars(select(ExamAttempt).where(ExamAttempt.exam_id == e.id, ExamAttempt.user_id == user_id))
    )
    changed = False
    for a in attempts:
        changed |= svc.finalize_expired(e, a, now)
    if changed:
        db.commit()
    done = [a for a in attempts if a.submitted_at is not None]
    open_attempt = next((a for a in attempts if a.submitted_at is None), None)
    best = max((a.score for a in done if a.score is not None), default=None)
    last = max(done, key=lambda a: a.submitted_at, default=None)
    return {
        "attempts_used": len(attempts),
        "best_score": best,
        "passed": any(a.passed for a in done) if done else None,
        "in_progress_attempt_id": open_attempt.id if open_attempt else None,
        "last_attempt_id": last.id if last else None,
    }


@router.get("/training/courses", response_model=list[CourseOut])
def my_courses(cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)):
    courses = [
        c
        for c in db.scalars(
            select(Course).where(Course.status == "published").options(selectinload(Course.items))
        )
        if svc.in_audience(c, cu.user, cu.role_codes)
    ]
    progress = _my_progress(db, cu.id, [c.id for c in courses])
    out = [
        _course_out(c, completed_count=sum(1 for i in c.items if progress.get(i.id, (False, False))[1]))
        for c in courses
    ]
    # اجباری‌ها و نزدیک‌ترین مهلت‌ها اول
    out.sort(key=lambda c: (not c.is_mandatory, c.due_date or datetime.max.date(), -c.created_at.timestamp()))
    return out


@router.get("/training/courses/{course_id}", response_model=CourseDetailOut)
def my_course(course_id: uuid.UUID, cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)):
    c = _course_for_learner(db, course_id, cu)
    progress = _my_progress(db, cu.id, [c.id])
    now = _now()
    exams = [
        _exam_out(e, **_my_exam_fields(db, e, cu.id, now))
        for e in db.scalars(
            select(Exam)
            .where(Exam.course_id == c.id, Exam.status == "published")
            .options(selectinload(Exam.questions))
        )
        if svc.in_audience(e, cu.user, cu.role_codes)
    ]
    items = [_item_out(i, *progress.get(i.id, (False, False))) for i in c.items]
    return _course_out(
        c,
        CourseDetailOut,
        items=items,
        exams=exams,
        completed_count=sum(1 for i in items if i.completed),
    )


@router.post("/training/items/{item_id}/views", response_model=ViewStartOut, status_code=201)
def start_view(item_id: uuid.UUID, cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)):
    item = db.get(CourseItem, item_id)
    if item is None:
        raise api_error(404, "not_found", "محتوا پیدا نشد")
    _course_for_learner(db, item.course_id, cu)
    v = ContentView(
        item_id=item.id, course_id=item.course_id, user_id=cu.id, completed=item.kind not in TIMED_KINDS
    )
    db.add(v)
    db.commit()
    return ViewStartOut(view_id=v.id)


@router.patch("/training/views/{view_id}", status_code=204)
def update_view(
    view_id: uuid.UUID, body: ViewProgressIn, cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)
):
    v = db.get(ContentView, view_id)
    if v is None or v.user_id != cu.id:
        raise api_error(404, "not_found", "بازدید پیدا نشد")
    # مقدارها فقط بزرگ‌تر می‌شوند تا ارسال دیرهنگام یک به‌روزرسانی قدیمی آن را عقب نبرد.
    v.seconds = max(v.seconds, body.seconds)
    v.progress = max(v.progress, body.progress)
    if v.progress >= COMPLETE_AT:
        v.completed = True
    db.commit()


@router.get("/training/media/{media_id}", include_in_schema=False)
def get_media(
    media_id: uuid.UUID,
    exp: int = Query(...),
    sig: str = Query(..., max_length=64),
    db: Session = Depends(get_db),
):
    # پیوند امضاشده را فقط کسی می‌گیرد که به آن دوره یا محتوا دسترسی دارد.
    if not svc.check_media_signature(media_id, exp, sig):
        raise api_error(403, "link_expired", "پیوند فایل منقضی شده است؛ صفحه را دوباره باز کنید")
    m = db.get(MediaFile, media_id)
    if m is None or not svc.media_path(m).is_file():
        raise api_error(404, "not_found", "فایل پیدا نشد")
    inline = m.content_type.startswith(svc.INLINE_PREFIXES)
    disposition = "inline" if inline else "attachment"
    return FileResponse(
        svc.media_path(m),
        media_type=m.content_type,
        headers={
            "Content-Disposition": f"{disposition}; filename*=UTF-8''{quote(m.original_name)}",
            "X-Content-Type-Options": "nosniff",
            "Cache-Control": "private, max-age=3600",
        },
    )


@router.get("/training/exams", response_model=list[ExamOut])
def my_exams(cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)):
    now = _now()
    out = [
        _exam_out(e, **_my_exam_fields(db, e, cu.id, now))
        for e in db.scalars(
            select(Exam).where(Exam.status == "published").options(selectinload(Exam.questions))
        )
        if svc.in_audience(e, cu.user, cu.role_codes)
    ]
    order = {"open": 0, "upcoming": 1, "closed": 2, "draft": 3}
    out.sort(key=lambda e: (order[e.state], e.closes_at or e.opens_at or datetime.max.replace(tzinfo=UTC)))
    return out


def _attempt_out(e: Exam, a: ExamAttempt, now: datetime) -> AttemptOut:
    by_id = {str(q.id): q for q in e.questions}
    visible = svc.answers_visible(e, a, now)
    questions = []
    for entry in a.layout:
        q = by_id.get(entry["q"])
        if q is None:
            continue
        opts = {o["id"]: o for o in q.options}
        selected = a.answers.get(entry["q"], [])
        options = []
        for oid in entry["o"]:
            o = opts.get(oid)
            if o is None:
                continue
            item = {"id": oid, "text": o["text"]}
            if visible:
                item["correct"] = bool(o.get("correct"))
            options.append(item)
        questions.append(
            AttemptQuestionOut(
                id=q.id,
                kind=q.kind,
                text=q.text,
                points=q.points,
                options=options,
                selected=selected,
                correct=svc.is_correct(q, selected) if visible else None,
                explanation=q.explanation if visible else None,
            )
        )
    return AttemptOut(
        id=a.id,
        exam_id=e.id,
        exam_title=e.title,
        started_at=a.started_at,
        deadline_at=a.deadline_at,
        submitted_at=a.submitted_at,
        server_time=now,
        finished=a.submitted_at is not None,
        score=a.score,
        passed=a.passed,
        pass_score=e.pass_score,
        answers_visible=visible,
        questions=questions,
    )


@router.post("/training/exams/{exam_id}/attempts", response_model=AttemptOut, status_code=201)
def start_attempt(
    exam_id: uuid.UUID, request: Request, cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)
):
    e = _exam_for_learner(db, exam_id, cu)
    now = _now()
    attempts = list(
        db.scalars(select(ExamAttempt).where(ExamAttempt.exam_id == e.id, ExamAttempt.user_id == cu.id))
    )
    for a in attempts:
        svc.finalize_expired(e, a, now)
    current = next((a for a in attempts if a.submitted_at is None), None)
    if current is not None:
        db.commit()
        return _attempt_out(e, current, now)
    state = svc.exam_state(e, now)
    if state != "open":
        db.commit()
        message = "آزمون هنوز باز نشده است" if state == "upcoming" else "زمان آزمون به پایان رسیده است"
        raise api_error(409, "exam_not_open", message)
    if len(attempts) >= e.max_attempts:
        db.commit()
        raise api_error(409, "no_attempts_left", "تعداد دفعات مجاز شرکت در این آزمون تمام شده است")
    if not e.questions:
        raise api_error(409, "exam_empty", "این آزمون هنوز سؤالی ندارد")
    deadlines = []
    if e.time_limit_minutes:
        deadlines.append(now + timedelta(minutes=e.time_limit_minutes))
    if e.manual_state == "auto" and e.closes_at:
        deadlines.append(e.closes_at)
    a = ExamAttempt(
        exam_id=e.id,
        user_id=cu.id,
        started_at=now,
        deadline_at=min(deadlines) if deadlines else None,
        layout=svc.make_layout(e),
        answers={},
    )
    db.add(a)
    db.flush()
    audit(db, "exam.attempt_started", cu.id, "exam", e.id, client_ip(request))
    db.commit()
    return _attempt_out(e, a, now)


def _my_attempt(db: Session, attempt_id: uuid.UUID, cu: CurrentUser) -> tuple[Exam, ExamAttempt]:
    a = db.get(ExamAttempt, attempt_id)
    if a is None or a.user_id != cu.id:
        raise api_error(404, "not_found", "آزمون پیدا نشد")
    e = db.get(Exam, a.exam_id, options=[selectinload(Exam.questions)])
    return e, a


def _clean_answers(e: Exam, a: ExamAttempt, answers: dict) -> dict:
    by_id = {str(q.id): q for q in e.questions}
    allowed = {entry["q"] for entry in a.layout}
    clean = {}
    for qid, selected in answers.items():
        qid = str(qid)
        q = by_id.get(qid)
        if q is None or qid not in allowed:
            continue
        valid = {o["id"] for o in q.options}
        picked = list(dict.fromkeys(s for s in selected if s in valid))
        if q.kind != "multiple":
            picked = picked[:1]
        clean[qid] = picked
    return clean


@router.get("/training/attempts/{attempt_id}", response_model=AttemptOut)
def get_attempt(attempt_id: uuid.UUID, cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)):
    e, a = _my_attempt(db, attempt_id, cu)
    now = _now()
    if svc.finalize_expired(e, a, now):
        db.commit()
    return _attempt_out(e, a, now)


@router.put("/training/attempts/{attempt_id}/answers", response_model=AttemptOut)
def save_answers(
    attempt_id: uuid.UUID, body: AnswersIn, cu: CurrentUser = Depends(READ), db: Session = Depends(get_db)
):
    """ذخیره میانی پاسخ‌ها تا قطع شدن برنامه یا شبکه پاسخ‌ها را از بین نبرد."""
    e, a = _my_attempt(db, attempt_id, cu)
    now = _now()
    if a.submitted_at is not None:
        raise api_error(409, "attempt_finished", "این آزمون قبلاً ثبت شده است")
    if a.deadline_at is not None and now > a.deadline_at + SUBMIT_GRACE:
        svc.finalize_expired(e, a, now)
        db.commit()
        raise api_error(409, "time_over", "زمان پاسخگویی تمام شده است")
    a.answers = {**a.answers, **_clean_answers(e, a, body.answers)}
    db.commit()
    return _attempt_out(e, a, now)


@router.post("/training/attempts/{attempt_id}/submit", response_model=AttemptOut)
def submit_attempt(
    attempt_id: uuid.UUID,
    body: AnswersIn,
    request: Request,
    cu: CurrentUser = Depends(READ),
    db: Session = Depends(get_db),
):
    e, a = _my_attempt(db, attempt_id, cu)
    now = _now()
    if a.submitted_at is None:
        if a.deadline_at is not None and now > a.deadline_at + SUBMIT_GRACE:
            # دیرتر از مهلت: فقط پاسخ‌هایی که پیش‌تر ذخیره شده‌اند حساب می‌شوند.
            svc.finalize_expired(e, a, now)
        else:
            a.answers = {**a.answers, **_clean_answers(e, a, body.answers)}
            svc.grade(e, a, now)
        audit(db, "exam.attempt_submitted", cu.id, "exam", e.id, client_ip(request))
        db.commit()
    return _attempt_out(e, a, now)


# ---------------------------------------------------------------- مسئول آموزش: فایل


@router.post("/admin/training/media", response_model=MediaOut, status_code=201)
async def upload_media(
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    cu: CurrentUser = Depends(MANAGE),
    db: Session = Depends(get_db),
):
    s = get_settings()
    m = await store_upload(request, filename, svc.ALLOWED_TYPES, s.max_upload_mb, cu.id)
    db.add(m)
    db.flush()
    audit(
        db,
        "training.media_uploaded",
        cu.id,
        "media",
        m.id,
        client_ip(request),
        size=m.size,
        type=m.content_type,
    )
    db.commit()
    return _media_out(m)


# ---------------------------------------------------------------- مسئول آموزش: دوره


def _course(db: Session, course_id: uuid.UUID) -> Course:
    c = db.get(Course, course_id, options=[selectinload(Course.items)])
    if c is None:
        raise api_error(404, "not_found", "دوره پیدا نشد")
    return c


def _check_media(db: Session, media_id: uuid.UUID | None) -> None:
    if media_id is not None and db.get(MediaFile, media_id) is None:
        raise api_error(400, "unknown_media", "فایل انتخاب‌شده پیدا نشد")


@router.get("/admin/training/people", response_model=PeopleCatalogOut)
def people(_: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    """فهرست کارکنان و سمت‌ها برای انتخاب مخاطب."""
    units = {u.id: u.name for u in db.scalars(select(Unit))}
    users = [_person(u, roles, units) for u, roles in svc.learners(db)]
    users.sort(key=lambda p: p.full_name)
    titles = sorted({t for t in db.scalars(select(User.job_title).where(User.job_title.is_not(None))) if t})
    return PeopleCatalogOut(users=users, job_titles=titles)


@router.get("/admin/training/courses", response_model=list[CourseOut])
def admin_courses(_: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    everyone = svc.learners(db)
    courses = db.scalars(
        select(Course).options(selectinload(Course.items)).order_by(Course.created_at.desc())
    )
    out = [
        _course_out(c, audience_size=sum(1 for u, r in everyone if svc.in_audience(c, u, r))) for c in courses
    ]
    order = {"published": 0, "draft": 1, "archived": 2}
    out.sort(key=lambda c: order[c.status])
    return out


def _sync_course_notes(db: Session, c: Course, cu: CurrentUser) -> None:
    """دوره منتشرشده به مخاطبانش اعلام می‌شود (هر نفر یک بار)؛ دوره برگشته از انتشار اعلان خوانده‌نشده ندارد."""
    db.flush()
    if c.status != "published":
        notes.retract(db, c.id)
        return
    notes.notify(
        db,
        [u.id for u, _ in svc.audience_of(db, c)],
        "training.course",
        f"دوره آموزشی جدید: {c.title}",
        body=("دوره الزامی · " if c.is_mandatory else "") + (c.description or ""),
        link_type="course",
        link_id=c.id,
        important=c.is_mandatory,
        actor_id=cu.id,
        once=True,
    )


def _sync_exam_notes(db: Session, e: Exam, cu: CurrentUser) -> None:
    """اعلان «آزمون باز شد» در لحظه باز شدن آزمون دیده می‌شود؛ با تغییر زمان یا وضعیت آزمون هماهنگ می‌ماند."""
    db.flush()
    now = datetime.now(UTC)
    state = svc.exam_state(e, now)
    if state not in ("open", "upcoming"):
        notes.retract(db, e.id, only_future=state == "closed", now=now)
        return
    at = e.opens_at if state == "upcoming" else now
    notes.reschedule(db, "training.exam", e.id, at, now)
    notes.notify(
        db,
        [u.id for u, _ in svc.audience_of(db, e)],
        "training.exam",
        f"آزمون باز شد: {e.title}",
        body=e.description,
        link_type="exam",
        link_id=e.id,
        important=True,
        at=at,
        actor_id=cu.id,
        once=True,
    )


@router.post("/admin/training/courses", response_model=CourseDetailOut, status_code=201)
def create_course(
    body: CourseIn, request: Request, cu: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)
):
    _check_media(db, body.cover_media_id)
    c = Course(
        title=body.title.strip(),
        description=body.description,
        status=body.status,
        is_mandatory=body.is_mandatory,
        due_date=body.due_date,
        cover_media_id=body.cover_media_id,
        created_by=cu.id,
    )
    _apply_audience(c, body)
    db.add(c)
    db.flush()
    _sync_course_notes(db, c, cu)
    audit(db, "training.course_created", cu.id, "course", c.id, client_ip(request), status=c.status)
    db.commit()
    return _admin_course_detail(db, c)


def _admin_course_detail(db: Session, c: Course) -> CourseDetailOut:
    db.refresh(c)
    exams = db.scalars(select(Exam).where(Exam.course_id == c.id).options(selectinload(Exam.questions)))
    return _course_out(
        c,
        CourseDetailOut,
        items=[_item_out(i) for i in c.items],
        exams=[_exam_out(e) for e in exams],
        audience_size=len(svc.audience_of(db, c)),
    )


@router.get("/admin/training/courses/{course_id}", response_model=CourseDetailOut)
def admin_course(course_id: uuid.UUID, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    return _admin_course_detail(db, _course(db, course_id))


@router.patch("/admin/training/courses/{course_id}", response_model=CourseDetailOut)
def update_course(
    course_id: uuid.UUID,
    body: CourseUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(MANAGE),
    db: Session = Depends(get_db),
):
    c = _course(db, course_id)
    data = body.model_dump(exclude_unset=True)
    if "title" in data and data["title"] is None:
        raise api_error(400, "invalid", "عنوان دوره خالی است")
    if "cover_media_id" in data:
        _check_media(db, data["cover_media_id"])
    for field in ("title", "description", "status", "is_mandatory", "due_date", "cover_media_id"):
        if field in data:
            setattr(c, field, data[field].strip() if field == "title" else data[field])
    _apply_audience(c, body)
    _sync_course_notes(db, c, cu)
    audit(db, "training.course_updated", cu.id, "course", c.id, client_ip(request), fields=sorted(data))
    db.commit()
    return _admin_course_detail(db, c)


@router.delete("/admin/training/courses/{course_id}", status_code=204)
def delete_course(
    course_id: uuid.UUID, request: Request, cu: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)
):
    c = _course(db, course_id)
    if db.scalar(select(func.count()).select_from(ContentView).where(ContentView.course_id == c.id)):
        raise api_error(
            409, "course_has_views", "این دوره بازدید ثبت‌شده دارد؛ برای حفظ آمار، آن را بایگانی کنید"
        )
    db.delete(c)
    audit(db, "training.course_deleted", cu.id, "course", course_id, client_ip(request))
    db.commit()


@router.post("/admin/training/courses/{course_id}/items", response_model=ItemOut, status_code=201)
def add_item(
    course_id: uuid.UUID,
    body: ItemIn,
    request: Request,
    cu: CurrentUser = Depends(MANAGE),
    db: Session = Depends(get_db),
):
    c = _course(db, course_id)
    _check_media(db, body.media_id)
    item = CourseItem(
        course_id=c.id,
        kind=body.kind,
        title=body.title.strip(),
        body=body.body,
        media_id=body.media_id if body.kind not in ("text", "link") else None,
        sort_order=max((i.sort_order for i in c.items), default=0) + 1,
    )
    db.add(item)
    db.flush()
    audit(db, "training.item_added", cu.id, "course", c.id, client_ip(request), kind=body.kind)
    db.commit()
    db.refresh(item)
    return _item_out(item)


@router.patch("/admin/training/items/{item_id}", response_model=ItemOut)
def update_item(
    item_id: uuid.UUID,
    body: ItemUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(MANAGE),
    db: Session = Depends(get_db),
):
    item = db.get(CourseItem, item_id)
    if item is None:
        raise api_error(404, "not_found", "محتوا پیدا نشد")
    data = body.model_dump(exclude_unset=True)
    if "media_id" in data:
        _check_media(db, data["media_id"])
    for k, v in data.items():
        if v is not None:
            setattr(item, k, v.strip() if k == "title" else v)
    audit(db, "training.item_updated", cu.id, "course", item.course_id, client_ip(request))
    db.commit()
    db.refresh(item)
    return _item_out(item)


@router.delete("/admin/training/items/{item_id}", status_code=204)
def delete_item(
    item_id: uuid.UUID, request: Request, cu: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)
):
    item = db.get(CourseItem, item_id)
    if item is None:
        raise api_error(404, "not_found", "محتوا پیدا نشد")
    audit(db, "training.item_deleted", cu.id, "course", item.course_id, client_ip(request))
    db.delete(item)
    db.commit()


@router.put("/admin/training/courses/{course_id}/order", status_code=204)
def reorder_items(
    course_id: uuid.UUID, body: OrderIn, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)
):
    c = _course(db, course_id)
    position = {item_id: n for n, item_id in enumerate(body.ids)}
    if set(position) != {i.id for i in c.items}:
        raise api_error(400, "invalid_order", "فهرست ترتیب با محتواهای دوره یکی نیست")
    for i in c.items:
        i.sort_order = position[i.id] + 1
    db.commit()


# ---------------------------------------------------------------- مسئول آموزش: آزمون


def _exam(db: Session, exam_id: uuid.UUID) -> Exam:
    e = db.get(Exam, exam_id, options=[selectinload(Exam.questions)])
    if e is None:
        raise api_error(404, "not_found", "آزمون پیدا نشد")
    return e


def _attempt_count(db: Session, exam_id: uuid.UUID) -> int:
    return db.scalar(select(func.count()).select_from(ExamAttempt).where(ExamAttempt.exam_id == exam_id)) or 0


def _admin_exam_detail(db: Session, e: Exam) -> ExamDetailOut:
    db.refresh(e)
    return _exam_out(
        e,
        ExamDetailOut,
        questions=[_question_out(q) for q in e.questions],
        audience_size=len(svc.audience_of(db, e)),
        attempt_count=_attempt_count(db, e.id),
    )


@router.get("/admin/training/exams", response_model=list[ExamOut])
def admin_exams(_: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    everyone = svc.learners(db)
    counts = dict(
        db.execute(
            select(ExamAttempt.exam_id, func.count(func.distinct(ExamAttempt.user_id))).group_by(
                ExamAttempt.exam_id
            )
        ).all()
    )
    exams = db.scalars(select(Exam).options(selectinload(Exam.questions)).order_by(Exam.created_at.desc()))
    return [
        _exam_out(
            e,
            audience_size=sum(1 for u, r in everyone if svc.in_audience(e, u, r)),
            attempt_count=counts.get(e.id, 0),
        )
        for e in exams
    ]


@router.post("/admin/training/exams", response_model=ExamDetailOut, status_code=201)
def create_exam(
    body: ExamIn, request: Request, cu: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)
):
    if body.course_id is not None:
        _course(db, body.course_id)
    data = body.model_dump(
        exclude={"target_unit_ids", "target_role_codes", "target_job_titles", "target_user_ids"}
    )
    data["title"] = data["title"].strip()
    e = Exam(**data, created_by=cu.id)
    _apply_audience(e, body)
    db.add(e)
    db.flush()
    _sync_exam_notes(db, e, cu)
    audit(db, "training.exam_created", cu.id, "exam", e.id, client_ip(request))
    db.commit()
    return _admin_exam_detail(db, e)


@router.get("/admin/training/exams/{exam_id}", response_model=ExamDetailOut)
def admin_exam(exam_id: uuid.UUID, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    return _admin_exam_detail(db, _exam(db, exam_id))


@router.patch("/admin/training/exams/{exam_id}", response_model=ExamDetailOut)
def update_exam(
    exam_id: uuid.UUID,
    body: ExamUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(MANAGE),
    db: Session = Depends(get_db),
):
    e = _exam(db, exam_id)
    data = body.model_dump(exclude_unset=True)
    if data.get("course_id") is not None:
        _course(db, data["course_id"])
    if "title" in data and not (data["title"] or "").strip():
        raise api_error(400, "invalid", "عنوان آزمون خالی است")
    nullable = {"description", "course_id", "opens_at", "closes_at", "time_limit_minutes"}
    for k, v in data.items():
        if k.startswith("target_"):
            continue
        if v is None and k not in nullable:
            continue
        setattr(e, k, v.strip() if k == "title" else v)
    if e.opens_at and e.closes_at and e.closes_at <= e.opens_at:
        raise api_error(400, "invalid_dates", "زمان بسته شدن باید بعد از زمان باز شدن باشد")
    _apply_audience(e, body)
    _sync_exam_notes(db, e, cu)
    audit(
        db,
        "training.exam_updated",
        cu.id,
        "exam",
        e.id,
        client_ip(request),
        fields=sorted(data),
        manual_state=data.get("manual_state"),
    )
    db.commit()
    return _admin_exam_detail(db, e)


@router.put("/admin/training/exams/{exam_id}/questions", response_model=ExamDetailOut)
def replace_questions(
    exam_id: uuid.UUID,
    body: list[QuestionIn],
    request: Request,
    cu: CurrentUser = Depends(MANAGE),
    db: Session = Depends(get_db),
):
    e = _exam(db, exam_id)
    if _attempt_count(db, e.id):
        raise api_error(
            409, "exam_has_attempts", "پس از شرکت اولین نفر، سؤال‌ها قابل تغییر نیستند تا نتایج یکسان بمانند"
        )
    e.questions.clear()
    db.flush()
    for n, q in enumerate(body, start=1):
        options = [
            {"id": chr(ord("a") + k), "text": o.text.strip(), "correct": o.correct}
            for k, o in enumerate(q.options)
        ]
        e.questions.append(
            ExamQuestion(
                sort_order=n,
                kind=q.kind,
                text=q.text.strip(),
                options=options,
                points=q.points,
                explanation=q.explanation,
            )
        )
    audit(db, "training.exam_questions_set", cu.id, "exam", e.id, client_ip(request), count=len(body))
    db.commit()
    return _admin_exam_detail(db, e)


@router.delete("/admin/training/exams/{exam_id}", status_code=204)
def delete_exam(
    exam_id: uuid.UUID, request: Request, cu: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)
):
    e = _exam(db, exam_id)
    if _attempt_count(db, e.id):
        raise api_error(409, "exam_has_attempts", "این آزمون شرکت‌کننده دارد و برای حفظ نتایج حذف نمی‌شود")
    db.delete(e)
    audit(db, "training.exam_deleted", cu.id, "exam", exam_id, client_ip(request))
    db.commit()


# ---------------------------------------------------------------- گزارش‌ها


def _role_names(db: Session) -> dict[str, str]:
    return {r.code: r.name for r in db.scalars(select(Role))}


def _person(u: User, roles: set[str], units: dict, role_names: dict | None = None) -> PersonRef:
    return PersonRef(
        user_id=u.id,
        personnel_code=u.personnel_code,
        full_name=u.full_name,
        unit_name=units.get(u.unit_id),
        job_title=u.job_title,
        roles=sorted((role_names or {}).get(r, r) for r in roles),
    )


def _breakdowns(people: list[tuple[PersonRef, bool, bool, bool]], key) -> list[Breakdown]:
    """people: (شخص، در مخاطب، شرکت کرده، کامل/قبول)."""
    groups: dict[str, list[int]] = defaultdict(lambda: [0, 0, 0])
    for p, in_aud, part, done in people:
        for label in key(p):
            g = groups[label]
            g[0] += in_aud
            g[1] += part
            g[2] += done
    rows = [Breakdown(label=k, audience=v[0], participated=v[1], completed=v[2]) for k, v in groups.items()]
    rows.sort(key=lambda b: (-b.audience, b.label))
    return rows


def _by_unit(p: PersonRef) -> list[str]:
    return [p.unit_name or "بدون واحد"]


def _by_title(p: PersonRef) -> list[str]:
    return [p.job_title or "سمت نامشخص"]


def _by_role(p: PersonRef) -> list[str]:
    return p.roles or ["بدون نقش"]


def _users_by_id(db: Session, ids: set[uuid.UUID]) -> dict[uuid.UUID, tuple[User, set[str]]]:
    if not ids:
        return {}
    out = {}
    for u in db.scalars(select(User).where(User.id.in_(ids)).options(selectinload(User.roles))):
        out[u.id] = (u, {r.role.code for r in u.roles})
    return out


def course_report(db: Session, c: Course) -> CourseReportOut:
    units = {u.id: u.name for u in db.scalars(select(Unit))}
    role_names = _role_names(db)
    audience = {u.id: (u, r) for u, r in svc.audience_of(db, c)}
    views = list(db.scalars(select(ContentView).where(ContentView.course_id == c.id)))
    others = _users_by_id(db, {v.user_id for v in views} - set(audience))
    people = {**audience, **others}

    item_ids = [i.id for i in c.items]
    per_user: dict[uuid.UUID, dict] = defaultdict(
        lambda: {"views": 0, "items": set(), "done": set(), "seconds": 0, "last": None}
    )
    per_item: dict[uuid.UUID, dict] = defaultdict(
        lambda: {"users": set(), "views": 0, "done": set(), "seconds": 0}
    )
    for v in views:
        u = per_user[v.user_id]
        u["views"] += 1
        u["items"].add(v.item_id)
        u["seconds"] += v.seconds
        u["last"] = max(u["last"], v.started_at) if u["last"] else v.started_at
        it = per_item[v.item_id]
        it["users"].add(v.user_id)
        it["views"] += 1
        it["seconds"] += v.seconds
        if v.completed:
            u["done"].add(v.item_id)
            it["done"].add(v.user_id)

    learners, not_started, flat = [], [], []
    for uid, (u, roles) in people.items():
        p = _person(u, roles, units, role_names)
        s = per_user.get(uid)
        completed = bool(item_ids) and s is not None and set(item_ids) <= s["done"]
        flat.append((p, uid in audience, s is not None, completed))
        if s is None:
            if uid in audience:
                not_started.append(p)
            continue
        learners.append(
            CourseLearnerRow(
                **p.model_dump(),
                in_audience=uid in audience,
                views=s["views"],
                items_viewed=len(s["items"] & set(item_ids)),
                items_completed=len(s["done"] & set(item_ids)),
                total_seconds=s["seconds"],
                last_view_at=s["last"],
                completed=completed,
            )
        )
    learners.sort(key=lambda r: (not r.completed, -r.items_completed, r.full_name))
    not_started.sort(key=lambda p: p.full_name)
    return CourseReportOut(
        course_id=c.id,
        title=c.title,
        item_count=len(item_ids),
        audience_size=len(audience),
        participants=sum(1 for _, a, part, _ in flat if part),
        completed=sum(1 for *_, done in flat if done),
        total_views=len(views),
        items=[
            ItemStat(
                item_id=i.id,
                title=i.title,
                kind=i.kind,
                viewers=len(per_item[i.id]["users"]),
                views=per_item[i.id]["views"],
                completed=len(per_item[i.id]["done"]),
                total_seconds=per_item[i.id]["seconds"],
            )
            for i in c.items
        ],
        learners=learners,
        not_started=not_started,
        by_unit=_breakdowns(flat, _by_unit),
        by_job_title=_breakdowns(flat, _by_title),
        by_role=_breakdowns(flat, _by_role),
    )


@router.get("/admin/training/courses/{course_id}/report", response_model=CourseReportOut)
def get_course_report(course_id: uuid.UUID, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    return course_report(db, _course(db, course_id))


@router.get("/admin/training/items/{item_id}/viewers", response_model=list[ItemViewerRow])
def item_viewers(item_id: uuid.UUID, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    if db.get(CourseItem, item_id) is None:
        raise api_error(404, "not_found", "محتوا پیدا نشد")
    units = {u.id: u.name for u in db.scalars(select(Unit))}
    role_names = _role_names(db)
    views = list(db.scalars(select(ContentView).where(ContentView.item_id == item_id)))
    people = _users_by_id(db, {v.user_id for v in views})
    grouped: dict[uuid.UUID, list[ContentView]] = defaultdict(list)
    for v in views:
        grouped[v.user_id].append(v)
    rows = []
    for uid, vs in grouped.items():
        u, roles = people[uid]
        rows.append(
            ItemViewerRow(
                **_person(u, roles, units, role_names).model_dump(),
                views=len(vs),
                total_seconds=sum(v.seconds for v in vs),
                max_progress=max(v.progress for v in vs),
                completed=any(v.completed for v in vs),
                first_view_at=min(v.started_at for v in vs),
                last_view_at=max(v.started_at for v in vs),
            )
        )
    rows.sort(key=lambda r: (-r.views, r.full_name))
    return rows


def exam_report(db: Session, e: Exam) -> ExamReportOut:
    now = _now()
    units = {u.id: u.name for u in db.scalars(select(Unit))}
    role_names = _role_names(db)
    audience = {u.id: (u, r) for u, r in svc.audience_of(db, e)}
    attempts = list(db.scalars(select(ExamAttempt).where(ExamAttempt.exam_id == e.id)))
    if any([svc.finalize_expired(e, a, now) for a in attempts]):
        db.commit()
    done = [a for a in attempts if a.submitted_at is not None]
    others = _users_by_id(db, {a.user_id for a in attempts} - set(audience))
    people = {**audience, **others}

    by_user: dict[uuid.UUID, list[ExamAttempt]] = defaultdict(list)
    for a in attempts:
        by_user[a.user_id].append(a)

    takers, not_attempted, flat, best_scores = [], [], [], []
    for uid, (u, roles) in people.items():
        p = _person(u, roles, units, role_names)
        mine = by_user.get(uid, [])
        finished = sorted((a for a in mine if a.submitted_at), key=lambda a: a.submitted_at)
        passed = any(a.passed for a in finished)
        flat.append((p, uid in audience, bool(mine), passed))
        if not mine:
            if uid in audience:
                not_attempted.append(p)
            continue
        best = max((a.score for a in finished), default=None)
        if best is not None:
            best_scores.append(best)
        takers.append(
            ExamTakerRow(
                **p.model_dump(),
                in_audience=uid in audience,
                attempts=len(mine),
                best_score=best,
                last_score=finished[-1].score if finished else None,
                passed=passed,
                last_submitted_at=finished[-1].submitted_at if finished else None,
            )
        )
    takers.sort(key=lambda r: (-(r.best_score or -1), r.full_name))
    not_attempted.sort(key=lambda p: p.full_name)

    histogram = [0] * 10
    for s in best_scores:
        histogram[min(int(s // 10), 9)] += 1

    questions = []
    for q in e.questions:
        qid = str(q.id)
        answered = correct = 0
        counts = {o["id"]: 0 for o in q.options}
        for a in done:
            if not any(entry["q"] == qid for entry in a.layout):
                continue
            selected = a.answers.get(qid, [])
            if selected:
                answered += 1
            correct += svc.is_correct(q, selected)
            for s in selected:
                if s in counts:
                    counts[s] += 1
        questions.append(
            QuestionStat(
                question_id=q.id,
                text=q.text,
                answered=answered,
                correct=correct,
                correct_percent=round(100 * correct / len(done), 1) if done else None,
                option_counts=counts,
            )
        )
    return ExamReportOut(
        exam_id=e.id,
        title=e.title,
        audience_size=len(audience),
        participants=len(by_user),
        passed=sum(1 for t in takers if t.passed),
        average_score=round(sum(best_scores) / len(best_scores), 1) if best_scores else None,
        histogram=histogram,
        takers=takers,
        not_attempted=not_attempted,
        questions=questions,
        by_unit=_breakdowns(flat, _by_unit),
        by_job_title=_breakdowns(flat, _by_title),
    )


@router.get("/admin/training/exams/{exam_id}/report", response_model=ExamReportOut)
def get_exam_report(exam_id: uuid.UUID, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    return exam_report(db, _exam(db, exam_id))


# ---------------------------------------------------------------- خروجی Excel (CSV)


def _csv(rows: list[list], filename: str) -> Response:
    buf = io.StringIO()
    # BOM تا Excel متن فارسی را درست نشان دهد
    buf.write("﻿")
    w = csv.writer(buf)
    for r in rows:
        w.writerow(["" if v is None else v for v in r])
    return Response(
        buf.getvalue().encode("utf-8"),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"},
    )


def _fmt(dt: datetime | None) -> str:
    return dt.astimezone(UTC).strftime("%Y-%m-%d %H:%M UTC") if dt else ""


@router.get("/admin/training/courses/{course_id}/report.csv", include_in_schema=False)
def course_report_csv(course_id: uuid.UUID, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    r = course_report(db, _course(db, course_id))
    rows = [
        [
            "کد پرسنلی",
            "نام",
            "واحد",
            "سمت",
            "نقش‌ها",
            "در مخاطبان",
            "دفعات بازدید",
            "محتوای دیده‌شده",
            "محتوای کامل‌شده",
            f"از {r.item_count}",
            "مدت (دقیقه)",
            "آخرین بازدید",
            "دوره کامل شد",
        ]
    ]
    for p in r.learners:
        rows.append(
            [
                p.personnel_code,
                p.full_name,
                p.unit_name,
                p.job_title,
                "، ".join(p.roles),
                "بله" if p.in_audience else "خیر",
                p.views,
                p.items_viewed,
                p.items_completed,
                r.item_count,
                round(p.total_seconds / 60, 1),
                _fmt(p.last_view_at),
                "بله" if p.completed else "خیر",
            ]
        )
    for p in r.not_started:
        rows.append(
            [
                p.personnel_code,
                p.full_name,
                p.unit_name,
                p.job_title,
                "، ".join(p.roles),
                "بله",
                0,
                0,
                0,
                r.item_count,
                0,
                "",
                "خیر",
            ]
        )
    return _csv(rows, f"گزارش دوره - {r.title}.csv")


@router.get("/admin/training/exams/{exam_id}/report.csv", include_in_schema=False)
def exam_report_csv(exam_id: uuid.UUID, _: CurrentUser = Depends(MANAGE), db: Session = Depends(get_db)):
    r = exam_report(db, _exam(db, exam_id))
    rows = [
        [
            "کد پرسنلی",
            "نام",
            "واحد",
            "سمت",
            "نقش‌ها",
            "در مخاطبان",
            "دفعات شرکت",
            "بهترین نمره",
            "آخرین نمره",
            "قبول",
            "زمان آخرین ثبت",
        ]
    ]
    for t in r.takers:
        rows.append(
            [
                t.personnel_code,
                t.full_name,
                t.unit_name,
                t.job_title,
                "، ".join(t.roles),
                "بله" if t.in_audience else "خیر",
                t.attempts,
                t.best_score,
                t.last_score,
                "بله" if t.passed else "خیر",
                _fmt(t.last_submitted_at),
            ]
        )
    for p in r.not_attempted:
        rows.append(
            [
                p.personnel_code,
                p.full_name,
                p.unit_name,
                p.job_title,
                "، ".join(p.roles),
                "بله",
                0,
                "",
                "",
                "شرکت نکرده",
                "",
            ]
        )
    return _csv(rows, f"نتایج آزمون - {r.title}.csv")
