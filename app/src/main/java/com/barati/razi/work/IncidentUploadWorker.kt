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

/** ارسال گزارش‌های صف آفلاین هر وقت اینترنت وصل باشد. */
class IncidentUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as RaziApp).container.incidentRepository
        return when (repo.flush().outcome) {
            FlushOutcome.DONE -> Result.success()
            FlushOutcome.RETRY_LATER -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "incident-upload"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<IncidentUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
