package com.abdelhay.dhikr.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * جدولة تذكيرات الورد.
 * نستعمل إنذارًا غير دقيق يعمل حتى في وضع توفير الطاقة، ولا يحتاج أي إذن خاص،
 * ونعيد جدولة الموعد التالي بعد كل إطلاق وبعد إعادة تشغيل الجهاز.
 */
object ReminderScheduler {

    private const val REQUEST_BASE = 5000

    fun reschedule(context: Context, enabled: Boolean, times: List<String>) {
        cancelAll(context, times.size.coerceAtLeast(8))
        if (!enabled || times.isEmpty()) return

        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()

        times.forEachIndexed { index, t ->
            val time = parse(t) ?: return@forEachIndexed
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val pi = pendingIntent(context, index, t)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
    }

    /** تأجيل الإشعار الحالي مدة [minutes]. */
    fun snooze(context: Context, minutes: Int = 60) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + minutes * 60_000L
        val pi = pendingIntent(context, 99, "snooze")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(context: Context, index: Int, tag: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + index,
            Intent(context, ReminderReceiver::class.java).putExtra("tag", tag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelAll(context: Context, upTo: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until upTo) am.cancel(pendingIntent(context, i, "cancel"))
    }

    private fun parse(hhmm: String): LocalTime? = runCatching {
        val (h, m) = hhmm.split(":").map { it.trim().toInt() }
        LocalTime.of(h, m)
    }.getOrNull()
}
