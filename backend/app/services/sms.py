"""ارسال کد ورود مراجعان با پیامک."""

import json
import logging
import urllib.parse
import urllib.request

from app.core.config import get_settings
from app.services.phone import mask_mobile

log = logging.getLogger(__name__)


class SmsError(Exception):
    pass


def send_otp(mobile: str, code: str) -> None:
    s = get_settings()
    if s.sms_provider == "console":
        # فقط برای توسعه؛ در production این حالت مجاز نیست (Settings.check_production).
        log.warning("OTP for %s: %s", mask_mobile(mobile), code)
        return
    if s.sms_provider == "kavenegar":
        _kavenegar(mobile, code, s.sms_api_key or "", s.sms_template or "")
        return
    raise SmsError(f"unknown sms provider: {s.sms_provider}")


def _kavenegar(mobile: str, code: str, api_key: str, template: str) -> None:
    # https://kavenegar.com/rest.html#sms-Lookup ؛ شماره داخلی با ۰ و شماره خارجی با 00 فرستاده می‌شود.
    receptor = "0" + mobile[3:] if mobile.startswith("+98") else "00" + mobile[1:]
    query = urllib.parse.urlencode({"receptor": receptor, "token": code, "template": template})
    url = f"https://api.kavenegar.com/v1/{urllib.parse.quote(api_key)}/verify/lookup.json?{query}"
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            body = json.loads(r.read().decode())
    except Exception as e:  # noqa: BLE001
        raise SmsError("sms request failed") from e
    if body.get("return", {}).get("status") != 200:
        raise SmsError(f"sms rejected: {body.get('return')}")
