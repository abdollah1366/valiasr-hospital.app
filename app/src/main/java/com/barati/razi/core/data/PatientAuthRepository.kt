package com.barati.razi.core.data

import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.OtpRequest
import com.barati.razi.core.network.OtpSentDto
import com.barati.razi.core.network.OtpVerifyRequest
import com.barati.razi.core.network.PatientApiService
import com.barati.razi.core.network.PatientDto
import com.barati.razi.core.network.PatientUpdateRequest
import com.barati.razi.core.network.RefreshRequest
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.TokenStorage
import com.barati.razi.core.network.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PatientSession {
    data object Unknown : PatientSession

    /** [skipped]: کاربر «ادامه بدون ورود» را زده و صفحه ثبت‌نام دوباره خودکار باز نمی‌شود. */
    data class SignedOut(val skipped: Boolean) : PatientSession
    data class SignedIn(val patient: PatientDto) : PatientSession
}

/** حساب مراجع (موبایل + کد پیامکی)؛ توکن‌هایش جدا از حساب کارکنان نگهداری می‌شوند. */
class PatientAuthRepository(
    private val api: PatientApiService,
    private val tokens: TokenStorage,
    private val store: KeyValueStore,
    private val languages: LanguageRepository,
    private val deviceName: String,
) {
    private val _session = MutableStateFlow<PatientSession>(PatientSession.Unknown)
    val session: StateFlow<PatientSession> = _session.asStateFlow()

    private suspend fun signedOut() = PatientSession.SignedOut(store.get(Keys.PATIENT_SIGNUP_SKIPPED) == "1")

    private suspend fun remember(patient: PatientDto) {
        store.put(Keys.PATIENT_PROFILE, AppJson.encodeToString(PatientDto.serializer(), patient))
        _session.value = PatientSession.SignedIn(patient)
    }

    suspend fun restore() {
        if (tokens.refreshToken == null) {
            _session.value = signedOut()
            return
        }
        when (val r = apiCall { api.me() }) {
            is Result.Ok -> remember(r.value)
            is Result.Err -> {
                // بدون اینترنت با آخرین نمایه ادامه می‌دهیم؛ اگر نشست باطل شده باشد، خارج می‌شویم.
                val cached = if (r.error.isRetryable) {
                    store.get(Keys.PATIENT_PROFILE)?.let {
                        runCatching { AppJson.decodeFromString(PatientDto.serializer(), it) }.getOrNull()
                    }
                } else {
                    null
                }
                _session.value = cached?.let { PatientSession.SignedIn(it) } ?: signedOut()
            }
        }
    }

    suspend fun requestCode(mobile: String): Result<OtpSentDto> = apiCall { api.requestOtp(OtpRequest(mobile)) }

    /** با کد درست وارد می‌شود و زبان انتخابی روی گوشی و سرور ذخیره می‌شود. */
    suspend fun verify(mobile: String, code: String, language: AppLanguage): Result<PatientDto> {
        val r = apiCall { api.verifyOtp(OtpVerifyRequest(mobile, code.trim(), language.code, deviceName)) }
        if (r is Result.Err) return r
        val t = (r as Result.Ok).value
        tokens.accessToken = t.accessToken
        tokens.refreshToken = t.refreshToken
        languages.set(AppLanguage.fromCode(t.patient.language))
        remember(t.patient)
        return Result.Ok(t.patient)
    }

    /** زبان روی گوشی فوراً عوض می‌شود؛ اگر مراجع وارد شده باشد، روی سرور هم ذخیره می‌شود. */
    suspend fun setLanguage(language: AppLanguage) {
        languages.set(language)
        if (_session.value is PatientSession.SignedIn) {
            val r = apiCall { api.updateMe(PatientUpdateRequest(language = language.code)) }
            if (r is Result.Ok) remember(r.value)
        }
    }

    suspend fun skip() {
        store.put(Keys.PATIENT_SIGNUP_SKIPPED, "1")
        _session.value = PatientSession.SignedOut(skipped = true)
    }

    suspend fun logout() {
        tokens.refreshToken?.let { refresh -> apiCall { api.logout(RefreshRequest(refresh)) } }
        tokens.clear()
        store.put(Keys.PATIENT_PROFILE, null)
        store.put(Keys.PATIENT_SIGNUP_SKIPPED, "1")
        _session.value = PatientSession.SignedOut(skipped = true)
    }

    fun onSessionExpired() {
        _session.value = PatientSession.SignedOut(skipped = true)
    }
}
