package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** کتابخانه آموزش به بیمار (سرور: app/routers/education.py)؛ عمومی و بدون ورود. */

@Serializable
data class EducationDto(
    val id: String,
    val title: String,
    val summary: String? = null,
    val kind: String,
    val condition: String? = null,
    @SerialName("care_type") val careType: String = "other",
    @SerialName("specialty_id") val specialtyId: String? = null,
    @SerialName("specialty_name") val specialtyName: String? = null,
    val source: String? = null,
    @SerialName("reviewed_on") val reviewedOn: String? = null,
    @SerialName("has_file") val hasFile: Boolean = false,
    @SerialName("file_version") val fileVersion: String? = null,
    val body: String? = null,
    @SerialName("external_url") val externalUrl: String? = null,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class EducationSpecialtyDto(val id: String, val name: String)

@Serializable
data class EducationFiltersDto(
    val conditions: List<String> = emptyList(),
    @SerialName("care_types") val careTypes: List<String> = emptyList(),
    val kinds: List<String> = emptyList(),
    val specialties: List<EducationSpecialtyDto> = emptyList(),
)
