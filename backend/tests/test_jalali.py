"""همان سناریوهای JalaliTest.kt در اپ."""

from datetime import date, timedelta

from app.core import jalali
from app.core.jalali import JalaliDate


def test_known_dates():
    assert jalali.from_gregorian(date(2024, 3, 20)) == JalaliDate(1403, 1, 1)
    assert jalali.from_gregorian(date(2026, 10, 14)) == JalaliDate(1405, 7, 22)
    assert jalali.from_gregorian(date(2021, 3, 20)) == JalaliDate(1399, 12, 30)
    assert jalali.to_gregorian(1404, 1, 1) == date(2025, 3, 21)


def test_round_trip_every_day_for_ten_years():
    d = date(2018, 1, 1)
    while d < date(2028, 1, 1):
        j = jalali.from_gregorian(d)
        assert jalali.to_gregorian(j.year, j.month, j.day) == d
        d += timedelta(days=1)


def test_leap_years_and_month_days():
    assert jalali.is_leap_year(1399) and jalali.is_leap_year(1403)
    assert not jalali.is_leap_year(1404)
    assert jalali.month_length(1403, 12) == 30
    assert jalali.month_length(1404, 12) == 29
    assert jalali.month_length(1404, 6) == 31
    days = jalali.month_days(1405, 8)
    assert len(days) == 30 and days[0] == date(2026, 10, 23) and days[-1] == date(2026, 11, 21)
    assert str(JalaliDate(1405, 7, 22)) == "1405/07/22"
    assert jalali.previous_month(1405, 1) == (1404, 12)
    assert jalali.month_title(1405, 8) == "آبان 1405"
