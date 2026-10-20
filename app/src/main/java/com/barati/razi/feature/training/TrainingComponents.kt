package com.barati.razi.feature.training

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.barati.razi.core.data.TrainingRepository
import com.barati.razi.core.network.Result
import com.barati.razi.core.training.TrainingLogic
import com.barati.razi.ui.components.Load
import com.barati.razi.ui.components.Loader
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** رنگ‌های مشترک بخش آموزش. */
object TrainingColors {
    val green = Color(0xFF1B7F3B)
    val greenBg = Color(0xFFE3F4E8)
    val amber = Color(0xFF9A5B00)
    val amberBg = Color(0xFFFFF0D6)
    val red = Color(0xFFB3261E)
    val redBg = Color(0xFFFDE7E5)
    val gray = Color(0xFF5F6B6D)
    val grayBg = Color(0xFFEBEFEF)
    val blue = Color(0xFF1F5FAF)
    val blueBg = Color(0xFFE3EDFB)
}

data class KindStyle(val icon: ImageVector, val color: Color, val background: Color)

fun kindStyle(kind: String): KindStyle = when (kind) {
    "video" -> KindStyle(Icons.Filled.PlayCircle, Color(0xFFC62828), Color(0xFFFDECEA))
    "image" -> KindStyle(Icons.Filled.Image, Color(0xFF6A1B9A), Color(0xFFF3E5F5))
    "audio" -> KindStyle(Icons.Filled.Headphones, Color(0xFF4527A0), Color(0xFFEDE7F6))
    "pdf" -> KindStyle(Icons.Filled.PictureAsPdf, Color(0xFFD84315), Color(0xFFFBE9E7))
    "pptx" -> KindStyle(Icons.Filled.Slideshow, Color(0xFFE65100), Color(0xFFFFF3E0))
    "file" -> KindStyle(Icons.Filled.AttachFile, Color(0xFF455A64), Color(0xFFECEFF1))
    "link" -> KindStyle(Icons.Filled.Link, Color(0xFF2E7D32), Color(0xFFE8F5E9))
    else -> KindStyle(Icons.Filled.Description, Color(0xFF1565C0), Color(0xFFE3F2FD))
}

@Composable
fun KindBadge(kind: String, size: Dp = 44.dp) {
    val style = kindStyle(kind)
    Box(
        Modifier.size(size).background(style.background, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(style.icon, contentDescription = TrainingLogic.kindLabels[kind], tint = style.color, modifier = Modifier.size(size * 0.55f))
    }
}

/** برچسب کوچک رنگی (مثل «اجباری»، «باز»). */
@Composable
fun Pill(text: String, color: Color, background: Color, icon: ImageVector? = null) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun IconCircle(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconCircle(icon, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, 72.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TrainingColors.gray, textAlign = TextAlign.Center)
    }
}

/**
 * مثل rememberLoader، ولی هنگام بازگشت به صفحه (مثلاً پس از دیدن یک محتوا) داده را
 * بدون نمایش دوباره «در حال بارگذاری» تازه می‌کند تا پیشرفت به‌روز دیده شود.
 */
@Composable
fun <T> rememberRefreshing(vararg keys: Any?, block: suspend () -> Result<T>): Loader<T> {
    var version by remember { mutableIntStateOf(0) }
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(version, *keys) {
        val current = state
        state = when (val r = block()) {
            is Result.Ok -> Load.Data(r.value)
            is Result.Err -> if (current is Load.Data) current else Load.Error(r.error.message)
        }
    }
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce) version++ else resumedOnce = true
    }
    return Loader(state) {
        state = Load.Loading
        version++
    }
}

/**
 * ثبت بازدید یک محتوا: زمان فقط وقتی صفحه جلوی چشم است شمرده می‌شود،
 * هر ۱۵ ثانیه و هنگام خروج از صفحه به سرور فرستاده می‌شود.
 */
class ViewTracker(private val repo: TrainingRepository, private val itemId: String) {
    @Volatile var seconds = 0
    @Volatile var progress = 0
        private set
    @Volatile private var viewId: String? = null
    private var lastSent: Pair<Int, Int>? = null

    fun report(value: Int) {
        progress = TrainingLogic.mergeProgress(progress, value)
    }

    suspend fun start() {
        viewId = (repo.startView(itemId) as? Result.Ok)?.value?.viewId
    }

    suspend fun flush() {
        val id = viewId ?: return
        val now = seconds to progress
        if (now == lastSent) return
        if (repo.updateView(id, now.first, now.second) is Result.Ok) lastSent = now
    }
}

@Composable
fun rememberViewTracker(repo: TrainingRepository, itemId: String): ViewTracker {
    val tracker = remember(itemId) { ViewTracker(repo, itemId) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tracker) {
        tracker.start()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var tick = 0
            while (true) {
                delay(1000)
                tracker.seconds = tracker.seconds + 1
                if (++tick % 15 == 0) tracker.flush()
            }
        }
    }
    DisposableEffect(tracker) {
        onDispose { repo.scope.launch { tracker.flush() } }
    }
    return tracker
}

/** باز کردن فایل با برنامه‌ای دیگر (پاورپوینت، ورد، PDF و ...). false یعنی برنامه مناسبی نصب نیست. */
fun openWithOtherApp(context: Context, file: File, contentType: String): Boolean {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, contentType.substringBefore(';').trim())
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
