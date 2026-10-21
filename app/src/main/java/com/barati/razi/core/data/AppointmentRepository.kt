package com.barati.razi.core.data

import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AppointmentStatusRequest
import com.barati.razi.core.network.BookRequest
import com.barati.razi.core.network.DoctorAppointmentDto
import com.barati.razi.core.network.DoctorDto
import com.barati.razi.core.network.DoctorOwnReviewDto
import com.barati.razi.core.network.DoctorReviewsDto
import com.barati.razi.core.network.MyReviewDto
import com.barati.razi.core.network.ReplyRequest
import com.barati.razi.core.network.ReviewRequest
import com.barati.razi.core.network.PatientApiService
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.SlotDto
import com.barati.razi.core.network.SpecialtyDto
import com.barati.razi.core.network.apiCall

/**
 * پزشکان (عمومی، بدون ورود)، نوبت‌های مراجع (توکن مراجع) و فهرست بیماران پزشک (توکن کارکنان).
 * [onAppointments] پس از هر بار گرفتن یا تغییر نوبت‌ها صدا زده می‌شود تا یادآورهای گوشی به‌روز شوند.
 */
class AppointmentRepository(
    private val api: ApiService,
    private val patientApi: PatientApiService,
    private val onAppointments: (List<PatientAppointmentDto>) -> Unit,
) {
    suspend fun specialties(): Result<List<SpecialtyDto>> = apiCall { api.specialties() }

    suspend fun doctors(specialtyId: String? = null, query: String? = null, featured: Boolean = false): Result<List<DoctorDto>> =
        apiCall { api.doctors(specialtyId, query?.trim()?.takeIf { it.isNotEmpty() }, if (featured) true else null) }

    suspend fun doctor(id: String): Result<DoctorDto> = apiCall { api.doctor(id) }

    suspend fun slots(doctorId: String): Result<List<SlotDto>> = apiCall { api.doctorSlots(doctorId) }

    suspend fun myAppointments(): Result<List<PatientAppointmentDto>> =
        apiCall { patientApi.appointments() }.also { if (it is Result.Ok) onAppointments(it.value) }

    suspend fun book(slotId: String, patientName: String?, note: String?): Result<PatientAppointmentDto> {
        val r = apiCall { patientApi.book(BookRequest(slotId, patientName?.trim(), note?.trim()?.takeIf { it.isNotEmpty() })) }
        if (r is Result.Ok) myAppointments()
        return r
    }

    suspend fun cancel(id: String): Result<PatientAppointmentDto> {
        val r = apiCall { patientApi.cancelAppointment(id) }
        if (r is Result.Ok) myAppointments()
        return r
    }

    /** با خروج مراجع، یادآورهای گوشی هم پاک می‌شوند. */
    fun clearReminders() = onAppointments(emptyList())

    suspend fun doctorDay(isoDate: String): Result<List<DoctorAppointmentDto>> = apiCall { api.myDoctorAppointments(isoDate) }

    suspend fun setDoctorStatus(id: String, status: String): Result<DoctorAppointmentDto> =
        apiCall { api.setDoctorAppointmentStatus(id, AppointmentStatusRequest(status)) }

    suspend fun doctorReviews(doctorId: String): Result<DoctorReviewsDto> = apiCall { api.doctorReviews(doctorId) }

    suspend fun review(appointmentId: String, stars: Int, comment: String?): Result<MyReviewDto> {
        val r = apiCall { patientApi.reviewAppointment(appointmentId, ReviewRequest(stars, comment?.trim()?.takeIf { it.isNotEmpty() })) }
        if (r is Result.Ok) myAppointments()
        return r
    }

    suspend fun myReviews(): Result<List<DoctorOwnReviewDto>> = apiCall { api.myDoctorReviews() }

    suspend fun reply(reviewId: String, text: String): Result<DoctorOwnReviewDto> =
        apiCall { api.replyToReview(reviewId, ReplyRequest(text.trim())) }
}
