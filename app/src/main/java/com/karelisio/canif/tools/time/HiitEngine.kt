package com.karelisio.canif.tools.time

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karelisio.canif.CanifApp
import com.karelisio.canif.R
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.json.JSONObject

@Immutable
data class HiitConfig(
    val prepS: Int = 10,
    val workS: Int = 20,
    val restS: Int = 10,
    val rounds: Int = 8,
    val sets: Int = 1,
    val setRestS: Int = 60,
) {
    fun toJson(): String = JSONObject()
        .put("prep", prepS).put("work", workS).put("rest", restS)
        .put("rounds", rounds).put("sets", sets).put("setRest", setRestS)
        .toString()

    val totalMs: Long get() = phases().sumOf { it.durationMs }

    fun phases(): List<HiitPhase> {
        val list = ArrayList<HiitPhase>()
        if (prepS > 0) list += HiitPhase(PhaseKind.PREP, prepS * 1000L, 0, 1)
        for (s in 1..sets.coerceAtLeast(1)) {
            for (r in 1..rounds.coerceAtLeast(1)) {
                list += HiitPhase(PhaseKind.WORK, workS.coerceAtLeast(1) * 1000L, r, s)
                val lastRound = r == rounds
                if (!lastRound && restS > 0) list += HiitPhase(PhaseKind.REST, restS * 1000L, r, s)
            }
            if (s < sets && setRestS > 0) list += HiitPhase(PhaseKind.SET_REST, setRestS * 1000L, rounds, s)
        }
        return list
    }

    companion object {
        fun fromJson(s: String): HiitConfig? = runCatching {
            val o = JSONObject(s)
            HiitConfig(
                o.optInt("prep", 10), o.optInt("work", 20), o.optInt("rest", 10),
                o.optInt("rounds", 8), o.optInt("sets", 1), o.optInt("setRest", 60),
            )
        }.getOrNull()
    }
}

enum class PhaseKind(val label: Txt) {
    PREP(Txt("Préparation", "Get ready")),
    WORK(Txt("Travail", "Work")),
    REST(Txt("Repos", "Rest")),
    SET_REST(Txt("Repos long", "Long rest")),
    DONE(Txt("Terminé", "Done")),
}

@Immutable
data class HiitPhase(val kind: PhaseKind, val durationMs: Long, val round: Int, val set: Int)

@Immutable
data class HiitState(
    val config: HiitConfig,
    val phases: List<HiitPhase>,
    val index: Int,
    val paused: Boolean,
    /** elapsedRealtime at which the current phase ends (when running). */
    val phaseEnd: Long,
    /** Remaining time of the current phase while paused. */
    val pausedRemaining: Long,
    val finished: Boolean = false,
) {
    val phase: HiitPhase get() = phases.getOrElse(index) { HiitPhase(PhaseKind.DONE, 0, config.rounds, config.sets) }
    fun remaining(now: Long = SystemClock.elapsedRealtime()): Long =
        if (finished) 0 else if (paused) pausedRemaining else (phaseEnd - now).coerceAtLeast(0)

    /** 0..1 over the whole session. */
    fun overall(now: Long = SystemClock.elapsedRealtime()): Float {
        val total = phases.sumOf { it.durationMs }.coerceAtLeast(1)
        if (finished) return 1f
        val done = phases.take(index).sumOf { it.durationMs } + (phase.durationMs - remaining(now))
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }
}

data class HiitCues(val voice: Boolean = true, val beeps: Boolean = true, val vibration: Boolean = true)

/**
 * Interval training engine. Lives in the process (not in the UI) and is kept alive in the
 * background by [HiitService]. Times are based on elapsedRealtime, so pauses and screen-off are exact.
 */
object HiitEngine {
    private val _state = MutableStateFlow<HiitState?>(null)
    val state: StateFlow<HiitState?> = _state
    var cues = HiitCues()

    private var job: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var tone: ToneGenerator? = null

    private val ctx: Context get() = CanifApp.instance

    fun start(context: Context, config: HiitConfig) {
        stopInternal()
        val phases = config.phases()
        if (phases.isEmpty()) return
        initAudio()
        _state.value = HiitState(config, phases, 0, false, SystemClock.elapsedRealtime() + phases[0].durationMs, 0)
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, HiitService::class.java))
        }
        announcePhase(phases[0])
        job = CanifApp.instance.appScope.launch { loop() }
    }

    fun togglePause() {
        val s = _state.value ?: return
        if (s.finished) return
        val now = SystemClock.elapsedRealtime()
        _state.value = if (s.paused) s.copy(paused = false, phaseEnd = now + s.pausedRemaining)
        else s.copy(paused = true, pausedRemaining = s.remaining(now))
    }

    fun skip() {
        val s = _state.value ?: return
        if (s.finished) return
        advance(s, SystemClock.elapsedRealtime(), skipping = true)
    }

    fun stop() = stopInternal()

    private fun stopInternal() {
        job?.cancel()
        job = null
        _state.value = null
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ttsReady = false
        pendingSpeech = null
        runCatching { tone?.release() }
        tone = null
    }

    private suspend fun loop() {
        var lastSecond = -1L
        var lastIndex = -1
        while (true) {
            val s = _state.value ?: return
            if (s.finished) return
            if (s.paused) {
                delay(50)
                continue
            }
            val now = SystemClock.elapsedRealtime()
            if (s.index != lastIndex) {
                lastIndex = s.index
                lastSecond = -1
            }
            val rem = s.phaseEnd - now
            if (rem <= 0) {
                advance(s, now, skipping = false)
                continue
            }
            val sec = (rem + 999) / 1000
            if (sec != lastSecond) {
                if (lastSecond != -1L && sec in 1..3 && s.phase.durationMs >= 4000) countdown(sec.toInt())
                lastSecond = sec
            }
            delay((rem % 1000).coerceIn(10, 50))
        }
    }

    private fun advance(s: HiitState, now: Long, skipping: Boolean) {
        val next = s.index + 1
        if (next >= s.phases.size) {
            _state.value = s.copy(index = s.phases.size, finished = true, paused = false)
            say(tr("Terminé", "Done"))
            beep(ToneGenerator.TONE_CDMA_CONFIRM, 900)
            vibrate(longArrayOf(0, 400, 150, 400, 150, 700))
            job?.cancel()
            return
        }
        // Keep the schedule exact: the next phase starts where the previous one should have ended.
        val base = if (skipping || s.phaseEnd < now - 1000) now else s.phaseEnd
        val p = s.phases[next]
        _state.value = s.copy(index = next, paused = false, phaseEnd = base + p.durationMs)
        announcePhase(p)
    }

    private fun announcePhase(p: HiitPhase) {
        when (p.kind) {
            PhaseKind.WORK -> {
                say(tr("Travail", "Work"))
                beep(ToneGenerator.TONE_PROP_BEEP2, 450)
                vibrate(longArrayOf(0, 250, 120, 250))
            }
            PhaseKind.REST, PhaseKind.SET_REST -> {
                say(if (p.kind == PhaseKind.REST) tr("Repos", "Rest") else tr("Repos long", "Long rest"))
                beep(ToneGenerator.TONE_PROP_ACK, 350)
                vibrate(longArrayOf(0, 450))
            }
            PhaseKind.PREP -> say(tr("Préparez-vous", "Get ready"))
            PhaseKind.DONE -> Unit
        }
    }

    private fun countdown(n: Int) {
        if (cues.voice && ttsReady) say(n.toString()) else beep(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrate(longArrayOf(0, 40))
    }

    // ------------------------------------------------------------------ audio

    private fun initAudio() {
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
        if (!cues.voice) return
        val locale = Lang.locale
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val r = t.setLanguage(locale)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(java.util.Locale.getDefault())
                t.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                t.setSpeechRate(1.1f)
                ttsReady = true
                pendingSpeech?.let { p -> t.speak(p, TextToSpeech.QUEUE_FLUSH, null, "hiit-first") }
                pendingSpeech = null
            }
        }
    }

    private fun say(text: String) {
        if (!cues.voice) return
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hiit-${SystemClock.elapsedRealtime()}") }
    }

    private fun beep(type: Int, ms: Int) {
        if (!cues.beeps) return
        runCatching { tone?.startTone(type, ms) }
    }

    private fun vibrate(pattern: LongArray) {
        if (!cues.vibration) return
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        if (v == null || !v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}

/** Keeps an interval session alive with the screen off: foreground notification + partial wake lock. */
class HiitService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = HiitEngine.state.value
        TimeNotifications.ensureChannels(this)
        try {
            ServiceCompat.startForeground(
                this, TimeNotifications.ID_HIIT, build(s),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (s == null) {
            finish()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "canif:hiit")
                .apply { setReferenceCounted(false); acquire(s.config.totalMs + 15 * 60_000L) }
            scope.launch {
                HiitEngine.state
                    .distinctUntilChangedBy { st -> st?.let { Triple(it.index, it.paused, it.finished) } }
                    .collect { st ->
                        if (st == null || st.finished) finish()
                        else TimeNotifications.notify(this@HiitService, TimeNotifications.ID_HIIT, build(st))
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(s: HiitState?): Notification {
        val b = NotificationCompat.Builder(this, TimeNotifications.CH_HIIT)
            .setSmallIcon(R.drawable.time_ic_notif_hiit)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(TimeNotifications.openTool(this, "intervals", 43))
        if (s == null) return b.setContentTitle(tr("Intervalles", "Intervals")).build()
        val p = s.phase
        b.setContentTitle(if (s.finished) tr("Séance terminée", "Workout done") else p.kind.label.text)
        if (!s.finished) {
            b.setContentText(
                tr("Round ${p.round.coerceAtLeast(1)}/${s.config.rounds}", "Round ${p.round.coerceAtLeast(1)}/${s.config.rounds}") +
                    if (s.config.sets > 1) tr(" · Série ${p.set}/${s.config.sets}", " · Set ${p.set}/${s.config.sets}") else "",
            )
            if (!s.paused) {
                val endWall = System.currentTimeMillis() + s.remaining()
                b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(endWall).setShowWhen(true)
            } else {
                b.setSubText(tr("En pause", "Paused"))
            }
            b.addAction(
                0, if (s.paused) tr("Reprendre", "Resume") else tr("Pause", "Pause"),
                TimeNotifications.action(this, TimerReceiver.ACTION_HIIT_TOGGLE),
            )
            b.addAction(0, tr("Arrêter", "Stop"), TimeNotifications.action(this, TimerReceiver.ACTION_HIIT_STOP))
        }
        return b.build()
    }

    private fun finish() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        scope.cancel()
        super.onDestroy()
    }
}
