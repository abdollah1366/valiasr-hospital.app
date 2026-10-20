package com.barati.razi.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.barati.razi.RaziApp
import com.barati.razi.core.data.FlushOutcome
import java.util.concurrent.TimeUnit

/** ارسال پیام‌های گفتگوی مانده در صف، هر وقت اینترنت وصل باشد (حتی اگر اپ بسته باشد). */
class ChatSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RaziApp).container
        if (!container.authRepository.hasSession) return Result.success()
        return when (container.chatRepository.flush()) {
            FlushOutcome.DONE -> Result.success()
            FlushOutcome.RETRY_LATER -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "chat-send"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChatSendWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
