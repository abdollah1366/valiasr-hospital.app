from tests.conftest import CONTENT, STAFF, auth

API = "/api/v1"
PDF = b"%PDF-1.4 test"


def make(client, h, **kw):
    body = {"title": "رژیم دیابت", "kind": "article", "body": "متن آموزشی", "condition": "دیابت", **kw}
    return client.post(f"{API}/admin/education", json=body, headers=h)


def test_library_hidden_until_published_and_filters(client):
    h = auth(client, CONTENT)
    a = make(client, h).json()
    assert client.get(f"{API}/public/education").json() == []
    assert client.get(f"{API}/public/education/{a['id']}").status_code == 404

    r = client.patch(f"{API}/admin/education/{a['id']}", json={"is_published": True}, headers=h)
    assert r.status_code == 200
    b = make(client, h, title="ورزش بعد از عمل", condition="قلب", care_type="after_procedure").json()
    client.patch(f"{API}/admin/education/{b['id']}", json={"is_published": True}, headers=h)

    assert len(client.get(f"{API}/public/education").json()) == 2
    only = client.get(f"{API}/public/education", params={"q": "ورزش"}).json()
    assert [i["id"] for i in only] == [b["id"]]
    assert client.get(f"{API}/public/education", params={"condition": "دیابت"}).json()[0]["id"] == a["id"]
    assert len(client.get(f"{API}/public/education", params={"care_type": "after_procedure"}).json()) == 1
    assert client.get(f"{API}/public/education", params={"kind": "video"}).json() == []

    f = client.get(f"{API}/public/education/filters").json()
    assert f["conditions"] == ["دیابت", "قلب"] and "nutrition" in f["care_types"]
    detail = client.get(f"{API}/public/education/{a['id']}").json()
    assert detail["body"] == "متن آموزشی" and detail["has_file"] is False


def test_publish_requires_content(client):
    h = auth(client, CONTENT)
    assert make(client, h, body=None, is_published=True).status_code == 400
    v = make(client, h, kind="video").json()
    r = client.patch(f"{API}/admin/education/{v['id']}", json={"is_published": True}, headers=h)
    assert r.status_code == 400 and r.json()["detail"]["code"] == "no_file"
    r = client.patch(
        f"{API}/admin/education/{v['id']}",
        json={"is_published": True, "external_url": "https://www.aparat.com/v/abc"},
        headers=h,
    )
    assert r.status_code == 200 and r.json()["external_url"].startswith("https://")
    bad = client.patch(
        f"{API}/admin/education/{v['id']}", json={"external_url": "javascript:alert(1)"}, headers=h
    )
    assert bad.status_code == 422


def test_file_upload_type_must_match_kind_and_is_served(client):
    h = auth(client, CONTENT)
    p = make(client, h, kind="pdf", body=None).json()
    wrong = client.post(f"{API}/admin/education/{p['id']}/file?filename=a.mp4", content=b"x", headers=h)
    assert wrong.status_code == 400
    ok = client.post(f"{API}/admin/education/{p['id']}/file?filename=guide.pdf", content=PDF, headers=h)
    assert ok.status_code == 201 and ok.json()["content_type"] == "application/pdf"
    # قبل از انتشار فایل عمومی نیست
    assert client.get(f"{API}/public/education/{p['id']}/file").status_code == 404
    client.patch(f"{API}/admin/education/{p['id']}", json={"is_published": True}, headers=h)
    f = client.get(f"{API}/public/education/{p['id']}/file")
    assert f.status_code == 200 and f.content == PDF and f.headers["content-type"] == "application/pdf"
    first = client.get(f"{API}/public/education/{p['id']}").json()["file_version"]
    client.post(f"{API}/admin/education/{p['id']}/file?filename=new.pdf", content=PDF + b"2", headers=h)
    assert client.get(f"{API}/public/education/{p['id']}").json()["file_version"] != first
    # حذف فایل بدون پیوند، محتوای منتشرشده را پیش‌نویس می‌کند
    d = client.delete(f"{API}/admin/education/{p['id']}/file", headers=h).json()
    assert d["is_published"] is False and d["has_file"] is False


def test_translation_shown_only_when_approved(client):
    h = auth(client, CONTENT)
    a = make(client, h, is_published=True).json()
    key = {"entity_type": "education", "entity_id": a["id"], "language": "en", "field": "title"}
    assert (
        client.put(f"{API}/admin/translations", json={**key, "text": "Diabetes diet"}, headers=h).status_code
        == 200
    )
    assert (
        client.get(f"{API}/public/education/{a['id']}", params={"lang": "en"}).json()["title"] == "رژیم دیابت"
    )
    assert client.post(f"{API}/admin/translations/approve", json=key, headers=h).status_code == 200
    assert (
        client.get(f"{API}/public/education/{a['id']}", params={"lang": "en"}).json()["title"]
        == "Diabetes diet"
    )
    assert client.get(f"{API}/public/education", params={"lang": "en"}).json()[0]["title"] == "Diabetes diet"


def test_only_managers_and_delete(client):
    h = auth(client, CONTENT)
    a = make(client, h).json()
    assert client.get(f"{API}/admin/education").status_code == 401
    assert client.get(f"{API}/admin/education", headers=auth(client, STAFF)).status_code == 403
    assert client.delete(f"{API}/admin/education/{a['id']}", headers=h).status_code == 204
    assert client.get(f"{API}/admin/education", headers=h).json() == []
