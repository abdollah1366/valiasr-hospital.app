package com.barati.razi.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.barati.razi.AppContainer
import com.barati.razi.core.data.Keys
import com.barati.razi.core.data.can
import com.barati.razi.core.network.MeDto
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.TileGrid
import com.barati.razi.ui.components.TileItem
import com.barati.razi.ui.components.WideCard
import com.barati.razi.ui.theme.brandColor
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
    val waiting = pending.size + pendingRequests.size + chatPending.size
    RaziScaffold(
        title = "بخش کارکنان",
        actions = {
            IconButton(onClick = onNotifications) {
                BadgedBox(badge = {
                    if (unread > 0) Badge { Text(toPersianDigits(if (unread > 99) "99+" else unread.toString())) }
                }) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = if (unread > 0) toPersianDigits("اعلان‌ها، $unread خوانده‌نشده") else "اعلان‌ها",
                    )
                }
            }
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "خروج") }
        },
        floatingActionButton = {
            // اقدام اصلی کارکنان در منطقه شست: ثبت سریع گزارش خطا
            if (me.can("incident.create")) {
                ExtendedFloatingActionButton(
                    onClick = onNewIncident,
                    icon = { Icon(Icons.Outlined.ReportProblem, contentDescription = null) },
                    text = { Text("گزارش خطا") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(me, pendingAck, onAnnouncements)
            if (waiting > 0) {
                Text(
                    toPersianDigits("$waiting مورد بدون اینترنت ثبت شده و با وصل شدن خودکار ارسال می‌شود."),
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TileGrid(
                buildList {
                    if (me.can("incident.create")) {
                        add(
                            TileItem(
                                "گزارش‌های من",
                                if (pending.isNotEmpty()) toPersianDigits("${pending.size} در انتظار ارسال") else "پیگیری وضعیت",
                                Icons.AutoMirrored.Outlined.ListAlt,
                                onClick = onMyIncidents,
                            ),
                        )
                    }
                    if (me.can("incident.read")) {
                        add(TileItem("بررسی گزارش‌ها", "گزارش‌های خطای واحد یا بیمارستان", Icons.Outlined.FactCheck, onClick = onReview))
                    }
                    if (me.can("announcement.read")) {
                        add(
                            TileItem(
                                "اخبار و بخشنامه‌ها",
                                if (pendingAck > 0) "منتظر مطالعه شما" else "اطلاعیه‌های بیمارستان",
                                Icons.Outlined.Campaign,
                                badge = pendingAck,
                                onClick = onAnnouncements,
                            ),
                        )
                    }
                    if (me.can("training.read")) {
                        add(
                            TileItem(
                                "آموزش",
                                if (pendingTraining > 0) "دوره و آزمون در انتظار شما" else "دوره‌ها و آزمون‌ها",
                                Icons.Outlined.School,
                                badge = pendingTraining,
                                onClick = onTraining,
                            ),
                        )
                    }
                    add(
                        TileItem(
                            "گفتگو",
                            if (chatUnread > 0) "پیام‌های تازه همکاران" else "همکاران، گروه‌ها و کانال واحد",
                            Icons.AutoMirrored.Outlined.Chat,
                            badge = chatUnread,
                            onClick = onChat,
                        ),
                    )
                    add(TileItem("برنامه شیفت", "شیفت‌های من و جابجایی", Icons.Outlined.CalendarMonth, onClick = onSchedule))
                    if (me.can("request.create") || me.can("request.handle") || me.can("request.read")) {
                        add(
                            TileItem(
                                "درخواست پشتیبانی",
                                if (me.can("request.handle")) "کارتابل واحد شما" else "فناوری، تجهیزات و تأسیسات",
                                Icons.Outlined.SupportAgent,
                                onClick = onRequests,
                            ),
                        )
                    }
                },
            )
            WideCard(Icons.Outlined.Public, "خدمات عمومی بیمارستان", "راهنمای بخش‌ها، پرسش‌ها و شکایات مراجعان", onPublic)
            // فاصله برای اینکه دکمه شناور روی کارت آخر نیفتد
            Spacer(Modifier.height(72.dp))
        }
    }
}

/** کارت بالای صفحه: نام، واحد و سمت؛ اگر بخشنامه‌ای منتظر مطالعه باشد، یادآوری می‌کند. */
@Composable
private fun ProfileCard(me: MeDto, pendingAck: Int, onAnnouncements: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        me.fullName.trim().take(1),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        me.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    listOfNotNull(me.unitName, me.jobTitle).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            // نوار باریک قرمز برند، مثل صفحه اول مراجعان
            Box(Modifier.size(width = 32.dp, height = 4.dp).background(brandColor, RoundedCornerShape(2.dp)))
            if (pendingAck > 0) {
                Button(
                    onClick = onAnnouncements,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) { Text(toPersianDigits("$pendingAck بخشنامه منتظر مطالعه شماست"), fontWeight = FontWeight.Bold) }
            }
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
