package com.barati.razi.core.data

import com.barati.razi.core.network.AnswersRequest
import com.barati.razi.core.network.ApiError
import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AttemptDto
import com.barati.razi.core.network.CourseDto
import com.barati.razi.core.network.ExamDto
import com.barati.razi.core.network.MediaDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.ViewProgressRequest
import com.barati.razi.core.network.ViewStartDto
import com.barati.razi.core.network.apiCall
import com.barati.razi.core.training.TrainingLogic
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * بخش آموزش همکاران.
 * فایل‌ها با پیوند امضاشده دریافت می‌شوند و به توکن نیاز ندارند؛ [origin] ریشه سرور است (مثلاً http://host:8000).
 */
class TrainingRepository(
    private val api: ApiService,
    private val http: OkHttpClient,
    private val cacheDir: File,
    private val origin: () -> String,
    /** برای کارهایی که باید پس از بسته شدن صفحه هم انجام شوند (ثبت آخرین وضعیت بازدید). */
    val scope: CoroutineScope,
) {
    suspend fun courses(): Result<List<CourseDto>> = apiCall { api.trainingCourses() }
    suspend fun course(id: String): Result<CourseDto> = apiCall { api.trainingCourse(id) }
    suspend fun exams(): Result<List<ExamDto>> = apiCall { api.trainingExams() }

    suspend fun startView(itemId: String): Result<ViewStartDto> = apiCall { api.startView(itemId) }
    suspend fun updateView(viewId: String, seconds: Int, progress: Int): Result<Unit> =
        apiCall { api.updateView(viewId, ViewProgressRequest(seconds.coerceIn(0, 24 * 3600), progress.coerceIn(0, 100))) }

    suspend fun startAttempt(examId: String): Result<AttemptDto> = apiCall { api.startAttempt(examId) }
    suspend fun attempt(id: String): Result<AttemptDto> = apiCall { api.attempt(id) }
    suspend fun saveAnswers(id: String, answers: Map<String, List<String>>): Result<AttemptDto> =
        apiCall { api.saveAnswers(id, AnswersRequest(answers)) }
    suspend fun submit(id: String, answers: Map<String, List<String>>): Result<AttemptDto> =
        apiCall { api.submitAttempt(id, AnswersRequest(answers)) }

    suspend fun pendingCount(): Int {
        val courses = (courses() as? Result.Ok)?.value ?: return 0
        val exams = (exams() as? Result.Ok)?.value ?: return 0
        return TrainingLogic.pendingCount(courses, exams)
    }

    fun absoluteUrl(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else origin().trimEnd('/') + path

    /** فایل را (در صورت نبودن در حافظه موقت) دریافت می‌کند؛ [onProgress] بین ۰ و ۱. */
    suspend fun download(media: MediaDto, onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(cacheDir, "training").apply { mkdirs() }
            val ext = media.originalName.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
            val target = File(dir, if (ext.isEmpty()) media.id else "${media.id}.$ext")
            if (target.isFile && target.length() == media.size) return@withContext Result.Ok(target)
            val part = File(dir, target.name + ".part")
            http.newCall(Request.Builder().url(absoluteUrl(media.url)).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Err(ApiError.parse(resp.code, null))
                val body = resp.body ?: return@withContext Result.Err(ApiError.parse(resp.code, null))
                val total = body.contentLength().takeIf { it > 0 } ?: media.size
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) return@withContext Result.Err(ApiError(null, "io", "ذخیره فایل ممکن نشد"))
            Result.Ok(target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.Err(ApiError.from(e))
        }
    }
}
