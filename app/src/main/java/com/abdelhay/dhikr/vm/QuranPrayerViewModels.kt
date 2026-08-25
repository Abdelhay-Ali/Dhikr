package com.abdelhay.dhikr.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abdelhay.dhikr.DhikrApp
import com.abdelhay.dhikr.data.*
import com.abdelhay.dhikr.prayer.AdhanNotifier
import com.abdelhay.dhikr.prayer.DayPrayers
import com.abdelhay.dhikr.prayer.PrayerCalculator
import com.abdelhay.dhikr.prayer.PrayerSlot
import com.abdelhay.dhikr.util.DateUtil
import com.abdelhay.dhikr.util.LocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

/** حالة الختمة معروضةً للواجهة: أين وصل، وأين يُفترض أن يكون. */
data class KhatmaState(
    val khatma: Khatma,
    val currentSurah: Int,
    val currentAyah: Int,
    val currentSurahName: String,
    /** موضع **الاستئناف**: الآية التالية لما قرأه، لا الآية التي وقف عندها. */
    val resumeSurah: Int,
    val resumeAyah: Int,
    val resumeSurahName: String,
    val currentJuz: Int,
    val dayNumber: Int,          // اليوم الأول = ١
    val expectedProgress: Int,   // ما كان ينبغي بلوغه اليوم
    val todayTargetEnd: Int,     // نهاية ورد اليوم بالترقيم المتصل
    val targetSurah: Int,
    val targetAyah: Int,
    val targetSurahName: String,
    val estimatedDays: Int
) {
    /** موجب = متقدّم على الخطة، سالب = متأخّر (بعدد الآيات). */
    val delta: Int get() = khatma.progress - expectedProgress
    val remainingToday: Int get() = (todayTargetEnd - khatma.progress).coerceAtLeast(0)
    val isTodayDone: Boolean get() = khatma.progress >= todayTargetEnd
}

class QuranViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DhikrApp
    private val repo = app.quran
    private val dao = app.quranDao
    private val store = app.settings

    val settings: StateFlow<Settings> = store.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _index = MutableStateFlow<List<SurahInfo>>(emptyList())
    val index: StateFlow<List<SurahInfo>> = _index.asStateFlow()

    private val _allSurahs = MutableStateFlow<List<SurahInfo>>(emptyList())

    private val _pages = MutableStateFlow<List<MushafPage>>(emptyList())
    val pages: StateFlow<List<MushafPage>> = _pages.asStateFlow()

    // ── صفحات مصحف المدينة ──
    private val mushafRepo = app.mushaf

    private val _qcfPages = MutableStateFlow<List<com.abdelhay.dhikr.data.QcfPage>>(emptyList())
    /** صفحات المصحف المعروضة الآن (سورة أو ورد ختمة). */
    val qcfPages: StateFlow<List<com.abdelhay.dhikr.data.QcfPage>> = _qcfPages.asStateFlow()

    fun fontOf(page: Int) = mushafRepo.font(page)

    fun juzOfPage(page: Int): Int = pageJuz[page] ?: 1
    private var pageJuz: Map<Int, Int> = emptyMap()

    private suspend fun loadPages(from: Int, to: Int) {
        val list = ArrayList<com.abdelhay.dhikr.data.QcfPage>(to - from + 1)
        val juz = HashMap<Int, Int>()
        val m = repo.meta()
        for (p in from..to) {
            val page = mushafRepo.page(p)
            list += page
            page.firstAyah?.let { a -> juz[p] = m.juzOf(m.globalOf(a.surah, a.ayah)) }
        }
        pageJuz = juz
        _qcfPages.value = list
    }

    /** صفحات سورة كاملة. */
    fun openPagesOfSurah(surahId: Int) = viewModelScope.launch {
        _qcfPages.value = emptyList()
        val m = repo.meta()
        val info = _index.value.firstOrNull { it.id == surahId }
            ?: repo.index().first { it.id == surahId }
        val first = m.pageOf(m.globalOf(surahId, 1))
        val last = m.pageOf(m.globalOf(surahId, info.verseCount))
        loadPages(first, last)
    }

    /** صفحات ورد الختمة. */
    fun openPagesOfPortion(startGlobal: Int, endGlobal: Int) = viewModelScope.launch {
        _qcfPages.value = emptyList()
        val m = repo.meta()
        loadPages(m.pageOf(startGlobal), m.pageOf(endGlobal))
    }

    private val _openSurah = MutableStateFlow<Surah?>(null)
    val openSurah: StateFlow<Surah?> = _openSurah.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> = dao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _khatmaState = MutableStateFlow<KhatmaState?>(null)
    val khatmaState: StateFlow<KhatmaState?> = _khatmaState.asStateFlow()

    private var meta: QuranMeta? = null

    init {
        viewModelScope.launch {
            meta = repo.meta()
            val list = repo.index()
            _allSurahs.value = list
            _index.value = list
            dao.observeActiveKhatma().collect { rebuildKhatma(it) }
        }
    }

    // ── الفهرس والقراءة ──

    fun search(q: String) {
        _query.value = q
        viewModelScope.launch { _index.value = repo.searchIndex(q) }
    }

    fun open(surahId: Int) = viewModelScope.launch {
        _openSurah.value = null
        _pages.value = emptyList()
        _openSurah.value = repo.surah(surahId)
        _pages.value = repo.pagesOf(surahId)
        openPagesOfSurah(surahId)
    }

    fun rememberPosition(surahId: Int, verseIndex: Int) =
        viewModelScope.launch { store.setLastRead(surahId, verseIndex) }

    fun setFontScale(f: Float) = viewModelScope.launch { store.setQuranFontScale(f) }
    fun setQuranFont(key: String) = viewModelScope.launch { store.setQuranFont(key) }
    fun setMushafLandscape(v: Boolean) = viewModelScope.launch { store.setMushafLandscape(v) }

    fun setReciter(key: String) = viewModelScope.launch {
        store.setReciter(key)
        // إن كانت التلاوة جارية تُستأنف الآية نفسها بالصوت الجديد
        com.abdelhay.dhikr.audio.QuranAudioService.changeReciter(getApplication<Application>(), key)
    }

    // ── التلاوة ──

    val downloader = com.abdelhay.dhikr.audio.RecitationDownloader(application)
    private var downloadJob: kotlinx.coroutines.Job? = null

    /**
     * تشغيل مدًى من الآيات مع تكراره.
     *
     * المدى الواحد يغطّي كل الحالات: آية واحدة تُكرَّر، صفحة تُكرَّر، سورة تُتلى مرّة،
     * أو المصحف من موضعك. والفرق بينها رقمان وعدد جولات.
     */
    fun playRange(
        from: com.abdelhay.dhikr.audio.AyahRef,
        to: com.abdelhay.dhikr.audio.AyahRef?,
        title: String,
        repeat: Int = 1
    ) = com.abdelhay.dhikr.audio.QuranAudioService.play(
        getApplication<Application>(), from, to, settings.value.reciter, title, repeat
    )

    /** من آية إلى آخر السورة، أو إلى آخر المصحف. */
    fun playFrom(surah: Int, ayah: Int, toEndOfQuran: Boolean = false) {
        val info = _index.value.firstOrNull { it.id == surah }
        playRange(
            com.abdelhay.dhikr.audio.AyahRef(surah, ayah),
            if (toEndOfQuran) null
            else com.abdelhay.dhikr.audio.AyahRef(surah, info?.verseCount ?: ayah),
            if (toEndOfQuran) "تلاوة المصحف" else "سورة ${info?.name.orEmpty()}"
        )
    }

    /** تكرار آية واحدة. [times] صفرًا يعني بلا نهاية. */
    fun repeatAyah(surah: Int, ayah: Int, times: Int) {
        val name = _index.value.firstOrNull { it.id == surah }?.name.orEmpty()
        playRange(
            com.abdelhay.dhikr.audio.AyahRef(surah, ayah),
            com.abdelhay.dhikr.audio.AyahRef(surah, ayah),
            "تكرار آية — $name",
            times
        )
    }

    /** تكرار مدًى (صفحة أو ورد). */
    fun repeatRange(
        fromSurah: Int, fromAyah: Int,
        toSurah: Int, toAyah: Int,
        title: String, times: Int
    ) = playRange(
        com.abdelhay.dhikr.audio.AyahRef(fromSurah, fromAyah),
        com.abdelhay.dhikr.audio.AyahRef(toSurah, toAyah),
        title, times
    )

    fun stopPlayback() = com.abdelhay.dhikr.audio.QuranAudioService.send(
        getApplication<Application>(), com.abdelhay.dhikr.audio.QuranAudioService.ACTION_STOP
    )

    fun togglePlayback() = com.abdelhay.dhikr.audio.QuranAudioService.send(
        getApplication<Application>(), com.abdelhay.dhikr.audio.QuranAudioService.ACTION_TOGGLE
    )

    fun downloadSurah(surah: Int) {
        val info = _index.value.firstOrNull { it.id == surah } ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloader.downloadSurah(
                com.abdelhay.dhikr.audio.Reciters.from(settings.value.reciter),
                surah,
                info.verseCount
            )
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloader.cancel()
    }

    // ── التفسير ──

    private val _tafsir = MutableStateFlow<Triple<Int, Int, String>?>(null)
    /** (رقم السورة، رقم الآية، نصّ التفسير) — null يعني أن اللوحة مغلقة. */
    val tafsir: StateFlow<Triple<Int, Int, String>?> = _tafsir.asStateFlow()

    fun showTafsir(surah: Int, ayah: Int) = viewModelScope.launch {
        _tafsir.value = Triple(surah, ayah, repo.tafsir(surah).of(ayah))
    }

    fun hideTafsir() { _tafsir.value = null }

    // ── الفواصل ──

    fun addBookmark(surah: Int, ayah: Int, note: String? = null) = viewModelScope.launch {
        val name = _allSurahs.value.firstOrNull { it.id == surah }?.name ?: ""
        dao.addBookmark(Bookmark(surah = surah, ayah = ayah, surahName = name, note = note))
    }

    fun removeBookmark(b: Bookmark) = viewModelScope.launch { dao.deleteBookmark(b) }

    // ── الختمة ──

    fun startKhatma(juzPerDay: Double) = viewModelScope.launch {
        dao.deactivateAll()
        dao.insertKhatma(
            Khatma(startDate = DateUtil.today(), juzPerDay = juzPerDay, progress = 0)
        )
    }

    fun setJuzPerDay(v: Double) = viewModelScope.launch {
        dao.activeKhatma()?.let { dao.updateKhatma(it.copy(juzPerDay = v.coerceIn(0.25, 10.0))) }
    }

    // ── وضع الختمة: منفصل تمامًا عن تصفّح السور ──

    private val _portion = MutableStateFlow<KhatmaPortion?>(null)
    val portion: StateFlow<KhatmaPortion?> = _portion.asStateFlow()

    private val _portionDone = MutableStateFlow(false)
    /** صار الورد مقروءًا كلّه — الشاشة تعرض شاشة الختام وتخرج. */
    val portionDone: StateFlow<Boolean> = _portionDone.asStateFlow()

    /**
     * يفتح ورد اليوم فقط: من الآية التالية لآخر ما قرأ، إلى مقدار الأجزاء المحدّدة.
     * لا يفتح المصحف كلّه — القارئ يرى ما عليه اليوم لا أكثر.
     */
    fun loadTodayPortion() = viewModelScope.launch {
        val m = meta ?: return@launch
        val k = dao.activeKhatma() ?: return@launch
        _portionDone.value = false
        _portion.value = null
        val start = (k.progress + 1).coerceAtMost(m.total)
        // النهاية عند حدّ الجزء الحقيقي: من وقف عند رأس جزء يقرأ الجزء كاملًا
        val end = globalAtJuz(m, juzFractionOf(m, k.progress) + k.juzPerDay)
            .coerceIn(start, m.total)
        _portion.value = repo.portion(start, end)
    }

    /**
     * يحفظ موضع القارئ داخل الورد.
     * [pageStartGlobal] أول آية في الصفحة المعروضة الآن، فنحفظ ما قبلها مقروءًا —
     * وبذلك تُفتح في المرّة القادمة **هذه الصفحة نفسها** لا التي بعدها.
     */
    /** يحفظ الموضع بأول آية في الصفحة المعروضة — الختمة تُستأنف من هذه الصفحة نفسها. */
    fun saveKhatmaPageAt(surah: Int, ayah: Int) = viewModelScope.launch {
        val m = repo.meta()
        saveProgress(m.globalOf(surah, ayah))
    }

    private suspend fun saveProgress(pageStartGlobal: Int) {
        val k = dao.activeKhatma() ?: return
        val newProgress = (pageStartGlobal - 1).coerceAtLeast(0)
        if (newProgress <= k.progress) return
        dao.updateKhatma(k.copy(progress = newProgress, lastReadDate = DateUtil.today()))
    }

    fun saveKhatmaPage(pageStartGlobal: Int) = viewModelScope.launch {
        val k = dao.activeKhatma() ?: return@launch
        val newProgress = (pageStartGlobal - 1).coerceAtLeast(0)
        if (newProgress <= k.progress) return@launch
        dao.updateKhatma(k.copy(progress = newProgress, lastReadDate = DateUtil.today()))
    }

    /** يُستدعى عند بلوغ آخر الورد: يُثبَّت التقدّم وتُغلق الجلسة. */
    fun finishPortion() = viewModelScope.launch {
        val m = meta ?: return@launch
        val k = dao.activeKhatma() ?: return@launch
        val p = _portion.value ?: return@launch
        val done = p.endGlobal >= m.total
        dao.updateKhatma(
            k.copy(
                progress = p.endGlobal,
                lastReadDate = DateUtil.today(),
                completedAt = if (done) System.currentTimeMillis() else null,
                isActive = !done
            )
        )
        _portionDone.value = true
    }

    fun clearPortion() {
        _portion.value = null
        _portionDone.value = false
    }

    fun endKhatma() = viewModelScope.launch { dao.deactivateAll() }

    private suspend fun rebuildKhatma(k: Khatma?) {
        val m = meta
        if (k == null || m == null) { _khatmaState.value = null; return }

        val names = _allSurahs.value
        val progress = k.progress.coerceIn(0, m.total)
        val (cs, ca) = if (progress == 0) 1 to 1 else m.locationOf(progress)

        // الاستئناف من الآية التي تلي آخر ما قرأ. بدون هذا السطر تُعيده الختمة
        // أبدًا إلى السورة التي أتمّها للتوّ.
        val resumeGlobal = (progress + 1).coerceAtMost(m.total)
        val (rs, ra) = m.locationOf(resumeGlobal)

        val day = daysSince(k.startDate) + 1
        val expected = globalAtJuz(m, day * k.juzPerDay)
        val todayEnd = globalAtJuz(m, juzFractionOf(m, progress) + k.juzPerDay)
            .coerceIn(progress, m.total)
        val (ts, ta) = m.locationOf(todayEnd.coerceAtLeast(1))

        _khatmaState.value = KhatmaState(
            khatma = k,
            currentSurah = cs,
            currentAyah = ca,
            currentSurahName = names.firstOrNull { it.id == cs }?.name ?: "",
            resumeSurah = rs,
            resumeAyah = ra,
            resumeSurahName = names.firstOrNull { it.id == rs }?.name ?: "",
            currentJuz = m.juzOf(progress.coerceAtLeast(1)),
            dayNumber = day,
            expectedProgress = expected,
            todayTargetEnd = todayEnd,
            targetSurah = ts,
            targetAyah = ta,
            targetSurahName = names.firstOrNull { it.id == ts }?.name ?: "",
            estimatedDays = ceil(30.0 / k.juzPerDay).toInt()
        )
    }

    /**
     * موضع نهاية [juz] جزءًا بالترقيم المتصل، ويقبل الكسور:
     * ١٫٥ جزء = الجزء الأول كاملًا ونصف الثاني.
     */
    /** طول الجزء [juz] (١..٣٠) بالآيات — يتراوح بين ١١٠ و٥٦٤، فلا يصلح متوسّط. */
    private fun juzLength(m: QuranMeta, juz: Int): Int {
        val j = juz.coerceIn(1, 30)
        val start = m.juzStartGlobal(j)
        val end = if (j >= 30) m.total else m.juzStartGlobal(j + 1) - 1
        return (end - start + 1).coerceAtLeast(1)
    }

    /**
     * موضع القارئ بوحدة الأجزاء (٠..٣٠) بالكسر.
     * الكسر يُحسب داخل الجزء الحقيقي الذي هو فيه، لا بقسمة عدد الآيات على ثلاثين —
     * فجزء عمّ ٥٦٤ آية وجزء تبارك ١١٠، والقسمة الساذجة تُخرج الورد عن حدود الأجزاء.
     */
    private fun juzFractionOf(m: QuranMeta, global: Int): Double {
        if (global <= 0) return 0.0
        if (global >= m.total) return 30.0
        val j = m.juzOf(global)
        val start = m.juzStartGlobal(j)
        return (j - 1) + (global - start + 1).toDouble() / juzLength(m, j)
    }

    /** يعيد الموضع المتصل عند كسرٍ من الأجزاء — عكس [juzFractionOf]. */
    private fun globalAtJuz(m: QuranMeta, juz: Double): Int {
        if (juz <= 0.0) return 0
        if (juz >= 30.0) return m.total
        val whole = floor(juz).toInt()
        val within = juz - whole
        val j = (whole + 1).coerceIn(1, 30)
        val start = m.juzStartGlobal(j)
        if (within <= 0.0) return (start - 1).coerceIn(0, m.total)
        val len = juzLength(m, j)
        return (start - 1 + ceil(within * len).toInt()).coerceIn(0, m.total)
    }


    private fun daysSince(startDate: String): Int = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.now()).toInt()
    }.getOrDefault(0).coerceAtLeast(0)
}

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DhikrApp
    private val store = app.settings

    val settings: StateFlow<Settings> = store.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _today = MutableStateFlow<DayPrayers?>(null)
    val today: StateFlow<DayPrayers?> = _today.asStateFlow()

    private val _next = MutableStateFlow<PrayerSlot?>(null)
    val next: StateFlow<PrayerSlot?> = _next.asStateFlow()

    private val _countdown = MutableStateFlow(0L)
    val countdown: StateFlow<Long> = _countdown.asStateFlow()

    /**
     * الصلاة التي أُقيمت للتوّ والثواني التي مضت عليها.
     *
     * يبقى الأذان معروضًا نصف ساعة بعد وقته — وهي مدّة الصلاة وأذكارها — ثم
     * تعود البطاقة إلى العدّ للصلاة القادمة. فمن نظر إلى هاتفه بعد الأذان بدقائق
     * وجد ما يعنيه: كم مضى، لا كم بقي لصلاة لم يحن وقتها.
     */
    /** المدّة التي يظلّ فيها الأذان معروضًا بعد وقته. */
    private val sinceWindowSec = 30L * 60L

    private val _since = MutableStateFlow<Pair<PrayerSlot, Long>?>(null)
    val since: StateFlow<Pair<PrayerSlot, Long>?> = _since.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { s -> if (s.hasLocation) recompute(s) }
        }
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()

                _next.value?.let { n ->
                    val remaining = (n.timeMillis - now) / 1000
                    if (remaining <= 0) recompute(settings.value) else _countdown.value = remaining
                }

                // آخر صلاة مضى وقتها اليوم — الشروق ليس صلاة فلا يُعرض
                val last = _today.value?.slots
                    ?.lastOrNull { it.name.isPrayer && it.timeMillis <= now }
                val elapsed = last?.let { (now - it.timeMillis) / 1000 }
                _since.value = if (last != null && elapsed != null && elapsed < sinceWindowSec)
                    last to elapsed else null

                delay(1000)
            }
        }
    }

    private fun recompute(s: Settings) {
        if (!s.hasLocation) return
        val method = PrayerCalculator.parseMethod(s.calculationMethod)
        val madhab = PrayerCalculator.parseMadhab(s.madhab)
        _today.value = PrayerCalculator.compute(s.latitude, s.longitude, method, madhab)
        _next.value = PrayerCalculator.nextSlot(s.latitude, s.longitude, method, madhab)
        AdhanNotifier.rescheduleAll(getApplication<Application>(), s)
    }

    fun detectLocation(onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        _locating.value = true
        val place = LocationHelper.lastKnown(getApplication<Application>())
        _locating.value = false
        if (place == null) { onResult(false); return@launch }
        store.setLocation(place.latitude, place.longitude, place.name ?: "")
        onResult(true)
    }

    fun setManualLocation(lat: Double, lng: Double, name: String) =
        viewModelScope.launch { store.setLocation(lat, lng, name) }

    fun setMethod(m: String) = viewModelScope.launch { store.setMethod(m) }
    fun setMadhab(m: String) = viewModelScope.launch { store.setMadhab(m) }
    fun setAfterPrayerAdhkar(v: Boolean) =
        viewModelScope.launch { store.setAfterPrayerAdhkar(v) }
    fun setHijriOffset(v: Int) = viewModelScope.launch { store.setHijriOffset(v) }
    fun setPrayerNotifications(v: Boolean) =
        viewModelScope.launch { store.setPrayerNotifications(v) }
    fun setAdhanSound(uri: String) = viewModelScope.launch { store.setAdhanSound(uri) }
    fun setAdhanSoundFajr(uri: String) = viewModelScope.launch { store.setAdhanSoundFajr(uri) }
    fun setPreAdhan(enabled: Boolean, minutes: Int) =
        viewModelScope.launch { store.setPreAdhan(enabled, minutes) }
    fun setUse12Hour(v: Boolean) = viewModelScope.launch { store.setUse12Hour(v) }
}

/** حالة الأوراد الجاهزة (الصباح والمساء والأدعية). */
class AdhkarSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DhikrApp
    private val store = app.settings
    private val sessions = com.abdelhay.dhikr.data.AdhkarSessionStore(application)

    val settings: StateFlow<Settings> = store.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _counts = MutableStateFlow<List<Int>>(emptyList())
    val counts: StateFlow<List<Int>> = _counts.asStateFlow()

    private var current: com.abdelhay.dhikr.data.AdhkarSet? = null

    fun load(set: com.abdelhay.dhikr.data.AdhkarSet) {
        if (current == set) return
        current = set
        viewModelScope.launch {
            sessions.observe(set, settings.value.dayStartHour).collect { stored ->
                val size = set.presets().size
                _counts.value = List(size) { stored.getOrNull(it) ?: 0 }
            }
        }
    }

    fun increment(set: com.abdelhay.dhikr.data.AdhkarSet, index: Int, size: Int, target: Int) =
        viewModelScope.launch {
            val list = MutableList(size) { _counts.value.getOrNull(it) ?: 0 }
            if (index in list.indices) {
                list[index] = (list[index] + 1).coerceAtMost(target)
                _counts.value = list
                sessions.setCounts(set, list, settings.value.dayStartHour)
            }
        }

    fun reset(set: com.abdelhay.dhikr.data.AdhkarSet) = viewModelScope.launch {
        _counts.value = List(set.presets().size) { 0 }
        sessions.reset(set, settings.value.dayStartHour)
    }
}

/** إذاعات القرآن. */
class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = com.abdelhay.dhikr.audio.RadioRepository(application)

    private val _all = MutableStateFlow(repo.cached())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val stations: StateFlow<List<com.abdelhay.dhikr.audio.RadioStation>> =
        combine(_all, _query) { list, q -> repo.search(list, q) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, repo.cached())

    init { refresh() }

    fun search(q: String) { _query.value = q }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        _all.value = repo.refresh()
        _loading.value = false
    }
}

/** قرّاء المصاحف الكاملة (ملف لكل سورة). */
class SurahReciterViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = com.abdelhay.dhikr.audio.SurahReciterRepository(application)

    private val _all = MutableStateFlow(repo.cached())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val reciters: StateFlow<List<com.abdelhay.dhikr.audio.SurahReciter>> =
        combine(_all, _query) { list, q -> repo.search(list, q) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, repo.cached())

    init { refresh() }

    fun search(q: String) { _query.value = q }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        _all.value = repo.refresh()
        _loading.value = false
    }
}
