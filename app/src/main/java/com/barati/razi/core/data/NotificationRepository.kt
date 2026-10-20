package com.barati.razi.core.data

import com.barati.razi.core.network.ApiService
import com.barati.razi.core.network.AppJson
import com.barati.razi.core.network.NotificationDto
import com.barati.razi.core.network.NotificationPageDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.apiCall
import com.barati.razi.core.notifications.NotificationLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** اعلان‌های کاربر؛ هر کاربر فقط اعلان‌های خودش را از سرور می‌گیرد. */
class NotificationRepository(private val api: ApiService, private val store: KeyValueStore) {
    private val _unread = MutableStateFlow(0)

    /** تعداد خوانده‌نشده‌ها برای زنگوله صفحه خانه. */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val shownLock = Mutex()
    private val idsSerializer = ListSerializer(String.serializer())

    suspend fun page(before: String? = null): Result<NotificationPageDto> {
        val r = apiCall { api.notifications(before = before) }
        if (r is Result.Ok) _unread.value = r.value.unread
        return r
    }

    suspend fun refreshUnread() {
        val r = apiCall { api.unreadNotifications() }
        if (r is Result.Ok) _unread.value = r.value.count
    }

    suspend fun markRead(id: String): Result<Unit> {
        val r = apiCall { api.markNotificationRead(id) }
        if (r is Result.Ok) _unread.value = (_unread.value - 1).coerceAtLeast(0)
        return r
    }

    suspend fun markAllRead(): Result<Unit> {
        val r = apiCall { api.markAllNotificationsRead() }
        if (r is Result.Ok) _unread.value = 0
        return r
    }

    /** اعلان‌های خوانده‌نشده‌ای که هنوز روی گوشی نشان داده نشده‌اند؛ پس از بازگشت، نشان‌داده‌شده حساب می‌شوند. */
    suspend fun takeNewForAlert(): Result<List<NotificationDto>> = shownLock.withLock {
        when (val r = apiCall { api.notifications(unreadOnly = true, limit = 50) }) {
            is Result.Err -> r
            is Result.Ok -> {
                _unread.value = r.value.unread
                val saved = store.get(Keys.SHOWN_NOTIFICATIONS)
                val shown = saved?.let { runCatching { AppJson.decodeFromString(idsSerializer, it) }.getOrNull() }.orEmpty()
                val (fresh, remembered) = NotificationLogic.pickNew(r.value.items, shown)
                if (fresh.isNotEmpty() || saved == null) {
                    store.put(Keys.SHOWN_NOTIFICATIONS, AppJson.encodeToString(idsSerializer, remembered))
                }
                // اولین بار پس از ورود، اعلان‌های قدیمی روی گوشی نمی‌آیند؛ کاربر آن‌ها را در خود اپ می‌بیند.
                Result.Ok(if (saved == null) emptyList() else fresh)
            }
        }
    }

    /** پس از خروج: شمارنده صفر و سابقه نمایش پاک می‌شود تا کاربر بعدی اعلان‌های قدیمی خودش را یکجا نگیرد. */
    suspend fun clear() {
        _unread.value = 0
        shownLock.withLock { store.put(Keys.SHOWN_NOTIFICATIONS, null) }
    }
}
