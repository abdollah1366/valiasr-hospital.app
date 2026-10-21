package com.barati.razi.core.data

import com.barati.razi.core.network.PatientApiService
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.ResultDto
import com.barati.razi.core.network.apiCall

/** جواب‌ها هیچ‌جا روی گوشی ذخیره نمی‌شوند؛ هر بار از سرور خوانده می‌شوند. */
class ResultsRepository(private val patientApi: PatientApiService) {
    suspend fun results(kind: String? = null): Result<List<ResultDto>> = apiCall { patientApi.results(kind) }
}
