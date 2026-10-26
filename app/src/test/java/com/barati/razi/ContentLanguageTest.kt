package com.barati.razi

import com.barati.razi.core.network.HttpFactory
import com.barati.razi.feature.appointments.doctorInitial
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** نام تخصص‌ها و پزشکان به زبان انتخابی مراجع: زبان با سرآیند Accept-Language به سرور می‌رود. */
class ContentLanguageTest {
    private val server = MockWebServer()
    private var language: String? = null

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun api() = HttpFactory.create(server.url("/api/v1/").toString(), MemoryTokens(), { null }, false, { language }) {}

    @Test
    fun sendsSelectedLanguageHeader() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        val api = api()

        api.specialties()
        assertNull(server.takeRequest().getHeader("Accept-Language"))

        language = "az"
        api.specialties()
        assertEquals("az", server.takeRequest().getHeader("Accept-Language"))
    }

    @Test
    fun doctorInitialSkipsTitlePrefix() {
        assertEquals("ن", doctorInitial("دکتر نمونه یکم"))
        assertEquals("S", doctorInitial("Dr. Sample One"))
        assertEquals("ا", doctorInitial("د. النموذج الأول"))
        assertEquals("A", doctorInitial("Ali Rezaei"))
    }
}
