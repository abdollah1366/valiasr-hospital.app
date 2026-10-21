import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

from app.core.config import Settings
from app.services import his
from tests.test_appointments import patient_headers

API = "/api/v1"


def test_sample_results_need_login_and_filter_by_kind(client):
    assert client.get(f"{API}/patient/results").status_code == 401
    h = patient_headers(client)
    rows = client.get(f"{API}/patient/results", headers=h).json()
    assert {r["kind"] for r in rows} == {"lab", "imaging"}
    assert [r["performed_at"] for r in rows] == sorted((r["performed_at"] for r in rows), reverse=True)
    labs = client.get(f"{API}/patient/results", params={"kind": "lab"}, headers=h).json()
    assert labs and all(r["kind"] == "lab" for r in labs)
    assert any(r["status"] == "pending" for r in labs)
    cbc = client.get(f"{API}/patient/results/lab-cbc", headers=h).json()
    assert any(i["flag"] == "low" for i in cbc["items"])
    assert client.get(f"{API}/patient/results/nope", headers=h).status_code == 404
    assert client.get(f"{API}/patient/results", params={"kind": "x"}, headers=h).status_code == 422


class FakeHis(BaseHTTPRequestHandler):
    seen: dict = {}

    def do_GET(self):
        FakeHis.seen = {"path": self.path, "auth": self.headers.get("Authorization")}
        body = json.dumps(
            [
                {
                    "id": "h1",
                    "kind": "lab",
                    "title": "از سرور بیمارستان",
                    "performed_at": "2026-10-01T08:00:00+03:30",
                }
            ]
        ).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


def test_http_provider_reads_hospital_server(client, monkeypatch):
    srv = HTTPServer(("127.0.0.1", 0), FakeHis)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    try:
        s = Settings(his_provider="http", his_base_url=f"http://127.0.0.1:{srv.server_port}", his_api_key="k")
        monkeypatch.setattr(his, "get_settings", lambda: s)
        rows = client.get(f"{API}/patient/results", headers=patient_headers(client)).json()
        assert [r["title"] for r in rows] == ["از سرور بیمارستان"]
        assert FakeHis.seen["auth"] == "Bearer k"
        assert FakeHis.seen["path"].startswith("/patients/results?mobile=")
    finally:
        srv.shutdown()


def test_http_provider_failure_is_502(client, monkeypatch):
    s = Settings(his_provider="http", his_base_url="http://127.0.0.1:1", his_timeout_seconds=1)
    monkeypatch.setattr(his, "get_settings", lambda: s)
    r = client.get(f"{API}/patient/results", headers=patient_headers(client))
    assert r.status_code == 502 and r.json()["detail"]["code"] == "his_unavailable"
