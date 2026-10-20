package com.barati.razi

import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.JalaliDate
import com.barati.razi.core.util.toLatinDigits
import com.barati.razi.core.util.toPersianDigits
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliTest {
    @Test
    fun knownDates() {
        assertEquals(JalaliDate(1403, 1, 1), Jalali.fromGregorian(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1405, 7, 22), Jalali.fromGregorian(LocalDate.of(2026, 10, 14)))
        assertEquals(JalaliDate(1399, 12, 30), Jalali.fromGregorian(LocalDate.of(2021, 3, 20)))
        assertEquals(LocalDate.of(2025, 3, 21), Jalali.toGregorian(JalaliDate(1404, 1, 1)))
    }

    @Test
    fun roundTripEveryDayForTenYears() {
        var d = LocalDate.of(2018, 1, 1)
        while (d.isBefore(LocalDate.of(2028, 1, 1))) {
            assertEquals(d, Jalali.toGregorian(Jalali.fromGregorian(d)))
            d = d.plusDays(1)
        }
    }

    @Test
    fun leapYears() {
        assertTrue(Jalali.isLeapYear(1399))
        assertTrue(Jalali.isLeapYear(1403))
        assertFalse(Jalali.isLeapYear(1404))
        assertEquals(30, Jalali.monthLength(1403, 12))
        assertEquals(29, Jalali.monthLength(1404, 12))
        assertEquals(31, Jalali.monthLength(1404, 6))
    }

    @Test
    fun digits() {
        assertEquals("۱۴۰۵/۰۷/۲۲", JalaliDate(1405, 7, 22).toString())
        assertEquals("1234", toLatinDigits("۱۲۳۴"))
        assertEquals("۲۲ مهر ۱۴۰۵", Jalali.formatLong(LocalDate.of(2026, 10, 14)))
        assertEquals("۰۹", toPersianDigits("09"))
    }
}
