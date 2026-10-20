package com.barati.razi

import com.barati.razi.core.data.Keys
import com.barati.razi.core.data.NotificationRepository
import com.barati.razi.core.network.HttpFactory
import com.barati.razi.core.network.NotificationDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.notifications.NotificationLogic
import com.barati.razi.core.notifications.NotificationTarget
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun note(id: String, at: String, read: Boolean = false, linkType: String? = "request") =
    NotificationDto(id = id, kind = "request.status", title = "درخواست $id", linkType = linkType, linkId = "x$id", createdAt = at, read = read)

class NotificationLogicTest {
    @Test
    fun pickNewSkipsShownAndReadAndKeepsOrder() {
        val unread = listOf(note("c", "2026-10-15T12:00:00Z"), note("b", "2026-10-15T11:00:00Z"), note("r", "2026-10-15T10:00:00Z", read = true))
        val (fresh, remembered) = NotificationLogic.pickNew(unread, listOf("a"))
        assertEquals(listOf("b", "c"), fresh.map { it.id }) // قدیمی‌ترین اول
        assertEquals(listOf("a", "b", "c"), remembered)
        val (again, _) = NotificationLogic.pickNew(unread, remembered)
        assertTrue(again.isEmpty())

        val many = (1..250).map { "n$it" }
        val (_, capped) = NotificationLogic.pickNew(listOf(note("new", "2026-10-15T12:00:00Z")), many)
        assertEquals(NotificationLogic.SHOWN_CAP, capped.size)
        assertEquals("new", capped.last())
    }

    @Test
    fun targetsAndGroups() {
        assertEquals(NotificationTarget.Request("1"), NotificationLogic.target("request", "1"))
        assertEquals(NotificationTarget.Incident("2"), NotificationLogic.target("incident", "2"))
        assertEquals(NotificationTarget.Course("3"), NotificationLogic.target("course", "3"))
        assertEquals(NotificationTarget.Training, NotificationLogic.target("exam", "4"))
        assertEquals(NotificationTarget.Announcements, NotificationLogic.target("announcement", "5"))
        assertNull(NotificationLogic.target("request", null))
        assertNull(NotificationLogic.target("unknown", "6"))

        val items = listOf(
            note("1", "2026-10-15T09:00:00Z"),
            note("2", "2026-10-14T23:30:00Z"),
            note("3", "2026-10-10T08:00:00Z"),
        )
        val groups = NotificationLogic.group(items, LocalDate.parse("2026-10-15"), ZoneOffset.UTC)
        assertEquals(listOf("امروز", "دیروز", "قبل‌تر"), groups.map { it.first })
        // ساعت ۲۳:۳۰ UTC در تهران (+۳:۳۰) فردای آن است
        val tehran = NotificationLogic.group(items, LocalDate.parse("2026-10-15"), ZoneOffset.ofHoursMinutes(3, 30))
        assertEquals(listOf("1", "2"), tehran.first().second.map { it.id })
    }
}

class NotificationRepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryStore()
    private lateinit var repo: NotificationRepository

    @Before
    fun setUp() {
        server.start()
        val api = HttpFactory.create(server.url("/api/v1/").toString(), MemoryTokens(), { null }, false) {}
        repo = NotificationRepository(api, store)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun page(vararg ids: String, unread: Int = ids.size) = MockResponse().setBody(
        """{"items":[${ids.joinToString(",") { """{"id":"$it","kind":"request.new","title":"t$it","link_type":"request","link_id":"r$it","created_at":"2026-10-15T10:00:0${ids.indexOf(it)}Z","read":false}""" }}],"unread":$unread,"next_before":null}""",
    )

    @Test
    fun firstFetchAfterLoginShowsNothingThenOnlyNewOnes() = runBlocking {
        server.enqueue(page("a", "b"))
        assertEquals(Result.Ok(emptyList<NotificationDto>()), repo.takeNewForAlert())
        assertEquals(2, repo.unread.value)
        assertTrue(server.takeRequest().path!!.contains("unread_only=true"))

        server.enqueue(page("a", "b", "c"))
        val r = repo.takeNewForAlert()
        assertEquals(listOf("c"), (r as Result.Ok).value.map { it.id })

        server.enqueue(page("a", "b", "c"))
        assertEquals(Result.Ok(emptyList<NotificationDto>()), repo.takeNewForAlert())

        // خروج: سابقه پاک می‌شود و کاربر بعدی اعلان‌های قدیمی خودش را یکجا نمی‌گیرد
        repo.clear()
        assertNull(store.get(Keys.SHOWN_NOTIFICATIONS))
        assertEquals(0, repo.unread.value)
        server.enqueue(page("x", "y"))
        assertEquals(Result.Ok(emptyList<NotificationDto>()), repo.takeNewForAlert())
    }

    @Test
    fun markReadUpdatesCounterAndErrorsDoNotTouchHistory() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"count":3,"important":1}"""))
        repo.refreshUnread()
        assertEquals(3, repo.unread.value)
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.markRead("n1") is Result.Ok)
        assertEquals(2, repo.unread.value)
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.markAllRead() is Result.Ok)
        assertEquals(0, repo.unread.value)

        server.enqueue(MockResponse().setResponseCode(503))
        val err = repo.takeNewForAlert()
        assertTrue(err is Result.Err && err.error.isRetryable)
        assertNull(store.get(Keys.SHOWN_NOTIFICATIONS))
    }
}
