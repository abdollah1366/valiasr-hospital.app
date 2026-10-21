package com.barati.razi

import android.content.Context
import android.os.Build
import com.barati.razi.core.data.AppointmentRepository
import com.barati.razi.core.data.ChatRepository
import com.barati.razi.core.data.ContentRepository
import com.barati.razi.core.data.DataStoreKeyValueStore
import com.barati.razi.core.data.DefaultAuthRepository
import com.barati.razi.core.data.IncidentRepository
import com.barati.razi.core.data.Keys
import com.barati.razi.core.data.LanguageRepository
import com.barati.razi.core.data.NotificationRepository
import com.barati.razi.core.data.PatientAuthRepository
import com.barati.razi.core.data.ScheduleRepository
import com.barati.razi.core.data.ServiceRequestRepository
import com.barati.razi.core.data.TrainingRepository
import com.barati.razi.core.network.HttpFactory
import com.barati.razi.work.AppointmentReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** وابستگی‌های اپ؛ تزریق دستی برای سادگی (بدون Hilt). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob())

    val store = DataStoreKeyValueStore(appContext)
    private val tokens = com.barati.razi.core.data.EncryptedTokenStorage(appContext)

    /** فقط در debug: آدرس سروری که کاربر در صفحه ورود وارد کرده است. */
    @Volatile
    var serverOrigin: String? = null
        private set

    private lateinit var authRef: DefaultAuthRepository

    val api = HttpFactory.create(
        baseUrl = BuildConfig.API_BASE_URL,
        tokens = tokens,
        originOverride = { if (BuildConfig.ALLOW_SERVER_OVERRIDE) serverOrigin else null },
        debug = BuildConfig.DEBUG,
        onSessionExpired = { authRef.onSessionExpired() },
    )

    val authRepository = DefaultAuthRepository(api, tokens, store, "${Build.MANUFACTURER} ${Build.MODEL}").also { authRef = it }
    val contentRepository = ContentRepository(api)
    val languageRepository = LanguageRepository(store, scope)

    private lateinit var patientAuthRef: PatientAuthRepository
    private val patientTokens = com.barati.razi.core.data.EncryptedTokenStorage(appContext, "valiasr_patient_tokens")
    private val patientApi = HttpFactory.createPatient(
        baseUrl = BuildConfig.API_BASE_URL,
        tokens = patientTokens,
        originOverride = { if (BuildConfig.ALLOW_SERVER_OVERRIDE) serverOrigin else null },
        debug = BuildConfig.DEBUG,
        onSessionExpired = { patientAuthRef.onSessionExpired() },
    )
    val patientAuthRepository = PatientAuthRepository(
        api = patientApi,
        tokens = patientTokens,
        store = store,
        languages = languageRepository,
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
    ).also { patientAuthRef = it }
    val incidentRepository = IncidentRepository(
        api = api,
        store = store,
        bundledForm = { appContext.assets.open("forms/incident_form.json").bufferedReader().use { it.readText() } },
    )

    val appointmentRepository = AppointmentRepository(api, patientApi) { items ->
        AppointmentReminderWorker.sync(appContext, items, languageRepository.language.value)
    }

    /** نشانی کامل عکس پزشک (عمومی، بدون توکن). */
    fun doctorPhotoUrl(doctorId: String): String = apiRoot() + "public/doctors/$doctorId/photo"

    private fun apiRoot(): String {
        val override = if (BuildConfig.ALLOW_SERVER_OVERRIDE) serverOrigin else null
        return if (override != null) "$override/api/v1/" else BuildConfig.API_BASE_URL
    }

    val serviceRequestRepository = ServiceRequestRepository(api, store)
    val notificationRepository = NotificationRepository(api, store)
    val scheduleRepository = ScheduleRepository(api, store)
    val chatRepository = ChatRepository(api, store, java.io.File(appContext.filesDir, "chat-outbox"))

    /** مقصدی که با لمس اعلان سیستمی باید باز شود؛ صفحه‌ها پس از آماده شدن نشست آن را مصرف می‌کنند. */
    val pendingOpen = MutableStateFlow<OpenRequest?>(null)

    /** ریشه سرور برای پیوند فایل‌های آموزشی (پیوندها نسبی‌اند: /api/v1/training/media/...). */
    private val defaultOrigin: String = BuildConfig.API_BASE_URL.toHttpUrl().let { "${it.scheme}://${it.host}:${it.port}" }

    val trainingRepository = TrainingRepository(
        api = api,
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build(),
        cacheDir = appContext.cacheDir,
        origin = { (if (BuildConfig.ALLOW_SERVER_OVERRIDE) serverOrigin else null) ?: defaultOrigin },
        scope = scope,
    )

    suspend fun loadServerOrigin() {
        serverOrigin = store.get(Keys.SERVER_ORIGIN)
    }

    fun setServerOrigin(origin: String?) {
        val cleaned = origin?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        serverOrigin = cleaned
        scope.launch { store.put(Keys.SERVER_ORIGIN, cleaned) }
    }
}

/** درخواست باز کردن یک اعلان از بیرون اپ (اعلان سیستمی). linkType «notifications» یعنی فهرست اعلان‌ها. */
data class OpenRequest(val notificationId: String?, val linkType: String?, val linkId: String?)
