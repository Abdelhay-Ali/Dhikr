package com.abdelhay.dhikr.data

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** آية على صفحة المصحف: رموزها بخطّ الصفحة، ومعها موضعها الحقيقي. */
data class QcfAyah(
    val surah: Int,
    val ayah: Int,
    /** كل محرف كلمةٌ مرسومة، وآخر محرف علامةُ رقم الآية. */
    val glyphs: String
)

/**
 * سطر واحد من سطور الصفحة كما طُبع.
 * [startIndex] موضع أول رموزه ضمن رموز الصفحة كلها — به نعرف الآية عند اللمس.
 */
data class QcfLine(
    val glyphs: String,
    /** عرض السطر بوحدات الخط — به يُضبط حجمه فلا يتجاوز عرض الشاشة. */
    val width: Int,
    val startIndex: Int,
    /** إن كانت سورة تبدأ عند هذا السطر، رقمها — فنرسم عنوانها والبسملة قبله. */
    val surahStart: Int
)

/** صفحة من مصحف المدينة. */
data class QcfPage(
    val page: Int,
    val ayahs: List<QcfAyah>,
    val lines: List<QcfLine>,
    /** عرض السطر المعتاد في هذه الصفحة بوحدات الخط. */
    val lineWidth: Int,
    /** أعرض سطر فيها — به نضمن ألّا يفيض سطرٌ عن الشاشة فيُقصّ طرفه. */
    val maxLineWidth: Int,
    val unitsPerEm: Int
) {
    val firstAyah: QcfAyah? get() = ayahs.firstOrNull()
    val lastAyah: QcfAyah? get() = ayahs.lastOrNull()

    /** فهرس بداية كل آية ضمن رموز الصفحة — أساس ترجمة اللمسة إلى آية. */
    private val offsets: IntArray = IntArray(ayahs.size + 1).also { arr ->
        var run = 0
        ayahs.forEachIndexed { i, a -> arr[i] = run; run += a.glyphs.length }
        arr[ayahs.size] = run
    }

    /** الآية التي يقع عندها الرمز رقم [index] من رموز الصفحة. */
    fun ayahAt(index: Int): QcfAyah? {
        for (i in ayahs.indices) {
            if (index >= offsets[i] && index < offsets[i + 1]) return ayahs[i]
        }
        return ayahs.lastOrNull()
    }

    /** مدى الآية داخل سطر بعينه، أو null إن لم تكن فيه — للتظليل. */
    fun rangeOf(line: QcfLine, target: QcfAyah): IntRange? {
        val i = ayahs.indexOfFirst { it.surah == target.surah && it.ayah == target.ayah }
        if (i < 0) return null
        val from = (offsets[i] - line.startIndex).coerceAtLeast(0)
        val to = (offsets[i + 1] - line.startIndex).coerceAtMost(line.glyphs.length)
        return if (from < to) from until to else null
    }
}

/**
 * مصحف المدينة بخطوط الصفحات (QCF).
 *
 * لكل صفحة خطٌّ خاصّ بها، وكل محرف فيه **كلمةٌ مرسومة** لا حروفًا تُركَّب — فلا يقع
 * خطأ تركيب أبدًا، وهو ما عجزت عنه الخطوط الحرفية في مواضع نادرة كالبقرة ٧٢.
 *
 * وسطور الصفحة محسوبة مسبقًا: عروض الكلمات في هذه الخطوط هي عروضها في المطبوع،
 * فمجموع كل سطر ثابت، ومنه استُخرجت مواضع الأسطر الحقيقية بلا تخمين.
 */
class MushafRepository(private val context: Context) {

    private val pageCache = LinkedHashMap<Int, QcfPage>()
    private val fontCache = LinkedHashMap<Int, FontFamily>()

    companion object {
        const val TOTAL_PAGES = 604
        private const val CACHE_LIMIT = 6

        /** عرض السطر القياسي في المصحف بوحدات الخط (وسيط الصفحات الـ٦٠٤). */
        const val REFERENCE_LINE_WIDTH = 29018
    }

    suspend fun page(number: Int): QcfPage = withContext(Dispatchers.IO) {
        val n = number.coerceIn(1, TOTAL_PAGES)
        pageCache[n] ?: parse(n).also {
            if (pageCache.size >= CACHE_LIMIT) pageCache.remove(pageCache.keys.first())
            pageCache[n] = it
        }
    }

    private fun parse(n: Int): QcfPage {
        val o = JSONObject(read("mushaf/p%03d.json".format(n)))

        val arr = o.getJSONArray("a")
        val ayahs = ArrayList<QcfAyah>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            ayahs += QcfAyah(a.getInt("s"), a.getInt("v"), a.getString("g"))
        }

        val blocks = o.getJSONArray("lines")
        val lines = ArrayList<QcfLine>()
        var offset = 0
        for (b in 0 until blocks.length()) {
            val blk = blocks.getJSONObject(b)
            val head = blk.optInt("h", 0)
            val ls = blk.getJSONArray("l")
            for (i in 0 until ls.length()) {
                val pair = ls.getJSONArray(i)
                val g = pair.getString(0)
                lines += QcfLine(g, pair.getInt(1), offset, if (i == 0) head else 0)
                offset += g.length
            }
        }

        val w = o.optInt("w", 1)
        return QcfPage(n, ayahs, lines, w, o.optInt("wmax", w), o.optInt("em", 2048))
    }

    /**
     * خطّ الصفحة، يُحمَّل من `assets` لا من `res/font` — فلا يثقل ملف الموارد
     * بستّمئة مدخل، ويبقى التحميل كسولًا لصفحات القراءة وحدها.
     */
    fun font(page: Int): FontFamily {
        val n = page.coerceIn(1, TOTAL_PAGES)
        return fontCache[n] ?: FontFamily(Font("qcf/p%03d.ttf".format(n), context.assets)).also {
            if (fontCache.size >= CACHE_LIMIT) fontCache.remove(fontCache.keys.first())
            fontCache[n] = it
        }
    }

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
