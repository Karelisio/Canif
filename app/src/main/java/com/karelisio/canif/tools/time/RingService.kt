package com.karelisio.canif.tools.time

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karelisio.canif.CanifApp
import com.karelisio.canif.R
import com.karelisio.canif.core.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RingingTimer(val id: Long, val name: String, val since: Long)

/**
 * Foreground service that rings for finished timers: looping alarm sound on the ALARM stream
 * (plays through silent / vibrate mode), looping vibration, partial wake lock, full-screen notification.
 * Several timers can ring together; the service stops when the last one is dismissed.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var toneJob: Job? = null
    private var rampJob: Job? = null
    private var timeoutJob: Job? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var restoreVolume: Int = -1
    private var ringingStarted = false
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            _ringing.collect { list ->
                if (list.isEmpty()) {
                    if (inForeground) shutdown()
                } else if (inForeground) {
                    TimeNotifications.notify(this@RingService, TimeNotifications.ID_RING, buildNotification(list))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val name = intent?.getStringExtra(EXTRA_NAME) ?: tr("Minuteur", "Timer")
        val redelivered = flags and START_FLAG_REDELIVERY != 0
        if (id >= 0 && _ringing.value.none { it.id == id }) {
            _ringing.update { it + RingingTimer(id, name, System.currentTimeMillis()) }
        }
        val list = _ringing.value
        // startForeground must be called right away, whatever happens next.
        TimeNotifications.ensureChannels(this)
        val n = buildNotification(list.ifEmpty { listOf(RingingTimer(id, name, System.currentTimeMillis())) })
        try {
            ServiceCompat.startForeground(
                this, TimeNotifications.ID_RING, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            inForeground = true
        } catch (e: Exception) {
            // Could not go foreground: fall back to the loud notification and give up the service.
            if (id >= 0) TimeNotifications.notify(this, TimeNotifications.backupId(id), TimeNotifications.backupRing(this, id, name))
            _ringing.update { l -> l.filterNot { it.id == id } }
            stopSelf()
            return START_NOT_STICKY
        }
        if (list.isEmpty()) {
            shutdown()
            return START_NOT_STICKY
        }
        // The backup notification of this timer (if any) is no longer needed.
        list.forEach { TimeNotifications.cancel(this, TimeNotifications.backupId(it.id)) }
        if (redelivered) verifyStillDue(id)
        startRinging()
        return START_REDELIVER_INTENT
    }

    /** After a system restart of the service, make sure the timer still is in the finished state. */
    private fun verifyStillDue(id: Long) {
        scope.launch {
            val t = kotlinx.coroutines.withContext(Dispatchers.IO) { CanifApp.instance.db.timers().get(id) }
            if (t == null || t.running || t.remainingMs > 0) dismiss(this@RingService, id)
        }
    }

    private fun buildNotification(list: List<RingingTimer>): Notification {
        val names = list.joinToString(" · ") { it.name }
        val first = list.first()
        val full = TimeNotifications.ringActivity(this)
        return NotificationCompat.Builder(this, TimeNotifications.CH_RING)
            .setSmallIcon(R.drawable.time_ic_notif_timer)
            .setContentTitle(if (list.size > 1) tr("${list.size} minuteurs terminés", "${list.size} timers finished") else tr("Minuteur terminé", "Timer finished"))
            .setContentText(names)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setUsesChronometer(true)
            .setWhen(first.since)
            .setShowWhen(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setDeleteIntent(TimeNotifications.action(this, TimerReceiver.ACTION_STOP))
            .addAction(0, tr("Arrêter", "Stop"), TimeNotifications.action(this, TimerReceiver.ACTION_STOP))
            .addAction(0, tr("+1 min", "+1 min"), TimeNotifications.action(this, TimerReceiver.ACTION_ADD_MINUTE, first.id))
            .build()
    }

    // ------------------------------------------------------------------ sound & vibration

    private fun startRinging() {
        if (ringingStarted) return
        ringingStarted = true
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "canif:timer-ring").apply {
            setReferenceCounted(false)
            acquire(RING_TIMEOUT_MS + 30_000)
        }
        val am = getSystemService(AudioManager::class.java)
        // An alarm at volume 0 would be silent: bring it to half, restored afterwards.
        runCatching {
            val v = am.getStreamVolume(AudioManager.STREAM_ALARM)
            if (v == 0) {
                restoreVolume = v
                am.setStreamVolume(AudioManager.STREAM_ALARM, (am.getStreamMaxVolume(AudioManager.STREAM_ALARM) + 1) / 2, 0)
            }
        }
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(TimeNotifications.alarmAudio)
            .setOnAudioFocusChangeListener { }
            .build()
            .also { runCatching { am.requestAudioFocus(it) } }

        if (!startPlayer()) startToneFallback()
        startVibration()

        // Safety net: stop ringing after a while and leave a "missed" notification.
        timeoutJob = scope.launch {
            delay(RING_TIMEOUT_MS)
            val names = _ringing.value.joinToString(" · ") { it.name }
            _ringing.value.forEach { t -> CanifApp.instance.appScope.launch { TimerManager.stopRinging(t.id) } }
            _ringing.value = emptyList()
            TimeNotifications.missed(this@RingService, names)
        }
    }

    private fun startPlayer(): Boolean {
        val candidates = listOfNotNull<Uri>(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            val ok = runCatching {
                mp.setAudioAttributes(TimeNotifications.alarmAudio)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.setVolume(0.35f, 0.35f)
                mp.prepare()
                mp.start()
            }.isSuccess
            if (ok) {
                player = mp
                // Gentle crescendo up to full volume.
                rampJob = scope.launch {
                    val start = SystemClock.elapsedRealtime()
                    while (isActive) {
                        val p = ((SystemClock.elapsedRealtime() - start) / 15_000f).coerceIn(0f, 1f)
                        val v = 0.35f + 0.65f * p
                        runCatching { player?.setVolume(v, v) }
                        if (p >= 1f) break
                        delay(250)
                    }
                }
                return true
            }
            runCatching { mp.release() }
        }
        return false
    }

    private fun startToneFallback() {
        val tg = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
        tone = tg
        toneJob = scope.launch {
            while (isActive) {
                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
                delay(1500)
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        if (v == null || !v.hasVibrator()) return
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1100), 0)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            else @Suppress("DEPRECATION") v.vibrate(effect, TimeNotifications.alarmAudio)
        }
    }

    private fun stopRinging() {
        rampJob?.cancel()
        toneJob?.cancel()
        timeoutJob?.cancel()
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { tone?.release() }
        tone = null
        runCatching { vibrator?.cancel() }
        val am = getSystemService(AudioManager::class.java)
        focus?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        if (restoreVolume >= 0) runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, restoreVolume, 0) }
        restoreVolume = -1
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        ringingStarted = false
    }

    private fun shutdown() {
        stopRinging()
        inForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_NAME = "name"
        private const val RING_TIMEOUT_MS = 10 * 60_000L

        private val _ringing = MutableStateFlow<List<RingingTimer>>(emptyList())
        val ringing: StateFlow<List<RingingTimer>> = _ringing

        /** Starts ringing for a timer. Returns false if the service could not be started. */
        fun ring(context: Context, id: Long, name: String): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RingService::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name),
            )
            true
        } catch (e: Exception) {
            false
        }

        /** Stops ringing for one timer (the service stops itself when nothing rings anymore). */
        fun dismiss(context: Context, id: Long) {
            _ringing.update { l -> l.filterNot { it.id == id } }
            TimeNotifications.cancel(context, TimeNotifications.backupId(id))
        }
    }
}
