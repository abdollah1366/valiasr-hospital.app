from sqlalchemy import select

from app.core.permissions import DEFAULT_ROLES
from app.models import Unit
from tests.conftest import STAFF, SYSADMIN, auth

API = "/api/v1"
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 64


def make_unit(client, h, name="واحد آزمایشی", **extra):
    body = {"name": name, "type": "ward", "is_public": False, **extra}
    r = client.post(f"{API}/admin/units", json=body, headers=h)
    assert r.status_code == 201
    return r.json()


def test_unit_image_is_served_only_for_public_units(client):
    h = auth(client, SYSADMIN)
    unit = make_unit(client, h)
    r = client.post(
        f"{API}/admin/units/{unit['id']}/image", params={"filename": "a.png"}, content=PNG, headers=h
    )
    assert r.status_code == 201 and r.json()["has_image"] is True

    # واحد عمومی نیست: تصویرش برای مراجع دیده نمی‌شود.
    assert client.get(f"{API}/public/units/{unit['id']}/image").status_code == 404

    client.put(f"{API}/admin/units/{unit['id']}", json={**unit, "is_public": True}, headers=h)
    served = client.get(f"{API}/public/units/{unit['id']}/image")
    assert served.status_code == 200 and served.headers["content-type"] == "image/png"


def test_unit_image_rejects_non_image_files(client):
    h = auth(client, SYSADMIN)
    unit = make_unit(client, h)
    r = client.post(
        f"{API}/admin/units/{unit['id']}/image", params={"filename": "a.pdf"}, content=PNG, headers=h
    )
    assert r.status_code == 400


def test_unit_in_use_cannot_be_deleted(client, db):
    h = auth(client, SYSADMIN)
    emergency = db.scalar(select(Unit).where(Unit.name == "اورژانس"))
    assert client.delete(f"{API}/admin/units/{emergency.id}", headers=h).status_code == 409


def test_unused_unit_can_be_deleted(client):
    h = auth(client, SYSADMIN)
    unit = make_unit(client, h)
    assert client.delete(f"{API}/admin/units/{unit['id']}", headers=h).status_code == 204
    names = [u["name"] for u in client.get(f"{API}/admin/units", headers=h).json()]
    assert unit["name"] not in names


def test_staff_cannot_edit_units(client):
    h = auth(client, STAFF)
    unit = make_unit(client, auth(client, SYSADMIN))
    assert (
        client.post(
            f"{API}/admin/units/{unit['id']}/image", params={"filename": "a.png"}, content=PNG, headers=h
        ).status_code
        == 403
    )
    assert client.delete(f"{API}/admin/units/{unit['id']}", headers=h).status_code == 403


def test_sample_departments_are_seeded_with_placeholders(db):
    internal = db.scalar(select(Unit).where(Unit.name == "داخلی ۱"))
    assert internal is not None and internal.is_public is True
    assert internal.hours and "نمونه" in internal.hours
    assert db.scalar(select(Unit).where(Unit.name == "ICU5 جنرال")).is_public is False


def test_quality_pr_role_can_manage_units_and_translations():
    _, perms = DEFAULT_ROLES["quality_pr"]
    assert {"unit.manage", "translation.manage"} <= set(perms)
