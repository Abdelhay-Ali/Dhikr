package com.abdelhay.dhikr.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** محطة إذاعية تبثّ القرآن على مدار الساعة. */
data class RadioStation(
    val id: String,
    val name: String,
    val url: String
)

/**
 * إذاعات القرآن.
 *
 * القائمة تُجلب من mp3quran.net عند أول فتح ثم تُحفظ، فتبقى محدَّثة إن أُضيفت
 * محطات أو تغيّرت روابطها — وهي تتغيّر بين الحين والحين، ولو ثبّتناها في الشيفرة
 * لتعطّل التطبيق يوم يتغيّر رابط.
 *
 * ومعها قائمة أساسية مضمّنة تعمل بلا إنترنت الأول أو إن تعذّر الجلب.
 */
class RadioRepository(private val context: Context) {

    private val cacheFile: File get() = File(context.filesDir, "radios.json")

    companion object {
        /**
         * مصدران للقائمة: الحديث أولًا، والقديم احتياطًا.
         * الاعتماد على مصدر واحد يعني تعطّل الميزة كلها يوم يتغيّر.
         */
        private val APIS = listOf(
            "https://mp3quran.net/api/v3/radios?language=ar",
            "https://www.mp3quran.net/api/radio/radio_ar.json"
        )

        /** محطات تُقدَّم في الترتيب: إذاعات الدول الرسمية قبل محطات القرّاء. */
        private val PRIORITY = listOf(
            "إذاعة القرآن الكريم", "القرآن الكريم", "السعودية", "القاهرة",
            "مكة", "المدينة", "الحرم"
        )

        /** محطات أساسية معروفة — تظهر فورًا ريثما تُجلب القائمة الكاملة. */
        val bundled = listOf(
            RadioStation("q_saudi", "إذاعة القرآن الكريم — السعودية", "https://Qurango.net/radio/tarateel"),
            RadioStation("q_cairo", "إذاعة القرآن الكريم — القاهرة", "http://live.mp3quran.net:9852/;"),
            RadioStation("q_mixed", "تلاوات خاشعة", "http://live.mp3quran.net:9992/;"),
            RadioStation("q_ruqyah", "الرقية الشرعية", "http://live.mp3quran.net:9936/;")
        )
    }

    /** القائمة المحفوظة إن وُجدت، وإلا الأساسية. */
    fun cached(): List<RadioStation> = runCatching {
        if (!cacheFile.exists()) return@runCatching bundled
        order(parse(cacheFile.readText()).ifEmpty { bundled })
    }.getOrDefault(bundled)

    /** يحدّث القائمة من الشبكة؛ يعيد ما نجح جلبه أو المحفوظ عند الفشل. */
    suspend fun refresh(): List<RadioStation> = withContext(Dispatchers.IO) {
        for (api in APIS) {
            val list = runCatching {
                val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val body = conn.use { it.inputStream.bufferedReader().readText() }
                val parsed = parse(body)
                if (parsed.isNotEmpty()) runCatching { cacheFile.writeText(body) }
                parsed
            }.getOrDefault(emptyList())
            if (list.isNotEmpty()) return@withContext order(list)
        }
        order(cached())
    }

    /** إذاعات القرآن الرسمية أولًا، ثم الباقي على ترتيب المصدر. */
    private fun order(list: List<RadioStation>): List<RadioStation> {
        fun rank(s: RadioStation): Int {
            PRIORITY.forEachIndexed { i, key -> if (s.name.contains(key)) return i }
            return PRIORITY.size
        }
        return list.sortedBy { rank(it) }
    }

    private fun parse(body: String): List<RadioStation> = runCatching {
        val root = JSONObject(body)
        // الصيغة الحديثة radios، والقديمة reads
        val arr = if (root.has("radios")) root.getJSONArray("radios")
        else root.getJSONArray("reads")
        val out = ArrayList<RadioStation>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("url").ifBlank { o.optString("URL") }.trim()
            val name = o.optString("name").trim()
            if (url.isNotEmpty() && name.isNotEmpty()) {
                out += RadioStation(o.optString("id", i.toString()), name, url)
            }
        }
        out
    }.getOrDefault(emptyList())

    /** بحث بالاسم يتجاهل التشكيل والهمزات — القائمة تتجاوز المئة محطة. */
    fun search(list: List<RadioStation>, query: String): List<RadioStation> {
        val q = query.trim()
        if (q.isEmpty()) return list
        val n = normalize(q)
        return list.filter { normalize(it.name).contains(n) }
    }

    private fun normalize(s: String) = s
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        .replace("ال", "")
        .trim()
}

private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
    try { block(this) } finally { disconnect() }
