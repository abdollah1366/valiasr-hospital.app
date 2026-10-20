package com.barati.razi.core.chat

import com.barati.razi.core.data.PendingChatMessage
import com.barati.razi.core.network.ChatMessageDto
import com.barati.razi.core.network.ConversationDto
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toPersianDigits
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** یک ردیف صفحه گفتگو: جداکننده روز، پیام سرور، یا پیامی که هنوز در صف ارسال است. */
sealed interface ChatRow {
    val key: String

    data class Day(val label: String, val seq: Int) : ChatRow {
        override val key get() = "day:$seq"
    }

    data class Sent(val message: ChatMessageDto, val mine: Boolean, val seen: Boolean) : ChatRow {
        override val key get() = message.id
    }

    data class Local(val pending: PendingChatMessage) : ChatRow {
        override val key get() = "local:" + pending.clientUuid
    }
}

/** منطق خالص گفتگو (بدون اندروید) تا با تست واحد آزموده شود. */
object ChatLogic {
    const val POLL_MILLIS = 3_000L
    const val MAX_TEXT = 4000

    fun instant(iso: String?): Instant? = iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    /** پیام‌های تازه یا ویرایش‌شده جای نسخه قبلی را می‌گیرند؛ ترتیب بر اساس زمان ارسال. */
    fun merge(current: List<ChatMessageDto>, incoming: List<ChatMessageDto>): List<ChatMessageDto> {
        if (incoming.isEmpty()) return current
        val byId = LinkedHashMap<String, ChatMessageDto>()
        current.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        return byId.values.sortedWith(compareBy({ instant(it.createdAt) ?: Instant.EPOCH }, { it.id }))
    }

    /** طرف مقابل گفتگوی دونفره پیام را دیده است؟ */
    fun seen(message: ChatMessageDto, peerReadAt: String?): Boolean {
        val read = instant(peerReadAt) ?: return false
        val sent = instant(message.createdAt) ?: return false
        return !read.isBefore(sent)
    }

    fun dayLabel(at: Instant, today: LocalDate, zone: ZoneId): String {
        val day = at.atZone(zone).toLocalDate()
        return when (day) {
            today -> "امروز"
            today.minusDays(1) -> "دیروز"
            else -> Jalali.formatLong(day)
        }
    }

    fun time(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = instant(iso)?.atZone(zone) ?: return ""
        return toPersianDigits("%d:%02d".format(t.hour, t.minute))
    }

    /**
     * ردیف‌های صفحه: پیام‌های سرور با جداکننده روز، و در انتها پیام‌های صف ارسال این گفتگو
     * (به جز آن‌هایی که نسخه سروری‌شان رسیده است).
     */
    fun timeline(
        messages: List<ChatMessageDto>,
        pending: List<PendingChatMessage>,
        myId: String,
        peerReadAt: String?,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ChatRow> {
        val rows = mutableListOf<ChatRow>()
        var lastDay: String? = null
        fun day(at: Instant) {
            val label = dayLabel(at, today, zone)
            if (label != lastDay) {
                rows += ChatRow.Day(label, rows.size)
                lastDay = label
            }
        }
        for (m in messages) {
            day(instant(m.createdAt) ?: Instant.EPOCH)
            val mine = m.senderId == myId
            rows += ChatRow.Sent(m, mine, mine && seen(m, peerReadAt))
        }
        val arrived = messages.mapTo(HashSet()) { it.clientUuid }
        for (p in pending.filter { it.clientUuid !in arrived }.sortedBy { it.createdAtMillis }) {
            day(Instant.ofEpochMilli(p.createdAtMillis))
            rows += ChatRow.Local(p)
        }
        return rows
    }

    /** متن کوتاه آخرین پیام برای فهرست گفتگوها. */
    fun preview(c: ConversationDto): String {
        val last = c.lastMessage ?: return when (c.kind) {
            "unit" -> "کانال اطلاع‌رسانی واحد"
            "group" -> "هنوز پیامی نیست"
            else -> "گفتگو را شروع کنید"
        }
        val text = when {
            last.deleted -> "این پیام حذف شد"
            last.text != null -> last.text
            last.hasAttachment -> "فایل پیوست"
            else -> ""
        }
        val who = when {
            last.mine -> "شما"
            c.kind == "direct" -> null
            else -> last.senderName
        }
        return if (who != null) "$who: $text" else text
    }

    /** زمان آخرین پیام در فهرست: امروز ساعت، روزهای دیگر تاریخ. */
    fun listTime(iso: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val t = instant(iso) ?: return ""
        return if (t.atZone(zone).toLocalDate() == today) time(iso, zone) else dayLabel(t, today, zone)
    }

    fun isImage(contentType: String?): Boolean = contentType?.startsWith("image/") == true

    fun badge(count: Int): String = toPersianDigits(if (count > 99) "99+" else count.toString())
}
