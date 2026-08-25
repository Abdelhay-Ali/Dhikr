package com.abdelhay.dhikr.util

import com.abdelhay.dhikr.data.DhikrRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * مؤقّت حلقة الذكر.
 *
 * يقيس الزمن من لحظة دخول شاشة العدّاد، ويتوقّف تلقائيًا إذا مرّت [IDLE_MS] بلا ضغطة —
 * فلو تركتَ الهاتف مفتوحًا وانشغلت، لا يُحسب ذلك ذكرًا. هذا يجعل الرقم صادقًا،
 * وهو أهمّ من أن يكون كبيرًا.
 *
 * والتنقّل بين أذكار الورد لا يقطع الحلقة: العودة خلال [RESUME_WINDOW_MS] تُكمل الحلقة نفسها.
 */
class SessionTimer(
    private val repo: DhikrRepository,
    private val scope: CoroutineScope,
    private val dayStartHour: () -> Int
) {

    companion object {
        const val IDLE_MS = 120_000L        // دقيقتان بلا ضغطة ← إيقاف مؤقّت
        const val RESUME_WINDOW_MS = 90_000L // العودة خلالها تُكمل الحلقة نفسها
        private const val FLUSH_EVERY = 10L  // كتابة في قاعدة البيانات كل ١٠ ثوانٍ
    }

    private val _elapsed = MutableStateFlow(0L)
    /** ثواني الحلقة الحالية. */
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    private val _running = MutableStateFlow(false)
    /** false تعني أن المؤقّت متوقّف لخمول أو لخروجك من الشاشة. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var tickJob: Job? = null
    private var lastActivity = 0L
    private var pausedAt = 0L
    private var unflushed = 0L
    private var currentDhikrId: Long? = null

    /** يُستدعى عند دخول شاشة العدّاد. */
    fun enter(dhikrId: Long) {
        currentDhikrId = dhikrId
        val now = System.currentTimeMillis()
        val isNewSession = pausedAt == 0L || (now - pausedAt) > RESUME_WINDOW_MS
        if (isNewSession) {
            _elapsed.value = 0L
            unflushed = 0L
            scope.launch { repo.markSessionStart(dayStartHour()) }
        }
        lastActivity = now
        pausedAt = 0L
        startTicking()
    }

    /** يُستدعى عند مغادرة الشاشة أو انتقال التطبيق إلى الخلفية. */
    fun leave() {
        pausedAt = System.currentTimeMillis()
        _running.value = false
        tickJob?.cancel()
        tickJob = null
        flush()
    }

    /** كل ضغطة عدّ تُبقي المؤقّت حيًّا. */
    fun onActivity(dhikrId: Long) {
        currentDhikrId = dhikrId
        lastActivity = System.currentTimeMillis()
        if (tickJob == null) startTicking()
    }

    private fun startTicking() {
        if (tickJob != null) return
        _running.value = true
        tickJob = scope.launch {
            while (true) {
                delay(1000)
                val idle = System.currentTimeMillis() - lastActivity
                if (idle > IDLE_MS) {
                    // خمول: نتوقّف عن العدّ لكن نبقى مستعدّين للاستئناف عند أول ضغطة
                    _running.value = false
                    flush()
                    continue
                }
                _running.value = true
                _elapsed.value = _elapsed.value + 1
                unflushed += 1
                if (unflushed >= FLUSH_EVERY) flush()
            }
        }
    }

    private fun flush() {
        val delta = unflushed
        if (delta <= 0) return
        unflushed = 0
        val id = currentDhikrId
        scope.launch { repo.addSeconds(dayStartHour(), delta, id) }
    }
}

/** ٠٥:٤٢ أو ١:٠٥:٤٢ */
fun formatClock(seconds: Long, arabicDigits: Boolean): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val raw = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    if (!arabicDigits) return raw
    val map = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return buildString { raw.forEach { c -> append(if (c.isDigit()) map[c - '0'] else c) } }
}

/** «١٢ دقيقة» / «١ ساعة و٥ دقائق» — للعرض في الملخّصات. */
fun humanDuration(seconds: Long, arabicDigits: Boolean): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    fun n(v: Long) = v.toArabicDigits(arabicDigits)
    return when {
        seconds < 60 -> "${n(seconds)} ثانية"
        h == 0L -> "${n(m)} دقيقة"
        m == 0L -> "${n(h)} ساعة"
        else -> "${n(h)} ساعة و${n(m)} دقيقة"
    }
}

/** تنسيق وقت الصلاة: ٥:١٤ ص / ٦:٤٧ م، أو نظام ٢٤ ساعة إن اختاره المستخدم. */
fun formatPrayerTime(millis: Long, use12Hour: Boolean, arabicDigits: Boolean): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val h24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val raw: String
    val suffix: String
    if (use12Hour) {
        val h = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        raw = "%d:%02d".format(h, minute)
        suffix = if (h24 < 12) " ص" else " م"
    } else {
        raw = "%02d:%02d".format(h24, minute)
        suffix = ""
    }
    if (!arabicDigits) return raw + suffix
    val map = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
    return buildString { raw.forEach { c -> append(if (c.isDigit()) map[c - '0'] else c) } } + suffix
}
