"""quality_pr role

Revision ID: 9b3e1f7c2a10
Revises: 6a4c425ac725
Create Date: 2026-10-20 20:00:00.000000

"""
import json
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '9b3e1f7c2a10'
down_revision: Union[str, None] = '6a4c425ac725'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# همان مقادیر core/permissions.py؛ seed هم همین نقش را upsert می‌کند.
ROLE_CODE = 'quality_pr'
ROLE_NAME = 'مسئول ایمنی، اعتبارسنجی و روابط عمومی'
ROLE_PERMISSIONS = ['announcement.read', 'training.read', 'request.create', 'unit.manage', 'translation.manage']


def upgrade() -> None:
    # روی سرورهایی که seed دوباره اجرا نشده، نقش را اضافه می‌کند؛ اگر وجود داشته باشد دست نمی‌زند.
    op.execute(
        sa.text(
            "INSERT INTO roles (id, code, name, permissions) "
            "SELECT gen_random_uuid(), :code, :name, CAST(:perms AS jsonb) "
            "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = :code)"
        ).bindparams(code=ROLE_CODE, name=ROLE_NAME, perms=json.dumps(ROLE_PERMISSIONS, ensure_ascii=False))
    )


def downgrade() -> None:
    op.execute(
        sa.text(
            "DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = :code)"
        ).bindparams(code=ROLE_CODE)
    )
    op.execute(sa.text("DELETE FROM roles WHERE code = :code").bindparams(code=ROLE_CODE))
