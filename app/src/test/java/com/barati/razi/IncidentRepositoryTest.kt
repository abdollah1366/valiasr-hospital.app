package com.barati.razi

import com.barati.razi.core.data.FlushOutcome
import com.barati.razi.core.data.IncidentRepository
import com.barati.razi.core.data.KeyValueStore
import com.barati.razi.core.network.HttpFactory
import com.barati.razi.core.network.IncidentCreateRequest
import com.barati.razi.core.network.TokenStorage
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import com.barati.razi.core.network.Result
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryStore : KeyValueStore {
    private val data = MutableStateFlow<Map<String, String>>(emptyMap())
    override suspend fun get(key: String) = data.value[key]
    override suspend fun put(key: String, value: String?) {
        data.value = if (value == null) data.value - key else data.value + (key to value)
    }
    override fun observe(key: String): Flow<String?> = data.map { it[key] }
}

class MemoryTokens : TokenStorage {
    override var accessToken: String? = "old-access"
    override var refreshToken: String? = "refresh-1"
    override fun clear() {
        accessToken = null
        refreshToken = null
    }
}

class IncidentRepositoryTest {
    private val server = MockWebServer()
    private val tokens = MemoryTokens()
    private var expired = false
    private lateinit var repo: IncidentRepository
    private val store = MemoryStore()

    @Before
    fun setUp() {
        server.start()
        val api = HttpFactory.create(server.url("/api/v1/").toString(), tokens, { null }, false) { expired = true }
        val form = listOf("../backend/app/data/incident_form.json", "backend/app/data/incident_form.json")
            .map(::File).first { it.exists() }.readText()
        repo = IncidentRepository(api, store, { form })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun request(id: String, anonymous: Boolean = false) = IncidentCreateRequest(
        clientUuid = id,
        kind = "error",
        unitId = "u1",
        occurredOn = "2026-10-14",
        shift = "night",
        isAnonymous = anonymous,
        followUpSecret = if (anonymous) "secret-123456" else null,
        answers = JsonObject(emptyMap()),
    )

    @Test
    fun queuedReportStaysWhenServerUnavailableThenSends() = runBlocking {
        repo.enqueue(request("a"))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(FlushOutcome.RETRY_LATER, repo.flush().outcome)
        assertEquals(1, repo.pending.first().size)

        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"tracking_code":"IR-AAAA","status":"submitted"}"""))
        val result = repo.flush()
        assertEquals(FlushOutcome.DONE, result.outcome)
        assertEquals("IR-AAAA", result.sent["a"])
        assertTrue(repo.pending.first().isEmpty())
    }

    @Test
    fun validationErrorMarksFailedWithoutBlockingOthers() = runBlocking {
        repo.enqueue(request("bad"))
        repo.enqueue(request("good", anonymous = true))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail":{"code":"invalid_answers","message":"نامعتبر"}}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"tracking_code":"IR-BBBB","status":"submitted"}"""))
        repo.flush()
        val pending = repo.pending.first()
        assertEquals(listOf("bad"), pending.map { it.request.clientUuid })
        assertTrue(pending.single().failedPermanently)
        assertEquals("نامعتبر", pending.single().lastError)
        // رمز گزارش ناشناس فقط روی گوشی ذخیره می‌شود
        assertEquals("secret-123456", repo.localAnonymous.first().single().secret)
    }

    @Test
    fun enqueueIsIdempotent() = runBlocking {
        repo.enqueue(request("x"))
        repo.enqueue(request("x"))
        assertEquals(1, repo.pending.first().size)
    }

    @Test
    fun expiredAccessTokenIsRefreshedOnce() = runBlocking {
        repo.enqueue(request("r"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"new-access","refresh_token":"refresh-2","expires_in":900,"must_change_password":false}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"tracking_code":"IR-CCCC","status":"submitted"}"""))
        assertEquals("IR-CCCC", repo.flush().sent["r"])
        assertEquals("refresh-2", tokens.refreshToken)
        server.takeRequest()
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun failedRefreshExpiresSessionAndKeepsQueue() = runBlocking {
        repo.enqueue(request("s"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(FlushOutcome.RETRY_LATER, repo.flush().outcome)
        assertTrue(expired)
        assertEquals(null, tokens.refreshToken)
        assertEquals(1, repo.pending.first().size)
    }

    @Test
    fun formFallsBackToBundledCopyOffline() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals("incident", repo.loadForm().code)
    }

    @Test
    fun reviewDetailAndStatusChangeUseServerHints() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"i1","tracking_code":"IR-1","kind":"error","occurred_on":"2026-10-14","status":"submitted",
                "answers":{"description":"x"},"sensitive_answers":null,"can_manage":true,
                "next_statuses":["assigned","rejected","under_review"],"history":[],"actions":[]}""",
            ),
        )
        val detail = (repo.incident("i1") as Result.Ok).value
        assertTrue(detail.canManage)
        assertEquals(listOf("assigned", "rejected", "under_review"), detail.nextStatuses)
        server.takeRequest()

        server.enqueue(
            MockResponse().setBody(
                """{"id":"i1","tracking_code":"IR-1","kind":"error","occurred_on":"2026-10-14","status":"under_review",
                "is_anonymous":false,"created_at":"2026-10-14T10:00:00Z"}""",
            ),
        )
        val changed = repo.changeStatus("i1", "under_review", "  ")
        assertEquals("under_review", (changed as Result.Ok).value.status)
        val req = server.takeRequest()
        assertEquals("/api/v1/incidents/i1/status", req.path)
        // یادداشت خالی اصلاً فرستاده نمی‌شود.
        assertEquals("""{"status":"under_review"}""", req.body.readUtf8())
    }
}
