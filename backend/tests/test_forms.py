import json

from app.seed import INCIDENT_FORM
from app.services.forms import validate_answers

SCHEMA = json.loads(INCIDENT_FORM.read_text(encoding="utf-8"))


def base_answers(**extra):
    return {
        "report_method": "خوداظهاری",
        "harm_level": "بدون آسیب",
        "error_types": ["دارویی"],
        "type_2_items": ["دوز اشتباه (بیش از حد، کمتر از حد، دوز فراموش‌شده)"],
        "description": "شرح",
        **extra,
    }


def test_valid_medication_error():
    r = validate_answers(SCHEMA, "error", base_answers())
    assert r.errors == {}
    assert r.answers["error_types"] == ["دارویی"]


def test_conditional_section_required_when_visible():
    answers = base_answers()
    del answers["type_2_items"]
    r = validate_answers(SCHEMA, "error", answers)
    assert "type_2_items" in r.errors


def test_hidden_section_answers_are_dropped():
    # پاسخ بخش جراحی وقتی نوع خطا جراحی نیست ذخیره نمی‌شود
    r = validate_answers(SCHEMA, "error", base_answers(type_1_items=["آبسه"]))
    assert r.errors == {}
    assert "type_1_items" not in r.answers


def test_sensitive_fields_are_separated():
    r = validate_answers(SCHEMA, "error", base_answers(patient_name="بیمار", file_number="123", room="12"))
    assert r.sensitive == {"patient_name": "بیمار", "file_number": "123"}
    assert "patient_name" not in r.answers and r.answers["room"] == "12"


def test_equipment_kind_hides_patient_sections():
    r = validate_answers(
        SCHEMA,
        "equipment",
        {
            "report_method": "خوداظهاری",
            "equipment_name": "پمپ انفوزیون",
            "equipment_problem": "عملکرد نادرست",
            "patient_affected": "خیر",
            "description": "شرح",
            "patient_name": "نباید ذخیره شود",
        },
    )
    assert r.errors == {}
    assert r.sensitive == {}
    assert "error_types" not in r.answers


def test_invalid_option_unknown_field_and_bad_date():
    r = validate_answers(
        SCHEMA, "error", base_answers(report_method="نامعتبر", foo="bar", admission_date="1403-13-40")
    )
    assert set(r.errors) == {"report_method", "foo", "admission_date"}


def test_nested_condition_student_field():
    r = validate_answers(SCHEMA, "error", base_answers(involved_roles=["پرستار"], student_field="پرستاری"))
    assert "student_field" not in r.answers
    r = validate_answers(SCHEMA, "error", base_answers(involved_roles=["دانشجو"], student_field="پرستاری"))
    assert r.answers["student_field"] == "پرستاری"
