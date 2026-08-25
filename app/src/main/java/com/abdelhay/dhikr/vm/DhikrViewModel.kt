package com.abdelhay.dhikr.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abdelhay.dhikr.DhikrApp
import com.abdelhay.dhikr.data.*
import com.abdelhay.dhikr.notify.ReminderScheduler
import com.abdelhay.dhikr.util.SessionTimer
import com.abdelhay.dhikr.util.VolumeKeyBus
import com.abdelhay.dhikr.util.DateUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DhikrViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DhikrApp
    private val repo: DhikrRepository = app.repository
    private val store = app.settings

    val settings: StateFlow<Settings> = store.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val adhkar: StateFlow<List<Dhikr>> = repo.allAdhkar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAdhkar: StateFlow<List<Dhikr>> = repo.activeAdhkar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daySummaries: StateFlow<List<DaySummary>> = repo.daySummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lifetimeTotal: StateFlow<Long> = repo.lifetimeTotal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ── المؤقّت ──
    val session = SessionTimer(repo, viewModelScope) { settings.value.dayStartHour }

    val secondsToday: StateFlow<Long> = settings
        .map { it.dayStartHour }
        .distinctUntilChanged()
        .flatMapLatest { repo.observeSecondsToday(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val secondsTotal: StateFlow<Long> = repo.secondsTotal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val secondsLast7Days: StateFlow<Long> = settings
        .map { it.dayStartHour }
        .distinctUntilChanged()
        .flatMapLatest {
            repo.observeSecondsSince(DateUtil.format(DateUtil.parse(DateUtil.today(it)).minusDays(6)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    /** حدث لحظي: اكتمل ذكر — تستعمله الشاشة للاهتزاز والانتقال. */
    private val _completed = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val completed: SharedFlow<Long> = _completed.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            val s = store.flow.first()
            repo.rolloverIfNeeded(s.dayStartHour)
            refreshStreak()
            ReminderScheduler.reschedule(app, s.remindersEnabled, s.reminderTimes)
        }
        // أي تغيير في إعدادات التذكير يعيد بناء الجدول فورًا
        viewModelScope.launch {
            store.flow
                .map { it.remindersEnabled to it.reminderTimes }
                .distinctUntilChanged()
                .drop(1)
                .collect { (enabled, times) -> ReminderScheduler.reschedule(app, enabled, times) }
        }
        viewModelScope.launch {
            store.flow.map { it.volumeKeysCount }.distinctUntilChanged()
                .collect { VolumeKeyBus.enabled = it }
        }
    }

    fun observe(id: Long): Flow<Dhikr?> = repo.observe(id)

    fun onResumeApp() = viewModelScope.launch {
        repo.rolloverIfNeeded(settings.value.dayStartHour)
        refreshStreak()
    }

    fun enterSession(id: Long) = session.enter(id)
    fun leaveSession() = session.leave()

    fun increment(id: Long) = viewModelScope.launch {
        session.onActivity(id)
        val before = repo.observe(id).first()
        val after = repo.increment(id, settings.value.dayStartHour)
        if (after != null && after.isCompleted && before?.isCompleted != true) {
            _completed.tryEmit(id)
            refreshStreak()
        }
    }

    fun undo(id: Long) = viewModelScope.launch { repo.undo(id, settings.value.dayStartHour) }

    fun resetOne(id: Long) = viewModelScope.launch { repo.resetOne(id, settings.value.dayStartHour) }

    fun resetAll() = viewModelScope.launch { repo.resetAll(settings.value.dayStartHour) }

    fun add(text: String, target: Int, mode: CountMode, note: String? = null, source: String? = null) =
        viewModelScope.launch { repo.add(text, target, mode, note, source) }

    fun addPreset(p: Preset) =
        viewModelScope.launch { repo.add(p.text, p.target, CountMode.UP, p.note, p.source) }

    fun save(d: Dhikr) = viewModelScope.launch {
        // إذا غيّر المستخدم الهدف في الوضع التنازلي نضبط العدّاد داخل المدى
        val fixed = if (d.mode == CountMode.DOWN) d.copy(raw = d.raw.coerceIn(0, d.target)) else d
        repo.update(fixed)
    }

    fun toggleActive(d: Dhikr) = viewModelScope.launch { repo.update(d.copy(isActive = !d.isActive)) }

    fun delete(d: Dhikr) = viewModelScope.launch { repo.delete(d) }

    fun move(from: Int, to: Int) = viewModelScope.launch { repo.move(adhkar.value, from, to) }

    // ---------- الإعدادات ----------
    fun setVibrate(v: Boolean) = viewModelScope.launch { store.setVibrate(v) }
    fun setSound(v: Boolean) = viewModelScope.launch { store.setSound(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { store.setKeepScreenOn(v) }
    fun setVolumeKeys(v: Boolean) = viewModelScope.launch { store.setVolumeKeys(v) }
    fun setArabicNumerals(v: Boolean) = viewModelScope.launch { store.setArabicNumerals(v) }
    fun setAutoAdvance(v: Boolean) = viewModelScope.launch { store.setAutoAdvance(v) }
    fun setDark(mode: String) = viewModelScope.launch { store.setDark(mode) }
    fun setStartTab(v: String) = viewModelScope.launch { store.setStartTab(v) }
    fun setLanguage(v: String) = viewModelScope.launch { store.setLanguage(v) }
    fun setReminders(v: Boolean) = viewModelScope.launch { store.setReminders(v) }
    fun setReminderTimes(t: List<String>) = viewModelScope.launch { store.setReminderTimes(t) }
    fun setDayStartHour(h: Int) = viewModelScope.launch { store.setDayStartHour(h) }
    fun setFontScale(f: Float) = viewModelScope.launch { store.setFontScale(f) }

    private suspend fun refreshStreak() {
        _streak.value = repo.currentStreak(settings.value.dayStartHour)
    }
}
