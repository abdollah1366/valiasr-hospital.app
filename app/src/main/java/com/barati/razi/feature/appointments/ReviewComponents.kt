package com.barati.razi.feature.appointments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.core.network.PublicReviewDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.rememberErrorText
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors
import kotlinx.coroutines.launch
import java.util.Locale

fun ratingText(average: Double, language: AppLanguage): String {
    val s = String.format(Locale.US, "%.1f", average)
    return if (language == AppLanguage.FA) toPersianDigits(s) else s
}

private fun countText(n: Int, language: AppLanguage) = if (language == AppLanguage.FA) toPersianDigits(n.toString()) else n.toString()

/** پنج ستاره فقط‌خواندنی؛ [value] هر عددی از ۰ تا ۵ (گرد می‌شود). */
@Composable
fun StarRow(value: Double, size: Dp = 16.dp) {
    val full = Math.round(value).toInt().coerceIn(0, 5)
    Row {
        repeat(5) { i ->
            Icon(
                if (i < full) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                Modifier.size(size),
                tint = ValiasrColors.GoldDeco,
            )
        }
    }
}

/** «۴٫۵ ★ (۱۲ نظر)»؛ اگر هنوز نظری نیست چیزی نمی‌کشد. */
@Composable
fun RatingSummary(average: Double?, count: Int) {
    if (average == null || count <= 0) return
    val language = LocalAppLanguage.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(16.dp), tint = ValiasrColors.GoldDeco)
        Text(ratingText(average, language), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.rating_count, countText(count, language)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StarPicker(value: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(5) { i ->
            Icon(
                if (i < value) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                Modifier.size(40.dp).clickable { onChange(i + 1) },
                tint = ValiasrColors.GoldDeco,
            )
        }
    }
}

/** پنجره امتیاز و نظر پس از ویزیت؛ ستاره الزامی و متن اختیاری است. */
@Composable
fun ReviewDialog(container: AppContainer, appointment: PatientAppointmentDto, onClose: (submitted: Boolean) -> Unit) {
    var stars by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var thanks by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val errorText = rememberErrorText()
    val thanksOnly = stringResource(R.string.review_thanks)
    val thanksPending = stringResource(R.string.review_thanks_pending)
    if (thanks != null) {
        AlertDialog(
            onDismissRequest = { onClose(true) },
            text = { Text(thanks!!) },
            confirmButton = { TextButton(onClick = { onClose(true) }) { Text(stringResource(R.string.review_ok)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose(false) },
        title = { Text(stringResource(R.string.review_title, appointment.doctorName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StarPicker(stars) { stars = it }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 1500) comment = it },
                    label = { Text(stringResource(R.string.review_comment_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.review_moderation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ErrorText(error)
            }
        },
        confirmButton = {
            PrimaryButtonSmall(stringResource(R.string.review_submit), enabled = stars > 0, loading = busy) {
                busy = true
                error = null
                scope.launch {
                    when (val r = container.appointmentRepository.review(appointment.id, stars, comment)) {
                        is Result.Ok -> thanks = if (r.value.status == "pending") thanksPending else thanksOnly
                        is Result.Err -> error = errorText(r.error)
                    }
                    busy = false
                }
            }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }, enabled = !busy) { Text(stringResource(R.string.later)) } },
    )
}

@Composable
private fun PrimaryButtonSmall(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled && !loading) { Text(text, fontWeight = FontWeight.Bold) }
}

/** نظرهای منتشرشده یک پزشک، همراه پاسخ پزشک و مدیریت. */
@Composable
fun ReviewList(items: List<PublicReviewDto>) {
    if (items.isEmpty()) return
    Text(stringResource(R.string.reviews_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    items.forEach { r ->
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StarRow(r.stars.toDouble())
                r.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                r.doctorReply?.let { ReplyBox(stringResource(R.string.review_doctor_reply), it) }
                r.adminReply?.let { ReplyBox(stringResource(R.string.review_admin_reply), it) }
            }
        }
    }
}

@Composable
private fun ReplyBox(label: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ValiasrColors.Link, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
