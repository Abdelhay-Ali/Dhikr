package com.abdelhay.dhikr.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.abdelhay.dhikr.MainActivity
import com.abdelhay.dhikr.R
import com.abdelhay.dhikr.data.Dhikr

object Notifications {

    const val CHANNEL_REMINDER = "wird_reminder"
    const val NOTIF_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.channel_reminder_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_reminder_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * إشعار الورد: يعرض نص الذكر كاملًا والمتبقّي منه،
     * وفيه زرّان — فتح العدّاد مباشرة على هذا الذكر، أو تأجيل ساعة.
     */
    fun showWirdReminder(context: Context, dhikr: Dhikr, remainingAdhkar: Int) {
        val open = PendingIntent.getActivity(
            context, dhikr.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DHIKR, dhikr.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notif_title, dhikr.remaining)
        val sub = if (remainingAdhkar > 1)
            context.getString(R.string.notif_sub_many, remainingAdhkar) else null

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(dhikr.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(dhikr.text).setSummaryText(sub))
            .setSubText(sub)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, context.getString(R.string.notif_action_open), open)
            .addAction(0, context.getString(R.string.notif_action_snooze), snooze)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
    }

    fun cancel(context: Context) =
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
}
