package com.abdelhay.dhikr.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * قارئ بملفات السور الكاملة.
 *
 * كثير من القرّاء المعاصرين — وإسلام صبحي منهم — لم تُقطَّع تلاواتهم آيةً آية،
 * فلا تصلح للتظليل ولا للتكرار بالآية. لكن مصاحفهم متاحة سورةً كاملة، وهذا
 * يكفي لمن أراد أن يسمع لا أن يتتبّع.
 *
 * [server] رابط المجلد، والملف فيه باسم رقم السورة بثلاث خانات.
 */
data class SurahReciter(
    val id: String,
    val name: String,
    val server: String
) {
    fun urlOf(surah: Int): String =
        server.trimEnd('/') + "/%03d.mp3".format(surah)
}

/**
 * قائمة قرّاء المصاحف الكاملة، تُجلب من mp3quran وتُحفظ.
 *
 * لم أُضمّن روابط الخوادم في الشيفرة لأنها تتغيّر (server8، server11، …)،
 * وتثبيتُها يعني تعطّل القارئ يوم يُنقل مصحفه إلى خادم آخر.
 */
class SurahReciterRepository(private val context: Context) {

    private val cacheFile: File get() = File(context.filesDir, "surah_reciters.json")

    companion object {
        private const val API = "https://mp3quran.net/api/v3/reciters?language=ar"
    }

    fun cached(): List<SurahReciter> = runCatching {
        if (!cacheFile.exists()) emptyList() else parse(cacheFile.readText())
    }.getOrDefault(emptyList())

    suspend fun refresh(): List<SurahReciter> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val body = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
            val list = parse(body)
            if (list.isNotEmpty()) {
                runCatching { cacheFile.writeText(body) }
                list
            } else cached()
        }.getOrElse { cached() }
    }

    private fun parse(body: String): List<SurahReciter> = runCatching {
        val arr = JSONObject(body).getJSONArray("reciters")
        val out = ArrayList<SurahReciter>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val name = r.optString("name").trim()
            val moshaf = r.optJSONArray("moshaf") ?: continue
            // نأخذ أول مصحف كامل للقارئ — الروايات الأخرى تُربك أكثر مما تفيد
            for (m in 0 until moshaf.length()) {
                val mo = moshaf.getJSONObject(m)
                val server = mo.optString("server").trim()
                if (server.isEmpty()) continue
                val label = mo.optString("name").trim()
                out += SurahReciter(
                    id = "${r.optInt("id")}-${mo.optInt("id")}",
                    name = if (label.isEmpty() || label == name) name else "$name — $label",
                    server = server
                )
                break
            }
        }
        out
    }.getOrDefault(emptyList())

    fun search(list: List<SurahReciter>, query: String): List<SurahReciter> {
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
