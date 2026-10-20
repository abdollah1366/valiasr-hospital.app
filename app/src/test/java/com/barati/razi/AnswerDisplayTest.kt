package com.barati.razi

import com.barati.razi.core.form.AnswerRow
import com.barati.razi.core.form.FieldType
import com.barati.razi.core.form.FormField
import com.barati.razi.core.form.FormSchema
import com.barati.razi.core.form.FormSection
import com.barati.razi.core.form.answerRows
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerDisplayTest {
    private val form = FormSchema(
        code = "incident",
        version = 1,
        sections = listOf(
            FormSection(
                id = "s1",
                title = "",
                fields = listOf(
                    FormField("description", "شرح ماجرا", FieldType.TEXTAREA),
                    FormField("types", "نوع خطا", FieldType.MULTI),
                    FormField("found_on", "تاریخ کشف", FieldType.DATE),
                    FormField("empty", "خالی", FieldType.TEXT),
                ),
            ),
        ),
    )

    @Test
    fun rowsFollowFormOrderWithLabelsAndFormattedValues() {
        val answers = JsonObject(
            mapOf(
                "old_field" to JsonPrimitive("از نسخه قبلی"),
                "found_on" to JsonPrimitive("2026-10-14"),
                "types" to JsonArray(listOf(JsonPrimitive("سقوط بیمار"), JsonPrimitive("دارویی"))),
                "description" to JsonPrimitive("شرح"),
                "empty" to JsonPrimitive(""),
            ),
        )
        assertEquals(
            listOf(
                AnswerRow("شرح ماجرا", "شرح"),
                AnswerRow("نوع خطا", "سقوط بیمار، دارویی"),
                AnswerRow("تاریخ کشف", "۲۲ مهر ۱۴۰۵"),
                AnswerRow("old_field", "از نسخه قبلی"),
            ),
            answerRows(form, answers),
        )
    }
}
