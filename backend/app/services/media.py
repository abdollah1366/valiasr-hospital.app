"""ذخیره امن فایل‌های بارگذاری‌شده روی دیسک (مشترک بین آموزش و درخواست‌ها)."""

import hashlib
import os
import uuid
from pathlib import Path

from fastapi import Request

from app.core.deps import api_error
from app.models import MediaFile
from app.services.training import media_root


async def store_upload(
    request: Request, filename: str, allowed: dict[str, str], max_mb: int, uploaded_by: uuid.UUID
) -> MediaFile:
    """بدنه درخواست خود فایل است (نه multipart) تا فایل‌های بزرگ تکه‌تکه روی دیسک نوشته شوند.

    نوع محتوا را سرور از پسوند تعیین می‌کند، نه مرورگر. MediaFile اضافه می‌شود ولی commit با فراخواننده است.
    """
    name = Path(filename.replace("\\", "/")).name.strip()
    ext = Path(name).suffix.lower()
    content_type = allowed.get(ext)
    if not name or content_type is None:
        raise api_error(400, "file_type", "این نوع فایل پذیرفته نمی‌شود")
    limit = max_mb * 1024 * 1024
    too_large = api_error(413, "file_too_large", f"حجم فایل بیشتر از {max_mb} مگابایت است")
    declared = request.headers.get("content-length")
    if declared and declared.isdigit() and int(declared) > limit:
        raise too_large
    root = media_root()
    storage_name = uuid.uuid4().hex + ext
    tmp = root / (storage_name + ".part")
    digest = hashlib.sha256()
    size = 0
    try:
        with open(tmp, "wb") as f:
            async for chunk in request.stream():
                size += len(chunk)
                if size > limit:
                    raise too_large
                digest.update(chunk)
                f.write(chunk)
        if size == 0:
            raise api_error(400, "empty_file", "فایل خالی است")
        os.replace(tmp, root / storage_name)
    finally:
        if tmp.exists():
            tmp.unlink()
    return MediaFile(
        original_name=name,
        content_type=content_type,
        size=size,
        sha256=digest.hexdigest(),
        storage_name=storage_name,
        uploaded_by=uploaded_by,
    )
