package com.barati.razi.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.network.ChatPersonDto
import com.barati.razi.core.network.ConversationDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.feature.training.Pill
import com.barati.razi.feature.training.TrainingColors
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** جستجوی همکار با نام یا کد پرسنلی؛ نتیجه پس از توقف تایپ گرفته می‌شود. */
@Composable
private fun PeopleSearch(
    container: AppContainer,
    selected: Set<String>?,
    exclude: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    onPick: (ChatPersonDto) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChatPersonDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        val text = q.trim()
        if (text.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        searching = true
        when (val r = container.chatRepository.people(text)) {
            is Result.Ok -> {
                results = r.value
                error = null
            }
            is Result.Err -> error = r.error.message
        }
        searching = false
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it.take(100) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("نام یا کد پرسنلی همکار") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        val shown = results.filter { it.id !in exclude }
        if (q.isNotBlank() && !searching && shown.isEmpty() && error == null) {
            Text("همکاری با این مشخصات پیدا نشد.", style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray)
        }
        shown.forEach { p ->
            PersonRow(p, checked = selected?.contains(p.id)) { onPick(p) }
        }
    }
}

@Composable
private fun PersonRow(p: ChatPersonDto, checked: Boolean?, trailing: @Composable () -> Unit = {}, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NameAvatar(p.fullName, 40)
        Column(Modifier.weight(1f)) {
            Text(p.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(p.jobTitle, p.unitName).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (checked != null) Checkbox(checked = checked, onCheckedChange = { onClick?.invoke() })
        trailing()
    }
}

/** گفتگوی تازه: انتخاب یک همکار، یا ساخت گروه برای کسی که اجازه دارد. */
@Composable
fun NewChatScreen(container: AppContainer, canCreateGroup: Boolean, onBack: () -> Unit, onOpened: (String) -> Unit, onNewGroup: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    RaziScaffold(title = "گفتگوی تازه", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canCreateGroup) {
                    Card(
                        Modifier.fillMaxWidth().clickable(onClick = onNewGroup),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.GroupAdd, null, tint = MaterialTheme.colorScheme.primary)
                            Text("گروه تازه", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                PeopleSearch(container, selected = null) { p ->
                    if (busy) return@PeopleSearch
                    busy = true
                    scope.launch {
                        when (val r = container.chatRepository.openDirect(p.id)) {
                            is Result.Ok -> onOpened(r.value.id)
                            is Result.Err -> snackbar.showSnackbar(r.error.message)
                        }
                        busy = false
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedChips(selected: List<ChatPersonDto>, onRemove: (ChatPersonDto) -> Unit) {
    if (selected.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        selected.forEach { p ->
            InputChip(
                selected = true,
                onClick = { onRemove(p) },
                label = { Text(p.fullName) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "برداشتن", Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
fun NewGroupScreen(container: AppContainer, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<ChatPersonDto>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    RaziScaffold(title = "گروه تازه", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text("نام گروه *") },
                placeholder = { Text("مثلاً شیفت شب اورژانس") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                toPersianDigits("اعضا (${selected.size} نفر، به‌علاوه خودتان)"),
                style = MaterialTheme.typography.labelLarge,
                color = TrainingColors.gray,
            )
            SelectedChips(selected) { p -> selected.removeAll { it.id == p.id } }
            ErrorText(error)
            PrimaryButton("ساختن گروه", loading = busy, onClick = {
                if (title.isBlank()) {
                    error = "نام گروه را بنویسید"
                    return@PrimaryButton
                }
                busy = true
                error = null
                scope.launch {
                    when (val r = container.chatRepository.createGroup(title, selected.map { it.id })) {
                        is Result.Ok -> onCreated(r.value.id)
                        is Result.Err -> error = r.error.message
                    }
                    busy = false
                }
            })
            HorizontalDivider()
            PeopleSearch(container, selected = selected.map { it.id }.toSet()) { p ->
                if (selected.any { it.id == p.id }) selected.removeAll { it.id == p.id } else selected.add(p)
            }
        }
    }
}

/** مشخصات گفتگو: بی‌صدا کردن، اعضا، و برای مدیر گروه تغییر نام و افزودن یا حذف عضو. */
@Composable
fun ChatInfoScreen(
    container: AppContainer,
    myId: String,
    id: String,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val repo = container.chatRepository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val loader = rememberLoader(id) { repo.conversation(id) }
    var current by remember(id) { mutableStateOf<ConversationDto?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    fun update(block: suspend () -> Result<ConversationDto>) {
        scope.launch {
            when (val r = block()) {
                is Result.Ok -> current = r.value
                is Result.Err -> snackbar.showSnackbar(r.error.message)
            }
        }
    }

    fun openDirect(userId: String) {
        scope.launch {
            when (val r = repo.openDirect(userId)) {
                is Result.Ok -> onOpenChat(r.value.id)
                is Result.Err -> snackbar.showSnackbar(r.error.message)
            }
        }
    }

    RaziScaffold(title = "اطلاعات گفتگو", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(loader) { loaded ->
                val c = current?.takeIf { it.id == loaded.id } ?: loaded
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ConversationAvatar(c, 72)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                if (c.canManage) {
                                    IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "تغییر نام") }
                                }
                            }
                            val kind = when (c.kind) {
                                "unit" -> "کانال اطلاع‌رسانی واحد؛ فقط مسئول واحد می‌نویسد"
                                "group" -> toPersianDigits("گروه · ${c.memberCount} عضو")
                                else -> listOfNotNull(c.peer?.jobTitle, c.peer?.unitName).joinToString(" · ")
                            }
                            if (kind.isNotEmpty()) Text(kind, style = MaterialTheme.typography.bodyMedium, color = TrainingColors.gray, textAlign = TextAlign.Center)
                            c.peer?.let { Text(toPersianDigits("کد پرسنلی " + it.personnelCode), style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray) }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("بی‌صدا", style = MaterialTheme.typography.bodyLarge)
                                    Text("پیام‌های این گفتگو اعلان نمی‌دهند", style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray)
                                }
                                Switch(checked = c.muted, onCheckedChange = { muted ->
                                    current = c.copy(muted = muted)
                                    scope.launch {
                                        val r = repo.mute(c.id, muted)
                                        if (r is Result.Err) {
                                            current = c
                                            snackbar.showSnackbar(r.error.message)
                                        }
                                    }
                                })
                            }
                        }
                    }
                    if (c.kind != "direct") {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    toPersianDigits("اعضا (${c.members.size})"),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (c.canManage) {
                                    TextButton(onClick = { adding = true }) {
                                        Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                                        Text(" افزودن")
                                    }
                                }
                            }
                        }
                        items(c.members, key = { it.id }) { m ->
                            val person = ChatPersonDto(m.id, m.fullName, m.personnelCode, m.jobTitle, m.unitName)
                            PersonRow(
                                person,
                                checked = null,
                                trailing = {
                                    if (m.isAdmin) Pill("مدیر", TrainingColors.green, TrainingColors.greenBg)
                                    if (c.canManage && m.id != myId) {
                                        IconButton(onClick = { update { repo.removeMember(c.id, m.id).then { repo.conversation(c.id) } } }) {
                                            Icon(Icons.Filled.PersonRemove, contentDescription = "حذف از گروه", tint = TrainingColors.red)
                                        }
                                    }
                                },
                                onClick = if (m.id == myId) null else ({ openDirect(m.id) }),
                            )
                        }
                    }
                    if (c.kind == "group") {
                        item {
                            OutlinedButton(onClick = { leaving = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                                Text(" خروج از گروه", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (renaming) {
                    var title by remember { mutableStateOf(c.title) }
                    AlertDialog(
                        onDismissRequest = { renaming = false },
                        title = { Text("نام گروه") },
                        text = { OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, singleLine = true) },
                        confirmButton = {
                            TextButton(enabled = title.isNotBlank(), onClick = {
                                renaming = false
                                update { repo.rename(c.id, title) }
                            }) { Text("ذخیره") }
                        },
                        dismissButton = { TextButton(onClick = { renaming = false }) { Text("انصراف") } },
                    )
                }
                if (adding) {
                    val picked = remember { mutableStateListOf<ChatPersonDto>() }
                    AlertDialog(
                        onDismissRequest = { adding = false },
                        title = { Text("افزودن عضو") },
                        text = {
                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectedChips(picked) { p -> picked.removeAll { it.id == p.id } }
                                PeopleSearch(container, selected = picked.map { it.id }.toSet(), exclude = c.members.map { it.id }.toSet()) { p ->
                                    if (picked.any { it.id == p.id }) picked.removeAll { it.id == p.id } else picked.add(p)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = picked.isNotEmpty(), onClick = {
                                adding = false
                                val ids = picked.map { it.id }
                                update { repo.addMembers(c.id, ids) }
                            }) { Text("افزودن") }
                        },
                        dismissButton = { TextButton(onClick = { adding = false }) { Text("انصراف") } },
                    )
                }
                if (leaving) {
                    AlertDialog(
                        onDismissRequest = { leaving = false },
                        title = { Text("خروج از گروه") },
                        text = { Text("دیگر پیام‌های این گروه را نمی‌بینید مگر مدیر گروه دوباره شما را اضافه کند.") },
                        confirmButton = {
                            TextButton(onClick = {
                                leaving = false
                                scope.launch {
                                    when (val r = repo.removeMember(c.id, myId)) {
                                        is Result.Ok -> onLeft()
                                        is Result.Err -> snackbar.showSnackbar(r.error.message)
                                    }
                                }
                            }) { Text("خروج", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { leaving = false }) { Text("انصراف") } },
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** اجرای کار دوم فقط اگر کار اول موفق بود. */
private suspend fun <T> Result<Unit>.then(next: suspend () -> Result<T>): Result<T> = when (this) {
    is Result.Ok -> next()
    is Result.Err -> this
}
