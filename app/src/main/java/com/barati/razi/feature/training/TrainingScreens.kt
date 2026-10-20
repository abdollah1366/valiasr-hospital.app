package com.barati.razi.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.barati.razi.AppContainer
import com.barati.razi.core.data.TrainingRepository
import com.barati.razi.core.network.CourseDto
import com.barati.razi.core.network.CourseItemDto
import com.barati.razi.core.network.ExamDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.training.TrainingLogic
import com.barati.razi.core.training.TrainingLogic.ExamAction
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.RaziScaffold
import java.time.LocalDate

private data class TrainingOverview(val courses: List<CourseDto>, val exams: List<ExamDto>)

private suspend fun loadOverview(repo: TrainingRepository): Result<TrainingOverview> {
    val courses = repo.courses()
    if (courses is Result.Err) return courses
    val exams = repo.exams()
    if (exams is Result.Err) return exams
    return Result.Ok(TrainingOverview((courses as Result.Ok).value, (exams as Result.Ok).value))
}

/** صفحه اصلی آموزش: دوره‌ها و آزمون‌های همکار. */
@Composable
fun TrainingHomeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCourse: (String) -> Unit,
    onTakeExam: (String) -> Unit,
    onAttempt: (String) -> Unit,
) {
    val repo = container.trainingRepository
    val loader = rememberRefreshing { loadOverview(repo) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    RaziScaffold(title = "آموزش", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { data ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                SummaryHeader(data)
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(tab == 0, onClick = { tab = 0 }, text = { Text(toPersianDigits("دوره‌ها (${data.courses.size})")) })
                    Tab(tab == 1, onClick = { tab = 1 }, text = { Text(toPersianDigits("آزمون‌ها (${data.exams.size})")) })
                }
                if (tab == 0) {
                    if (data.courses.isEmpty()) {
                        EmptyState(Icons.Filled.School, "دوره‌ای برای شما نیست", "دوره‌هایی که مسئول آموزش برای شما منتشر کند اینجا نمایش داده می‌شود.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(data.courses, key = { it.id }) { c -> CourseCard(container, c) { onCourse(c.id) } }
                        }
                    }
                } else {
                    if (data.exams.isEmpty()) {
                        EmptyState(Icons.Filled.Quiz, "آزمونی برای شما نیست", "آزمون‌هایی که برای گروه شما باز شود اینجا نمایش داده می‌شود.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(data.exams, key = { it.id }) { e -> ExamCard(e, onTakeExam, onAttempt) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(data: TrainingOverview) {
    val mandatoryLeft = data.courses.count { it.isMandatory && !TrainingLogic.courseFinished(it) }
    val openExams = data.exams.count { TrainingLogic.examAction(it).let { a -> a is ExamAction.Start || a is ExamAction.Continue } }
    val finished = data.courses.count { TrainingLogic.courseFinished(it) }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(primary, Color(0xFF13505A))))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("آموزش همکاران", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        mandatoryLeft > 0 -> toPersianDigits("$mandatoryLeft دوره اجباری را هنوز کامل نکرده‌اید")
                        openExams > 0 -> toPersianDigits("$openExams آزمون باز در انتظار شماست")
                        else -> "همه کارهای آموزشی شما انجام شده است"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    toPersianDigits("$finished/${data.courses.size}"),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("دوره کامل", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseCard(container: AppContainer, c: CourseDto, onClick: () -> Unit) {
    val done = TrainingLogic.courseFinished(c)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(128.dp)) {
            CourseCover(container, c.coverUrl, Modifier.fillMaxSize())
            FlowRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.isMandatory) Pill("اجباری", TrainingColors.amber, TrainingColors.amberBg, Icons.Filled.PriorityHigh)
                if (done) Pill("تکمیل شده", TrainingColors.green, TrainingColors.greenBg, Icons.Filled.CheckCircle)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            c.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            CourseProgress(c)
            DueDate(c)
        }
    }
}

@Composable
private fun CourseCover(container: AppContainer, coverUrl: String?, modifier: Modifier) {
    if (coverUrl != null) {
        AsyncImage(
            model = container.trainingRepository.absoluteUrl(coverUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
        )
    } else {
        val primary = MaterialTheme.colorScheme.primary
        Box(
            modifier.background(Brush.linearGradient(listOf(primary.copy(alpha = 0.85f), Color(0xFF2A8C96)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.School, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun CourseProgress(c: CourseDto) {
    val fraction = if (c.itemCount == 0) 0f else c.completedCount.toFloat() / c.itemCount
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
            color = if (fraction >= 1f) TrainingColors.green else MaterialTheme.colorScheme.primary,
            trackColor = TrainingColors.grayBg,
        )
        Text(
            toPersianDigits("${c.completedCount} از ${c.itemCount}"),
            style = MaterialTheme.typography.labelMedium,
            color = TrainingColors.gray,
        )
    }
}

@Composable
private fun DueDate(c: CourseDto) {
    val due = c.dueDate ?: return
    val late = runCatching { LocalDate.parse(due).isBefore(LocalDate.now()) }.getOrDefault(false) &&
        !TrainingLogic.courseFinished(c)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Event, contentDescription = null, tint = if (late) TrainingColors.red else TrainingColors.gray, modifier = Modifier.size(16.dp))
        Text(
            (if (late) "مهلت گذشته: " else "مهلت تکمیل: ") + Jalali.formatIso(due),
            style = MaterialTheme.typography.labelMedium,
            color = if (late) TrainingColors.red else TrainingColors.gray,
        )
    }
}

/** جزئیات یک دوره: محتواها به ترتیب و آزمون‌های مرتبط. */
@Composable
fun CourseScreen(
    container: AppContainer,
    courseId: String,
    onBack: () -> Unit,
    onItem: (String) -> Unit,
    onTakeExam: (String) -> Unit,
    onAttempt: (String) -> Unit,
) {
    val repo = container.trainingRepository
    val loader = rememberRefreshing(courseId) { repo.course(courseId) }
    RaziScaffold(title = "دوره آموزشی", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { c ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { CourseHeader(container, c) }
                item { SectionTitle("محتوای دوره", toPersianDigits("${c.items.size} مورد")) }
                if (c.items.isEmpty()) {
                    item {
                        Text(
                            "هنوز محتوایی به این دوره اضافه نشده است.",
                            Modifier.padding(horizontal = 16.dp),
                            color = TrainingColors.gray,
                        )
                    }
                }
                itemsIndexed(c.items, key = { _, it -> it.id }) { i, it ->
                    ItemRow(i, it, Modifier.padding(horizontal = 16.dp)) { onItem(it.id) }
                }
                if (c.exams.isNotEmpty()) {
                    item { SectionTitle("آزمون دوره", null) }
                    items(c.exams, key = { it.id }) { e ->
                        Box(Modifier.padding(horizontal = 16.dp)) { ExamCard(e, onTakeExam, onAttempt) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseHeader(container: AppContainer, c: CourseDto) {
    Column {
        CourseCover(container, c.coverUrl, Modifier.fillMaxWidth().height(170.dp))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(c.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.isMandatory) Pill("اجباری", TrainingColors.amber, TrainingColors.amberBg, Icons.Filled.PriorityHigh)
                if (TrainingLogic.courseFinished(c)) Pill("تکمیل شده", TrainingColors.green, TrainingColors.greenBg, Icons.Filled.CheckCircle)
            }
            c.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پیشرفت شما", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    CourseProgress(c)
                    DueDate(c)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = TrainingColors.gray)
    }
}

@Composable
private fun ItemRow(index: Int, item: CourseItemDto, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.completed) TrainingColors.greenBg.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KindBadge(item.kind)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    toPersianDigits("${index + 1}. ") + item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = listOfNotNull(TrainingLogic.kindLabels[item.kind], item.media?.let { TrainingLogic.formatSize(it.size) })
                Text(detail.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = TrainingColors.gray)
            }
            when {
                item.completed -> Icon(Icons.Filled.CheckCircle, contentDescription = "کامل شده", tint = TrainingColors.green)
                item.viewed -> Icon(Icons.Filled.Visibility, contentDescription = "دیده شده", tint = TrainingColors.gray)
            }
        }
    }
}

/** کارت آزمون با دکمه مناسب وضعیت: شروع، ادامه، دیدن نتیجه. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamCard(e: ExamDto, onTakeExam: (String) -> Unit, onAttempt: (String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val action = TrainingLogic.examAction(e)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Filled.Quiz, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                Text(
                    e.title,
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                ExamStatePill(e.state)
            }
            e.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(toPersianDigits("${e.questionCount} سؤال"), TrainingColors.blue, TrainingColors.blueBg, Icons.Filled.HelpOutline)
                Pill(
                    e.timeLimitMinutes?.let { toPersianDigits("$it دقیقه") } ?: "بدون محدودیت زمانی",
                    TrainingColors.blue,
                    TrainingColors.blueBg,
                    Icons.Filled.AccessTime,
                )
                Pill(toPersianDigits("شرکت: ${e.attemptsUsed} از ${e.maxAttempts}"), TrainingColors.gray, TrainingColors.grayBg, Icons.Filled.Replay)
                Pill(toPersianDigits("قبولی: ${e.passScore}٪"), TrainingColors.gray, TrainingColors.grayBg, Icons.Filled.EmojiEvents)
            }
            examWindow(e)?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Event, contentDescription = null, tint = TrainingColors.gray, modifier = Modifier.size(16.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = TrainingColors.gray)
                }
            }
            e.bestScore?.let { score ->
                val ok = e.passed == true
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (ok) TrainingColors.greenBg else TrainingColors.redBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = if (ok) TrainingColors.green else TrainingColors.red)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "بهترین نمره شما: ${TrainingLogic.formatScore(score)}٪ — " + if (ok) "قبول" else "قبول نشده",
                        color = if (ok) TrainingColors.green else TrainingColors.red,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            when (action) {
                is ExamAction.Start -> Button(onClick = { confirm = true }, Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (e.attemptsUsed > 0) "شرکت دوباره در آزمون" else "شروع آزمون")
                }
                is ExamAction.Continue -> Button(onClick = { onTakeExam(e.id) }, Modifier.fillMaxWidth().height(48.dp)) {
                    Text("ادامه آزمون")
                }
                is ExamAction.ViewResult -> OutlinedButton(onClick = { onAttempt(action.attemptId) }, Modifier.fillMaxWidth().height(48.dp)) {
                    Text("دیدن نتیجه")
                }
                is ExamAction.Unavailable -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = TrainingColors.gray, modifier = Modifier.size(16.dp))
                    Text(action.reason, style = MaterialTheme.typography.bodySmall, color = TrainingColors.gray)
                }
            }
            if (action is ExamAction.Start && e.lastAttemptId != null) {
                TextButton(onClick = { onAttempt(e.lastAttemptId) }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text("دیدن نتیجه آخرین شرکت")
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(e.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(toPersianDigits("• ${e.questionCount} سؤال، مجموع ${e.totalPoints} امتیاز"))
                    Text(
                        e.timeLimitMinutes?.let {
                            toPersianDigits("• زمان پاسخگویی $it دقیقه است و از همین لحظه شمرده می‌شود؛ بستن برنامه زمان را متوقف نمی‌کند.")
                        } ?: "• محدودیت زمانی ندارد، ولی تا پیش از بسته شدن آزمون باید پاسخ‌ها را ثبت کنید.",
                    )
                    Text(toPersianDigits("• نمره قبولی ${e.passScore}٪ است."))
                    Text(toPersianDigits("• این شرکت ${e.attemptsUsed + 1} از ${e.maxAttempts} بار مجاز شماست."))
                    Text("• پاسخ‌ها خودکار ذخیره می‌شوند؛ اگر اینترنت قطع شد، دوباره وارد شوید و ادامه دهید.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    onTakeExam(e.id)
                }) { Text("شروع آزمون") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun ExamStatePill(state: String) {
    when (state) {
        "open" -> Pill("باز", TrainingColors.green, TrainingColors.greenBg)
        "upcoming" -> Pill("به‌زودی", TrainingColors.amber, TrainingColors.amberBg)
        else -> Pill("بسته", TrainingColors.gray, TrainingColors.grayBg)
    }
}

private fun examWindow(e: ExamDto): String? = when {
    e.opensAt != null && e.closesAt != null ->
        "از ${TrainingLogic.formatDateTime(e.opensAt)} تا ${TrainingLogic.formatDateTime(e.closesAt)}"
    e.closesAt != null -> "تا ${TrainingLogic.formatDateTime(e.closesAt)}"
    e.opensAt != null -> "از ${TrainingLogic.formatDateTime(e.opensAt)}"
    else -> null
}
