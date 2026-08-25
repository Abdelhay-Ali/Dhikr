package com.abdelhay.dhikr.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.abdelhay.dhikr.MainActivity
import com.abdelhay.dhikr.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** حالة الإذاعة الجارية، تقرأها الواجهة. */
object RadioBus {
    private val _station = MutableStateFlow<RadioStation?>(null)
    val station: StateFlow<RadioStation?> = _station.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal fun set(s: RadioStation?, playing: Boolean, connecting: Boolean = false, error: String? = null) {
        _station.value = s
        _playing.value = playing
        _connecting.value = connecting
        _error.value = error
    }

    fun clearError() { _error.value = null }
}

/**
 * بثّ إذاعة القرآن.
 *
 * خدمة أمامية تُبقي البثّ يعمل والشاشة مقفلة، مع تركيز صوت يوقفها إذا شغّل غيرُنا
 * صوتًا. ومهلة الاتصال ظاهرة للمستخدم: البثّ قد يتأخّر ثوانيَ، والسكوت بلا خبر
 * يُوهم أن التطبيق تعطّل.
 */
class RadioService : Service() {

    companion object {
        const val ACTION_PLAY = "com.abdelhay.dhikr.RADIO_PLAY"
        const val ACTION_STOP = "com.abdelhay.dhikr.RADIO_STOP"
        const val EXTRA_ID = "id"
        const val EXTRA_NAME = "name"
        const val EXTRA_URL = "url"
        const val CHANNEL = "quran_radio"
        const val NOTIF_ID = 3002

        fun play(context: Context, s: RadioStation) {
            val i = Intent(context, RadioService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_ID, s.id)
                .putExtra(EXTRA_NAME, s.name)
                .putExtra(EXTRA_URL, s.url)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RadioService::class.java).setAction(ACTION_STOP))
        }

        /**
         * تشغيل سورة كاملة لقارئ ملفاته سُوريّة.
         * نستعمل المشغّل نفسه لأن الحالة واحدة: صوت متصل بلا حدود آيات.
         */
        fun playSurah(context: Context, title: String, url: String) {
            play(context, RadioStation("surah:$url", title, url))
        }
    }

    private var player: MediaPlayer? = null
    private var current: RadioStation? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL, getString(R.string.channel_radio),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { setSound(null, null); setShowBadge(false) }
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val s = RadioStation(
                    intent.getStringExtra(EXTRA_ID).orEmpty(),
                    intent.getStringExtra(EXTRA_NAME).orEmpty(),
                    intent.getStringExtra(EXTRA_URL).orEmpty()
                )
                current = s
                startForegroundCompat(s.name)
                RadioBus.set(s, playing = false, connecting = true)
                requestFocus()
                start(s)
            }
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun start(s: RadioStation) {
        release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnPreparedListener {
                it.start()
                RadioBus.set(s, playing = true)
                updateNotification(s.name)
            }
            setOnErrorListener { _, _, _ ->
                RadioBus.set(null, playing = false, error = "تعذّر الاتصال بالمحطة. جرّب محطة أخرى أو تحقّق من الشبكة.")
                stopAll()
                true
            }
        }
        runCatching {
            player?.setDataSource(s.url)
            player?.prepareAsync()
        }.onFailure {
            RadioBus.set(null, playing = false, error = "رابط المحطة غير صالح.")
            stopAll()
        }
    }

    private fun requestFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> stopAll()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> runCatching { player?.pause() }
                        AudioManager.AUDIOFOCUS_GAIN -> runCatching { player?.start() }
                    }
                }
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION") am.abandonAudioFocus(null)
        }
        focusRequest = null
    }

    private fun release() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun stopAll() {
        release()
        abandonFocus()
        RadioBus.set(null, playing = false, error = RadioBus.error.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        release(); abandonFocus()
        RadioBus.set(null, playing = false)
        super.onDestroy()
    }

    private fun startForegroundCompat(name: String) {
        val n = buildNotification(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIF_ID, n)
    }

    private fun updateNotification(name: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(name))
        }
    }

    private fun buildNotification(name: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "radio")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RadioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(getString(R.string.radio_playing))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.adhan_action_stop), stop)
            .build()
    }
}
