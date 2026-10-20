package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LoginRequest(
    @SerialName("personnel_code") val personnelCode: String,
    val password: String,
    val device: String? = null,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("must_change_password") val mustChangePassword: Boolean,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class MeDto(
    val id: String,
    @SerialName("personnel_code") val personnelCode: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("unit_name") val unitName: String? = null,
    @SerialName("job_title") val jobTitle: String? = null,
    val roles: List<String>,
    val permissions: List<String>,
    @SerialName("must_change_password") val mustChangePassword: Boolean,
)

@Serializable
data class UnitDto(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("is_public") val isPublic: Boolean = false,
    val location: String? = null,
    val phone: String? = null,
    val hours: String? = null,
    val description: String? = null,
)

@Serializable
data class FaqDto(
    val id: String,
    val question: String,
    val answer: String,
    val category: String? = null,
)

@Serializable
data class AnnouncementDto(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val audience: String,
    @SerialName("is_important") val isImportant: Boolean = false,
    @SerialName("requires_ack") val requiresAck: Boolean = false,
    @SerialName("published_at") val publishedAt: String,
    val read: Boolean? = null,
    val acknowledged: Boolean? = null,
)

@Serializable
data class FeedbackRequest(
    val kind: String,
    val body: String,
    val name: String? = null,
    val mobile: String? = null,
    @SerialName("unit_id") val unitId: String? = null,
)

@Serializable
data class FeedbackCreated(@SerialName("tracking_code") val trackingCode: String)

@Serializable
data class FeedbackStatusDto(
    @SerialName("tracking_code") val trackingCode: String,
    val kind: String,
    val status: String,
    val response: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class IncidentCreateRequest(
    @SerialName("client_uuid") val clientUuid: String,
    val kind: String,
    @SerialName("unit_id") val unitId: String,
    @SerialName("occurred_on") val occurredOn: String,
    val shift: String,
    @SerialName("is_anonymous") val isAnonymous: Boolean,
    @SerialName("follow_up_secret") val followUpSecret: String? = null,
    @SerialName("form_version") val formVersion: Int? = null,
    val answers: JsonObject,
)

@Serializable
data class IncidentCreated(
    val id: String? = null,
    @SerialName("tracking_code") val trackingCode: String,
    val status: String,
    val duplicate: Boolean = false,
)

@Serializable
data class IncidentSummaryDto(
    val id: String,
    @SerialName("tracking_code") val trackingCode: String,
    val kind: String,
    @SerialName("unit_name") val unitName: String? = null,
    @SerialName("occurred_on") val occurredOn: String,
    val status: String,
    @SerialName("is_anonymous") val isAnonymous: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class StatusChangeDto(
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String,
    val note: String? = null,
    val at: String,
)

@Serializable
data class ActionDto(
    val id: String,
    val description: String,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("done_at") val doneAt: String? = null,
    val result: String? = null,
)

@Serializable
data class IncidentDetailDto(
    val id: String,
    @SerialName("tracking_code") val trackingCode: String,
    val kind: String,
    @SerialName("unit_name") val unitName: String? = null,
    @SerialName("occurred_on") val occurredOn: String,
    val status: String,
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val shift: String? = null,
    val answers: JsonObject = JsonObject(emptyMap()),
    // فقط برای کاربران دارای مجوز؛ برای بقیه null است.
    @SerialName("sensitive_answers") val sensitiveAnswers: JsonObject? = null,
    @SerialName("reporter_name") val reporterName: String? = null,
    @SerialName("assigned_unit_name") val assignedUnitName: String? = null,
    val history: List<StatusChangeDto> = emptyList(),
    val actions: List<ActionDto> = emptyList(),
    @SerialName("can_manage") val canManage: Boolean = false,
    @SerialName("next_statuses") val nextStatuses: List<String> = emptyList(),
)

@Serializable
data class IncidentPageDto(
    val items: List<IncidentSummaryDto>,
    val total: Int,
    val page: Int,
    val size: Int,
)

@Serializable
data class StatusChangeRequest(val status: String, val note: String? = null)

@Serializable
data class AssignRequest(@SerialName("unit_id") val unitId: String, val note: String? = null)

@Serializable
data class ActionRequest(
    val description: String,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
)

@Serializable
data class ActionUpdateRequest(val done: Boolean)

@Serializable
data class TrackRequest(
    @SerialName("tracking_code") val trackingCode: String,
    @SerialName("follow_up_secret") val followUpSecret: String,
)

@Serializable
data class TrackResponse(
    @SerialName("tracking_code") val trackingCode: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    val history: List<StatusChangeDto> = emptyList(),
)

object Labels {
    val incidentKinds = linkedMapOf(
        "error" to "خطا",
        "adverse_event" to "حادثه (عارضه ناخواسته)",
        "near_miss" to "شبه‌خطا (پیش از رسیدن به بیمار)",
        "equipment" to "مشکل تجهیزات",
    )
    val shifts = linkedMapOf("morning" to "صبح", "evening" to "عصر", "night" to "شب", "unknown" to "نامشخص")
    val incidentStatus = mapOf(
        "submitted" to "ثبت‌شده",
        "under_review" to "در حال بررسی",
        "assigned" to "ارجاع‌شده",
        "in_progress" to "در حال اقدام",
        "action_taken" to "اقدام اصلاحی انجام شد",
        "closed" to "بسته‌شده",
        "rejected" to "رد شده",
    )
    val feedbackKinds = linkedMapOf(
        "suggestion" to "پیشنهاد",
        "criticism" to "انتقاد",
        "complaint" to "شکایت",
        "praise" to "قدردانی",
    )
    val feedbackStatus = mapOf("received" to "دریافت شد", "reviewing" to "در حال بررسی", "answered" to "پاسخ داده شد")
    val announcementCategories = mapOf(
        "news" to "خبر",
        "circular" to "بخشنامه",
        "guideline" to "دستورالعمل",
        "notice" to "اطلاعیه",
    )
}
