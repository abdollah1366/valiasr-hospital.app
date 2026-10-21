package com.barati.razi.feature.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.DoctorAppointmentDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import kotlinx.coroutines.launch
import java.time.LocalDate

private val STATUS_FA = mapOf("booked" to "در انتظار", "attended" to "مراجعه کرد", "no_show" to "نیامد", "cancelled" to "لغو شده")

/** فهرست بیماران روز پزشک (حساب کارکنانی که به پرونده پزشک وصل است)؛ ثبت آمد و نیامد. */
@Composable
fun DoctorPatientsScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = container.appointmentRepository
    val today = remember { java.time.Instant.now().atOffset(AppointmentLogic.ZONE).toLocalDate() }
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val day = LocalDate.parse(dayText)
    val loader = rememberLoader(dayText) { repo.doctorDay(dayText) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    RaziScaffold(title = "بیماران من", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dayText = day.minusDays(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "روز قبل")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(AppointmentLogic.dayText(day, AppLanguage.FA), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (day != today) {
                        TextButton(onClick = { dayText = today.toString() }) { Text("امروز") }
                    }
                }
                IconButton(onClick = { dayText = day.plusDays(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "روز بعد")
                }
            }
            ErrorText(error)
            LoadContent(loader) { items ->
                if (items.isEmpty()) {
                    MessageBox("برای این روز نوبتی ثبت نشده است.")
                } else {
                    PatientList(items, onStatus = { a, status ->
                        scope.launch {
                            when (val r = repo.setDoctorStatus(a.id, status)) {
                                is Result.Ok -> {
                                    error = null
                                    loader.reload()
                                }
                                is Result.Err -> error = r.error.message
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun PatientList(items: List<DoctorAppointmentDto>, onStatus: (DoctorAppointmentDto, String) -> Unit) {
    val active = items.count { it.status != "cancelled" }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                toPersianDigits("$active بیمار"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(items, key = { it.id }) { a -> PatientRow(a) { status -> onStatus(a, status) } }
    }
}

@Composable
private fun PatientRow(a: DoctorAppointmentDto, onStatus: (String) -> Unit) {
    val uri = LocalUriHandler.current
    val cancelled = a.status == "cancelled"
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cancelled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppointmentLogic.timeText(a.startsAt, AppLanguage.FA),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.patientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        toPersianDigits("کد ${a.trackingCode}") + if (a.source == "secretary") " · ثبت منشی" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(a.status, STATUS_FA[a.status] ?: a.status)
            }
            a.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (!cancelled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { runCatching { uri.openUri("tel:${a.patientMobile}") } }) {
                        Icon(Icons.Filled.Phone, contentDescription = "تماس با بیمار", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (a.status == "booked") {
                        FilledTonalButton(onClick = { onStatus("attended") }) { Text("مراجعه کرد") }
                        OutlinedButton(onClick = { onStatus("no_show") }) { Text("نیامد") }
                    } else {
                        TextButton(onClick = { onStatus("booked") }) { Text("برگرداندن به انتظار") }
                    }
                }
            }
        }
    }
}
