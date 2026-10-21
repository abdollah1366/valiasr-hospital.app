package com.barati.razi

import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.DoctorDto
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.feature.appointments.ratingText
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun ratingTextFollowsLanguageDigits() {
        assertEquals("4.5", ratingText(4.5, AppLanguage.EN))
        assertEquals("۴.۵", ratingText(4.5, AppLanguage.FA))
    }

    @Test
    fun doctorWithoutReviewsHasNoRating() {
        val d = json.decodeFromString<DoctorDto>(
            """{"id":"1","full_name":"د","specialty_id":"s","specialty_name":"قلب"}""",
        )
        assertNull(d.ratingAverage)
        assertEquals(0, d.ratingCount)
    }

    @Test
    fun appointmentCarriesReviewState() {
        val a = json.decodeFromString<PatientAppointmentDto>(
            """{"id":"a","tracking_code":"c","status":"attended","starts_at":"2026-10-24T10:00:00+03:30","minutes":15,
            "doctor_id":"d","doctor_name":"x","specialty_name":"y","can_review":true,
            "my_review":{"id":"r","stars":4,"comment":null,"status":"pending"}}""",
        )
        assertTrue(a.canReview)
        assertEquals(4, a.myReview?.stars)
        assertEquals("pending", a.myReview?.status)
    }
}
