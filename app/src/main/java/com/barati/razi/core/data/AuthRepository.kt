package com.barati.razi.core.data

import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.ChangePasswordRequest
import com.barati.razi.core.network.LoginRequest
import com.barati.razi.core.network.MeDto
import com.barati.razi.core.network.RefreshRequest
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.TokenStorage
import com.barati.razi.core.network.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    data object Unknown : SessionState
    data class LoggedOut(val message: String? = null) : SessionState
    data class LoggedIn(val me: MeDto) : SessionState
}

fun MeDto.can(permission: String) = permission in permissions

interface AuthRepository {
    val session: StateFlow<SessionState>
    suspend fun restore()
    suspend fun login(personnelCode: String, password: String): Result<MeDto>
    suspend fun changePassword(current: String, new: String): Result<MeDto>
    suspend fun logout()
    fun onSessionExpired()
}

class DefaultAuthRepository(
    private val api: ApiService,
    private val tokens: TokenStorage,
    private val cache: KeyValueStore,
    private val deviceName: String,
) : AuthRepository {
    private suspend fun remember(me: MeDto) {
        cache.put(KEY_ME, AppJson.encodeToString(MeDto.serializer(), me))
        _session.value = SessionState.LoggedIn(me)
    }

    private suspend fun cachedMe(): MeDto? =
        cache.get(KEY_ME)?.let { runCatching { AppJson.decodeFromString(MeDto.serializer(), it) }.getOrNull() }

    /** آیا توکن ورود روی گوشی هست؛ کارهای پس‌زمینه بدون آن به سرور درخواست نمی‌فرستند. */
    val hasSession: Boolean get() = tokens.refreshToken != null

    private val _session = MutableStateFlow<SessionState>(SessionState.Unknown)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    override suspend fun restore() {
        if (tokens.refreshToken == null) {
            _session.value = SessionState.LoggedOut()
            return
        }
        when (val r = apiCall { api.me() }) {
            is Result.Ok -> remember(r.value)
            is Result.Err -> {
                // بدون اینترنت با آخرین اطلاعات کاربر ادامه می‌دهیم تا ثبت گزارش آفلاین ممکن باشد.
                val cached = if (r.error.isRetryable) cachedMe() else null
                _session.value = if (cached != null) SessionState.LoggedIn(cached) else SessionState.LoggedOut()
            }
        }
    }

    override suspend fun login(personnelCode: String, password: String): Result<MeDto> {
        val t = apiCall { api.login(LoginRequest(personnelCode.trim(), password, deviceName)) }
        if (t is Result.Err) return t
        val tokenResponse = (t as Result.Ok).value
        tokens.accessToken = tokenResponse.accessToken
        tokens.refreshToken = tokenResponse.refreshToken
        val me = apiCall { api.me() }
        if (me is Result.Ok) remember(me.value) else tokens.clear()
        return me
    }

    override suspend fun changePassword(current: String, new: String): Result<MeDto> {
        val r = apiCall { api.changePassword(ChangePasswordRequest(current, new)) }
        if (r is Result.Err) return r
        val me = apiCall { api.me() }
        if (me is Result.Ok) remember(me.value)
        return me
    }

    override suspend fun logout() {
        tokens.refreshToken?.let { refresh -> apiCall { api.logout(RefreshRequest(refresh)) } }
        tokens.clear()
        cache.put(KEY_ME, null)
        _session.value = SessionState.LoggedOut()
    }

    companion object {
        private const val KEY_ME = "me"
    }

    override fun onSessionExpired() {
        _session.value = SessionState.LoggedOut("نشست شما منقضی شده است؛ دوباره وارد شوید")
    }
}
