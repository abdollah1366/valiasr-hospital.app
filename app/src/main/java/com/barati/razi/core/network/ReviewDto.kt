package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** نظر و ستاره مراجعان درباره پزشک (سرور: app/routers/reviews.py). */

@Serializable
data class ReviewRequest(val stars: Int, val comment: String? = null)

@Serializable
data class MyReviewDto(val id: String, val stars: Int, val comment: String? = null, val status: String = "approved")

@Serializable
data class RatingDto(val average: Double? = null, val count: Int = 0)

/** نظر منتشرشده؛ نام مراجع هرگز نمی‌آید. */
@Serializable
data class PublicReviewDto(
    val id: String,
    val stars: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("doctor_reply") val doctorReply: String? = null,
    @SerialName("admin_reply") val adminReply: String? = null,
)

@Serializable
data class DoctorReviewsDto(val rating: RatingDto = RatingDto(), val items: List<PublicReviewDto> = emptyList())

/** نمای پزشک از نظرهای تاییدشده خودش. */
@Serializable
data class DoctorOwnReviewDto(
    val id: String,
    val stars: Int,
    val comment: String? = null,
    @SerialName("visit_at") val visitAt: String,
    @SerialName("doctor_reply") val doctorReply: String? = null,
)

@Serializable
data class ReplyRequest(val text: String)
