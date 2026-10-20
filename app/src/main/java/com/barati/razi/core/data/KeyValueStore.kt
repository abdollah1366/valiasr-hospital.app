package com.barati.razi.core.data

import kotlinx.coroutines.flow.Flow

/** ذخیره‌سازی ساده کلید-مقدار؛ پیاده‌سازی اندرویدی با DataStore است (DataStoreKeyValueStore). */
interface KeyValueStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String?)
    fun observe(key: String): Flow<String?>
}

object Keys {
    const val SERVER_ORIGIN = "server_origin"
    const val INCIDENT_FORM = "form_incident"
    const val PENDING_INCIDENTS = "pending_incidents"
    const val LOCAL_ANONYMOUS = "local_anonymous_reports"
    const val PENDING_REQUESTS = "pending_service_requests"
    const val SHOWN_NOTIFICATIONS = "shown_notifications"
    const val NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
    const val CHAT_OUTBOX = "chat_outbox"
    const val MY_SCHEDULE = "my_schedule_cache"
    const val PATIENT_LANGUAGE = "patient_language"
}
