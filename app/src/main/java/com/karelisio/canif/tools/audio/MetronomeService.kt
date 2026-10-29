package com.karelisio.canif.tools.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karelisio.canif.CanifApp
import com.karelisio.canif.R
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.system.Shortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide entry point of the metronome, usable from the screen, a quick settings tile or a widget.
 * The engine itself lives in [MetronomeService] (foreground, keeps ticking with the screen off).
 *
 * - [config] / [update] / [setBpm]: current settings (persisted, applied live while running).
 * - [start] / [stop] / [toggle]: control playback; [isRunning] reflects the service state.
 */
object MetronomeController {
    private const val KEY = "metronome.last"

    private val _config = MutableStateFlow(MetronomeConfig())
    val config: StateFlow<MetronomeConfig> = _config.asStateFlow()

    private val _running = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _running.asStateFlow()

    /** The live engine while running (beat clock for visuals), null otherwise. */
    @Volatile var engine: MetronomeEngine? = null
        internal set

    private val loadLock = Mutex()
    @Volatile private var loaded = false
    @Volatile private var touched = false
    private var saveJob: Job? = null

    /** Restores the last used settings once per process. */
    suspend fun load() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            val s = runCatching { CanifApp.instance.settings.string(KEY, "").first() }.getOrDefault("")
            if (s.isNotEmpty() && !touched) MetronomeConfig.fromJson(s)?.let { _config.value = it }
            loaded = true
        }
    }

    fun update(transform: (MetronomeConfig) -> MetronomeConfig) {
        touched = true
        _config.update { transform(it).normalized() }
        val app = CanifApp.instance
        saveJob?.cancel()
        saveJob = app.appScope.launch {
            delay(600)
            app.settings.setString(KEY, _config.value.toJson(withOptions = true))
        }
    }

    fun setBpm(bpm: Int) = update { it.copy(bpm = bpm) }

    fun start(context: Context) {
        val ctx = context.applicationContext
        ContextCompat.startForegroundService(ctx, Intent(ctx, MetronomeService::class.java).setAction(MetronomeService.ACTION_START))
    }

    fun stop(context: Context) {
        val s = MetronomeService.instance
        if (s != null) s.stopMetronome()
        else if (_running.value) runCatching {
            val ctx = context.applicationContext
            ctx.startService(Intent(ctx, MetronomeService::class.java).setAction(MetronomeService.ACTION_STOP))
        }
    }

    fun toggle(context: Context) = if (_running.value) stop(context) else start(context)

    internal fun setRunning(v: Boolean) {
        _running.value = v
    }
}

/** Foreground service (mediaPlayback) owning the [MetronomeEngine]; notification with BPM and a Stop action. */
class MetronomeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: MetronomeEngine? = null
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMetronome()
            return START_NOT_STICKY
        }
        instance = this
        ensureChannel()
        val c = MetronomeController.config.value
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification(c),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                MetronomeController.load()
                val e = MetronomeEngine(applicationContext)
                e.apply(MetronomeController.config.value)
                if (!e.start()) {
                    stopMetronome()
                    return@launch
                }
                engine = e
                MetronomeController.engine = e
                MetronomeController.setRunning(true)
                acquireWakeLock()
                MetronomeController.config.collect { cfg ->
                    e.apply(cfg)
                    updateNotification(cfg)
                }
            }
        }
        return START_NOT_STICKY
    }

    fun stopMetronome() {
        release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        job?.cancel()
        job = null
        engine?.stop()
        engine = null
        MetronomeController.engine = null
        MetronomeController.setRunning(false)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        release()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Canif:metronome").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Métronome", "Metronome"), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun text(c: MetronomeConfig) = "${c.bpm} BPM · ${c.beats}/${c.unit}"

    private fun notification(c: MetronomeConfig): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Shortcuts.toolIntent(this, "metronome"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        lastText = text(c)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_metronome)
            .setContentTitle(tr("Métronome", "Metronome"))
            .setContentText(lastText)
            .setContentIntent(open)
            .addAction(R.drawable.ic_notif_stop, tr("Arrêter", "Stop"), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(c: MetronomeConfig) {
        if (text(c) == lastText) return
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notification(c)) }
    }

    companion object {
        const val ACTION_START = "com.karelisio.canif.metronome.START"
        const val ACTION_STOP = "com.karelisio.canif.metronome.STOP"
        private const val CHANNEL = "metronome"
        private const val NOTIF_ID = 4120

        @Volatile var instance: MetronomeService? = null
            private set
    }
}
