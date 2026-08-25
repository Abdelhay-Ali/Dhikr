package com.abdelhay.dhikr.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val vibrate: Boolean = true,
    val clickSound: Boolean = false,
    val keepScreenOn: Boolean = true,
    val volumeKeysCount: Boolean = true,
    val arabicNumerals: Boolean = true,
    val autoAdvance: Boolean = true,
    val darkTheme: Boolean? = null,          // null = تبع النظام
    val remindersEnabled: Boolean = true,
    val reminderTimes: List<String> = listOf("07:00", "13:30", "20:30"),
    val dayStartHour: Int = 4,               // اليوم يبدأ الرابعة فجرًا
    val fontScale: Float = 1f,

    // ── المواقيت ──
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeName: String = "",
    val calculationMethod: String = "EGYPTIAN",
    val madhab: String = "SHAFI",
    val prayerNotifications: Boolean = true,
    val afterPrayerAdhkar: Boolean = true,
    val hijriOffset: Int = 0,
    /** "" = صوت الإشعار الافتراضي، "silent" = صامت، أو معرّف نغمة/ملف. */
    val adhanSound: String = "",
    val adhanSoundFajr: String = "",
    val preAdhanEnabled: Boolean = true,
    val preAdhanMinutes: Int = 10,
    val use12Hour: Boolean = true,

    // ── المصحف ──
    val lastSurah: Int = 1,
    val lastVerse: Int = 0,
    val quranFontScale: Float = 1f,
    val quranFont: String = "kfgqpc",
    val reciter: String = "husary",
    val mushafLandscape: Boolean = false,
    val startTab: String = "prayer",
    val language: String = "ar"
) {
    val hasLocation: Boolean get() = latitude != 0.0 || longitude != 0.0
}

class SettingsStore(private val context: Context) {

    private object K {
        val VIBRATE = booleanPreferencesKey("vibrate")
        val SOUND = booleanPreferencesKey("sound")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
        val ARABIC_NUM = booleanPreferencesKey("arabic_numerals")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        val DARK = stringPreferencesKey("dark")           // "system" | "dark" | "light"
        val REMIND = booleanPreferencesKey("reminders")
        val TIMES = stringPreferencesKey("reminder_times")
        val DAY_START = intPreferencesKey("day_start_hour")
        val FONT = floatPreferencesKey("font_scale")
        val LAT = doublePreferencesKey("latitude")
        val LNG = doublePreferencesKey("longitude")
        val PLACE = stringPreferencesKey("place_name")
        val METHOD = stringPreferencesKey("calc_method")
        val MADHAB = stringPreferencesKey("madhab")
        val PRAYER_NOTIF = booleanPreferencesKey("prayer_notifications")
        val AFTER_PRAYER = booleanPreferencesKey("after_prayer_adhkar")
        val HIJRI_OFFSET = intPreferencesKey("hijri_offset")
        val LAST_SURAH = intPreferencesKey("last_surah")
        val LAST_VERSE = intPreferencesKey("last_verse")
        val QURAN_FONT = floatPreferencesKey("quran_font_scale")
        val QURAN_TYPEFACE = stringPreferencesKey("quran_typeface")
        val RECITER = stringPreferencesKey("reciter")
        val LANDSCAPE = booleanPreferencesKey("mushaf_landscape")
        val START_TAB = stringPreferencesKey("start_tab")
        val LANGUAGE = stringPreferencesKey("language")
        val ADHAN_SOUND = stringPreferencesKey("adhan_sound")
        val ADHAN_SOUND_FAJR = stringPreferencesKey("adhan_sound_fajr")
        val PRE_ADHAN = booleanPreferencesKey("pre_adhan")
        val PRE_ADHAN_MIN = intPreferencesKey("pre_adhan_minutes")
        val USE_12H = booleanPreferencesKey("use_12_hour")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            vibrate = p[K.VIBRATE] ?: true,
            clickSound = p[K.SOUND] ?: false,
            keepScreenOn = p[K.KEEP_SCREEN] ?: true,
            volumeKeysCount = p[K.VOLUME_KEYS] ?: true,
            arabicNumerals = p[K.ARABIC_NUM] ?: true,
            autoAdvance = p[K.AUTO_ADVANCE] ?: true,
            darkTheme = when (p[K.DARK]) {
                "dark" -> true
                "light" -> false
                else -> null
            },
            remindersEnabled = p[K.REMIND] ?: true,
            reminderTimes = (p[K.TIMES] ?: "07:00,13:30,20:30")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() },
            dayStartHour = p[K.DAY_START] ?: 4,
            fontScale = p[K.FONT] ?: 1f,
            latitude = p[K.LAT] ?: 0.0,
            longitude = p[K.LNG] ?: 0.0,
            placeName = p[K.PLACE] ?: "",
            calculationMethod = p[K.METHOD] ?: "EGYPTIAN",
            madhab = p[K.MADHAB] ?: "SHAFI",
            prayerNotifications = p[K.PRAYER_NOTIF] ?: true,
            afterPrayerAdhkar = p[K.AFTER_PRAYER] ?: true,
            hijriOffset = p[K.HIJRI_OFFSET] ?: 0,
            adhanSound = p[K.ADHAN_SOUND] ?: "",
            adhanSoundFajr = p[K.ADHAN_SOUND_FAJR] ?: "",
            preAdhanEnabled = p[K.PRE_ADHAN] ?: true,
            preAdhanMinutes = p[K.PRE_ADHAN_MIN] ?: 10,
            use12Hour = p[K.USE_12H] ?: true,
            lastSurah = p[K.LAST_SURAH] ?: 1,
            lastVerse = p[K.LAST_VERSE] ?: 0,
            quranFontScale = p[K.QURAN_FONT] ?: 1f,
            quranFont = p[K.QURAN_TYPEFACE] ?: "kfgqpc",
            reciter = p[K.RECITER] ?: "husary",
            mushafLandscape = p[K.LANDSCAPE] ?: false,
            startTab = p[K.START_TAB] ?: "prayer",
            language = p[K.LANGUAGE] ?: "ar"
        )
    }

    suspend fun setVibrate(v: Boolean) = edit { it[K.VIBRATE] = v }
    suspend fun setSound(v: Boolean) = edit { it[K.SOUND] = v }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[K.KEEP_SCREEN] = v }
    suspend fun setVolumeKeys(v: Boolean) = edit { it[K.VOLUME_KEYS] = v }
    suspend fun setArabicNumerals(v: Boolean) = edit { it[K.ARABIC_NUM] = v }
    suspend fun setAutoAdvance(v: Boolean) = edit { it[K.AUTO_ADVANCE] = v }
    suspend fun setDark(mode: String) = edit { it[K.DARK] = mode }
    suspend fun setReminders(v: Boolean) = edit { it[K.REMIND] = v }
    suspend fun setReminderTimes(times: List<String>) = edit { it[K.TIMES] = times.joinToString(",") }
    suspend fun setDayStartHour(h: Int) = edit { it[K.DAY_START] = h.coerceIn(0, 12) }
    suspend fun setFontScale(f: Float) = edit { it[K.FONT] = f.coerceIn(0.8f, 1.8f) }

    suspend fun setLocation(lat: Double, lng: Double, name: String) = edit {
        it[K.LAT] = lat; it[K.LNG] = lng; it[K.PLACE] = name
    }
    suspend fun setMethod(m: String) = edit { it[K.METHOD] = m }
    suspend fun setMadhab(m: String) = edit { it[K.MADHAB] = m }
    suspend fun setPrayerNotifications(v: Boolean) = edit { it[K.PRAYER_NOTIF] = v }
    suspend fun setAfterPrayerAdhkar(v: Boolean) = edit { it[K.AFTER_PRAYER] = v }
    suspend fun setHijriOffset(v: Int) = edit { it[K.HIJRI_OFFSET] = v.coerceIn(-2, 2) }
    suspend fun setLastRead(surah: Int, verse: Int) = edit {
        it[K.LAST_SURAH] = surah; it[K.LAST_VERSE] = verse
    }
    suspend fun setQuranFontScale(f: Float) = edit { it[K.QURAN_FONT] = f.coerceIn(1f, 3f) }
    suspend fun setQuranFont(key: String) = edit { it[K.QURAN_TYPEFACE] = key }
    suspend fun setReciter(key: String) = edit { it[K.RECITER] = key }
    suspend fun setMushafLandscape(v: Boolean) = edit { it[K.LANDSCAPE] = v }
    suspend fun setStartTab(v: String) = edit { it[K.START_TAB] = v }
    suspend fun setLanguage(v: String) = edit { it[K.LANGUAGE] = v }

    suspend fun setAdhanSound(uri: String) = edit { it[K.ADHAN_SOUND] = uri }
    suspend fun setAdhanSoundFajr(uri: String) = edit { it[K.ADHAN_SOUND_FAJR] = uri }
    suspend fun setPreAdhan(enabled: Boolean, minutes: Int) = edit {
        it[K.PRE_ADHAN] = enabled; it[K.PRE_ADHAN_MIN] = minutes.coerceIn(1, 60)
    }
    suspend fun setUse12Hour(v: Boolean) = edit { it[K.USE_12H] = v }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
