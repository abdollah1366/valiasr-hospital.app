package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatPersonDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("personnel_code") val personnelCode: String,
    @SerialName("job_title") val jobTitle: String? = null,
    @SerialName("unit_name") val unitName: String? = null,
)

@Serializable
data class ChatMemberDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("personnel_code") val personnelCode: String,
    @SerialName("job_title") val jobTitle: String? = null,
    @SerialName("unit_name") val unitName: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

@Serializable
data class LastMessageDto(
    @SerialName("sender_name") val senderName: String? = null,
    val text: String? = null,
    @SerialName("has_attachment") val hasAttachment: Boolean = false,
    val deleted: Boolean = false,
    val at: String,
    val mine: Boolean = false,
)

@Serializable
data class ConversationDto(
    val id: String,
    /** direct | group | unit */
    val kind: String,
    val title: String,
    val peer: ChatPersonDto? = null,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("last_message") val lastMessage: LastMessageDto? = null,
    val unread: Int = 0,
    val muted: Boolean = false,
    @SerialName("can_post") val canPost: Boolean = false,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    val members: List<ChatMemberDto> = emptyList(),
    @SerialName("peer_read_at") val peerReadAt: String? = null,
    @SerialName("can_manage") val canManage: Boolean = false,
)

@Serializable
data class ConversationListDto(
    val items: List<ConversationDto>,
    @SerialName("unread_total") val unreadTotal: Int = 0,
)

@Serializable
data class ReplyPreviewDto(
    val id: String,
    @SerialName("sender_name") val senderName: String? = null,
    val text: String? = null,
    @SerialName("has_attachment") val hasAttachment: Boolean = false,
    val deleted: Boolean = false,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    val body: String? = null,
    val attachment: RequestAttachmentDto? = null,
    @SerialName("reply_to") val replyTo: ReplyPreviewDto? = null,
    @SerialName("created_at") val createdAt: String,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    @SerialName("can_edit") val canEdit: Boolean = false,
)

@Serializable
data class ChatMessagePageDto(
    val items: List<ChatMessageDto>,
    @SerialName("next_before") val nextBefore: String? = null,
    val cursor: String,
    @SerialName("peer_read_at") val peerReadAt: String? = null,
)

@Serializable
data class DirectBody(@SerialName("user_id") val userId: String)

@Serializable
data class GroupBody(val title: String, @SerialName("member_ids") val memberIds: List<String>)

@Serializable
data class GroupTitleBody(val title: String)

@Serializable
data class MembersBody(@SerialName("user_ids") val userIds: List<String>)

@Serializable
data class ChatMessageBody(
    @SerialName("client_uuid") val clientUuid: String,
    val body: String,
    @SerialName("reply_to_id") val replyToId: String? = null,
)

@Serializable
data class ChatEditBody(val body: String)

@Serializable
data class MuteBody(val muted: Boolean)

@Serializable
data class ChatUnreadDto(val count: Int)
