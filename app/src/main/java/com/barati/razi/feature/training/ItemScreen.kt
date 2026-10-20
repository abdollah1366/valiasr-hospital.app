package com.barati.razi.feature.training

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.barati.razi.AppContainer
import com.barati.razi.core.data.TrainingRepository
import com.barati.razi.core.network.CourseItemDto
import com.barati.razi.core.network.MediaDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.training.TrainingLogic
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** نمایش یک محتوای دوره (فیلم، صوت، عکس، PDF، فایل، متن یا لینک) و رفتن به محتوای قبلی/بعدی. */
@Composable
fun CourseItemScreen(
    container: AppContainer,
    courseId: String,
    itemId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val repo = container.trainingRepository
    val loader = rememberLoader(courseId) { repo.course(courseId) }
    RaziScaffold(title = "محتوای آموزشی", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { course ->
            val index = course.items.indexOfFirst { it.id == itemId }
            if (index < 0) {
                MessageBox("این محتوا دیگر در دوره نیست", Modifier.padding(padding), "بازگشت", onBack)
                return@LoadContent
            }
            val item = course.items[index]
            val tracker = rememberViewTracker(repo, item.id)
            Column(Modifier.fillMaxSize().padding(padding)) {
                ItemHeader(item, index, course.items.size)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (item.kind) {
                        "video", "audio" -> item.media?.let { MediaContent(repo, item, it, tracker) }
                        "image" -> item.media?.let { ImageContent(repo, item, it, tracker) }
                        "pdf" -> item.media?.let { PdfContent(repo, item, it, tracker) }
                        "link" -> LinkContent(item, tracker)
                        "text" -> TextContent(item, tracker)
                        else -> item.media?.let { FileContent(repo, item, it, tracker) }
                    }
                }
                ItemNavigation(
                    previous = course.items.getOrNull(index - 1)?.id,
                    next = course.items.getOrNull(index + 1)?.id,
                    onOpenItem = onOpenItem,
                    onFinish = onBack,
                )
            }
        }
    }
}

@Composable
private fun ItemHeader(item: CourseItemDto, index: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        KindBadge(item.kind)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                toPersianDigits("${TrainingLogic.kindLabels[item.kind].orEmpty()} · محتوای ${index + 1} از $total"),
                style = MaterialTheme.typography.labelMedium,
                color = TrainingColors.gray,
            )
        }
        if (item.completed) Icon(Icons.Filled.CheckCircle, contentDescription = "کامل شده", tint = TrainingColors.green)
    }
}

@Composable
private fun ItemNavigation(previous: String?, next: String?, onOpenItem: (String) -> Unit, onFinish: () -> Unit) {
    Surface(shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { previous?.let(onOpenItem) }, enabled = previous != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  قبلی")
            }
            if (next != null) {
                Button(onClick = { onOpenItem(next) }, modifier = Modifier.weight(1f)) {
                    Text("بعدی  ")
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            } else {
                Button(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("پایان و بازگشت به دوره") }
            }
        }
    }
}

@Composable
private fun Description(text: String?) {
    if (text.isNullOrBlank()) return
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("توضیح", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
    }
}

// ---------------------------------------------------------------- متن و لینک

@Composable
private fun TextContent(item: CourseItemDto, tracker: ViewTracker) {
    LaunchedEffect(item.id) { tracker.report(100) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            SelectionContainer {
                Text(
                    item.body.orEmpty(),
                    Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35f),
                )
            }
        }
    }
}

@Composable
private fun LinkContent(item: CourseItemDto, tracker: ViewTracker) {
    val uriHandler = LocalUriHandler.current
    var error by remember { mutableStateOf<String?>(null) }
    val url = item.body.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigFileCard(item.kind, "پیوند به منبع بیرونی", url)
        Button(
            onClick = {
                error = try {
                    uriHandler.openUri(url)
                    tracker.report(100)
                    null
                } catch (e: Exception) {
                    "مرورگری برای باز کردن این لینک پیدا نشد"
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null)
            Text("  باز کردن در مرورگر")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun BigFileCard(kind: String, title: String, subtitle: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KindBadge(kind, 72.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- فیلم و صوت

@OptIn(UnstableApi::class)
@Composable
private fun MediaContent(repo: TrainingRepository, item: CourseItemDto, media: MediaDto, tracker: ViewTracker) {
    val context = LocalContext.current
    val audio = item.kind == "audio"
    val player = remember(media.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(repo.absoluteUrl(media.url)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            tracker.report(TrainingLogic.percent(player.currentPosition, player.duration))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (audio) {
            Box(
                Modifier.fillMaxWidth().padding(16.dp).height(160.dp).clip(RoundedCornerShape(20.dp))
                    .background(kindStyle("audio").background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = kindStyle("audio").color, modifier = Modifier.size(72.dp))
            }
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // کنترل‌های پخش همیشه چپ‌به‌راست‌اند، حتی در رابط فارسی.
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    keepScreenOn = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    this.player = player
                    if (audio) {
                        useArtwork = false
                        controllerShowTimeoutMs = 0
                        controllerHideOnTouch = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                }
            },
            modifier = if (audio) {
                Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 8.dp)
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
            },
        )
        if (!audio) {
            Text(
                "برای تمام‌صفحه، گوشی را افقی بگیرید.",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = TrainingColors.gray,
            )
        }
        Description(item.body)
    }
}

// ---------------------------------------------------------------- عکس

@Composable
private fun ImageContent(repo: TrainingRepository, item: CourseItemDto, media: MediaDto, tracker: ViewTracker) {
    LaunchedEffect(item.id) { tracker.report(100) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp))
                .background(Color(0xFF101414))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = repo.absoluteUrl(media.url),
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            )
        }
        Text(
            "برای بزرگ‌نمایی دو انگشت را باز کنید یا دو بار بزنید.",
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TrainingColors.gray,
        )
        if (!item.body.isNullOrBlank()) {
            HorizontalDivider()
            Box(Modifier.height(140.dp).verticalScroll(rememberScrollState())) { Description(item.body) }
        }
    }
}

// ---------------------------------------------------------------- دریافت فایل

/** فایل را دریافت می‌کند و تا آماده شدن، پیشرفت دریافت را نشان می‌دهد. */
@Composable
private fun WithDownloadedFile(
    repo: TrainingRepository,
    media: MediaDto,
    autoStart: Boolean,
    idle: @Composable (start: () -> Unit) -> Unit,
    ready: @Composable (File) -> Unit,
) {
    var started by remember(media.id) { mutableStateOf(autoStart) }
    var attempt by remember(media.id) { mutableIntStateOf(0) }
    var file by remember(media.id) { mutableStateOf<File?>(null) }
    var progress by remember(media.id) { mutableFloatStateOf(0f) }
    var error by remember(media.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(media.id, started, attempt) {
        if (!started) return@LaunchedEffect
        error = null
        when (val r = repo.download(media) { progress = it }) {
            is Result.Ok -> file = r.value
            is Result.Err -> error = r.error.message
        }
    }
    val f = file
    val err = error
    when {
        f != null -> ready(f)
        err != null -> MessageBox(err, actionLabel = "تلاش دوباره", onAction = { attempt++ })
        !started -> idle { started = true }
        else -> Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("در حال دریافت فایل…", style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
            Text(
                toPersianDigits("${(progress * 100).toInt()}٪ از ${TrainingLogic.formatSize(media.size)}"),
                style = MaterialTheme.typography.labelMedium,
                color = TrainingColors.gray,
            )
        }
    }
}

@Composable
private fun FileContent(repo: TrainingRepository, item: CourseItemDto, media: MediaDto, tracker: ViewTracker) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val open: (File) -> Unit = { file ->
        if (openWithOtherApp(context, file, media.contentType)) {
            tracker.report(100)
            error = null
        } else {
            error = if (item.kind == "pptx") {
                "برنامه‌ای برای باز کردن پاورپوینت نصب نیست. Microsoft PowerPoint یا WPS Office را از بازار نصب کنید."
            } else {
                "برنامه‌ای برای باز کردن این نوع فایل روی گوشی نصب نیست."
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigFileCard(item.kind, media.originalName, TrainingLogic.formatSize(media.size))
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
            WithDownloadedFile(
                repo,
                media,
                autoStart = false,
                idle = { start ->
                    Button(onClick = start, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("  دریافت و باز کردن")
                    }
                },
                ready = { file ->
                    LaunchedEffect(file) { open(file) }
                    Button(onClick = { open(file) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        Text("  باز کردن")
                    }
                },
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (item.kind == "pptx") {
            Text(
                "پاورپوینت با برنامه PowerPoint یا WPS روی گوشی باز می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = TrainingColors.gray,
            )
        }
        Description(item.body)
    }
}

// ---------------------------------------------------------------- PDF

@Composable
private fun PdfContent(repo: TrainingRepository, item: CourseItemDto, media: MediaDto, tracker: ViewTracker) {
    val context = LocalContext.current
    WithDownloadedFile(repo, media, autoStart = true, idle = {}) { file ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.body.orEmpty(),
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
                TextButton(onClick = { openWithOtherApp(context, file, media.contentType) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" برنامه دیگر")
                }
            }
            PdfPages(repo, file, tracker)
        }
    }
}

/** PdfRenderer هم‌زمان فقط یک صفحه را رندر می‌کند؛ قفل از تداخل و از بستن هنگام رندر جلوگیری می‌کند. */
private class PdfDocument(file: File) : Closeable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(fd)
    } catch (e: Exception) {
        fd.close()
        throw e
    }
    private val lock = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    suspend fun closeSafely() = lock.withLock { close() }

    override fun close() {
        if (closed) return
        closed = true
        renderer.close()
        fd.close()
    }
}

@Composable
private fun PdfPages(repo: TrainingRepository, file: File, tracker: ViewTracker) {
    val doc = remember(file) { runCatching { PdfDocument(file) }.getOrNull() }
    if (doc == null) {
        MessageBox("این فایل PDF قابل نمایش نیست؛ «برنامه دیگر» را امتحان کنید.")
        return
    }
    DisposableEffect(doc) { onDispose { repo.scope.launch { doc.closeSafely() } } }
    val listState = rememberLazyListState()
    LaunchedEffect(doc) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { tracker.report(TrainingLogic.percent(it + 1L, doc.pageCount.toLong())) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFE9EDED))) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(1, 1600)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(doc.pageCount) { i -> PdfPage(doc, i, widthPx) }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(12.dp),
            shape = RoundedCornerShape(50),
            color = Color(0xCC1F2B2D),
        ) {
            val current = (listState.firstVisibleItemIndex + 1).coerceAtMost(doc.pageCount)
            Text(
                toPersianDigits("صفحه $current از ${doc.pageCount}"),
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDocument, index: Int, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) { value = doc.render(index, widthPx) }
    val b = bitmap
    if (b == null) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = toPersianDigits("صفحه ${index + 1}"),
            modifier = Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height).shadow(2.dp),
        )
    }
}
