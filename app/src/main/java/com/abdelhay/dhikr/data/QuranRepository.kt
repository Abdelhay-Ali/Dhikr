package com.abdelhay.dhikr.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SurahInfo(
    val id: Int,
    val name: String,
    val transliteration: String,
    val isMeccan: Boolean,
    val verseCount: Int
) {
    val revelationLabel: String get() = if (isMeccan) "مكية" else "مدنية"
}

data class Surah(
    val id: Int,
    val name: String,
    /** الآيات مرتّبة، الفهرس ٠ هو الآية الأولى. */
    val verses: List<String>
)

/**
 * فهرسة المصحف: بداية كل سورة وكل جزء وكل صفحة بترقيم الآيات المتصل (١..٦٢٣٦).
 * هذا الترقيم المتصل هو ما يجعل تتبّع الختمة ممكنًا بحساب واحد بسيط
 * بدل مقارنة (سورة، آية) في كل خطوة.
 */
data class QuranMeta(
    val total: Int,
    val surahStart: List<Int>,   // ١١٤
    val juzStart: List<Int>,     // ٣٠
    val pageStart: List<Int>     // ٦٠٤
) {
    fun globalOf(surah: Int, ayah: Int): Int =
        surahStart[(surah - 1).coerceIn(0, 113)] + (ayah - 1).coerceAtLeast(0)

    /** يعيد (رقم السورة، رقم الآية) من الترقيم المتصل. */
    fun locationOf(global: Int): Pair<Int, Int> {
        val g = global.coerceIn(1, total)
        var lo = 0; var hi = surahStart.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (surahStart[mid] <= g) lo = mid else hi = mid - 1
        }
        return (lo + 1) to (g - surahStart[lo] + 1)
    }

    fun juzOf(global: Int): Int = boundaryIndex(juzStart, global) + 1
    fun pageOf(global: Int): Int = boundaryIndex(pageStart, global) + 1

    /** أول آية في جزء معيّن (١..٣٠) بالترقيم المتصل. */
    fun juzStartGlobal(juz: Int): Int = juzStart[(juz - 1).coerceIn(0, 29)]

    private fun boundaryIndex(list: List<Int>, global: Int): Int {
        var lo = 0; var hi = list.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (list[mid] <= global) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/** تفسير سورة: تعريف بها، ونصّ لكل آية. */
data class SurahTafsir(
    val id: Int,
    val intro: String,
    /** الفهرس ٠ يقابل الآية الأولى. */
    val verses: List<String>
) {
    fun of(ayah: Int): String = verses.getOrNull(ayah - 1).orEmpty()
}

/** صفحة من المصحف داخل سورة واحدة — وحدة العرض في شاشة القراءة. */
data class MushafPage(
    val page: Int,
    val juz: Int,
    /** (رقم الآية داخل السورة، نصّها) */
    val verses: List<Pair<Int, String>>
)

/**
 * المصحف يعمل **دون إنترنت**. النص مخزّن في `assets/quran/`:
 * ملف فهرس واحد، وملف مستقل لكل سورة — فتحُ سورة يقرأ بضعة كيلوبايتات لا ميغابايت،
 * ولا حاجة إلى قاعدة بيانات ولا إلى خطوة استيراد عند أول تشغيل.
 *
 * المصدر: موسوعة القرآن الكريم (quranenc.com) عبر مجموعة quran-json.
 * ‼️ قابِل النصّ بمصحف المدينة قبل النشر — راجع README.
 */
class QuranRepository(private val context: Context) {

    private var indexCache: List<SurahInfo>? = null
    private var metaCache: QuranMeta? = null
    private val surahCache = LinkedHashMap<Int, Surah>()
    private val tafsirCache = LinkedHashMap<Int, SurahTafsir>()

    suspend fun index(): List<SurahInfo> = withContext(Dispatchers.IO) {
        indexCache ?: run {
            val json = JSONArray(readAsset("quran/index.json"))
            val list = ArrayList<SurahInfo>(114)
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                list += SurahInfo(
                    id = o.getInt("id"),
                    name = o.getString("name"),
                    transliteration = o.optString("translit"),
                    isMeccan = o.optString("type") == "meccan",
                    verseCount = o.getInt("verses")
                )
            }
            indexCache = list
            list
        }
    }

    suspend fun surah(id: Int): Surah = withContext(Dispatchers.IO) {
        surahCache[id] ?: run {
            val o = JSONObject(readAsset("quran/s$id.json"))
            val arr = o.getJSONArray("verses")
            val verses = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) verses += arr.getString(i)
            val s = Surah(o.getInt("id"), o.getString("name"), verses)
            // نحتفظ بآخر ثلاث سور فقط في الذاكرة
            if (surahCache.size >= 3) surahCache.remove(surahCache.keys.first())
            surahCache[id] = s
            s
        }
    }

    suspend fun meta(): QuranMeta = withContext(Dispatchers.IO) {
        metaCache ?: run {
            val o = JSONObject(readAsset("quran/meta.json"))
            fun arr(key: String): List<Int> {
                val a = o.getJSONArray(key)
                return List(a.length()) { a.getInt(it) }
            }
            val m = QuranMeta(o.getInt("total"), arr("surahStart"), arr("juzStart"), arr("pageStart"))
            metaCache = m
            m
        }
    }

    /**
     * يقسّم السورة إلى صفحات المصحف كما في مصحف المدينة (٦٠٤ صفحة).
     * العرض صفحةً صفحة يجعل القارئ يرى موضعه من المصحف الذي يحفظ منه،
     * ويجعل القائمة الكسولة تعرض كتلًا معقولة بدل آية واحدة في كل صفّ.
     */
    suspend fun pagesOf(surahId: Int): List<MushafPage> {
        val s = surah(surahId)
        val m = meta()
        val out = ArrayList<MushafPage>()
        var current: MutableList<Pair<Int, String>> = ArrayList()
        var currentPage = -1
        s.verses.forEachIndexed { i, text ->
            val global = m.globalOf(surahId, i + 1)
            val page = m.pageOf(global)
            if (page != currentPage) {
                if (currentPage != -1) {
                    out += MushafPage(currentPage, m.juzOf(m.globalOf(surahId, i)), current)
                }
                currentPage = page
                current = ArrayList()
            }
            current += (i + 1) to text
        }
        if (current.isNotEmpty()) {
            out += MushafPage(currentPage, m.juzOf(m.globalOf(surahId, s.verses.size)), current)
        }
        return out
    }

    /** بحث بسيط في الفهرس بالاسم العربي أو اللاتيني أو الرقم. */
    suspend fun searchIndex(query: String): List<SurahInfo> {
        val q = query.trim()
        if (q.isEmpty()) return index()
        val normalized = normalize(q)
        return index().filter {
            normalize(it.name).contains(normalized) ||
                it.transliteration.contains(q, ignoreCase = true) ||
                it.id.toString() == q
        }
    }

    /** تجاهل التشكيل والهمزات حتى يجد المستخدم «الاخلاص» و«الإخلاص» سواء. */
    private fun normalize(s: String): String = s
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        .replace("ال", "")
        .trim()

    /**
     * يبني ورد الختمة بين موضعين بالترقيم المتصل، مقسّمًا على صفحات المصحف.
     * الحدّان يُحترمان كما هما، فينتهي الورد حيث ينتهي الجزء تمامًا.
     */
    suspend fun portion(startGlobal: Int, endGlobal: Int): KhatmaPortion =
        withContext(Dispatchers.IO) {
            val m = meta()
            val names = index()
            val start = startGlobal.coerceIn(1, m.total)
            val rawEnd = endGlobal.coerceIn(start, m.total)

            // لا نمدّ النهاية إلى آخر الصفحة: ستة وعشرون جزءًا تبدأ عند رأس صفحة،
            // وأربعة تبدأ في وسطها — فالمدّ يقحم في الورد آياتٍ من الجزء التالي.
            val end = rawEnd

            val firstPage = m.pageOf(start)
            val lastPage = m.pageOf(end)
            val pages = ArrayList<KhatmaPage>(lastPage - firstPage + 1)
            var seenSurah = -1

            for (p in firstPage..lastPage) {
                val pageStart = m.pageStart[p - 1]
                val pageEnd = if (p >= m.pageStart.size) m.total else m.pageStart[p] - 1
                val from = maxOf(pageStart, start)
                val to = minOf(pageEnd, end)
                if (from > to) continue

                val verses = ArrayList<KhatmaVerse>(to - from + 1)
                for (g in from..to) {
                    val (sid, aya) = m.locationOf(g)
                    val text = surah(sid).verses.getOrNull(aya - 1) ?: continue
                    val isNew = sid != seenSurah
                    seenSurah = sid
                    verses += KhatmaVerse(
                        global = g,
                        surah = sid,
                        ayah = aya,
                        text = text,
                        startsSurah = isNew,
                        surahName = names.firstOrNull { it.id == sid }?.name ?: ""
                    )
                }
                if (verses.isNotEmpty()) {
                    pages += KhatmaPage(
                        page = p,
                        juz = m.juzOf(from),
                        startGlobal = from,
                        endGlobal = to,
                        verses = verses
                    )
                }
            }
            KhatmaPortion(start, end, pages)
        }

    /**
     * التفسير الميسّر — مجمع الملك فهد لطباعة المصحف الشريف.
     * ملف لكل سورة في `assets/tafsir/`، يُقرأ عند أول فتح ويُحتفظ بسورتين في الذاكرة.
     */
    suspend fun tafsir(surahId: Int): SurahTafsir = withContext(Dispatchers.IO) {
        tafsirCache[surahId] ?: run {
            val o = JSONObject(readAsset("tafsir/s$surahId.json"))
            val arr = o.getJSONArray("t")
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) list += arr.getString(i)
            val t = SurahTafsir(surahId, o.optString("intro"), list)
            if (tafsirCache.size >= 2) tafsirCache.remove(tafsirCache.keys.first())
            tafsirCache[surahId] = t
            t
        }
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}

// ─────────────────────────── وضع الختمة ───────────────────────────

/** آية داخل ورد الختمة، تحمل موضعها المتصل حتى تُحفظ بلا التباس. */
data class KhatmaVerse(
    val global: Int,
    val surah: Int,
    val ayah: Int,
    val text: String,
    /** أول آية في سورتها ضمن هذا الورد — نرسم عندها اسم السورة والبسملة. */
    val startsSurah: Boolean,
    val surahName: String
)

/** صفحة مصحف داخل ورد الختمة، وقد تعبر حدود السور. */
data class KhatmaPage(
    val page: Int,
    val juz: Int,
    val startGlobal: Int,
    val endGlobal: Int,
    val verses: List<KhatmaVerse>
)

/**
 * ورد الختمة: مقطع متصل من المصحف بحدوده الدقيقة.
 * منفصل تمامًا عن تصفّح السور الحرّ — لا يشتركان في حالة ولا في موضع محفوظ.
 */
data class KhatmaPortion(
    val startGlobal: Int,
    val endGlobal: Int,
    val pages: List<KhatmaPage>
) {
    val ayahCount: Int get() = endGlobal - startGlobal + 1
    val isEmpty: Boolean get() = pages.isEmpty()
}
