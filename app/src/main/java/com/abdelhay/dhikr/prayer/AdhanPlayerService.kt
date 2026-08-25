package com.abdelhay.dhikr.prayer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.abdelhay.dhikr.MainActivity
import com.abdelhay.dhikr.R

/**
 * تشغيل الأذان كاملًا.
 *
 * صوت الإشعار لا يصلح للأذان: النظام يقصّه بعد ثوانٍ، وقد يخفضه مع صوت الإشعارات.
 * فالأذان يُشغَّل هنا في خدمة أمامية بمسار «المنبّه»، مع إشعار فيه زرّ إيقاف —
 * وهي الطريقة التي تعمل بها تطبيقات الأذان الحقيقية.
 */
class AdhanPlayerService : Service() {

    companion object {
        const val ACTION_PLAY = "com.abdelhay.dhikr.PLAY_ADHAN"
        const val ACTION_STOP = "com.abdelhay.dhikr.STOP_ADHAN"
        const val EXTRA_RAW = "raw_name"
        const val EXTRA_LABEL = "label"
        const val NOTIF_ID = 2003

        fun play(context: Context, rawName: String, label: String) {
            val i = Intent(context, AdhanPlayerService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_RAW, rawName)
                .putExtra(EXTRA_LABEL, label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AdhanPlayerService::class.java).setAction(ACTION_STOP)
            )
        }

        /** هل يُرفع الأذان الآن؟ تستعمله الواجهة لالتقاط أزرار الصوت. */
        @Volatile
        var isPlaying: Boolean = false
            private set
    }

    private val WATCHED = listOf(
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION
    )

    private var player: MediaPlayer? = null
    private var volumeObserver: ContentObserver? = null
    private var session: MediaSession? = null
    private var audio: AudioManager? = null
    private var watchedStreams: Map<Int, Int> = emptyMap()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_PLAY -> {
                val raw = intent.getStringExtra(EXTRA_RAW).orEmpty()
                val label = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.adhan_playing)
                startForegroundCompat(label)
                startPlayback(raw)
            }
            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(label: String) {
        val notification = buildNotification(label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(label: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "prayer")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AdhanPlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AdhanNotifier.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(getString(R.string.adhan_stop_hint))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, getString(R.string.adhan_action_stop), stop)
            .build()
    }

    private fun startPlayback(rawName: String) {
        stopPlayer()
        val id = resources.getIdentifier(rawName, "raw", packageName)
        if (id == 0) { stopEverything(); return }

        isPlaying = true
        captureVolumeKeys()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setOnCompletionListener { stopEverything() }
            setOnErrorListener { _, _, _ -> stopEverything(); true }
        }

        val afd = resources.openRawResourceFd(id) ?: run { stopEverything(); return }
        runCatching {
            afd.use { player?.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player?.prepare()
            player?.start()
        }.onFailure { stopEverything() }
    }

    /**
     * التقاط زرّ الصوت لإيقاف الأذان.
     *
     * المحاولة الأولى راقبت مستوى مسار المنبّه، وكانت خاطئة: الأذان يخرج على مسار
     * المنبّه، بينما زرّ الصوت يتحكّم بمسار الوسائط حين يكون التطبيق في الخلفية —
     * فلا يتغيّر مستوى المنبّه ولا يُستدعى المراقب.
     *
     * الحلّ الصحيح جلسة وسائط بمزوّد صوت «بعيد»: النظام يوجّه ضغطات الصوت إلى
     * [VolumeProvider] بدل تغيير أي مسار، فيصلنا الزرّ نفسه أينما كان التطبيق.
     * وهي الآلية التي تلتقط بها تطبيقات المنبّه زرّ الصوت.
     */
    private fun captureVolumeKeys() {
        val s = MediaSession(this, "adhan")
        session = s

        val provider = object : VolumeProvider(
            VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50
        ) {
            // تصلنا هذه النداءات على خيط آخر، فنعود بالإيقاف إلى الخيط الرئيسي
            override fun onAdjustVolume(direction: Int) = stopOnMain()
            override fun onSetVolumeTo(volume: Int) = stopOnMain()
        }
        s.setPlaybackToRemote(provider)

        s.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .setActions(PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE)
                .build()
        )
        s.setCallback(object : MediaSession.Callback() {
            override fun onStop() = stopOnMain()
            override fun onPause() = stopOnMain()
            // زرّ السمّاعة أو البلوتوث يُسكت الأذان كذلك
            override fun onMediaButtonEvent(intent: Intent): Boolean {
                stopOnMain(); return true
            }
        })
        s.isActive = true

        // احتياط: بعض الأجهزة تُبقي التحكّم بالمسارات. أيّ تغيّر في أيّ مسار يُسكت الأذان.
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio = am
        watchedStreams = WATCHED.associateWith { am.getStreamVolume(it) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val changed = watchedStreams.any { (stream, level) ->
                    am.getStreamVolume(stream) != level
                }
                if (changed) stopOnMain()
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true, observer
            )
            volumeObserver = observer
        }
    }

    private fun stopOnMain() {
        Handler(Looper.getMainLooper()).post { stopEverything() }
    }

    private fun releaseVolumeCapture() {
        volumeObserver?.let { o -> runCatching { contentResolver.unregisterContentObserver(o) } }
        volumeObserver = null
        session?.let { s ->
            runCatching { s.isActive = false }
            runCatching { s.release() }
        }
        session = null
    }

    private fun stopPlayer() {
        isPlaying = false
        releaseVolumeCapture()
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun stopEverything() {
        stopPlayer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }
}

/** مشغّل معاينة قصير داخل شاشة الإعدادات — منفصل عن خدمة الأذان. */
object AdhanPreview {

    private val WATCHED = listOf(
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION
    )

    private var player: MediaPlayer? = null
    private var volumeObserver: ContentObserver? = null
    private var session: MediaSession? = null
    private var audio: AudioManager? = null
    private var watchedStreams: Map<Int, Int> = emptyMap()
    var playingKey: String? = null
        private set

    fun toggle(context: Context, key: String, onStateChanged: () -> Unit) {
        if (playingKey == key) { stop(); onStateChanged(); return }
        stop()
        val id = context.resources.getIdentifier(key, "raw", context.packageName)
        if (id == 0) { onStateChanged(); return }
        player = MediaPlayer.create(context, id)?.apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnCompletionListener { stop(); onStateChanged() }
            start()
        }
        playingKey = if (player != null) key else null
        onStateChanged()
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingKey = null
    }
}
