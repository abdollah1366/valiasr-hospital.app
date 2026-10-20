package com.barati.razi.feature.requests

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.barati.razi.core.network.RequestSummaryDto
import com.barati.razi.core.requests.RequestLogic
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.feature.training.IconCircle
import com.barati.razi.feature.training.Pill
import com.barati.razi.feature.training.TrainingColors
import java.io.ByteArrayOutputStream

data class Tone(val color: Color, val background: Color)

private val blue = Tone(TrainingColors.blue, TrainingColors.blueBg)
private val amber = Tone(TrainingColors.amber, TrainingColors.amberBg)
private val green = Tone(TrainingColors.green, TrainingColors.greenBg)
private val red = Tone(TrainingColors.red, TrainingColors.redBg)
private val gray = Tone(TrainingColors.gray, TrainingColors.grayBg)

fun statusTone(status: String?): Tone = when (status) {
    "submitted", "accepted" -> blue
    "in_progress" -> amber
    "done" -> green
    "rejected" -> red
    else -> gray
}

fun priorityTone(priority: String): Tone = when (priority) {
    "critical" -> red
    "urgent" -> amber
    else -> gray
}

data class CategoryStyle(val icon: ImageVector, val tone: Tone)

fun categoryStyle(category: String): CategoryStyle = when (category) {
    "it" -> CategoryStyle(Icons.Filled.Computer, blue)
    "equipment" -> CategoryStyle(Icons.Filled.MedicalServices, red)
    "facilities" -> CategoryStyle(Icons.Filled.Build, amber)
    else -> CategoryStyle(Icons.Filled.MoreHoriz, gray)
}

@Composable
fun StatusPill(status: String) {
    val t = statusTone(status)
    Pill(RequestLogic.statuses[status] ?: status, t.color, t.background)
}

@Composable
fun PriorityPill(priority: String) {
    val t = priorityTone(priority)
    Pill(RequestLogic.priorities[priority] ?: priority, t.color, t.background)
}

@Composable
fun CategoryIcon(category: String, size: Int = 44) {
    val s = categoryStyle(category)
    IconCircle(s.icon, s.tone.color, s.tone.background, size.dp)
}

/** کارت یک درخواست در فهرست «درخواست‌های من» یا کارتابل. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestCard(r: RequestSummaryDto, showRequester: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryIcon(r.category)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.typeName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(RequestLogic.ago(r.createdAt), style = MaterialTheme.typography.labelSmall, color = TrainingColors.gray)
                }
                Text(
                    r.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrainingColors.gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showRequester) {
                    Text(
                        listOfNotNull(r.requester.fullName, r.requesterUnitName).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(r.status)
                    if (r.priority != "normal") PriorityPill(r.priority)
                    if (showRequester && r.assignedTo != null) Pill(r.assignedTo.fullName, TrainingColors.gray, TrainingColors.grayBg)
                    Text(
                        r.trackingCode,
                        Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelSmall,
                        color = TrainingColors.gray,
                    )
                }
            }
        }
    }
}

/** ستاره‌های امتیاز ۱ تا ۵؛ اگر [onChange] باشد قابل لمس است. */
@Composable
fun Stars(value: Int, onChange: ((Int) -> Unit)? = null, size: Int = 32) {
    Row(horizontalArrangement = Arrangement.Center) {
        for (i in 1..5) {
            val icon = if (i <= value) Icons.Filled.Star else Icons.Filled.StarBorder
            val tint = if (i <= value) Color(0xFFF2A100) else Color(0xFFB8C0C8)
            if (onChange != null) {
                IconButton(onClick = { onChange(i) }) {
                    Icon(icon, contentDescription = toPersianDigits("$i ستاره"), tint = tint, modifier = Modifier.size(size.dp))
                }
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size.dp))
            }
        }
    }
}

@Composable
fun TimelineDot(tone: Tone) {
    Box(Modifier.padding(top = 4.dp).size(12.dp).background(tone.color, CircleShape))
}

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** عکس انتخاب‌شده آماده ارسال: حداکثر ۱۶۰۰ پیکسل و JPEG تا حجم کم بماند؛ PDF بدون تغییر. */
class PreparedFile(val name: String, val contentType: String, val bytes: ByteArray)

fun prepareUpload(context: Context, uri: Uri, maxSide: Int = 1600): PreparedFile? = runCatching {
    val resolver = context.contentResolver
    val type = resolver.getType(uri).orEmpty()
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "photo.jpg"
    if (type == "application/pdf") {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return PreparedFile(if (name.endsWith(".pdf", true)) name else "$name.pdf", type, bytes)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    // عکس دوربین معمولاً چرخش را فقط در EXIF دارد؛ چون EXIF در فایل جدید نمی‌ماند، خود تصویر چرخانده می‌شود.
    val degrees = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(degrees)
    }
    val scaled = if (scale < 1f || degrees != 0f) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    PreparedFile(name.substringBeforeLast('.') + ".jpg", "image/jpeg", out.toByteArray())
}.getOrNull()
