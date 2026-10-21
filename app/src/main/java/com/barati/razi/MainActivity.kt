package com.barati.razi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.barati.razi.core.data.PatientSession
import com.barati.razi.core.data.ThemeMode
import com.barati.razi.core.data.SessionState
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.notify.SystemNotifier
import com.barati.razi.ui.i18n.LocalizedContent
import com.barati.razi.ui.navigation.RaziNavHost
import com.barati.razi.ui.theme.RaziTheme
import com.barati.razi.work.ChatSendWorker
import com.barati.razi.work.IncidentUploadWorker
import com.barati.razi.work.NotificationWorker
import com.barati.razi.work.RequestUploadWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as RaziApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleOpenIntent(intent)
        lifecycleScope.launch {
            container.loadServerOrigin()
            container.authRepository.restore()
            container.patientAuthRepository.restore()
            // اگر گزارش یا درخواستی از قبل در صف مانده باشد، ارسال آن دوباره تلاش می‌شود.
            IncidentUploadWorker.schedule(applicationContext)
            RequestUploadWorker.schedule(applicationContext)
        }
        lifecycleScope.launch {
            container.authRepository.session.collect { s ->
                when (s) {
                    is SessionState.LoggedIn -> {
                        SystemNotifier.ensureChannels(applicationContext)
                        NotificationWorker.schedule(applicationContext)
                        // پیام‌های گفتگو که پیش از بسته شدن اپ در صف ماندند
                        ChatSendWorker.schedule(applicationContext)
                    }
                    is SessionState.LoggedOut -> {
                        // اعلان‌های کاربر قبلی روی گوشی نمی‌مانند و دریافت دوره‌ای متوقف می‌شود.
                        NotificationWorker.cancel(applicationContext)
                        SystemNotifier.cancelAll(applicationContext)
                        container.notificationRepository.clear()
                        // صف پیام‌های کاربر قبلی نباید با حساب بعدی فرستاده شود
                        ChatSendWorker.cancel(applicationContext)
                        container.chatRepository.clear()
                    }
                    SessionState.Unknown -> Unit
                }
            }
        }
        lifecycleScope.launch {
            container.patientAuthRepository.session.collect { s ->
                when (s) {
                    // یادآورهای نوبت با نوبت‌های تازه سرور به‌روز می‌شوند؛ با خروج مراجع پاک می‌شوند.
                    is PatientSession.SignedIn -> container.appointmentRepository.myAppointments()
                    is PatientSession.SignedOut -> container.appointmentRepository.clearReminders()
                    PatientSession.Unknown -> Unit
                }
            }
        }
        setContent {
            val themeMode by container.themeRepository.mode.collectAsState()
            RaziTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                },
            ) {
                // زبان گوشی اثری ندارد: اپ فارسی است و فقط صفحه‌های مراجعان زبان خودشان را می‌گیرند.
                LocalizedContent(AppLanguage.FA) {
                    // اندروید ۱۵ به بعد adjustResize را نادیده می‌گیرد و صفحه‌کلید روی فرم‌ها می‌افتد و پایین صفحه دست‌نیافتنی می‌شود.
                    Surface(Modifier.fillMaxSize().imePadding()) { RaziNavHost(container) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        val type = intent?.getStringExtra(SystemNotifier.EXTRA_LINK_TYPE) ?: return
        container.pendingOpen.value = OpenRequest(
            notificationId = intent.getStringExtra(SystemNotifier.EXTRA_NOTIFICATION_ID),
            linkType = type,
            linkId = intent.getStringExtra(SystemNotifier.EXTRA_LINK_ID),
        )
    }
}
