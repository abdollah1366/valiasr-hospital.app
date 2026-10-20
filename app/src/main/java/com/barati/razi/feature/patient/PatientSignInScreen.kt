package com.barati.razi.feature.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.data.LanguageRepository
import com.barati.razi.core.data.PatientAuthRepository
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.ApiError
import com.barati.razi.core.network.OtpSentDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.util.toLatinDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberErrorText
import com.barati.razi.ui.i18n.LanguageAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientSignInState(
    val mobile: String = "",
    val code: String = "",
    val sent: OtpSentDto? = null,
    /** زمان (میلی‌ثانیه) که ارسال دوباره کد مجاز می‌شود. */
    val resendAt: Long = 0,
    val loading: Boolean = false,
    val error: ApiError? = null,
    val invalidMobile: Boolean = false,
    /** کاربر زبان را خودش انتخاب کرده و زبانِ حدس‌زده از پیش‌شماره جای آن را نمی‌گیرد. */
    val languageChosen: Boolean = false,
    val signedIn: Boolean = false,
)

class PatientSignInViewModel(
    private val repo: PatientAuthRepository,
    private val languages: LanguageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PatientSignInState())
    val state: StateFlow<PatientSignInState> = _state.asStateFlow()

    fun onMobile(v: String) = _state.update {
        it.copy(mobile = toLatinDigits(v).filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20), error = null, invalidMobile = false)
    }

    fun onCode(v: String) = _state.update { it.copy(code = toLatinDigits(v).filter(Char::isDigit).take(6), error = null) }

    /** زبانی که با پیش‌شماره شماره واردشده پیشنهاد می‌شود. */
    fun detectedLanguage(): AppLanguage? {
        val digits = _state.value.mobile.count(Char::isDigit)
        return if (digits >= 4) AppLanguage.forPhone(_state.value.mobile) else null
    }

    fun chooseLanguage(language: AppLanguage) {
        _state.update { it.copy(languageChosen = true) }
        viewModelScope.launch { languages.set(language) }
    }

    fun requestCode() {
        val s = _state.value
        if (s.mobile.count(Char::isDigit) < 8) {
            _state.update { it.copy(invalidMobile = true) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.requestCode(s.mobile)) {
                is Result.Ok -> {
                    // صفحه کد به زبانِ پیش‌شماره نشان داده می‌شود، مگر اینکه کاربر زبان دیگری انتخاب کرده باشد.
                    if (!s.languageChosen) languages.set(AppLanguage.fromCode(r.value.suggestedLanguage))
                    _state.update {
                        it.copy(
                            loading = false,
                            sent = r.value,
                            code = "",
                            resendAt = System.currentTimeMillis() + r.value.resendAfter * 1000L,
                        )
                    }
                }
                is Result.Err -> _state.update {
                    it.copy(loading = false, error = r.error, invalidMobile = r.error.code == "invalid_mobile")
                }
            }
        }
    }

    fun verify() {
        val s = _state.value
        val sent = s.sent ?: return
        if (s.code.length < 4) {
            _state.update { it.copy(error = ApiError(400, "otp_invalid", "")) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.verify(sent.mobile, s.code, languages.language.value)) {
                is Result.Ok -> _state.update { it.copy(loading = false, signedIn = true) }
                is Result.Err -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun changeNumber() = _state.update { it.copy(sent = null, code = "", error = null) }
}

@Composable
fun PatientSignInScreen(
    container: AppContainer,
    language: AppLanguage,
    onSignedIn: () -> Unit,
    onSkip: () -> Unit,
    onStaff: () -> Unit,
    vm: PatientSignInViewModel = viewModel {
        PatientSignInViewModel(container.patientAuthRepository, container.languageRepository)
    },
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    val errorText = rememberErrorText()
    val otpInvalid = stringResource(R.string.otp_invalid)
    val tooMany = stringResource(R.string.error_too_many)
    val message = state.error?.let { e ->
        when (e.code) {
            "otp_invalid" -> otpInvalid
            "otp_too_soon", "otp_limit" -> if (language == AppLanguage.FA) e.message else tooMany
            else -> errorText(e)
        }
    }

    RaziScaffold(
        title = stringResource(R.string.signin_title),
        actions = { LanguageAction(language, vm::chooseLanguage) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.hospital_full_name),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val sent = state.sent
            if (sent == null) {
                Text(stringResource(R.string.signin_subtitle), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    state.mobile,
                    vm::onMobile,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.signin_mobile)) },
                    placeholder = { Text(stringResource(R.string.signin_mobile_hint)) },
                    singleLine = true,
                    isError = state.invalidMobile,
                    // شماره تلفن همیشه چپ‌به‌راست است، حتی در صفحه فارسی و عربی.
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = if (state.invalidMobile) {
                        { Text(stringResource(R.string.signin_invalid_mobile)) }
                    } else {
                        null
                    },
                )
                val shown = if (state.languageChosen) language else vm.detectedLanguage() ?: language
                LanguageInfo(shown)
                ErrorText(message.takeUnless { state.invalidMobile })
                PrimaryButton(stringResource(R.string.signin_send_code), vm::requestCode, loading = state.loading)
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.signin_skip)) }
                TextButton(onClick = onStaff, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.signin_staff)) }
            } else {
                Text(stringResource(R.string.otp_sent_to, "⁦${sent.mobile}⁩"))
                OutlinedTextField(
                    state.code,
                    vm::onCode,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.otp_code)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                sent.debugCode?.let {
                    Text(stringResource(R.string.otp_debug, it), color = MaterialTheme.colorScheme.secondary)
                }
                ErrorText(message)
                PrimaryButton(stringResource(R.string.otp_verify), vm::verify, loading = state.loading)
                ResendRow(state.resendAt, onResend = vm::requestCode, onChangeNumber = vm::changeNumber)
            }
        }
    }
}

@Composable
private fun LanguageInfo(language: AppLanguage) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Language, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.signin_language, language.nativeName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ResendRow(resendAt: Long, onResend: () -> Unit, onChangeNumber: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resendAt) {
        while (now < resendAt) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = ((resendAt - now + 999) / 1000).toInt()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (remaining > 0) {
            Text(
                stringResource(R.string.otp_resend_in, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onResend) { Text(stringResource(R.string.otp_resend)) }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onChangeNumber) { Text(stringResource(R.string.otp_change_number)) }
    }
}
