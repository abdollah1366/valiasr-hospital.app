package com.barati.razi.core.data

import com.barati.razi.core.form.FormSchema
import com.barati.razi.core.network.ActionDto
import com.barati.razi.core.network.ActionRequest
import com.barati.razi.core.network.ActionUpdateRequest
import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AssignRequest
import com.barati.razi.core.network.IncidentCreateRequest
import com.barati.razi.core.network.IncidentDetailDto
import com.barati.razi.core.network.IncidentPageDto
import com.barati.razi.core.network.IncidentSummaryDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.StatusChangeRequest
import com.barati.razi.core.network.TrackRequest
import com.barati.razi.core.network.TrackResponse
import com.barati.razi.core.network.UnitDto
import com.barati.razi.core.network.apiCall
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** گزارشی که هنوز به سرور نرسیده است (صف آفلاین). */
@Serializable
data class PendingIncident(
    val request: IncidentCreateRequest,
    val createdAtMillis: Long,
    val lastError: String? = null,
    val failedPermanently: Boolean = false,
)

/** کد و رمز پیگیری گزارش‌های ناشناس که از همین گوشی فرستاده شده‌اند؛ فقط روی گوشی نگه داشته می‌شود. */
@Serializable
data class LocalAnonymousReport(
    val trackingCode: String,
    val secret: String,
    val kind: String,
    val createdAtMillis: Long,
)

enum class FlushOutcome { DONE, RETRY_LATER }

/** [sent]: نگاشت client_uuid به کد پیگیری گزارش‌هایی که در همین نوبت ارسال شدند. */
data class FlushResult(val outcome: FlushOutcome, val sent: Map<String, String>)

class IncidentRepository(
    private val api: ApiService,
    private val store: KeyValueStore,
    private val bundledForm: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val queueLock = Mutex()
    private val pendingSerializer = ListSerializer(PendingIncident.serializer())
    private val localSerializer = ListSerializer(LocalAnonymousReport.serializer())

    /** قالب فرم: از سرور، در غیر این صورت آخرین نسخه ذخیره‌شده، در غیر این صورت نسخه داخل اپ. */
    suspend fun loadForm(): FormSchema {
        val remote = apiCall { api.form("incident") }
        if (remote is Result.Ok) {
            val text = remote.value.toString()
            runCatching { AppJson.decodeFromString(FormSchema.serializer(), text) }.getOrNull()?.let {
                store.put(Keys.INCIDENT_FORM, text)
                return it
            }
        }
        store.get(Keys.INCIDENT_FORM)?.let { cached ->
            runCatching { AppJson.decodeFromString(FormSchema.serializer(), cached) }.getOrNull()?.let { return it }
        }
        return AppJson.decodeFromString(FormSchema.serializer(), bundledForm())
    }

    suspend fun units(): Result<List<UnitDto>> = apiCall { api.allUnits() }

    suspend fun myIncidents(): Result<List<IncidentSummaryDto>> = apiCall { api.myIncidents() }

    suspend fun incident(id: String): Result<IncidentDetailDto> = apiCall { api.incident(id) }

    // ----- بررسی گزارش‌ها (کارشناس ایمنی / مسئول واحد). مجوزها فقط در سرور بررسی می‌شوند.

    suspend fun reviewList(status: String?): Result<IncidentPageDto> = apiCall { api.incidents(status = status) }

    suspend fun changeStatus(id: String, status: String, note: String?): Result<IncidentSummaryDto> =
        apiCall { api.changeIncidentStatus(id, StatusChangeRequest(status, note?.trim()?.ifEmpty { null })) }

    suspend fun assign(id: String, unitId: String, note: String?): Result<IncidentSummaryDto> =
        apiCall { api.assignIncident(id, AssignRequest(unitId, note?.trim()?.ifEmpty { null })) }

    suspend fun addAction(id: String, description: String, owner: String?, dueDate: String?): Result<ActionDto> =
        apiCall { api.addIncidentAction(id, ActionRequest(description.trim(), owner?.trim()?.ifEmpty { null }, dueDate)) }

    suspend fun setActionDone(id: String, actionId: String, done: Boolean): Result<ActionDto> =
        apiCall { api.updateIncidentAction(id, actionId, ActionUpdateRequest(done)) }

    suspend fun track(code: String, secret: String): Result<TrackResponse> =
        apiCall { api.trackIncident(TrackRequest(code.trim().uppercase(), secret.trim())) }

    val pending: Flow<List<PendingIncident>> = store.observe(Keys.PENDING_INCIDENTS).map(::decodePending)

    val localAnonymous: Flow<List<LocalAnonymousReport>> = store.observe(Keys.LOCAL_ANONYMOUS).map { raw ->
        raw?.let { runCatching { AppJson.decodeFromString(localSerializer, it) }.getOrNull() } ?: emptyList()
    }

    private fun decodePending(raw: String?): List<PendingIncident> =
        raw?.let { runCatching { AppJson.decodeFromString(pendingSerializer, it) }.getOrNull() } ?: emptyList()

    private suspend fun savePending(list: List<PendingIncident>) =
        store.put(Keys.PENDING_INCIDENTS, AppJson.encodeToString(pendingSerializer, list))

    suspend fun enqueue(request: IncidentCreateRequest) = queueLock.withLock {
        val list = decodePending(store.get(Keys.PENDING_INCIDENTS))
        if (list.none { it.request.clientUuid == request.clientUuid }) {
            savePending(list + PendingIncident(request, clock()))
        }
    }

    suspend fun discard(clientUuid: String) = queueLock.withLock {
        savePending(decodePending(store.get(Keys.PENDING_INCIDENTS)).filterNot { it.request.clientUuid == clientUuid })
    }

    suspend fun retryFailed() = queueLock.withLock {
        savePending(decodePending(store.get(Keys.PENDING_INCIDENTS)).map { it.copy(failedPermanently = false) })
    }

    /** ارسال صف. ارسال تکراری بی‌خطر است چون سرور با client_uuid تکرار را تشخیص می‌دهد. */
    suspend fun flush(): FlushResult = queueLock.withLock {
        var list = decodePending(store.get(Keys.PENDING_INCIDENTS))
        val sent = mutableMapOf<String, String>()
        for (item in list.filterNot { it.failedPermanently }) {
            when (val r = apiCall { api.createIncident(item.request) }) {
                is Result.Ok -> {
                    if (item.request.isAnonymous && item.request.followUpSecret != null) {
                        rememberAnonymous(
                            LocalAnonymousReport(r.value.trackingCode, item.request.followUpSecret, item.request.kind, clock()),
                        )
                    }
                    sent[item.request.clientUuid] = r.value.trackingCode
                    list = list.filterNot { it.request.clientUuid == item.request.clientUuid }
                    savePending(list)
                }
                is Result.Err -> {
                    val e = r.error
                    // شبکه، خطای سرور یا نشست منقضی: بعداً دوباره تلاش می‌شود.
                    if (e.isRetryable || e.httpStatus == 401 || e.httpStatus == 403) {
                        list = list.map {
                            if (it.request.clientUuid == item.request.clientUuid) it.copy(lastError = e.message) else it
                        }
                        savePending(list)
                        return@withLock FlushResult(FlushOutcome.RETRY_LATER, sent)
                    }
                    list = list.map {
                        if (it.request.clientUuid == item.request.clientUuid) {
                            it.copy(lastError = e.message, failedPermanently = true)
                        } else {
                            it
                        }
                    }
                    savePending(list)
                }
            }
        }
        FlushResult(FlushOutcome.DONE, sent)
    }

    private suspend fun rememberAnonymous(report: LocalAnonymousReport) {
        val raw = store.get(Keys.LOCAL_ANONYMOUS)
        val list = raw?.let { runCatching { AppJson.decodeFromString(localSerializer, it) }.getOrNull() } ?: emptyList()
        if (list.none { it.trackingCode == report.trackingCode }) {
            store.put(Keys.LOCAL_ANONYMOUS, AppJson.encodeToString(localSerializer, listOf(report) + list))
        }
    }

    companion object {
        private val random = SecureRandom()

        fun newSecret(): String {
            val bytes = ByteArray(18).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
