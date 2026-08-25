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

/** موضع آية في المصحف بالترقيم المألوف. */
data class AyahRef(val surah: Int, val ayah: Int)

/** حالة التلاوة، تقرأها الواجهة لتظليل الآية الجارية. */
object PlaybackBus {
    /** مفتاح القارئ الجاري — تعرضه الواجهة وتغيّره من مكانها. */
    private val _reciter = MutableStateFlow("")
    val reciter: StateFlow<String> = _reciter.asStateFlow()

    internal fun setReciter(key: String) { _reciter.value = key }

    private val _current = MutableStateFlow<AyahRef?>(null)
    val current: StateFlow<AyahRef?> = _current.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    /** الجولة الحالية من التكرار وعددها الكلي — ٠ في الكلي يعني تكرارًا بلا نهاية. */
    private val _round = MutableStateFlow(0 to 1)
    val round: StateFlow<Pair<Int, Int>> = _round.asStateFlow()

    internal fun set(ref: AyahRef?, playing: Boolean, buffering: Boolean = false) {
        _current.value = ref
        _playing.value = playing
        _buffering.value = buffering
    }

    internal fun setRound(current: Int, total: Int) { _round.value = current to total }
}

/**
 * تلاوة القرآن.
 *
 * قائمة التشغيل مجرّد مدى من الآيات بالترقيم المتصل، فالانتقال من سورة إلى ما بعدها
 * لا يحتاج منطقًا خاصًّا: «اقرأ سورة» مدًى، و«اقرأ المصحف» مدًى أوسع، والفرق رقمان.
 *
 * ويُشغَّل الملف المنزَّل إن وُجد وإلا يُبَثّ من الشبكة — والقارئ لا يرى فرقًا.
 */
class QuranAudioService : Service() {

    companion object {
        const val ACTION_PLAY = "com.abdelhay.dhikr.PLAY_QURAN"
        const val ACTION_TOGGLE = "com.abdelhay.dhikr.TOGGLE_QURAN"
        const val ACTION_NEXT = "com.abdelhay.dhikr.NEXT_AYAH"
        const val ACTION_PREV = "com.abdelhay.dhikr.PREV_AYAH"
        const val ACTION_STOP = "com.abdelhay.dhikr.STOP_QURAN"
        const val ACTION_SET_RECITER = "com.abdelhay.dhikr.SET_RECITER"

        const val EXTRA_SURAH = "surah"
        const val EXTRA_AYAH = "ayah"
        const val EXTRA_END_SURAH = "end_surah"
        const val EXTRA_END_AYAH = "end_ayah"
        const val EXTRA_RECITER = "reciter"
        const val EXTRA_TITLE = "title"
        /** عدد مرّات إعادة المدى؛ ٠ يعني بلا نهاية حتى يوقفه المستخدم. */
        const val EXTRA_REPEAT = "repeat"

        const val CHANNEL = "quran_playback"
        const val NOTIF_ID = 3001

        /** يبدأ التلاوة من آية إلى آية. اتركهما فارغين للتلاوة إلى آخر المصحف. */
        fun play(
            context: Context,
            from: AyahRef,
            to: AyahRef?,
            reciterKey: String,
            title: String,
            repeat: Int = 1
        ) {
            val i = Intent(context, QuranAudioService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_SURAH, from.surah)
                .putExtra(EXTRA_AYAH, from.ayah)
                .putExtra(EXTRA_END_SURAH, to?.surah ?: 114)
                .putExtra(EXTRA_END_AYAH, to?.ayah ?: 6)
                .putExtra(EXTRA_RECITER, reciterKey)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_REPEAT, repeat)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, QuranAudioService::class.java).setAction(action))
        }

        /** تغيير القارئ أثناء التلاوة: تُستأنف الآية نفسها بصوت القارئ الجديد. */
        fun changeReciter(context: Context, reciterKey: String) {
            context.startService(
                Intent(context, QuranAudioService::class.java)
                    .setAction(ACTION_SET_RECITER)
                    .putExtra(EXTRA_RECITER, reciterKey)
            )
        }
    }

    private var player: MediaPlayer? = null
    private var reciter: Reciter = Reciters.all.first()
    private var title: String = "تلاوة"

    private var current: AyahRef? = null
    private var startRef: AyahRef = AyahRef(1, 1)
    private var endRef: AyahRef = AyahRef(114, 6)

    /** ٠ = بلا نهاية. */
    private var repeatTotal: Int = 1
    private var repeatDone: Int = 0

    /** عدد آيات كل سورة — يُملأ عند أول تشغيل من فهرس المصحف. */
    private var counts: IntArray = IntArray(115)

    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        loadCounts()
    }

    private fun loadCounts() {
        runCatching {
            val json = assets.open("quran/index.json").bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                counts[o.getInt("id")] = o.getInt("verses")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                reciter = Reciters.from(intent.getStringExtra(EXTRA_RECITER))
                PlaybackBus.setReciter(reciter.key)
                title = intent.getStringExtra(EXTRA_TITLE) ?: "تلاوة"
                endRef = AyahRef(
                    intent.getIntExtra(EXTRA_END_SURAH, 114),
                    intent.getIntExtra(EXTRA_END_AYAH, 6)
                )
                val from = AyahRef(
                    intent.getIntExtra(EXTRA_SURAH, 1),
                    intent.getIntExtra(EXTRA_AYAH, 1)
                )
                startRef = from
                repeatTotal = intent.getIntExtra(EXTRA_REPEAT, 1).coerceAtLeast(0)
                repeatDone = 0
                PlaybackBus.setRound(1, repeatTotal)
                startForegroundCompat()
                requestFocus()
                playAyah(from)
            }
            ACTION_SET_RECITER -> {
                val key = intent.getStringExtra(EXTRA_RECITER)
                if (key != null && key != reciter.key) {
                    reciter = Reciters.from(key)
                    PlaybackBus.setReciter(reciter.key)
                    // نعيد الآية الجارية من أولها بالصوت الجديد، والمدى والتكرار كما هما
                    current?.let { playAyah(it) } ?: run { updateNotification() }
                }
            }
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> current?.let { next(it)?.let(::playAyah) }
            ACTION_PREV -> current?.let { prev(it)?.let(::playAyah) }
            ACTION_STOP -> stopAll()
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    // ── التنقّل في المصحف ──

    private fun next(ref: AyahRef): AyahRef? {
        val count = counts.getOrElse(ref.surah) { 0 }
        val n = when {
            ref.ayah < count -> AyahRef(ref.surah, ref.ayah + 1)
            ref.surah < 114 -> AyahRef(ref.surah + 1, 1)
            else -> return null
        }
        // لا نتجاوز نهاية المدى المطلوب
        val beyond = n.surah > endRef.surah || (n.surah == endRef.surah && n.ayah > endRef.ayah)
        return if (beyond) null else n
    }

    private fun prev(ref: AyahRef): AyahRef? = when {
        ref.ayah > 1 -> AyahRef(ref.surah, ref.ayah - 1)
        ref.surah > 1 -> AyahRef(ref.surah - 1, counts.getOrElse(ref.surah - 1) { 1 })
        else -> null
    }

    // ── التشغيل ──

    private fun playAyah(ref: AyahRef) {
        current = ref
        PlaybackBus.set(ref, playing = false, buffering = true)
        updateNotification()

        releasePlayer()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnPreparedListener {
                start()
                PlaybackBus.set(ref, playing = true)
                updateNotification()
            }
            setOnCompletionListener {
                val n = next(ref)
                when {
                    n != null -> playAyah(n)
                    // انتهى المدى: نعيده إن بقيت جولات
                    else -> {
                        repeatDone++
                        val more = repeatTotal == 0 || repeatDone < repeatTotal
                        if (more) {
                            PlaybackBus.setRound(repeatDone + 1, repeatTotal)
                            updateNotification()
                            playAyah(startRef)
                        } else stopAll()
                    }
                }
            }
            setOnErrorListener { _, _, _ ->
                // آية تعذّر جلبها لا تُوقف التلاوة كلها
                val n = next(ref)
                if (n != null) playAyah(n) else stopAll()
                true
            }
        }

        runCatching {
            player?.setDataSource(QuranAudio.source(this, reciter, ref.surah, ref.ayah))
            player?.prepareAsync()
        }.onFailure { stopAll() }
    }

    private fun toggle() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            PlaybackBus.set(current, playing = false)
        } else {
            requestFocus()
            p.start()
            PlaybackBus.set(current, playing = true)
        }
        updateNotification()
    }

    // ── تركيز الصوت: نتوقّف إذا شغّل غيرُنا صوتًا ──

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
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
                        AudioManager.AUDIOFOCUS_GAIN -> player?.start()
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

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun stopAll() {
        releasePlayer()
        abandonFocus()
        PlaybackBus.set(null, playing = false)
        PlaybackBus.setRound(0, 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        abandonFocus()
        PlaybackBus.set(null, playing = false)
        super.onDestroy()
    }

    // ── الإشعار ──

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_recitation), NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null); setShowBadge(false) }
        )
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        }
    }

    private fun action(a: String) = PendingIntent.getService(
        this, a.hashCode(),
        Intent(this, QuranAudioService::class.java).setAction(a),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "quran")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ref = current
        val playing = PlaybackBus.playing.value

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(
                buildString {
                    ref?.let { append("الآية ${it.ayah} • ") }
                    append(reciter.name)
                    val (r, total) = PlaybackBus.round.value
                    if (total != 1) {
                        append(" • تكرار ")
                        append(if (total == 0) "$r" else "$r من $total")
                    }
                }
            )
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "السابقة", action(ACTION_PREV))
            .addAction(0, if (playing) "إيقاف مؤقّت" else "متابعة", action(ACTION_TOGGLE))
            .addAction(0, "التالية", action(ACTION_NEXT))
            .addAction(0, "إنهاء", action(ACTION_STOP))
            .build()
    }
}
