"""unit image

Revision ID: 3d7a9c1e5b20
Revises: 9b3e1f7c2a10
Create Date: 2026-10-20 21:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '3d7a9c1e5b20'
down_revision: Union[str, None] = '9b3e1f7c2a10'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('units', sa.Column('image_media_id', sa.Uuid(), nullable=True))
    op.create_foreign_key(
        'fk_units_image_media_id_media_files',
        'units',
        'media_files',
        ['image_media_id'],
        ['id'],
        ondelete='SET NULL',
    )


def downgrade() -> None:
    op.drop_constraint('fk_units_image_media_id_media_files', 'units', type_='foreignkey')
    op.drop_column('units', 'image_media_id')
