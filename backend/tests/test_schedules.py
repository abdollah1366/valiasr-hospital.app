from datetime import date, datetime

from sqlalchemy import select

from app.core import jalali
from app.models import AuditLog, Role, Schedule, ShiftType, Unit, User
from app.services.schedule_logic import LOCAL_TZ
from tests.conftest import STAFF, SYSADMIN, UNIT_MANAGER, auth
from tests.test_notifications import inbox, kinds

API = "/api/v1"
MATRON, SUPERVISOR, NURSE2, IT_AGENT = "1009", "1010", "1011", "1007"
M, E, N, EN, OFF, LEAVE = 31, 32, 33, 35, 50, 20


def next_month() -> tuple[int, int]:
    j = jalali.from_gregorian(datetime.now(LOCAL_TZ).date())
    return (j.year + 1, 1) if j.month == 12 else (j.year, j.month + 1)


def uid(db, code: str) -> str:
    return str(db.scalar(select(User.id).where(User.personnel_code == code)))


def unit_id(db, name: str) -> str:
    return str(db.scalar(select(Unit.id).where(Unit.name == name)))


def create(client, h, db, unit="اورژانس", month=None, **extra):
    jy, jm = month or next_month()
    return client.post(
        f"{API}/schedules", json={"unit_id": unit_id(db, unit), "jyear": jy, "jmonth": jm, **extra}, headers=h
    )


def days_of(month=None) -> list[date]:
    return jalali.month_days(*(month or next_month()))


def cells(client, h, sid, items, reason=None):
    body = {"cells": [{"user_id": u, "day": d.isoformat(), "code": c} for u, d, c in items]}
    if reason:
        body["reason"] = reason
    return client.put(f"{API}/schedules/{sid}/entries", json=body, headers=h)


def member(detail, user_id):
    return next(m for m in detail["members"] if m["user_id"] == user_id)


def cell_map(detail, user_id):
    return {c["day"]: (c["code"], c["label"]) for c in member(detail, user_id)["cells"]}


def add_guest(client, h, db, sid):
    r = client.post(f"{API}/schedules/{sid}/members", json={"user_id": uid(db, NURSE2)}, headers=h)
    assert r.status_code == 201, r.text
    return r.json()


def publish(client, db, items):
    mgr, matron = auth(client, UNIT_MANAGER), auth(client, MATRON)
    sid = create(client, mgr, db).json()["id"]
    add_guest(client, mgr, db, sid)
    assert cells(client, mgr, sid, items).is_success
    assert client.post(f"{API}/schedules/{sid}/submit", headers=mgr).is_success
    r = client.post(f"{API}/schedules/{sid}/approve", headers=matron)
    assert r.status_code == 200, r.text
    return sid


def test_hospital_codes_and_roles_are_seeded(client, db):
    assert db.scalar(select(ShiftType).where(ShiftType.code == 61)).replaced_by == 1
    roles = {r.code: r for r in db.scalars(select(Role))}
    assert "schedule.approve" in roles["matron"].permissions
    assert "schedule.write" in roles["unit_manager"].permissions
    codes = {c["code"]: c for c in client.get(f"{API}/shift-types", headers=auth(client, STAFF)).json()}
    assert len(codes) == 149 and not codes[276]["is_active"]
    assert codes[EN]["label"] == "ع ش" and codes[EN]["work_hours"] == 18.5
    assert codes[37]["work_hours"] == 36 and codes[37]["span_days"] == 1
    assert codes[79]["review_note"] and codes[161]["review_note"]


def test_workflow_draft_submit_return_approve(client, db):
    mgr, matron, staff = auth(client, UNIT_MANAGER), auth(client, MATRON), auth(client, STAFF)
    assert create(client, staff, db).status_code == 403
    assert create(client, mgr, db, unit="ICU").status_code == 403  # فقط واحد خودش
    r = create(client, mgr, db)
    assert r.status_code == 201, r.text
    d = r.json()
    sid = d["id"]
    assert create(client, mgr, db).status_code == 409
    assert {m["personnel_code"] for m in d["members"]} == {STAFF, UNIT_MANAGER}
    assert d["status"] == "draft" and "submit" in d["actions"] and len(d["days"]) in (29, 30, 31)
    # نیروی قرضی از ICU
    d = add_guest(client, mgr, db, sid)
    assert member(d, uid(db, NURSE2))["is_guest"]
    assert (
        client.post(
            f"{API}/schedules/{sid}/members", json={"user_id": uid(db, NURSE2)}, headers=mgr
        ).status_code
        == 409
    )
    assert client.get(f"{API}/schedules/{sid}", headers=staff).status_code == 404  # پیش‌نویس
    assert client.get(f"{API}/schedules/{sid}", headers=matron).status_code == 200

    days = days_of()
    s1, s2 = uid(db, STAFF), uid(db, NURSE2)
    assert client.post(f"{API}/schedules/{sid}/submit", headers=mgr).status_code == 400  # خالی
    r = cells(client, mgr, sid, [(s1, days[0], 61)])
    assert r.status_code == 400 and "کد 1" in r.json()["detail"]["message"]
    assert cells(client, mgr, sid, [(s1, days[0], 150)]).json()["detail"]["code"] == "code_not_allowed"
    assert cells(client, mgr, sid, [(s1, days[0], 999)]).json()["detail"]["code"] == "unknown_code"
    assert cells(client, mgr, sid, [(uid(db, SYSADMIN), days[0], M)]).json()["detail"]["code"] == "not_member"
    assert cells(client, staff, sid, [(s1, days[0], M)]).status_code == 404

    r = cells(client, mgr, sid, [(s1, days[0], N), (s1, days[1], M), (s2, days[0], M), (s2, days[1], OFF)])
    assert r.status_code == 200, r.text
    d = r.json()
    assert cell_map(d, s1)[days[0].isoformat()] == (N, "ش")
    assert member(d, s1)["work_hours"] == 19.5
    assert [w["code"] for w in d["warnings"]] == ["overlap"]  # شب و بعد صبح
    cov = {c["date"]: c for c in d["coverage"]}
    assert cov[days[0].isoformat()] == {"date": days[0].isoformat(), "morning": 1, "evening": 0, "night": 1}

    r = client.post(f"{API}/schedules/{sid}/submit", headers=mgr)
    assert r.json()["status"] == "submitted" and r.json()["actions"] == ["export", "changes"]
    assert "schedule.submitted" in kinds(client, matron)
    assert cells(client, mgr, sid, [(s1, days[1], E)]).status_code == 409
    assert client.post(f"{API}/schedules/{sid}/approve", headers=mgr).status_code == 403
    assert client.post(f"{API}/schedules/{sid}/return", json={"note": " "}, headers=matron).status_code == 400
    r = client.post(f"{API}/schedules/{sid}/return", json={"note": "شب اول کم است"}, headers=matron)
    assert r.json()["status"] == "draft" and r.json()["return_note"] == "شب اول کم است"
    assert inbox(client, mgr)["items"][0]["kind"] == "schedule.returned"

    assert cells(client, mgr, sid, [(s1, days[1], OFF)]).json()["warnings"] == []
    client.post(f"{API}/schedules/{sid}/submit", headers=mgr)
    r = client.post(f"{API}/schedules/{sid}/approve", headers=matron)
    assert r.json()["status"] == "published" and r.json()["published_at"]
    for code in (STAFF, NURSE2):
        assert "schedule.published" in kinds(client, auth(client, code))

    # پس از انتشار: کارکنان همان بخش و سوپروایزر می‌بینند؛ واحد دیگر نه
    d = client.get(f"{API}/schedules/{sid}", headers=staff).json()
    assert d["warnings"] == [] and "swap" in d["actions"] and "edit" not in d["actions"]
    assert client.get(f"{API}/schedules/{sid}", headers=auth(client, SUPERVISOR)).status_code == 200
    assert client.get(f"{API}/schedules/{sid}", headers=auth(client, IT_AGENT)).status_code == 404
    assert client.delete(f"{API}/schedules/{sid}", headers=mgr).status_code == 409
    actions = [a.action for a in db.scalars(select(AuditLog).where(AuditLog.entity_id == sid))]
    assert actions.count("schedule.submit") == 2 and "schedule.approve" in actions


def test_listing_by_role(client, db):
    mgr = auth(client, UNIT_MANAGER)
    sid = create(client, mgr, db).json()["id"]
    jy, jm = next_month()
    q = {"jyear": jy, "jmonth": jm}
    staff_rows = client.get(f"{API}/schedules", params=q, headers=auth(client, STAFF)).json()
    assert [(r["unit_name"], r["schedule"]) for r in staff_rows] == [("اورژانس", None)]
    mgr_rows = {r["unit_name"]: r for r in client.get(f"{API}/schedules", params=q, headers=mgr).json()}
    assert mgr_rows["اورژانس"]["schedule"]["id"] == sid and not mgr_rows["اورژانس"]["can_create"]
    matron_rows = {
        r["unit_name"]: r
        for r in client.get(f"{API}/schedules", params=q, headers=auth(client, MATRON)).json()
    }
    assert matron_rows["اورژانس"]["schedule"]["status"] == "draft" and "ICU" in matron_rows
    assert not matron_rows["ICU"]["can_create"]
    sup_rows = {
        r["unit_name"]: r
        for r in client.get(f"{API}/schedules", params=q, headers=auth(client, SUPERVISOR)).json()
    }
    assert sup_rows["اورژانس"]["schedule"] is None  # پیش‌نویس برای سوپروایزر دیده نمی‌شود


def test_changes_after_publish_notify_only_affected_people(client, db):
    days = days_of()
    s1, s2 = uid(db, STAFF), uid(db, NURSE2)
    sid = publish(client, db, [(s1, days[3], M), (s2, days[3], E), (s2, days[5], LEAVE)])
    mgr = auth(client, UNIT_MANAGER)
    staff, nurse2 = auth(client, STAFF), auth(client, NURSE2)
    before = len(inbox(client, nurse2)["items"])
    assert cells(client, mgr, sid, [(s1, days[3], N)]).json()["detail"]["code"] == "reason_required"
    r = cells(
        client, mgr, sid, [(s1, days[3], N), (s1, days[4], OFF), (s2, days[3], E)], reason="کمبود نیروی شب"
    )
    assert r.status_code == 200, r.text
    note = inbox(client, staff)["items"][0]
    assert note["kind"] == "schedule.changed" and "ص ← ش" in note["body"]
    assert len(inbox(client, nurse2)["items"]) == before  # خانه او عوض نشد
    changes = client.get(f"{API}/schedules/{sid}/changes", headers=auth(client, MATRON)).json()
    assert {(c["old_label"], c["new_label"]) for c in changes} == {("ص", "ش"), (None, "OFF")}
    assert changes[0]["reason"] == "کمبود نیروی شب"
    assert client.get(f"{API}/schedules/{sid}/changes", headers=staff).status_code == 403

    # نوع مرخصی برای همکار پنهان است، برای خود فرد و مسئول نه
    leave_day = days[5].isoformat()
    assert cell_map(client.get(f"{API}/schedules/{sid}", headers=staff).json(), s2)[leave_day] == (
        None,
        "مرخصی",
    )
    assert cell_map(client.get(f"{API}/schedules/{sid}", headers=nurse2).json(), s2)[leave_day][0] == LEAVE
    assert cell_map(client.get(f"{API}/schedules/{sid}", headers=mgr).json(), s2)[leave_day][0] == LEAVE


def test_my_schedule_and_on_duty(client, db):
    days = days_of()
    s1 = uid(db, STAFF)
    publish(client, db, [(s1, days[2], N), (s1, days[0], M), (s1, days[1], OFF)])
    mine = client.get(f"{API}/schedules/mine", headers=auth(client, STAFF)).json()
    assert [i["code"] for i in mine["items"]] == [M, OFF, N]
    assert mine["next_shift"]["day"] == days[0].isoformat()
    assert mine["next_shift"]["starts_at"].startswith(f"{days[0].isoformat()}T07:30:00+03:30")
    assert client.get(f"{API}/schedules/mine", headers=auth(client, NURSE2)).json()["items"] == []

    sup = auth(client, SUPERVISOR)
    assert client.get(f"{API}/schedules/on-duty", headers=auth(client, STAFF)).status_code == 403
    r = client.get(f"{API}/schedules/on-duty", params={"day": days[3].isoformat()}, headers=sup).json()
    unit = next(u for u in r["units"] if u["unit_name"] == "اورژانس")
    # شب روز سوم تا صبح روز چهارم ادامه دارد
    assert unit["published"] and [p["code"] for p in unit["people"]] == [N]
    assert unit["people"][0]["periods"] == [] and r["now"] is None
    r = client.get(f"{API}/schedules/on-duty", params={"day": days[2].isoformat()}, headers=sup).json()
    assert next(u for u in r["units"] if u["unit_name"] == "اورژانس")["people"][0]["periods"] == ["night"]


def test_shift_swap_flow(client, db):
    days = days_of()
    s1, s2 = uid(db, STAFF), uid(db, NURSE2)
    sid = publish(client, db, [(s1, days[1], M), (s2, days[1], OFF), (s1, days[4], OFF), (s2, days[4], N)])
    staff, nurse2, mgr = auth(client, STAFF), auth(client, NURSE2), auth(client, UNIT_MANAGER)
    body = {
        "schedule_id": sid,
        "my_day": days[1].isoformat(),
        "partner_id": s2,
        "partner_day": days[4].isoformat(),
    }
    assert (
        client.post(f"{API}/schedules/swaps", json={**body, "partner_id": s1}, headers=staff).status_code
        == 400
    )
    r = client.post(f"{API}/schedules/swaps", json=body, headers=staff)
    assert r.status_code == 201, r.text
    w = r.json()
    assert w["status"] == "pending_partner" and w["actions"] == ["cancel"]
    assert w["requester_day"]["label"] == "ص" and w["partner_day"]["label"] == "ش"
    assert "schedule.swap" in kinds(client, nurse2)
    assert client.post(f"{API}/schedules/swaps/{w['id']}/approve", headers=mgr).status_code == 409
    r = client.post(f"{API}/schedules/swaps/{w['id']}/accept", headers=nurse2)
    assert r.json()["status"] == "pending_manager"
    pending = client.get(f"{API}/schedules/swaps", headers=mgr).json()
    assert [p["id"] for p in pending] == [w["id"]] and pending[0]["actions"] == ["approve", "reject"]
    r = client.post(f"{API}/schedules/swaps/{w['id']}/approve", json={"note": "باشد"}, headers=mgr)
    assert r.status_code == 200 and r.json()["status"] == "approved"
    d = client.get(f"{API}/schedules/{sid}", headers=mgr).json()
    assert cell_map(d, s1)[days[1].isoformat()][0] == OFF and cell_map(d, s1)[days[4].isoformat()][0] == N
    assert cell_map(d, s2)[days[1].isoformat()][0] == M and cell_map(d, s2)[days[4].isoformat()][0] == OFF
    assert len(client.get(f"{API}/schedules/{sid}/changes", headers=mgr).json()) == 4

    # درخواستی که پیش از تأیید، برنامه‌اش عوض شده کهنه است
    w2 = client.post(f"{API}/schedules/swaps", json=body, headers=staff).json()
    client.post(f"{API}/schedules/swaps/{w2['id']}/accept", headers=nurse2)
    cells(client, mgr, sid, [(s1, days[1], E)], reason="تغییر")
    assert (
        client.post(f"{API}/schedules/swaps/{w2['id']}/approve", headers=mgr).json()["detail"]["code"]
        == "stale"
    )
    assert client.post(f"{API}/schedules/swaps/{w2['id']}/reject", headers=mgr).json()["status"] == "rejected"
    assert client.post(f"{API}/schedules/swaps/{w2['id']}/cancel", headers=staff).status_code == 409


def test_copy_previous_month_and_export(client, db):
    mgr = auth(client, UNIT_MANAGER)
    jy, jm = next_month()
    ny, nm = (jy + 1, 1) if jm == 12 else (jy, jm + 1)
    s1 = uid(db, STAFF)
    sid = create(client, mgr, db).json()["id"]
    first = days_of()
    cells(client, mgr, sid, [(s1, first[0], M), (s1, first[1], 18)])
    db.get(ShiftType, 18).is_active = False
    db.commit()
    d = create(client, mgr, db, month=(ny, nm), copy_previous=True).json()
    nxt = days_of((ny, nm))
    assert cell_map(d, s1) == {nxt[0].isoformat(): (M, "ص")}  # کد غیرفعال کپی نشد
    assert len(d["members"]) == 2

    r = client.get(f"{API}/schedules/{sid}/export.csv", headers=mgr)
    assert r.status_code == 200 and r.text.startswith("﻿")
    lines = r.text.splitlines()
    assert "کد پرسنلی" in lines[1] and any(line.startswith(f"{STAFF},") and ",31," in line for line in lines)
    assert client.get(f"{API}/schedules/{sid}/export.csv", headers=auth(client, STAFF)).status_code == 404
    assert client.delete(f"{API}/schedules/{d['id']}", headers=mgr).status_code == 204
    assert db.scalar(select(Schedule).where(Schedule.jyear == ny, Schedule.jmonth == nm)) is None


def test_settings_codes_and_holidays_admin(client, db):
    admin, matron, staff, mgr = (auth(client, c) for c in (SYSADMIN, MATRON, STAFF, UNIT_MANAGER))
    emergency = unit_id(db, "اورژانس")
    assert client.put(f"{API}/admin/schedule-settings/{emergency}", json={}, headers=staff).status_code == 403
    body = {"shift_groups": ["general", "physician"], "min_staff": {"night": 2}, "required_monthly_hours": 10}
    r = client.put(f"{API}/admin/schedule-settings/{emergency}", json=body, headers=matron)
    assert r.status_code == 200 and not r.json()["is_default"]
    bad = {"shift_groups": ["general"], "min_staff": {"noon": 1}}
    assert (
        client.put(f"{API}/admin/schedule-settings/{emergency}", json=bad, headers=matron).status_code == 422
    )

    sid = create(client, mgr, db).json()["id"]
    days = days_of()
    d = cells(client, mgr, sid, [(uid(db, STAFF), days[0], 150)]).json()  # کد پزشکان اکنون مجاز است
    assert member(d, uid(db, STAFF))["overtime_hours"] == 8
    assert sum(1 for w in d["warnings"] if w["code"] == "understaffed") == len(days)  # حداقل ۲ نفر شب

    new = {
        "code": 300,
        "title": "صبح آزمایشی",
        "label": "ص*",
        "segments": [{"start": "08:00", "end": "14:00"}],
    }
    assert client.post(f"{API}/admin/shift-types", json=new, headers=staff).status_code == 403
    assert (
        client.post(f"{API}/admin/shift-types", json={**new, "segments": []}, headers=admin).status_code
        == 422
    )
    r = client.post(f"{API}/admin/shift-types", json=new, headers=admin)
    assert r.status_code == 201 and r.json()["work_hours"] == 6
    assert client.post(f"{API}/admin/shift-types", json=new, headers=admin).status_code == 409
    r = client.patch(
        f"{API}/admin/shift-types/300", json={"is_active": False, "replaced_by": 31}, headers=admin
    )
    assert r.json()["replaced_by"] == 31
    assert (
        client.patch(f"{API}/admin/shift-types/300", json={"replaced_by": 61}, headers=admin).status_code
        == 400
    )

    holiday = {"day": days[2].isoformat(), "title": "تعطیل نمونه"}
    assert client.post(f"{API}/admin/holidays", json=holiday, headers=admin).status_code == 201
    d = client.get(f"{API}/schedules/{sid}", headers=mgr).json()
    assert d["days"][2]["is_holiday"] and d["days"][2]["holiday_title"] == "تعطیل نمونه"
    listed = client.get(f"{API}/holidays", params={"jyear": next_month()[0]}, headers=staff).json()
    assert [h["title"] for h in listed] == ["تعطیل نمونه"]
    assert client.delete(f"{API}/admin/holidays/{days[2].isoformat()}", headers=admin).status_code == 204
