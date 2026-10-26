"""drop machine-translated person names

Revision ID: 7c2b9e4d1a55
Revises: 5e1d2c7a9b40
Create Date: 2026-10-26 18:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '7c2b9e4d1a55'
down_revision: Union[str, None] = '5e1d2c7a9b40'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # مترجم ماشینی نام پزشکان را کلمه به کلمه معنی کرده بود؛ تا ترجمه دستی، نام فارسی نمایش داده می‌شود.
    op.execute(
        sa.text(
            "DELETE FROM content_translations "
            "WHERE status = 'machine' AND entity_type = 'doctor' AND field = 'full_name'"
        )
    )


def downgrade() -> None:
    pass
