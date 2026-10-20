package com.barati.razi.core.notifications

import com.barati.razi.core.network.NotificationDto
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** مقصد لمس یک اعلان در اپ. */
sealed interface NotificationTarget {
    data class Request(val id: String) : NotificationTarget
    data class Incident(val id: String) : NotificationTarget
    data class Course(val id: String) : NotificationTarget
    data class Chat(val id: String) : NotificationTarget
    data object Announcements : NotificationTarget
    data object Training : NotificationTarget
    data class Schedule(val id: String?) : NotificationTarget
}

/** منطق خالص اعلان‌ها (بدون اندروید) تا با تست واحد آزموده شود. */
object NotificationLogic {
    /** چند اعلان تازه جداگانه نشان داده می‌شود؛ بیشتر از این، یک اعلان خلاصه. */
    const val MAX_SEPARATE = 3

    /** شناسه‌هایی که به‌خاطر سپرده می‌شوند تا یک اعلان دو بار روی گوشی نیاید. */
    const val SHOWN_CAP = 200

    fun target(linkType: String?, linkId: String?): NotificationTarget? = when (linkType) {
        "request" -> linkId?.let { NotificationTarget.Request(it) }
        "incident" -> linkId?.let { NotificationTarget.Incident(it) }
        "course" -> linkId?.let { NotificationTarget.Course(it) }
        "announcement" -> NotificationTarget.Announcements
        "schedule" -> NotificationTarget.Schedule(linkId)
        "chat" -> linkId?.let { NotificationTarget.Chat(it) }
        // آزمون از صفحه آموزش شروع می‌شود تا کاربر پیش از شروع، زمان و شرایط را ببیند.
        "exam" -> NotificationTarget.Training
        else -> null
    }

    /**
     * از اعلان‌های خوانده‌نشده سرور، آن‌هایی که هنوز روی گوشی نشان داده نشده‌اند (قدیمی‌ترین اول)
     * و فهرست تازه شناسه‌های نشان‌داده‌شده (جدیدترین‌ها نگه داشته می‌شوند).
     */
    fun pickNew(unread: List<NotificationDto>, shown: List<String>): Pair<List<NotificationDto>, List<String>> {
        val seen = shown.toSet()
        val fresh = unread.filter { !it.read && it.id !in seen }.sortedBy { it.createdAt }
        val remembered = (shown + fresh.map { it.id }).takeLast(SHOWN_CAP)
        return fresh to remembered
    }

    /** گروه‌بندی فهرست با برچسب «امروز»، «دیروز» و «قبل‌تر» با حفظ ترتیب. */
    fun group(
        items: List<NotificationDto>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<String, List<NotificationDto>>> {
        fun label(n: NotificationDto): String {
            val day = runCatching { OffsetDateTime.parse(n.createdAt).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
            return when (day) {
                today -> "امروز"
                today.minusDays(1) -> "دیروز"
                else -> "قبل‌تر"
            }
        }
        return items.groupBy(::label).toList()
    }
}
