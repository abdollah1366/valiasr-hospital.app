package com.barati.razi.feature.training

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.data.TrainingRepository
import com.barati.razi.core.network.AttemptDto
import com.barati.razi.core.network.AttemptOptionDto
import com.barati.razi.core.network.AttemptQuestionDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.training.TrainingLogic
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** شروع یا ادامه آزمون (اگر آزمون نیمه‌کاره‌ای باشد سرور همان را برمی‌گرداند). */
@Composable
fun ExamTakeScreen(container: AppContainer, examId: String, onBack: () -> Unit) {
    val repo = container.trainingRepository
    val loader = rememberLoader(examId) { repo.startAttempt(examId) }
    AttemptHost(repo, loader, onBack)
}

/** دیدن نتیجه یک شرکت قبلی. */
@Composable
fun AttemptScreen(container: AppContainer, attemptId: String, onBack: () -> Unit) {
    val repo = container.trainingRepository
    val loader = rememberLoader(attemptId) { repo.attempt(attemptId) }
    AttemptHost(repo, loader, onBack)
}

@Composable
private fun AttemptHost(repo: TrainingRepository, loader: com.barati.razi.ui.components.Loader<AttemptDto>, onBack: () -> Unit) {
    val state = loader.state
    if (state is com.barati.razi.ui.components.Load.Data) {
        var attempt by remember(state.value.id) { mutableStateOf(state.value) }
        val current = attempt
        if (current.finished) {
            ResultScreen(current, onBack)
        } else {
            TakingScreen(repo, current, onFinished = { attempt = it }, onBack = onBack)
        }
    } else {
        RaziScaffold(title = "آزمون", onBack = onBack) { padding ->
            LoadContent(loader, Modifier.padding(padding)) {}
        }
    }
}

private enum class SaveState { Saved, Saving, Failed }

@Composable
private fun TakingScreen(repo: TrainingRepository, attempt: AttemptDto, onFinished: (AttemptDto) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val answers = remember(attempt.id) {
        mutableStateMapOf<String, List<String>>().apply {
            attempt.questions.filter { it.selected.isNotEmpty() }.forEach { put(it.id, it.selected) }
        }
    }
    var version by remember { mutableIntStateOf(0) }
    var saveState by remember { mutableStateOf(SaveState.Saved) }
    var submitting by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // زمان باقی‌مانده بر اساس ساعت سرور هنگام دریافت آزمون
    val fetchedAt = remember(attempt.id) { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(fetchedAt) }
    LaunchedEffect(attempt.id) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val remaining = TrainingLogic.remainingSeconds(attempt.deadlineAt, attempt.serverTime, now - fetchedAt)

    /** اگر سرور گفت آزمون تمام شده (مثلاً مهلت گذشته)، نتیجه را از سرور بگیر. */
    suspend fun refreshIfFinished(code: String) {
        if (code == "time_over" || code == "attempt_finished") {
            (repo.attempt(attempt.id) as? Result.Ok)?.value?.let(onFinished)
        }
    }

    val submit: () -> Unit = {
        if (!submitting) {
            submitting = true
            confirmSubmit = false
            scope.launch {
                when (val r = repo.submit(attempt.id, answers.toMap())) {
                    is Result.Ok -> onFinished(r.value)
                    is Result.Err -> {
                        error = r.error.message
                        submitting = false
                        refreshIfFinished(r.error.code)
                    }
                }
            }
        }
    }

    // ذخیره خودکار با کمی تأخیر پس از هر تغییر
    LaunchedEffect(version) {
        if (version == 0) return@LaunchedEffect
        saveState = SaveState.Saving
        delay(1200)
        when (val r = repo.saveAnswers(attempt.id, answers.toMap())) {
            is Result.Ok -> saveState = SaveState.Saved
            is Result.Err -> {
                saveState = SaveState.Failed
                refreshIfFinished(r.error.code)
            }
        }
    }

    // پایان زمان: ارسال خودکار (سرور تا ۳۰ ثانیه پس از مهلت می‌پذیرد)
    LaunchedEffect(remaining == 0L) {
        if (remaining == 0L) submit()
    }

    BackHandler { confirmExit = true }

    val total = attempt.questions.size
    val answered = attempt.questions.count { !answers[it.id].isNullOrEmpty() }
    RaziScaffold(title = attempt.examTitle, onBack = { confirmExit = true }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ExamStatusBar(remaining, answered, total, saveState)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(attempt.questions, key = { _, q -> q.id }) { i, q ->
                    QuestionCard(i, q, answers[q.id].orEmpty(), enabled = !submitting) { optionId ->
                        answers[q.id] = TrainingLogic.toggle(q.kind, answers[q.id].orEmpty(), optionId)
                        version++
                    }
                }
            }
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { confirmSubmit = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        if (submitting) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text("پایان و ثبت پاسخ‌ها")
                        }
                    }
                }
            }
        }
    }

    if (confirmSubmit) {
        val unanswered = total - answered
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("ثبت نهایی پاسخ‌ها") },
            text = {
                Text(
                    if (unanswered > 0) {
                        toPersianDigits("$unanswered سؤال بی‌پاسخ مانده است. پس از ثبت، امکان تغییر پاسخ‌ها نیست.")
                    } else {
                        "به همه سؤال‌ها پاسخ داده‌اید. پس از ثبت، امکان تغییر پاسخ‌ها نیست."
                    },
                )
            },
            confirmButton = { Button(onClick = submit) { Text("ثبت نهایی") } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("بازگشت به سؤال‌ها") } },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("خروج از آزمون؟") },
            text = {
                Text(
                    if (remaining != null) {
                        "پاسخ‌ها ذخیره می‌شوند و تا پایان زمان می‌توانید برگردید و ادامه دهید. زمان آزمون با خروج متوقف نمی‌شود."
                    } else {
                        "پاسخ‌ها ذخیره می‌شوند و بعداً می‌توانید ادامه دهید."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    val snapshot = answers.toMap()
                    repo.scope.launch { repo.saveAnswers(attempt.id, snapshot) }
                    onBack()
                }) { Text("خروج") }
            },
            dismissButton = { Button(onClick = { confirmExit = false }) { Text("ادامه آزمون") } },
        )
    }
}

@Composable
private fun ExamStatusBar(remaining: Long?, answered: Int, total: Int, saveState: SaveState) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (remaining != null) {
                    val low = remaining <= 60
                    Pill(
                        TrainingLogic.formatCountdown(remaining),
                        if (low) TrainingColors.red else TrainingColors.blue,
                        if (low) TrainingColors.redBg else TrainingColors.blueBg,
                        Icons.Filled.Timer,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    toPersianDigits("$answered از $total پاسخ داده شد"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                val (icon, label) = when (saveState) {
                    SaveState.Saved -> Icons.Filled.CloudDone to "ذخیره شد"
                    SaveState.Saving -> Icons.Filled.CloudUpload to "در حال ذخیره"
                    SaveState.Failed -> Icons.Filled.CloudOff to "ذخیره نشد"
                }
                val color = if (saveState == SaveState.Failed) TrainingColors.red else TrainingColors.gray
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(" $label", style = MaterialTheme.typography.labelSmall, color = color)
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else answered.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                trackColor = TrainingColors.grayBg,
            )
        }
    }
}

@Composable
private fun QuestionHeader(index: Int, q: AttemptQuestionDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(toPersianDigits("${index + 1}"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Text(
            TrainingLogic.questionKindLabels[q.kind].orEmpty(),
            Modifier.weight(1f).padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TrainingColors.gray,
        )
        Text(toPersianDigits("${q.points} امتیاز"), style = MaterialTheme.typography.labelMedium, color = TrainingColors.gray)
    }
}

@Composable
private fun QuestionCard(index: Int, q: AttemptQuestionDto, selected: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuestionHeader(index, q)
            Text(q.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            q.options.forEach { o ->
                OptionRow(o, o.id in selected, q.kind == "multiple", enabled) { onSelect(o.id) }
            }
        }
    }
}

@Composable
private fun OptionRow(option: AttemptOptionDto, checked: Boolean, multiple: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(if (checked) 1.5.dp else 1.dp, if (checked) primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = if (multiple) Role.Checkbox else Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (multiple) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            } else {
                RadioButton(selected = checked, onClick = null, enabled = enabled)
            }
            Text(option.text, Modifier.padding(horizontal = 8.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------------------------------------------------------------- نتیجه

@Composable
private fun ResultScreen(attempt: AttemptDto, onBack: () -> Unit) {
    RaziScaffold(title = attempt.examTitle, onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ResultHeader(attempt) }
            if (attempt.answersVisible) {
                itemsIndexed(attempt.questions, key = { _, q -> q.id }) { i, q -> ReviewCard(i, q) }
            } else {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TrainingColors.grayBg).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = TrainingColors.gray)
                        Text(
                            "پاسخ درست سؤال‌ها فعلاً نمایش داده نمی‌شود. اگر مسئول آموزش اجازه داده باشد، پس از بسته شدن آزمون از همین‌جا قابل دیدن است.",
                            Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TrainingColors.gray,
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("بازگشت") }
            }
        }
    }
}

@Composable
private fun ResultHeader(attempt: AttemptDto) {
    val score = attempt.score ?: 0.0
    val passed = attempt.passed == true
    val color = if (passed) TrainingColors.green else TrainingColors.red
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (score / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.size(140.dp),
                    color = color,
                    strokeWidth = 12.dp,
                    trackColor = TrainingColors.grayBg,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        TrainingLogic.formatScore(score) + "٪",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text("نمره شما", style = MaterialTheme.typography.labelMedium, color = TrainingColors.gray)
                }
            }
            Pill(
                if (passed) "قبول شدید" else "قبول نشدید",
                color,
                if (passed) TrainingColors.greenBg else TrainingColors.redBg,
                if (passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            )
            Text(
                toPersianDigits("حد نصاب قبولی: ${attempt.passScore}٪"),
                style = MaterialTheme.typography.bodySmall,
                color = TrainingColors.gray,
            )
            if (attempt.answersVisible) {
                val correct = attempt.questions.count { it.correct == true }
                Text(
                    toPersianDigits("$correct پاسخ درست از ${attempt.questions.size} سؤال"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            attempt.submittedAt?.let {
                Text(
                    "ثبت شده در " + TrainingLogic.formatDateTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = TrainingColors.gray,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(index: Int, q: AttemptQuestionDto) {
    val ok = q.correct == true
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, if (ok) TrainingColors.green.copy(alpha = 0.4f) else TrainingColors.red.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { QuestionHeader(index, q) }
                Spacer(Modifier.size(8.dp))
                if (ok) {
                    Pill("درست", TrainingColors.green, TrainingColors.greenBg, Icons.Filled.CheckCircle)
                } else {
                    Pill("نادرست", TrainingColors.red, TrainingColors.redBg, Icons.Filled.Cancel)
                }
            }
            Text(q.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            q.options.forEach { o ->
                val picked = o.id in q.selected
                val right = o.correct == true
                val (icon, tint) = when {
                    right -> Icons.Filled.CheckCircle to TrainingColors.green
                    picked -> Icons.Filled.Cancel to TrainingColors.red
                    else -> Icons.Filled.RadioButtonUnchecked to TrainingColors.gray
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                right -> TrainingColors.greenBg
                                picked -> TrainingColors.redBg
                                else -> MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(o.text, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
                    if (picked) Text("پاسخ شما", style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
            q.explanation?.takeIf { it.isNotBlank() }?.let {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TrainingColors.amberBg).padding(12.dp),
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = TrainingColors.amber, modifier = Modifier.size(18.dp))
                    Text(it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
