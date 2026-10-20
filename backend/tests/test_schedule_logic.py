"""منطق برنامه شیفت؛ همین سناریوها در ScheduleLogicTest.kt اپ هم هستند."""

import json
from datetime import date, timedelta
from pathlib import Path

from app.services.schedule_logic import Entry, Settings, Shift, coverage, covers, totals, warnings

CODES = json.loads((Path(__file__).parents[1] / "app" / "data" / "shift_codes.json").read_text("utf-8"))[
    "codes"
]
SHIFTS = {
    c["code"]: Shift.from_json(c["code"], c["kind"], c["segments"], c["hours"], c["weekday"]) for c in CODES
}
M, E, N, ME, EN, MEN, H36, MN, OFF, LEAVE = 31, 32, 33, 34, 35, 36, 37, 40, 50, 20
THU_MORNING = 19
D1 = date(2026, 10, 23)  # ۱ آبان ۱۴۰۵، جمعه
DAYS = [D1 + timedelta(days=i) for i in range(30)]


def day(n: int) -> date:
    return D1 + timedelta(days=n - 1)


def codes_of(ws):
    return [(w.code, w.user_id, w.day) for w in ws]


def test_shift_hours_and_span():
    assert SHIFTS[M].work_hours() == 7 and SHIFTS[N].work_hours() == 12.5
    assert SHIFTS[MEN].work_hours() == 24.5 and SHIFTS[H36].work_hours() == 36  # ساعت مصوب
    assert SHIFTS[MN].work_hours() == 19.5
    assert SHIFTS[141].work_hours() == 12.5 and SHIFTS[141].oncall_hours() == 12  # ص ع موظف، آنکال شب
    assert SHIFTS[OFF].work_hours() == 0 and not SHIFTS[OFF].is_work and not SHIFTS[138].is_work
    assert SHIFTS[N].span_days() == 0 and SHIFTS[H36].span_days() == 1 and SHIFTS[45].span_days() == 1
    assert SHIFTS[42].span_days() == 1  # ش ص تا ظهر فردا
    assert covers(SHIFTS[EN], day(1)) == {day(1): {"evening", "night"}}
    assert covers(SHIFTS[H36], day(1)) == {
        day(1): {"morning", "evening", "night"},
        day(2): {"morning", "evening"},
    }
    assert covers(SHIFTS[138], day(1)) == {}  # آنکال نیروی حاضر حساب نمی‌شود


def test_coverage_and_totals():
    entries = [Entry("a", day(1), MEN), Entry("b", day(1), M), Entry("b", day(2), N), Entry("c", day(1), 141)]
    cov = coverage(entries, SHIFTS, DAYS)
    assert cov[day(1)] == {"morning": 3, "evening": 2, "night": 1}
    assert cov[day(2)] == {"morning": 0, "evening": 0, "night": 1}
    t = totals(entries, SHIFTS, Settings(required_monthly_hours=20))
    assert t["a"] == {"work": 24.5, "oncall": 0.0, "overtime": 4.5}
    assert t["b"] == {"work": 19.5, "oncall": 0.0, "overtime": 0.0}
    assert t["c"]["oncall"] == 12


def test_clean_rotation_has_no_warnings():
    pattern = [M, E, N, OFF, OFF]
    entries = [Entry("a", d, pattern[i % 5]) for i, d in enumerate(DAYS)]
    assert warnings(entries, SHIFTS, Settings(), DAYS) == []


def test_overlap_short_rest_and_other_unit():
    entries = [
        Entry("a", day(1), N),  # تا ۸ صبح روز ۲
        Entry("a", day(2), M),  # از ۷:۳۰ روز ۲ ← همپوشانی
        Entry("b", day(1), N),
        Entry("b", day(2), E),  # ۵.۵ ساعت استراحت
        Entry("c", day(3), M),
        Entry("c", day(3), E, own=False),  # همان روز در بخش دیگر
        Entry("d", day(1), H36),
        Entry("d", day(2), OFF),  # استراحت روی خانه پوشیده هشدار ندارد
    ]
    got = codes_of(warnings(entries, SHIFTS, Settings(), DAYS))
    assert got == [("overlap", "a", day(2)), ("overlap", "c", day(3)), ("short_rest", "b", day(2))]
    msg = [w.message for w in warnings(entries, SHIFTS, Settings(), DAYS) if w.user_id == "c"][0]
    assert "بخش دیگر" in msg


def test_consecutive_weekday_hours_and_staffing():
    entries = [Entry("a", day(i), M) for i in range(1, 9)]  # ۸ روز پشت سر هم
    entries.append(Entry("b", day(1), THU_MORNING))  # جمعه است، نه پنجشنبه
    entries.append(Entry("b", day(7), THU_MORNING))  # پنجشنبه
    settings = Settings(max_consecutive=6, max_monthly_hours=50, min_staff={"night": 1})
    ws = warnings(entries, SHIFTS, settings, DAYS)
    got = codes_of(ws)
    assert ("consecutive", "a", day(7)) in got
    assert ("wrong_weekday", "b", day(1)) in got and ("wrong_weekday", "b", day(7)) not in got
    assert ("over_hours", "a", None) in got
    assert sum(1 for w in ws if w.code == "understaffed") == 30


def test_night_shifts_do_not_double_count_consecutive_days():
    entries = [Entry("a", day(i), N) for i in range(1, 7)]  # ۶ شب پشت سر هم
    assert [w.code for w in warnings(entries, SHIFTS, Settings(max_consecutive=6), DAYS)] == []
