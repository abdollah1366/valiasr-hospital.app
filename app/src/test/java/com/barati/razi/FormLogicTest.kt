package com.barati.razi

import com.barati.razi.core.form.Answer
import com.barati.razi.core.form.FormLogic
import com.barati.razi.core.form.FormSchema
import com.barati.razi.core.network.AppJson
import java.io.File
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** همان سناریوهای backend/tests/test_forms.py تا منطق اپ و سرور یکسان بماند. */
class FormLogicTest {
    private val serverFile = listOf("../backend/app/data/incident_form.json", "backend/app/data/incident_form.json")
        .map(::File).first { it.exists() }
    private val assetFile = listOf("src/main/assets/forms/incident_form.json", "app/src/main/assets/forms/incident_form.json")
        .map(::File).first { it.exists() }
    private val schema = AppJson.decodeFromString(FormSchema.serializer(), serverFile.readText())

    private fun base(vararg extra: Pair<String, Answer>) = mapOf(
        "report_method" to Answer.Text("خوداظهاری"),
        "harm_level" to Answer.Text("بدون آسیب"),
        "error_types" to Answer.Choices(listOf("دارویی")),
        "type_2_items" to Answer.Choices(listOf("دوز اشتباه (بیش از حد، کمتر از حد، دوز فراموش‌شده)")),
        "description" to Answer.Text("شرح"),
    ) + extra

    @Test
    fun bundledFormMatchesServerForm() {
        assertEquals(serverFile.readText(), assetFile.readText())
    }

    @Test
    fun validMedicationError() {
        assertEquals(emptyMap<String, String>(), FormLogic.validate(schema, "error", base()))
    }

    @Test
    fun conditionalSectionRequiredWhenVisible() {
        val errors = FormLogic.validate(schema, "error", base() - "type_2_items")
        assertTrue("type_2_items" in errors)
    }

    @Test
    fun hiddenAnswersAreDropped() {
        val json = FormLogic.toJson(schema, "error", base("type_1_items" to Answer.Choices(listOf("آبسه"))))
        assertFalse("type_1_items" in json)
        assertEquals(1, (json["error_types"] as JsonArray).size)
    }

    @Test
    fun equipmentHidesPatientSections() {
        val sections = FormLogic.visibleSections(schema, "equipment", emptyMap()).map { it.id }
        assertFalse("patient" in sections)
        assertTrue("equipment" in sections)
        assertTrue("description" in sections)
    }

    @Test
    fun invalidOptionAndDate() {
        val errors = FormLogic.validate(
            schema,
            "error",
            base("report_method" to Answer.Text("نامعتبر"), "admission_date" to Answer.Text("1403-13-40")),
        )
        assertEquals(setOf("report_method", "admission_date"), errors.keys)
    }

    @Test
    fun nestedConditionStudentField() {
        val hidden = FormLogic.toJson(
            schema, "error",
            base("involved_roles" to Answer.Choices(listOf("پرستار")), "student_field" to Answer.Text("پرستاری")),
        )
        assertFalse("student_field" in hidden)
        val shown = FormLogic.toJson(
            schema, "error",
            base("involved_roles" to Answer.Choices(listOf("دانشجو")), "student_field" to Answer.Text("پرستاری")),
        )
        assertTrue("student_field" in shown)
    }
}
