"""ترجمه آماده نام گروه‌های تخصصی رسمی بیمارستان به زبان‌های مراجعان.

این‌ها اصطلاحات استاندارد پزشکی‌اند و تأییدشده افزوده می‌شوند تا صفحه اول اپ از همان ابتدا به زبان
مراجع باشد. فقط برای گروهی که نام فارسی‌اش دقیقاً همین است و هنوز ترجمه‌ای برای آن زبان ندارد ثبت
می‌شود؛ ترجمه‌ای که مدیر در پنل ویرایش کرده دست نمی‌خورد. هم seed و هم مایگریشن از این استفاده می‌کنند.
"""

import hashlib

from sqlalchemy import text

# نام فارسی → (آذربایجانی، انگلیسی، عربی، ترکی)
SPECIALTY_TRANSLATIONS: dict[str, tuple[str, str, str, str]] = {
    "قلب و عروق": ("Kardiologiya", "Cardiology", "القلب والأوعية الدموية", "Kardiyoloji"),
    "زنان و زایمان": ("Ginekologiya və mamalıq", "Obstetrics & Gynecology", "النساء والتوليد", "Kadın Doğum"),
    "مغز و اعصاب": ("Nevrologiya", "Neurology", "المخ والأعصاب", "Nöroloji"),
    "گوارش و کبد": ("Qastroenterologiya", "Gastroenterology", "الجهاز الهضمي والكبد", "Gastroenteroloji"),
    "ارتوپدی": ("Ortopediya", "Orthopedics", "جراحة العظام", "Ortopedi"),
    "اورولوژی": ("Urologiya", "Urology", "المسالك البولية", "Üroloji"),
    "نفرولوژی": ("Nefrologiya", "Nephrology", "أمراض الكلى", "Nefroloji"),
    "آنکولوژی": ("Onkologiya", "Oncology", "الأورام", "Onkoloji"),
    "عفونی": ("İnfeksion xəstəliklər", "Infectious Diseases", "الأمراض المعدية", "Enfeksiyon Hastalıkları"),
    "ریه": ("Pulmonologiya", "Pulmonology", "أمراض الرئة", "Göğüs Hastalıkları"),
    "گوش و حلق و بینی": ("Otorinolarinqologiya", "ENT", "الأنف والأذن والحنجرة", "Kulak Burun Boğaz"),
    "ناباروری و IVF": (
        "Sonsuzluq və IVF",
        "Infertility & IVF",
        "العقم وأطفال الأنابيب",
        "Kısırlık ve Tüp Bebek",
    ),
    "کودکان": ("Pediatriya", "Pediatrics", "طب الأطفال", "Çocuk Sağlığı"),
    "جراحی عمومی": ("Ümumi cərrahiyyə", "General Surgery", "الجراحة العامة", "Genel Cerrahi"),
    "دندانپزشکی": ("Stomatologiya", "Dentistry", "طب الأسنان", "Diş Hekimliği"),
}
LANGUAGES = ("az", "en", "ar", "tr")

_INSERT = text(
    "INSERT INTO content_translations "
    "(id, entity_type, entity_id, language, field, text, source_hash, status, updated_at, approved_at) "
    "SELECT gen_random_uuid(), 'specialty', s.id, CAST(:lang AS VARCHAR), 'name', CAST(:text AS TEXT), :hash, 'approved', now(), now() "
    "FROM specialties s WHERE s.name = :name AND NOT EXISTS ("
    " SELECT 1 FROM content_translations t WHERE t.entity_type = 'specialty' AND t.entity_id = s.id"
    " AND t.language = CAST(:lang AS VARCHAR) AND t.field = 'name')"
)


def seed_specialty_translations(conn) -> None:
    """conn می‌تواند Session یا Connection (در مایگریشن) باشد."""
    for name, texts in SPECIALTY_TRANSLATIONS.items():
        digest = hashlib.sha256(name.encode()).hexdigest()
        for lang, value in zip(LANGUAGES, texts, strict=True):
            conn.execute(_INSERT, {"lang": lang, "text": value, "hash": digest, "name": name})
