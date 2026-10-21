package com.barati.razi.feature.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.DoctorOwnReviewDto
import com.barati.razi.core.network.Result
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import kotlinx.coroutines.launch

/** نظرهای تاییدشده بیماران درباره پزشک واردشده، با امکان پاسخ (بخش کارکنان، فقط فارسی). */
@Composable
fun DoctorReviewsScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = container.appointmentRepository
    val loader = rememberLoader { repo.myReviews() }
    val scope = rememberCoroutineScope()
    var replying by remember { mutableStateOf<DoctorOwnReviewDto?>(null) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    RaziScaffold(title = "نظرات من", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { items ->
            if (items.isEmpty()) {
                MessageBox("هنوز نظر تاییدشده‌ای ندارید.", Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { ErrorText(error) }
                    items(items, key = { it.id }) { r ->
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StarRow(r.stars.toDouble())
                                Text(
                                    AppointmentLogic.dateTimeText(r.visitAt, AppLanguage.FA),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                r.comment?.let { Text(it) }
                                r.doctorReply?.let {
                                    Text("پاسخ شما: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                                OutlinedButton(onClick = { text = r.doctorReply.orEmpty(); replying = r }) {
                                    Text(if (r.doctorReply == null) "پاسخ" else "ویرایش پاسخ")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    replying?.let { r ->
        AlertDialog(
            onDismissRequest = { replying = null },
            title = { Text("پاسخ به نظر") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1500) text = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    replying = null
                    scope.launch {
                        when (val res = repo.reply(r.id, text)) {
                            is Result.Ok -> {
                                error = null
                                loader.reload()
                            }
                            is Result.Err -> error = res.error.message
                        }
                    }
                }) { Text("ثبت پاسخ") }
            },
            dismissButton = { TextButton(onClick = { replying = null }) { Text("انصراف") } },
        )
    }
}
