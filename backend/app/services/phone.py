"""شماره موبایل مراجعان: یکسان‌سازی به قالب بین‌المللی E.164 و زبان پیشنهادی از پیش‌شماره.

نقشه زبان باید با AppLanguage.forPhone در اپ اندروید یکسان بماند.
"""

import re

_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")

_ARABIC_CODES = (
    "964", "966", "971", "965", "974", "973", "968", "962", "961", "963", "970",
    "20", "218", "216", "213", "212", "249", "967",
)  # fmt: skip

LANGUAGES = ("fa", "az", "en", "ar", "tr")


def normalize_mobile(raw: str) -> str | None:
    """۰۹۱۲… و ‎+98912… و 0098912… همه به ‎+98912… تبدیل می‌شوند؛ شماره نامعتبر None."""
    s = re.sub(r"[\s\-()]", "", raw.translate(_DIGITS))
    if s.startswith("00"):
        s = "+" + s[2:]
    if re.fullmatch(r"09\d{9}", s):
        return "+98" + s[1:]
    if not re.fullmatch(r"\+[1-9]\d{7,14}", s):
        return None
    if s.startswith("+98") and not re.fullmatch(r"\+989\d{9}", s):
        return None
    return s


def language_for_mobile(e164: str) -> str:
    digits = e164.lstrip("+")
    if digits.startswith("98"):
        return "fa"
    if digits.startswith("994"):
        return "az"
    if digits.startswith("90"):
        return "tr"
    if digits.startswith(_ARABIC_CODES):
        return "ar"
    return "en"


def mask_mobile(e164: str) -> str:
    """برای نمایش و لاگ: فقط ۴ رقم آخر."""
    return e164[:-4].translate(str.maketrans("0123456789", "**********")) + e164[-4:]
