package com.barati.razi

import com.barati.razi.core.network.EducationDto
import com.barati.razi.core.network.EducationFiltersDto
import com.barati.razi.feature.education.toggled
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EducationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesListItemWithDefaults() {
        val e = json.decodeFromString<EducationDto>(
            """{"id":"a","title":"رژیم دیابت","kind":"article","care_type":"nutrition","condition":"دیابت",
            "has_file":false,"reviewed_on":"2026-10-01","extra":1}""",
        )
        assertEquals("nutrition", e.careType)
        assertEquals("2026-10-01", e.reviewedOn)
        assertNull(e.body)
        assertNull(e.fileVersion)
    }

    @Test
    fun parsesFilters() {
        val f = json.decodeFromString<EducationFiltersDto>(
            """{"conditions":["دیابت"],"care_types":["prevention"],"kinds":["video"],"specialties":[{"id":"s","name":"قلب"}]}""",
        )
        assertEquals("قلب", f.specialties.single().name)
        assertEquals(listOf("video"), f.kinds)
    }

    @Test
    fun pickingSameFilterAgainClearsIt() {
        assertEquals("video", toggled(null, "video"))
        assertEquals("audio", toggled("video", "audio"))
        assertNull(toggled("video", "video"))
    }
}
