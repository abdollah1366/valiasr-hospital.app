package com.barati.razi.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.barati.razi.RaziApp
import com.barati.razi.core.network.Result as ApiResult
import com.barati.razi.notify.SystemNotifier
import java.util.concurrent.TimeUnit

/**
 * بدون سرویس پوش گوگل: هر ۱۵ دقیقه (کمترین فاصله WorkManager) اعلان‌های تازه از سرور گرفته
 * و به‌صورت اعلان سیستمی نشان داده می‌شوند. ممکن است سیستم در حالت صرفه‌جویی باتری این کار را عقب بیندازد.
 */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RaziApp).container
        if (!container.authRepository.hasSession) return Result.success()
        // ممکن است اپ بسته باشد و این کار در فرایند تازه اجرا شود؛ آدرس سرور (نسخه debug) خوانده می‌شود.
        container.loadServerOrigin()
        return when (val r = container.notificationRepository.takeNewForAlert()) {
            is ApiResult.Ok -> {
                SystemNotifier.show(applicationContext, r.value)
                Result.success()
            }
            is ApiResult.Err -> if (r.error.isRetryable) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val NAME = "notification-poll"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
