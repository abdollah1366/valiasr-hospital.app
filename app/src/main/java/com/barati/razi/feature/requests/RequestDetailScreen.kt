package com.barati.razi.feature.requests

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.barati.razi.AppContainer
import com.barati.razi.core.network.PersonDto
import com.barati.razi.core.network.RequestAttachmentDto
import com.barati.razi.core.network.RequestDetailDto
import com.barati.razi.core.network.RequestEventDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.requests.RequestLogic
import com.barati.razi.core.training.TrainingLogic
import com.barati.razi.feature.training.Pill
import com.barati.razi.feature.training.TrainingColors
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** جزئیات درخواست: روند رسیدگی، یادداشت و پیوست؛ برای کارشناس دکمه‌های رسیدگی و برای درخواست‌کننده امتیاز و اعتراض. */
@Composable
fun RequestDetailScreen(container: AppContainer, id: String, onBack: () -> Unit) {
    val repo = container.serviceRequestRepository
    val loader = rememberLoader(id) { repo.detail(id) }
    val snackbar = remember { SnackbarHostState() }
    var current by remember(id) { mutableStateOf<RequestDetailDto?>(null) }
    var gone by remember(id) { mutableStateOf(false) }
    RaziScaffold(title = "درخواست پشتیبانی", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (gone) {
                MessageBox("درخواست به واحد دیگری ارجاع شد و از کارتابل شما خارج شد.", actionLabel = "بازگشت", onAction = onBack)
            } else {
                LoadContent(loader) { loaded ->
                    val d = current?.takeIf { it.id == loaded.id } ?: loaded
                    DetailBody(
                        container,
                        d,
                        onChanged = { current = it },
                        onGone = { gone = true },
                        onMessage = { msg -> snackbar.showSnackbar(msg) },
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

private enum class Ask { NONE, STATUS, ASSIGN, TRANSFER, REOPEN }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    container: AppContainer,
    d: RequestDetailDto,
    onChanged: (RequestDetailDto) -> Unit,
    onGone: () -> Unit,
    onMessage: suspend (String) -> Unit,
) {
    val repo = container.serviceRequestRepository
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(Ask.NONE) }
    var nextStatus by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<RequestAttachmentDto?>(null) }

    /** اجرای یک کار روی سرور؛ پاسخ موفق جایگزین داده صفحه می‌شود. */
    fun act(okMessage: String, block: suspend () -> Result<RequestDetailDto>) {
        busy = true
        scope.launch {
            when (val r = block()) {
                is Result.Ok -> {
                    onChanged(r.value)
                    ask = Ask.NONE
                    onMessage(okMessage)
                }
                is Result.Err -> onMessage(r.error.message)
            }
            busy = false
        }
    }

    val transitions = RequestLogic.transitions(d.actions)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(d)

        if (transitions.isNotEmpty() || "assign" in d.actions || "transfer" in d.actions) {
            SectionCard("رسیدگی") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transitions.forEach { s ->
                        val label = RequestLogic.transitionLabels.getValue(s)
                        val onClick = { nextStatus = s; ask = Ask.STATUS }
                        when (s) {
                            "rejected" -> OutlinedButton(onClick = onClick, enabled = !busy) {
                                Text(label, color = MaterialTheme.colorScheme.error)
                            }
                            "on_hold" -> OutlinedButton(onClick = onClick, enabled = !busy) { Text(label) }
                            else -> Button(onClick = onClick, enabled = !busy) { Text(label) }
                        }
                    }
                    if ("assign" in d.actions) {
                        OutlinedButton(onClick = { ask = Ask.ASSIGN }, enabled = !busy) {
                            Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                            Text(" ارجاع به کارشناس")
                        }
                    }
                    if ("transfer" in d.actions) {
                        OutlinedButton(onClick = { ask = Ask.TRANSFER }, enabled = !busy) {
                            Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
                            Text(" واحد دیگر")
                        }
                    }
                }
            }
        }

        if ("rate" in d.actions) RateCard(busy) { score, comment -> act("از نظر شما سپاسگزاریم") { repo.rate(d.id, score, comment) } }
        if ("reopen" in d.actions) {
            OutlinedButton(onClick = { ask = Ask.REOPEN }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("مشکل حل نشده است (اعتراض)")
            }
        }

        SectionCard("شرح درخواست") {
            Text(d.description, style = MaterialTheme.typography.bodyLarge)
            d.location?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(" $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
            d.answers.forEach { InfoRow(it.label, RequestLogic.displayValue(it.value)) }
        }

        d.resolution?.let {
            SectionCard("نتیجه رسیدگی") { Text(it, style = MaterialTheme.typography.bodyMedium, color = TrainingColors.green) }
        }
        d.satisfaction?.let { score ->
            SectionCard("نظر درخواست‌کننده") {
                Stars(score, size = 24)
                d.satisfactionComment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }

        SectionCard("مشخصات") {
            InfoRow("کد پیگیری", d.trackingCode)
            InfoRow("درخواست‌کننده", listOfNotNull(d.requester.fullName, d.requester.jobTitle).joinToString(" · "))
            InfoRow("واحد درخواست‌کننده", d.requesterUnitName)
            InfoRow("واحد مسئول", d.handlerUnitName)
            InfoRow("کارشناس", d.assignedTo?.fullName ?: "هنوز ارجاع نشده")
            InfoRow("ثبت", TrainingLogic.formatDateTime(d.createdAt))
            InfoRow("انجام", TrainingLogic.formatDateTime(d.doneAt))
            if (d.status == "done" && d.reopenUntil != null) {
                Text(
                    "اگر تا ${TrainingLogic.formatDateTime(d.reopenUntil)} اعتراضی ثبت نشود، درخواست خودکار بسته می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrainingColors.gray,
                )
            }
        }

        SectionCard("روند رسیدگی") {
            d.events.forEach { e -> EventRow(container, e) { viewing = it } }
            if ("comment" in d.actions || "attach" in d.actions) {
                Composer(d, busy, onComment = { text -> act("یادداشت ثبت شد") { repo.comment(d.id, text) } }, onUpload = { file ->
                    act("پیوست افزوده شد") { repo.attach(d.id, file.name, file.contentType, file.bytes) }
                }, onError = { scope.launch { onMessage(it) } })
            }
        }
    }

    when (ask) {
        Ask.STATUS -> StatusDialog(d, nextStatus, busy, onDismiss = { ask = Ask.NONE }) { note, resolution ->
            act("وضعیت به‌روز شد") { repo.changeStatus(d.id, nextStatus, note, resolution) }
        }
        Ask.ASSIGN -> AssignDialog(container, d, busy, onDismiss = { ask = Ask.NONE }) { userId ->
            act("ارجاع شد") { repo.assign(d.id, userId) }
        }
        Ask.TRANSFER -> TransferDialog(container, d, busy, onDismiss = { ask = Ask.NONE }) { unitId, note ->
            busy = true
            scope.launch {
                when (val r = repo.transfer(d.id, unitId, note)) {
                    is Result.Ok -> {
                        ask = Ask.NONE
                        val next = r.value
                        if (next == null) onGone() else onChanged(next)
                    }
                    is Result.Err -> onMessage(r.error.message)
                }
                busy = false
            }
        }
        Ask.REOPEN -> NoteDialog(
            title = "اعتراض به نتیجه",
            hint = "چه مشکلی هنوز باقی است؟",
            confirm = "ثبت اعتراض",
            busy = busy,
            onDismiss = { ask = Ask.NONE },
        ) { note -> act("درخواست دوباره باز شد") { repo.reopen(d.id, note) } }
        Ask.NONE -> Unit
    }
    viewing?.let { ImageViewer(container.trainingRepository.absoluteUrl(it.url)) { viewing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(d: RequestDetailDto) {
    val style = categoryStyle(d.category)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(style.tone.background)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryIcon(d.category, 48)
                Column(Modifier.weight(1f)) {
                    Text(d.typeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        d.trackingCode + " · " + RequestLogic.ago(d.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = TrainingColors.gray,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(d.status)
                PriorityPill(d.priority)
                d.handlerUnitName?.let { Pill(it, style.tone.color, Color.White) }
            }
            Text(RequestLogic.statusHint(d.status), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EventRow(container: AppContainer, e: RequestEventDto, onView: (RequestAttachmentDto) -> Unit) {
    val context = LocalContext.current
    val tone = when (e.kind) {
        "status", "rated", "reopened" -> statusTone(e.toStatus)
        "transfer" -> Tone(TrainingColors.amber, TrainingColors.amberBg)
        "created" -> Tone(TrainingColors.blue, TrainingColors.blueBg)
        else -> Tone(TrainingColors.gray, TrainingColors.grayBg)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimelineDot(tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(RequestLogic.eventTitle(e), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(e.actor?.fullName, TrainingLogic.formatDateTime(e.at)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TrainingColors.gray,
            )
            e.note?.let {
                if (e.kind == "comment") {
                    Text(
                        it,
                        Modifier.clip(RoundedCornerShape(12.dp)).background(TrainingColors.grayBg).padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            e.attachment?.let { a ->
                val url = container.trainingRepository.absoluteUrl(a.url)
                if (a.contentType.startsWith("image/")) {
                    AsyncImage(
                        model = url,
                        contentDescription = a.originalName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(140.dp, 100.dp).clip(RoundedCornerShape(12.dp)).clickable { onView(a) },
                    )
                } else {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(TrainingColors.redBg)
                            .clickable { openUrl(context, url) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = TrainingColors.red)
                        Text(
                            " " + a.originalName + " · " + TrainingLogic.formatSize(a.size),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // برنامه‌ای برای باز کردن پیوند نیست؛ کاری از دست اپ برنمی‌آید.
    }
}

@Composable
private fun ImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Composer(
    d: RequestDetailDto,
    busy: Boolean,
    onComment: (String) -> Unit,
    onUpload: (PreparedFile) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(d.events.size) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun upload(uri: Uri?) {
        if (uri == null) return
        preparing = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { prepareUpload(context, uri) }
            preparing = false
            if (file == null) onError("خواندن فایل ممکن نشد") else onUpload(file)
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { upload(it) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { upload(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) upload(Uri.fromFile(f))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if ("comment" in d.actions) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                placeholder = { Text("یادداشت…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                trailingIcon = {
                    IconButton(onClick = { if (text.isNotBlank()) onComment(text) }, enabled = !busy && text.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ارسال")
                    }
                },
            )
        }
        if ("attach" in d.actions) {
            Box {
                OutlinedButton(onClick = { menu = true }, enabled = !busy && !preparing) {
                    if (preparing || busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp))
                    }
                    Text(" افزودن عکس یا PDF")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("گرفتن عکس") },
                        leadingIcon = { Icon(Icons.Filled.CameraAlt, null) },
                        onClick = {
                            menu = false
                            val dir = File(context.cacheDir, "requests").apply { mkdirs() }
                            val f = File(dir, "camera-${System.currentTimeMillis()}.jpg")
                            cameraFile = f
                            takePhoto.launch(FileProvider.getUriForFile(context, context.packageName + ".files", f))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("انتخاب از گالری") },
                        leadingIcon = { Icon(Icons.Filled.Image, null) },
                        onClick = {
                            menu = false
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("فایل PDF") },
                        leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                        onClick = {
                            menu = false
                            pickPdf.launch("application/pdf")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RateCard(busy: Boolean, onRate: (Int, String?) -> Unit) {
    var score by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    SectionCard("از رسیدگی راضی بودید؟") {
        Text("با امتیاز دادن، درخواست بسته می‌شود.", style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Stars(score, onChange = { score = it }, size = 34) }
        if (score > 0) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(2000) },
                placeholder = { Text("توضیح (اختیاری)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onRate(score, comment) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ثبت امتیاز") }
        }
    }
}

@Composable
private fun StatusDialog(
    d: RequestDetailDto,
    next: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (note: String?, resolution: String?) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val label = RequestLogic.transitionLabels.getValue(next)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (next == "done") {
                    OutlinedTextField(
                        value = resolution,
                        onValueChange = { resolution = it.take(4000) },
                        label = { Text("چه کاری انجام شد؟ *") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(2000) },
                    label = {
                        Text(
                            when (next) {
                                "rejected" -> "دلیل رد *"
                                "on_hold" -> "منتظر چه هستید؟ (مثلاً قطعه)"
                                else -> "توضیح (اختیاری)"
                            },
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (next == "accepted" && d.assignedTo == null) {
                    Text("درخواست به نام شما ارجاع می‌شود.", style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray)
                }
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                error = when {
                    next == "done" && resolution.isBlank() -> "نتیجه رسیدگی را بنویسید"
                    next == "rejected" && note.isBlank() -> "دلیل رد را بنویسید"
                    else -> null
                }
                if (error == null) onConfirm(note, resolution)
            }) { Text(label) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun AssignDialog(container: AppContainer, d: RequestDetailDto, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    val loader = rememberLoader(d.id) { container.serviceRequestRepository.agents(d.id) }
    var selected by remember { mutableStateOf(d.assignedTo?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ارجاع به کارشناس") },
        text = {
            LoadContent(loader, Modifier.heightIn(max = 160.dp)) { agents: List<PersonDto> ->
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (listOf<PersonDto?>(null) + agents).forEach { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected == a?.id, onClick = { selected = a?.id }, role = Role.RadioButton)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == a?.id, onClick = null)
                            Text(
                                a?.let { listOfNotNull(it.fullName, it.jobTitle).joinToString(" · ") } ?: "بدون کارشناس",
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onConfirm(selected) }) { Text("ارجاع") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun TransferDialog(
    container: AppContainer,
    d: RequestDetailDto,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (unitId: String, note: String) -> Unit,
) {
    // واحدهای پشتیبانی از روی انواع درخواست فعال به دست می‌آیند.
    val loader = rememberLoader(d.id) {
        when (val r = container.serviceRequestRepository.types()) {
            is Result.Ok -> Result.Ok(
                r.value.map { it.handlerUnitId to (it.handlerUnitName ?: "") }.distinct().filter { it.first != d.handlerUnitId },
            )
            is Result.Err -> r
        }
    }
    var unit by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ارجاع به واحد دیگر") },
        text = {
            LoadContent(loader, Modifier.heightIn(max = 160.dp)) { units: List<Pair<String, String>> ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (units.isEmpty()) Text("واحد پشتیبانی دیگری تعریف نشده است.")
                    units.forEach { (uid, name) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = unit == uid, onClick = { unit = uid }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = unit == uid, onClick = null)
                            Text(name, Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(2000) },
                        label = { Text("دلیل ارجاع *") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && unit != null && note.isNotBlank(), onClick = { unit?.let { onConfirm(it, note) } }) {
                Text("ارجاع")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun NoteDialog(title: String, hint: String, confirm: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(2000) },
                label = { Text(hint) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = !busy && note.isNotBlank(), onClick = { onConfirm(note) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

