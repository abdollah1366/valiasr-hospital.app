package com.barati.razi.core.training

import com.barati.razi.core.network.CourseDto
import com.barati.razi.core.network.ExamDto
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toPersianDigits
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** منطق بدون وابستگی به اندروید بخش آموزش (برای تست واحد). */
object TrainingLogic {
    val kindLabels = mapOf(
        "text" to "متن",
        "image" to "عکس",
        "video" to "فیلم",
        "audio" to "صوت",
        "pdf" to "PDF",
        "pptx" to "پاورپوینت",
        "file" to "فایل",
        "link" to "لینک",
    )

    val questionKindLabels = mapOf(
        "single" to "تک‌گزینه‌ای",
        "multiple" to "چندگزینه‌ای (همه گزینه‌های درست را انتخاب کنید)",
        "true_false" to "درست / غلط",
    )

    /** پیشرفت فقط رو به جلو می‌رود؛ عقب زدن فیلم پیشرفت قبلی را کم نمی‌کند. */
    fun mergeProgress(old: Int, new: Int): Int = maxOf(old, new.coerceIn(0, 100))

    fun percent(position: Long, total: Long): Int =
        if (total <= 0) 0 else ((position.coerceIn(0, total) * 100.0) / total).roundToInt()

    /** انتخاب گزینه: در چندگزینه‌ای افزودن/برداشتن، در بقیه جایگزینی. */
    fun toggle(kind: String, selected: List<String>, optionId: String): List<String> = when {
        kind != "multiple" -> listOf(optionId)
        optionId in selected -> selected - optionId
        else -> selected + optionId
    }

    /**
     * ثانیه‌های باقی‌مانده تا مهلت، بر اساس ساعت سرور (نه ساعت گوشی که ممکن است درست نباشد).
     * [elapsedMillis] زمان گذشته از دریافت پاسخ سرور است. بدون مهلت: null.
     */
    fun remainingSeconds(deadlineIso: String?, serverTimeIso: String, elapsedMillis: Long): Long? {
        if (deadlineIso == null) return null
        val deadline = parse(deadlineIso) ?: return null
        val server = parse(serverTimeIso) ?: return null
        val millis = java.time.Duration.between(server, deadline).toMillis() - elapsedMillis
        return maxOf(0L, Math.ceil(millis / 1000.0).toLong())
    }

    /** «۰۵:۰۹» یا «۱:۰۵:۰۹» */
    fun formatCountdown(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        return toPersianDigits(text)
    }

    /** «۲۳ مهر ۱۴۰۵، ۸:۰۰» به وقت محلی گوشی. */
    fun formatDateTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = iso?.let { parse(it) } ?: return ""
        val local = t.atZoneSameInstant(zone)
        return Jalali.formatLong(local.toLocalDate()) + "، " + toPersianDigits("%d:%02d".format(local.hour, local.minute))
    }

    fun formatSize(bytes: Long): String = toPersianDigits(
        when {
            bytes < 1024 -> "$bytes بایت"
            bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} کیلوبایت"
            else -> "%.1f مگابایت".format(bytes / (1024.0 * 1024.0))
        },
    )

    fun formatScore(score: Double): String {
        val text = if (score % 1.0 == 0.0) score.toLong().toString() else "%.1f".format(score)
        return toPersianDigits(text)
    }

    fun parse(iso: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(iso) }.getOrNull()

    fun courseFinished(c: CourseDto): Boolean = c.itemCount > 0 && c.completedCount >= c.itemCount

    /** کاری که همکار در کارت آزمون می‌تواند انجام دهد. */
    sealed interface ExamAction {
        data class Continue(val attemptId: String) : ExamAction
        data object Start : ExamAction
        data class ViewResult(val attemptId: String) : ExamAction
        data class Unavailable(val reason: String) : ExamAction
    }

    fun examAction(e: ExamDto): ExamAction {
        e.inProgressAttemptId?.let { return ExamAction.Continue(it) }
        val canStart = e.state == "open" && e.attemptsUsed < e.maxAttempts && e.questionCount > 0
        if (canStart) return ExamAction.Start
        e.lastAttemptId?.let { return ExamAction.ViewResult(it) }
        return ExamAction.Unavailable(
            when {
                e.state == "upcoming" -> "از ${formatDateTime(e.opensAt)} باز می‌شود"
                e.state == "closed" -> "مهلت شرکت در آزمون تمام شده است"
                e.questionCount == 0 -> "آزمون هنوز آماده نیست"
                else -> "تعداد دفعات مجاز شرکت تمام شده است"
            },
        )
    }

    /** کارهای مانده برای نشان روی کاشی «آموزش»: دوره‌های اجباری ناتمام و آزمون‌های باز شرکت‌نکرده. */
    fun pendingCount(courses: List<CourseDto>, exams: List<ExamDto>): Int =
        courses.count { it.isMandatory && !courseFinished(it) } +
            exams.count { it.state == "open" && it.attemptsUsed == 0 && it.questionCount > 0 }
}
