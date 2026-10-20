package com.barati.razi.core.data

import com.barati.razi.core.network.AnnouncementDto
import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.FaqDto
import com.barati.razi.core.network.FeedbackCreated
import com.barati.razi.core.network.FeedbackRequest
import com.barati.razi.core.network.FeedbackStatusDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.UnitDto
import com.barati.razi.core.network.apiCall

/** بخش عمومی و اطلاعیه‌ها. */
class ContentRepository(private val api: ApiService) {
    suspend fun publicUnits(): Result<List<UnitDto>> = apiCall { api.publicUnits() }
    suspend fun faqs(): Result<List<FaqDto>> = apiCall { api.faqs() }
    suspend fun publicAnnouncements(): Result<List<AnnouncementDto>> = apiCall { api.publicAnnouncements() }
    suspend fun submitFeedback(req: FeedbackRequest): Result<FeedbackCreated> = apiCall { api.submitFeedback(req) }
    suspend fun feedbackStatus(code: String): Result<FeedbackStatusDto> =
        apiCall { api.feedbackStatus(code.trim().uppercase()) }

    suspend fun staffAnnouncements(): Result<List<AnnouncementDto>> = apiCall { api.announcements() }
    suspend fun pendingAckCount(): Int =
        (apiCall { api.announcements(onlyPendingAck = true) } as? Result.Ok)?.value?.size ?: 0
    suspend fun markRead(id: String): Result<AnnouncementDto> = apiCall { api.markRead(id) }
    suspend fun acknowledge(id: String): Result<AnnouncementDto> = apiCall { api.acknowledge(id) }
}
