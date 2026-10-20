package com.barati.razi

import com.barati.razi.core.chat.ChatLogic
import com.barati.razi.core.chat.ChatRow
import com.barati.razi.core.data.ChatRepository
import com.barati.razi.core.data.FlushOutcome
import com.barati.razi.core.data.Keys
import com.barati.razi.core.data.PendingChatMessage
import com.barati.razi.core.network.ChatMessageDto
import com.barati.razi.core.network.ConversationDto
import com.barati.razi.core.network.HttpFactory
import com.barati.razi.core.network.LastMessageDto
import com.barati.razi.core.notifications.NotificationLogic
import com.barati.razi.core.notifications.NotificationTarget
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun msg(id: String, at: String, sender: String = "me", body: String? = "متن $id", clientUuid: String = "c$id") =
    ChatMessageDto(id = id, clientUuid = clientUuid, senderId = sender, body = body, createdAt = at)

class ChatLogicTest {
    @Test
    fun mergeReplacesEditedAndSortsByTime() {
        val a = msg("a", "2026-10-16T10:00:00Z")
        val b = msg("b", "2026-10-16T10:05:00.5Z")
        val merged = ChatLogic.merge(listOf(b, a), listOf(a.copy(body = "ویرایش", edited = true), msg("c", "2026-10-16T09:00:00+03:30")))
        // ۰۹:۰۰ تهران همان ۰۵:۳۰ UTC است و اول می‌آید
        assertEquals(listOf("c", "a", "b"), merged.map { it.id })
        assertEquals("ویرایش", merged[1].body)
    }

    @Test
    fun timelineAddsDaysSeenAndPendingNotYetArrived() {
        val messages = listOf(
            msg("1", "2026-10-15T08:00:00Z", sender = "peer"),
            msg("2", "2026-10-16T08:00:00Z"),
            msg("3", "2026-10-16T09:00:00Z", clientUuid = "sent-already"),
        )
        val pending = listOf(
            PendingChatMessage("sent-already", "conv", "x", createdAtMillis = 1_760_000_000_000),
            PendingChatMessage("queued", "conv", "در صف", createdAtMillis = 1_760_605_000_000), // 2025-10-16
        )
        val rows = ChatLogic.timeline(messages, pending, "me", peerReadAt = "2026-10-16T08:30:00Z", LocalDate.parse("2026-10-16"), ZoneOffset.UTC)
        val labels = rows.map {
            when (it) {
                is ChatRow.Day -> "day:" + it.label
                is ChatRow.Sent -> it.message.id + (if (it.mine) "/mine" else "") + (if (it.seen) "/seen" else "")
                is ChatRow.Local -> "local:" + it.pending.clientUuid
            }
        }
        assertEquals("day:دیروز", labels[0])
        assertEquals(listOf("1", "day:امروز", "2/mine/seen", "3/mine"), labels.subList(1, 5))
        assertTrue(labels[5].startsWith("day:")) // پیام صف با روز خودش
        assertEquals("local:queued", labels.last())
        assertEquals(7, labels.size)
    }

    @Test
    fun previewsAndTargets() {
        fun conv(kind: String, last: LastMessageDto?) = ConversationDto(id = "c", kind = kind, title = "t", lastMessage = last)
        val at = "2026-10-16T08:00:00Z"
        assertEquals("گفتگو را شروع کنید", ChatLogic.preview(conv("direct", null)))
        assertEquals("سلام", ChatLogic.preview(conv("direct", LastMessageDto("علی", "سلام", at = at))))
        assertEquals("علی: سلام", ChatLogic.preview(conv("group", LastMessageDto("علی", "سلام", at = at))))
        assertEquals("شما: فایل پیوست", ChatLogic.preview(conv("group", LastMessageDto("من", null, hasAttachment = true, at = at, mine = true))))
        assertEquals("علی: این پیام حذف شد", ChatLogic.preview(conv("unit", LastMessageDto("علی", null, deleted = true, at = at))))
        assertEquals(NotificationTarget.Chat("c1"), NotificationLogic.target("chat", "c1"))
        assertEquals("۹۹+", ChatLogic.badge(120))
        assertFalse(ChatLogic.seen(msg("1", at), null))
    }
}

class ChatRepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryStore()
    private lateinit var dir: File
    private lateinit var repo: ChatRepository

    @Before
    fun setUp() {
        server.start()
        dir = Files.createTempDirectory("chat-outbox").toFile()
        val api = HttpFactory.create(server.url("/api/v1/").toString(), MemoryTokens(), { null }, false) {}
        repo = ChatRepository(api, store, dir, clock = { 1000L })
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun sent(clientUuid: String) = MockResponse().setResponseCode(201).setBody(
        """{"id":"m-$clientUuid","client_uuid":"$clientUuid","sender_id":"me","body":"x","created_at":"2026-10-16T08:00:00Z"}""",
    )

    @Test
    fun outboxSendsInOrderKeepsNetworkErrorsAndFailsForbidden() = runBlocking {
        val text = repo.enqueueText("conv", "  سلام  ", replyToId = "m0")
        val file = repo.enqueueFile("conv", "عکس.jpg", "image/jpeg", byteArrayOf(1, 2, 3), null)
        assertTrue(File(dir, file.clientUuid).exists())
        assertEquals(2, repo.pending.first().size)

        // خطای شبکه: هیچ چیز از صف حذف نمی‌شود
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(FlushOutcome.RETRY_LATER, repo.flush())
        assertEquals(listOf(false, false), repo.pending.first().map { it.failed })

        server.enqueue(sent(text.clientUuid))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":{"code":"cannot_post","message":"اجازه نوشتن ندارید"}}"""))
        assertEquals(FlushOutcome.DONE, repo.flush())
        server.takeRequest()
        val textReq = server.takeRequest()
        assertTrue(textReq.body.readUtf8().contains("\"body\":\"سلام\""))
        val fileReq = server.takeRequest()
        assertTrue(fileReq.path!!.contains("/chat/conversations/conv/attachments") && fileReq.path!!.contains("client_uuid=${file.clientUuid}"))
        assertEquals(3L, fileReq.bodySize)

        val left = repo.pending.first()
        assertEquals(listOf(file.clientUuid), left.map { it.clientUuid })
        assertTrue(left[0].failed && left[0].error == "اجازه نوشتن ندارید")
        // پیام ناموفق تا «تلاش دوباره» کاربر فرستاده نمی‌شود
        assertEquals(FlushOutcome.DONE, repo.flush())
        assertEquals(3, server.requestCount)

        repo.discard(file.clientUuid)
        assertTrue(repo.pending.first().isEmpty())
        assertFalse(File(dir, file.clientUuid).exists())
    }

    @Test
    fun clearOnLogoutDropsQueueAndFiles() = runBlocking {
        repo.enqueueFile("conv", "a.pdf", "application/pdf", byteArrayOf(9), null)
        server.enqueue(MockResponse().setBody("""{"count":4}"""))
        repo.refreshUnread()
        assertEquals(4, repo.unread.value)
        repo.clear()
        assertNull(store.get(Keys.CHAT_OUTBOX))
        assertFalse(dir.exists())
        assertEquals(0, repo.unread.value)
    }
}
