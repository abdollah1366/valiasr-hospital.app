package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** پزشکان و نوبت‌دهی (سرور: app/routers/appointments.py). زمان‌ها ISO با منطقه زمانی‌اند. */

@Serializable
data class SpecialtyDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val icon: String = "stethoscope",
    @SerialName("doctor_count") val doctorCount: Int = 0,
)

@Serializable
data class DoctorDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val title: String? = null,
    val bio: String? = null,
    @SerialName("specialty_id") val specialtyId: String,
    @SerialName("specialty_name") val specialtyName: String,
    @SerialName("specialty_icon") val specialtyIcon: String = "stethoscope",
    @SerialName("clinic_name") val clinicName: String? = null,
    @SerialName("clinic_location") val clinicLocation: String? = null,
    @SerialName("has_photo") val hasPhoto: Boolean = false,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("booking_enabled") val bookingEnabled: Boolean = true,
    @SerialName("next_free_at") val nextFreeAt: String? = null,
    @SerialName("rating_average") val ratingAverage: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
)

@Serializable
data class SlotDto(
    val id: String,
    @SerialName("starts_at") val startsAt: String,
    val minutes: Int,
    val free: Int = 1,
)

@Serializable
data class BookRequest(
    @SerialName("slot_id") val slotId: String,
    @SerialName("patient_name") val patientName: String?,
    val note: String? = null,
)

@Serializable
data class PatientAppointmentDto(
    val id: String,
    @SerialName("tracking_code") val trackingCode: String,
    val status: String,
    @SerialName("starts_at") val startsAt: String,
    val minutes: Int,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("doctor_name") val doctorName: String,
    @SerialName("doctor_title") val doctorTitle: String? = null,
    @SerialName("specialty_name") val specialtyName: String,
    @SerialName("clinic_name") val clinicName: String? = null,
    @SerialName("clinic_location") val clinicLocation: String? = null,
    val note: String? = null,
    @SerialName("can_cancel") val canCancel: Boolean = false,
    @SerialName("cancelled_by") val cancelledBy: String? = null,
    @SerialName("can_review") val canReview: Boolean = false,
    @SerialName("my_review") val myReview: MyReviewDto? = null,
)

/** نوبت در نمای پزشک (بخش کارکنان). */
@Serializable
data class DoctorAppointmentDto(
    val id: String,
    @SerialName("tracking_code") val trackingCode: String,
    val status: String,
    @SerialName("patient_name") val patientName: String,
    @SerialName("patient_mobile") val patientMobile: String,
    val note: String? = null,
    val source: String = "app",
    @SerialName("starts_at") val startsAt: String,
    val minutes: Int,
)

@Serializable
data class AppointmentStatusRequest(val status: String)
