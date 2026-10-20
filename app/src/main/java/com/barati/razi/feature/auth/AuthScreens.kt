package com.barati.razi.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barati.razi.AppContainer
import com.barati.razi.BuildConfig
import com.barati.razi.core.data.AuthRepository
import com.barati.razi.core.network.MeDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.util.toLatinDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

data class LoginUiState(
    val personnelCode: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: MeDto? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onCode(v: String) = _state.update { it.copy(personnelCode = toLatinDigits(v).filter { c -> !c.isWhitespace() }, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun submit() {
        val s = _state.value
        if (s.personnelCode.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "کد پرسنلی و رمز عبور را وارد کنید") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.login(s.personnelCode, toLatinDigits(s.password))) {
                is Result.Ok -> _state.update { it.copy(loading = false, password = "", loggedIn = r.value) }
                is Result.Err -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }
}

@Composable
fun LoginScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLoggedIn: (mustChangePassword: Boolean) -> Unit,
    vm: LoginViewModel = viewModel { LoginViewModel(container.authRepository) },
) {
    val state by vm.state.collectAsState()
    state.loggedIn?.let { me ->
        androidx.compose.runtime.LaunchedEffect(me) { onLoggedIn(me.mustChangePassword) }
    }
    RaziScaffold(title = "ورود کارکنان", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("با کد پرسنلی و رمز عبوری که واحد فناوری اطلاعات به شما داده است وارد شوید.")
            OutlinedTextField(
                value = state.personnelCode,
                onValueChange = vm::onCode,
                label = { Text("کد پرسنلی") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPassword,
                label = { Text("رمز عبور") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error)
            PrimaryButton("ورود", onClick = vm::submit, loading = state.loading)
            if (BuildConfig.ALLOW_SERVER_OVERRIDE) ServerOverride(container)
        }
    }
}

/** فقط در نسخه debug: برای آزمایش روی گوشی واقعی، آدرس کامپیوتری که بک‌اند روی آن اجراست. */
@Composable
private fun ServerOverride(container: AppContainer) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(container.serverOrigin.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "سرور (نسخه آزمایشی): " + (container.serverOrigin ?: BuildConfig.API_BASE_URL),
            style = MaterialTheme.typography.bodySmall,
        )
        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("مثلاً http://192.168.1.10:8000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                container.setServerOrigin(text)
                editing = false
            }) { Text("ذخیره آدرس (خالی = پیش‌فرض)") }
        } else {
            TextButton(onClick = { editing = true }) { Text("تغییر آدرس سرور") }
        }
    }
}

@Composable
fun ChangePasswordScreen(container: AppContainer, onDone: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    RaziScaffold(title = "تغییر رمز عبور") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("برای ادامه، رمز موقت را به یک رمز شخصی تغییر دهید. رمز جدید حداقل ۸ نویسه و ترکیبی از حرف و عدد باشد.")
            listOf(
                Triple("رمز فعلی", current) { v: String -> current = v },
                Triple("رمز جدید", new1) { v: String -> new1 = v },
                Triple("تکرار رمز جدید", new2) { v: String -> new2 = v },
            ).forEach { (label, value, set) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { set(it); error = null },
                    label = { Text(label) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ErrorText(error)
            PrimaryButton("ذخیره", loading = loading, onClick = {
                error = when {
                    new1.length < 8 -> "رمز جدید باید حداقل ۸ نویسه باشد"
                    new1 != new2 -> "تکرار رمز با رمز جدید یکسان نیست"
                    else -> null
                }
                if (error == null) {
                    loading = true
                    scope.launch {
                        when (val r = container.authRepository.changePassword(toLatinDigits(current), toLatinDigits(new1))) {
                            is Result.Ok -> onDone()
                            is Result.Err -> error = r.error.message
                        }
                        loading = false
                    }
                }
            })
        }
    }
}
