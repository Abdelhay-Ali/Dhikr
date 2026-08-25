package com.abdelhay.dhikr.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/** نبضة قصيرة لكل عدّة، ونبضة مركّبة عند إتمام الذكر. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val tone: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 45) }.getOrNull()

    fun tick() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(12, 60))
        } else {
            @Suppress("DEPRECATION") v.vibrate(12)
        }
    }

    /** إشارة مميزة عند بلوغ الهدف — يشعر بها المستخدم دون النظر إلى الشاشة. */
    fun complete() {
        val v = vibrator ?: return
        val pattern = longArrayOf(0, 40, 60, 40, 60, 120)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }

    fun click() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 25)
    }

    fun release() = tone?.release()
}

/** تحويل الأرقام إلى الهندية المستعملة في المصاحف عند اختيار المستخدم ذلك. */
fun Int.toArabicDigits(enabled: Boolean): String {
    val s = this.toString()
    if (!enabled) return s
    val map = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return buildString { s.forEach { c -> append(if (c.isDigit()) map[c - '0'] else c) } }
}

fun Long.toArabicDigits(enabled: Boolean): String {
    val s = this.toString()
    if (!enabled) return s
    val map = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return buildString { s.forEach { c -> append(if (c.isDigit()) map[c - '0'] else c) } }
}

/**
 * العدّ بأزرار الصوت: يتيح الذكر والهاتف في الجيب أو الشاشة مطفأة تقريبًا،
 * وهو أقرب إحساسًا إلى السبحة من الضغط على الزجاج.
 */
object VolumeKeyBus {
    val events = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    var enabled: Boolean = true
    var active: Boolean = false   // مفعّل فقط داخل شاشة العدّاد
}
