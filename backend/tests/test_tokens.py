from tests.conftest import SYSADMIN, auth
from tests.test_appointments import DOCTOR, SECRETARY, book, first_doctor, free_slot, patient_headers

API = "/api/v1"


def tokens(client, h):
    return client.get(f"{API}/patient/tokens", headers=h).json()


def visit(client, h, index=0):
    d = first_doctor(client)
    appt = book(client, h, free_slot(client, d["id"], index)["id"]).json()
    r = client.post(
        f"{API}/doctor/me/appointments/{appt['id']}/status",
        json={"status": "attended"},
        headers=auth(client, DOCTOR),
    )
    assert r.status_code == 200
    return appt


def test_referral_rewards_both_after_first_attended_visit(client):
    a = patient_headers(client, "09121110001")
    b = patient_headers(client, "09121110002")
    code = tokens(client, a)["referral_code"]
    assert tokens(client, a)["referral_code"] == code and len(code) == 6
    assert tokens(client, a)["balance"] == 0

    assert client.post(f"{API}/patient/referral", json={"code": "ZZZZZZ"}, headers=b).status_code == 404
    assert (
        client.post(
            f"{API}/patient/referral", json={"code": tokens(client, b)["referral_code"]}, headers=b
        ).status_code
        == 409
    )
    r = client.post(f"{API}/patient/referral", json={"code": code.lower()}, headers=b)
    assert r.status_code == 200 and r.json()["referred"] is True
    assert client.post(f"{API}/patient/referral", json={"code": code}, headers=b).status_code == 409
    # قبل از ویزیت چیزی داده نمی‌شود
    assert tokens(client, a)["balance"] == 0

    visit(client, b)
    ta, tb = tokens(client, a), tokens(client, b)
    assert ta["balance"] == ta["referral_reward"] == 50
    assert tb["balance"] == tb["referred_bonus"] == 20
    # ویزیت دوم دوباره پاداش نمی‌دهد
    visit(client, b, index=1)
    assert tokens(client, a)["balance"] == 50


def test_admin_award_and_negative_balance_guard(client):
    h = patient_headers(client, "09121110003")
    t = tokens(client, h)
    pid = client.get(
        f"{API}/manage/tokens/patients", params={"q": "9121110003"}, headers=auth(client, SYSADMIN)
    ).json()[0]["id"]
    sysadmin = auth(client, SYSADMIN)
    r = client.post(
        f"{API}/manage/tokens/award",
        json={"patient_id": pid, "amount": 100, "note": "هدیه"},
        headers=sysadmin,
    )
    assert r.json()["balance"] == 100 and t["balance"] == 0
    bad = client.post(
        f"{API}/manage/tokens/award",
        json={"patient_id": pid, "amount": -500, "note": "کسر"},
        headers=sysadmin,
    )
    assert bad.status_code == 409 and bad.json()["detail"]["code"] == "insufficient_tokens"
    assert (
        client.post(
            f"{API}/manage/tokens/award",
            json={"patient_id": pid, "amount": 0, "note": "صفر"},
            headers=sysadmin,
        ).status_code
        == 422
    )
    assert tokens(client, h)["history"][0]["amount"] == 100


def test_redeem_voucher_flow(client):
    sysadmin = auth(client, SYSADMIN)
    h = patient_headers(client, "09121110004")

    tokens(client, h)
    pid = client.get(f"{API}/manage/tokens/patients", params={"q": "9121110004"}, headers=sysadmin).json()[0][
        "id"
    ]
    reward = client.post(
        f"{API}/manage/tokens/rewards", json={"title": "تخفیف پذیرش", "cost": 60}, headers=sysadmin
    ).json()
    assert [r["title"] for r in tokens(client, h)["rewards"]] == ["تخفیف پذیرش"]
    r = client.post(f"{API}/patient/tokens/redeem", json={"reward_id": reward["id"]}, headers=h)
    assert r.status_code == 409  # موجودی ندارد
    client.post(
        f"{API}/manage/tokens/award",
        json={"patient_id": pid, "amount": 100, "note": "هدیه"},
        headers=sysadmin,
    )
    r = client.post(f"{API}/patient/tokens/redeem", json={"reward_id": reward["id"]}, headers=h)
    assert r.status_code == 201
    code = r.json()["code"]
    assert tokens(client, h)["balance"] == 40

    sec = auth(client, SECRETARY)
    v = client.get(f"{API}/manage/tokens/vouchers/{code.lower()}", headers=sec).json()
    assert v["status"] == "issued" and v["patient_mobile"].endswith("9121110004")
    assert client.post(f"{API}/manage/tokens/vouchers/{code}/use", headers=sec).json()["status"] == "used"
    again = client.post(f"{API}/manage/tokens/vouchers/{code}/use", headers=sec)
    assert again.status_code == 409
    # منشی نمی‌تواند ژتون بدهد یا کد را باطل و بازپرداخت کند
    assert client.post(f"{API}/manage/tokens/vouchers/{code}/cancel", headers=sec).status_code == 403
    assert client.get(f"{API}/manage/tokens/rewards", headers=sec).status_code == 403


def test_cancel_voucher_refunds_and_inactive_reward_hidden(client):
    sysadmin = auth(client, SYSADMIN)
    h = patient_headers(client, "09121110005")
    tokens(client, h)
    pid = client.get(f"{API}/manage/tokens/patients", params={"q": "9121110005"}, headers=sysadmin).json()[0][
        "id"
    ]
    client.post(
        f"{API}/manage/tokens/award", json={"patient_id": pid, "amount": 50, "note": "هدیه"}, headers=sysadmin
    )
    reward = client.post(
        f"{API}/manage/tokens/rewards", json={"title": "امتیاز", "cost": 30}, headers=sysadmin
    ).json()
    code = client.post(f"{API}/patient/tokens/redeem", json={"reward_id": reward["id"]}, headers=h).json()[
        "code"
    ]
    assert tokens(client, h)["balance"] == 20
    assert (
        client.post(f"{API}/manage/tokens/vouchers/{code}/cancel", headers=sysadmin).json()["status"]
        == "cancelled"
    )
    assert tokens(client, h)["balance"] == 50
    client.patch(f"{API}/manage/tokens/rewards/{reward['id']}", json={"is_active": False}, headers=sysadmin)
    assert tokens(client, h)["rewards"] == []
    assert (
        client.post(f"{API}/patient/tokens/redeem", json={"reward_id": reward["id"]}, headers=h).status_code
        == 404
    )


def test_tokens_need_patient_login(client):
    assert client.get(f"{API}/patient/tokens").status_code == 401
    assert client.get(f"{API}/manage/tokens/patients", params={"q": "912"}).status_code == 401
