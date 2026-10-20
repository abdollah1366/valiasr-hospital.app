package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RequestFieldDto(
    val id: String,
    val label: String,
    val kind: String = "text",
    val options: List<String> = emptyList(),
    val required: Boolean = false,
)

@Serializable
data class RequestTypeDto(
    val id: String,
    val name: String,
    val category: String,
    val description: String? = null,
    @SerialName("handler_unit_id") val handlerUnitId: String,
    @SerialName("handler_unit_name") val handlerUnitName: String? = null,
    val fields: List<RequestFieldDto> = emptyList(),
)

@Serializable
data class RequestCreateRequest(
    /** شناسه‌ای که اپ می‌سازد تا ارسال دوباره از صف آفلاین درخواست تکراری نسازد. */
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("type_id") val typeId: String,
    val description: String,
    val location: String? = null,
    val priority: String = "normal",
    val answers: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class RequestCreatedDto(
    val id: String,
    @SerialName("tracking_code") val trackingCode: String,
    val status: String,
    val duplicate: Boolean = false,
)

@Serializable
data class PersonDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("personnel_code") val personnelCode: String,
    @SerialName("job_title") val jobTitle: String? = null,
)

@Serializable
data class RequestSummaryDto(
    val id: String,
    @SerialName("tracking_code") val trackingCode: String,
    @SerialName("type_name") val typeName: String,
    val category: String,
    val description: String,
    val priority: String,
    val status: String,
    val requester: PersonDto,
    @SerialName("requester_unit_name") val requesterUnitName: String? = null,
    @SerialName("handler_unit_id") val handlerUnitId: String,
    @SerialName("handler_unit_name") val handlerUnitName: String? = null,
    @SerialName("assigned_to") val assignedTo: PersonDto? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val satisfaction: Int? = null,
)

@Serializable
data class RequestPageDto(val items: List<RequestSummaryDto>, val total: Int)

@Serializable
data class RequestAttachmentDto(
    val id: String,
    @SerialName("original_name") val originalName: String,
    @SerialName("content_type") val contentType: String,
    val size: Long,
    /** پیوند امضاشده و موقت، نسبت به ریشه سرور. */
    val url: String,
)

@Serializable
data class RequestEventDto(
    val id: String,
    val at: String,
    val kind: String,
    val actor: PersonDto? = null,
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String? = null,
    val note: String? = null,
    val attachment: RequestAttachmentDto? = null,
)

@Serializable
data class LabeledValueDto(val label: String, val value: JsonElement)

@Serializable
data class RequestDetailDto(
    val id: String,
    @SerialName("tracking_code") val trackingCode: String,
    @SerialName("type_name") val typeName: String,
    val category: String,
    val description: String,
    val priority: String,
    val status: String,
    val requester: PersonDto,
    @SerialName("requester_unit_name") val requesterUnitName: String? = null,
    @SerialName("handler_unit_id") val handlerUnitId: String,
    @SerialName("handler_unit_name") val handlerUnitName: String? = null,
    @SerialName("assigned_to") val assignedTo: PersonDto? = null,
    @SerialName("created_at") val createdAt: String,
    val satisfaction: Int? = null,
    val location: String? = null,
    val answers: List<LabeledValueDto> = emptyList(),
    val resolution: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("satisfaction_comment") val satisfactionComment: String? = null,
    @SerialName("reopen_until") val reopenUntil: String? = null,
    val events: List<RequestEventDto> = emptyList(),
    /** کارهایی که کاربر الان مجاز است: status:accepted، assign، transfer، rate، reopen، comment، attach */
    val actions: List<String> = emptyList(),
)

@Serializable
data class RequestStatusBody(val status: String, val note: String? = null, val resolution: String? = null)

@Serializable
data class RequestAssignBody(@SerialName("user_id") val userId: String?)

@Serializable
data class RequestTransferBody(@SerialName("unit_id") val unitId: String, val note: String)

@Serializable
data class RequestCommentBody(val text: String)

@Serializable
data class RequestRateBody(val score: Int, val comment: String? = null)

@Serializable
data class RequestReopenBody(val note: String)
