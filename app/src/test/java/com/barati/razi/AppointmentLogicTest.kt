package com.barati.razi

import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.core.network.SlotDto
import com.barati.razi.core.notifications.NotificationLogic
import com.barati.razi.core.notifications.NotificationTarget
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

private fun appt(id: String, at: String, status: String = "booked") = PatientAppointmentDto(
    id = id,
    trackingCode = "CODE$id",
    status = status,
    startsAt = at,
    minutes = 15,
    doctorId = "d",
    doctorName = "دکتر نمونه",
    specialtyName = "قلب",
)

class AppointmentLogicTest {
    @Test
    fun groupsSlotsByTehranDayInOrder() {
        // ۲۲:۰۰ گرینویچ یعنی ۰۱:۳۰ روز بعد در تهران
        val slots = listOf(
            SlotDto("b", "2026-10-24T10:00:00+03:30", 15),
            SlotDto("c", "2026-10-24T22:00:00Z", 15),
            SlotDto("a", "2026-10-24T09:00:00+03:30", 15),
        )
        val days = AppointmentLogic.groupByDay(slots)
        assertEquals(listOf(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25)), days.keys.toList())
        assertEquals(listOf("a", "b"), days.getValue(LocalDate.of(2026, 10, 24)).map { it.id })
    }

    @Test
    fun timeAndDayTextPerLanguage() {
        assertEquals("۰۹:۳۰", AppointmentLogic.timeText("2026-10-24T06:00:00Z", AppLanguage.FA))
        assertEquals("09:30", AppointmentLogic.timeText("2026-10-24T06:00:00Z", AppLanguage.EN))
        assertEquals("شنبه ۲ آبان", AppointmentLogic.dayText(LocalDate.of(2026, 10, 24), AppLanguage.FA))
        assertEquals("Saturday 24 October", AppointmentLogic.dayText(LocalDate.of(2026, 10, 24), AppLanguage.EN))
    }

    @Test
    fun upcomingKeepsOnlyFutureBookedSoonestFirst() {
        val now = Instant.parse("2026-10-21T08:00:00Z")
        val items = listOf(
            appt("late", "2026-10-30T09:00:00+03:30"),
            appt("past", "2026-10-20T09:00:00+03:30"),
            appt("cancelled", "2026-10-25T09:00:00+03:30", status = "cancelled"),
            appt("soon", "2026-10-22T09:00:00+03:30"),
        )
        assertEquals(listOf("soon", "late"), AppointmentLogic.upcoming(items, now).map { it.id })
    }

    @Test
    fun remindersOneDayAndTwoHoursBeforeSkipPassedOnes() {
        val start = Instant.parse("2026-10-22T10:00:00Z")
        assertEquals(
            listOf(Instant.parse("2026-10-21T10:00:00Z"), Instant.parse("2026-10-22T08:00:00Z")),
            AppointmentLogic.reminderTimes(start, Instant.parse("2026-10-21T09:00:00Z")),
        )
        // کمتر از یک روز مانده: فقط یادآور دو ساعت پیش
        assertEquals(
            listOf(Instant.parse("2026-10-22T08:00:00Z")),
            AppointmentLogic.reminderTimes(start, Instant.parse("2026-10-21T12:00:00Z")),
        )
        assertEquals(emptyList<Instant>(), AppointmentLogic.reminderTimes(start, Instant.parse("2026-10-22T09:00:00Z")))
    }

    @Test
    fun appointmentNotificationOpensDoctorPatients() {
        assertEquals(NotificationTarget.Appointments, NotificationLogic.target("appointment", "x"))
    }
}
