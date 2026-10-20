from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from urllib.parse import urlsplit

from sqlalchemy import select

from app.models import ExamAttempt, Unit, User
from app.services.training import in_audience
from tests.conftest import SAFETY, STAFF, SYSADMIN, TRAINER, UNIT_MANAGER, auth

API = "/api/v1"


def upload(client, h, name: str, data: bytes = b"x" * 2048):
    return client.post(f"{API}/admin/training/media", params={"filename": name}, content=data, headers=h)


def unit_id(db, name: str) -> str:
    return str(db.scalar(select(Unit.id).where(Unit.name == name)))


def make_course(client, h, **extra):
    body = {"title": "احیای قلبی ریوی", "description": "دوره پایه", "is_mandatory": True, **extra}
    r = client.post(f"{API}/admin/training/courses", json=body, headers=h)
    assert r.status_code == 201, r.text
    return r.json()


def test_only_training_manager_uploads_and_only_safe_types(client):
    t = auth(client, TRAINER)
    assert upload(client, auth(client, STAFF), "a.mp4").status_code == 403
    assert upload(client, t, "virus.exe").status_code == 400
    assert upload(client, t, "page.html").status_code == 400
    assert upload(client, t, "empty.pdf", b"").status_code == 400
    r = upload(client, t, r"C:\fakepath\اسلاید جلسه.pptx")
    assert r.status_code == 201
    m = r.json()
    assert m["original_name"] == "اسلاید جلسه.pptx" and "presentationml" in m["content_type"]


def test_course_visibility_media_and_view_stats(client, db):
    t = auth(client, TRAINER)
    video = upload(client, t, "cpr.mp4", bytes(range(256)) * 40).json()
    pdf = upload(client, t, "guide.pdf").json()
    c = make_course(client, t, target_unit_ids=[unit_id(db, "اورژانس")])
    for item in (
        {"kind": "video", "title": "فیلم آموزشی", "media_id": video["id"]},
        {"kind": "pdf", "title": "راهنما", "media_id": pdf["id"]},
        {"kind": "text", "title": "نکات", "body": "فشردن قفسه سینه ۱۰۰ تا ۱۲۰ بار در دقیقه"},
    ):
        assert (
            client.post(f"{API}/admin/training/courses/{c['id']}/items", json=item, headers=t).status_code
            == 201
        )
    assert (
        client.post(
            f"{API}/admin/training/courses/{c['id']}/items",
            json={"kind": "video", "title": "بدون فایل"},
            headers=t,
        ).status_code
        == 422
    )

    staff = auth(client, STAFF)
    assert client.get(f"{API}/training/courses", headers=staff).json() == []  # هنوز پیش‌نویس
    client.patch(f"{API}/admin/training/courses/{c['id']}", json={"status": "published"}, headers=t)
    assert [x["id"] for x in client.get(f"{API}/training/courses", headers=staff).json()] == [c["id"]]
    assert client.get(f"{API}/training/courses", headers=auth(client, SAFETY)).json() == []  # واحد دیگر
    assert client.get(f"{API}/training/courses/{c['id']}", headers=auth(client, SAFETY)).status_code == 404

    detail = client.get(f"{API}/training/courses/{c['id']}", headers=staff).json()
    items = {i["kind"]: i for i in detail["items"]}
    url = items["video"]["media"]["url"]
    r = client.get(url, headers={"Range": "bytes=0-99"})
    assert r.status_code == 206 and len(r.content) == 100
    assert r.headers["content-disposition"].startswith("inline")
    bad = urlsplit(url)
    assert client.get(bad.path + "?" + bad.query.replace("sig=", "sig=0")).status_code == 403

    # فیلم فقط با ۹۰٪ تماشا کامل حساب می‌شود؛ PDF با باز کردن.
    v1 = client.post(f"{API}/training/items/{items['video']['id']}/views", headers=staff).json()["view_id"]
    client.patch(f"{API}/training/views/{v1}", json={"seconds": 30, "progress": 40}, headers=staff)
    v2 = client.post(f"{API}/training/items/{items['video']['id']}/views", headers=staff).json()["view_id"]
    client.patch(f"{API}/training/views/{v2}", json={"seconds": 120, "progress": 95}, headers=staff)
    client.patch(f"{API}/training/views/{v2}", json={"seconds": 10, "progress": 20}, headers=staff)  # دیررس
    client.post(f"{API}/training/items/{items['pdf']['id']}/views", headers=staff)
    # بازدید کاربر دیگری را نمی‌توان تغییر داد
    assert (
        client.patch(
            f"{API}/training/views/{v1}", json={"seconds": 1, "progress": 1}, headers=auth(client, TRAINER)
        ).status_code
        == 404
    )

    mine = client.get(f"{API}/training/courses/{c['id']}", headers=staff).json()
    assert mine["completed_count"] == 2 and mine["item_count"] == 3

    rep = client.get(f"{API}/admin/training/courses/{c['id']}/report", headers=t).json()
    assert rep["audience_size"] == 2  # 1001 و 1002 در اورژانس
    assert rep["participants"] == 1 and rep["total_views"] == 3 and rep["completed"] == 0
    video_stat = next(s for s in rep["items"] if s["kind"] == "video")
    assert video_stat["viewers"] == 1 and video_stat["views"] == 2 and video_stat["total_seconds"] == 150
    learner = rep["learners"][0]
    assert learner["job_title"] == "پرستار" and learner["views"] == 3 and learner["items_completed"] == 2
    assert [p["personnel_code"] for p in rep["not_started"]] == [UNIT_MANAGER]
    titles = {b["label"]: b for b in rep["by_job_title"]}
    assert titles["پرستار"]["participated"] == 1 and titles["سرپرستار"]["participated"] == 0

    viewers = client.get(f"{API}/admin/training/items/{items['video']['id']}/viewers", headers=t).json()
    assert viewers[0]["views"] == 2 and viewers[0]["max_progress"] == 95 and viewers[0]["completed"]

    csv = client.get(f"{API}/admin/training/courses/{c['id']}/report.csv", headers=t)
    assert csv.content.startswith("\ufeff".encode()) and "کد پرسنلی" in csv.text and "سرپرستار" in csv.text
    assert client.get(f"{API}/admin/training/courses/{c['id']}/report", headers=staff).status_code == 403
    # دوره‌ای که بازدید دارد حذف نمی‌شود (بایگانی شود)
    assert client.delete(f"{API}/admin/training/courses/{c['id']}", headers=t).status_code == 409


QUESTIONS = [
    {
        "kind": "single",
        "text": "تعداد فشردن قفسه سینه در دقیقه؟",
        "options": [{"text": "۶۰"}, {"text": "۱۰۰ تا ۱۲۰", "correct": True}, {"text": "۲۰۰"}],
        "points": 2,
    },
    {
        "kind": "multiple",
        "text": "کدام‌ها جزو زنجیره بقا هستند؟",
        "options": [
            {"text": "تماس فوری", "correct": True},
            {"text": "شوک زودهنگام", "correct": True},
            {"text": "صبر کردن"},
        ],
    },
    {
        "kind": "true_false",
        "text": "احیا فقط کار پزشک است.",
        "options": [{"text": "درست"}, {"text": "غلط", "correct": True}],
    },
]


def make_exam(client, h, **extra):
    body = {"title": "آزمون احیا", "status": "published", "max_attempts": 1, "pass_score": 70, **extra}
    e = client.post(f"{API}/admin/training/exams", json=body, headers=h).json()
    r = client.put(f"{API}/admin/training/exams/{e['id']}/questions", json=QUESTIONS, headers=h)
    assert r.status_code == 200, r.text
    return r.json()


def correct_answers(exam_detail, attempt):
    right = {q["id"]: [o["id"] for o in q["options"] if o["correct"]] for q in exam_detail["questions"]}
    return {q["id"]: right[q["id"]] for q in attempt["questions"]}


def test_exam_window_group_grading_and_answer_visibility(client):
    t = auth(client, TRAINER)
    future = (datetime.now(UTC) + timedelta(days=1)).isoformat()
    exam = make_exam(client, t, opens_at=future, target_job_titles=["پرستار"], show_answers="after_close")
    assert exam["question_count"] == 3 and exam["total_points"] == 4 and exam["audience_size"] == 1

    staff = auth(client, STAFF)
    mine = client.get(f"{API}/training/exams", headers=staff).json()
    assert [e["state"] for e in mine] == ["upcoming"]
    assert client.get(f"{API}/training/exams", headers=auth(client, UNIT_MANAGER)).json() == []  # سمت دیگر
    r = client.post(f"{API}/training/exams/{exam['id']}/attempts", headers=staff)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "exam_not_open"

    # مسئول آموزش دستی باز می‌کند
    client.patch(f"{API}/admin/training/exams/{exam['id']}", json={"manual_state": "open"}, headers=t)
    a = client.post(f"{API}/training/exams/{exam['id']}/attempts", headers=staff).json()
    assert len(a["questions"]) == 3 and not a["finished"]
    assert all("correct" not in o for q in a["questions"] for o in q["options"])  # پاسخ لو نمی‌رود
    # دوباره زدن «شروع» همان آزمون نیمه‌کاره را برمی‌گرداند
    assert client.post(f"{API}/training/exams/{exam['id']}/attempts", headers=staff).json()["id"] == a["id"]

    answers = correct_answers(exam, a)
    tf = next(q for q in a["questions"] if q["kind"] == "true_false")
    wrong_tf = next(o["id"] for o in tf["options"] if o["id"] not in answers[tf["id"]])
    saved = client.put(
        f"{API}/training/attempts/{a['id']}/answers",
        json={"answers": {tf["id"]: [wrong_tf, "zzz"]}},
        headers=staff,
    )
    assert saved.json()["questions"][[q["id"] for q in a["questions"]].index(tf["id"])]["selected"] == [
        wrong_tf
    ]
    answers.pop(tf["id"])  # پاسخ غلطِ ذخیره‌شده می‌ماند
    res = client.post(
        f"{API}/training/attempts/{a['id']}/submit", json={"answers": answers}, headers=staff
    ).json()
    assert res["finished"] and res["score"] == 75.0 and res["passed"] is True
    assert res["answers_visible"] is False
    mine = client.get(f"{API}/training/exams", headers=staff).json()[0]
    assert mine["last_attempt_id"] == a["id"] and mine["in_progress_attempt_id"] is None

    r = client.post(f"{API}/training/exams/{exam['id']}/attempts", headers=staff)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "no_attempts_left"
    # پس از شرکت اولین نفر سؤال‌ها قفل‌اند
    assert (
        client.put(
            f"{API}/admin/training/exams/{exam['id']}/questions", json=QUESTIONS, headers=t
        ).status_code
        == 409
    )

    client.patch(f"{API}/admin/training/exams/{exam['id']}", json={"manual_state": "closed"}, headers=t)
    after = client.get(f"{API}/training/attempts/{a['id']}", headers=staff).json()
    assert after["answers_visible"] is True
    assert next(q for q in after["questions"] if q["id"] == tf["id"])["correct"] is False
    # آزمون دیگران را نمی‌توان دید
    assert client.get(f"{API}/training/attempts/{a['id']}", headers=t).status_code == 404

    listed = client.get(f"{API}/admin/training/exams", headers=t).json()
    assert [(x["id"], x["attempt_count"], x["state"]) for x in listed] == [(exam["id"], 1, "closed")]
    assert client.get(f"{API}/admin/training/courses", headers=t).status_code == 200
    rep = client.get(f"{API}/admin/training/exams/{exam['id']}/report", headers=t).json()
    assert rep["participants"] == 1 and rep["passed"] == 1 and rep["average_score"] == 75.0
    assert rep["histogram"][7] == 1 and rep["not_attempted"] == []
    tf_stat = next(q for q in rep["questions"] if q["question_id"] == tf["id"])
    assert tf_stat["correct"] == 0 and tf_stat["option_counts"][wrong_tf] == 1
    csv = client.get(f"{API}/admin/training/exams/{exam['id']}/report.csv", headers=t)
    assert "بهترین نمره" in csv.text and "75.0" in csv.text


def test_time_limit_is_enforced_by_server(client, db):
    t = auth(client, TRAINER)
    exam = make_exam(client, t, time_limit_minutes=10, max_attempts=2)
    staff = auth(client, STAFF)
    a = client.post(f"{API}/training/exams/{exam['id']}/attempts", headers=staff).json()
    deadline = datetime.fromisoformat(a["deadline_at"])
    assert timedelta(minutes=9) < deadline - datetime.fromisoformat(a["server_time"]) <= timedelta(minutes=10)
    answers = correct_answers(exam, a)
    first = a["questions"][0]["id"]
    client.put(
        f"{API}/training/attempts/{a['id']}/answers", json={"answers": {first: answers[first]}}, headers=staff
    )

    attempt = db.get(ExamAttempt, a["id"])
    attempt.deadline_at = datetime.now(UTC) - timedelta(minutes=5)
    db.commit()
    r = client.put(f"{API}/training/attempts/{a['id']}/answers", json={"answers": answers}, headers=staff)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "time_over"
    late = client.post(
        f"{API}/training/attempts/{a['id']}/submit", json={"answers": answers}, headers=staff
    ).json()
    # فقط پاسخ ذخیره‌شده پیش از مهلت حساب شد
    assert late["finished"] and late["score"] < 100
    # دفعه دوم مجاز است
    assert client.post(f"{API}/training/exams/{exam['id']}/attempts", headers=staff).status_code == 201


def test_question_validation(client):
    t = auth(client, TRAINER)
    e = client.post(f"{API}/admin/training/exams", json={"title": "x"}, headers=t).json()
    bad = [
        {
            "kind": "single",
            "text": "?",
            "options": [{"text": "a", "correct": True}, {"text": "b", "correct": True}],
        },
        {"kind": "multiple", "text": "?", "options": [{"text": "a"}, {"text": "b"}]},
        {"kind": "single", "text": "?", "options": [{"text": "a", "correct": True}]},
    ]
    for q in bad:
        assert (
            client.put(f"{API}/admin/training/exams/{e['id']}/questions", json=[q], headers=t).status_code
            == 422
        )
    r = client.post(
        f"{API}/admin/training/exams",
        json={"title": "x", "opens_at": "2026-10-20T10:00:00Z", "closes_at": "2026-10-19T10:00:00Z"},
        headers=t,
    )
    assert r.status_code == 422


def test_audience_rules():
    def user(uid="u1", unit="w", title="پرستار"):
        return SimpleNamespace(id=uid, unit_id=unit, job_title=title)

    def target(units=(), roles=(), titles=(), users=()):
        return SimpleNamespace(
            target_unit_ids=list(units),
            target_role_codes=list(roles),
            target_job_titles=list(titles),
            target_user_ids=list(users),
        )

    assert in_audience(target(), user(), {"staff"})
    assert in_audience(target(units=["w"], titles=["پرستار"]), user(), set())
    assert not in_audience(target(units=["w"], titles=["پزشک"]), user(), set())
    assert in_audience(target(units=["x"], users=["u1"]), user(), set())  # نام‌برده همیشه
    assert not in_audience(target(users=["u2"]), user(), set())
    assert not in_audience(target(roles=["unit_manager"]), user(), {"staff"})


def test_job_title_on_users_and_people_catalog(client, db):
    admin = auth(client, SYSADMIN)
    r = client.post(
        f"{API}/admin/users",
        json={
            "personnel_code": "2002",
            "full_name": "دکتر نمونه",
            "job_title": " پزشک ",
            "temporary_password": "Temp-12345",
            "roles": [{"role": "staff"}],
        },
        headers=admin,
    )
    assert r.json()["job_title"] == "پزشک"
    uid = r.json()["id"]
    assert (
        client.patch(f"{API}/admin/users/{uid}", json={"job_title": ""}, headers=admin).json()["job_title"]
        is None
    )
    assert db.scalar(select(User.job_title).where(User.personnel_code == STAFF)) == "پرستار"
    me = client.get(f"{API}/auth/me", headers=auth(client, STAFF)).json()
    assert me["job_title"] == "پرستار" and "training.read" in me["permissions"]

    t = auth(client, TRAINER)
    people = client.get(f"{API}/admin/training/people", headers=t).json()
    assert "پرستار" in people["job_titles"]
    codes = {p["personnel_code"] for p in people["users"]}
    assert STAFF in codes and SYSADMIN not in codes  # مدیر سیستم نقش آموزشی ندارد
    assert client.get(f"{API}/admin/units", headers=t).status_code == 200
    assert client.get(f"{API}/admin/training/people", headers=auth(client, STAFF)).status_code == 403
