package com.barati.razi.feature.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.network.ResultDto
import com.barati.razi.core.network.ResultItemDto
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors

/** فیلتر نمایش: همه، فقط آزمایش یا فقط تصویربرداری. */
fun filterResults(items: List<ResultDto>, kind: String?): List<ResultDto> =
    if (kind == null) items else items.filter { it.kind == kind }

/** جواب آزمایش و تصویربرداری مراجع؛ با ورود مراجع از سرور خوانده می‌شود. */
@Composable
fun ResultsTab(container: AppContainer, padding: PaddingValues, signedIn: Boolean, onSignIn: () -> Unit) {
    if (!signedIn) {
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Science, contentDescription = null, Modifier.size(64.dp), tint = ValiasrColors.Brand)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.results_signin_needed), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSignIn, Modifier.height(48.dp)) { Text(stringResource(R.string.signin_action)) }
        }
        return
    }
    val loader = rememberLoader { container.resultsRepository.results() }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    LoadContent(loader, Modifier.padding(padding)) { all ->
        val items = filterResults(all, kind)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == null, { kind = null }, { Text(stringResource(R.string.results_all)) })
                    FilterChip(kind == "lab", { kind = "lab" }, { Text(stringResource(R.string.results_lab)) })
                    FilterChip(kind == "imaging", { kind = "imaging" }, { Text(stringResource(R.string.results_imaging)) })
                }
            }
            if (items.isEmpty()) {
                item { Text(stringResource(R.string.results_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(items, key = { it.id }) { r ->
                ResultCard(r, expanded = openId == r.id, onToggle = { openId = if (openId == r.id) null else r.id })
            }
        }
    }
}

@Composable
private fun ResultCard(r: ResultDto, expanded: Boolean, onToggle: () -> Unit) {
    val language = LocalAppLanguage.current
    val ready = r.status == "ready"
    Card(
        Modifier.fillMaxWidth().clickable(enabled = ready, onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        AppointmentLogic.dateTimeText(r.performedAt, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!ready) {
                    Text(stringResource(R.string.results_pending), style = MaterialTheme.typography.labelMedium, color = ValiasrColors.Link)
                }
            }
            if (ready) {
                r.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (expanded) {
                    r.doctorName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    r.items.forEach { ItemRow(it) }
                    r.report?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(i: ResultItemDto) {
    val abnormal = i.flag == "high" || i.flag == "low"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(i.name, style = MaterialTheme.typography.bodyMedium)
            i.reference?.let {
                Text(
                    stringResource(R.string.results_reference, "⁦$it⁩"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "⁦${i.value}${i.unit?.let { " $it" }.orEmpty()}⁩" + when (i.flag) {
                "high" -> " ▲"
                "low" -> " ▼"
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (abnormal) FontWeight.Bold else FontWeight.Normal,
            color = if (abnormal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
