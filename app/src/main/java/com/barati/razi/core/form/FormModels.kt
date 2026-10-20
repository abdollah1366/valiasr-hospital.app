package com.barati.razi.core.form

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FormSchema(
    val code: String,
    val version: Int,
    val title: String = "",
    val sections: List<FormSection>,
)

@Serializable
data class FormSection(
    val id: String,
    val title: String,
    @SerialName("show_if") val showIf: Condition? = null,
    val fields: List<FormField>,
)

@Serializable
data class FormField(
    val id: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val sensitive: Boolean = false,
    @SerialName("show_if") val showIf: Condition? = null,
)

@Serializable
enum class FieldType {
    @SerialName("text") TEXT,
    @SerialName("textarea") TEXTAREA,
    @SerialName("date") DATE,
    @SerialName("single") SINGLE,
    @SerialName("multi") MULTI,
}

@Serializable
data class Condition(
    @SerialName("kind_in") val kindIn: List<String>? = null,
    val field: String? = null,
    val contains: String? = null,
    val equals: String? = null,
)

/** پاسخ یک فیلد: متن (برای text/textarea/date/single) یا فهرست (برای multi). */
sealed interface Answer {
    data class Text(val value: String) : Answer
    data class Choices(val values: List<String>) : Answer
}
