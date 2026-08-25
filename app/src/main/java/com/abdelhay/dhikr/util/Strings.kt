package com.abdelhay.dhikr.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * جدول نصوص الواجهة بلغتين.
 *
 * **حدود مقصودة**: المترجَم هنا هو *هيكل* التطبيق — أسماء الأقسام والأزرار
 * والإعدادات. أمّا المحتوى — القرآن والتفسير والأذكار وأسماء السور والقرّاء —
 * فيبقى عربيًّا لأنه نصٌّ لا يُترجَم بطبيعته، ومصادره عربية.
 *
 * فمن اختار الإنجليزية وجد واجهةً إنجليزية ومحتوًى عربيًّا — وهو حال أكثر
 * تطبيقات المصحف، ويكفي غير الناطق بالعربية للتنقّل وضبط المواقيت.
 */
class Strings(private val en: Boolean) {

    private fun s(ar: String, enText: String) = if (en) enText else ar

    // الأقسام
    val tabWird get() = s("الورد", "Dhikr")
    val tabQuran get() = s("المصحف", "Quran")
    val tabPrayer get() = s("المواقيت", "Prayer")
    val tabRadio get() = s("الإذاعة", "Radio")

    // عام
    val settings get() = s("الإعدادات", "Settings")
    val stats get() = s("الإحصاءات", "Statistics")
    val back get() = s("رجوع", "Back")
    val cancel get() = s("إلغاء", "Cancel")
    val save get() = s("حفظ", "Save")
    val delete get() = s("حذف", "Delete")
    val search get() = s("ابحث", "Search")
    val done get() = s("تمّ", "Done")

    // المواقيت
    val nextPrayer get() = s("الصلاة القادمة", "Next prayer")
    val adhanCalled get() = s("أُذّن لصلاة", "Adhan for")
    val elapsed get() = s("مضى", "Elapsed")
    val remaining get() = s("بقي", "Remaining")
    val qibla get() = s("اتجاه القبلة", "Qibla direction")
    val calcMethod get() = s("طريقة الحساب", "Calculation method")
    val adhanSound get() = s("صوت الأذان", "Adhan sound")
    val location get() = s("الموقع", "Location")
    val useMyLocation get() = s("استخدم موقعي", "Use my location")

    // الإعدادات
    val language get() = s("اللغة", "Language")
    val startScreen get() = s("شاشة البدء", "Start screen")
    val appearance get() = s("المظهر", "Appearance")
    val themeSystem get() = s("تبع النظام", "System")
    val themeLight get() = s("فاتح", "Light")
    val themeDark get() = s("داكن", "Dark")
    val counting get() = s("العدّ", "Counting")
    val vibrate get() = s("اهتزاز عند كل عدّة", "Vibrate on each count")
    val keepScreenOn get() = s("إبقاء الشاشة مضاءة", "Keep screen on")

    // المصحف
    val reciter get() = s("القارئ", "Reciter")
    val listenFromHere get() = s("استمع من هنا", "Listen from here")
    val repeatAyah get() = s("كرّر الآية", "Repeat verse")
    val repeatPage get() = s("كرّر الصفحة", "Repeat page")
    val tafsir get() = s("التفسير", "Tafsir")
    val bookmark get() = s("فاصل", "Bookmark")
    val khatma get() = s("الختمة", "Khatma")
}

val LocalStrings = compositionLocalOf { Strings(en = false) }

@Composable
fun ProvideStrings(language: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides Strings(en = language == "en")) {
        content()
    }
}
