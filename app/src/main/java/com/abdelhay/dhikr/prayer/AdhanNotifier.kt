package com.abdelhay.dhikr.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.abdelhay.dhikr.MainActivity
import com.abdelhay.dhikr.R
import com.abdelhay.dhikr.app
import com.abdelhay.dhikr.data.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** خيار صوت جاهز يظهر للمستخدم. */
data class AdhanChoice(val key: String, val label: String)

object AdhanNotifier {

    const val CHANNEL_ADHAN = "adhan_v2"
    const val CHANNEL_PRE = "pre_adhan"
    const val CHANNEL_PLAYBACK = "adhan_playback"
    const val CHANNEL_AFTER = "after_prayer_adhkar"
    const val NOTIF_AFTER = 2004
    /** مهلة بعد الأذان تكفي لأداء الصلاة قبل التذكير بأذكارها. */
    const val AFTER_PRAYER_DELAY_MIN = 10
    const val NOTIF_ADHAN = 2001
    const val NOTIF_PRE = 2002

    private const val REQ_ADHAN = 7100
    private const val REQ_PRE = 7101
    private const val REQ_AFTER = 7102

    const val SOUND_DEFAULT = ""
    const val SOUND_SILENT = "silent"
    const val SOUND_SYSTEM_ALARM = "system_alarm"

    /**
     * ملفات الأذان الاختيارية: ضع أيًّا منها في `res/raw` بالاسم المذكور،
     * ويظهر الخيار تلقائيًا. نبحث بالاسم لا بالمرجع فلا يتعطّل البناء إن غابت.
     */
    val bundled = listOf(
        "adhan_makkah" to "الحرم المكي",
        "adhan_madinah" to "المسجد النبوي",
        "adhan_masr" to "مصر",
        "adhan_aqsa" to "المسجد الأقصى",
        "adhan_qatar" to "قطر",
        "adhan_short" to "أذان قصير",
        "a1" to "أحمد جلال يحيى",
        "a2" to "رابح بن دراح الجزائري",
        "a3" to "حمزة المجالي",
        "a4" to "محمد الدمرداش"
    )

    /** أذان الفجر يزيد «الصلاة خير من النوم»، فله ملفاته المستقلة. */
    val bundledFajr = listOf(
        "adhan_fajr_makkah" to "فجر الحرم المكي",
        "adhan_fajr_madinah" to "فجر المسجد النبوي",
        "adhan_fajr_masr" to "فجر مصر"
    )

    fun isBundled(key: String): Boolean =
        key.isNotEmpty() && (bundled.any { it.first == key } || bundledFajr.any { it.first == key })

    fun labelOf(key: String): String =
        (bundled + bundledFajr).firstOrNull { it.first == key }?.second ?: "الأذان"

    fun fajrChoices(context: Context): List<AdhanChoice> {
        val list = mutableListOf(AdhanChoice("", "نفس صوت باقي الصلوات"))
        bundledFajr.forEach { (n, l) -> if (rawId(context, n) != 0) list += AdhanChoice(n, l) }
        return list
    }

    fun availableChoices(context: Context): List<AdhanChoice> {
        val list = mutableListOf(
            AdhanChoice(SOUND_DEFAULT, "صوت الإشعار الافتراضي"),
            AdhanChoice(SOUND_SYSTEM_ALARM, "نغمة المنبّه في الجهاز"),
            AdhanChoice(SOUND_SILENT, "صامت (اهتزاز فقط)")
        )
        bundled.forEach { (name, label) ->
            if (rawId(context, name) != 0) list += AdhanChoice(name, "أذان $label")
        }
        return list
    }

    private fun rawId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "raw", context.packageName)

    private fun soundUri(context: Context, key: String): Uri? = when (key) {
        SOUND_SILENT -> null
        SOUND_SYSTEM_ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        SOUND_DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> {
            val id = rawId(context, key)
            if (id != 0) Uri.parse("android.resource://${context.packageName}/$id")
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    /**
     * صوت القناة يُثبَّت عند إنشائها ولا يقبل التعديل بعد ذلك في أندرويد ٨ فأحدث.
     * لذلك نُنشئ قناة جديدة لكل صوت يختاره المستخدم ونحذف السابقة —
     * وهي الحيلة المعتادة للالتفاف على هذا القيد.
     */
    fun createChannels(context: Context, adhanSound: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        val channelId = adhanChannelId(adhanSound)
        nm.notificationChannels
            .filter { it.id.startsWith("$CHANNEL_ADHAN:") && it.id != channelId }
            .forEach { nm.deleteNotificationChannel(it.id) }

        if (nm.getNotificationChannel(channelId) == null) {
            val ch = NotificationChannel(
                channelId,
                context.getString(R.string.channel_adhan_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_adhan_desc)
                enableVibration(true)
                // إن كان الصوت ملفًّا مضمّنًا فسنشغّله بأنفسنا، والقناة تبقى صامتة
                val uri = if (isBundled(adhanSound)) null else soundUri(context, adhanSound)
                if (uri == null) setSound(null, null) else setSound(
                    uri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            nm.createNotificationChannel(ch)
        }

        if (nm.getNotificationChannel(CHANNEL_PLAYBACK) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PLAYBACK,
                    context.getString(R.string.channel_playback_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_playback_desc)
                    // الصوت يخرج من المشغّل نفسه، فالقناة صامتة كي لا يتداخل صوتان
                    setSound(null, null)
                }
            )
        }

        if (nm.getNotificationChannel(CHANNEL_AFTER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AFTER,
                    context.getString(R.string.channel_after_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.channel_after_desc) }
            )
        }

        if (nm.getNotificationChannel(CHANNEL_PRE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PRE,
                    context.getString(R.string.channel_pre_adhan_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.channel_pre_adhan_desc) }
            )
        }
    }

    private fun adhanChannelId(sound: String) =
        "$CHANNEL_ADHAN:${sound.ifEmpty { "default" }}"

    // ── الإشعارات ──

    fun notifyAdhan(context: Context, slot: PrayerSlot, sound: String) {
        createChannels(context, sound)
        val n = NotificationCompat.Builder(context, adhanChannelId(sound))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.adhan_title, slot.name.label))
            .setContentText(context.getString(R.string.adhan_body))
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { soundUri(context, sound)?.let { setSound(it, android.media.AudioManager.STREAM_ALARM) } }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ADHAN, n) }
    }

    fun notifyPreAdhan(context: Context, slot: PrayerSlot, minutes: Int) {
        val n = NotificationCompat.Builder(context, CHANNEL_PRE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.pre_adhan_title, minutes, slot.name.label))
            .setContentText(context.getString(R.string.pre_adhan_body))
            .setContentIntent(openApp(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_PRE, n) }
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, "prayer")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // ── الجدولة ──

    /** يجدول الصلاة القادمة وتنبيهها المسبق. يُعاد استدعاؤه بعد كل إطلاق. */
    fun rescheduleAll(context: Context, s: Settings) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pi(context, REQ_ADHAN, AdhanReceiver::class.java, AdhanReceiver.KIND_ADHAN))
        am.cancel(pi(context, REQ_PRE, AdhanReceiver::class.java, AdhanReceiver.KIND_PRE))
        am.cancel(pi(context, REQ_AFTER, AdhanReceiver::class.java, AdhanReceiver.KIND_AFTER))
        if (!s.prayerNotifications || !s.hasLocation) return

        val slot = nextPrayerSlot(s) ?: return
        setAlarm(am, context, REQ_ADHAN, slot.timeMillis, AdhanReceiver.KIND_ADHAN)

        if (s.preAdhanEnabled) {
            val at = slot.timeMillis - s.preAdhanMinutes * 60_000L
            if (at > System.currentTimeMillis()) setAlarm(am, context, REQ_PRE, at, AdhanReceiver.KIND_PRE)
        }

        // تذكير أذكار ما بعد الصلاة: نجدوله من الصلاة **الماضية** إن كان وقتها قريبًا،
        // وإلا فمن القادمة — فلا يضيع التذكير حين يُعاد الجدولة بعد الأذان مباشرة.
        if (s.afterPrayerAdhkar) {
            val now = System.currentTimeMillis()
            val previous = previousPrayerSlot(s, now)
            val candidate = previous?.let { it.timeMillis + AFTER_PRAYER_DELAY_MIN * 60_000L }
            val at = if (candidate != null && candidate > now) candidate
            else slot.timeMillis + AFTER_PRAYER_DELAY_MIN * 60_000L
            setAlarm(am, context, REQ_AFTER, at, AdhanReceiver.KIND_AFTER)
        }
    }

    /** آخر صلاة مضى وقتها اليوم. */
    fun previousPrayerSlot(s: Settings, now: Long = System.currentTimeMillis()): PrayerSlot? {
        val today = PrayerCalculator.compute(
            s.latitude, s.longitude,
            PrayerCalculator.parseMethod(s.calculationMethod),
            PrayerCalculator.parseMadhab(s.madhab)
        )
        return today.slots.lastOrNull { it.name.isPrayer && it.timeMillis <= now }
    }

    /** أول وقت صلاة قادم (نتجاوز الشروق فليس صلاة). */
    fun nextPrayerSlot(s: Settings, from: Long = System.currentTimeMillis()): PrayerSlot? {
        val method = PrayerCalculator.parseMethod(s.calculationMethod)
        val madhab = PrayerCalculator.parseMadhab(s.madhab)
        var cursor = from
        repeat(8) {
            val slot = PrayerCalculator.nextSlot(s.latitude, s.longitude, method, madhab, cursor)
                ?: return null
            if (slot.name.isPrayer) return slot
            cursor = slot.timeMillis + 1000
        }
        return null
    }

    /**
     * الأذان يحتاج إنذارًا دقيقًا: تأخّره خمس دقائق يفقده معناه.
     * وهو أيضًا شرط تشغيل خدمة أمامية من مستقبِل بثّ في أندرويد ١٢ فأحدث.
     * إن لم يمنح المستخدم الإذن نرجع إلى إنذار غير دقيق بدل أن نعطّل الميزة.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun setAlarm(am: AlarmManager, context: Context, req: Int, at: Long, kind: String) {
        val intent = pi(context, req, AdhanReceiver::class.java, kind)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canScheduleExact(context) ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else -> am.set(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun pi(context: Context, req: Int, cls: Class<*>, kind: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, cls).putExtra(AdhanReceiver.EXTRA_KIND, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class AdhanReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_KIND = "kind"
        const val KIND_ADHAN = "adhan"
        const val KIND_PRE = "pre"
        const val KIND_AFTER = "after"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: KIND_ADHAN
        val pending = goAsync()
        val app = context.app
        app.appScope.launch {
            try {
                val s = app.settings.flow.first()
                if (!s.prayerNotifications || !s.hasLocation) return@launch

                if (kind == KIND_AFTER) {
                    if (s.afterPrayerAdhkar) {
                        val prev = AdhanNotifier.previousPrayerSlot(s)
                        if (prev != null) AfterPrayerNotifier.notify(context, prev.name.label)
                    }
                } else if (kind == KIND_PRE) {
                    AdhanNotifier.nextPrayerSlot(s)?.let {
                        AdhanNotifier.notifyPreAdhan(context, it, s.preAdhanMinutes)
                    }
                } else {
                    val now = System.currentTimeMillis()
                    val today = PrayerCalculator.compute(
                        s.latitude, s.longitude,
                        PrayerCalculator.parseMethod(s.calculationMethod),
                        PrayerCalculator.parseMadhab(s.madhab)
                    )
                    val fired = today.slots.lastOrNull {
                        it.name.isPrayer && it.timeMillis <= now + 120_000
                    }
                    if (fired != null && now - fired.timeMillis < 10 * 60_000) {
                        val sound = if (fired.name == PrayerName.FAJR && s.adhanSoundFajr.isNotEmpty())
                            s.adhanSoundFajr else s.adhanSound
                        AdhanNotifier.notifyAdhan(context, fired, sound)
                        if (AdhanNotifier.isBundled(sound)) {
                            AdhanPlayerService.play(
                                context, sound,
                                "${fired.name.label} — ${AdhanNotifier.labelOf(sound)}"
                            )
                        }
                    }
                }
                AdhanNotifier.rescheduleAll(context, s)
            } finally {
                pending.finish()
            }
        }
    }
}


/** تذكير بأذكار ما بعد الصلاة، يفتح الورد الجاهز مباشرة. */
object AfterPrayerNotifier {

    fun notify(context: Context, prayerLabel: String) {
        val open = PendingIntent.getActivity(
            context, 4,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "after_prayer")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, AdhanNotifier.CHANNEL_AFTER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.after_prayer_title, prayerLabel))
            .setContentText(context.getString(R.string.after_prayer_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(AdhanNotifier.NOTIF_AFTER, n) }
    }
}
