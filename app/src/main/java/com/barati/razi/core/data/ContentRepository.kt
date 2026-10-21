package com.barati.razi.core.data

import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.AnnouncementDto
import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.FaqDto
import com.barati.razi.core.network.FeedbackCreated
import com.barati.razi.core.network.FeedbackRequest
import com.barati.razi.core.network.FeedbackStatusDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.UnitDto
import com.barati.razi.core.network.apiCall

/** بخش عمومی و اطلاعیه‌ها. محتوای عمومی به زبان مراجع (اگر ترجمه تأییدشده داشته باشد) برمی‌گردد. */
class ContentRepository(private val api: ApiService) {
    suspend fun publicUnits(language: AppLanguage = AppLanguage.FA): Result<List<UnitDto>> =
        apiCall { api.publicUnits(language.query) }
    suspend fun faqs(language: AppLanguage = AppLanguage.FA): Result<List<FaqDto>> = apiCall { api.faqs(language.query) }
    suspend fun publicAnnouncements(language: AppLanguage = AppLanguage.FA): Result<List<AnnouncementDto>> =
        apiCall { api.publicAnnouncements(language.query) }
    suspend fun submitFeedback(req: FeedbackRequest): Result<FeedbackCreated> = apiCall { api.submitFeedback(req) }
    suspend fun feedbackStatus(code: String): Result<FeedbackStatusDto> =
        apiCall { api.feedbackStatus(code.trim().uppercase()) }

    suspend fun staffAnnouncements(): Result<List<AnnouncementDto>> = apiCall { api.announcements() }
    suspend fun pendingAckCount(): Int =
        (apiCall { api.announcements(onlyPendingAck = true) } as? Result.Ok)?.value?.size ?: 0
    suspend fun markRead(id: String): Result<AnnouncementDto> = apiCall { api.markRead(id) }
    suspend fun acknowledge(id: String): Result<AnnouncementDto> = apiCall { api.acknowledge(id) }
}

/** فارسی متن اصلی است و پارامتر زبان لازم ندارد. */
internal val AppLanguage.query: String? get() = if (this == AppLanguage.FA) null else code
