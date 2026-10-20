"""منطق برنامه شیفت بدون پایگاه داده: ساعت هر کد، پوشش نوبت‌ها و هشدارها.

همین منطق در اپ (`core/schedule/ScheduleLogic.kt`) تکرار می‌شود و تست‌های دو طرف همان سناریوها را دارند.
همه زمان‌ها به وقت محلی بیمارستان (ایران، بدون ساعت تابستانی) و بدون منطقه زمانی حساب می‌شوند.
"""

from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta, timezone

from app.core.jalali import from_gregorian

LOCAL_TZ = timezone(timedelta(hours=3, minutes=30))

# نوبت‌های روز برای شمارش نیرو: کسی که در این لحظه سر کار است، نیروی آن نوبت حساب می‌شود.
PERIODS = {"morning": time(10, 0), "evening": time(17, 0), "night": time(23, 0)}
PERIOD_NAMES = {"morning": "صبح", "evening": "عصر", "night": "شب"}

SHIFT_GROUPS = {
    "general": "عمومی (عادی و بهره‌وری)",
    "nursing": "آنکال پرستاران",
    "physician": "پزشکان",
    "radiology": "رادیولوژی (اشعه)",
    "pharmacy": "داروخانه",
    "nutrition": "تغذیه و آشپزخانه",
    "guard": "نگهبانی",
    "admin": "اداری",
}
DEFAULT_GROUPS = ["general", "nursing"]
KINDS = {"work": "کاری", "off": "استراحت", "leave": "مرخصی یا مأموریت"}


@dataclass(frozen=True)
class Shift:
    """یک کد شیفت. segments: [(شروع، روز شروع، پایان، روز پایان، آنکال)]؛ روز نسبت به روز خانه جدول."""

    code: int
    kind: str = "work"
    segments: tuple[tuple[time, int, time, int, bool], ...] = ()
    hours: float | None = None  # ساعت مصوب؛ اگر خالی باشد از بازه‌ها حساب می‌شود
    weekday: int | None = None  # date.weekday()؛ مثلاً پنجشنبه = ۳

    @classmethod
    def from_json(cls, code: int, kind: str, segments: list[dict], hours=None, weekday=None) -> "Shift":
        return cls(
            code=code,
            kind=kind,
            segments=tuple(
                (
                    time.fromisoformat(s["start"]),
                    int(s.get("start_day", 0)),
                    time.fromisoformat(s["end"]),
                    int(s.get("end_day", 0)),
                    bool(s.get("oncall")),
                )
                for s in segments
            ),
            hours=hours,
            weekday=weekday,
        )

    def intervals(self, day: date, oncall: bool | None = None) -> list[tuple[datetime, datetime]]:
        base = datetime.combine(day, time())
        out = []
        for start, sd, end, ed, is_oncall in self.segments:
            if oncall is not None and is_oncall != oncall:
                continue
            a = base + timedelta(days=sd, hours=start.hour, minutes=start.minute)
            b = base + timedelta(days=ed, hours=end.hour, minutes=end.minute)
            out.append((a, b))
        return out

    @property
    def is_work(self) -> bool:
        return self.kind == "work" and any(not s[4] for s in self.segments)

    def work_hours(self) -> float:
        if self.hours is not None:
            return float(self.hours)
        return _hours(self.intervals(date(2000, 1, 1), oncall=False))

    def oncall_hours(self) -> float:
        return _hours(self.intervals(date(2000, 1, 1), oncall=True))

    def span_days(self) -> int:
        """تعداد روزهای بعدی که این شیفت ظهرشان را هم می‌گیرد (برای کم‌رنگ کردن خانه‌های پوشیده)."""
        d0 = date(2000, 1, 1)
        iv = self.intervals(d0)
        last = 0
        for k in range(1, max((s[3] for s in self.segments), default=0) + 1):
            noon = datetime.combine(d0 + timedelta(days=k), time(12))
            if any(a <= noon < b for a, b in iv):
                last = k
        return last


def _hours(intervals) -> float:
    return round(sum((b - a).total_seconds() for a, b in intervals) / 3600, 2)


@dataclass
class Settings:
    min_staff: dict[str, int] = field(default_factory=dict)  # morning | evening | night
    max_consecutive: int = 6
    min_rest_hours: int = 8
    required_monthly_hours: int | None = None
    max_monthly_hours: int | None = None


@dataclass(frozen=True)
class Entry:
    user_id: object
    day: date
    code: int
    own: bool = True  # False یعنی از برنامه بخش دیگر، فقط برای بررسی همپوشانی


@dataclass(frozen=True)
class Warning:
    code: str
    message: str
    user_id: object = None
    day: date | None = None
    period: str | None = None


def covers(shift: Shift, day: date) -> dict[date, set[str]]:
    """نوبت‌هایی که این شیفت (شروع در day) پوشش می‌دهد؛ فقط بخش کاری، نه آنکال."""
    out: dict[date, set[str]] = defaultdict(set)
    for a, b in shift.intervals(day, oncall=False):
        d = a.date()
        while d <= b.date():
            for p, t in PERIODS.items():
                if a <= datetime.combine(d, t) < b:
                    out[d].add(p)
            d += timedelta(days=1)
    return out


def coverage(entries: list[Entry], shifts: dict[int, Shift], days: list[date]) -> dict[date, dict[str, int]]:
    """تعداد نیروی هر نوبت در هر روز ماه."""
    result = {d: dict.fromkeys(PERIODS, 0) for d in days}
    for e in entries:
        if not e.own or e.code not in shifts:
            continue
        for d, periods in covers(shifts[e.code], e.day).items():
            if d in result:
                for p in periods:
                    result[d][p] += 1
    return result


def totals(
    entries: list[Entry], shifts: dict[int, Shift], settings: Settings
) -> dict[object, dict[str, float]]:
    out: dict[object, dict[str, float]] = defaultdict(lambda: {"work": 0.0, "oncall": 0.0, "overtime": 0.0})
    for e in entries:
        s = shifts.get(e.code)
        if not e.own or s is None or s.kind != "work":
            continue
        out[e.user_id]["work"] += s.work_hours()
        out[e.user_id]["oncall"] += s.oncall_hours()
    for t in out.values():
        t["work"], t["oncall"] = round(t["work"], 2), round(t["oncall"], 2)
        if settings.required_monthly_hours is not None:
            t["overtime"] = round(max(0.0, t["work"] - settings.required_monthly_hours), 2)
    return dict(out)


def _fa(d: date) -> str:
    j = from_gregorian(d)
    return f"{j.month}/{j.day}"


def warnings(
    entries: list[Entry], shifts: dict[int, Shift], settings: Settings, days: list[date]
) -> list[Warning]:
    """هشدارهای برنامه یک ماه. entries می‌تواند شیفت‌های همان افراد در بخش‌های دیگر را هم داشته باشد (own=False)."""
    out: list[Warning] = []
    by_user: dict[object, list[Entry]] = defaultdict(list)
    for e in entries:
        if e.code in shifts:
            by_user[e.user_id].append(e)
    month = set(days)

    for uid, items in by_user.items():
        # همپوشانی و استراحت کوتاه میان دو شیفت پشت سر هم
        spans = []
        for e in items:
            s = shifts[e.code]
            iv = s.intervals(e.day)
            if s.kind == "work" and iv:
                work = s.intervals(e.day, oncall=False)
                spans.append((min(a for a, _ in iv), max(b for _, b in iv), e, work))
        spans.sort(key=lambda x: (x[0], x[1]))
        for (_a1, b1, e1, w1), (a2, _b2, e2, w2) in zip(spans, spans[1:], strict=False):
            if not (e1.own or e2.own):
                continue
            day = e2.day if e2.own else e1.day
            if a2 < b1:
                where = "" if e1.own and e2.own else " (با برنامه بخش دیگر)"
                out.append(
                    Warning(
                        "overlap",
                        f"شیفت {_fa(e2.day)} با شیفت {_fa(e1.day)} همپوشانی دارد{where}",
                        uid,
                        day,
                    )
                )
            elif w1 and w2:
                gap = (min(a for a, _ in w2) - max(b for _, b in w1)).total_seconds() / 3600
                if 0 <= gap < settings.min_rest_hours:
                    out.append(
                        Warning(
                            "short_rest",
                            f"میان شیفت {_fa(e1.day)} و {_fa(e2.day)} فقط {gap:g} ساعت استراحت است",
                            uid,
                            day,
                        )
                    )

        own = [e for e in items if e.own]
        # روز کاری: روزی که شیفت کاری در آن شروع می‌شود یا ظهرش زیر پوشش شیفت است
        worked: set[date] = set()
        for e in own:
            s = shifts[e.code]
            if not s.is_work:
                continue
            worked.add(e.day)
            for a, b in s.intervals(e.day, oncall=False):
                d = a.date()
                while d <= b.date():
                    if a <= datetime.combine(d, time(12)) < b:
                        worked.add(d)
                    d += timedelta(days=1)
        streak = 0
        for d in days:
            streak = streak + 1 if d in worked else 0
            if streak == settings.max_consecutive + 1:
                out.append(
                    Warning(
                        "consecutive",
                        f"بیش از {settings.max_consecutive} روز کاری پشت سر هم بدون استراحت",
                        uid,
                        d,
                    )
                )

        for e in own:
            s = shifts[e.code]
            if s.weekday is not None and e.day.weekday() != s.weekday and e.day in month:
                out.append(Warning("wrong_weekday", f"کد {s.code} برای روز دیگری از هفته است", uid, e.day))

    if settings.max_monthly_hours is not None:
        for uid, t in totals(entries, shifts, settings).items():
            if t["work"] > settings.max_monthly_hours:
                out.append(
                    Warning(
                        "over_hours",
                        f"جمع ساعت کار ({t['work']:g}) از سقف {settings.max_monthly_hours} بیشتر است",
                        uid,
                    )
                )

    mins = {p: n for p, n in settings.min_staff.items() if p in PERIODS and n and n > 0}
    if mins:
        cov = coverage(entries, shifts, days)
        for d in days:
            for p, n in mins.items():
                if cov[d][p] < n:
                    out.append(
                        Warning(
                            "understaffed",
                            f"نیروی نوبت {PERIOD_NAMES[p]} {_fa(d)}: {cov[d][p]} نفر (حداقل {n})",
                            day=d,
                            period=p,
                        )
                    )
    order = {
        "overlap": 0,
        "short_rest": 1,
        "consecutive": 2,
        "wrong_weekday": 3,
        "over_hours": 4,
        "understaffed": 5,
    }
    out.sort(key=lambda w: (order[w.code], w.day or date.min, str(w.user_id)))
    return out
