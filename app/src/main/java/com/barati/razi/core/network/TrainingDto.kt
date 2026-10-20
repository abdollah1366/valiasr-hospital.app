package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaDto(
    val id: String,
    @SerialName("original_name") val originalName: String,
    @SerialName("content_type") val contentType: String,
    val size: Long,
    /** پیوند امضاشده و موقت (بدون نیاز به توکن)، نسبت به ریشه سرور. */
    val url: String,
)

@Serializable
data class CourseItemDto(
    val id: String,
    val kind: String,
    val title: String,
    val body: String? = null,
    val media: MediaDto? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val viewed: Boolean = false,
    val completed: Boolean = false,
)

/** هم برای فهرست دوره‌ها و هم جزئیات دوره (items و exams فقط در جزئیات پر است). */
@Serializable
data class CourseDto(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("is_mandatory") val isMandatory: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0,
    val items: List<CourseItemDto> = emptyList(),
    val exams: List<ExamDto> = emptyList(),
)

@Serializable
data class ExamDto(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("course_id") val courseId: String? = null,
    /** draft | upcoming | open | closed */
    val state: String,
    @SerialName("opens_at") val opensAt: String? = null,
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("time_limit_minutes") val timeLimitMinutes: Int? = null,
    @SerialName("max_attempts") val maxAttempts: Int = 1,
    @SerialName("pass_score") val passScore: Int = 0,
    @SerialName("show_answers") val showAnswers: String = "never",
    @SerialName("question_count") val questionCount: Int = 0,
    @SerialName("total_points") val totalPoints: Int = 0,
    @SerialName("attempts_used") val attemptsUsed: Int = 0,
    @SerialName("best_score") val bestScore: Double? = null,
    val passed: Boolean? = null,
    @SerialName("in_progress_attempt_id") val inProgressAttemptId: String? = null,
    @SerialName("last_attempt_id") val lastAttemptId: String? = null,
)

@Serializable
data class AttemptOptionDto(
    val id: String,
    val text: String,
    /** فقط وقتی پاسخ‌ها قابل نمایش است. */
    val correct: Boolean? = null,
)

@Serializable
data class AttemptQuestionDto(
    val id: String,
    /** single | multiple | true_false */
    val kind: String,
    val text: String,
    val points: Int = 1,
    val options: List<AttemptOptionDto>,
    val selected: List<String> = emptyList(),
    val correct: Boolean? = null,
    val explanation: String? = null,
)

@Serializable
data class AttemptDto(
    val id: String,
    @SerialName("exam_id") val examId: String,
    @SerialName("exam_title") val examTitle: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("server_time") val serverTime: String,
    val finished: Boolean,
    val score: Double? = null,
    val passed: Boolean? = null,
    @SerialName("pass_score") val passScore: Int = 0,
    @SerialName("answers_visible") val answersVisible: Boolean = false,
    val questions: List<AttemptQuestionDto>,
)

@Serializable
data class ViewStartDto(@SerialName("view_id") val viewId: String)

@Serializable
data class ViewProgressRequest(val seconds: Int, val progress: Int)

@Serializable
data class AnswersRequest(val answers: Map<String, List<String>>)
