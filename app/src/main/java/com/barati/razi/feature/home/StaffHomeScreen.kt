package com.barati.razi.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.barati.razi.AppContainer
import com.barati.razi.core.data.Keys
import com.barati.razi.core.data.can
import com.barati.razi.core.network.MeDto
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.MenuTile
import com.barati.razi.ui.components.RaziScaffold
import kotlinx.coroutines.launch

@Composable
fun StaffHomeScreen(
    container: AppContainer,
    me: MeDto,
    onNewIncident: () -> Unit,
    onMyIncidents: () -> Unit,
    onAnnouncements: () -> Unit,
    onReview: () -> Unit,
    onTraining: () -> Unit,
    onRequests: () -> Unit,
    onNotifications: () -> Unit,
    onChat: () -> Unit,
    onSchedule: () -> Unit,
    onPublic: () -> Unit,
    onLogout: () -> Unit,
) {
    var pendingAck by remember { mutableIntStateOf(0) }
    var pendingTraining by remember { mutableIntStateOf(0) }
    val pending by container.incidentRepository.pending.collectAsState(initial = emptyList())
    val pendingRequests by container.serviceRequestRepository.pending.collectAsState(initial = emptyList())
    val unread by container.notificationRepository.unread.collectAsState()
    val chatUnread by container.chatRepository.unread.collectAsState()
    val chatPending by container.chatRepository.pending.collectAsState(initial = emptyList())
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        container.scope.launch { container.notificationRepository.refreshUnread() }
        container.scope.launch { container.chatRepository.refreshUnread() }
    }
    NotificationPermissionPrompt(container)
    LaunchedEffect(me.id) {
        if (me.can("announcement.read")) pendingAck = container.contentRepository.pendingAckCount()
        if (me.can("training.read")) pendingTraining = container.trainingRepository.pendingCount()
    }
    RaziScaffold(
        title = "بخش کارکنان",
        actions = {
            IconButton(onClick = onNotifications) {
                BadgedBox(badge = {
                    if (unread > 0) Badge { Text(toPersianDigits(if (unread > 99) "99+" else unread.toString())) }
                }) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = if (unread > 0) toPersianDigits("اعلان‌ها، $unread خوانده‌نشده") else "اعلان‌ها",
                    )
                }
            }
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "خروج") }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(me.fullName, style = MaterialTheme.typography.titleLarge)
            listOfNotNull(me.unitName, me.jobTitle).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            if (me.can("incident.create")) {
                MenuTile("ثبت گزارش خطا، حادثه یا شبه‌خطا", "امکان ارسال ناشناس", Icons.Filled.ReportProblem, onNewIncident)
                MenuTile(
                    "گزارش‌های من",
                    if (pending.isNotEmpty()) toPersianDigits("${pending.size} گزارش در انتظار ارسال") else "پیگیری وضعیت",
                    Icons.Filled.ListAlt,
                    onMyIncidents,
                )
            }
            if (me.can("incident.read")) {
                MenuTile("بررسی گزارش‌ها", "گزارش‌های خطای واحد یا کل بیمارستان", Icons.Filled.FactCheck, onReview)
            }
            if (me.can("announcement.read")) {
                MenuTile(
                    "اخبار و بخشنامه‌ها",
                    if (pendingAck > 0) "بخشنامه‌هایی که باید مطالعه کنید" else null,
                    Icons.Filled.Campaign,
                    onAnnouncements,
                    badge = pendingAck,
                )
            }
            if (me.can("training.read")) {
                MenuTile(
                    "آموزش",
                    if (pendingTraining > 0) "دوره‌ها و آزمون‌هایی که در انتظار شماست" else "دوره‌ها، فیلم‌ها و آزمون‌های آموزشی",
                    Icons.Filled.School,
                    onTraining,
                    badge = pendingTraining,
                )
            }
            if (me.can("request.create") || me.can("request.handle") || me.can("request.read")) {
                MenuTile(
                    "درخواست پشتیبانی",
                    when {
                        pendingRequests.isNotEmpty() -> toPersianDigits("${pendingRequests.size} درخواست در انتظار ارسال")
                        me.can("request.handle") -> "کارتابل درخواست‌های واحد شما"
                        else -> "فناوری اطلاعات، تجهیزات پزشکی و تأسیسات"
                    },
                    Icons.Filled.SupportAgent,
                    onRequests,
                )
            }
            MenuTile(
                "گفتگو",
                when {
                    chatPending.isNotEmpty() -> toPersianDigits("${chatPending.size} پیام در انتظار ارسال")
                    chatUnread > 0 -> "پیام‌های تازه همکاران"
                    else -> "پیام به همکاران، گروه‌ها و کانال واحد"
                },
                Icons.Filled.Forum,
                onChat,
                badge = chatUnread,
            )
            MenuTile("برنامه شیفت", "شیفت‌های من، برنامه بخش و جابجایی", Icons.Filled.CalendarMonth, onSchedule)
            MenuTile("خدمات عمومی بیمارستان", "راهنمای بخش‌ها، پرسش‌ها و شکایات", Icons.Filled.Public, onPublic)
            Text(
                "بخش پژوهش در مراحل بعدی اضافه می‌شود.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** اندروید ۱۳ به بالا: یک بار پس از ورود، با توضیح، اجازه نمایش اعلان پرسیده می‌شود. */
@Composable
private fun NotificationPermissionPrompt(container: AppContainer) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        show = !granted && container.store.get(Keys.NOTIFICATION_PERMISSION_ASKED) == null
    }
    if (!show) return
    fun done(ask: Boolean) {
        show = false
        scope.launch { container.store.put(Keys.NOTIFICATION_PERMISSION_ASKED, "1") }
        if (ask) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    AlertDialog(
        onDismissRequest = { done(false) },
        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
        title = { Text("اعلان‌ها را روشن کنید") },
        text = {
            Text(
                "وقتی درخواستی به شما ارجاع شود، وضعیت درخواست یا گزارش شما تغییر کند یا بخشنامه و آزمون تازه‌ای " +
                    "منتشر شود، روی گوشی باخبر می‌شوید. متن اعلان روی صفحه قفل نشان داده نمی‌شود.",
            )
        },
        confirmButton = { TextButton(onClick = { done(true) }) { Text("روشن کردن") } },
        dismissButton = { TextButton(onClick = { done(false) }) { Text("بعداً") } },
    )
}
