package com.barati.razi.core.data

import com.barati.razi.core.network.ApiError
import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.ChatEditBody
import com.barati.razi.core.network.ChatMessageBody
import com.barati.razi.core.network.ChatMessageDto
import com.barati.razi.core.network.ChatMessagePageDto
import com.barati.razi.core.network.ChatPersonDto
import com.barati.razi.core.network.ConversationDto
import com.barati.razi.core.network.ConversationListDto
import com.barati.razi.core.network.DirectBody
import com.barati.razi.core.network.GroupBody
import com.barati.razi.core.network.GroupTitleBody
import com.barati.razi.core.network.MembersBody
import com.barati.razi.core.network.MuteBody
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.apiCall
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

/** پیامی که هنوز به سرور نرسیده است؛ فایل پیوست تا ارسال در پوشه صف می‌ماند. */
@Serializable
data class PendingChatMessage(
    val clientUuid: String,
    val conversationId: String,
    val body: String? = null,
    val replyToId: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val createdAtMillis: Long,
    val error: String? = null,
    val failed: Boolean = false,
)

/** گفتگوی داخلی. دسترسی فقط در سرور بررسی می‌شود؛ متن پیام جایی جز صف ارسال روی گوشی ذخیره نمی‌شود. */
class ChatRepository(
    private val api: ApiService,
    private val store: KeyValueStore,
    private val outboxDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private val serializer = ListSerializer(PendingChatMessage.serializer())
    private val _unread = MutableStateFlow(0)
    private val _delivered = MutableSharedFlow<ChatMessageDto>(extraBufferCapacity = 64)

    /** پیام‌هایی که از صف به سرور رسیدند؛ صفحه گفتگو آن‌ها را بی‌درنگ جای نسخه در حال ارسال می‌گذارد. */
    val delivered: SharedFlow<ChatMessageDto> = _delivered.asSharedFlow()

    /** جمع پیام‌های خوانده‌نشده گفتگوهای بی‌صدا نشده، برای نشان کاشی «گفتگو». */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    suspend fun conversations(): Result<ConversationListDto> =
        apiCall { api.chatConversations() }.also { if (it is Result.Ok) _unread.value = it.value.unreadTotal }

    suspend fun refreshUnread() {
        (apiCall { api.chatUnread() } as? Result.Ok)?.let { _unread.value = it.value.count }
    }

    suspend fun people(q: String): Result<List<ChatPersonDto>> = apiCall { api.chatPeople(q.trim()) }
    suspend fun openDirect(userId: String): Result<ConversationDto> = apiCall { api.openDirect(DirectBody(userId)) }
    suspend fun createGroup(title: String, memberIds: List<String>): Result<ConversationDto> =
        apiCall { api.createGroup(GroupBody(title.trim(), memberIds)) }
    suspend fun conversation(id: String): Result<ConversationDto> = apiCall { api.conversation(id) }
    suspend fun rename(id: String, title: String): Result<ConversationDto> = apiCall { api.renameGroup(id, GroupTitleBody(title.trim())) }
    suspend fun addMembers(id: String, userIds: List<String>): Result<ConversationDto> =
        apiCall { api.addMembers(id, MembersBody(userIds)) }
    suspend fun removeMember(id: String, userId: String): Result<Unit> = apiCall { api.removeMember(id, userId) }
    suspend fun mute(id: String, muted: Boolean): Result<Unit> = apiCall { api.muteConversation(id, MuteBody(muted)) }

    suspend fun messages(id: String, before: String? = null, since: String? = null): Result<ChatMessagePageDto> =
        apiCall { api.chatMessages(id, before = before, since = since) }

    suspend fun edit(conversationId: String, messageId: String, text: String): Result<ChatMessageDto> =
        apiCall { api.editChatMessage(conversationId, messageId, ChatEditBody(text.trim())) }

    suspend fun delete(conversationId: String, messageId: String): Result<Unit> =
        apiCall { api.deleteChatMessage(conversationId, messageId) }

    suspend fun markRead(id: String) {
        if (apiCall { api.markChatRead(id) } is Result.Ok) refreshUnread()
    }

    // ----- صف ارسال: پیام بدون اینترنت هم ثبت می‌شود و با وصل شدن فرستاده می‌شود.

    val pending: Flow<List<PendingChatMessage>> = store.observe(Keys.CHAT_OUTBOX).map(::decode)

    private fun decode(raw: String?): List<PendingChatMessage> =
        raw?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    private suspend fun load() = decode(store.get(Keys.CHAT_OUTBOX))

    private suspend fun save(list: List<PendingChatMessage>) =
        store.put(Keys.CHAT_OUTBOX, if (list.isEmpty()) null else AppJson.encodeToString(serializer, list))

    private fun fileOf(clientUuid: String) = File(outboxDir, clientUuid)

    suspend fun enqueueText(conversationId: String, text: String, replyToId: String?): PendingChatMessage =
        add(PendingChatMessage(UUID.randomUUID().toString(), conversationId, text.trim(), replyToId, createdAtMillis = clock()))

    suspend fun enqueueFile(
        conversationId: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        replyToId: String?,
    ): PendingChatMessage {
        val item = PendingChatMessage(
            UUID.randomUUID().toString(), conversationId, null, replyToId, fileName, contentType, clock(),
        )
        withContext(Dispatchers.IO) {
            outboxDir.mkdirs()
            fileOf(item.clientUuid).writeBytes(bytes)
        }
        return add(item)
    }

    private suspend fun add(item: PendingChatMessage) = lock.withLock {
        save(load() + item)
        item
    }

    suspend fun retry(clientUuid: String) = lock.withLock {
        save(load().map { if (it.clientUuid == clientUuid) it.copy(failed = false, error = null) else it })
    }

    suspend fun discard(clientUuid: String) = lock.withLock {
        save(load().filterNot { it.clientUuid == clientUuid })
        withContext(Dispatchers.IO) { fileOf(clientUuid).delete() }
    }

    /** ارسال صف به ترتیب. خطای شبکه ارسال را برای بعد نگه می‌دارد؛ خطای دائمی (مثلاً عدم دسترسی) فقط همان پیام را ناموفق می‌کند. */
    suspend fun flush(): FlushOutcome = lock.withLock {
        for (item in load().filterNot { it.failed }) {
            val r = send(item)
            val list = load()
            when (r) {
                is Result.Ok -> {
                    _delivered.tryEmit(r.value)
                    save(list.filterNot { it.clientUuid == item.clientUuid })
                    withContext(Dispatchers.IO) { fileOf(item.clientUuid).delete() }
                }
                is Result.Err -> {
                    val e = r.error
                    val later = (e.isRetryable || e.httpStatus == 401) && e.code != "missing_file"
                    save(list.map { if (it.clientUuid == item.clientUuid) it.copy(error = e.message, failed = !later) else it })
                    if (later) return@withLock FlushOutcome.RETRY_LATER
                }
            }
        }
        FlushOutcome.DONE
    }

    private suspend fun send(item: PendingChatMessage): Result<ChatMessageDto> {
        val name = item.fileName
        if (name == null) {
            return apiCall {
                api.sendChatMessage(item.conversationId, ChatMessageBody(item.clientUuid, item.body.orEmpty(), item.replyToId))
            }
        }
        val file = fileOf(item.clientUuid)
        if (!file.exists()) return Result.Err(ApiError(null, "missing_file", "فایل پیوست دیگر روی گوشی نیست"))
        val type = (item.contentType ?: "application/octet-stream").toMediaType()
        return apiCall { api.sendChatAttachment(item.conversationId, name, item.clientUuid, item.replyToId, file.asRequestBody(type)) }
    }

    /** خروج از حساب: صف و پیوست‌های کاربر قبلی پاک می‌شود تا با حساب بعدی فرستاده نشود. */
    suspend fun clear() = lock.withLock {
        save(emptyList())
        withContext(Dispatchers.IO) { outboxDir.deleteRecursively() }
        _unread.value = 0
    }
}
