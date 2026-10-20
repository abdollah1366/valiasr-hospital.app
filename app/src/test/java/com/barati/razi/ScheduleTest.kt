package com.barati.razi

import com.barati.razi.core.data.Keys
import com.barati.razi.core.data.ScheduleRepository
import com.barati.razi.core.network.HttpFactory
import com.barati.razi.core.network.MyShiftDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.ScheduleCellDto
import com.barati.razi.core.network.SwapCellDto
import com.barati.razi.core.network.SwapDto
import com.barati.razi.core.network.SchedulePersonDto
import com.barati.razi.core.notifications.NotificationLogic
import com.barati.razi.core.notifications.NotificationTarget
import com.barati.razi.core.schedule.ScheduleLogic
import com.barati.razi.core.schedule.ShiftTone
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val TEHRAN = ZoneOffset.ofHoursMinutes(3, 30)

private fun shift(day: String, kind: String = "work", label: String = "ص", starts: String? = "${day}T07:30:00+03:30") =
    MyShiftDto(
        day = day, jalali = "1405/08/01", code = 31, label = label, title = "صبح", kind = kind,
        unitId = "u", unitName = "ICU", scheduleId = "s", startsAt = starts, endsAt = starts?.replace("07:30", "14:30"),
    )

class ScheduleLogicTest {
    @Test
    fun weekStartsOnSaturday() {
        assertEquals(0, ScheduleLogic.column(LocalDate.parse("2026-10-24"))) // شنبه
        assertEquals(1, ScheduleLogic.column(LocalDate.parse("2026-10-25"))) // یکشنبه
        assertEquals(6, ScheduleLogic.column(LocalDate.parse("2026-10-23"))) // جمعه
    }

    @Test
    fun monthRowsPadToFullWeeksAndPlaceShifts() {
        // ۱ آبان ۱۴۰۵ جمعه است و آبان ۳۰ روز دارد: ۶ خانه خالی + ۳۰ روز + ۶ خانه پایانی
        val rows = ScheduleLogic.monthRows(1405, 8, listOf(shift("2026-10-23"), shift("2026-10-24", kind = "off", label = "ا")))
        assertEquals(6, rows.size)
        assertTrue(rows.all { it.size == 7 })
        assertEquals(List(6) { 0 }, rows[0].take(6).map { it.day })
        assertEquals(1, rows[0][6].day)
        assertEquals("ص", rows[0][6].shift?.label)
        assertEquals("ا", rows[1][0].shift?.label)
        assertEquals(30, rows.flatten().maxOf { it.day })
        assertEquals(30, rows.flatten().count { it.day != 0 })
    }

    @Test
    fun workShiftWinsWhenTwoUnitsShareADay() {
        val rows = ScheduleLogic.monthRows(
            1405, 8,
            listOf(shift("2026-10-23", kind = "leave", label = "م"), shift("2026-10-23", label = "ص")),
        )
        assertEquals("ص", rows[0][6].shift?.label)
    }

    @Test
    fun hasShiftsLooksAtThatMonthOnly() {
        val items = listOf(shift("2026-10-23"))
        assertTrue(ScheduleLogic.hasShifts(1405, 8, items))
        assertFalse(ScheduleLogic.hasShifts(1405, 9, items))
    }

    @Test
    fun tonesFollowStartHourInDeviceZone() {
        assertEquals(ShiftTone.Morning, ScheduleLogic.tone("work", "2026-10-23T07:30:00+03:30", TEHRAN))
        assertEquals(ShiftTone.Evening, ScheduleLogic.tone("work", "2026-10-23T13:30:00+03:30", TEHRAN))
        assertEquals(ShiftTone.Night, ScheduleLogic.tone("work", "2026-10-23T19:30:00+03:30", TEHRAN))
        assertEquals(ShiftTone.Off, ScheduleLogic.tone("off", null, TEHRAN))
        assertEquals(ShiftTone.Leave, ScheduleLogic.tone("leave", null, TEHRAN))
    }

    @Test
    fun timeRangeMarksNextDayEnd() {
        assertEquals("07:30 تا 14:30", ScheduleLogic.timeRange("2026-10-23T07:30:00+03:30", "2026-10-23T14:30:00+03:30", TEHRAN))
        assertEquals("19:30 تا 08:00 (فردا)", ScheduleLogic.timeRange("2026-10-23T19:30:00+03:30", "2026-10-24T08:00:00+03:30", TEHRAN))
        assertNull(ScheduleLogic.timeRange(null, null, TEHRAN))
    }

    @Test
    fun untilTextCountsDownThenReportsInProgress() {
        val start = "2026-10-23T07:30:00+03:30"
        val end = "2026-10-23T14:30:00+03:30"
        fun at(s: String) = OffsetDateTime.parse(s)
        assertEquals("30 دقیقه دیگر", ScheduleLogic.untilText(start, end, at("2026-10-23T07:00:00+03:30")))
        assertEquals("2 ساعت دیگر", ScheduleLogic.untilText(start, end, at("2026-10-23T05:30:00+03:30")))
        assertEquals("3 روز دیگر", ScheduleLogic.untilText(start, end, at("2026-10-20T07:30:00+03:30")))
        assertEquals("در جریان است", ScheduleLogic.untilText(start, end, at("2026-10-23T09:00:00+03:30")))
        assertNull(ScheduleLogic.untilText(start, end, at("2026-10-23T15:00:00+03:30")))
    }

    @Test
    fun swapHelpers() {
        assertTrue(ScheduleLogic.isSwappable(ScheduleCellDto("2026-10-23", 31, "ص", "work")))
        assertFalse(ScheduleLogic.isSwappable(ScheduleCellDto("2026-10-23", 50, "ا", "off")))
        assertFalse(ScheduleLogic.isSwappable(ScheduleCellDto("2026-10-23", null)))
        val p = SchedulePersonDto("1", "الف")
        val c = SwapCellDto("2026-10-23", "1405/08/01", 31, "ص")
        fun swap(vararg actions: String) = SwapDto("x", "s", "ICU", "آبان ۱۴۰۵", p, p, c, c, null, "pending_partner", null, actions.toList())
        assertEquals(1, ScheduleLogic.needsMyAction(listOf(swap("accept", "reject"), swap("cancel"))).size)
        assertEquals("منتظر پاسخ همکار", ScheduleLogic.swapStatusName("pending_partner"))
        assertNull(ScheduleLogic.swapActionName("unknown"))
    }

    @Test
    fun notificationOpensScheduleRosterOrHome() {
        assertEquals(NotificationTarget.Schedule("abc"), NotificationLogic.target("schedule", "abc"))
        assertEquals(NotificationTarget.Schedule(null), NotificationLogic.target("schedule", null))
    }
}

class ScheduleRepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryStore()
    private lateinit var repo: ScheduleRepository

    @Before
    fun setUp() {
        server.start()
        val api = HttpFactory.create(server.url("/api/v1/").toString(), MemoryTokens(), { null }, false) {}
        repo = ScheduleRepository(api, store) { 1_000L }
    }

    @After
    fun tearDown() = server.shutdown()

    private val mineJson = """{"start":"2026-10-23","end":"2026-12-21","items":[
        {"day":"2026-10-23","jalali":"1405/08/01","code":31,"label":"ص","title":"صبح بهره‌وری","kind":"work",
         "unit_id":"u1","unit_name":"ICU","schedule_id":"s1","starts_at":"2026-10-23T07:30:00+03:30","ends_at":"2026-10-23T14:30:00+03:30","work_hours":7.0}],
        "next_shift":null,"extra_field_from_newer_server":1}"""

    @Test
    fun mineIsParsedAndCachedForThatUserOnly() = runBlocking {
        server.enqueue(MockResponse().setBody(mineJson))
        val r = repo.mine("me")
        assertTrue(r is Result.Ok)
        assertEquals(1, (r as Result.Ok).value.items.size)
        assertEquals("/api/v1/schedules/mine", server.takeRequest().path)

        val cached = repo.cachedMine("me")
        assertNotNull(cached)
        assertEquals(1_000L, cached!!.fetchedAtMillis)
        assertEquals("ICU", cached.data.items.single().unitName)
        assertNull(repo.cachedMine("someone-else")) // گوشی مشترک: برنامه کاربر قبلی دیده نمی‌شود
    }

    @Test
    fun failedRefreshKeepsLastCache() = runBlocking {
        server.enqueue(MockResponse().setBody(mineJson))
        repo.mine("me")
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertTrue(repo.mine("me") is Result.Err)
        assertNotNull(repo.cachedMine("me"))
    }

    @Test
    fun customRangeIsNotCached() = runBlocking {
        server.enqueue(MockResponse().setBody(mineJson))
        repo.mine("me", "2026-10-23", "2026-11-01")
        assertNull(store.get(Keys.MY_SCHEDULE))
        assertEquals("/api/v1/schedules/mine?start=2026-10-23&end=2026-11-01", server.takeRequest().path)
    }

    @Test
    fun swapAnswerPostsToActionPath() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"w1","schedule_id":"s1","unit_name":"ICU","month_title":"آبان ۱۴۰۵",
                "requester":{"id":"1","full_name":"الف"},"partner":{"id":"2","full_name":"ب"},
                "requester_day":{"day":"2026-10-23","jalali":"1405/08/01","code":31,"label":"ص"},
                "partner_day":{"day":"2026-10-24","jalali":"1405/08/02","code":32,"label":"ع"},
                "note":null,"status":"pending_manager","actions":["cancel"]}""",
            ),
        )
        val r = repo.answerSwap("w1", "accept")
        assertEquals("pending_manager", (r as Result.Ok).value.status)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/schedules/swaps/w1/accept", req.path)
    }
}
