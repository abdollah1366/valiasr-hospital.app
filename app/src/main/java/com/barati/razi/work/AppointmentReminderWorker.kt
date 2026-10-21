package com.barati.razi.work

import android.content.Context
import android.content.res.Configuration
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.notify.SystemNotifier
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * یادآور نوبت روی خود گوشی (بدون اینترنت هم کار می‌کند): یک روز و دو ساعت پیش از هر نوبت فعال.
 * متن یادآور هنگام زمان‌بندی به زبان مراجع ساخته می‌شود.
 */
class AppointmentReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val body = inputData.getString(KEY_BODY).orEmpty()
        SystemNotifier.showReminder(applicationContext, inputData.getString(KEY_ID).orEmpty(), title, body)
        return Result.success()
    }

    companion object {
        private const val TAG = "appointment-reminder"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"

        /** همه یادآورهای قبلی لغو و برای نوبت‌های فعال دوباره ساخته می‌شوند. */
        fun sync(context: Context, items: List<PatientAppointmentDto>, language: AppLanguage, now: Instant = Instant.now()) {
            val wm = WorkManager.getInstance(context)
            wm.cancelAllWorkByTag(TAG)
            val res = localized(context, language)
            for (a in AppointmentLogic.upcoming(items, now)) {
                val start = AppointmentLogic.instant(a.startsAt)
                val title = res.getString(R.string.appt_reminder_title, a.doctorName)
                val body = res.getString(
                    R.string.appt_reminder_body,
                    AppointmentLogic.dateTimeText(a.startsAt, language),
                    a.clinicName ?: a.specialtyName,
                )
                for (at in AppointmentLogic.reminderTimes(start, now)) {
                    val request = OneTimeWorkRequestBuilder<AppointmentReminderWorker>()
                        .setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf(KEY_ID to a.id + at.epochSecond, KEY_TITLE to title, KEY_BODY to body))
                        .addTag(TAG)
                        .build()
                    wm.enqueue(request)
                }
            }
        }

        private fun localized(context: Context, language: AppLanguage) =
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.code)) },
            ).resources
    }
}
