package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val kind: String,
    val title: String,
    val body: String? = null,
    @SerialName("link_type") val linkType: String? = null,
    @SerialName("link_id") val linkId: String? = null,
    val important: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    val read: Boolean = false,
)

@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto>,
    val unread: Int,
    @SerialName("next_before") val nextBefore: String? = null,
)

@Serializable
data class UnreadCountDto(val count: Int, val important: Int = 0)
