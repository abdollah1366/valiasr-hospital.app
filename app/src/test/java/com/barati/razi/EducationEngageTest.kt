package com.barati.razi

import com.barati.razi.core.network.EducationDto
import com.barati.razi.core.network.TicketDetailDto
import com.barati.razi.feature.education.AttachmentCheck
import com.barati.razi.feature.education.TicketAttachmentRules
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EducationEngageTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val mb = 1024L * 1024L

    @Test
    fun assignedEducationCarriesSeenFlagAndNote() {
        val e = json.decodeFromString<EducationDto>(
            """{"id":"a","title":"t","kind":"video","assigned_at":"2026-10-21T10:00:00+03:30",
            "assignment_note":"بخوانید","seen":false,"rating_average":4.5,"rating_count":3}""",
        )
        assertFalse(e.seen)
        assertEquals("بخوانید", e.assignmentNote)
        assertEquals(4.5, e.ratingAverage!!, 0.001)
    }

    @Test
    fun libraryItemsCountAsSeenByDefault() {
        val e = json.decodeFromString<EducationDto>("""{"id":"a","title":"t","kind":"article"}""")
        assertTrue(e.seen)
        assertNull(e.ratingAverage)
        assertEquals(0, e.ratingCount)
    }

    @Test
    fun parsesTicketWithAttachment() {
        val t = json.decodeFromString<TicketDetailDto>(
            """{"id":"t","subject":"s","status":"open","updated_at":"2026-10-21T10:00:00Z","messages":[
            {"id":"m1","from_staff":false,"body":"سلام","created_at":"2026-10-21T10:00:00Z"},
            {"id":"m2","from_staff":true,"staff_name":"مسئول","created_at":"2026-10-21T10:05:00Z",
             "attachment":{"original_name":"a.png","content_type":"image/png","size":20,"url":"/api/v1/training/media/x"}}]}""",
        )
        assertEquals(2, t.messages.size)
        assertEquals("image/png", t.messages[1].attachment!!.contentType)
        assertNull(t.messages[0].attachment)
    }

    @Test
    fun attachmentRulesMatchServerLimits() {
        val ok = TicketAttachmentRules.check("image/jpeg", 5 * mb, 123) as AttachmentCheck.Ok
        assertEquals("attachment-123.jpg", ok.filename)
        assertEquals(AttachmentCheck.TooLarge(10), TicketAttachmentRules.check("image/png", 11 * mb, 1))
        assertTrue(TicketAttachmentRules.check("video/mp4", 100 * mb, 1) is AttachmentCheck.Ok)
        assertEquals(AttachmentCheck.TooLarge(100), TicketAttachmentRules.check("video/mp4", 100 * mb + 1, 1))
        assertEquals(AttachmentCheck.Unsupported, TicketAttachmentRules.check("application/pdf", 1, 1))
        assertEquals(AttachmentCheck.Unsupported, TicketAttachmentRules.check(null, 1, 1))
        assertTrue(TicketAttachmentRules.check("audio/x-m4a", null, 7) is AttachmentCheck.Ok)
    }
}
