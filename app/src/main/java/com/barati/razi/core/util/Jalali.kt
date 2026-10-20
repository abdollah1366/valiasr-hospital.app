package com.barati.razi.core.util

import java.time.LocalDate

/** تاریخ شمسی. ماه از ۱ تا ۱۲. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    override fun toString(): String =
        toPersianDigits("%04d/%02d/%02d".format(year, month, day))
}

/**
 * تبدیل تاریخ میلادی و شمسی (الگوریتم محاسباتی رایج jalaali-js).
 * در سرور همه تاریخ‌ها میلادی (ISO) ذخیره می‌شوند و فقط در نمایش شمسی می‌شوند.
 */
object Jalali {
    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun div(a: Int, b: Int) = a / b
    private fun mod(a: Int, b: Int) = a - (a / b) * b

    private fun jalCal(jy: Int): JalCal {
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        require(jy >= jp && jy < breaks.last()) { "سال خارج از محدوده: $jy" }
        var i = 1
        while (i < breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
            div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2g(jdn: Int): LocalDate {
        var j = 4 * jdn + 139361631
        j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return LocalDate.of(gy, gm, gd)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    fun isLeapYear(jy: Int): Boolean = jalCal(jy).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        isLeapYear(jy) -> 30
        else -> 29
    }

    fun toGregorian(date: JalaliDate): LocalDate = d2g(j2d(date.year, date.month, date.day))

    fun fromGregorian(date: LocalDate): JalaliDate {
        val jdn = g2d(date.year, date.monthValue, date.dayOfMonth)
        val gy = date.year
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return JalaliDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    /** «۲۲ مهر ۱۴۰۵» */
    fun formatLong(date: LocalDate): String {
        val j = fromGregorian(date)
        return toPersianDigits("${j.day} ${MONTH_NAMES[j.month - 1]} ${j.year}")
    }

    fun formatIso(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching { formatLong(LocalDate.parse(iso.take(10))) }.getOrDefault(iso)
    }
}

fun toPersianDigits(text: String): String = buildString(text.length) {
    for (c in text) append(if (c in '0'..'9') '۰' + (c - '0') else c)
}

/** ارقام فارسی و عربی را به لاتین تبدیل می‌کند (برای ورودی کاربر). */
fun toLatinDigits(text: String): String = buildString(text.length) {
    for (c in text) {
        append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            },
        )
    }
}
