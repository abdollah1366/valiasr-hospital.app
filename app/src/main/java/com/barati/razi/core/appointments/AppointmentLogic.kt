package com.barati.razi.core.appointments

import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.core.network.SlotDto
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toPersianDigits
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** منطق خالص نوبت‌دهی (بدون اندروید) تا با تست واحد آزموده شود. */
object AppointmentLogic {
    /** ساعت تهران؛ ایران از ۱۴۰۱ ساعت تابستانی ندارد و سرور هم همین را به کار می‌برد. */
    val ZONE: ZoneOffset = ZoneOffset.ofHoursMinutes(3, 30)

    /** یادآور روی گوشی: یک روز و دو ساعت پیش از نوبت. */
    val REMINDER_OFFSETS: List<Duration> = listOf(Duration.ofHours(24), Duration.ofHours(2))

    private val PERSIAN_WEEKDAYS = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

    fun instant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

    fun localDate(iso: String): LocalDate = instant(iso).atOffset(ZONE).toLocalDate()

    /** وقت‌ها به ترتیب، دسته‌شده بر اساس روز محلی. */
    fun groupByDay(slots: List<SlotDto>): Map<LocalDate, List<SlotDto>> =
        slots.sortedBy { instant(it.startsAt) }.groupBy { localDate(it.startsAt) }

    private fun digits(text: String, language: AppLanguage) =
        if (language == AppLanguage.FA) toPersianDigits(text) else text

    fun timeText(iso: String, language: AppLanguage): String =
        digits(instant(iso).atOffset(ZONE).format(DateTimeFormatter.ofPattern("HH:mm")), language)

    /** «شنبه ۳ آبان» برای فارسی (شمسی)؛ برای زبان‌های دیگر تاریخ میلادی به همان زبان. */
    fun dayText(date: LocalDate, language: AppLanguage): String {
        if (language == AppLanguage.FA) {
            val j = Jalali.fromGregorian(date)
            val weekday = PERSIAN_WEEKDAYS[date.dayOfWeek.value - 1]
            return toPersianDigits("$weekday ${j.day} ${Jalali.MONTH_NAMES[j.month - 1]}")
        }
        val locale = Locale.forLanguageTag(language.code)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val month = date.month.getDisplayName(TextStyle.FULL, locale)
        return "$weekday ${date.dayOfMonth} $month"
    }

    fun dateTimeText(iso: String, language: AppLanguage): String =
        dayText(localDate(iso), language) + " · " + timeText(iso, language)

    /** نوبت‌های پیش‌رو که هنوز فعال‌اند، نزدیک‌ترین اول. */
    fun upcoming(items: List<PatientAppointmentDto>, now: Instant): List<PatientAppointmentDto> =
        items.filter { it.status == "booked" && instant(it.startsAt) > now }.sortedBy { instant(it.startsAt) }

    /** زمان‌های یادآور هر نوبت که هنوز نرسیده‌اند. */
    fun reminderTimes(startsAt: Instant, now: Instant): List<Instant> =
        REMINDER_OFFSETS.map { startsAt.minus(it) }.filter { it > now }
}
