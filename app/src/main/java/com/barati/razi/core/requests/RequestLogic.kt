package com.barati.razi.core.requests

import com.barati.razi.core.network.RequestEventDto
import com.barati.razi.core.network.RequestFieldDto
import com.barati.razi.core.util.toPersianDigits
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong

/** منطق بدون وابستگی به اندروید بخش درخواست پشتیبانی (برای تست واحد). */
object RequestLogic {
    val categories = linkedMapOf(
        "it" to "فناوری اطلاعات",
        "equipment" to "تجهیزات پزشکی",
        "facilities" to "تأسیسات",
        "other" to "سایر",
    )

    val priorities = linkedMapOf("normal" to "عادی", "urgent" to "فوری", "critical" to "بحرانی")

    val statuses = mapOf(
        "submitted" to "ثبت‌شده",
        "accepted" to "پذیرفته‌شده",
        "in_progress" to "در حال انجام",
        "on_hold" to "در انتظار",
        "done" to "انجام شد",
        "closed" to "بسته",
        "rejected" to "رد شده",
    )

    val openStatuses = setOf("submitted", "accepted", "in_progress", "on_hold")

    /** ترتیب و برچسب دکمه‌های تغییر وضعیت برای کارشناس. */
    val transitionLabels = linkedMapOf(
        "accepted" to "پذیرش",
        "in_progress" to "شروع کار",
        "on_hold" to "در انتظار",
        "done" to "انجام شد",
        "rejected" to "رد",
    )

    /** وضعیت‌هایی که کارشناس می‌تواند به آن برود، به ترتیب نمایش. */
    fun transitions(actions: List<String>): List<String> {
        val allowed = actions.filter { it.startsWith("status:") }.map { it.removePrefix("status:") }.toSet()
        return transitionLabels.keys.filter { it in allowed }
    }

    /**
     * همان بررسی سرور (services/requests.py: validate_answers): فیلد لازم، عدد و گزینه مجاز.
     * خروجی: پاسخ‌های تمیز برای ارسال و خطای هر فیلد.
     */
    fun validate(fields: List<RequestFieldDto>, input: Map<String, String>): Pair<Map<String, JsonElement>, Map<String, String>> {
        val clean = linkedMapOf<String, JsonElement>()
        val errors = linkedMapOf<String, String>()
        for (f in fields) {
            val value = input[f.id]?.trim().orEmpty()
            if (value.isEmpty()) {
                if (f.required) errors[f.id] = "این فیلد لازم است"
                continue
            }
            when (f.kind) {
                "number" -> {
                    val n = toLatinDigits(value).replace('٫', '.').toDoubleOrNull()
                    if (n == null) errors[f.id] = "عدد وارد کنید" else clean[f.id] = JsonPrimitive(n)
                }
                "select" -> if (value in f.options) clean[f.id] = JsonPrimitive(value) else errors[f.id] = "گزینه نامعتبر است"
                else -> clean[f.id] = JsonPrimitive(value.take(1000))
            }
        }
        return clean to errors
    }

    fun toLatinDigits(s: String): String = buildString {
        for (c in s) {
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                },
            )
        }
    }

    /** نمایش مقدار پاسخ: عدد صحیح بدون «.0» و با ارقام فارسی. */
    fun displayValue(value: JsonElement): String {
        val p = value as? JsonPrimitive ?: return value.toString()
        if (!p.isString) {
            p.doubleOrNull?.let { d -> return toPersianDigits(if (d % 1.0 == 0.0) d.roundToLong().toString() else d.toString()) }
        }
        return toPersianDigits(p.contentOrNull ?: "")
    }

    /** «همین الان»، «۵ دقیقه پیش»، «۳ ساعت پیش»، «۲ روز پیش» */
    fun ago(iso: String, now: OffsetDateTime = OffsetDateTime.now()): String {
        val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return ""
        val min = maxOf(0L, Duration.between(t, now).toMinutes())
        return toPersianDigits(
            when {
                min < 1 -> "همین الان"
                min < 60 -> "$min دقیقه پیش"
                min < 1440 -> "${min / 60} ساعت پیش"
                else -> "${min / 1440} روز پیش"
            },
        )
    }

    fun eventTitle(e: RequestEventDto): String = when (e.kind) {
        "created" -> "درخواست ثبت شد"
        "status" -> if (e.toStatus == "closed" && e.actor == null) "بسته شدن خودکار" else "وضعیت: ${statuses[e.toStatus] ?: e.toStatus}"
        "assign" -> "ارجاع به کارشناس"
        "transfer" -> "ارجاع به واحد دیگر"
        "comment" -> "یادداشت"
        "attachment" -> "پیوست"
        "rated" -> "امتیاز درخواست‌کننده"
        "reopened" -> "اعتراض و بازگشایی"
        else -> e.kind
    }

    /** پیام نتیجه برای درخواست‌کننده بر اساس وضعیت. */
    fun statusHint(status: String): String = when (status) {
        "submitted" -> "در صف واحد مسئول است."
        "accepted" -> "کارشناس درخواست را پذیرفته است."
        "in_progress" -> "کارشناس روی درخواست کار می‌کند."
        "on_hold" -> "رسیدگی متوقف است (مثلاً در انتظار قطعه یا پاسخ شما)."
        "done" -> "انجام شده است؛ لطفاً تأیید و امتیاز دهید یا اگر مشکل باقی است اعتراض کنید."
        "closed" -> "درخواست بسته شده است."
        "rejected" -> "درخواست رد شده است."
        else -> ""
    }
}
