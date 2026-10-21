"""جواب آزمایش و تصویربرداری مراجع؛ فقط خواندنی و از منبع [app/services/his.py]."""

from typing import Literal

from fastapi import APIRouter, Depends, Query

from app.core.deps import api_error
from app.models import Patient
from app.routers.patient import get_current_patient
from app.schemas.results import ResultOut
from app.services import his

router = APIRouter(prefix="/patient/results", tags=["patient"])


@router.get("", response_model=list[ResultOut])
def list_results(
    kind: Literal["lab", "imaging"] | None = Query(None),
    patient: Patient = Depends(get_current_patient),
):
    return his.patient_results(patient.mobile, kind)


@router.get("/{result_id}", response_model=ResultOut)
def get_result(result_id: str, patient: Patient = Depends(get_current_patient)):
    for r in his.patient_results(patient.mobile):
        if r.id == result_id:
            return r
    raise api_error(404, "not_found", "جواب پیدا نشد")
