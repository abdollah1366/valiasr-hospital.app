package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** کتابخانه آموزش به بیمار (سرور: app/routers/education.py)؛ عمومی و بدون ورود. */

@Serializable
data class EducationDto(
    val id: String,
    val title: String,
    val summary: String? = null,
    val kind: String,
    val condition: String? = null,
    @SerialName("care_type") val careType: String = "other",
    @SerialName("specialty_id") val specialtyId: String? = null,
    @SerialName("specialty_name") val specialtyName: String? = null,
    val source: String? = null,
    @SerialName("reviewed_on") val reviewedOn: String? = null,
    @SerialName("has_file") val hasFile: Boolean = false,
    @SerialName("file_version") val fileVersion: String? = null,
    val body: String? = null,
    @SerialName("external_url") val externalUrl: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("rating_average") val ratingAverage: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
    // فقط برای «آموزش‌های من»
    @SerialName("assigned_at") val assignedAt: String? = null,
    @SerialName("assignment_note") val assignmentNote: String? = null,
    val seen: Boolean = true,
)

@Serializable
data class EducationSpecialtyDto(val id: String, val name: String)

@Serializable
data class EducationFiltersDto(
    val conditions: List<String> = emptyList(),
    @SerialName("care_types") val careTypes: List<String> = emptyList(),
    val kinds: List<String> = emptyList(),
    val specialties: List<EducationSpecialtyDto> = emptyList(),
)

@Serializable
data class MyRatingDto(
    val stars: Int? = null,
    @SerialName("rating_average") val ratingAverage: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
)

@Serializable
data class RatingRequest(val stars: Int)

/** تیکت مراجع با مسئول آموزش به بیمار (سرور: app/routers/education_engage.py). */

@Serializable
data class TicketDto(
    val id: String,
    val subject: String,
    val status: String,
    val unread: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("last_message") val lastMessage: String? = null,
)

@Serializable
data class TicketAttachmentDto(
    @SerialName("original_name") val originalName: String,
    @SerialName("content_type") val contentType: String,
    val size: Long = 0,
    val url: String,
)

@Serializable
data class TicketMessageDto(
    val id: String,
    @SerialName("from_staff") val fromStaff: Boolean,
    @SerialName("staff_name") val staffName: String? = null,
    val body: String? = null,
    val attachment: TicketAttachmentDto? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class TicketDetailDto(
    val id: String,
    val subject: String,
    val status: String,
    val unread: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
    val messages: List<TicketMessageDto> = emptyList(),
)

@Serializable
data class TicketCreateRequest(val subject: String, val body: String)

@Serializable
data class TicketMessageRequest(val body: String)
