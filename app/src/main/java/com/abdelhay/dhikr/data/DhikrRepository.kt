package com.abdelhay.dhikr.data

import com.abdelhay.dhikr.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DhikrRepository(private val dao: DhikrDao) {

    val activeAdhkar: Flow<List<Dhikr>> = dao.observeActive()
    val allAdhkar: Flow<List<Dhikr>> = dao.observeAll()
    val lifetimeTotal: Flow<Long> = dao.observeLifetimeTotal()
    val daySummaries: Flow<List<DaySummary>> = dao.observeDaySummaries()
    val secondsTotal: Flow<Long> = dao.observeSecondsTotal()

    fun observeSecondsToday(dayStartHour: Int): Flow<Long> =
        dao.observeSessionDay(DateUtil.today(dayStartHour)).map { it?.seconds ?: 0L }

    fun observeSecondsSince(fromDate: String): Flow<Long> = dao.observeSecondsSince(fromDate)

    fun observe(id: Long): Flow<Dhikr?> = dao.observeOne(id)

    /** ينشئ الورد الابتدائي عند أول تشغيل فقط. */
    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            dao.insertAll(Presets.starter.mapIndexed { i, p -> Presets.toDhikr(p, i) })
        }
    }

    /**
     * التصفير التلقائي عند دخول يوم جديد.
     * قبل التصفير يُحفظ إنجاز اليوم السابق في السجل، فلا يضيع التاريخ.
     */
    suspend fun rolloverIfNeeded(dayStartHour: Int) {
        val today = DateUtil.today(dayStartHour)
        dao.allOnce().forEach { d ->
            if (d.lastResetDate.isNotEmpty() && d.lastResetDate != today) {
                dao.upsertLog(DailyLog(d.lastResetDate, d.id, d.done, d.target))
            }
            if (d.lastResetDate != today) {
                dao.update(d.copy(raw = d.startValue(), lastResetDate = today))
            }
        }
    }

    /** يسجّل تقدّم اليوم الحالي حتى تكون الإحصاءات صحيحة قبل منتصف الليل. */
    private suspend fun touchLog(d: Dhikr, today: String) {
        dao.upsertLog(DailyLog(today, d.id, d.done, d.target))
    }

    /** يزيد الذكر بمقدار [step] ويعيد النسخة المحدّثة. */
    suspend fun increment(id: Long, dayStartHour: Int, step: Int = 1): Dhikr? {
        val d = dao.byId(id) ?: return null
        val today = DateUtil.today(dayStartHour)
        val newRaw = when (d.mode) {
            CountMode.UP -> d.raw + step
            CountMode.DOWN -> (d.raw - step).coerceAtLeast(0)
        }
        val actualStep = kotlin.math.abs(newRaw - d.raw)
        val updated = d.copy(raw = newRaw, lifetime = d.lifetime + actualStep, lastResetDate = today)
        dao.update(updated)
        touchLog(updated, today)
        return updated
    }

    /** تراجع عن ضغطة واحدة (خطأ شائع أثناء العد). */
    suspend fun undo(id: Long, dayStartHour: Int): Dhikr? {
        val d = dao.byId(id) ?: return null
        val today = DateUtil.today(dayStartHour)
        val newRaw = when (d.mode) {
            CountMode.UP -> (d.raw - 1).coerceAtLeast(0)
            CountMode.DOWN -> (d.raw + 1).coerceAtMost(d.target)
        }
        if (newRaw == d.raw) return d
        val updated = d.copy(raw = newRaw, lifetime = (d.lifetime - 1).coerceAtLeast(0), lastResetDate = today)
        dao.update(updated)
        touchLog(updated, today)
        return updated
    }

    suspend fun resetOne(id: Long, dayStartHour: Int) {
        val d = dao.byId(id) ?: return
        val updated = d.copy(raw = d.startValue(), lastResetDate = DateUtil.today(dayStartHour))
        dao.update(updated)
        touchLog(updated, updated.lastResetDate)
    }

    suspend fun resetAll(dayStartHour: Int) {
        val today = DateUtil.today(dayStartHour)
        dao.allOnce().forEach {
            val u = it.copy(raw = it.startValue(), lastResetDate = today)
            dao.update(u)
            touchLog(u, today)
        }
    }

    /** يضيف ثواني إلى زمن اليوم، وإلى زمن الذكر الحالي إن وُجد. */
    suspend fun addSeconds(dayStartHour: Int, delta: Long, dhikrId: Long?) {
        if (delta <= 0) return
        val today = DateUtil.today(dayStartHour)
        val existing = dao.sessionDay(today)
        dao.upsertSessionDay(
            existing?.copy(seconds = existing.seconds + delta)
                ?: SessionDay(date = today, seconds = delta, sessions = 1)
        )
        if (dhikrId != null) dao.addDhikrSeconds(dhikrId, delta)
    }

    /** يسجّل بداية حلقة ذكر جديدة. */
    suspend fun markSessionStart(dayStartHour: Int) {
        val today = DateUtil.today(dayStartHour)
        val existing = dao.sessionDay(today)
        dao.upsertSessionDay(
            existing?.copy(sessions = existing.sessions + 1)
                ?: SessionDay(date = today, seconds = 0, sessions = 1)
        )
    }

    suspend fun add(text: String, target: Int, mode: CountMode, note: String?, source: String? = null): Long {
        val order = dao.nextOrderIndex()
        return dao.insert(
            Dhikr(
                text = text.trim(),
                target = target.coerceAtLeast(1),
                mode = mode,
                raw = if (mode == CountMode.UP) 0 else target,
                orderIndex = order,
                lastResetDate = DateUtil.today(),
                note = note?.takeIf { it.isNotBlank() },
                source = source?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun update(d: Dhikr) = dao.update(d)
    suspend fun delete(d: Dhikr) = dao.delete(d)

    suspend fun move(list: List<Dhikr>, from: Int, to: Int) {
        val mutable = list.toMutableList()
        if (from !in mutable.indices || to !in mutable.indices) return
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        mutable.forEachIndexed { i, d -> if (d.orderIndex != i) dao.update(d.copy(orderIndex = i)) }
    }

    /** الذكر الذي يحتاج المستخدم إكماله الآن — أساس نص الإشعار. */
    suspend fun nextIncomplete(): Dhikr? =
        dao.activeOnce().firstOrNull { !it.isCompleted }

    /** عدد الأيام المتتالية التي اكتمل فيها الورد، انتهاءً بالأمس أو اليوم. */
    suspend fun currentStreak(dayStartHour: Int): Int {
        var streak = 0
        var day = DateUtil.parse(DateUtil.today(dayStartHour))
        // اليوم لا يكسر السلسلة إن لم يكتمل بعد
        val todayLogs = dao.logsFor(DateUtil.format(day))
        if (!fullDay(todayLogs)) day = day.minusDays(1)
        while (true) {
            val logs = dao.logsFor(DateUtil.format(day))
            if (fullDay(logs)) { streak++; day = day.minusDays(1) } else break
        }
        return streak
    }

    private fun fullDay(logs: List<DailyLog>): Boolean =
        logs.isNotEmpty() && logs.all { it.target > 0 && it.done >= it.target }
}
