package com.barati.razi.core.network

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** محل نگهداری توکن‌ها؛ پیاده‌سازی اندرویدی رمزنگاری‌شده است (EncryptedTokenStorage). */
interface TokenStorage {
    var accessToken: String?
    var refreshToken: String?
    fun clear()
}

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** در نسخه debug می‌توان آدرس سرور را از داخل اپ عوض کرد (مثلاً IP کامپیوتر در شبکه محلی). */
class BaseUrlInterceptor(private val origin: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val override = origin()?.toHttpUrlOrNull()
            ?: return chain.proceed(chain.request())
        val url: HttpUrl = chain.request().url.newBuilder()
            .scheme(override.scheme)
            .host(override.host)
            .port(override.port)
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}

/**
 * زبان بخش مراجعان را در سرآیند Accept-Language می‌فرستد تا سرور نام تخصص‌ها، پزشکان و محتوای
 * ترجمه‌شده را به همان زبان برگرداند. null یعنی فارسی (متن اصلی) و سرآیندی فرستاده نمی‌شود.
 */
class AcceptLanguageInterceptor(private val language: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val code = language() ?: return chain.proceed(chain.request())
        if (chain.request().header("Accept-Language") != null) return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header("Accept-Language", code).build())
    }
}

class AuthHeaderInterceptor(private val tokens: TokenStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/**
 * با پاسخ 401، یک بار توکن را با refresh token تمدید می‌کند و درخواست را تکرار می‌کند.
 * اگر تمدید ناموفق باشد، توکن‌ها پاک و [onSessionExpired] فراخوانی می‌شود.
 */
class TokenRefreshAuthenticator(
    private val tokens: TokenStorage,
    private val refreshUrl: () -> HttpUrl,
    private val plainClient: OkHttpClient,
    private val onSessionExpired: () -> Unit,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null
        if (response.priorResponse != null) return null
        synchronized(lock) {
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = tokens.accessToken
            // درخواست هم‌زمان دیگری توکن را تمدید کرده است
            if (current != null && current != sent) {
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val refresh = tokens.refreshToken ?: return expire()
            val body = AppJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
                .toRequestBody(JSON_MEDIA)
            val newTokens = runCatching {
                plainClient.newCall(Request.Builder().url(refreshUrl()).post(body).build()).execute().use { r ->
                    if (!r.isSuccessful) null
                    else AppJson.decodeFromString(TokenResponse.serializer(), r.body!!.string())
                }
            }.getOrNull() ?: return expire()
            tokens.accessToken = newTokens.accessToken
            tokens.refreshToken = newTokens.refreshToken
            return response.request.newBuilder().header("Authorization", "Bearer ${newTokens.accessToken}").build()
        }
    }

    private fun expire(): Request? {
        tokens.clear()
        onSessionExpired()
        return null
    }

    companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

object HttpFactory {
    fun create(
        baseUrl: String,
        tokens: TokenStorage,
        originOverride: () -> String?,
        debug: Boolean,
        language: () -> String? = { null },
        onSessionExpired: () -> Unit,
    ): ApiService = retrofit(baseUrl, "auth/refresh", tokens, originOverride, debug, onSessionExpired, language)
        .create(ApiService::class.java)

    /** سرویس مراجعان با توکن‌های جداگانه؛ تمدید نشست از مسیر patient/auth/refresh. */
    fun createPatient(
        baseUrl: String,
        tokens: TokenStorage,
        originOverride: () -> String?,
        debug: Boolean,
        language: () -> String? = { null },
        onSessionExpired: () -> Unit,
    ): PatientApiService = retrofit(baseUrl, "patient/auth/refresh", tokens, originOverride, debug, onSessionExpired, language)
        .create(PatientApiService::class.java)

    private fun retrofit(
        baseUrl: String,
        refreshPath: String,
        tokens: TokenStorage,
        originOverride: () -> String?,
        debug: Boolean,
        onSessionExpired: () -> Unit,
        language: () -> String?,
    ): Retrofit {
        val baseInterceptor = BaseUrlInterceptor(originOverride)
        val plain = OkHttpClient.Builder()
            .addInterceptor(baseInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val refreshUrl = { "$baseUrl$refreshPath".toHttpUrlOrNull()!! }
        val builder = plain.newBuilder()
            .addInterceptor(AuthHeaderInterceptor(tokens))
            .addInterceptor(AcceptLanguageInterceptor(language))
            .authenticator(TokenRefreshAuthenticator(tokens, refreshUrl, plain, onSessionExpired))
        if (debug) {
            // فقط در debug؛ بدنه درخواست‌ها ممکن است اطلاعات بیمار داشته باشد، پس فقط سرخط‌ها لاگ می‌شوند.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                },
            )
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(builder.build())
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
