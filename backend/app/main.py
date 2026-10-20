from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

from app.core.config import get_settings
from app.routers import (
    admin,
    announcements,
    auth,
    chat,
    dashboard,
    incidents,
    notifications,
    patient,
    public,
    requests,
    schedules,
    training,
    translations,
)

API_PREFIX = "/api/v1"
ADMIN_WEB_DIR = Path(__file__).parent / "admin_web"

# پنل فقط فایل‌های خودش را بارگذاری می‌کند؛ اسکریپت و استایل درون‌خطی و قاب‌گرفتن صفحه مجاز نیست.
ADMIN_SECURITY_HEADERS = {
    "Content-Security-Policy": (
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
        "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
    ),
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Cache-Control": "no-cache",
}


def create_app() -> FastAPI:
    settings = get_settings()
    settings.check_production()
    app = FastAPI(
        title="سامانه جامع بیمارستان بین‌المللی ولیعصر تبریز",
        version="0.1.0",
        docs_url=None if settings.is_exposed else "/docs",
        redoc_url=None,
    )
    if settings.cors_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origins,
            allow_methods=["*"],
            allow_headers=["Authorization", "Content-Type"],
        )

    @app.exception_handler(RequestValidationError)
    async def validation_handler(_: Request, exc: RequestValidationError):
        fields = {".".join(str(p) for p in e["loc"][1:]) or "body": e["msg"] for e in exc.errors()}
        return JSONResponse(
            status_code=422,
            content={
                "detail": {"code": "validation_error", "message": "ورودی نامعتبر است", "fields": fields}
            },
        )

    for r in (
        auth.router,
        admin.router,
        incidents.router,
        announcements.router,
        public.router,
        patient.router,
        dashboard.router,
        training.router,
        requests.router,
        notifications.router,
        chat.router,
        schedules.router,
        translations.router,
    ):
        app.include_router(r, prefix=API_PREFIX)

    @app.get("/health", tags=["health"])
    def health():
        return {"status": "ok"}

    # پنل مدیریت وب: صفحه ایستا که فقط از همین API استفاده می‌کند. مجوزها همچنان در API بررسی می‌شوند.
    @app.middleware("http")
    async def admin_headers(request: Request, call_next):
        response = await call_next(request)
        if request.url.path == "/admin" or request.url.path.startswith("/admin/"):
            response.headers.update(ADMIN_SECURITY_HEADERS)
        return response

    @app.get("/admin", include_in_schema=False)
    def admin_root():
        return RedirectResponse("/admin/")

    app.mount("/admin", StaticFiles(directory=ADMIN_WEB_DIR, html=True), name="admin")

    return app


app = create_app()
