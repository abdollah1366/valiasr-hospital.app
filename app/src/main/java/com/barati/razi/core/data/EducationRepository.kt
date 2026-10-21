package com.barati.razi.core.data

import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.EducationDto
import com.barati.razi.core.network.EducationFiltersDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.apiCall

/** محتوای آموزشی منتشرشده؛ متن‌ها با ترجمه تأییدشده زبان مراجع (در نبود آن فارسی) می‌آیند. */
class EducationRepository(private val api: ApiService) {
    suspend fun list(
        language: AppLanguage,
        query: String? = null,
        kind: String? = null,
        careType: String? = null,
        condition: String? = null,
        specialtyId: String? = null,
    ): Result<List<EducationDto>> = apiCall {
        api.education(query?.trim()?.ifEmpty { null }, kind, careType, condition, specialtyId, language.query)
    }

    suspend fun item(id: String, language: AppLanguage): Result<EducationDto> = apiCall { api.educationItem(id, language.query) }

    suspend fun filters(): Result<EducationFiltersDto> = apiCall { api.educationFilters() }
}
