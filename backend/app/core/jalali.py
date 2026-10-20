"""تبدیل تاریخ میلادی و شمسی؛ همان الگوریتم `core/util/Jalali.kt` در اپ (jalaali-js).

تقسیم‌ها مثل Kotlin به سمت صفر گرد می‌شوند (نه مثل `//` پایتون) تا نتیجه دو طرف یکی بماند.
"""

from dataclasses import dataclass
from datetime import date, timedelta

MONTH_NAMES = (
    "فروردین",
    "اردیبهشت",
    "خرداد",
    "تیر",
    "مرداد",
    "شهریور",
    "مهر",
    "آبان",
    "آذر",
    "دی",
    "بهمن",
    "اسفند",
)
# date.weekday(): دوشنبه = ۰
WEEKDAY_NAMES = ("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
FRIDAY = 4

_BREAKS = (
    -61,
    9,
    38,
    199,
    426,
    686,
    756,
    818,
    1111,
    1181,
    1210,
    1635,
    2060,
    2097,
    2192,
    2262,
    2324,
    2394,
    2456,
    3178,
)


@dataclass(frozen=True)
class JalaliDate:
    year: int
    month: int
    day: int

    def __str__(self) -> str:
        return f"{self.year:04d}/{self.month:02d}/{self.day:02d}"


def _div(a: int, b: int) -> int:
    q = abs(a) // abs(b)
    return q if (a >= 0) == (b >= 0) else -q


def _mod(a: int, b: int) -> int:
    return a - _div(a, b) * b


def _jal_cal(jy: int) -> tuple[int, int, int]:
    gy = jy + 621
    leap_j = -14
    jp = _BREAKS[0]
    jump = 0
    if not (jp <= jy < _BREAKS[-1]):
        raise ValueError(f"سال خارج از محدوده: {jy}")
    for jm in _BREAKS[1:]:
        jump = jm - jp
        if jy < jm:
            break
        leap_j += _div(jump, 33) * 8 + _div(_mod(jump, 33), 4)
        jp = jm
    n = jy - jp
    leap_j += _div(n, 33) * 8 + _div(_mod(n, 33) + 3, 4)
    if _mod(jump, 33) == 4 and jump - n == 4:
        leap_j += 1
    leap_g = _div(gy, 4) - _div((_div(gy, 100) + 1) * 3, 4) - 150
    march = 20 + leap_j - leap_g
    if jump - n < 6:
        n = n - jump + _div(jump + 4, 33) * 33
    leap = _mod(_mod(n + 1, 33) - 1, 4)
    if leap == -1:
        leap = 4
    return leap, gy, march


def _g2d(gy: int, gm: int, gd: int) -> int:
    d = _div((gy + _div(gm - 8, 6) + 100100) * 1461, 4) + _div(153 * _mod(gm + 9, 12) + 2, 5) + gd - 34840408
    return d - _div(_div(gy + 100100 + _div(gm - 8, 6), 100) * 3, 4) + 752


def _d2g(jdn: int) -> date:
    j = 4 * jdn + 139361631
    j += _div(_div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
    i = _div(_mod(j, 1461), 4) * 5 + 308
    gd = _div(_mod(i, 153), 5) + 1
    gm = _mod(_div(i, 153), 12) + 1
    gy = _div(j, 1461) - 100100 + _div(8 - gm, 6)
    return date(gy, gm, gd)


def is_leap_year(jy: int) -> bool:
    return _jal_cal(jy)[0] == 0


def month_length(jy: int, jm: int) -> int:
    if jm <= 6:
        return 31
    if jm <= 11:
        return 30
    return 30 if is_leap_year(jy) else 29


def to_gregorian(jy: int, jm: int, jd: int) -> date:
    _, gy, march = _jal_cal(jy)
    return _d2g(_g2d(gy, 3, march) + (jm - 1) * 31 - _div(jm, 7) * (jm - 7) + jd - 1)


def from_gregorian(d: date) -> JalaliDate:
    jdn = _g2d(d.year, d.month, d.day)
    jy = d.year - 621
    leap, _, march = _jal_cal(jy)
    k = jdn - _g2d(d.year, 3, march)
    if k >= 0:
        if k <= 185:
            return JalaliDate(jy, 1 + _div(k, 31), _mod(k, 31) + 1)
        k -= 186
    else:
        jy -= 1
        k += 179
        if leap == 1:
            k += 1
    return JalaliDate(jy, 7 + _div(k, 30), _mod(k, 30) + 1)


def month_days(jy: int, jm: int) -> list[date]:
    """روزهای میلادی متناظر با یک ماه شمسی، به ترتیب."""
    first = to_gregorian(jy, jm, 1)
    return [first + timedelta(days=i) for i in range(month_length(jy, jm))]


def previous_month(jy: int, jm: int) -> tuple[int, int]:
    return (jy - 1, 12) if jm == 1 else (jy, jm - 1)


def month_title(jy: int, jm: int) -> str:
    return f"{MONTH_NAMES[jm - 1]} {jy}"
