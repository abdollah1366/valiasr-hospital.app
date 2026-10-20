package com.barati.razi

import com.barati.razi.core.data.FlushOutcome
import com.barati.razi.core.data.ServiceRequestRepository
import com.barati.razi.core.network.HttpFactory
import com.barati.razi.core.network.RequestCreateRequest
import com.barati.razi.core.network.RequestEventDto
import com.barati.razi.core.network.RequestFieldDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.requests.RequestLogic
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** همان سناریوهای تست سرور (tests/test_requests.py) برای منطق سمت اپ. */
class ServiceRequestLogicTest {
    private val fields = listOf(
        RequestFieldDto("device", "نوع دستگاه", "select", listOf("رایانه", "پرینتر"), required = true),
        RequestFieldDto("asset_no", "شماره اموال", "text"),
        RequestFieldDto("ext", "شماره داخلی", "number"),
    )

    @Test
    fun validateMatchesServerRules() {
        val (_, missing) = RequestLogic.validate(fields, emptyMap())
        assertEquals(mapOf("device" to "این فیلد لازم است"), missing)

        val (_, bad) = RequestLogic.validate(fields, mapOf("device" to "تلویزیون", "ext" to "abc"))
        assertEquals(setOf("device", "ext"), bad.keys)

        val (clean, ok) = RequestLogic.validate(fields, mapOf("device" to "پرینتر", "asset_no" to "  ۴۵۲  ", "ext" to "۲۱۴", "x" to "1"))
        assertTrue(ok.isEmpty())
        assertEquals(JsonPrimitive("پرینتر"), clean["device"])
        assertEquals(JsonPrimitive("۴۵۲"), clean["asset_no"])
        assertEquals(JsonPrimitive(214.0), clean["ext"])
        assertNull(clean["x"]) // فیلد ناشناخته فرستاده نمی‌شود
    }

    @Test
    fun displayAndTransitions() {
        assertEquals("۲۱۴", RequestLogic.displayValue(JsonPrimitive(214.0)))
        assertEquals("۲.۵", RequestLogic.displayValue(JsonPrimitive(2.5)))
        assertEquals("پرینتر", RequestLogic.displayValue(JsonPrimitive("پرینتر")))
        // ترتیب دکمه‌ها ثابت است، نه ترتیب سرور
        assertEquals(
            listOf("in_progress", "on_hold", "done", "rejected"),
            RequestLogic.transitions(listOf("status:rejected", "status:done", "assign", "status:on_hold", "status:in_progress")),
        )
        assertTrue(RequestLogic.transitions(listOf("comment", "attach")).isEmpty())
    }

    @Test
    fun agoAndEventTitles() {
        val now = OffsetDateTime.parse("2026-10-15T12:00:00Z")
        assertEquals("همین الان", RequestLogic.ago("2026-10-15T11:59:40Z", now))
        assertEquals("۵ دقیقه پیش", RequestLogic.ago("2026-10-15T11:55:00Z", now))
        assertEquals("۳ ساعت پیش", RequestLogic.ago("2026-10-15T09:00:00Z", now))
        assertEquals("۲ روز پیش", RequestLogic.ago("2026-10-13T11:00:00Z", now))
        val auto = RequestEventDto("e", "2026-10-15T12:00:00Z", "status", null, "done", "closed")
        assertEquals("بسته شدن خودکار", RequestLogic.eventTitle(auto))
        assertEquals("وضعیت: انجام شد", RequestLogic.eventTitle(auto.copy(toStatus = "done")))
    }
}

class ServiceRequestRepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryStore()
    private lateinit var repo: ServiceRequestRepository

    @Before
    fun setUp() {
        server.start()
        val api = HttpFactory.create(server.url("/api/v1/").toString(), MemoryTokens(), { null }, false) {}
        repo = ServiceRequestRepository(api, store)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun request(id: String) = RequestCreateRequest(
        clientUuid = id,
        typeId = "t1",
        description = "پرینتر کار نمی‌کند",
        answers = mapOf("device" to JsonPrimitive("پرینتر")),
    )

    @Test
    fun offlineRequestIsKeptThenSent() = runBlocking {
        repo.enqueue(request("a"), "خرابی پرینتر")
        repo.enqueue(request("a"), "خرابی پرینتر") // تکراری افزوده نمی‌شود
        assertEquals(1, repo.pending.first().size)

        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(FlushOutcome.RETRY_LATER, repo.flush().outcome)
        assertEquals("خرابی پرینتر", repo.pending.first().single().typeName)

        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"r1","tracking_code":"R-AAAA","status":"submitted"}"""))
        val result = repo.flush()
        assertEquals(FlushOutcome.DONE, result.outcome)
        assertEquals(mapOf("a" to "r1"), result.sent)
        assertTrue(repo.pending.first().isEmpty())

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"client_uuid\":\"a\"") && body.contains("\"type_id\":\"t1\""))
    }

    @Test
    fun invalidRequestFailsPermanentlyWithoutBlockingOthers() = runBlocking {
        repo.enqueue(request("bad"), "نوع غیرفعال")
        repo.enqueue(request("good"), "خرابی پرینتر")
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"detail":{"code":"unknown_type","message":"نوع درخواست نامعتبر است"}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"r2","tracking_code":"R-BBBB","status":"submitted"}"""))
        val result = repo.flush()
        assertEquals(FlushOutcome.DONE, result.outcome)
        assertEquals(mapOf("good" to "r2"), result.sent)
        val left = repo.pending.first().single()
        assertTrue(left.failedPermanently)
        assertEquals("نوع درخواست نامعتبر است", left.lastError)
    }

    @Test
    fun transferWithoutAccessReturnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val r = repo.transfer("r1", "u2", "مشکل برق است")
        assertTrue(r is Result.Ok && r.value == null)
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":{"code":"not_open","message":"این درخواست باز نیست"}}"""))
        val err = repo.transfer("r1", "u2", "x")
        assertTrue(err is Result.Err && err.error.message == "این درخواست باز نیست")
    }
}
