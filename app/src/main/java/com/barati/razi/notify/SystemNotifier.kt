package com.barati.razi.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.barati.razi.MainActivity
import com.barati.razi.R
import com.barati.razi.core.network.NotificationDto
import com.barati.razi.core.notifications.NotificationLogic
import com.barati.razi.core.util.toPersianDigits

/** نمایش اعلان‌های سرور به‌صورت اعلان سیستمی اندروید. */
object SystemNotifier {
    const val CHANNEL_IMPORTANT = "important"
    const val CHANNEL_GENERAL = "general"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_LINK_TYPE = "link_type"
    const val EXTRA_LINK_ID = "link_id"
    private const val SUMMARY_ID = 1

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_IMPORTANT, "اعلان‌های مهم", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "ارجاع کار به شما، بخشنامه‌های نیازمند تأیید، دوره‌های الزامی و آزمون‌ها"
                },
                NotificationChannel(CHANNEL_GENERAL, "اعلان‌های عمومی", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "تغییر وضعیت درخواست‌ها و گزارش‌ها، پیام‌ها و اطلاعیه‌ها"
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )

    fun show(context: Context, items: List<NotificationDto>) {
        if (items.isEmpty() || !canPost(context)) return
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        try {
            if (items.size <= NotificationLogic.MAX_SEPARATE) {
                items.forEach { manager.notify(it.id.hashCode(), single(context, it)) }
            } else {
                manager.notify(SUMMARY_ID, summary(context, items))
            }
        } catch (_: SecurityException) {
            // اجازه اعلان همین لحظه گرفته شد؛ کاربر اعلان‌ها را در خود اپ می‌بیند.
        }
    }

    fun cancelAll(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    private fun base(context: Context, important: Boolean) =
        NotificationCompat.Builder(context, if (important) CHANNEL_IMPORTANT else CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF01687F.toInt())
            .setAutoCancel(true)
            // روی صفحه قفل فقط «اعلان تازه» دیده می‌شود، نه متن آن.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_GENERAL)
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setContentTitle("بیمارستان ولیعصر")
                    .setContentText("اعلان تازه دارید")
                    .build(),
            )
            .setPriority(if (important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

    private fun single(context: Context, n: NotificationDto) =
        base(context, n.important)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(openIntent(context, n.id.hashCode(), n.id, n.linkType, n.linkId))
            .build()

    private fun summary(context: Context, items: List<NotificationDto>) =
        base(context, items.any { it.important })
            .setContentTitle(toPersianDigits("${items.size} اعلان تازه"))
            .setContentText(items.last().title)
            .setStyle(
                NotificationCompat.InboxStyle().also { style -> items.takeLast(5).reversed().forEach { style.addLine(it.title) } },
            )
            .setNumber(items.size)
            .setContentIntent(openIntent(context, SUMMARY_ID, null, "notifications", null))
            .build()

    private fun openIntent(context: Context, requestCode: Int, id: String?, linkType: String?, linkId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_ID, id)
            putExtra(EXTRA_LINK_TYPE, linkType)
            putExtra(EXTRA_LINK_ID, linkId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
