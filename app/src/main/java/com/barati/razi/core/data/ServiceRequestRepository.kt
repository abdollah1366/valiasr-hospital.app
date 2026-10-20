package com.barati.razi.core.data

import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.PersonDto
import com.barati.razi.core.network.RequestAssignBody
import com.barati.razi.core.network.RequestCommentBody
import com.barati.razi.core.network.RequestCreateRequest
import com.barati.razi.core.network.RequestDetailDto
import com.barati.razi.core.network.RequestPageDto
import com.barati.razi.core.network.RequestRateBody
import com.barati.razi.core.network.RequestReopenBody
import com.barati.razi.core.network.RequestStatusBody
import com.barati.razi.core.network.RequestSummaryDto
import com.barati.razi.core.network.RequestTransferBody
import com.barati.razi.core.network.RequestTypeDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/** درخواستی که هنوز به سرور نرسیده است (صف آفلاین). */
@Serializable
data class PendingServiceRequest(
    val request: RequestCreateRequest,
    val typeName: String,
    val createdAtMillis: Long,
    val lastError: String? = null,
    val failedPermanently: Boolean = false,
)

/** درخواست‌های پشتیبانی (فناوری اطلاعات، تجهیزات پزشکی، تأسیسات). مجوزها فقط در سرور بررسی می‌شوند. */
class ServiceRequestRepository(
    private val api: ApiService,
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val queueLock = Mutex()
    private val serializer = ListSerializer(PendingServiceRequest.serializer())

    suspend fun types(): Result<List<RequestTypeDto>> = apiCall { api.requestTypes() }
    suspend fun mine(): Result<List<RequestSummaryDto>> = apiCall { api.myRequests() }
    suspend fun queue(status: String?, assigned: String?): Result<RequestPageDto> =
        apiCall { api.requestQueue(status = status, assigned = assigned) }
    suspend fun detail(id: String): Result<RequestDetailDto> = apiCall { api.request(id) }

    suspend fun changeStatus(id: String, status: String, note: String?, resolution: String?): Result<RequestDetailDto> =
        apiCall { api.changeRequestStatus(id, RequestStatusBody(status, note.clean(), resolution.clean())) }

    suspend fun agents(id: String): Result<List<PersonDto>> = apiCall { api.requestAgents(id) }

    suspend fun assign(id: String, userId: String?): Result<RequestDetailDto> =
        apiCall { api.assignRequest(id, RequestAssignBody(userId)) }

    /** null یعنی ارجاع انجام شد و کاربر دیگر به این درخواست دسترسی ندارد. */
    suspend fun transfer(id: String, unitId: String, note: String): Result<RequestDetailDto?> = apiCall {
        val resp = api.transferRequest(id, RequestTransferBody(unitId, note.trim()))
        if (!resp.isSuccessful) throw HttpException(resp)
        if (resp.code() == 204) null else resp.body()
    }

    suspend fun comment(id: String, text: String): Result<RequestDetailDto> =
        apiCall { api.commentRequest(id, RequestCommentBody(text.trim())) }

    suspend fun attach(id: String, filename: String, contentType: String, bytes: ByteArray): Result<RequestDetailDto> =
        apiCall { api.attachToRequest(id, filename, bytes.toRequestBody(contentType.toMediaType())) }

    suspend fun rate(id: String, score: Int, comment: String?): Result<RequestDetailDto> =
        apiCall { api.rateRequest(id, RequestRateBody(score, comment.clean())) }

    suspend fun reopen(id: String, note: String): Result<RequestDetailDto> =
        apiCall { api.reopenRequest(id, RequestReopenBody(note.trim())) }

    // ----- صف آفلاین: درخواست ثبت‌شده بدون اینترنت بعداً خودکار فرستاده می‌شود.

    val pending: Flow<List<PendingServiceRequest>> = store.observe(Keys.PENDING_REQUESTS).map(::decode)

    private fun decode(raw: String?): List<PendingServiceRequest> =
        raw?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    private suspend fun save(list: List<PendingServiceRequest>) =
        store.put(Keys.PENDING_REQUESTS, AppJson.encodeToString(serializer, list))

    suspend fun enqueue(request: RequestCreateRequest, typeName: String) = queueLock.withLock {
        val list = decode(store.get(Keys.PENDING_REQUESTS))
        if (list.none { it.request.clientUuid == request.clientUuid }) {
            save(list + PendingServiceRequest(request, typeName, clock()))
        }
    }

    suspend fun discard(clientUuid: String) = queueLock.withLock {
        save(decode(store.get(Keys.PENDING_REQUESTS)).filterNot { it.request.clientUuid == clientUuid })
    }

    suspend fun retryFailed() = queueLock.withLock {
        save(decode(store.get(Keys.PENDING_REQUESTS)).map { it.copy(failedPermanently = false) })
    }

    /** ارسال صف؛ [FlushResult.sent] نگاشت client_uuid به شناسه درخواست ساخته‌شده است. ارسال تکراری بی‌خطر است. */
    suspend fun flush(): FlushResult = queueLock.withLock {
        var list = decode(store.get(Keys.PENDING_REQUESTS))
        val sent = mutableMapOf<String, String>()
        for (item in list.filterNot { it.failedPermanently }) {
            val uuid = item.request.clientUuid
            when (val r = apiCall { api.createRequest(item.request) }) {
                is Result.Ok -> {
                    sent[uuid] = r.value.id
                    list = list.filterNot { it.request.clientUuid == uuid }
                    save(list)
                }
                is Result.Err -> {
                    val e = r.error
                    val retry = e.isRetryable || e.httpStatus == 401 || e.httpStatus == 403
                    list = list.map {
                        if (it.request.clientUuid == uuid) it.copy(lastError = e.message, failedPermanently = !retry) else it
                    }
                    save(list)
                    if (retry) return@withLock FlushResult(FlushOutcome.RETRY_LATER, sent)
                }
            }
        }
        FlushResult(FlushOutcome.DONE, sent)
    }

    private fun String?.clean(): String? = this?.trim()?.ifEmpty { null }
}
