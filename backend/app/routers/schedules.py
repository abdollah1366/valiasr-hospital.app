"""برنامه شیفت ماهانه واحدها: نوشتن توسط مسئول واحد، تأیید مترون، دیدن برنامه خود و بخش، جابجایی شیفت.

دیدن برنامه خود و برنامه منتشرشده بخش خود برای همه کاربران فعال است و مجوز جدا ندارد.
"""

import csv
import io
import uuid
from collections import defaultdict
from datetime import UTC, date, datetime, timedelta

from fastapi import APIRouter, Depends, Query, Request, Response
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session, selectinload

from app.core import jalali
from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, build_current_user, client_ip, get_active_user, require
from app.models import (
    Holiday,
    Schedule,
    ScheduleChange,
    ScheduleEntry,
    ScheduleMember,
    ScheduleUnitSettings,
    ShiftSwap,
    ShiftType,
    Unit,
    User,
)
from app.schemas.schedules import (
    CellOut,
    ChangeOut,
    CoverageOut,
    DayOut,
    EntriesIn,
    HolidayIn,
    HolidayOut,
    MemberIn,
    MemberOut,
    MyScheduleOut,
    MyShiftOut,
    OnDutyOut,
    OnDutyPerson,
    OnDutyUnit,
    PersonOut,
    ReturnIn,
    ScheduleCreateIn,
    ScheduleDetailOut,
    ScheduleSummaryOut,
    SegmentIO,
    ShiftTypeIn,
    ShiftTypeOut,
    ShiftTypeUpdateIn,
    SwapAnswerIn,
    SwapCellOut,
    SwapIn,
    SwapOut,
    UnitScheduleRow,
    UnitSettingsIn,
    UnitSettingsOut,
    WarningOut,
)
from app.services import notifications as notes
from app.services.schedule_logic import (
    DEFAULT_GROUPS,
    LOCAL_TZ,
    SHIFT_GROUPS,
    Entry,
    Settings,
    Shift,
    coverage,
    covers,
    totals,
    warnings,
)

router = APIRouter(tags=["schedules"])

CONFIG = require("schedule.config")
SUPERVISE = require("schedule.read.all", "schedule.approve")

STATUS_NAMES = {"draft": "پیش‌نویس", "submitted": "در انتظار تأیید", "published": "منتشرشده"}
HIDDEN_LEAVE = "مرخصی"


def _now() -> datetime:
    return datetime.now(UTC)


def _local_now() -> datetime:
    return datetime.now(LOCAL_TZ)


def _jstr(d: date) -> str:
    return str(jalali.from_gregorian(d))


def _jlong(d: date) -> str:
    j = jalali.from_gregorian(d)
    return f"{j.day} {jalali.MONTH_NAMES[j.month - 1]}"


# ---------------------------------------------------------------- دسترسی


def _can_write(cu: CurrentUser, unit_id: uuid.UUID) -> bool:
    return cu.can_in_unit("schedule.write", unit_id)


def _can_approve(cu: CurrentUser, unit_id: uuid.UUID) -> bool:
    return cu.can_in_unit("schedule.approve", unit_id)


def _can_read_all(cu: CurrentUser, unit_id: uuid.UUID) -> bool:
    return cu.can_in_unit("schedule.read.all", unit_id) or _can_approve(cu, unit_id)


def _privileged(cu: CurrentUser, s: Schedule) -> bool:
    """نویسنده و تأییدکننده: پیش‌نویس، هشدارها و نوع مرخصی را می‌بینند."""
    return _can_write(cu, s.unit_id) or _can_approve(cu, s.unit_id)


def _is_member(db: Session, schedule_id: uuid.UUID, user_id: uuid.UUID) -> bool:
    return (
        db.scalar(
            select(ScheduleMember.id).where(
                ScheduleMember.schedule_id == schedule_id, ScheduleMember.user_id == user_id
            )
        )
        is not None
    )


def _can_view(db: Session, cu: CurrentUser, s: Schedule) -> bool:
    if _privileged(cu, s):
        return True
    if s.status != "published":
        return False
    return _can_read_all(cu, s.unit_id) or cu.user.unit_id == s.unit_id or _is_member(db, s.id, cu.id)


def _load(db: Session, schedule_id: uuid.UUID, cu: CurrentUser) -> Schedule:
    s = db.get(Schedule, schedule_id)
    if s is None or not _can_view(db, cu, s):
        raise api_error(404, "not_found", "برنامه پیدا نشد")
    return s


def _writable(db: Session, schedule_id: uuid.UUID, cu: CurrentUser) -> Schedule:
    s = _load(db, schedule_id, cu)
    if not _can_write(cu, s.unit_id):
        raise api_error(403, "forbidden", "فقط مسئول واحد برنامه را تغییر می‌دهد")
    if s.status == "submitted":
        raise api_error(409, "locked", "برنامه در انتظار تأیید است و تا تصمیم مترون تغییر نمی‌کند")
    return s


def _holders(db: Session, permission: str, unit_id: uuid.UUID) -> list[uuid.UUID]:
    """کاربران فعالی که این مجوز را برای این واحد دارند (مثلاً مترون‌ها یا مسئولان واحد)."""
    users = db.scalars(select(User).where(User.is_active.is_(True)).options(selectinload(User.roles)))
    return [u.id for u in users if build_current_user(u).can_in_unit(permission, unit_id)]


# ---------------------------------------------------------------- کدها و تنظیمات


def _shift_types(db: Session) -> dict[int, ShiftType]:
    return {st.code: st for st in db.scalars(select(ShiftType))}


def _logic(st: ShiftType) -> Shift:
    return Shift.from_json(st.code, st.kind, st.segments or [], st.hours, st.weekday)


def _shift_out(st: ShiftType) -> ShiftTypeOut:
    sh = _logic(st)
    return ShiftTypeOut(
        code=st.code,
        title=st.title,
        label=st.label,
        kind=st.kind,
        group=st.group,
        group_name=SHIFT_GROUPS.get(st.group, st.group),
        segments=[SegmentIO(**s) for s in st.segments or []],
        hours=st.hours,
        work_hours=sh.work_hours() if st.kind == "work" else 0,
        oncall_hours=sh.oncall_hours(),
        span_days=sh.span_days(),
        productivity=st.productivity,
        overtime=st.overtime,
        weekday=st.weekday,
        is_active=st.is_active,
        replaced_by=st.replaced_by,
        review_note=st.review_note,
    )


def _settings(db: Session, unit_id: uuid.UUID) -> tuple[Settings, UnitSettingsIn, bool]:
    row = db.get(ScheduleUnitSettings, unit_id)
    if row is None:
        io_ = UnitSettingsIn(shift_groups=DEFAULT_GROUPS)
    else:
        io_ = UnitSettingsIn(
            shift_groups=row.shift_groups or DEFAULT_GROUPS,
            min_staff=row.min_staff or {},
            max_consecutive=row.max_consecutive,
            min_rest_hours=row.min_rest_hours,
            required_monthly_hours=row.required_monthly_hours,
            max_monthly_hours=row.max_monthly_hours,
        )
    logic = Settings(
        min_staff=io_.min_staff,
        max_consecutive=io_.max_consecutive,
        min_rest_hours=io_.min_rest_hours,
        required_monthly_hours=io_.required_monthly_hours,
        max_monthly_hours=io_.max_monthly_hours,
    )
    return logic, io_, row is None


@router.get("/shift-types", response_model=list[ShiftTypeOut])
def shift_types(_: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(ShiftType).order_by(ShiftType.is_active.desc(), ShiftType.code))
    return [_shift_out(st) for st in rows]


def _check_replacement(db: Session, code: int, replaced_by: int | None) -> None:
    if replaced_by is None:
        return
    target = db.get(ShiftType, replaced_by)
    if replaced_by == code or target is None or not target.is_active:
        raise api_error(400, "invalid_replacement", "کد جایگزین باید یک کد فعال دیگر باشد")


@router.post("/admin/shift-types", response_model=ShiftTypeOut, status_code=201)
def create_shift_type(
    body: ShiftTypeIn, request: Request, cu: CurrentUser = Depends(CONFIG), db: Session = Depends(get_db)
):
    if db.get(ShiftType, body.code) is not None:
        raise api_error(409, "exists", f"کد {body.code} از قبل تعریف شده است")
    _check_replacement(db, body.code, body.replaced_by)
    data = body.model_dump()
    data["segments"] = [s.model_dump() for s in body.segments]
    st = ShiftType(**data)
    db.add(st)
    audit(db, "shift_type.create", cu.id, "shift_type", st.code, client_ip(request))
    db.commit()
    return _shift_out(st)


@router.patch("/admin/shift-types/{code}", response_model=ShiftTypeOut)
def update_shift_type(
    code: int,
    body: ShiftTypeUpdateIn,
    request: Request,
    cu: CurrentUser = Depends(CONFIG),
    db: Session = Depends(get_db),
):
    st = db.get(ShiftType, code)
    if st is None:
        raise api_error(404, "not_found", "کد پیدا نشد")
    data = body.model_dump(exclude_unset=True)
    if "group" in data and data["group"] not in SHIFT_GROUPS:
        raise api_error(400, "invalid_group", "گروه کد نامعتبر است")
    if "segments" in data:
        data["segments"] = [s.model_dump() for s in body.segments or []]
    for key in ("title", "label", "kind", "group", "segments", "productivity", "is_active"):
        if key in data and data[key] is None:
            raise api_error(400, "required", "این مقدار نمی‌تواند خالی باشد")
    for k, v in data.items():
        setattr(st, k, v)
    if st.kind == "work" and st.is_active and not st.segments:
        raise api_error(400, "segments_required", "کد کاری باید دست‌کم یک بازه ساعت داشته باشد")
    if "replaced_by" in data:
        _check_replacement(db, code, st.replaced_by)
    audit(db, "shift_type.update", cu.id, "shift_type", code, client_ip(request), fields=sorted(data))
    db.commit()
    return _shift_out(st)


def _settings_out(db: Session, unit: Unit) -> UnitSettingsOut:
    _, io_, is_default = _settings(db, unit.id)
    return UnitSettingsOut(**io_.model_dump(), unit_id=unit.id, unit_name=unit.name, is_default=is_default)


@router.get("/admin/schedule-settings", response_model=list[UnitSettingsOut])
def list_unit_settings(cu: CurrentUser = Depends(CONFIG), db: Session = Depends(get_db)):
    units = db.scalars(select(Unit).order_by(Unit.name))
    return [_settings_out(db, u) for u in units if cu.can_in_unit("schedule.config", u.id)]


@router.put("/admin/schedule-settings/{unit_id}", response_model=UnitSettingsOut)
def put_unit_settings(
    unit_id: uuid.UUID,
    body: UnitSettingsIn,
    request: Request,
    cu: CurrentUser = Depends(CONFIG),
    db: Session = Depends(get_db),
):
    unit = db.get(Unit, unit_id)
    if unit is None or not cu.can_in_unit("schedule.config", unit_id):
        raise api_error(404, "not_found", "واحد پیدا نشد")
    row = db.get(ScheduleUnitSettings, unit_id) or ScheduleUnitSettings(unit_id=unit_id)
    for k, v in body.model_dump().items():
        setattr(row, k, v)
    db.add(row)
    audit(db, "schedule.settings", cu.id, "unit", unit_id, client_ip(request))
    db.commit()
    return _settings_out(db, unit)


@router.get("/holidays", response_model=list[HolidayOut])
def list_holidays(
    jyear: int | None = Query(default=None, ge=1400, le=1500),
    _: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    jyear = jyear or jalali.from_gregorian(_local_now().date()).year
    start, end = jalali.to_gregorian(jyear, 1, 1), jalali.to_gregorian(jyear + 1, 1, 1)
    rows = db.scalars(select(Holiday).where(Holiday.day >= start, Holiday.day < end).order_by(Holiday.day))
    return [HolidayOut(day=h.day, title=h.title, jalali=_jstr(h.day)) for h in rows]


@router.post("/admin/holidays", response_model=HolidayOut, status_code=201)
def put_holiday(
    body: HolidayIn, request: Request, cu: CurrentUser = Depends(CONFIG), db: Session = Depends(get_db)
):
    h = db.get(Holiday, body.day) or Holiday(day=body.day)
    h.title = body.title.strip()
    db.add(h)
    audit(db, "holiday.set", cu.id, "holiday", body.day.isoformat(), client_ip(request))
    db.commit()
    return HolidayOut(day=h.day, title=h.title, jalali=_jstr(h.day))


@router.delete("/admin/holidays/{day}", status_code=204)
def delete_holiday(
    day: date, request: Request, cu: CurrentUser = Depends(CONFIG), db: Session = Depends(get_db)
):
    h = db.get(Holiday, day)
    if h is None:
        raise api_error(404, "not_found", "تعطیلی پیدا نشد")
    db.delete(h)
    audit(db, "holiday.delete", cu.id, "holiday", day.isoformat(), client_ip(request))
    db.commit()
    return Response(status_code=204)


# ---------------------------------------------------------------- خروجی برنامه


def _person(u: User | None) -> PersonOut | None:
    if u is None:
        return None
    return PersonOut(id=u.id, full_name=u.full_name, personnel_code=u.personnel_code, job_title=u.job_title)


def _title(s: Schedule, unit_name: str) -> str:
    return f"{unit_name} – {jalali.month_title(s.jyear, s.jmonth)}"


def _summary(s: Schedule, unit_name: str, member_count: int) -> ScheduleSummaryOut:
    return ScheduleSummaryOut(
        id=s.id,
        unit_id=s.unit_id,
        unit_name=unit_name,
        jyear=s.jyear,
        jmonth=s.jmonth,
        title=_title(s, unit_name),
        status=s.status,
        member_count=member_count,
        return_note=s.return_note,
        submitted_at=s.submitted_at,
        published_at=s.published_at,
        updated_at=s.updated_at,
    )


def _member_counts(db: Session, ids: list[uuid.UUID]) -> dict[uuid.UUID, int]:
    if not ids:
        return {}
    return dict(
        db.execute(
            select(ScheduleMember.schedule_id, func.count())
            .where(ScheduleMember.schedule_id.in_(ids))
            .group_by(ScheduleMember.schedule_id)
        ).all()
    )


def _days(db: Session, s: Schedule) -> list[DayOut]:
    days = jalali.month_days(s.jyear, s.jmonth)
    holidays = dict(
        db.execute(select(Holiday.day, Holiday.title).where(Holiday.day.between(days[0], days[-1]))).all()
    )
    return [
        DayOut(
            date=d,
            jalali=_jstr(d),
            day=i + 1,
            weekday=d.weekday(),
            weekday_name=jalali.WEEKDAY_NAMES[d.weekday()],
            is_holiday=d.weekday() == jalali.FRIDAY or d in holidays,
            holiday_title=holidays.get(d),
        )
        for i, d in enumerate(days)
    ]


def _members(db: Session, s: Schedule) -> list[tuple[ScheduleMember, User]]:
    return list(
        db.execute(
            select(ScheduleMember, User)
            .join(User, User.id == ScheduleMember.user_id)
            .where(ScheduleMember.schedule_id == s.id)
            .order_by(ScheduleMember.sort_order, User.full_name)
        )
        .tuples()
        .all()
    )


def _entries(db: Session, s: Schedule) -> list[ScheduleEntry]:
    return list(db.scalars(select(ScheduleEntry).where(ScheduleEntry.schedule_id == s.id)))


def _actions(db: Session, cu: CurrentUser, s: Schedule) -> list[str]:
    out = []
    if _can_write(cu, s.unit_id):
        if s.status == "draft":
            out += ["edit", "members", "submit"]
            if s.published_at is None:
                out.append("delete")
        elif s.status == "published":
            out += ["edit", "members"]
    if _can_approve(cu, s.unit_id) and s.status == "submitted":
        out += ["approve", "return"]
    if _privileged(cu, s) or _can_read_all(cu, s.unit_id):
        out += ["export", "changes"]
    if s.status == "published" and _is_member(db, s.id, cu.id):
        out.append("swap")
    return out


def _detail(db: Session, cu: CurrentUser, s: Schedule) -> ScheduleDetailOut:
    unit = db.get(Unit, s.unit_id)
    days = _days(db, s)
    month = [d.date for d in days]
    types = _shift_types(db)
    logic = {c: _logic(st) for c, st in types.items()}
    members = _members(db, s)
    entries = _entries(db, s)
    privileged = _privileged(cu, s)
    settings, settings_io, _ = _settings(db, s.unit_id)
    own = [Entry(e.user_id, e.day, e.code) for e in entries]
    tot = totals(own, logic, settings)
    by_user: dict[uuid.UUID, dict[date, int]] = defaultdict(dict)
    for e in entries:
        by_user[e.user_id][e.day] = e.code

    out_members = []
    for m, u in members:
        cells = []
        for d, code in sorted(by_user.get(u.id, {}).items()):
            st = types[code]
            if st.kind == "leave" and not privileged and u.id != cu.id:
                cells.append(CellOut(day=d, code=None, label=HIDDEN_LEAVE, kind="leave", span_days=0))
            else:
                cells.append(
                    CellOut(day=d, code=code, label=st.label, kind=st.kind, span_days=logic[code].span_days())
                )
        t = tot.get(u.id, {"work": 0.0, "oncall": 0.0, "overtime": 0.0})
        out_members.append(
            MemberOut(
                user_id=u.id,
                full_name=u.full_name,
                personnel_code=u.personnel_code,
                job_title=u.job_title,
                is_guest=m.is_guest,
                cells=cells,
                work_hours=t["work"],
                oncall_hours=t["oncall"],
                overtime_hours=t["overtime"] if settings.required_monthly_hours is not None else None,
            )
        )

    cov = coverage(own, logic, month)
    warns: list[WarningOut] = []
    if privileged:
        ids = [u.id for _, u in members]
        # شیفت‌های همین افراد در برنامه‌های دیگر (بخش دیگر یا ماه کناری) برای همپوشانی و استراحت
        others = db.scalars(
            select(ScheduleEntry).where(
                ScheduleEntry.user_id.in_(ids),
                ScheduleEntry.schedule_id != s.id,
                ScheduleEntry.day.between(month[0] - timedelta(days=3), month[-1] + timedelta(days=3)),
            )
        )
        context = own + [Entry(e.user_id, e.day, e.code, own=False) for e in others]
        warns = [
            WarningOut(code=w.code, message=w.message, user_id=w.user_id, day=w.day, period=w.period)
            for w in warnings(context, logic, settings, month)
        ]
    return ScheduleDetailOut(
        **_summary(s, unit.name, len(members)).model_dump(),
        days=days,
        members=out_members,
        coverage=[CoverageOut(date=d, **cov[d]) for d in month],
        warnings=warns,
        settings=settings_io,
        actions=_actions(db, cu, s),
    )


# ---------------------------------------------------------------- فهرست و ساخت


def _current_month() -> tuple[int, int]:
    j = jalali.from_gregorian(_local_now().date())
    return j.year, j.month


@router.get("/schedules", response_model=list[UnitScheduleRow])
def list_schedules(
    jyear: int | None = Query(default=None, ge=1400, le=1500),
    jmonth: int | None = Query(default=None, ge=1, le=12),
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    """برنامه هر واحد در یک ماه، فقط واحدهایی که کاربر می‌تواند ببیند."""
    if jyear is None or jmonth is None:
        jyear, jmonth = _current_month()
    units = list(db.scalars(select(Unit).order_by(Unit.name)))
    schedules = {
        s.unit_id: s
        for s in db.scalars(select(Schedule).where(Schedule.jyear == jyear, Schedule.jmonth == jmonth))
    }
    mine = set(
        db.scalars(
            select(ScheduleMember.schedule_id).where(
                ScheduleMember.user_id == cu.id,
                ScheduleMember.schedule_id.in_([s.id for s in schedules.values()]),
            )
        )
    )
    counts = _member_counts(db, [s.id for s in schedules.values()])
    rows = []
    for u in units:
        s = schedules.get(u.id)
        editor = _can_write(cu, u.id) or _can_approve(cu, u.id)
        if editor or _can_read_all(cu, u.id):
            visible = s if s and (editor or s.status == "published") else None
            show = True
        else:
            own_unit = u.id == cu.user.unit_id
            visible = s if s and s.status == "published" and (own_unit or s.id in mine) else None
            show = own_unit or visible is not None
        if show:
            rows.append(
                UnitScheduleRow(
                    unit_id=u.id,
                    unit_name=u.name,
                    unit_type=u.type,
                    schedule=_summary(visible, u.name, counts.get(visible.id, 0)) if visible else None,
                    can_create=s is None and _can_write(cu, u.id),
                )
            )
    return rows


@router.post("/schedules", response_model=ScheduleDetailOut, status_code=201)
def create_schedule(
    body: ScheduleCreateIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    unit = db.get(Unit, body.unit_id)
    if unit is None or not _can_write(cu, body.unit_id):
        raise api_error(403, "forbidden", "فقط مسئول واحد برنامه واحد خود را می‌نویسد")
    exists = db.scalar(
        select(Schedule.id).where(
            Schedule.unit_id == body.unit_id, Schedule.jyear == body.jyear, Schedule.jmonth == body.jmonth
        )
    )
    if exists:
        raise api_error(409, "exists", "برنامه این ماه برای این واحد از قبل ساخته شده است")
    s = Schedule(unit_id=unit.id, jyear=body.jyear, jmonth=body.jmonth, status="draft", created_by=cu.id)
    db.add(s)
    db.flush()

    prev = None
    if body.copy_previous:
        py, pm = jalali.previous_month(body.jyear, body.jmonth)
        prev = db.scalar(
            select(Schedule).where(Schedule.unit_id == unit.id, Schedule.jyear == py, Schedule.jmonth == pm)
        )
    if prev is not None:
        active = set(db.scalars(select(User.id).where(User.is_active.is_(True))))
        for m in db.scalars(select(ScheduleMember).where(ScheduleMember.schedule_id == prev.id)):
            if m.user_id in active:
                db.add(
                    ScheduleMember(
                        schedule_id=s.id, user_id=m.user_id, is_guest=m.is_guest, sort_order=m.sort_order
                    )
                )
        # همان روز ماه (روز ۱ به روز ۱)؛ کدهای غیرفعال یا نامجاز کپی نمی‌شوند.
        types = _shift_types(db)
        _, settings_io, _ = _settings(db, unit.id)
        new_days = jalali.month_days(body.jyear, body.jmonth)
        prev_days = jalali.month_days(prev.jyear, prev.jmonth)
        index = {d: i for i, d in enumerate(prev_days)}
        for e in _entries(db, prev):
            st = types.get(e.code)
            i = index.get(e.day)
            if (
                e.user_id in active
                and i is not None
                and i < len(new_days)
                and st is not None
                and st.is_active
                and st.group in settings_io.shift_groups
            ):
                db.add(ScheduleEntry(schedule_id=s.id, user_id=e.user_id, day=new_days[i], code=e.code))
    else:
        users = db.scalars(
            select(User).where(User.unit_id == unit.id, User.is_active.is_(True)).order_by(User.full_name)
        )
        for i, u in enumerate(users):
            db.add(ScheduleMember(schedule_id=s.id, user_id=u.id, sort_order=i))
    audit(
        db,
        "schedule.create",
        cu.id,
        "schedule",
        s.id,
        client_ip(request),
        month=f"{body.jyear}/{body.jmonth:02d}",
        copied=prev is not None or None,
    )
    db.commit()
    return _detail(db, cu, s)


# ---------------------------------------------------------------- برنامه من و کشیک امروز


def _bounds(sh: Shift, day: date, oncall: bool | None = None) -> tuple[datetime, datetime] | None:
    iv = sh.intervals(day, oncall=oncall)
    if not iv:
        return None
    return min(a for a, _ in iv).replace(tzinfo=LOCAL_TZ), max(b for _, b in iv).replace(tzinfo=LOCAL_TZ)


def _my_rows(db: Session, user_id: uuid.UUID, start: date, end: date):
    return (
        db.execute(
            select(ScheduleEntry, Schedule, ShiftType, Unit.name)
            .join(Schedule, Schedule.id == ScheduleEntry.schedule_id)
            .join(ShiftType, ShiftType.code == ScheduleEntry.code)
            .join(Unit, Unit.id == Schedule.unit_id)
            .where(
                ScheduleEntry.user_id == user_id,
                Schedule.status == "published",
                ScheduleEntry.day.between(start, end),
            )
            .order_by(ScheduleEntry.day)
        )
        .tuples()
        .all()
    )


def _my_item(e: ScheduleEntry, s: Schedule, st: ShiftType, unit_name: str) -> MyShiftOut:
    sh = _logic(st)
    b = _bounds(sh, e.day)
    return MyShiftOut(
        day=e.day,
        jalali=_jstr(e.day),
        code=st.code,
        label=st.label,
        title=st.title,
        kind=st.kind,
        unit_id=s.unit_id,
        unit_name=unit_name,
        schedule_id=s.id,
        starts_at=b[0] if b else None,
        ends_at=b[1] if b else None,
        work_hours=sh.work_hours() if st.kind == "work" else 0,
    )


@router.get("/schedules/mine", response_model=MyScheduleOut)
def my_schedule(
    start: date | None = None,
    end: date | None = None,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    """شیفت‌های منتشرشده خود کاربر؛ پیش‌فرض ماه جاری و ماه بعد."""
    now = _local_now()
    if start is None or end is None:
        jy, jm = _current_month()
        ny, nm = (jy + 1, 1) if jm == 12 else (jy, jm + 1)
        start, end = jalali.to_gregorian(jy, jm, 1), jalali.month_days(ny, nm)[-1]
    if end < start or (end - start).days > 100:
        raise api_error(400, "invalid_range", "بازه تاریخ نامعتبر است (حداکثر ۱۰۰ روز)")
    items = [_my_item(*row) for row in _my_rows(db, cu.id, start, end)]
    next_shift = None
    for row in _my_rows(db, cu.id, now.date() - timedelta(days=3), now.date() + timedelta(days=62)):
        item = _my_item(*row)
        if item.kind == "work" and item.ends_at and item.ends_at > now:
            if next_shift is None or item.starts_at < next_shift.starts_at:
                next_shift = item
    return MyScheduleOut(start=start, end=end, items=items, next_shift=next_shift)


@router.get("/schedules/on-duty", response_model=OnDutyOut)
def on_duty(
    day: date | None = None,
    cu: CurrentUser = Depends(SUPERVISE),
    db: Session = Depends(get_db),
):
    """کشیک یک روز در همه واحدهای قابل دید: چه کسی در کدام نوبت سر کار یا آنکال است."""
    now = _local_now()
    d = day or now.date()
    window = (
        datetime.combine(d, datetime.min.time()),
        datetime.combine(d + timedelta(days=1), datetime.min.time()),
    )
    units = [u for u in db.scalars(select(Unit).order_by(Unit.name)) if _can_read_all(cu, u.id)]
    unit_ids = [u.id for u in units]
    j = jalali.from_gregorian(d)
    published = set(
        db.scalars(
            select(Schedule.unit_id).where(
                Schedule.unit_id.in_(unit_ids),
                Schedule.jyear == j.year,
                Schedule.jmonth == j.month,
                Schedule.status == "published",
            )
        )
    )
    rows = db.execute(
        select(ScheduleEntry, Schedule.unit_id, ShiftType, User)
        .join(Schedule, Schedule.id == ScheduleEntry.schedule_id)
        .join(ShiftType, ShiftType.code == ScheduleEntry.code)
        .join(User, User.id == ScheduleEntry.user_id)
        .where(
            Schedule.status == "published",
            Schedule.unit_id.in_(unit_ids),
            ScheduleEntry.day.between(d - timedelta(days=3), d),
            ShiftType.kind == "work",
        )
    ).tuples()
    naive_now = now.replace(tzinfo=None)
    people: dict[uuid.UUID, list[OnDutyPerson]] = defaultdict(list)
    for e, unit_id, st, u in rows:
        sh = _logic(st)
        iv = sh.intervals(e.day)
        if not any(a < window[1] and b > window[0] for a, b in iv):
            continue
        b = _bounds(sh, e.day)
        people[unit_id].append(
            OnDutyPerson(
                person=_person(u),
                mobile=u.mobile,
                code=st.code,
                label=st.label,
                starts_at=b[0],
                ends_at=b[1],
                periods=sorted(covers(sh, e.day).get(d, set()), key=["morning", "evening", "night"].index),
                on_duty_now=d == now.date()
                and any(a <= naive_now < b for a, b in sh.intervals(e.day, False)),
                oncall_now=d == now.date() and any(a <= naive_now < b for a, b in sh.intervals(e.day, True)),
            )
        )
    out = []
    for u in units:
        if u.id in published or people.get(u.id):
            ps = sorted(people.get(u.id, []), key=lambda p: (p.starts_at, p.person.full_name))
            out.append(OnDutyUnit(unit_id=u.id, unit_name=u.name, published=u.id in published, people=ps))
    return OnDutyOut(day=d, jalali=_jstr(d), now=now if d == now.date() else None, units=out)


# ---------------------------------------------------------------- جابجایی شیفت


def _code_at(db: Session, schedule_id: uuid.UUID, user_id: uuid.UUID, day: date) -> int | None:
    return db.scalar(
        select(ScheduleEntry.code).where(
            ScheduleEntry.schedule_id == schedule_id,
            ScheduleEntry.user_id == user_id,
            ScheduleEntry.day == day,
        )
    )


def _set_code(db: Session, schedule_id: uuid.UUID, user_id: uuid.UUID, day: date, code: int | None) -> None:
    e = db.scalar(
        select(ScheduleEntry).where(
            ScheduleEntry.schedule_id == schedule_id,
            ScheduleEntry.user_id == user_id,
            ScheduleEntry.day == day,
        )
    )
    if code is None:
        if e is not None:
            db.delete(e)
    elif e is None:
        db.add(ScheduleEntry(schedule_id=schedule_id, user_id=user_id, day=day, code=code))
    else:
        e.code = code


def _snapshot(db: Session, w: ShiftSwap) -> dict:
    return {
        "a1": _code_at(db, w.schedule_id, w.requester_id, w.requester_day),
        "b1": _code_at(db, w.schedule_id, w.partner_id, w.requester_day),
        "a2": _code_at(db, w.schedule_id, w.requester_id, w.partner_day),
        "b2": _code_at(db, w.schedule_id, w.partner_id, w.partner_day),
    }


def _swap_actions(cu: CurrentUser, w: ShiftSwap, unit_id: uuid.UUID) -> list[str]:
    out = []
    if w.status == "pending_partner" and cu.id == w.partner_id:
        out += ["accept", "reject"]
    if w.status == "pending_manager" and _can_write(cu, unit_id):
        out += ["approve", "reject"]
    if w.status in ("pending_partner", "pending_manager") and cu.id == w.requester_id:
        out.append("cancel")
    return out


def _swap_out(db: Session, cu: CurrentUser, w: ShiftSwap) -> SwapOut:
    s = db.get(Schedule, w.schedule_id)
    unit = db.get(Unit, s.unit_id)
    types = _shift_types(db)
    people = {
        u.id: u
        for u in db.scalars(select(User).where(User.id.in_({w.requester_id, w.partner_id, w.decided_by})))
    }

    def cell(day: date, code: int | None) -> SwapCellOut:
        return SwapCellOut(
            day=day, jalali=_jstr(day), code=code, label=types[code].label if code in types else None
        )

    snap = w.snapshot or {}
    return SwapOut(
        id=w.id,
        schedule_id=w.schedule_id,
        unit_name=unit.name,
        month_title=jalali.month_title(s.jyear, s.jmonth),
        requester=_person(people[w.requester_id]),
        partner=_person(people[w.partner_id]),
        requester_day=cell(w.requester_day, snap.get("a1")),
        partner_day=cell(w.partner_day, snap.get("b2")),
        note=w.note,
        status=w.status,
        created_at=w.created_at,
        decided_by=_person(people.get(w.decided_by)),
        decided_at=w.decided_at,
        decision_note=w.decision_note,
        actions=_swap_actions(cu, w, s.unit_id),
    )


@router.get("/schedules/swaps", response_model=list[SwapOut])
def list_swaps(cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)):
    """جابجایی‌های خود کاربر و جابجایی‌هایی که مسئول واحد باید تأیید کند."""
    managed = list(cu.units_for("schedule.write"))
    conds = [ShiftSwap.requester_id == cu.id, ShiftSwap.partner_id == cu.id]
    if cu.has_global("schedule.write"):
        conds.append(ShiftSwap.status == "pending_manager")
    elif managed:
        conds.append(
            (ShiftSwap.status == "pending_manager")
            & ShiftSwap.schedule_id.in_(select(Schedule.id).where(Schedule.unit_id.in_(managed)))
        )
    rows = db.scalars(select(ShiftSwap).where(or_(*conds)).order_by(ShiftSwap.created_at.desc()).limit(100))
    return [_swap_out(db, cu, w) for w in rows]


@router.post("/schedules/swaps", response_model=SwapOut, status_code=201)
def create_swap(
    body: SwapIn, request: Request, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)
):
    s = _load(db, body.schedule_id, cu)
    if s.status != "published":
        raise api_error(409, "not_published", "جابجایی فقط در برنامه منتشرشده ممکن است")
    if body.partner_id == cu.id:
        raise api_error(400, "same_person", "همکار دیگری را انتخاب کنید")
    if not (_is_member(db, s.id, cu.id) and _is_member(db, s.id, body.partner_id)):
        raise api_error(400, "not_member", "هر دو نفر باید در همین برنامه باشند")
    days = set(jalali.month_days(s.jyear, s.jmonth))
    today = _local_now().date()
    for d in (body.my_day, body.partner_day):
        if d not in days:
            raise api_error(400, "invalid_day", "روز در ماه این برنامه نیست")
        if d < today:
            raise api_error(400, "past_day", "روزهای گذشته جابجا نمی‌شوند")
    w = ShiftSwap(
        schedule_id=s.id,
        requester_id=cu.id,
        partner_id=body.partner_id,
        requester_day=body.my_day,
        partner_day=body.partner_day,
        note=(body.note or "").strip() or None,
        status="pending_partner",
    )
    w.snapshot = _snapshot(db, w)
    snap = w.snapshot
    if snap["a1"] == snap["b1"] and snap["a2"] == snap["b2"]:
        raise api_error(400, "nothing_to_swap", "با این جابجایی چیزی در برنامه عوض نمی‌شود")
    db.add(w)
    db.flush()
    notes.notify(
        db,
        [body.partner_id],
        "schedule.swap",
        f"درخواست جابجایی شیفت از {cu.user.full_name}",
        body=f"{_jlong(body.my_day)} و {_jlong(body.partner_day)}",
        link_type="schedule",
        link_id=s.id,
        actor_id=cu.id,
    )
    audit(db, "schedule.swap.create", cu.id, "shift_swap", w.id, client_ip(request))
    db.commit()
    return _swap_out(db, cu, w)


def _swap(db: Session, swap_id: uuid.UUID) -> tuple[ShiftSwap, Schedule]:
    w = db.get(ShiftSwap, swap_id)
    if w is None:
        raise api_error(404, "not_found", "درخواست جابجایی پیدا نشد")
    return w, db.get(Schedule, w.schedule_id)


@router.post("/schedules/swaps/{swap_id}/{action}", response_model=SwapOut)
def answer_swap(
    swap_id: uuid.UUID,
    action: str,
    request: Request,
    body: SwapAnswerIn | None = None,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    w, s = _swap(db, swap_id)
    allowed = _swap_actions(cu, w, s.unit_id)
    if cu.id not in (w.requester_id, w.partner_id) and not _can_write(cu, s.unit_id):
        raise api_error(404, "not_found", "درخواست جابجایی پیدا نشد")
    if action not in ("accept", "reject", "approve", "cancel"):
        raise api_error(404, "not_found", "عمل نامعتبر است")
    if action not in allowed:
        raise api_error(409, "invalid_transition", "این کار در وضعیت فعلی ممکن نیست")
    now = _now()
    note = ((body.note if body else None) or "").strip() or None
    unit = db.get(Unit, s.unit_id)
    requester = db.get(User, w.requester_id)

    def tell(to, title):
        notes.notify(
            db, to, "schedule.swap", title, body=note, link_type="schedule", link_id=s.id, actor_id=cu.id
        )

    if action == "accept":
        w.status, w.partner_answered_at = "pending_manager", now
        tell(
            _holders(db, "schedule.write", s.unit_id), f"جابجایی شیفت {requester.full_name} منتظر تأیید شماست"
        )
    elif action == "cancel":
        w.status = "cancelled"
    elif action == "reject":
        if w.status == "pending_partner":
            w.partner_answered_at = now
        else:
            w.decided_by, w.decided_at = cu.id, now
        w.status, w.decision_note = "rejected", note
        tell([w.requester_id, w.partner_id], "درخواست جابجایی شیفت رد شد")
    else:  # approve
        if s.status != "published" or _snapshot(db, w) != (w.snapshot or {}):
            raise api_error(409, "stale", "برنامه این روزها پس از درخواست تغییر کرده است؛ درخواست را رد کنید")
        snap = w.snapshot
        moves = [
            (w.requester_id, w.requester_day, snap["a1"], snap["b1"]),
            (w.partner_id, w.requester_day, snap["b1"], snap["a1"]),
        ]
        if w.partner_day != w.requester_day:
            moves += [
                (w.requester_id, w.partner_day, snap["a2"], snap["b2"]),
                (w.partner_id, w.partner_day, snap["b2"], snap["a2"]),
            ]
        for user_id, day, old, new in moves:
            if old != new:
                _set_code(db, s.id, user_id, day, new)
                db.add(
                    ScheduleChange(
                        schedule_id=s.id,
                        user_id=user_id,
                        day=day,
                        old_code=old,
                        new_code=new,
                        reason="جابجایی شیفت",
                        changed_by=cu.id,
                    )
                )
        w.status, w.decided_by, w.decided_at, w.decision_note = "approved", cu.id, now, note
        s.updated_at = now
        tell([w.requester_id, w.partner_id], f"جابجایی شیفت در برنامه {unit.name} تأیید شد")
    audit(db, f"schedule.swap.{action}", cu.id, "shift_swap", w.id, client_ip(request))
    db.commit()
    return _swap_out(db, cu, w)


# ---------------------------------------------------------------- یک برنامه


@router.get("/schedules/{schedule_id}", response_model=ScheduleDetailOut)
def get_schedule(
    schedule_id: uuid.UUID, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)
):
    return _detail(db, cu, _load(db, schedule_id, cu))


@router.delete("/schedules/{schedule_id}", status_code=204)
def delete_schedule(
    schedule_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    s = _writable(db, schedule_id, cu)
    if s.status != "draft" or s.published_at is not None:
        raise api_error(409, "not_draft", "فقط پیش‌نویسی که هرگز منتشر نشده حذف می‌شود")
    audit(db, "schedule.delete", cu.id, "schedule", s.id, client_ip(request))
    db.delete(s)
    db.commit()
    return Response(status_code=204)


def _change_text(changes: list[tuple[date, int | None, int | None]], types: dict[int, ShiftType]) -> str:
    def lab(c):
        return types[c].label if c in types else "خالی"

    parts = [f"{_jlong(d)}: {lab(o)} ← {lab(n)}" for d, o, n in sorted(changes)[:3]]
    more = len(changes) - 3
    return "؛ ".join(parts) + (f" و {more} مورد دیگر" if more > 0 else "")


@router.put("/schedules/{schedule_id}/entries", response_model=ScheduleDetailOut)
def save_entries(
    schedule_id: uuid.UUID,
    body: EntriesIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    s = _writable(db, schedule_id, cu)
    reason = (body.reason or "").strip()
    if s.status == "published" and not reason:
        raise api_error(400, "reason_required", "برای تغییر برنامه منتشرشده علت را بنویسید")
    days = set(jalali.month_days(s.jyear, s.jmonth))
    members = {m.user_id for m, _ in _members(db, s)}
    types = _shift_types(db)
    _, settings_io, _ = _settings(db, s.unit_id)
    cells: dict[tuple[uuid.UUID, date], int | None] = {}
    for c in body.cells:
        if c.day not in days:
            raise api_error(400, "invalid_day", "روز در ماه این برنامه نیست")
        if c.user_id not in members:
            raise api_error(400, "not_member", "این نفر در برنامه نیست")
        if c.code is not None:
            st = types.get(c.code)
            if st is None:
                raise api_error(400, "unknown_code", f"کد {c.code} تعریف نشده است")
            if not st.is_active:
                hint = f"؛ به جای آن کد {st.replaced_by}" if st.replaced_by else ""
                raise api_error(400, "inactive_code", f"کد {c.code} دیگر استفاده نمی‌شود{hint}")
            if st.group not in settings_io.shift_groups:
                raise api_error(
                    400,
                    "code_not_allowed",
                    f"کد {c.code} ({SHIFT_GROUPS.get(st.group, st.group)}) برای این واحد مجاز نیست",
                )
        cells[(c.user_id, c.day)] = c.code

    existing = {(e.user_id, e.day): e for e in _entries(db, s)}
    changed: list[tuple[uuid.UUID, date, int | None, int | None]] = []
    for (user_id, day), code in cells.items():
        e = existing.get((user_id, day))
        old = e.code if e else None
        if old == code:
            continue
        if code is None:
            db.delete(e)
        elif e is None:
            db.add(ScheduleEntry(schedule_id=s.id, user_id=user_id, day=day, code=code))
        else:
            e.code = code
        changed.append((user_id, day, old, code))

    if changed:
        s.updated_at = _now()
    if s.status == "published" and changed:
        unit = db.get(Unit, s.unit_id)
        per_user: dict[uuid.UUID, list] = defaultdict(list)
        for user_id, day, old, new in changed:
            db.add(
                ScheduleChange(
                    schedule_id=s.id,
                    user_id=user_id,
                    day=day,
                    old_code=old,
                    new_code=new,
                    reason=reason,
                    changed_by=cu.id,
                )
            )
            per_user[user_id].append((day, old, new))
        for user_id, items in per_user.items():
            notes.notify(
                db,
                [user_id],
                "schedule.changed",
                f"برنامه شما در {unit.name} تغییر کرد",
                body=_change_text(items, types),
                link_type="schedule",
                link_id=s.id,
                important=any(d <= _local_now().date() + timedelta(days=2) for d, _, _ in items),
                actor_id=cu.id,
            )
        audit(db, "schedule.change", cu.id, "schedule", s.id, client_ip(request), cells=len(changed))
    db.commit()
    return _detail(db, cu, s)


@router.post("/schedules/{schedule_id}/members", response_model=ScheduleDetailOut, status_code=201)
def add_member(
    schedule_id: uuid.UUID,
    body: MemberIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    s = _writable(db, schedule_id, cu)
    u = db.get(User, body.user_id)
    if u is None or not u.is_active:
        raise api_error(400, "invalid_user", "کاربر پیدا نشد یا غیرفعال است")
    if _is_member(db, s.id, u.id):
        raise api_error(409, "exists", "این نفر از قبل در برنامه است")
    last = db.scalar(select(func.max(ScheduleMember.sort_order)).where(ScheduleMember.schedule_id == s.id))
    db.add(
        ScheduleMember(
            schedule_id=s.id, user_id=u.id, is_guest=u.unit_id != s.unit_id, sort_order=(last or 0) + 1
        )
    )
    audit(db, "schedule.member.add", cu.id, "schedule", s.id, client_ip(request), user=str(u.id))
    db.commit()
    return _detail(db, cu, s)


@router.delete("/schedules/{schedule_id}/members/{user_id}", response_model=ScheduleDetailOut)
def remove_member(
    schedule_id: uuid.UUID,
    user_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    s = _writable(db, schedule_id, cu)
    if s.status != "draft":
        raise api_error(409, "published", "در برنامه منتشرشده به جای حذف، خانه‌های این نفر را خالی کنید")
    m = db.scalar(
        select(ScheduleMember).where(ScheduleMember.schedule_id == s.id, ScheduleMember.user_id == user_id)
    )
    if m is None:
        raise api_error(404, "not_found", "این نفر در برنامه نیست")
    for e in db.scalars(
        select(ScheduleEntry).where(ScheduleEntry.schedule_id == s.id, ScheduleEntry.user_id == user_id)
    ):
        db.delete(e)
    db.delete(m)
    audit(db, "schedule.member.remove", cu.id, "schedule", s.id, client_ip(request), user=str(user_id))
    db.commit()
    return _detail(db, cu, s)


@router.post("/schedules/{schedule_id}/submit", response_model=ScheduleDetailOut)
def submit_schedule(
    schedule_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    s = _writable(db, schedule_id, cu)
    if s.status != "draft":
        raise api_error(409, "invalid_transition", "فقط پیش‌نویس برای تأیید فرستاده می‌شود")
    if not _entries(db, s):
        raise api_error(400, "empty", "برنامه هنوز خالی است")
    s.status, s.submitted_at, s.submitted_by, s.return_note = "submitted", _now(), cu.id, None
    unit = db.get(Unit, s.unit_id)
    notes.notify(
        db,
        _holders(db, "schedule.approve", s.unit_id),
        "schedule.submitted",
        f"برنامه {_title(s, unit.name)} برای تأیید فرستاده شد",
        link_type="schedule",
        link_id=s.id,
        actor_id=cu.id,
    )
    audit(db, "schedule.submit", cu.id, "schedule", s.id, client_ip(request))
    db.commit()
    return _detail(db, cu, s)


def _reviewable(db: Session, schedule_id: uuid.UUID, cu: CurrentUser) -> Schedule:
    s = _load(db, schedule_id, cu)
    if not _can_approve(cu, s.unit_id):
        raise api_error(403, "forbidden", "فقط مترون برنامه را تأیید یا برمی‌گرداند")
    if s.status != "submitted":
        raise api_error(409, "invalid_transition", "این برنامه در انتظار تأیید نیست")
    return s


@router.post("/schedules/{schedule_id}/approve", response_model=ScheduleDetailOut)
def approve_schedule(
    schedule_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    s = _reviewable(db, schedule_id, cu)
    now = _now()
    s.status, s.reviewed_at, s.reviewed_by, s.published_at = "published", now, cu.id, now
    unit = db.get(Unit, s.unit_id)
    members = [m.user_id for m, _ in _members(db, s)]
    notes.notify(
        db,
        members + _holders(db, "schedule.write", s.unit_id),
        "schedule.published",
        f"برنامه {_title(s, unit.name)} منتشر شد",
        link_type="schedule",
        link_id=s.id,
        important=True,
        actor_id=cu.id,
    )
    audit(db, "schedule.approve", cu.id, "schedule", s.id, client_ip(request))
    db.commit()
    return _detail(db, cu, s)


@router.post("/schedules/{schedule_id}/return", response_model=ScheduleDetailOut)
def return_schedule(
    schedule_id: uuid.UUID,
    body: ReturnIn,
    request: Request,
    cu: CurrentUser = Depends(get_active_user),
    db: Session = Depends(get_db),
):
    s = _reviewable(db, schedule_id, cu)
    note = body.note.strip()
    if not note:
        raise api_error(400, "note_required", "علت برگرداندن را بنویسید")
    s.status, s.reviewed_at, s.reviewed_by, s.return_note = "draft", _now(), cu.id, note
    unit = db.get(Unit, s.unit_id)
    notes.notify(
        db,
        _holders(db, "schedule.write", s.unit_id),
        "schedule.returned",
        f"برنامه {_title(s, unit.name)} برای اصلاح برگشت",
        body=note,
        link_type="schedule",
        link_id=s.id,
        important=True,
        actor_id=cu.id,
    )
    audit(db, "schedule.return", cu.id, "schedule", s.id, client_ip(request))
    db.commit()
    return _detail(db, cu, s)


def _reader(db: Session, schedule_id: uuid.UUID, cu: CurrentUser) -> Schedule:
    s = _load(db, schedule_id, cu)
    if not (_privileged(cu, s) or _can_read_all(cu, s.unit_id)):
        raise api_error(403, "forbidden", "به این بخش دسترسی ندارید")
    return s


@router.get("/schedules/{schedule_id}/changes", response_model=list[ChangeOut])
def schedule_changes(
    schedule_id: uuid.UUID, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)
):
    s = _reader(db, schedule_id, cu)
    rows = list(
        db.scalars(
            select(ScheduleChange)
            .where(ScheduleChange.schedule_id == s.id)
            .order_by(ScheduleChange.at.desc())
        )
    )
    types = _shift_types(db)
    ids = {r.user_id for r in rows} | {r.changed_by for r in rows if r.changed_by}
    people = {u.id: u for u in db.scalars(select(User).where(User.id.in_(ids)))} if ids else {}
    return [
        ChangeOut(
            id=r.id,
            at=r.at,
            person=_person(people[r.user_id]),
            day=r.day,
            jalali=_jstr(r.day),
            old_code=r.old_code,
            new_code=r.new_code,
            old_label=types[r.old_code].label if r.old_code in types else None,
            new_label=types[r.new_code].label if r.new_code in types else None,
            reason=r.reason,
            changed_by=_person(people.get(r.changed_by)),
        )
        for r in rows
    ]


@router.get("/schedules/{schedule_id}/export.csv")
def export_schedule(
    schedule_id: uuid.UUID, cu: CurrentUser = Depends(get_active_user), db: Session = Depends(get_db)
):
    """جدول ماه با شماره کدهای بیمارستان؛ با BOM تا اکسل نویسه‌های فارسی را درست نشان دهد."""
    s = _reader(db, schedule_id, cu)
    d = _detail(db, cu, s)
    buf = io.StringIO()
    buf.write("﻿")
    w = csv.writer(buf)
    w.writerow([d.title, STATUS_NAMES[d.status]])
    w.writerow(
        ["کد پرسنلی", "نام و نام خانوادگی", "سمت"]
        + [f"{day.jalali} {day.weekday_name}" for day in d.days]
        + ["ساعت کار", "ساعت آنکال", "اضافه‌کار"]
    )
    for m in d.members:
        codes = {c.day: c.code for c in m.cells}
        w.writerow(
            [m.personnel_code, m.full_name, m.job_title or ""]
            + [codes.get(day.date, "") or "" for day in d.days]
            + [m.work_hours, m.oncall_hours, "" if m.overtime_hours is None else m.overtime_hours]
        )
    return Response(
        content=buf.getvalue().encode("utf-8"),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="schedule-{s.jyear}-{s.jmonth:02d}.csv"'},
    )
