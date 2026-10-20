package com.barati.razi.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.barati.razi.R
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.util.Jalali
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** برچسب‌های بخش مراجعان به زبان انتخابی؛ کلیدها همان مقادیر سرور هستند. */
object PatientLabels {
    val feedbackKinds = listOf("suggestion", "criticism", "complaint", "praise")

    @Composable
    fun feedbackKind(key: String): String = when (key) {
        "suggestion" -> stringResource(R.string.feedback_kind_suggestion)
        "criticism" -> stringResource(R.string.feedback_kind_criticism)
        "complaint" -> stringResource(R.string.feedback_kind_complaint)
        "praise" -> stringResource(R.string.feedback_kind_praise)
        else -> key
    }

    @Composable
    fun feedbackStatus(key: String): String = when (key) {
        "received" -> stringResource(R.string.feedback_status_received)
        "reviewing" -> stringResource(R.string.feedback_status_reviewing)
        "answered" -> stringResource(R.string.feedback_status_answered)
        else -> key
    }

    @Composable
    fun announcementCategory(key: String): String = when (key) {
        "news" -> stringResource(R.string.announcement_news)
        "circular" -> stringResource(R.string.announcement_circular)
        "guideline" -> stringResource(R.string.announcement_guideline)
        "notice" -> stringResource(R.string.announcement_notice)
        else -> key
    }

    /** تاریخ شمسی برای فارسی، میلادی برای زبان‌های دیگر. */
    @Composable
    fun date(iso: String?): String {
        val language = LocalAppLanguage.current
        if (language == AppLanguage.FA || iso.isNullOrBlank()) return Jalali.formatIso(iso)
        return runCatching {
            LocalDate.parse(iso.take(10))
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.forLanguageTag(language.code)))
        }.getOrDefault(iso)
    }
}
