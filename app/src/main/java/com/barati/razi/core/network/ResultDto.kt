package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** جواب آزمایش و تصویربرداری (سرور: app/routers/results.py؛ داده نمونه یا سامانه بیمارستان). */

@Serializable
data class ResultItemDto(
    val name: String,
    val value: String,
    val unit: String? = null,
    val reference: String? = null,
    val flag: String? = null,
)

@Serializable
data class ResultDto(
    val id: String,
    val kind: String,
    val title: String,
    @SerialName("performed_at") val performedAt: String,
    val status: String = "ready",
    val department: String? = null,
    @SerialName("doctor_name") val doctorName: String? = null,
    val summary: String? = null,
    val report: String? = null,
    val items: List<ResultItemDto> = emptyList(),
    @SerialName("file_url") val fileUrl: String? = null,
)
