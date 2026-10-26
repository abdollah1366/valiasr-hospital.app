"""آموزش به بیمار: کتابخانه محتوا (مقاله، تصویر، ویدئو، صوت، PDF).

- عمومی (بدون ورود): فقط محتوای منتشرشده، با جست‌وجو و فیلتر؛ متن‌ها با ترجمه تأییدشده زبان درخواست
- مدیریت (education.manage): ساخت، ویرایش، بارگذاری فایل، انتشار و حذف
"""

import uuid

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import FileResponse
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.core.audit import audit
from app.core.db import get_db
from app.core.deps import CurrentUser, api_error, client_ip, require
from app.models import (
    ContentTranslation,
    EducationAssignment,
    EducationItem,
    EducationRating,
    MediaFile,
    Specialty,
)
from app.schemas.education import (
    EducationDetailOut,
    EducationFiltersOut,
    EducationIn,
    EducationListOut,
    EducationManageOut,
    EducationPatch,
    EducationSpecialtyOut,
)
from app.services.media import store_upload
from app.services.training import media_path
from app.services.translations import Localizer, content_language, localize

router = APIRouter(tags=["education"])

CARE_TYPES = (
    "prevention",
    "before_procedure",
    "after_procedure",
    "medication",
    "nutrition",
    "exercise",
    "home_care",
    "warning_signs",
    "other",
)
KINDS = ("article", "image", "video", "audio", "pdf")

# نوع فایل مجاز برای هر نوع محتوا؛ نوع محتوا را سرور از پسوند تعیین می‌کند.
KIND_FILES: dict[str, tuple[dict[str, str], int]] = {
    "image": ({".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp"}, 5),
    "pdf": ({".pdf": "application/pdf"}, 30),
    "audio": ({".mp3": "audio/mpeg", ".m4a": "audio/mp4", ".ogg": "audio/ogg"}, 50),
    "video": ({".mp4": "video/mp4", ".webm": "video/webm"}, 200),
}


def _specialty_names(
    db: Session, items: list[EducationItem], language: str | None = None
) -> dict[uuid.UUID, str]:
    ids = {i.specialty_id for i in items if i.specialty_id}
    if not ids:
        return {}
    loc = Localizer(db, language)
    loc.prefetch("specialty", ids)
    return {
        s.id: loc.text("specialty", s, "name")
        for s in db.scalars(select(Specialty).where(Specialty.id.in_(ids)))
    }


def rating_map(db: Session, item_ids: list[uuid.UUID]) -> dict[uuid.UUID, tuple[float, int]]:
    """میانگین و تعداد امتیاز هر آموزش."""
    if not item_ids:
        return {}
    rows = db.execute(
        select(EducationRating.item_id, func.avg(EducationRating.stars), func.count())
        .where(EducationRating.item_id.in_(item_ids))
        .group_by(EducationRating.item_id)
    )
    return {item_id: (round(float(avg), 1), n) for item_id, avg, n in rows}


def _media(db: Session, item: EducationItem) -> MediaFile | None:
    return db.get(MediaFile, item.media_id) if item.media_id else None


def _list_out(
    item: EducationItem,
    names: dict[uuid.UUID, str],
    ratings: dict[uuid.UUID, tuple[float, int]] | None = None,
) -> dict:
    avg, count = (ratings or {}).get(item.id, (None, 0))
    return {
        "id": item.id,
        "title": item.title,
        "summary": item.summary,
        "kind": item.kind,
        "condition": item.condition,
        "care_type": item.care_type,
        "specialty_id": item.specialty_id,
        "specialty_name": names.get(item.specialty_id) if item.specialty_id else None,
        "source": item.source,
        "reviewed_on": item.reviewed_on,
        "has_file": item.media_id is not None,
        "file_version": str(item.media_id) if item.media_id else None,
        "rating_average": avg,
        "rating_count": count,
    }


def _detail_out(db: Session, item: EducationItem, language: str | None = None) -> dict:
    m = _media(db, item)
    return {
        **_list_out(item, _specialty_names(db, [item], language), rating_map(db, [item.id])),
        "body": item.body,
        "external_url": item.external_url,
        "content_type": m.content_type if m else None,
    }


def _manage_out(db: Session, item: EducationItem) -> EducationManageOut:
    m = _media(db, item)
    assigned, seen = db.execute(
        select(func.count(), func.count(EducationAssignment.first_seen_at)).where(
            EducationAssignment.item_id == item.id
        )
    ).one()
    return EducationManageOut(
        **_detail_out(db, item),
        assigned_count=assigned,
        seen_count=seen,
        is_published=item.is_published,
        file_name=m.original_name if m else None,
        created_at=item.created_at,
        updated_at=item.updated_at,
    )


def _check_publishable(item: EducationItem) -> None:
    if item.kind == "article":
        if not (item.body or "").strip():
            raise api_error(400, "empty_body", "برای انتشار مقاله متن آن را بنویسید")
    elif item.media_id is None and not item.external_url:
        raise api_error(400, "no_file", "برای انتشار، فایل را بارگذاری کنید یا پیوند بگذارید")


def _check_specialty(db: Session, specialty_id: uuid.UUID | None) -> None:
    if specialty_id is not None and db.get(Specialty, specialty_id) is None:
        raise api_error(404, "not_found", "تخصص پیدا نشد")


# ---------------------------------------------------------------- عمومی


@router.get("/public/education", response_model=list[EducationListOut])
def public_education(
    q: str | None = Query(None, max_length=100),
    kind: str | None = Query(None, max_length=10),
    care_type: str | None = Query(None, max_length=20),
    condition: str | None = Query(None, max_length=150),
    specialty_id: uuid.UUID | None = None,
    language: str | None = Depends(content_language),
    db: Session = Depends(get_db),
):
    stmt = select(EducationItem).where(EducationItem.is_published.is_(True))
    if kind:
        stmt = stmt.where(EducationItem.kind == kind)
    if care_type:
        stmt = stmt.where(EducationItem.care_type == care_type)
    if condition:
        stmt = stmt.where(EducationItem.condition == condition)
    if specialty_id:
        stmt = stmt.where(EducationItem.specialty_id == specialty_id)
    if q and q.strip():
        like = "%" + q.strip().replace("%", r"\%").replace("_", r"\_") + "%"
        stmt = stmt.where(
            or_(
                EducationItem.title.ilike(like),
                EducationItem.summary.ilike(like),
                EducationItem.condition.ilike(like),
                EducationItem.body.ilike(like),
            )
        )
    items = list(db.scalars(stmt.order_by(EducationItem.updated_at.desc(), EducationItem.title)))
    names = _specialty_names(db, items, language)
    ratings = rating_map(db, [i.id for i in items])
    out = [EducationListOut(**_list_out(i, names, ratings)) for i in items]
    return localize(db, "education", out, language)


@router.get("/public/education/filters", response_model=EducationFiltersOut)
def public_education_filters(language: str | None = Depends(content_language), db: Session = Depends(get_db)):
    pub = EducationItem.is_published.is_(True)
    conditions = db.scalars(
        select(EducationItem.condition).where(pub, EducationItem.condition.is_not(None)).distinct()
    )
    used = {
        r
        for r in db.scalars(
            select(EducationItem.specialty_id).where(pub, EducationItem.specialty_id.is_not(None))
        )
    }
    specs = list(
        db.scalars(
            select(Specialty).where(Specialty.id.in_(used)).order_by(Specialty.sort_order, Specialty.name)
        )
    )
    loc = Localizer(db, language)
    loc.prefetch("specialty", [s.id for s in specs])
    return EducationFiltersOut(
        conditions=sorted(c for c in conditions if c),
        care_types=list(CARE_TYPES),
        kinds=list(KINDS),
        specialties=[EducationSpecialtyOut(id=s.id, name=loc.text("specialty", s, "name")) for s in specs],
    )


def _published(db: Session, item_id: uuid.UUID) -> EducationItem:
    item = db.get(EducationItem, item_id)
    if item is None or not item.is_published:
        raise api_error(404, "not_found", "این آموزش پیدا نشد")
    return item


@router.get("/public/education/{item_id}", response_model=EducationDetailOut)
def public_education_item(
    item_id: uuid.UUID,
    language: str | None = Depends(content_language),
    db: Session = Depends(get_db),
):
    item = _published(db, item_id)
    out = EducationDetailOut(**_detail_out(db, item, language))
    return localize(db, "education", [out], language)[0]


@router.get("/public/education/{item_id}/file", include_in_schema=False)
def public_education_file(item_id: uuid.UUID, db: Session = Depends(get_db)):
    item = _published(db, item_id)
    m = _media(db, item)
    if m is None:
        raise api_error(404, "not_found", "فایلی برای این آموزش نیست")
    return FileResponse(
        media_path(m),
        media_type=m.content_type,
        headers={"X-Content-Type-Options": "nosniff", "Cache-Control": "public, max-age=3600"},
    )


# ---------------------------------------------------------------- مدیریت


@router.get("/admin/education", response_model=list[EducationManageOut])
def manage_list(_: CurrentUser = Depends(require("education.manage")), db: Session = Depends(get_db)):
    items = list(db.scalars(select(EducationItem).order_by(EducationItem.updated_at.desc())))
    return [_manage_out(db, i) for i in items]


@router.post("/admin/education", response_model=EducationManageOut, status_code=201)
def manage_create(
    body: EducationIn,
    request: Request,
    cu: CurrentUser = Depends(require("education.manage")),
    db: Session = Depends(get_db),
):
    _check_specialty(db, body.specialty_id)
    data = body.model_dump()
    data["external_url"] = str(body.external_url) if body.external_url else None
    item = EducationItem(**data, created_by=cu.id)
    if item.is_published:
        _check_publishable(item)
    db.add(item)
    db.flush()
    audit(db, "education.created", cu.id, "education", item.id, client_ip(request))
    db.commit()
    db.refresh(item)
    return _manage_out(db, item)


def _get(db: Session, item_id: uuid.UUID) -> EducationItem:
    item = db.get(EducationItem, item_id)
    if item is None:
        raise api_error(404, "not_found", "این آموزش پیدا نشد")
    return item


@router.patch("/admin/education/{item_id}", response_model=EducationManageOut)
def manage_update(
    item_id: uuid.UUID,
    body: EducationPatch,
    request: Request,
    cu: CurrentUser = Depends(require("education.manage")),
    db: Session = Depends(get_db),
):
    item = _get(db, item_id)
    changes = body.model_dump(exclude_unset=True)
    if "specialty_id" in changes:
        _check_specialty(db, changes["specialty_id"])
    if "external_url" in changes:
        changes["external_url"] = str(body.external_url) if body.external_url else None
    for key in ("title", "kind", "care_type", "is_published"):
        if key in changes and changes[key] is None:
            del changes[key]
    if "kind" in changes and changes["kind"] != item.kind and item.media_id is not None:
        raise api_error(409, "kind_has_file", "ابتدا فایل بارگذاری‌شده را حذف کنید، بعد نوع محتوا را عوض کنید")
    for key, value in changes.items():
        setattr(item, key, value)
    if item.is_published:
        _check_publishable(item)
    audit(db, "education.updated", cu.id, "education", item.id, client_ip(request))
    db.commit()
    db.refresh(item)
    return _manage_out(db, item)


@router.post("/admin/education/{item_id}/file", response_model=EducationManageOut, status_code=201)
async def manage_upload(
    item_id: uuid.UUID,
    request: Request,
    filename: str = Query(min_length=1, max_length=300),
    cu: CurrentUser = Depends(require("education.manage")),
    db: Session = Depends(get_db),
):
    """فایل محتوا؛ بدنه درخواست خود فایل است و نوعش باید با نوع محتوا بخواند."""
    item = _get(db, item_id)
    if item.kind not in KIND_FILES:
        raise api_error(400, "kind_no_file", "مقاله فایل ندارد؛ نوع محتوا را عوض کنید")
    allowed, max_mb = KIND_FILES[item.kind]
    m = await store_upload(request, filename, allowed, max_mb, cu.id)
    db.add(m)
    db.flush()
    old = _media(db, item)
    item.media_id = m.id
    if old is not None:
        _drop_media(db, old)
    audit(db, "education.file_uploaded", cu.id, "education", item.id, client_ip(request))
    db.commit()
    db.refresh(item)
    return _manage_out(db, item)


def _drop_media(db: Session, m: MediaFile) -> None:
    path = media_path(m)
    db.delete(m)
    db.flush()
    path.unlink(missing_ok=True)


@router.delete("/admin/education/{item_id}/file", response_model=EducationManageOut)
def manage_delete_file(
    item_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("education.manage")),
    db: Session = Depends(get_db),
):
    item = _get(db, item_id)
    m = _media(db, item)
    if m is not None:
        item.media_id = None
        db.flush()
        _drop_media(db, m)
        if item.is_published and not item.external_url:
            item.is_published = False
    audit(db, "education.file_deleted", cu.id, "education", item.id, client_ip(request))
    db.commit()
    db.refresh(item)
    return _manage_out(db, item)


@router.delete("/admin/education/{item_id}", status_code=204)
def manage_delete(
    item_id: uuid.UUID,
    request: Request,
    cu: CurrentUser = Depends(require("education.manage")),
    db: Session = Depends(get_db),
):
    item = _get(db, item_id)
    m = _media(db, item)
    item.media_id = None
    db.flush()
    if m is not None:
        _drop_media(db, m)
    db.query(ContentTranslation).filter(
        ContentTranslation.entity_type == "education", ContentTranslation.entity_id == item.id
    ).delete()
    db.delete(item)
    audit(db, "education.deleted", cu.id, "education", item_id, client_ip(request))
    db.commit()
