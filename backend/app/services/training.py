"""منطق بخش آموزش: مخاطب، وضعیت آزمون، تصحیح، و نگهداری امن فایل‌ها."""

import hashlib
import hmac
import random
import time
import uuid
from datetime import UTC, datetime
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.core.config import get_settings
from app.core.deps import build_current_user
from app.models import Exam, ExamAttempt, MediaFile, User

# ---------------------------------------------------------------- مخاطب


def in_audience(target, user: User, role_codes: set[str]) -> bool:
    """افراد نام‌برده همیشه مخاطب‌اند؛ بقیه باید در همه فهرست‌های پرشده بگنجند."""
    if str(user.id) in (target.target_user_ids or []):
        return True
    dims = [
        (
            target.target_unit_ids,
            lambda: user.unit_id is not None and str(user.unit_id) in target.target_unit_ids,
        ),
        (target.target_role_codes, lambda: bool(role_codes.intersection(target.target_role_codes))),
        (target.target_job_titles, lambda: (user.job_title or "") in target.target_job_titles),
    ]
    filled = [check for values, check in dims if values]
    if not filled:
        # فقط افراد مشخص انتخاب شده‌اند و این کاربر جزو آن‌ها نیست؛ یا هیچ قیدی نیست = همه.
        return not target.target_user_ids
    return all(check() for check in filled)


def learners(db: Session) -> list[tuple[User, set[str]]]:
    """همه کاربران فعالی که اجازه شرکت در آموزش دارند، با کد نقش‌هایشان."""
    out = []
    for u in db.scalars(select(User).where(User.is_active.is_(True)).options(selectinload(User.roles))):
        cu = build_current_user(u)
        if cu.has("training.read"):
            out.append((u, cu.role_codes))
    return out


def audience_of(db: Session, target) -> list[tuple[User, set[str]]]:
    return [(u, roles) for u, roles in learners(db) if in_audience(target, u, roles)]


# ---------------------------------------------------------------- آزمون


def exam_state(exam: Exam, now: datetime | None = None) -> str:
    """draft | upcoming | open | closed — تنها مرجع باز یا بسته بودن آزمون."""
    now = now or datetime.now(UTC)
    if exam.status != "published":
        return "draft"
    if exam.manual_state == "open":
        return "open"
    if exam.manual_state == "closed":
        return "closed"
    if exam.opens_at and now < exam.opens_at:
        return "upcoming"
    if exam.closes_at and now >= exam.closes_at:
        return "closed"
    return "open"


def make_layout(exam: Exam, rng: random.Random | None = None) -> list[dict]:
    rng = rng or random.SystemRandom()
    questions = list(exam.questions)
    if exam.shuffle_questions:
        rng.shuffle(questions)
    layout = []
    for q in questions:
        ids = [o["id"] for o in q.options]
        # ترتیب «درست/غلط» عوض نمی‌شود تا خوانا بماند.
        if exam.shuffle_options and q.kind != "true_false":
            rng.shuffle(ids)
        layout.append({"q": str(q.id), "o": ids})
    return layout


def is_correct(question, selected: list[str]) -> bool:
    right = {o["id"] for o in question.options if o.get("correct")}
    return set(selected) == right


def grade(exam: Exam, attempt: ExamAttempt, now: datetime) -> None:
    by_id = {str(q.id): q for q in exam.questions}
    points = max_points = 0.0
    for entry in attempt.layout:
        q = by_id.get(entry["q"])
        if q is None:
            continue
        max_points += q.points
        if is_correct(q, attempt.answers.get(entry["q"], [])):
            points += q.points
    attempt.points, attempt.max_points = points, max_points
    attempt.score = round(100 * points / max_points, 1) if max_points else 0.0
    attempt.passed = attempt.score >= exam.pass_score
    attempt.submitted_at = now


def finalize_expired(exam: Exam, attempt: ExamAttempt, now: datetime) -> bool:
    """آزمونی که مهلتش گذشته با همان پاسخ‌های ذخیره‌شده تصحیح می‌شود."""
    if attempt.submitted_at is None and attempt.deadline_at is not None and now > attempt.deadline_at:
        grade(exam, attempt, attempt.deadline_at)
        return True
    return False


def answers_visible(exam: Exam, attempt: ExamAttempt, now: datetime) -> bool:
    if attempt.submitted_at is None or exam.show_answers == "never":
        return False
    if exam.show_answers == "after_submit":
        return True
    return exam_state(exam, now) == "closed"


# ---------------------------------------------------------------- فایل‌ها

# فقط این پسوندها پذیرفته می‌شوند؛ نوع محتوا را سرور از پسوند تعیین می‌کند، نه از مرورگر.
ALLOWED_TYPES: dict[str, str] = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
    ".gif": "image/gif",
    ".mp4": "video/mp4",
    ".m4v": "video/mp4",
    ".webm": "video/webm",
    ".mov": "video/quicktime",
    ".mp3": "audio/mpeg",
    ".m4a": "audio/mp4",
    ".aac": "audio/aac",
    ".ogg": "audio/ogg",
    ".wav": "audio/wav",
    ".pdf": "application/pdf",
    ".ppt": "application/vnd.ms-powerpoint",
    ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".pps": "application/vnd.ms-powerpoint",
    ".ppsx": "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    ".doc": "application/msword",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xls": "application/vnd.ms-excel",
    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".txt": "text/plain; charset=utf-8",
    ".zip": "application/zip",
}
INLINE_PREFIXES = ("image/", "video/", "audio/", "application/pdf")

LINK_TTL_SECONDS = 6 * 3600


def media_root() -> Path:
    root = Path(get_settings().media_dir).resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def media_path(m: MediaFile) -> Path:
    return media_root() / m.storage_name


def sign_media(media_id: uuid.UUID, now: float | None = None) -> str:
    """پیوند موقت برای <img>/<video> و پخش‌کننده‌ها که سرآیند Authorization نمی‌فرستند."""
    exp = int((now or time.time()) + LINK_TTL_SECONDS)
    sig = _sig(media_id, exp)
    return f"/api/v1/training/media/{media_id}?exp={exp}&sig={sig}"


def check_media_signature(media_id: uuid.UUID, exp: int, sig: str) -> bool:
    return exp >= time.time() and hmac.compare_digest(_sig(media_id, exp), sig)


def _sig(media_id: uuid.UUID, exp: int) -> str:
    key = get_settings().jwt_secret.encode()
    return hmac.new(key, f"media:{media_id}:{exp}".encode(), hashlib.sha256).hexdigest()[:40]
