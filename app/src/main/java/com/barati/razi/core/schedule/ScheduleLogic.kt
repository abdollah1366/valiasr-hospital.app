package com.barati.razi.core.schedule

import com.barati.razi.core.network.MyShiftDto
import com.barati.razi.core.network.ScheduleCellDto
import com.barati.razi.core.network.ScheduleDetailDto
import com.barati.razi.core.network.SwapDto
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.JalaliDate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** رنگ‌بندی خانه شیفت؛ بر اساس ساعت شروع، مثل پنل وب. */
enum class ShiftTone { Morning, Evening, Night, Off, Leave }

/** یک خانه از تقویم ماهانه؛ day صفر یعنی خانه خالی پیش از روز اول ماه. */
data class CalendarCell(val day: Int, val date: LocalDate?, val shift: MyShiftDto?)

object ScheduleLogic {
    /** هفته از شنبه شروع می‌شود. */
    val WEEKDAY_HEADERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    /** ستون یک روز در هفته‌ای که از شنبه شروع می‌شود (شنبه = ۰ ... جمعه = ۶). */
    fun column(date: LocalDate): Int = (date.dayOfWeek.value + 1) % 7

    fun tone(kind: String, startsAt: String?, zone: ZoneId = ZoneId.systemDefault()): ShiftTone = when (kind) {
        "off" -> ShiftTone.Off
        "leave" -> ShiftTone.Leave
        else -> {
            val hour = startsAt?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(zone).hour }.getOrNull() }
            when {
                hour == null -> ShiftTone.Morning
                hour < 12 -> ShiftTone.Morning
                hour < 18 -> ShiftTone.Evening
                else -> ShiftTone.Night
            }
        }
    }

    /**
     * تقویم یک ماه شمسی به صورت ردیف‌های هفتگی هفت‌خانه‌ای.
     * اگر در یک روز چند شیفت باشد (چند بخش)، شیفت کاری بر استراحت و مرخصی مقدم است.
     */
    fun monthRows(jy: Int, jm: Int, items: List<MyShiftDto>): List<List<CalendarCell>> {
        val first = Jalali.toGregorian(JalaliDate(jy, jm, 1))
        val byDay = items.groupBy { it.day }.mapValues { (_, list) -> list.sortedBy { if (it.kind == "work") 0 else 1 }.first() }
        val cells = ArrayList<CalendarCell>()
        repeat(column(first)) { cells += CalendarCell(0, null, null) }
        for (d in 1..Jalali.monthLength(jy, jm)) {
            val date = first.plusDays((d - 1).toLong())
            cells += CalendarCell(d, date, byDay[date.toString()])
        }
        while (cells.size % 7 != 0) cells += CalendarCell(0, null, null)
        return cells.chunked(7)
    }

    /** آیا در این ماه شمسی دست‌کم یک شیفت (کاری، استراحت یا مرخصی) ثبت شده است. */
    fun hasShifts(jy: Int, jm: Int, items: List<MyShiftDto>): Boolean =
        monthRows(jy, jm, items).any { week -> week.any { it.shift != null } }

    fun monthRange(jy: Int, jm: Int): Pair<String, String> {
        val first = Jalali.toGregorian(JalaliDate(jy, jm, 1))
        return first.toString() to first.plusDays((Jalali.monthLength(jy, jm) - 1).toLong()).toString()
    }

    fun nextMonth(jy: Int, jm: Int): Pair<Int, Int> = if (jm == 12) jy + 1 to 1 else jy to jm + 1
    fun prevMonth(jy: Int, jm: Int): Pair<Int, Int> = if (jm == 1) jy - 1 to 12 else jy to jm - 1

    /** «۰۷:۳۰ تا ۱۴:۳۰» به وقت دستگاه؛ بدون ساعت (مرخصی و استراحت) خالی است. */
    fun timeRange(startsAt: String?, endsAt: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (startsAt == null || endsAt == null) return null
        return runCatching {
            val a = OffsetDateTime.parse(startsAt).atZoneSameInstant(zone)
            val b = OffsetDateTime.parse(endsAt).atZoneSameInstant(zone)
            val tail = if (b.toLocalDate() != a.toLocalDate()) " (فردا)" else ""
            "${a.format(HHMM)} تا ${b.format(HHMM)}$tail"
        }.getOrNull()
    }

    /** فاصله تا شیفت بعدی به زبان ساده: «شروع شده»، «۳ ساعت دیگر»، «فردا»، «۴ روز دیگر». */
    fun untilText(startsAt: String?, endsAt: String?, now: OffsetDateTime = OffsetDateTime.now()): String? {
        val start = startsAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
        val end = endsAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        if (!start.isAfter(now)) return if (end == null || end.isAfter(now)) "در جریان است" else null
        val minutes = java.time.Duration.between(now, start).toMinutes()
        return when {
            minutes < 60 -> "${minutes.coerceAtLeast(1)} دقیقه دیگر"
            minutes < 24 * 60 -> "${minutes / 60} ساعت دیگر"
            else -> "${minutes / (24 * 60)} روز دیگر"
        }
    }

    fun statusName(status: String): String = when (status) {
        "draft" -> "پیش‌نویس"
        "submitted" -> "در انتظار تأیید مترون"
        "published" -> "منتشر شده"
        else -> status
    }

    fun swapStatusName(status: String): String = when (status) {
        "pending_partner" -> "منتظر پاسخ همکار"
        "pending_manager" -> "منتظر تأیید مسئول"
        "approved" -> "تأیید شد"
        "rejected" -> "رد شد"
        "cancelled" -> "لغو شد"
        else -> status
    }

    fun swapActionName(action: String): String? = when (action) {
        "accept" -> "قبول"
        "reject" -> "رد"
        "approve" -> "تأیید"
        "cancel" -> "لغو درخواست"
        else -> null
    }

    /** خانه‌ای که شیفت کاری دارد و می‌شود برایش جابجایی خواست. */
    fun isSwappable(cell: ScheduleCellDto): Boolean = cell.code != null && cell.kind == "work"

    /** سطر خود کاربر در جدول بخش (اگر عضو باشد). */
    fun myRow(detail: ScheduleDetailDto, userId: String) = detail.members.firstOrNull { it.userId == userId }

    /** درخواست‌هایی که منتظر اقدام همین کاربرند (برای نشان روی صفحه). */
    fun needsMyAction(swaps: List<SwapDto>): List<SwapDto> =
        swaps.filter { s -> s.actions.any { it == "accept" || it == "approve" } }
}
