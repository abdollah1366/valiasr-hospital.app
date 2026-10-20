package com.barati.razi

import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.AttemptDto
import com.barati.razi.core.network.CourseDto
import com.barati.razi.core.network.ExamDto
import com.barati.razi.core.training.TrainingLogic
import com.barati.razi.core.training.TrainingLogic.ExamAction
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLogicTest {
    private fun exam(
        state: String = "open",
        used: Int = 0,
        max: Int = 1,
        questions: Int = 3,
        inProgress: String? = null,
        last: String? = null,
    ) = ExamDto(
        id = "e1",
        title = "آزمون",
        state = state,
        maxAttempts = max,
        attemptsUsed = used,
        questionCount = questions,
        inProgressAttemptId = inProgress,
        lastAttemptId = last,
        opensAt = "2026-10-15T04:30:00Z",
    )

    @Test
    fun toggleSingleReplacesAndMultipleToggles() {
        assertEquals(listOf("b"), TrainingLogic.toggle("single", listOf("a"), "b"))
        assertEquals(listOf("a"), TrainingLogic.toggle("true_false", listOf("b"), "a"))
        assertEquals(listOf("a", "c"), TrainingLogic.toggle("multiple", listOf("a"), "c"))
        assertEquals(listOf("c"), TrainingLogic.toggle("multiple", listOf("a", "c"), "a"))
    }

    @Test
    fun progressOnlyMovesForward() {
        assertEquals(60, TrainingLogic.mergeProgress(60, 20))
        assertEquals(90, TrainingLogic.mergeProgress(60, 90))
        assertEquals(100, TrainingLogic.mergeProgress(0, 250))
        assertEquals(0, TrainingLogic.percent(10, 0))
        assertEquals(0, TrainingLogic.percent(10, Long.MIN_VALUE + 1)) // مدت نامعلوم فیلم
        assertEquals(50, TrainingLogic.percent(30_000, 60_000))
        assertEquals(100, TrainingLogic.percent(70_000, 60_000))
    }

    @Test
    fun remainingTimeUsesServerClock() {
        val server = "2026-10-15T10:00:00.250000+00:00"
        val deadline = "2026-10-15T10:15:00.250000Z"
        assertEquals(900L, TrainingLogic.remainingSeconds(deadline, server, 0))
        assertEquals(840L, TrainingLogic.remainingSeconds(deadline, server, 60_000))
        assertEquals(1L, TrainingLogic.remainingSeconds(deadline, server, 899_500))
        assertEquals(0L, TrainingLogic.remainingSeconds(deadline, server, 2_000_000))
        assertNull(TrainingLogic.remainingSeconds(null, server, 0))
    }

    @Test
    fun formatting() {
        assertEquals("۰۵:۰۹", TrainingLogic.formatCountdown(309))
        assertEquals("۱:۰۰:۰۵", TrainingLogic.formatCountdown(3605))
        assertEquals("۷۵", TrainingLogic.formatScore(75.0))
        assertEquals("۲۳ مهر ۱۴۰۵، ۸:۰۰", TrainingLogic.formatDateTime("2026-10-15T04:30:00Z", ZoneId.of("Asia/Tehran")))
        assertEquals("", TrainingLogic.formatDateTime(null))
    }

    @Test
    fun examActions() {
        assertEquals(ExamAction.Start, TrainingLogic.examAction(exam()))
        assertEquals(ExamAction.Continue("a1"), TrainingLogic.examAction(exam(used = 1, inProgress = "a1")))
        assertEquals(ExamAction.Start, TrainingLogic.examAction(exam(used = 1, max = 2, last = "a1")))
        assertEquals(ExamAction.ViewResult("a1"), TrainingLogic.examAction(exam(used = 1, last = "a1")))
        assertEquals(ExamAction.ViewResult("a1"), TrainingLogic.examAction(exam(state = "closed", used = 1, last = "a1")))
        val upcoming = TrainingLogic.examAction(exam(state = "upcoming"))
        assertTrue(upcoming is ExamAction.Unavailable && upcoming.reason.contains("باز می‌شود"))
        assertTrue(TrainingLogic.examAction(exam(questions = 0)) is ExamAction.Unavailable)
    }

    @Test
    fun pendingCountsMandatoryUnfinishedAndOpenUntakenExams() {
        val courses = listOf(
            CourseDto(id = "1", title = "a", isMandatory = true, itemCount = 3, completedCount = 1),
            CourseDto(id = "2", title = "b", isMandatory = true, itemCount = 2, completedCount = 2),
            CourseDto(id = "3", title = "c", isMandatory = false, itemCount = 2, completedCount = 0),
        )
        val exams = listOf(exam(), exam(used = 1, last = "x"), exam(state = "closed"))
        assertEquals(2, TrainingLogic.pendingCount(courses, exams))
    }

    @Test
    fun parsesServerAttempt() {
        val json = """
            {"id":"a1","exam_id":"e1","exam_title":"آزمون","started_at":"2026-10-15T10:00:00+00:00",
             "deadline_at":null,"submitted_at":null,"server_time":"2026-10-15T10:00:01.5+00:00",
             "finished":false,"score":null,"passed":null,"pass_score":60,"answers_visible":false,
             "questions":[{"id":"q1","kind":"single","text":"؟","points":1,
               "options":[{"id":"b","text":"دو"},{"id":"a","text":"یک"}],"selected":["b"],
               "correct":null,"explanation":null}]}
        """.trimIndent()
        val a = AppJson.decodeFromString(AttemptDto.serializer(), json)
        assertEquals(listOf("b", "a"), a.questions[0].options.map { it.id })
        assertEquals(listOf("b"), a.questions[0].selected)
        assertNull(a.questions[0].options[0].correct)
    }
}
