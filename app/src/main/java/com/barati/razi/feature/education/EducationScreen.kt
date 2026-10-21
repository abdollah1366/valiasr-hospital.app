package com.barati.razi.feature.education

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.network.EducationDto
import com.barati.razi.core.network.Result
import com.barati.razi.feature.appointments.RatingSummary
import com.barati.razi.feature.appointments.StarPicker
import com.barati.razi.ui.components.Load
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** برچسب نوع محتوا و نوع مراقبت؛ کلیدها همان‌هایی است که سرور می‌فرستد. */
@StringRes
fun kindLabel(kind: String): Int = when (kind) {
    "article" -> R.string.edu_kind_article
    "image" -> R.string.edu_kind_image
    "video" -> R.string.edu_kind_video
    "audio" -> R.string.edu_kind_audio
    else -> R.string.edu_kind_pdf
}

@StringRes
fun careLabel(care: String): Int = when (care) {
    "prevention" -> R.string.edu_care_prevention
    "before_procedure" -> R.string.edu_care_before
    "after_procedure" -> R.string.edu_care_after
    "medication" -> R.string.edu_care_medication
    "nutrition" -> R.string.edu_care_nutrition
    "exercise" -> R.string.edu_care_exercise
    "home_care" -> R.string.edu_care_home
    "warning_signs" -> R.string.edu_care_warning
    else -> R.string.edu_care_other
}

fun kindIcon(kind: String): ImageVector = when (kind) {
    "article" -> Icons.AutoMirrored.Outlined.Article
    "image" -> Icons.Outlined.Image
    "video" -> Icons.Outlined.PlayCircle
    "audio" -> Icons.Outlined.Headphones
    else -> Icons.Outlined.PictureAsPdf
}

/** انتخاب دوباره‌ی همان گزینه فیلتر را برمی‌دارد. */
fun toggled(current: String?, picked: String): String? = if (current == picked) null else picked

/** آموزش به بیمار: کتابخانه، آموزش‌های فرستاده‌شده برای خود مراجع و تیکت با مسئول آموزش. */
@Composable
fun EducationTab(container: AppContainer, padding: PaddingValues, signedIn: Boolean, onSignIn: () -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(R.string.edu_tab_library, R.string.edu_tab_mine, R.string.edu_tab_tickets)
    Column(Modifier.fillMaxSize().padding(padding)) {
        TabRow(selectedTabIndex = section) {
            labels.forEachIndexed { i, l -> Tab(selected = section == i, onClick = { section = i }, text = { Text(stringResource(l)) }) }
        }
        val inner = PaddingValues(0.dp)
        when (section) {
            0 -> EducationLibrary(container, inner, signedIn, onSignIn)
            1 -> MyEducationList(container, inner, signedIn, onSignIn)
            else -> EducationTickets(container, inner, signedIn, onSignIn)
        }
    }
}

/** کتابخانه: جست‌وجو، فیلتر و باز کردن مقاله، تصویر، ویدئو، صوت یا PDF. */
@Composable
private fun EducationLibrary(container: AppContainer, padding: PaddingValues, signedIn: Boolean, onSignIn: () -> Unit) {
    val language = LocalAppLanguage.current
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openId != null) { openId = null }
    val id = openId
    if (id != null) {
        EducationDetail(container, id, padding, signedIn, onSignIn)
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    var debounced by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var care by rememberSaveable { mutableStateOf<String?>(null) }
    var condition by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(400)
        debounced = query
    }
    val filters = rememberLoader { container.educationRepository.filters() }
    val loader = rememberLoader(language, debounced, kind, care, condition) {
        container.educationRepository.list(language, debounced, kind, care, condition)
    }
    val f = (filters.state as? Load.Data)?.value

    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.edu_search)) },
            shape = RoundedCornerShape(50),
        )
        ChipRow(f?.kinds.orEmpty(), kind, { kind = toggled(kind, it) }) { stringResource(kindLabel(it)) }
        ChipRow(f?.careTypes.orEmpty(), care, { care = toggled(care, it) }) { stringResource(careLabel(it)) }
        ChipRow(f?.conditions.orEmpty(), condition, { condition = toggled(condition, it) }) { it }
        LoadContent(loader, Modifier) { items ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (items.isEmpty()) {
                    item { Text(stringResource(R.string.edu_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(items, key = { it.id }) { e -> EducationCard(e) { openId = e.id } }
            }
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String?, onPick: (String) -> Unit, label: @Composable (String) -> String) {
    if (options.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        items(options) { o -> FilterChip(selected == o, { onPick(o) }, { Text(label(o)) }) }
    }
}

@Composable
private fun EducationCard(e: EducationDto, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(ValiasrColors.BrandSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(kindIcon(e.kind), contentDescription = stringResource(kindLabel(e.kind)), tint = ValiasrColors.Brand) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                e.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3) }
                Text(
                    listOfNotNull(stringResource(kindLabel(e.kind)), stringResource(careLabel(e.careType)), e.condition).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EducationDetail(container: AppContainer, id: String, padding: PaddingValues, signedIn: Boolean, onSignIn: () -> Unit) {
    val language = LocalAppLanguage.current
    val loader = rememberLoader(id, language) { container.educationRepository.item(id, language) }
    // اگر این آموزش را برای مراجع فرستاده باشند، دیده شدنش به مسئول آموزش اطلاع داده می‌شود.
    LaunchedEffect(id, signedIn) { if (signedIn) container.educationRepository.markSeen(id) }
    LoadContent(loader, Modifier.padding(padding)) { e ->
        val context = LocalContext.current
        fun open(url: String) = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val fileUrl = if (e.hasFile) container.educationFileUrl(e) else null
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(e.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(stringResource(kindLabel(e.kind)), stringResource(careLabel(e.careType)), e.condition, e.specialtyName)
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            e.summary?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            if (fileUrl != null) {
                when (e.kind) {
                    "image" -> AsyncImage(
                        model = fileUrl,
                        contentDescription = e.title,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                    )
                    "video", "audio" -> EducationPlayer(fileUrl, audio = e.kind == "audio")
                    else -> Button(onClick = { open(fileUrl) }, Modifier.height(48.dp)) { Text(stringResource(R.string.edu_open_file)) }
                }
            }
            e.externalUrl?.let { url ->
                Button(onClick = { open(url) }, Modifier.height(48.dp)) { Text(stringResource(R.string.edu_open_link)) }
            }
            e.body?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (e.source != null || e.reviewedOn != null) {
                Spacer(Modifier.height(4.dp))
                e.source?.let {
                    Text(stringResource(R.string.edu_source, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                e.reviewedOn?.let {
                    val day = runCatching { AppointmentLogic.dayText(LocalDate.parse(it), language) }.getOrDefault(it)
                    Text(stringResource(R.string.edu_reviewed, day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RatingSection(container, e, signedIn, onSignIn)
            Text(stringResource(R.string.edu_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun EducationPlayer(url: String, audio: Boolean) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                // کنترل‌های پخش همیشه چپ‌به‌راست‌اند، حتی در رابط فارسی.
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setShowNextButton(false)
                setShowPreviousButton(false)
                this.player = player
                if (audio) {
                    useArtwork = false
                    controllerShowTimeoutMs = 0
                    controllerHideOnTouch = false
                }
            }
        },
        modifier = if (audio) {
            Modifier.fillMaxWidth().height(120.dp)
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
        },
    )
}

/** میانگین امتیاز برای همه؛ مراجع واردشده می‌تواند ستاره بدهد یا عوض کند. */
@Composable
private fun RatingSection(container: AppContainer, e: EducationDto, signedIn: Boolean, onSignIn: () -> Unit) {
    var average by remember(e.id) { mutableStateOf(e.ratingAverage) }
    var count by remember(e.id) { mutableIntStateOf(e.ratingCount) }
    var mine by remember(e.id) { mutableIntStateOf(0) }
    var failed by remember(e.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(e.id, signedIn) {
        if (signedIn) (container.educationRepository.myRating(e.id) as? Result.Ok)?.value?.let { mine = it.stars ?: 0 }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RatingSummary(average, count)
        if (signedIn) {
            Text(stringResource(if (mine > 0) R.string.edu_rate_yours else R.string.edu_rate_prompt), style = MaterialTheme.typography.labelLarge)
            StarPicker(mine) { stars ->
                scope.launch {
                    when (val r = container.educationRepository.rate(e.id, stars)) {
                        is Result.Ok -> {
                            mine = stars
                            average = r.value.ratingAverage
                            count = r.value.ratingCount
                            failed = false
                        }
                        is Result.Err -> failed = true
                    }
                }
            }
            if (failed) Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.edu_rate_signin)) }
        }
    }
}
