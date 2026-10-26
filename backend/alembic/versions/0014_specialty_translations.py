"""specialty name translations

Revision ID: 5e1d2c7a9b40
Revises: 3bb2818aad03
Create Date: 2026-10-26 17:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

from app.seed_translations import SPECIALTY_TRANSLATIONS, seed_specialty_translations


# revision identifiers, used by Alembic.
revision: str = '5e1d2c7a9b40'
down_revision: Union[str, None] = '3bb2818aad03'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # روی سرورهایی که گروه‌های تخصصی رسمی از قبل ثبت شده‌اند، ترجمه نام‌ها را می‌افزاید.
    seed_specialty_translations(op.get_bind())


def downgrade() -> None:
    op.execute(
        sa.text(
            "DELETE FROM content_translations WHERE entity_type = 'specialty' AND field = 'name' "
            "AND entity_id IN (SELECT id FROM specialties WHERE name = ANY(:names)) AND updated_by IS NULL"
        ).bindparams(names=list(SPECIALTY_TRANSLATIONS))
    )
