from app.models import Faq
from tests.conftest import CONTENT, STAFF, SYSADMIN, auth

API = "/api/v1"


def _emergency(client):
    return next(u for u in client.get(f"{API}/public/units").json() if u["name"] == "اورژانس")


def _save(client, h, entity_id, text, field="name", language="en", entity_type="unit"):
    return client.put(
        f"{API}/admin/translations",
        headers=h,
        json={
            "entity_type": entity_type,
            "entity_id": entity_id,
            "language": language,
            "field": field,
            "text": text,
        },
    )


def _approve(client, h, entity_id, field="name", language="en", entity_type="unit"):
    return client.post(
        f"{API}/admin/translations/approve",
        headers=h,
        json={"entity_type": entity_type, "entity_id": entity_id, "language": language, "field": field},
    )


def test_only_approved_translation_is_served(client):
    h = auth(client, CONTENT)
    uid = _emergency(client)["id"]
    assert _save(client, h, uid, "Emergency").json()["status"] == "draft"
    # پیش‌نویس هنوز به مراجع نشان داده نمی‌شود.
    assert _emergency(client)["name"] == "اورژانس"
    names = [u["name"] for u in client.get(f"{API}/public/units?lang=en").json()]
    assert "Emergency" not in names

    assert _approve(client, h, uid).json()["status"] == "approved"
    units = client.get(f"{API}/public/units?lang=en").json()
    assert next(u for u in units if u["id"] == uid)["name"] == "Emergency"
    # بدون زبان یا با فارسی، متن اصلی
    assert _emergency(client)["name"] == "اورژانس"
    by_header = client.get(f"{API}/public/units", headers={"Accept-Language": "en-US,en;q=0.9"}).json()
    assert next(u for u in by_header if u["id"] == uid)["name"] == "Emergency"
    # زبانی که ترجمه ندارد، فارسی برمی‌گرداند.
    tr = client.get(f"{API}/public/units?lang=tr").json()
    assert next(u for u in tr if u["id"] == uid)["name"] == "اورژانس"


def test_changed_source_hides_translation_until_reviewed(client, db):
    h = auth(client, CONTENT)
    faq = client.get(f"{API}/public/faqs").json()[0]
    _save(client, h, faq["id"], "Visiting hours?", field="question", entity_type="faq")
    _approve(client, h, faq["id"], field="question", entity_type="faq")
    assert client.get(f"{API}/public/faqs?lang=en").json()[0]["question"] == "Visiting hours?"

    row = db.get(Faq, faq["id"])
    row.question = row.question + " (ویرایش‌شده)"
    db.commit()
    assert client.get(f"{API}/public/faqs?lang=en").json()[0]["question"] == row.question
    listed = client.get(f"{API}/admin/translations?entity_type=faq&language=en", headers=h).json()
    field = next(
        f for i in listed if i["entity_id"] == faq["id"] for f in i["fields"] if f["field"] == "question"
    )
    assert field["status"] == "outdated"
    # ترجمه قدیمی را نمی‌شود بدون بازبینی تأیید کرد.
    r = _approve(client, h, faq["id"], field="question", entity_type="faq")
    assert r.status_code == 409 and r.json()["detail"]["code"] == "source_changed"


def test_public_announcement_translation(client):
    h = auth(client, CONTENT)
    a = client.get(f"{API}/public/announcements").json()[0]
    _save(client, h, a["id"], "Duyuru", field="title", language="tr", entity_type="announcement")
    _approve(client, h, a["id"], field="title", language="tr", entity_type="announcement")
    assert client.get(f"{API}/public/announcements?lang=tr").json()[0]["title"] == "Duyuru"


def test_permissions_and_validation(client):
    uid = _emergency(client)["id"]
    for code in (STAFF, SYSADMIN):
        h = auth(client, code)
        assert _save(client, h, uid, "x").status_code == 403
        assert (
            client.get(f"{API}/admin/translations?entity_type=unit&language=en", headers=h).status_code == 403
        )
    h = auth(client, CONTENT)
    assert _save(client, h, uid, "x", field="phone").status_code == 400
    assert _save(client, h, uid, "x", language="fr").status_code == 422
    assert _approve(client, h, uid, field="hours").status_code in (404, 400)
    listed = client.get(f"{API}/admin/translations?entity_type=unit&language=ar", headers=h).json()
    # فقط بخش‌های عمومی در فهرست ترجمه هستند.
    assert all(i["label"] != "ICU" for i in listed)
    # جز درمانگاه نمونه که ترجمه آماده دارد، چیزی ترجمه نشده است.
    assert all(
        f["status"] == "missing" for i in listed if i["label"] != "درمانگاه تخصصی" for f in i["fields"]
    )


def test_deleting_faq_removes_its_translations(client, db):
    from sqlalchemy import func, select

    from app.models import ContentTranslation

    h = auth(client, CONTENT)
    faq = client.get(f"{API}/public/faqs").json()[0]
    _save(client, h, faq["id"], "Q", field="question", entity_type="faq")
    assert client.delete(f"{API}/admin/faqs/{faq['id']}", headers=h).status_code == 204
    assert (
        db.scalar(
            select(func.count())
            .where(ContentTranslation.entity_type == "faq")
            .select_from(ContentTranslation)
        )
        == 0
    )


def test_official_specialties_come_translated(client):
    """گروه‌های تخصصی رسمی از ابتدا به زبان مراجع نمایش داده می‌شوند (کاشی‌های صفحه اول اپ)."""
    names = [s["name"] for s in client.get(f"{API}/public/specialties?lang=az").json()]
    assert "Kardiologiya" in names and "قلب و عروق" not in names
    by_header = client.get(f"{API}/public/specialties", headers={"Accept-Language": "en"}).json()
    assert "Cardiology" in [s["name"] for s in by_header]
    assert "قلب و عروق" in [s["name"] for s in client.get(f"{API}/public/specialties").json()]


def test_doctor_card_follows_language(client):
    h = auth(client, "1012")  # مدیر پزشکان (doctor_admin) هم ترجمه را ثبت و تأیید می‌کند
    doctor = next(d for d in client.get(f"{API}/public/doctors").json() if d["full_name"] == "دکتر نمونه دوم")
    assert _save(client, h, doctor["id"], "Orthopedic surgeon", "title", "en", "doctor").status_code == 200
    assert _approve(client, h, doctor["id"], "title", "en", "doctor").status_code == 200

    en = client.get(f"{API}/public/doctors/{doctor['id']}?lang=en").json()
    assert en["full_name"] == "Dr. Sample Two"
    assert en["title"] == "Orthopedic surgeon"
    assert en["specialty_name"] == "Orthopedics"
    # معرفی نمونه ترجمه آماده دارد
    assert en["bio"].startswith("This is sample text")
    # جستجو با نام ترجمه‌شده
    found = client.get(f"{API}/public/doctors", params={"q": "Sample Two", "lang": "en"}).json()
    assert [d["id"] for d in found] == [doctor["id"]]
    found = client.get(f"{API}/public/doctors", params={"q": "Kardiologiya", "lang": "az"}).json()
    assert [d["full_name"] for d in found] == ["Dr. Nümunə Birinci"]

    listed = client.get(f"{API}/admin/translations?entity_type=doctor&language=en", headers=h).json()
    item = next(i for i in listed if i["entity_id"] == doctor["id"])
    status = {f["field"]: f["status"] for f in item["fields"]}
    assert status["full_name"] == "approved" and status["title"] == "approved" and status["bio"] == "approved"


def test_patient_appointments_follow_language(client):
    from tests.test_appointments import book, first_doctor, free_slot, patient_headers

    doctor = first_doctor(client)
    h = patient_headers(client)
    appt = book(client, h, free_slot(client, doctor["id"])["id"]).json()
    assert appt["doctor_name"] == "دکتر نمونه یکم"
    mine = client.get(f"{API}/patient/appointments", headers={**h, "Accept-Language": "tr"}).json()
    assert mine[0]["doctor_name"] == "Dr. Örnek Bir" and mine[0]["specialty_name"] == "Kardiyoloji"


def _machine_on(monkeypatch, show=True):
    from app.core.config import get_settings

    monkeypatch.setattr(get_settings(), "mt_provider", "fake")
    monkeypatch.setattr(get_settings(), "mt_show_unreviewed", show)


def test_machine_translation_fills_only_missing(client, monkeypatch):
    h = auth(client, CONTENT)
    body = {"entity_type": "unit", "language": "en"}
    assert client.post(f"{API}/admin/translations/machine", headers=h, json=body).status_code == 503
    _machine_on(monkeypatch)
    uid = _emergency(client)["id"]
    _save(client, h, uid, "Emergency Department")  # ترجمه دستی (پیش‌نویس) نباید عوض شود
    r = client.post(f"{API}/admin/translations/machine", headers=h, json=body)
    assert r.status_code == 200 and r.json()["translated"] > 0
    item = next(
        i
        for i in client.get(f"{API}/admin/translations?entity_type=unit&language=en", headers=h).json()
        if i["entity_id"] == uid
    )
    fields = {f["field"]: f for f in item["fields"]}
    assert fields["name"]["status"] == "draft" and fields["name"]["text"] == "Emergency Department"
    assert fields["location"]["status"] == "machine" and fields["location"]["text"].startswith("[en] ")
    # ترجمه ماشینی تا بازبینی نشان داده می‌شود؛ پیش‌نویس دستی هنوز نه.
    unit = next(u for u in client.get(f"{API}/public/units?lang=en").json() if u["id"] == uid)
    assert unit["name"] == "اورژانس" and unit["location"].startswith("[en] ")
    # بیمارستان ترجمه ماشینی را تأیید می‌کند.
    assert _approve(client, h, uid, field="location").json()["status"] == "approved"

    _machine_on(monkeypatch, show=False)
    unit = next(u for u in client.get(f"{API}/public/units?lang=en").json() if u["id"] == uid)
    assert unit["location"].startswith("[en] ")  # تأییدشده
    assert not unit["hours"].startswith("[en]")  # ماشینیِ تأییدنشده پنهان


def test_missing_translation_is_made_in_background(client, monkeypatch):
    from app.services import machine_translation

    _machine_on(monkeypatch)
    doctor = next(d for d in client.get(f"{API}/public/doctors").json() if d["full_name"] == "دکتر نمونه دوم")
    # آخرین پزشک نمونه‌ای که ترجمه آماده ندارد: پزشک تازه
    h = auth(client, "1012")
    spec = client.get(f"{API}/public/specialties").json()[0]
    created = client.post(
        f"{API}/admin/doctors",
        headers=h,
        json={"full_name": "دکتر آزمایشی", "specialty_id": spec["id"], "title": "متخصص", "bio": "معرفی"},
    ).json()
    first = client.get(f"{API}/public/doctors/{created['id']}?lang=ar").json()
    assert first["full_name"] == "دکتر آزمایشی"
    machine_translation.drain()
    again = client.get(f"{API}/public/doctors/{created['id']}?lang=ar").json()
    # نام ترجمه ماشینی نمی‌شود؛ عنوان و معرفی می‌شوند.
    assert again["full_name"] == "دکتر آزمایشی" and again["bio"] == "[ar] معرفی"
    # ترجمه آماده پزشک نمونه با ماشینی جایگزین نمی‌شود.
    assert client.get(f"{API}/public/doctors/{doctor['id']}?lang=en").json()["full_name"] == "Dr. Sample Two"
    machine_translation.drain()


def test_long_text_is_split_for_provider(monkeypatch):
    from app.services import machine_translation as mt

    calls = []

    def fake(texts, target):
        calls.append(sum(len(t) for t in texts))
        return texts

    monkeypatch.setitem(mt._PROVIDERS, "fake", fake)
    from app.core.config import get_settings

    monkeypatch.setattr(get_settings(), "mt_provider", "fake")
    long = "\n".join(["سطر " * 300] * 10)
    assert mt.translate([long, "کوتاه"], "en") == [long, "کوتاه"]
    assert all(c <= mt.MAX_REQUEST_CHARS for c in calls) and len(calls) > 1


def test_doctor_names_are_not_machine_translated(client, monkeypatch):
    from app.services import machine_translation

    _machine_on(monkeypatch)
    h = auth(client, "1012")
    spec = client.get(f"{API}/public/specialties").json()[0]
    created = client.post(
        f"{API}/admin/doctors",
        headers=h,
        json={"full_name": "دکتر براتی", "specialty_id": spec["id"], "title": "متخصص"},
    ).json()
    client.get(f"{API}/public/doctors/{created['id']}?lang=en")
    machine_translation.drain()
    again = client.get(f"{API}/public/doctors/{created['id']}?lang=en").json()
    assert again["full_name"] == "دکتر براتی" and again["title"] == "[en] متخصص"
