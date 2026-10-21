package com.barati.razi.feature.notifications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.barati.razi.AppContainer
import com.barati.razi.core.network.NotificationDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.notifications.NotificationLogic
import com.barati.razi.core.notifications.NotificationTarget
import com.barati.razi.core.requests.RequestLogic
import com.barati.razi.feature.requests.Tone
import com.barati.razi.feature.training.EmptyState
import com.barati.razi.feature.training.IconCircle
import com.barati.razi.feature.training.Pill
import com.barati.razi.feature.training.TrainingColors
import com.barati.razi.notify.SystemNotifier
import com.barati.razi.ui.components.LoadingBox
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.RaziScaffold
import kotlinx.coroutines.launch

private data class NotifStyle(val icon: ImageVector, val tone: Tone)

private fun styleOf(linkType: String?): NotifStyle = when (linkType) {
    "request" -> NotifStyle(Icons.Filled.Build, Tone(TrainingColors.blue, TrainingColors.blueBg))
    "incident" -> NotifStyle(Icons.Filled.ReportProblem, Tone(TrainingColors.red, TrainingColors.redBg))
    "announcement" -> NotifStyle(Icons.Filled.Campaign, Tone(TrainingColors.amber, TrainingColors.amberBg))
    "course" -> NotifStyle(Icons.Filled.School, Tone(TrainingColors.green, TrainingColors.greenBg))
    "exam" -> NotifStyle(Icons.Filled.Quiz, Tone(TrainingColors.amber, TrainingColors.amberBg))
    "schedule" -> NotifStyle(Icons.Filled.CalendarMonth, Tone(TrainingColors.amber, TrainingColors.amberBg))
    "chat" -> NotifStyle(Icons.Filled.Forum, Tone(TrainingColors.blue, TrainingColors.blueBg))
    "appointment" -> NotifStyle(Icons.Filled.Event, Tone(TrainingColors.green, TrainingColors.greenBg))
    else -> NotifStyle(Icons.Filled.Notifications, Tone(TrainingColors.gray, TrainingColors.grayBg))
}

@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit, onOpen: (NotificationTarget) -> Unit) {
    val repo = container.notificationRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val list = remember { mutableStateListOf<NotificationDto>() }
    var nextBefore by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var canPost by remember { mutableStateOf(SystemNotifier.canPost(context)) }

    LaunchedEffect(version) {
        when (val r = repo.page()) {
            is Result.Ok -> {
                list.clear()
                list.addAll(r.value.items)
                nextBefore = r.value.nextBefore
                error = null
            }
            is Result.Err -> if (list.isEmpty()) error = r.error.message
        }
        loading = false
    }
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canPost = SystemNotifier.canPost(context)
        if (resumedOnce) version++ else resumedOnce = true
    }

    fun openItem(n: NotificationDto) {
        if (!n.read) {
            val i = list.indexOfFirst { it.id == n.id }
            if (i >= 0) list[i] = n.copy(read = true)
            scope.launch { repo.markRead(n.id) }
        }
        NotificationLogic.target(n.linkType, n.linkId)?.let(onOpen)
    }

    RaziScaffold(
        title = "اعلان‌ها",
        onBack = onBack,
        actions = {
            if (list.any { !it.read }) {
                IconButton(onClick = {
                    scope.launch {
                        if (repo.markAllRead() is Result.Ok) {
                            for (i in list.indices) list[i] = list[i].copy(read = true)
                        }
                    }
                }) { Icon(Icons.Filled.DoneAll, contentDescription = "همه خوانده شد") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingBox()
                error != null -> MessageBox(error!!, actionLabel = "تلاش دوباره", onAction = {
                    loading = true
                    version++
                })
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!canPost) {
                        item { PermissionCard(context) }
                    }
                    if (list.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Filled.Notifications,
                                "اعلانی ندارید",
                                "تغییر وضعیت درخواست‌ها و گزارش‌ها، ارجاع کار و اطلاعیه‌های تازه اینجا نشان داده می‌شود.",
                                Modifier.padding(top = 48.dp),
                            )
                        }
                    }
                    NotificationLogic.group(list).forEach { (label, group) ->
                        item(key = "h-$label") {
                            Text(
                                label,
                                Modifier.padding(top = 8.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = TrainingColors.gray,
                            )
                        }
                        items(group, key = { it.id }) { n -> NotificationRow(n) { openItem(n) } }
                    }
                    val before = nextBefore
                    if (before != null) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                OutlinedButton(
                                    enabled = !loadingMore,
                                    onClick = {
                                        loadingMore = true
                                        scope.launch {
                                            val r = repo.page(before)
                                            if (r is Result.Ok) {
                                                list.addAll(r.value.items.filter { n -> list.none { it.id == n.id } })
                                                nextBefore = r.value.nextBefore
                                            }
                                            loadingMore = false
                                        }
                                    },
                                ) { Text("اعلان‌های قدیمی‌تر") }
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        item { BatteryHint(context) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationDto, onClick: () -> Unit) {
    val style = styleOf(n.linkType)
    val unread = !n.read
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (unread) 0.dp else 1.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconCircle(style.icon, style.tone.color, style.tone.background, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    n.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!n.body.isNullOrBlank()) {
                    Text(
                        n.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = TrainingColors.gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(RequestLogic.ago(n.createdAt), style = MaterialTheme.typography.labelSmall, color = TrainingColors.gray)
                    if (n.important) Pill("مهم", TrainingColors.red, TrainingColors.redBg)
                }
            }
            if (unread) {
                Box(
                    Modifier.padding(top = 6.dp).size(9.dp).background(
                        if (n.important) TrainingColors.red else MaterialTheme.colorScheme.primary,
                        CircleShape,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(context: Context) {
    HintCard(
        Icons.Filled.NotificationsOff,
        "اعلان روی گوشی خاموش است",
        "اعلان‌ها فقط در همین صفحه دیده می‌شوند. برای باخبر شدن بدون باز کردن اپ، اعلان را روشن کنید.",
        "روشن کردن اعلان",
    ) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        }
        context.startSafely(intent)
    }
}

@Composable
private fun BatteryHint(context: Context) {
    HintCard(
        Icons.Filled.BatterySaver,
        "اعلان‌ها دیر می‌رسند؟",
        "اپ هر ۱۵ دقیقه یک بار اعلان تازه را بررسی می‌کند. اگر گوشی در حالت صرفه‌جویی باتری است، " +
            "در تنظیمات باتری، «بیمارستان ولیعصر» را از محدودیت خارج کنید.",
        "تنظیمات باتری",
    ) {
        context.startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Composable
private fun HintCard(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TrainingColors.amberBg),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = TrainingColors.amber)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TrainingColors.amber)
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
