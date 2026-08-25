package com.abdelhay.dhikr.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateUtil {

    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * مفتاح اليوم الحالي.
     * [dayStartHour] يسمح ببدء اليوم فجرًا بدل منتصف الليل، فمن ذكر الله الساعة 1 صباحًا
     * يُحسب ضمن ورد اليوم السابق بدل أن يفقد تقدّمه.
     */
    fun today(dayStartHour: Int = 0, now: LocalDateTime = LocalDateTime.now()): String {
        val shifted = if (now.hour < dayStartHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return shifted.format(FMT)
    }

    fun parse(key: String): LocalDate = LocalDate.parse(key, FMT)

    fun format(date: LocalDate): String = date.format(FMT)

    /** أسماء الأيام مختصرة للرسم البياني. */
    fun shortLabel(key: String): String = runCatching { parse(key).dayOfMonth.toString() }.getOrDefault("")
}
