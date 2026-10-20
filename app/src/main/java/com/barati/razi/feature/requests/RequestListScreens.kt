package com.barati.razi.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.data.PendingServiceRequest
import com.barati.razi.core.data.can
import com.barati.razi.core.network.MeDto
import com.barati.razi.core.network.RequestCreateRequest
import com.barati.razi.core.network.RequestPageDto
import com.barati.razi.core.network.RequestTypeDto
import com.barati.razi.core.requests.RequestLogic
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.feature.training.EmptyState
import com.barati.razi.feature.training.TrainingColors
import com.barati.razi.feature.training.rememberRefreshing
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.work.RequestUploadWorker
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** درخواست‌های من و (برای کارشناس یا مسئول واحد) کارتابل. */
@Composable
fun RequestsHomeScreen(container: AppContainer, me: MeDto, onBack: () -> Unit, onNew: () -> Unit, onOpen: (String) -> Unit) {
    val hasQueue = me.can("request.handle") || me.can("request.read")
    val canCreate = me.can("request.create")
    // کارشناس معمولاً برای کارتابل می‌آید؛ بقیه برای درخواست‌های خودشان.
    var tab by rememberSaveable { mutableIntStateOf(if (me.can("request.handle")) 1 else 0) }
    RaziScaffold(
        title = "درخواست پشتیبانی",
        onBack = onBack,
        floatingActionButton = {
            if (canCreate) {
                ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("درخواست جدید") })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (hasQueue && canCreate) {
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(tab == 0, onClick = { tab = 0 }, text = { Text("درخواست‌های من") })
                    Tab(tab == 1, onClick = { tab = 1 }, text = { Text(if (me.can("request.handle")) "کارتابل واحد" else "درخواست‌های واحد") })
                }
            }
            if (hasQueue && (tab == 1 || !canCreate)) {
                QueueTab(container, handles = me.can("request.handle"), onOpen = onOpen)
            } else {
                MyRequestsTab(container, onOpen)
            }
        }
    }
}

@Composable
private fun MyRequestsTab(container: AppContainer, onOpen: (String) -> Unit) {
    val repo = container.serviceRequestRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by repo.pending.collectAsState(initial = emptyList())
    val loader = rememberRefreshing(pending.size) { repo.mine() }
    LoadContent(loader) { list ->
        if (list.isEmpty() && pending.isEmpty()) {
            EmptyState(
                Icons.Filled.SupportAgent,
                "درخواستی ثبت نکرده‌اید",
                "خرابی رایانه، پرینتر، دستگاه‌های پزشکی یا مشکل برق و تأسیسات را از «درخواست جدید» اعلام کنید.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pending, key = { "p" + it.request.clientUuid }) { p ->
                    PendingRequestCard(
                        p,
                        onRetry = {
                            scope.launch {
                                repo.retryFailed()
                                RequestUploadWorker.schedule(context.applicationContext)
                            }
                        },
                        onDiscard = { scope.launch { repo.discard(p.request.clientUuid) } },
                    )
                }
                items(list, key = { it.id }) { r -> RequestCard(r, showRequester = false) { onOpen(r.id) } }
            }
        }
    }
}

@Composable
private fun PendingRequestCard(p: PendingServiceRequest, onRetry: () -> Unit, onDiscard: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TrainingColors.amberBg),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.CloudOff, null, tint = TrainingColors.amber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.typeName, fontWeight = FontWeight.Bold)
                Text(p.request.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(
                    if (p.failedPermanently) "ارسال ناموفق بود" else "ذخیره شد؛ با وصل شدن اینترنت خودکار ارسال می‌شود",
                    style = MaterialTheme.typography.labelMedium,
                    color = TrainingColors.amber,
                )
                p.lastError?.takeIf { p.failedPermanently }?.let { ErrorText(it) }
                Row {
                    TextButton(onClick = onRetry) { Text("ارسال دوباره") }
                    if (confirm) {
                        TextButton(onClick = onDiscard) { Text("بله، حذف شود", color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = { confirm = true }) { Text("حذف") }
                    }
                }
            }
        }
    }
}

private enum class QueueFilter(val label: String, val status: String?, val assigned: String?) {
    OPEN("باز", "open", null),
    MINE("ارجاع به من", "open", "me"),
    UNASSIGNED("بدون کارشناس", "open", "none"),
    ALL("همه", null, null),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueTab(container: AppContainer, handles: Boolean, onOpen: (String) -> Unit) {
    val repo = container.serviceRequestRepository
    var filter by rememberSaveable { mutableStateOf(QueueFilter.OPEN) }
    val loader = rememberRefreshing(filter) { repo.queue(filter.status, filter.assigned) }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QueueFilter.entries.filter { handles || it.assigned == null }.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        LoadContent(loader) { page: RequestPageDto ->
            if (page.items.isEmpty()) {
                EmptyState(Icons.Filled.Inbox, "درخواستی نیست", if (filter == QueueFilter.ALL) "هنوز درخواستی ثبت نشده است." else "همه درخواست‌ها رسیدگی شده‌اند.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        val critical = page.items.count { it.priority == "critical" && it.status in RequestLogic.openStatuses }
                        Text(
                            toPersianDigits("${page.total} درخواست" + if (critical > 0) " · $critical بحرانی" else ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (critical > 0) TrainingColors.red else TrainingColors.gray,
                        )
                    }
                    items(page.items, key = { it.id }) { r -> RequestCard(r, showRequester = true) { onOpen(r.id) } }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ ثبت درخواست

private sealed interface NewResult {
    data class Sent(val id: String) : NewResult
    data object Queued : NewResult
    data class Failed(val message: String) : NewResult
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewRequestScreen(container: AppContainer, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val repo = container.serviceRequestRepository
    val loader = rememberLoader { repo.types() }
    var typeId by rememberSaveable { mutableStateOf<String?>(null) }
    RaziScaffold(title = "درخواست جدید", onBack = { if (typeId != null) typeId = null else onBack() }) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { types ->
            val type = types.firstOrNull { it.id == typeId }
            if (type == null) {
                TypePicker(types, Modifier.padding(padding)) { typeId = it.id }
            } else {
                RequestForm(container, type, Modifier.padding(padding), onChangeType = { typeId = null }, onBack = onBack, onCreated = onCreated)
            }
        }
    }
}

@Composable
private fun TypePicker(types: List<RequestTypeDto>, modifier: Modifier, onPick: (RequestTypeDto) -> Unit) {
    if (types.isEmpty()) {
        EmptyState(Icons.Filled.SupportAgent, "نوع درخواستی تعریف نشده", "مدیر سیستم باید انواع درخواست را در پنل مدیریت تعریف کند.", modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("موضوع درخواست را انتخاب کنید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        RequestLogic.categories.forEach { (category, label) ->
            val group = types.filter { it.category == category }
            if (group.isNotEmpty()) {
                item(key = "h-$category") {
                    Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = categoryStyle(category).tone.color)
                }
                items(group, key = { it.id }) { t ->
                    Card(
                        onClick = { onPick(t) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryIcon(t.category)
                            Column(Modifier.weight(1f)) {
                                Text(t.name, fontWeight = FontWeight.Bold)
                                Text(
                                    t.description?.takeIf { it.isNotBlank() } ?: ("رسیدگی: " + (t.handlerUnitName ?: "")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrainingColors.gray,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestForm(
    container: AppContainer,
    type: RequestTypeDto,
    modifier: Modifier,
    onChangeType: () -> Unit,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val repo = container.serviceRequestRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clientUuid = rememberSaveable { UUID.randomUUID().toString() }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("normal") }
    val answers = remember { mutableStateMapOf<String, String>() }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<NewResult?>(null) }

    fun submit() {
        val (clean, fieldErrors) = RequestLogic.validate(type.fields, answers)
        val all = fieldErrors.toMutableMap()
        if (description.trim().length < 3) all["description"] = "شرح مشکل را بنویسید"
        errors = all
        if (all.isNotEmpty()) return
        val request = RequestCreateRequest(
            clientUuid = clientUuid,
            typeId = type.id,
            description = description.trim(),
            location = location.trim().ifEmpty { null },
            priority = priority,
            answers = clean,
        )
        sending = true
        scope.launch {
            repo.enqueue(request, type.name)
            val flush = repo.flush()
            val id = flush.sent[clientUuid]
            val still = repo.pending.first().firstOrNull { it.request.clientUuid == clientUuid }
            result = when {
                id != null -> NewResult.Sent(id)
                still?.failedPermanently == true -> {
                    repo.discard(clientUuid)
                    NewResult.Failed(still.lastError ?: "ارسال ناموفق بود")
                }
                else -> {
                    RequestUploadWorker.schedule(context.applicationContext)
                    NewResult.Queued
                }
            }
            sending = false
            (result as? NewResult.Sent)?.let { onCreated(it.id) }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryIcon(type.category)
                Column(Modifier.weight(1f)) {
                    Text(type.name, fontWeight = FontWeight.Bold)
                    Text("رسیدگی: " + (type.handlerUnitName ?: ""), style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray)
                }
                TextButton(onClick = onChangeType) { Text("تغییر") }
            }
            type.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(4000) },
            label = { Text("شرح مشکل *") },
            placeholder = { Text("مثلاً: پرینتر کاغذ را گیر می‌اندازد") },
            isError = "description" in errors,
            supportingText = if ("description" in errors) { { Text(errors.getValue("description")) } } else null,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it.take(300) },
            label = { Text("محل (بخش، اتاق، تخت)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        type.fields.forEach { f ->
            val label = if (f.required) "${f.label} *" else f.label
            when (f.kind) {
                "select" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        f.options.forEach { o ->
                            FilterChip(selected = answers[f.id] == o, onClick = { answers[f.id] = o }, label = { Text(o) })
                        }
                    }
                    errors[f.id]?.let { ErrorText(it) }
                }
                else -> OutlinedTextField(
                    value = answers[f.id].orEmpty(),
                    onValueChange = { answers[f.id] = it.take(1000) },
                    label = { Text(label) },
                    isError = f.id in errors,
                    supportingText = if (f.id in errors) { { Text(errors.getValue(f.id)) } } else null,
                    singleLine = true,
                    keyboardOptions = if (f.kind == "number") KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text("فوریت", style = MaterialTheme.typography.titleSmall)
        val keys = RequestLogic.priorities.keys.toList()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            keys.forEachIndexed { i, key ->
                val tone = priorityTone(key)
                SegmentedButton(
                    selected = priority == key,
                    onClick = { priority = key },
                    shape = SegmentedButtonDefaults.itemShape(i, keys.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = tone.background, activeContentColor = tone.color),
                ) { Text(RequestLogic.priorities.getValue(key)) }
            }
        }
        if (priority == "critical") {
            Text(
                "«بحرانی» فقط برای وقتی است که مراقبت از بیمار مختل شده است؛ در موارد فوری با داخلی واحد مسئول هم تماس بگیرید.",
                style = MaterialTheme.typography.bodySmall,
                color = TrainingColors.red,
            )
        }
        Text(
            "پس از ثبت می‌توانید عکس یا فایل PDF هم به درخواست پیوست کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = TrainingColors.gray,
        )
        PrimaryButton("ثبت درخواست", onClick = ::submit, loading = sending)
    }

    when (val r = result) {
        NewResult.Queued -> AlertDialog(
            onDismissRequest = onBack,
            icon = { Icon(Icons.Filled.CloudOff, null) },
            title = { Text("درخواست ذخیره شد") },
            text = { Text("اتصال به سرور برقرار نشد. درخواست روی گوشی ذخیره شد و با وصل شدن اینترنت خودکار ارسال می‌شود.") },
            confirmButton = { TextButton(onClick = onBack) { Text("باشه") } },
        )
        is NewResult.Failed -> AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("ارسال ناموفق بود") },
            text = { Text(r.message) },
            confirmButton = { TextButton(onClick = { result = null }) { Text("بستن") } },
        )
        else -> Unit
    }
}
