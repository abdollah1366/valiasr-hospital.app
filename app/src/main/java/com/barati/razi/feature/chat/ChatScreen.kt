package com.barati.razi.feature.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.barati.razi.AppContainer
import com.barati.razi.core.chat.ChatLogic
import com.barati.razi.core.chat.ChatRow
import com.barati.razi.core.data.FlushOutcome
import com.barati.razi.core.data.PendingChatMessage
import com.barati.razi.core.network.ChatMessageDto
import com.barati.razi.core.network.ConversationDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.training.TrainingLogic
import com.barati.razi.feature.requests.prepareUpload
import com.barati.razi.feature.training.TrainingColors
import com.barati.razi.ui.components.LoadingBox
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.work.ChatSendWorker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_FILE_BYTES = 15 * 1024 * 1024

/** ارسال صف در پس‌زمینه اپ (نه صفحه) تا با بستن صفحه قطع نشود؛ بدون اینترنت به WorkManager سپرده می‌شود. */
fun sendQueued(container: AppContainer, context: Context) {
    val app = context.applicationContext
    container.scope.launch {
        if (container.chatRepository.flush() == FlushOutcome.RETRY_LATER) ChatSendWorker.schedule(app)
    }
}

/** صفحه یک گفتگو: تا وقتی باز است هر ۳ ثانیه پیام‌های تازه، ویرایش‌ها و «دیده شد» گرفته می‌شود. */
@Composable
fun ChatScreen(container: AppContainer, myId: String, id: String, onBack: () -> Unit, onInfo: () -> Unit) {
    val repo = container.chatRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var conv by remember(id) { mutableStateOf<ConversationDto?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var messages by remember(id) { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var cursor by remember(id) { mutableStateOf<String?>(null) }
    var nextBefore by remember(id) { mutableStateOf<String?>(null) }
    var peerReadAt by remember(id) { mutableStateOf<String?>(null) }
    var loadingOlder by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val allPending by repo.pending.collectAsState(initial = emptyList())
    val pending = allPending.filter { it.conversationId == id }

    // بارگذاری اول، سپس پرسش دوره‌ای فقط وقتی صفحه جلوی چشم است
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(id, attempt) {
        when (val c = repo.conversation(id)) {
            is Result.Ok -> conv = c.value
            is Result.Err -> {
                error = c.error.message
                return@LaunchedEffect
            }
        }
        when (val r = repo.messages(id)) {
            is Result.Ok -> {
                messages = r.value.items
                cursor = r.value.cursor
                nextBefore = r.value.nextBefore
                peerReadAt = r.value.peerReadAt
                error = null
            }
            is Result.Err -> {
                error = r.error.message
                return@LaunchedEffect
            }
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repo.markRead(id)
            while (true) {
                delay(ChatLogic.POLL_MILLIS)
                val r = repo.messages(id, since = cursor)
                if (r is Result.Ok) {
                    val incoming = r.value.items
                    messages = ChatLogic.merge(messages, incoming)
                    cursor = r.value.cursor
                    peerReadAt = r.value.peerReadAt
                    if (incoming.any { it.senderId != myId }) repo.markRead(id)
                }
            }
        }
    }
    // پیامی که از صف این گفتگو رسید بی‌درنگ جای نسخه «در حال ارسال» را می‌گیرد
    val currentPending by rememberUpdatedState(pending)
    LaunchedEffect(id) {
        repo.delivered.collect { m ->
            if (currentPending.any { it.clientUuid == m.clientUuid }) messages = ChatLogic.merge(messages, listOf(m))
        }
    }

    var replyTo by remember(id) { mutableStateOf<ChatMessageDto?>(null) }
    var editing by remember(id) { mutableStateOf<ChatMessageDto?>(null) }
    var deleting by remember(id) { mutableStateOf<ChatMessageDto?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }

    val c = conv
    RaziScaffold(
        title = c?.title ?: "گفتگو",
        onBack = onBack,
        actions = {
            if (c != null) IconButton(onClick = onInfo) { Icon(Icons.Filled.Info, contentDescription = "اطلاعات گفتگو") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null -> MessageBox(error!!, actionLabel = "تلاش دوباره", onAction = {
                        error = null
                        conv = null
                        attempt++
                    })
                c == null -> LoadingBox()
                else -> Column(Modifier.fillMaxSize().imePadding()) {
                    val rows = ChatLogic.timeline(messages, pending, myId, peerReadAt.takeIf { c.kind == "direct" })
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(rows.asReversed(), key = { it.key }) { row ->
                            when (row) {
                                is ChatRow.Day -> DayChip(row.label)
                                is ChatRow.Sent -> MessageBubble(
                                    container,
                                    row,
                                    showSender = c.kind != "direct",
                                    onReply = { if (c.canPost) replyTo = it },
                                    onEdit = { editing = it },
                                    onDelete = { deleting = it },
                                    onImage = { viewing = it },
                                )
                                is ChatRow.Local -> PendingBubble(row.pending, onRetry = {
                                    scope.launch {
                                        repo.retry(row.pending.clientUuid)
                                        sendQueued(container, context)
                                    }
                                }, onDiscard = { scope.launch { repo.discard(row.pending.clientUuid) } })
                            }
                        }
                        val before = nextBefore
                        if (before != null) {
                            item(key = "older") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    OutlinedButton(enabled = !loadingOlder, onClick = {
                                        loadingOlder = true
                                        scope.launch {
                                            val r = repo.messages(id, before = before)
                                            if (r is Result.Ok) {
                                                messages = ChatLogic.merge(r.value.items, messages)
                                                nextBefore = r.value.nextBefore
                                            } else if (r is Result.Err) {
                                                snackbar.showSnackbar(r.error.message)
                                            }
                                            loadingOlder = false
                                        }
                                    }) { Text("پیام‌های قدیمی‌تر") }
                                }
                            }
                        }
                        item(key = "privacy") { PrivacyNote() }
                    }
                    HorizontalDivider()
                    if (c.canPost) {
                        Composer(
                            replyTo = replyTo,
                            editing = editing,
                            onCancel = {
                                replyTo = null
                                editing = null
                            },
                            onSend = { text ->
                                val target = editing
                                if (target != null) {
                                    editing = null
                                    scope.launch {
                                        when (val r = repo.edit(id, target.id, text)) {
                                            is Result.Ok -> messages = ChatLogic.merge(messages, listOf(r.value))
                                            is Result.Err -> snackbar.showSnackbar(r.error.message)
                                        }
                                    }
                                } else {
                                    val reply = replyTo?.id
                                    replyTo = null
                                    scope.launch {
                                        repo.enqueueText(id, text, reply)
                                        sendQueued(container, context)
                                    }
                                }
                            },
                            onFile = { name, type, bytes ->
                                val reply = replyTo?.id
                                replyTo = null
                                scope.launch {
                                    repo.enqueueFile(id, name, type, bytes, reply)
                                    sendQueued(container, context)
                                }
                            },
                            onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                        )
                    } else {
                        Text(
                            if (c.kind == "unit") "در کانال واحد فقط مسئول واحد پیام می‌فرستد." else "این همکار دیگر در سامانه فعال نیست.",
                            Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TrainingColors.gray,
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف پیام") },
            text = { Text("پیام برای همه اعضای گفتگو پاک می‌شود و به جای آن «این پیام حذف شد» نمایش داده می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        when (val r = repo.delete(id, m.id)) {
                            is Result.Ok -> messages = ChatLogic.merge(
                                messages,
                                listOf(m.copy(body = null, attachment = null, deleted = true, canEdit = false)),
                            )
                            is Result.Err -> snackbar.showSnackbar(r.error.message)
                        }
                    }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } },
        )
    }
    viewing?.let { url -> ImageViewer(url) { viewing = null } }
}

@Composable
private fun PrivacyNote() {
    Text(
        "اطلاعات هویتی بیمار را در گفتگو نفرستید. برای موارد فوری تماس بگیرید.",
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = TrainingColors.amber,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.background(TrainingColors.grayBg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TrainingColors.gray,
        )
    }
}

private val mineBg = Color(0xFFDDF1EC)
private val otherBg = Color(0xFFF1F3F4)

/** پیام خود کاربر در سمت شروع سطر (راست، در چیدمان فارسی) و پیام دیگران در سمت مقابل. */
@Composable
private fun BubbleFrame(mine: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd) {
        Surface(
            color = if (mine) mineBg else otherBg,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (mine) 4.dp else 16.dp,
                bottomEnd = if (mine) 16.dp else 4.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    container: AppContainer,
    row: ChatRow.Sent,
    showSender: Boolean,
    onReply: (ChatMessageDto) -> Unit,
    onEdit: (ChatMessageDto) -> Unit,
    onDelete: (ChatMessageDto) -> Unit,
    onImage: (String) -> Unit,
) {
    val m = row.message
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    BubbleFrame(row.mine) {
        Box {
            Column(
                Modifier
                    .combinedClickable(onClick = {}, onLongClick = { if (!m.deleted) menu = true })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showSender && !row.mine && m.senderName != null) {
                    Text(m.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                m.replyTo?.let { r ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(r.senderName ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            when {
                                r.deleted -> "این پیام حذف شد"
                                r.text != null -> r.text
                                r.hasAttachment -> "فایل پیوست"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    m.deleted -> Text(
                        "این پیام حذف شد",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = TrainingColors.gray,
                    )
                    m.attachment != null -> m.attachment?.let { a ->
                        val url = container.trainingRepository.absoluteUrl(a.url)
                        if (ChatLogic.isImage(a.contentType)) {
                            AsyncImage(
                                model = url,
                                contentDescription = a.originalName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(220.dp, 160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(onClick = { onImage(url) }, onLongClick = { menu = true }),
                            )
                        } else {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TrainingColors.redBg)
                                    .clickable { openUrl(context, url) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    else -> Text(m.body.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (m.edited && !m.deleted) Text("ویرایش‌شده", style = MaterialTheme.typography.labelSmall, color = TrainingColors.gray)
                    Text(ChatLogic.time(m.createdAt), style = MaterialTheme.typography.labelSmall, color = TrainingColors.gray)
                    if (row.mine) {
                        Icon(
                            if (row.seen) Icons.Filled.DoneAll else Icons.Filled.Check,
                            contentDescription = if (row.seen) "دیده شد" else "ارسال شد",
                            tint = if (row.seen) MaterialTheme.colorScheme.primary else TrainingColors.gray,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("پاسخ") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    onClick = {
                        menu = false
                        onReply(m)
                    },
                )
                if (m.body != null) {
                    DropdownMenuItem(
                        text = { Text("کپی متن") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = {
                            menu = false
                            clipboard.setText(AnnotatedString(m.body))
                        },
                    )
                }
                if (m.canEdit && m.body != null) {
                    DropdownMenuItem(
                        text = { Text("ویرایش") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menu = false
                            onEdit(m)
                        },
                    )
                }
                if (m.canEdit) {
                    DropdownMenuItem(
                        text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onDelete(m)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingBubble(p: PendingChatMessage, onRetry: () -> Unit, onDiscard: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    BubbleFrame(mine = true) {
        Box {
            Column(
                Modifier.clickable(enabled = p.failed) { menu = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (p.fileName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ChatLogic.isImage(p.contentType)) Icons.Filled.Image else Icons.Filled.PictureAsPdf,
                            null,
                            tint = TrainingColors.gray,
                        )
                        Text(" " + p.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(p.body.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (p.failed) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = TrainingColors.red, modifier = Modifier.size(14.dp))
                        Text(
                            "ارسال نشد" + (p.error?.let { ": $it" } ?: "") + " (لمس کنید)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrainingColors.red,
                        )
                    } else {
                        Icon(Icons.Filled.Schedule, null, tint = TrainingColors.gray, modifier = Modifier.size(14.dp))
                        Text("در حال ارسال", style = MaterialTheme.typography.labelSmall, color = TrainingColors.gray)
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("تلاش دوباره") }, onClick = {
                    menu = false
                    onRetry()
                })
                DropdownMenuItem(text = { Text("حذف از صف", color = MaterialTheme.colorScheme.error) }, onClick = {
                    menu = false
                    onDiscard()
                })
            }
        }
    }
}

@Composable
private fun Composer(
    replyTo: ChatMessageDto?,
    editing: ChatMessageDto?,
    onCancel: () -> Unit,
    onSend: (String) -> Unit,
    onFile: (name: String, type: String, bytes: ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(editing?.id) { editing?.body?.let { text = it } }

    fun upload(uri: Uri?) {
        if (uri == null) return
        preparing = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { prepareUpload(context, uri) }
            preparing = false
            when {
                file == null -> onError("خواندن فایل ممکن نشد")
                file.bytes.size > MAX_FILE_BYTES -> onError("حجم فایل بیشتر از ۱۵ مگابایت است")
                else -> onFile(file.name, file.contentType, file.bytes)
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { upload(it) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { upload(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) upload(Uri.fromFile(f))
    }

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        val banner = editing?.let { "ویرایش پیام" } ?: replyTo?.let { "پاسخ به " + (it.senderName ?: "") }
        if (banner != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (editing != null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(banner, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    (editing ?: replyTo)?.let { m ->
                        Text(
                            m.body ?: m.attachment?.originalName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = {
                    if (editing != null) text = ""
                    onCancel()
                }) { Icon(Icons.Filled.Close, contentDescription = "انصراف") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            if (editing == null) {
                Box {
                    IconButton(onClick = { menu = true }, enabled = !preparing) {
                        if (preparing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.AttachFile, contentDescription = "پیوست عکس یا PDF")
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("گرفتن عکس") },
                            leadingIcon = { Icon(Icons.Filled.CameraAlt, null) },
                            onClick = {
                                menu = false
                                val dir = File(context.cacheDir, "chat").apply { mkdirs() }
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ChatLogic.MAX_TEXT) },
                placeholder = { Text("پیام…") },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 160.dp),
                shape = RoundedCornerShape(24.dp),
            )
            IconButton(
                onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        onSend(t)
                        text = ""
                    }
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    if (editing != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (editing != null) "ذخیره ویرایش" else "ارسال",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else TrainingColors.gray,
                )
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // برنامه‌ای برای باز کردن پیوند نیست.
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
