package com.abdelhay.dhikr.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class DownloadState(
    val surah: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
    val error: String? = null
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * تنزيل تلاوة سورة للاستماع دون إنترنت.
 *
 * ننزّل آيةً آيةً بالعناوين نفسها التي نبثّ منها، فالملف المنزَّل يحلّ محلّ البثّ
 * تلقائيًا في المرّة القادمة دون أي منطق إضافي.
 *
 * والتنزيل يتخطّى ما هو موجود، فاستئنافه بعد انقطاع لا يعيد ما تمّ.
 */
class RecitationDownloader(private val context: Context) {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    suspend fun downloadSurah(
        reciter: Reciter,
        surah: Int,
        ayahCount: Int
    ) = withContext(Dispatchers.IO) {
        _state.value = DownloadState(surah, 0, ayahCount, running = true)
        var done = 0
        try {
            for (ayah in 1..ayahCount) {
                coroutineContext.ensureActive()
                val target = QuranAudio.localFile(context, reciter, surah, ayah)
                if (!target.exists() || target.length() == 0L) {
                    fetch(QuranAudio.url(reciter, surah, ayah), target)
                }
                done++
                _state.value = DownloadState(surah, done, ayahCount, running = true)
            }
            _state.value = DownloadState(surah, done, ayahCount, running = false)
        } catch (e: Exception) {
            _state.value = DownloadState(
                surah, done, ayahCount, running = false,
                error = "تعذّر إتمام التنزيل. تحقّق من الاتصال ثم أعد المحاولة."
            )
        }
    }

    fun cancel() {
        _state.value = _state.value.copy(running = false)
    }

    private fun fetch(url: String, target: File) {
        val tmp = File(target.absolutePath + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, 16 * 1024) }
            }
            // لا نُسمّي الملف باسمه النهائي إلا بعد اكتماله، فلا يبقى ملف ناقص يُظنّ سليمًا
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        } finally {
            conn.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
