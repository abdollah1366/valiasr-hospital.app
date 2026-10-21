from tests.conftest import CONTENT, SAFETY, STAFF, SYSADMIN, auth


def test_admin_page_is_served_with_strict_headers(client):
    root = client.get("/", follow_redirects=False)
    assert root.status_code in (307, 308) and root.headers["location"] == "/admin/"
    r = client.get("/admin", follow_redirects=False)
    assert r.status_code in (307, 308) and r.headers["location"] == "/admin/"
    page = client.get("/admin/")
    assert page.status_code == 200 and 'dir="rtl"' in page.text
    csp = page.headers["content-security-policy"]
    assert "script-src 'self'" in csp and "frame-ancestors 'none'" in csp
    assert page.headers["x-frame-options"] == "DENY"
    js = client.get("/admin/app.js")
    assert js.status_code == 200 and "javascript" in js.headers["content-type"]
    assert client.get("/admin/schedules.js").status_code == 200
    assert client.get("/admin/translations.js").status_code == 200
    assert client.get("/admin/appointments.js").status_code == 200
    assert client.get("/admin/reviews.js").status_code == 200
    assert client.get("/admin/tokens.js").status_code == 200
    assert client.get("/admin/education.js").status_code == 200
    assert client.get("/admin/fonts/vazirmatn_regular.ttf").status_code == 200
    assert client.get("/admin/logo.svg").status_code == 200
    # API همان رفتار قبلی را دارد و سرآیندهای پنل به آن اضافه نمی‌شود.
    assert "content-security-policy" not in client.get("/health").headers


def test_admin_announcement_list_needs_permission(client):
    assert client.get("/api/v1/admin/announcements", headers=auth(client, STAFF)).status_code == 403
    items = client.get("/api/v1/admin/announcements", headers=auth(client, CONTENT)).json()
    audiences = {a["audience"] for a in items}
    assert "public" in audiences and "staff" in audiences


def test_catalog_readable_by_content_manager_only_among_non_admins(client):
    h = auth(client, CONTENT)
    assert client.get("/api/v1/admin/units", headers=h).status_code == 200
    assert client.get("/api/v1/admin/roles", headers=h).status_code == 200
    assert client.get("/api/v1/admin/units", headers=auth(client, SYSADMIN)).status_code == 200
    for code in (STAFF, SAFETY):
        assert client.get("/api/v1/admin/units", headers=auth(client, code)).status_code == 403
    # خواندن فهرست به معنی اجازه تغییر نیست.
    assert client.post("/api/v1/admin/units", headers=h, json={"name": "آزمایشی"}).status_code == 403


def test_admin_scripts_do_not_redeclare_top_level_names():
    """اسکریپت‌های پنل در یک دامنه سراسری بارگذاری می‌شوند؛ تعریف دوباره یک نام کل صفحه را از کار می‌اندازد."""
    import re
    from pathlib import Path

    web = Path(__file__).resolve().parents[1] / "app" / "admin_web"
    decl = re.compile(r"^(?:async\s+)?(?:function\s*\*?|const|let|class)\s+([A-Za-z_$][\w$]*)", re.M)
    seen: dict[str, str] = {}
    for js in sorted(web.glob("*.js")):
        for name in decl.findall(js.read_text(encoding="utf-8")):
            assert name not in seen, f"{name} در {seen[name]} و {js.name} دوبار تعریف شده است"
            seen[name] = js.name
