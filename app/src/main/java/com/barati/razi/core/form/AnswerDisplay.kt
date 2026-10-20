package com.barati.razi.core.form

import com.barati.razi.core.util.Jalali
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** یک ردیف قابل نمایش از پاسخ‌های فرم: برچسب فیلد و مقدار متنی آن. */
data class AnswerRow(val label: String, val value: String)

/**
 * پاسخ‌های ذخیره‌شده را به ترتیب فرم و با برچسب فارسی فیلدها برمی‌گرداند.
 * کلیدهایی که در این نسخه فرم نیستند (مثلاً از نسخه قدیمی‌تر فرم) با همان شناسه در انتها می‌آیند.
 */
fun answerRows(form: FormSchema, answers: JsonObject): List<AnswerRow> {
    val rows = mutableListOf<AnswerRow>()
    val seen = mutableSetOf<String>()
    for (section in form.sections) {
        for (field in section.fields) {
            val text = answers[field.id]?.let { display(it, field.type) } ?: continue
            seen += field.id
            if (text.isNotBlank()) rows += AnswerRow(field.label, text)
        }
    }
    for ((key, value) in answers) {
        if (key in seen) continue
        val text = display(value, null)
        if (text.isNotBlank()) rows += AnswerRow(key, text)
    }
    return rows
}

private fun display(value: JsonElement, type: FieldType?): String = when (value) {
    is JsonNull -> ""
    is JsonArray -> value.joinToString("، ") { display(it, null) }
    is JsonPrimitive -> if (type == FieldType.DATE) Jalali.formatIso(value.content) else value.content
    is JsonObject -> value.toString()
}
