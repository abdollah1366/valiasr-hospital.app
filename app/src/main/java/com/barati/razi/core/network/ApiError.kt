package com.barati.razi.core.network

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/** خطای قابل نمایش به کاربر. */
data class ApiError(
    val httpStatus: Int?,
    val code: String,
    val message: String,
    val fields: Map<String, String> = emptyMap(),
) {
    /** خطای شبکه یا سرور که با تلاش دوباره ممکن است برطرف شود. */
    val isRetryable: Boolean get() = httpStatus == null || httpStatus >= 500 || httpStatus == 429

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(t: Throwable): ApiError = when (t) {
            is HttpException -> parse(t.code(), t.response()?.errorBody()?.string())
            is IOException -> ApiError(null, "network", "اتصال به سرور برقرار نشد. اینترنت یا شبکه بیمارستان را بررسی کنید.")
            else -> ApiError(null, "unknown", "خطای پیش‌بینی‌نشده رخ داد")
        }

        fun parse(status: Int, body: String?): ApiError {
            val fallback = when (status) {
                401 -> "نشست شما منقضی شده است؛ دوباره وارد شوید"
                403 -> "به این بخش دسترسی ندارید"
                404 -> "مورد درخواستی پیدا نشد"
                in 500..599 -> "خطای سرور؛ کمی بعد دوباره تلاش کنید"
                else -> "درخواست نامعتبر است"
            }
            val detail = runCatching { json.parseToJsonElement(body.orEmpty()).jsonObject["detail"] }.getOrNull()
            if (detail is JsonObject) {
                val fields = (detail["fields"] as? JsonObject)?.mapValues {
                    (it.value as? JsonPrimitive)?.contentOrNull.orEmpty()
                } ?: emptyMap()
                return ApiError(
                    status,
                    detail["code"]?.jsonPrimitive?.contentOrNull ?: "http_$status",
                    detail["message"]?.jsonPrimitive?.contentOrNull ?: fallback,
                    fields,
                )
            }
            return ApiError(status, "http_$status", fallback)
        }
    }
}

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Err(val error: ApiError) : Result<Nothing>
}

suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.Ok(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.Err(ApiError.from(e))
}
