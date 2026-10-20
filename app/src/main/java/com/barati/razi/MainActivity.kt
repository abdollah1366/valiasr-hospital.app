package com.barati.razi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.barati.razi.core.data.SessionState
import com.barati.razi.notify.SystemNotifier
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
        setContent {
            RaziTheme {
                Surface(Modifier.fillMaxSize()) { RaziNavHost(container) }
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
