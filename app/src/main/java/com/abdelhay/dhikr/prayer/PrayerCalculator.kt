package com.abdelhay.dhikr.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date

enum class PrayerName(val label: String) {
    FAJR("الفجر"),
    SUNRISE("الشروق"),
    DHUHR("الظهر"),
    ASR("العصر"),
    MAGHRIB("المغرب"),
    ISHA("العشاء");

    /** الشروق ليس صلاة، فلا أذان له ولا أذكار بعده. */
    val isPrayer: Boolean get() = this != SUNRISE
}

data class PrayerSlot(val name: PrayerName, val timeMillis: Long)

data class DayPrayers(val slots: List<PrayerSlot>) {

    fun at(name: PrayerName): Long? = slots.firstOrNull { it.name == name }?.timeMillis

    /** الصلاة القادمة بعد [now]، أو null إذا انقضى اليوم كله. */
    fun next(now: Long = System.currentTimeMillis()): PrayerSlot? =
        slots.firstOrNull { it.timeMillis > now }

    /** الوقت الحالي الذي نحن فيه (آخر صلاة مضت). */
    fun current(now: Long = System.currentTimeMillis()): PrayerSlot? =
        slots.lastOrNull { it.timeMillis <= now }
}

/**
 * حساب المواقيت **محليًّا على الجهاز** بمكتبة Adhan، بمعادلات فلكية عالية الدقة.
 * لا اتصال بالإنترنت، ولا خدمة قد تتوقف، ولا بيانات موقع تغادر الهاتف.
 */
object PrayerCalculator {

    /** طرق الحساب المعتمدة، بأسماء عربية للعرض. */
    val methods: List<Pair<CalculationMethod, String>> = listOf(
        CalculationMethod.EGYPTIAN to "الهيئة المصرية العامة للمساحة",
        CalculationMethod.UMM_AL_QURA to "أم القرى — مكة المكرمة",
        CalculationMethod.MUSLIM_WORLD_LEAGUE to "رابطة العالم الإسلامي",
        CalculationMethod.KARACHI to "جامعة العلوم الإسلامية — كراتشي",
        CalculationMethod.DUBAI to "دبي",
        CalculationMethod.QATAR to "قطر",
        CalculationMethod.KUWAIT to "الكويت",
        CalculationMethod.SINGAPORE to "سنغافورة",
        CalculationMethod.MOON_SIGHTING_COMMITTEE to "لجنة رؤية الهلال",
        CalculationMethod.NORTH_AMERICA to "أمريكا الشمالية (ISNA)"
    )

    fun methodLabel(m: CalculationMethod): String =
        methods.firstOrNull { it.first == m }?.second ?: m.name

    fun parseMethod(name: String): CalculationMethod =
        runCatching { CalculationMethod.valueOf(name) }.getOrDefault(CalculationMethod.EGYPTIAN)

    fun parseMadhab(name: String): Madhab =
        runCatching { Madhab.valueOf(name) }.getOrDefault(Madhab.SHAFI)

    fun compute(
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        madhab: Madhab,
        date: Date = Date()
    ): DayPrayers {
        val params: CalculationParameters = method.parameters.apply { this.madhab = madhab }
        val times = PrayerTimes(
            Coordinates(latitude, longitude),
            DateComponents.from(date),
            params
        )
        return DayPrayers(
            listOf(
                PrayerSlot(PrayerName.FAJR, times.fajr.time),
                PrayerSlot(PrayerName.SUNRISE, times.sunrise.time),
                PrayerSlot(PrayerName.DHUHR, times.dhuhr.time),
                PrayerSlot(PrayerName.ASR, times.asr.time),
                PrayerSlot(PrayerName.MAGHRIB, times.maghrib.time),
                PrayerSlot(PrayerName.ISHA, times.isha.time)
            )
        )
    }

    /**
     * أول صلاة قادمة، ولو كانت فجر الغد.
     * تُستعمل في جدولة الأذان وفي العدّ التنازلي على الشاشة.
     */
    fun nextSlot(
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        madhab: Madhab,
        now: Long = System.currentTimeMillis()
    ): PrayerSlot? {
        compute(latitude, longitude, method, madhab).next(now)?.let { return it }
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
        }.time
        return compute(latitude, longitude, method, madhab, tomorrow).slots.firstOrNull()
    }
}
