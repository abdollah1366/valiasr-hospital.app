package com.barati.razi.core.data

import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.MyScheduleDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.ReturnBody
import com.barati.razi.core.network.ScheduleDetailDto
import com.barati.razi.core.network.SwapAnswerBody
import com.barati.razi.core.network.SwapBody
import com.barati.razi.core.network.SwapDto
import com.barati.razi.core.network.apiCall
import kotlinx.serialization.Serializable

/** آخرین برنامه شخصی دریافت‌شده؛ برای نمایش بدون اینترنت. فقط برای همان کاربری که دریافت کرده معتبر است. */
@Serializable
data class CachedSchedule(val userId: String, val fetchedAtMillis: Long, val data: MyScheduleDto)

/** برنامه شیفت. دسترسی‌ها فقط در سرور بررسی می‌شوند؛ روی گوشی فقط برنامه شخصی خود کاربر نگه داشته می‌شود. */
class ScheduleRepository(
    private val api: ApiService,
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun mine(userId: String, start: String? = null, end: String? = null): Result<MyScheduleDto> {
        val r = apiCall { api.mySchedule(start, end) }
        // فقط پیش‌فرض (ماه جاری و بعد) ذخیره می‌شود تا نمایش آفلاین همیشه همان را نشان دهد
        if (r is Result.Ok && start == null && end == null) {
            store.put(Keys.MY_SCHEDULE, AppJson.encodeToString(CachedSchedule.serializer(), CachedSchedule(userId, clock(), r.value)))
        }
        return r
    }

    suspend fun cachedMine(userId: String): CachedSchedule? {
        val raw = store.get(Keys.MY_SCHEDULE) ?: return null
        val cached = runCatching { AppJson.decodeFromString(CachedSchedule.serializer(), raw) }.getOrNull()
        return cached?.takeIf { it.userId == userId }
    }

    suspend fun units(jy: Int, jm: Int) = apiCall { api.scheduleUnits(jy, jm) }
    suspend fun detail(id: String) = apiCall { api.scheduleDetail(id) }
    suspend fun approve(id: String): Result<ScheduleDetailDto> = apiCall { api.approveSchedule(id) }
    suspend fun returnToDraft(id: String, note: String): Result<ScheduleDetailDto> = apiCall { api.returnSchedule(id, ReturnBody(note)) }
    suspend fun onDuty(day: String? = null) = apiCall { api.onDuty(day) }
    suspend fun swaps() = apiCall { api.swaps() }
    suspend fun requestSwap(body: SwapBody): Result<SwapDto> = apiCall { api.createSwap(body) }
    suspend fun answerSwap(id: String, action: String, note: String? = null): Result<SwapDto> =
        apiCall { api.answerSwap(id, action, SwapAnswerBody(note)) }
}
