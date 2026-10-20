package com.barati.razi.feature.incident

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.network.ActionDto
import com.barati.razi.core.network.IncidentDetailDto
import com.barati.razi.core.network.Labels
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.UnitDto
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.JalaliDateField
import com.barati.razi.ui.components.Load
import com.barati.razi.ui.components.LoadingBox
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.SelectField
import com.barati.razi.ui.components.rememberLoader
import java.time.LocalDate
import kotlinx.coroutines.launch

/** فهرست گزارش‌ها برای کارشناس ایمنی یا مسئول واحد. سرور فقط گزارش‌های مجاز همین کاربر را برمی‌گرداند. */
@Composable
fun ReviewListScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    val loader = rememberLoader(status) { container.incidentRepository.reviewList(status) }
    RaziScaffold(title = "بررسی گزارش‌ها", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = status == null, onClick = { status = null }, label = { Text("همه") })
                Labels.incidentStatus.forEach { (code, label) ->
                    FilterChip(selected = status == code, onClick = { status = code }, label = { Text(label) })
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val st = loader.state) {
                    Load.Loading -> item { LoadingBox(Modifier.height(160.dp)) }
                    is Load.Error -> item {
                        Column {
                            ErrorText(st.message)
                            TextButton(onClick = loader.reload) { Text("تلاش دوباره") }
                        }
                    }
                    is Load.Data -> {
                        val page = st.value
                        item {
                            Text(
                                toPersianDigits("${page.total} گزارش") +
                                    if (page.total > page.items.size) toPersianDigits(" (${page.items.size} مورد آخر نمایش داده شده)") else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (page.items.isEmpty()) item { Text("گزارشی با این وضعیت نیست.") }
                        items(page.items, key = { it.id }) { i -> IncidentCard(i) { onOpen(i.id) } }
                    }
                }
            }
        }
    }
}

/** یک اقدام اصلاحی؛ برای کاربر مجاز امکان علامت‌زدن «انجام شد» دارد. */
@Composable
internal fun ActionCard(container: AppContainer, incidentId: String, a: ActionDto, canManage: Boolean, onChanged: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(a.description)
                val meta = listOfNotNull(
                    a.ownerName?.let { "مسئول: $it" },
                    a.dueDate?.let { "مهلت: " + Jalali.formatIso(it) },
                ).joinToString("  ·  ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
                Text(if (a.doneAt != null) "انجام شد" else "در حال انجام", style = MaterialTheme.typography.bodySmall)
                ErrorText(error)
            }
            if (canManage) {
                Checkbox(
                    checked = a.doneAt != null,
                    enabled = !busy,
                    onCheckedChange = { done ->
                        busy = true
                        error = null
                        scope.launch {
                            when (val r = container.incidentRepository.setActionDone(incidentId, a.id, done)) {
                                is Result.Ok -> onChanged()
                                is Result.Err -> error = r.error.message
                            }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

/**
 * ابزار مدیریت گزارش. وضعیت‌های مجاز بعدی را سرور در [IncidentDetailDto.nextStatuses] می‌فرستد؛
 * اپ قواعد گردش کار را تکرار نمی‌کند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManagePanel(container: AppContainer, d: IncidentDetailDto, onChanged: () -> Unit) {
    val repo = container.incidentRepository
    val scope = rememberCoroutineScope()
    var busy by remember(d.status) { mutableStateOf(false) }
    var error by remember(d.status) { mutableStateOf<String?>(null) }

    fun submit(block: suspend () -> Result<*>, onOk: () -> Unit = {}) {
        busy = true
        error = null
        scope.launch {
            when (val r = block()) {
                is Result.Ok<*> -> {
                    onOk()
                    onChanged()
                }
                is Result.Err -> error = r.error.message
            }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---------- تغییر وضعیت
        // در ابتدای کار، «ارجاع‌شده» از بخش ارجاع انجام می‌شود تا واحد مسئول هم ثبت شود.
        val statuses = d.nextStatuses.filterNot { it == "assigned" && d.status in setOf("submitted", "under_review") }
        if (statuses.isNotEmpty()) {
            SectionTitle("تغییر وضعیت")
            var target by remember(d.status) { mutableStateOf<String?>(null) }
            var note by remember(d.status) { mutableStateOf("") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                statuses.forEach { s ->
                    FilterChip(selected = target == s, onClick = { target = s }, label = { Text(Labels.incidentStatus[s] ?: s) })
                }
            }
            target?.let { t ->
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("یادداشت (اختیاری)") }, minLines = 2)
                if (t == "closed" || t == "rejected") {
                    Text(
                        if (t == "closed") "پس از بستن، گزارش دیگر قابل تغییر نیست." else "گزارش رد می‌شود؛ بعداً فقط می‌توان دوباره بررسی‌اش کرد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                PrimaryButton(
                    "ثبت «${Labels.incidentStatus[t] ?: t}»",
                    loading = busy,
                    onClick = { submit({ repo.changeStatus(d.id, t, note) }) },
                )
            }
        }

        // ---------- ارجاع به واحد
        if (d.status != "closed") {
            SectionTitle("ارجاع به واحد")
            val units by produceState<List<UnitDto>>(emptyList()) {
                value = (repo.units() as? Result.Ok)?.value.orEmpty()
            }
            var unit by remember(d.status) { mutableStateOf<UnitDto?>(null) }
            var note by remember(d.status) { mutableStateOf("") }
            SelectField("واحد مسئول پیگیری", unit, units, { it.name }, { unit = it })
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("توضیح ارجاع (اختیاری)") })
            OutlinedButton(
                onClick = { unit?.let { u -> submit({ repo.assign(d.id, u.id, note) }) } },
                enabled = unit != null && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ارجاع") }
        }

        // ---------- اقدام اصلاحی جدید
        if (d.status != "closed" && d.status != "rejected") {
            SectionTitle("افزودن اقدام اصلاحی")
            var description by remember { mutableStateOf("") }
            var owner by remember { mutableStateOf("") }
            var due by remember { mutableStateOf<LocalDate?>(null) }
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("شرح اقدام") }, minLines = 2)
            OutlinedTextField(owner, { owner = it }, Modifier.fillMaxWidth(), label = { Text("مسئول اجرا (اختیاری)") }, singleLine = true)
            Text("مهلت انجام (اختیاری)", style = MaterialTheme.typography.bodySmall)
            JalaliDateField(due, { due = it }, maxDate = LocalDate.now().plusYears(2))
            OutlinedButton(
                onClick = {
                    submit({ repo.addAction(d.id, description, owner, due?.toString()) }) {
                        description = ""
                        owner = ""
                        due = null
                    }
                },
                enabled = description.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("افزودن اقدام") }
        }

        ErrorText(error)
    }
}
