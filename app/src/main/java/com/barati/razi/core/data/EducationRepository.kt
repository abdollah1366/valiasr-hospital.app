package com.barati.razi.core.data

import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.MyRatingDto
import com.barati.razi.core.network.PatientApiService
import com.barati.razi.core.network.RatingRequest
import com.barati.razi.core.network.TicketCreateRequest
import com.barati.razi.core.network.TicketDetailDto
import com.barati.razi.core.network.TicketDto
import com.barati.razi.core.network.TicketMessageRequest
import okhttp3.RequestBody
import com.barati.razi.core.network.EducationDto
import com.barati.razi.core.network.EducationFiltersDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.apiCall

/** محتوای آموزشی منتشرشده؛ متن‌ها با ترجمه تأییدشده زبان مراجع (در نبود آن فارسی) می‌آیند. */
class EducationRepository(private val api: ApiService, private val patientApi: PatientApiService) {
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

    suspend fun mine(language: AppLanguage): Result<List<EducationDto>> = apiCall { patientApi.myEducation(language.query) }

    /** باز شدن آموزش فرستاده‌شده؛ خطای آن مهم نیست و نمایش آموزش را نباید خراب کند. */
    suspend fun markSeen(id: String) {
        apiCall { patientApi.educationSeen(id) }
    }

    suspend fun myRating(id: String): Result<MyRatingDto> = apiCall { patientApi.educationRating(id) }

    suspend fun rate(id: String, stars: Int): Result<MyRatingDto> = apiCall { patientApi.rateEducation(id, RatingRequest(stars)) }

    suspend fun tickets(): Result<List<TicketDto>> = apiCall { patientApi.educationTickets() }

    suspend fun ticket(id: String): Result<TicketDetailDto> = apiCall { patientApi.educationTicket(id) }

    suspend fun createTicket(subject: String, body: String): Result<TicketDetailDto> =
        apiCall { patientApi.createEducationTicket(TicketCreateRequest(subject.trim(), body.trim())) }

    suspend fun sendMessage(id: String, body: String): Result<TicketDetailDto> =
        apiCall { patientApi.sendEducationTicketMessage(id, TicketMessageRequest(body.trim())) }

    suspend fun sendAttachment(id: String, filename: String, body: RequestBody): Result<TicketDetailDto> =
        apiCall { patientApi.sendEducationTicketAttachment(id, filename, body) }
}
