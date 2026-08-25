package com.abdelhay.dhikr.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abdelhay.dhikr.app
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** يُطلق عند موعد التذكير: يختار أول ذكر غير مكتمل ويعرضه في الإشعار. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.app
        app.appScope.launch {
            try {
                val settings = app.settings.flow.first()
                app.repository.rolloverIfNeeded(settings.dayStartHour)

                val next = app.repository.nextIncomplete()
                if (next != null && settings.remindersEnabled) {
                    val remaining = app.repository.activeAdhkar.first().count { !it.isCompleted }
                    Notifications.showWirdReminder(context, next, remaining)
                }
                // إعادة جدولة مواعيد الغد
                ReminderScheduler.reschedule(context, settings.remindersEnabled, settings.reminderTimes)
            } finally {
                pending.finish()
            }
        }
    }
}

/** يعيد بناء الجدول بعد إعادة التشغيل أو تغيير الوقت أو تحديث التطبيق. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.app
        app.appScope.launch {
            try {
                val s = app.settings.flow.first()
                ReminderScheduler.reschedule(context, s.remindersEnabled, s.reminderTimes)
            } finally {
                pending.finish()
            }
        }
    }
}

/** أزرار الإشعار. */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.abdelhay.dhikr.SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_SNOOZE -> {
                Notifications.cancel(context)
                ReminderScheduler.snooze(context, 60)
            }
        }
    }
}
