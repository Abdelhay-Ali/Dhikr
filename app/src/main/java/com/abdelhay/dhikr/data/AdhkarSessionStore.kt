package com.abdelhay.dhikr.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abdelhay.dhikr.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adhkarStore by preferencesDataStore("adhkar_sessions")

/** أوراد جاهزة يفتحها المستخدم من شاشة الورد. */
enum class AdhkarSet(val key: String, val title: String, val subtitle: String) {
    MORNING("morning", "أذكار الصباح", "تُقال بعد الفجر إلى الضحى"),
    EVENING("evening", "أذكار المساء", "تُقال بعد العصر إلى المغرب"),
    DUA("dua", "أدعية مأثورة", "من القرآن والسنّة"),
    AFTER_PRAYER("after_prayer", "أذكار بعد الصلاة", "عقب كل صلاة مكتوبة");

    fun presets(): List<Preset> = when (this) {
        MORNING -> Presets.morning
        EVENING -> Presets.evening
        DUA -> Presets.duas
        AFTER_PRAYER -> Presets.afterPrayerAdhkar
    }

    companion object {
        fun from(key: String?): AdhkarSet =
            entries.firstOrNull { it.key == key } ?: MORNING
    }
}

/**
 * تقدّم الورد الجاهز محفوظ ليومه فقط.
 *
 * لو انقطعتَ في منتصف أذكار الصباح ثم عدت، تجد ما أتممتَه كما تركته؛
 * وفي اليوم التالي يبدأ الورد من جديد بلا تدخّل منك — التاريخ مخزون مع العدّات،
 * فاختلافه عن اليوم يعني تصفيرًا تلقائيًا.
 */
class AdhkarSessionStore(private val context: Context) {

    private fun key(set: AdhkarSet) = stringPreferencesKey("session_${set.key}")

    fun observe(set: AdhkarSet, dayStartHour: Int): Flow<List<Int>> =
        context.adhkarStore.data.map { p -> decode(p[key(set)], dayStartHour) }

    suspend fun setCounts(set: AdhkarSet, counts: List<Int>, dayStartHour: Int) {
        context.adhkarStore.edit { p ->
            p[key(set)] = DateUtil.today(dayStartHour) + "|" + counts.joinToString(",")
        }
    }

    suspend fun reset(set: AdhkarSet, dayStartHour: Int) = setCounts(set, emptyList(), dayStartHour)

    private fun decode(raw: String?, dayStartHour: Int): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val parts = raw.split("|", limit = 2)
        if (parts.size != 2) return emptyList()
        if (parts[0] != DateUtil.today(dayStartHour)) return emptyList()   // يوم جديد
        return parts[1].split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
