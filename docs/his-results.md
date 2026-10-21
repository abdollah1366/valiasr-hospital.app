# اتصال جواب آزمایش و تصویربرداری به سرور بیمارستان

پیش‌فرض (`RAZI_HIS_PROVIDER=sample`): داده نمونه نشان داده می‌شود و چیزی ذخیره نمی‌شود.

برای اتصال به سامانه بیمارستان (HIS/LIS/PACS):

```
RAZI_HIS_PROVIDER=http
RAZI_HIS_BASE_URL=https://his.example.ir/api
RAZI_HIS_API_KEY=...        # اختیاری، به صورت Bearer فرستاده می‌شود
RAZI_HIS_TIMEOUT_SECONDS=10
```

سرور ما این درخواست را می‌فرستد:

`GET {RAZI_HIS_BASE_URL}/patients/results?mobile=%2B989121234567`

و آرایه JSON زیر را انتظار دارد (`app/schemas/results.py`، `ResultOut`):

```json
[{
  "id": "lab-123", "kind": "lab", "title": "CBC",
  "performed_at": "2026-10-01T08:00:00+03:30", "status": "ready",
  "department": "آزمایشگاه", "doctor_name": null,
  "summary": null, "report": null, "file_url": null,
  "items": [{"name": "Hemoglobin", "value": "11.9", "unit": "g/dL", "reference": "12 - 16", "flag": "low"}]
}]
```

`kind` فقط `lab` یا `imaging` و `status` فقط `ready` یا `pending` است؛ `flag` یکی از `normal|high|low`.
در صورت خطای شبکه یا پاسخ نامعتبر، اپ پیام «اتصال به سامانه بیمارستان برقرار نشد» (کد 502) می‌بیند.
اگر سامانه بیمارستان قالب دیگری دارد، تبدیل آن در `app/services/his.py` (`_fetch_http`) انجام شود.
