package com.barati.razi.core.form

import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * منطق نمایش شرطی و اعتبارسنجی فرم.
 * باید دقیقاً با backend/app/services/forms.py یکسان بماند (تست FormLogicTest همان فایل JSON سرور را می‌خواند).
 */
object FormLogic {
    const val MAX_TEXT = 4000

    fun conditionMet(cond: Condition?, kind: String, answers: Map<String, Answer>): Boolean {
        if (cond == null) return true
        cond.kindIn?.let { return kind in it }
        val value = answers[cond.field]
        if (cond.contains != null) {
            return when (value) {
                is Answer.Choices -> cond.contains in value.values
                is Answer.Text -> value.value == cond.contains
                null -> false
            }
        }
        if (cond.equals != null) return (value as? Answer.Text)?.value == cond.equals
        return false
    }

    fun visibleSections(schema: FormSchema, kind: String, answers: Map<String, Answer>): List<FormSection> =
        schema.sections
            .filter { conditionMet(it.showIf, kind, answers) }
            .map { s -> s.copy(fields = s.fields.filter { conditionMet(it.showIf, kind, answers) }) }
            .filter { it.fields.isNotEmpty() }

    fun visibleFields(schema: FormSchema, kind: String, answers: Map<String, Answer>): List<FormField> =
        visibleSections(schema, kind, answers).flatMap { it.fields }

    /** پاسخ‌هایی که به دلیل تغییر شرط‌ها پنهان شده‌اند حذف می‌شوند (مثل سرور). */
    fun prune(schema: FormSchema, kind: String, answers: Map<String, Answer>): Map<String, Answer> {
        var current = answers
        repeat(10) {
            val ids = visibleFields(schema, kind, current).map { it.id }.toSet()
            val pruned = current.filterKeys { it in ids }
            if (pruned == current) return current
            current = pruned
        }
        return current
    }

    private fun Answer?.isEmpty(): Boolean = when (this) {
        null -> true
        is Answer.Text -> value.isBlank()
        is Answer.Choices -> values.isEmpty()
    }

    fun validateField(field: FormField, answer: Answer?): String? {
        if (answer.isEmpty()) return if (field.required) "پاسخ این سؤال الزامی است" else null
        return when (field.type) {
            FieldType.TEXT, FieldType.TEXTAREA ->
                if ((answer as? Answer.Text)?.value?.trim()?.length ?: 0 > MAX_TEXT) "متن بیش از حد طولانی است" else null
            FieldType.DATE ->
                if (runCatching { LocalDate.parse((answer as Answer.Text).value) }.isFailure) "تاریخ نامعتبر است" else null
            FieldType.SINGLE ->
                if ((answer as? Answer.Text)?.value !in field.options) "گزینه نامعتبر است" else null
            FieldType.MULTI ->
                if ((answer as? Answer.Choices)?.values?.all { it in field.options } != true) "گزینه نامعتبر است" else null
        }
    }

    fun validateSection(section: FormSection, answers: Map<String, Answer>): Map<String, String> =
        section.fields.mapNotNull { f -> validateField(f, answers[f.id])?.let { f.id to it } }.toMap()

    fun validate(schema: FormSchema, kind: String, answers: Map<String, Answer>): Map<String, String> {
        val pruned = prune(schema, kind, answers)
        return visibleFields(schema, kind, pruned)
            .mapNotNull { f -> validateField(f, pruned[f.id])?.let { f.id to it } }
            .toMap()
    }

    fun toJson(schema: FormSchema, kind: String, answers: Map<String, Answer>): JsonObject {
        val pruned = prune(schema, kind, answers)
        return JsonObject(
            pruned.mapNotNull { (k, v) ->
                val json: JsonElement = when (v) {
                    is Answer.Text -> if (v.value.isBlank()) return@mapNotNull null else JsonPrimitive(v.value.trim())
                    is Answer.Choices -> if (v.values.isEmpty()) return@mapNotNull null else JsonArray(v.values.map { JsonPrimitive(it) })
                }
                k to json
            }.toMap(),
        )
    }
}
