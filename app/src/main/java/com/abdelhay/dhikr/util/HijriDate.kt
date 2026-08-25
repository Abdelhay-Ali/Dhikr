package com.abdelhay.dhikr.util

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * التاريخ الهجري.
 *
 * نستعمل `HijrahChronology` وهو تقويم **أم القرى** الحسابي. وهو تقدير فلكي،
 * وقد يخالف رؤية بلدك يومًا في الشهر الواحد — ولذلك جعلنا الإزاحة [offset]
 * بيد المستخدم بدل ادّعاء دقّة لا يملكها حساب.
 */
object HijriDate {

    private val monthNames = arrayOf(
        "محرَّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة"
    )

    private val dayNames = arrayOf(
        "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"
    )

    data class Parts(
        val day: Int,
        val month: Int,
        val year: Int,
        val monthName: String,
        val dayName: String
    ) {
        val isRamadan: Boolean get() = month == 9
        val isFriday: Boolean get() = dayName == "الجمعة"
    }

    /**
     * [offsetDays] يزيح **التاريخ الهجري** وحده.
     *
     * اسم اليوم يُؤخذ من التاريخ الميلادي الحقيقي لا من المُزاح: الجمعة تبقى جمعة
     * مهما عدّلتَ الشهر الهجري، لأن الإزاحة تصحّح فرق الرؤية لا تغيّر اليوم نفسه.
     */
    fun of(date: LocalDate = LocalDate.now(), offsetDays: Int = 0): Parts {
        val shifted = date.plusDays(offsetDays.toLong())
        val h: HijrahDate = HijrahChronology.INSTANCE.date(shifted)
        val month = h.get(ChronoField.MONTH_OF_YEAR)
        return Parts(
            day = h.get(ChronoField.DAY_OF_MONTH),
            month = month,
            year = h.get(ChronoField.YEAR),
            monthName = monthNames.getOrElse(month - 1) { "" },
            dayName = dayNames.getOrElse(date.dayOfWeek.value - 1) { "" }
        )
    }

    /** «الجمعة ١٢ رجب ١٤٤٧هـ» */
    fun format(parts: Parts, arabicDigits: Boolean): String =
        "${parts.dayName} ${parts.day.toArabicDigits(arabicDigits)} ${parts.monthName} " +
            "${parts.year.toArabicDigits(arabicDigits)}هـ"

    /** ملاحظة موسمية تظهر تحت التاريخ حين يكون لليوم شأن. */
    fun occasion(parts: Parts): String? = when {
        parts.month == 9 && parts.day <= 30 -> "رمضان — اليوم ${parts.day}"
        parts.month == 12 && parts.day == 9 -> "يوم عرفة"
        parts.month == 12 && parts.day in 10..13 -> "أيام عيد الأضحى"
        parts.month == 10 && parts.day == 1 -> "عيد الفطر"
        parts.month == 1 && parts.day == 10 -> "يوم عاشوراء"
        parts.month == 8 && parts.day == 15 -> "منتصف شعبان"
        parts.day in 13..15 -> "الأيام البيض"
        parts.isFriday -> "يوم الجمعة — سورة الكهف والإكثار من الصلاة على النبي ﷺ"
        else -> null
    }
}
