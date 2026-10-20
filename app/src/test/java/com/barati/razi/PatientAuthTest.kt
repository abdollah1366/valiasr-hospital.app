package com.barati.razi

import com.barati.razi.core.data.Keys
import com.barati.razi.core.data.LanguageRepository
import com.barati.razi.core.data.PatientAuthRepository
import com.barati.razi.core.data.PatientSession
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.HttpFactory
import com.barati.razi.core.network.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** همان سناریوهای tests/test_patient_auth.py در سمت اپ. */
class PatientAuthTest {
    private val server = MockWebServer()
    private val tokens = MemoryTokens().apply { clear() }
    private val store = MemoryStore()
    private lateinit var languages: LanguageRepository
    private lateinit var repo: PatientAuthRepository

    private val patientJson = """{"id":"p1","mobile":"+994501234567","language":"az","full_name":null}"""

    @Before
    fun setUp() {
        server.start()
        val api = HttpFactory.createPatient(server.url("/api/v1/").toString(), tokens, { null }, false) {}
        languages = LanguageRepository(store, CoroutineScope(Dispatchers.Unconfined))
        repo = PatientAuthRepository(api, tokens, store, languages, "test")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun firstLaunchAsksForSignUpUntilSkipped() = runBlocking {
        repo.restore()
        assertEquals(PatientSession.SignedOut(skipped = false), repo.session.value)
        repo.skip()
        repo.restore()
        assertEquals(PatientSession.SignedOut(skipped = true), repo.session.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun verifyStoresTokensAndServerLanguage() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(202).setBody(
                """{"mobile":"+994501234567","expires_in":120,"resend_after":60,"suggested_language":"az","debug_code":"123456"}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"a1","refresh_token":"r1","token_type":"bearer","expires_in":900,"must_change_password":false,"patient":$patientJson,"is_new":true}""",
            ),
        )
        val sent = (repo.requestCode("+994 50 123 45 67") as Result.Ok).value
        assertEquals("az", sent.suggestedLanguage)
        assertEquals("123456", sent.debugCode)

        val r = repo.verify(sent.mobile, " 123456 ", AppLanguage.AZ)
        assertTrue(r is Result.Ok)
        assertEquals("r1", tokens.refreshToken)
        assertEquals(AppLanguage.AZ, AppLanguage.fromCode(store.get(Keys.PATIENT_LANGUAGE)))
        assertTrue(repo.session.value is PatientSession.SignedIn)

        server.takeRequest()
        val verifyBody = server.takeRequest().body.readUtf8()
        assertTrue(verifyBody, verifyBody.contains("\"code\":\"123456\"") && verifyBody.contains("\"language\":\"az\""))
    }

    @Test
    fun wrongCodeKeepsSignedOut() = runBlocking {
        repo.restore()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"detail":{"code":"otp_invalid","message":"کد واردشده اشتباه یا منقضی است"}}"""),
        )
        val r = repo.verify("+989121234567", "000000", AppLanguage.FA)
        assertEquals("otp_invalid", (r as Result.Err).error.code)
        assertNull(tokens.refreshToken)
        assertTrue(repo.session.value is PatientSession.SignedOut)
    }

    @Test
    fun languageChangeIsSavedLocallyAndOnServer() = runBlocking {
        tokens.accessToken = "a1"
        tokens.refreshToken = "r1"
        server.enqueue(MockResponse().setBody(patientJson))
        server.enqueue(MockResponse().setBody(patientJson.replace("\"az\"", "\"tr\"")))
        repo.restore()
        repo.setLanguage(AppLanguage.TR)
        assertEquals(AppLanguage.TR, languages.language.first { it == AppLanguage.TR })
        server.takeRequest()
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("""{"language":"tr"}""", patch.body.readUtf8())
        assertEquals("tr", (repo.session.value as PatientSession.SignedIn).patient.language)
    }
}
