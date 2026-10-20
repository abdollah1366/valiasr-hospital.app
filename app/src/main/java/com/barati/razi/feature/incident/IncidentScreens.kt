package com.barati.razi.feature.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barati.razi.AppContainer
import com.barati.razi.core.data.LocalAnonymousReport
import com.barati.razi.core.data.PendingIncident
import com.barati.razi.core.form.Answer
import com.barati.razi.core.form.AnswerRow
import com.barati.razi.core.form.FormSchema
import com.barati.razi.core.form.answerRows
import com.barati.razi.core.network.IncidentSummaryDto
import com.barati.razi.core.network.Labels
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.StatusChangeDto
import com.barati.razi.core.network.TrackResponse
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.DynamicField
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.FieldLabel
import com.barati.razi.ui.components.JalaliDateField
import com.barati.razi.ui.components.Load
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.LoadingBox
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.SelectField
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.work.IncidentUploadWorker
import kotlinx.coroutines.launch

@Composable
fun IncidentFormScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: IncidentFormViewModel = viewModel {
        IncidentFormViewModel(container.incidentRepository, onQueued = { IncidentUploadWorker.schedule(context.applicationContext) })
    }
    val s by vm.state.collectAsState()
    RaziScaffold(title = "ثبت گزارش خطا و حادثه", onBack = onBack) { padding ->
        when {
            s.loading -> LoadingBox(Modifier.padding(padding))
            s.result is SubmitResult.Sent || s.result is SubmitResult.Queued ->
                ResultView(s.result!!, Modifier.padding(padding), onDone)
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                val total = s.reviewPage
                LinearProgressIndicator(progress = { s.page.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Column(
                    Modifier.weight(1f).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        s.page == 0 -> BasicsPage(s, vm)
                        s.page <= s.sections.size -> {
                            val section = s.sections[s.page - 1]
                            Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            section.fields.forEach { f ->
                                DynamicField(f, s.answers[f.id], s.errors[f.id]) { vm.setAnswer(f.id, it) }
                            }
                        }
                        else -> ReviewPage(s, vm)
                    }
                    (s.result as? SubmitResult.Failed)?.let { ErrorText(it.message) }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.page > 0) {
                        OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) { Text("قبلی") }
                    }
                    Column(Modifier.weight(1f)) {
                        if (s.page < s.reviewPage) {
                            PrimaryButton("بعدی", onClick = vm::next)
                        } else {
                            PrimaryButton("ارسال گزارش", onClick = vm::submit, loading = s.submitting)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicsPage(s: IncidentFormState, vm: IncidentFormViewModel) {
    Text("اطلاعات پایه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    FieldLabel("نوع رویداد", true)
    Labels.incidentKinds.forEach { (k, label) ->
        Row(
            Modifier.fillMaxWidth().selectable(selected = s.kind == k, role = Role.RadioButton, onClick = { vm.setKind(k) })
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = s.kind == k, onClick = null)
            Text(label, Modifier.padding(start = 8.dp))
        }
    }
    ErrorText(s.errors["kind"])
    Spacer(Modifier.height(8.dp))
    SelectField("بخش محل وقوع *", s.unit, s.units, { it.name }, vm::setUnit)
    ErrorText(s.errors["unit"])
    if (s.unitsError != null) {
        ErrorText("فهرست بخش‌ها دریافت نشد: ${s.unitsError}")
        TextButton(onClick = { vm.reloadUnits() }) { Text("تلاش دوباره") }
    }
    FieldLabel("تاریخ وقوع", true)
    JalaliDateField(s.occurredOn, vm::setDate)
    ErrorText(s.errors["date"])
    FieldLabel("شیفت", false)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Labels.shifts.forEach { (k, label) ->
            FilterChip(selected = s.shift == k, onClick = { vm.setShift(k) }, label = { Text(label) })
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ارسال ناشناس", fontWeight = FontWeight.Bold)
                Text(
                    "نام شما در گزارش ذخیره نمی‌شود. پیگیری فقط با کد و رمزی ممکن است که پس از ارسال نمایش داده می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = s.anonymous, onCheckedChange = vm::setAnonymous)
        }
    }
}

@Composable
private fun ReviewPage(s: IncidentFormState, vm: IncidentFormViewModel) {
    Text("مرور پیش از ارسال", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("نوع: " + (Labels.incidentKinds[s.kind] ?: ""))
            Text("بخش: " + (s.unit?.name ?: ""))
            Text("تاریخ: " + (s.occurredOn?.let { Jalali.formatLong(it) } ?: ""))
            Text("شیفت: " + (Labels.shifts[s.shift] ?: ""))
            Text(if (s.anonymous) "ارسال ناشناس" else "ارسال با نام")
            TextButton(onClick = { vm.goTo(0) }) { Text("ویرایش") }
        }
    }
    s.sections.forEachIndexed { index, section ->
        val filled = section.fields.mapNotNull { f ->
            when (val a = s.answers[f.id]) {
                is Answer.Text -> if (a.value.isBlank()) null else f to (if (f.type.name == "DATE") Jalali.formatIso(a.value) else a.value)
                is Answer.Choices -> if (a.values.isEmpty()) null else f to a.values.joinToString("، ")
                null -> null
            }
        }
        if (filled.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(section.title, fontWeight = FontWeight.Bold)
                    filled.forEach { (f, v) -> Text("${f.label}: $v", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { vm.goTo(index + 1) }) { Text("ویرایش") }
                }
            }
        }
    }
}

@Composable
private fun ResultView(result: SubmitResult, modifier: Modifier, onDone: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (result) {
            is SubmitResult.Sent -> {
                Text("گزارش ثبت شد. سپاس از شما.", style = MaterialTheme.typography.titleLarge)
                Text("کد پیگیری:")
                Text(result.trackingCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                result.secret?.let { SecretNote(it) }
            }
            is SubmitResult.Queued -> {
                Text("اتصال به سرور برقرار نشد.", style = MaterialTheme.typography.titleLarge)
                Text("گزارش روی گوشی ذخیره شد و به‌محض وصل شدن اینترنت خودکار ارسال می‌شود. وضعیت آن را در «گزارش‌های من» ببینید.")
                result.secret?.let { SecretNote(it) }
            }
            is SubmitResult.Failed -> Text(result.message)
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("بازگشت", onClick = onDone)
    }
}

@Composable
private fun SecretNote(secret: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("رمز پیگیری گزارش ناشناس:", fontWeight = FontWeight.Bold)
            Text(secret, style = MaterialTheme.typography.titleMedium)
            Text(
                "کد و رمز روی همین گوشی در «گزارش‌های من» نگه داشته می‌شود. اگر می‌خواهید از جای دیگری پیگیری کنید، آن‌ها را یادداشت کنید. بیمارستان این رمز را ندارد و قابل بازیابی نیست.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun MyIncidentsScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit, onTrack: (String, String) -> Unit) {
    val repo = container.incidentRepository
    val loader = rememberLoader { repo.myIncidents() }
    val pending by repo.pending.collectAsState(initial = emptyList())
    val anonymous by repo.localAnonymous.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    RaziScaffold(title = "گزارش‌های من", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pending.isNotEmpty()) {
                item { SectionTitle("در انتظار ارسال") }
                items(pending, key = { "p" + it.request.clientUuid }) { p ->
                    PendingCard(
                        p,
                        onRetry = {
                            scope.launch {
                                repo.retryFailed()
                                IncidentUploadWorker.schedule(context.applicationContext)
                            }
                        },
                        onDiscard = { scope.launch { repo.discard(p.request.clientUuid) } },
                    )
                }
            }
            if (anonymous.isNotEmpty()) {
                item { SectionTitle("گزارش‌های ناشناس از این گوشی") }
                items(anonymous, key = { "a" + it.trackingCode }) { a -> AnonymousCard(a) { onTrack(a.trackingCode, a.secret) } }
            }
            item { SectionTitle("گزارش‌های ثبت‌شده با نام") }
            when (val st = loader.state) {
                Load.Loading -> item { LoadingBox(Modifier.height(120.dp)) }
                is Load.Error -> item {
                    Column {
                        ErrorText(st.message)
                        TextButton(onClick = loader.reload) { Text("تلاش دوباره") }
                    }
                }
                is Load.Data -> if (st.value.isEmpty()) {
                    item { Text("گزارشی ثبت نکرده‌اید.") }
                } else {
                    items(st.value, key = { it.id }) { i -> IncidentCard(i) { onOpen(i.id) } }
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun IncidentCard(i: IncidentSummaryDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text((Labels.incidentKinds[i.kind] ?: i.kind) + if (i.isAnonymous) " (ناشناس)" else "", fontWeight = FontWeight.Bold)
            Text("کد: ${i.trackingCode}  ·  ${i.unitName.orEmpty()}")
            Text("تاریخ وقوع: " + Jalali.formatIso(i.occurredOn))
            StatusText(i.status)
        }
    }
}

@Composable
internal fun StatusText(status: String) {
    Text(
        "وضعیت: " + (Labels.incidentStatus[status] ?: status),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun PendingCard(p: PendingIncident, onRetry: () -> Unit, onDiscard: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Labels.incidentKinds[p.request.kind] ?: p.request.kind, fontWeight = FontWeight.Bold)
            Text("تاریخ وقوع: " + Jalali.formatIso(p.request.occurredOn))
            Text(if (p.failedPermanently) "ارسال ناموفق" else "منتظر اتصال به اینترنت")
            p.lastError?.let { ErrorText(it) }
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

@Composable
private fun AnonymousCard(a: LocalAnonymousReport, onTrack: () -> Unit) {
    Card(onClick = onTrack, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text((Labels.incidentKinds[a.kind] ?: a.kind) + " (ناشناس)", fontWeight = FontWeight.Bold)
            Text("کد: ${a.trackingCode}")
            Text("رمز: ${a.secret}", style = MaterialTheme.typography.bodySmall)
            Text("برای دیدن وضعیت لمس کنید", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun IncidentDetailScreen(container: AppContainer, id: String, onBack: () -> Unit) {
    val loader = rememberLoader(id) { container.incidentRepository.incident(id) }
    // قالب فرم برای نمایش برچسب فارسی پاسخ‌ها (از سرور یا نسخه ذخیره‌شده).
    val form by produceState<FormSchema?>(null) { value = container.incidentRepository.loadForm() }
    RaziScaffold(title = "جزئیات گزارش", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { d ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(Labels.incidentKinds[d.kind] ?: d.kind, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("کد پیگیری: ${d.trackingCode}")
                Text("بخش: ${d.unitName.orEmpty()}  ·  تاریخ وقوع: ${Jalali.formatIso(d.occurredOn)}")
                d.shift?.let { Text("شیفت: " + (Labels.shifts[it] ?: it)) }
                StatusText(d.status)
                d.assignedUnitName?.let { Text("ارجاع به: $it") }
                when {
                    d.isAnonymous -> Text("گزارش‌دهنده: ناشناس", style = MaterialTheme.typography.bodySmall)
                    d.reporterName != null -> Text("گزارش‌دهنده: ${d.reporterName}", style = MaterialTheme.typography.bodySmall)
                }
                d.sensitiveAnswers?.let { sensitive ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("اطلاعات هویتی بیمار (محرمانه)", fontWeight = FontWeight.Bold)
                            form?.let { f -> answerRows(f, sensitive).forEach { AnswerLine(it) } }
                        }
                    }
                }
                if (d.answers.isNotEmpty()) {
                    SectionTitle("شرح گزارش")
                    val f = form
                    if (f == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) else answerRows(f, d.answers).forEach { AnswerLine(it) }
                }
                History(d.history)
                if (d.actions.isNotEmpty() || d.canManage) SectionTitle("اقدامات اصلاحی")
                d.actions.forEach { a -> ActionCard(container, d.id, a, d.canManage, onChanged = loader.reload) }
                if (d.canManage) {
                    HorizontalDivider()
                    ManagePanel(container, d, onChanged = loader.reload)
                }
            }
        }
    }
}

@Composable
private fun AnswerLine(row: AnswerRow) {
    Column {
        Text(row.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(row.value)
    }
}

@Composable
internal fun History(history: List<StatusChangeDto>) {
    SectionTitle("روند رسیدگی")
    history.forEach { h ->
        Text("• " + (Labels.incidentStatus[h.toStatus] ?: h.toStatus) + " — " + Jalali.formatIso(h.at))
        h.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp)) }
    }
}

@Composable
fun TrackIncidentScreen(container: AppContainer, initialCode: String, initialSecret: String, onBack: () -> Unit) {
    var code by remember { mutableStateOf(initialCode) }
    var secret by remember { mutableStateOf(initialSecret) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<TrackResponse?>(null) }
    val scope = rememberCoroutineScope()
    fun track() {
        loading = true
        error = null
        scope.launch {
            when (val r = container.incidentRepository.track(code, secret)) {
                is Result.Ok -> result = r.value
                is Result.Err -> { result = null; error = r.error.message }
            }
            loading = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (initialCode.isNotEmpty() && initialSecret.isNotEmpty()) track() }
    RaziScaffold(title = "پیگیری گزارش ناشناس", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("کد پیگیری (IR-…)") }, singleLine = true)
            OutlinedTextField(secret, { secret = it }, Modifier.fillMaxWidth(), label = { Text("رمز پیگیری") }, singleLine = true)
            ErrorText(error)
            PrimaryButton("پیگیری", onClick = ::track, loading = loading, enabled = code.isNotBlank() && secret.length >= 8)
            result?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusText(r.status)
                        Text("ثبت: " + Jalali.formatIso(r.createdAt))
                        History(r.history)
                    }
                }
            }
            if (result == null && error == null && !loading) {
                Text(toPersianDigits("کد و رمز پس از ثبت گزارش ناشناس نمایش داده شده‌اند."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
