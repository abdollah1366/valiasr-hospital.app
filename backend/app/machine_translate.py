"""ترجمه ماشینی یک‌باره همه محتوای عمومیِ بدون ترجمه (پس از روشن کردن RAZI_MT_PROVIDER).

python -m app.machine_translate            # همه نوع‌ها و هر چهار زبان
python -m app.machine_translate doctor en  # فقط پزشکان، انگلیسی
"""

import sys

from app.core.db import SessionLocal
from app.routers.translations import _entities
from app.services import machine_translation as mt
from app.services.translations import FIELDS, LANGUAGES


def run(entity_types: list[str], languages: list[str]) -> int:
    if not mt.enabled():
        raise SystemExit("RAZI_MT_PROVIDER تنظیم نشده است (google یا libretranslate)")
    total = 0
    with SessionLocal() as db:
        for entity_type in entity_types:
            objs = _entities(db, entity_type)
            for language in languages:
                n = mt.fill(db, entity_type, objs, language)
                print(f"{entity_type} {language}: {n}")
                total += n
    return total


if __name__ == "__main__":
    args = sys.argv[1:]
    types = [args[0]] if args else list(FIELDS)
    langs = [args[1]] if len(args) > 1 else list(LANGUAGES)
    print(f"translated fields: {run(types, langs)}")
