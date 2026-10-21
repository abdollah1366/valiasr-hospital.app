package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** ورود مراجعان با موبایل و کد پیامکی (سرور: app/routers/patient.py). */
interface PatientApiService {
    @POST("patient/auth/otp")
    suspend fun requestOtp(@Body body: OtpRequest): OtpSentDto

    @POST("patient/auth/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): PatientTokenDto

    @POST("patient/auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    @GET("patient/me")
    suspend fun me(): PatientDto

    @PATCH("patient/me")
    suspend fun updateMe(@Body body: PatientUpdateRequest): PatientDto

    @POST("patient/appointments")
    suspend fun book(@Body body: BookRequest): PatientAppointmentDto

    @GET("patient/appointments")
    suspend fun appointments(): List<PatientAppointmentDto>

    @POST("patient/appointments/{id}/review")
    suspend fun reviewAppointment(@Path("id") id: String, @Body body: ReviewRequest): MyReviewDto

    @POST("patient/appointments/{id}/cancel")
    suspend fun cancelAppointment(@Path("id") id: String): PatientAppointmentDto
}

@Serializable
data class OtpRequest(val mobile: String)

@Serializable
data class OtpSentDto(
    val mobile: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("resend_after") val resendAfter: Int,
    @SerialName("suggested_language") val suggestedLanguage: String,
    @SerialName("debug_code") val debugCode: String? = null,
)

@Serializable
data class OtpVerifyRequest(
    val mobile: String,
    val code: String,
    val language: String? = null,
    val device: String? = null,
)

@Serializable
data class PatientDto(
    val id: String,
    val mobile: String,
    val language: String,
    @SerialName("full_name") val fullName: String? = null,
)

@Serializable
data class PatientTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val patient: PatientDto,
    @SerialName("is_new") val isNew: Boolean = false,
)

@Serializable
data class PatientUpdateRequest(
    val language: String? = null,
    @SerialName("full_name") val fullName: String? = null,
)
