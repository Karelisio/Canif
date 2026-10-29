package com.karelisio.canif.tools.audio

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioTimestamp
import android.os.Build
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Arrays
import java.util.Random
import java.util.concurrent.locks.LockSupport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Immutable metronome settings, shared by the screen, the service and the engine. */
data class MetronomeConfig(
    val bpm: Int = 120,
    val beats: Int = 4,
    val unit: Int = 4,
    val accents: List<Int> = defaultAccents(4, 4),
    val sub: Int = 1,
    val vibrate: Boolean = false,
    val flash: Boolean = false,
    val pulse: Boolean = false,
) {
    fun accentOf(beat: Int): Int = accents.getOrElse(beat) { NORMAL }

    fun normalized(): MetronomeConfig {
        val b = beats.coerceIn(1, 16)
        return copy(
            bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
            beats = b,
            unit = if (unit == 8) 8 else 4,
            sub = sub.coerceIn(1, 4),
            accents = List(b) { accents.getOrElse(it) { NORMAL }.coerceIn(MUTE, ACCENT) },
        )
    }

    /** Preset JSON (org.json): bpm, signature, accents, subdivision; options only for the last-session state. */
    fun toJson(withOptions: Boolean): String = JSONObject().apply {
        put("bpm", bpm)
        put("beats", beats)
        put("unit", unit)
        put("accents", JSONArray(accents))
        put("sub", sub)
        if (withOptions) {
            put("vibrate", vibrate)
            put("flash", flash)
            put("pulse", pulse)
        }
    }.toString()

    companion object {
        const val MUTE = 0
        const val NORMAL = 1
        const val ACCENT = 2
        const val MIN_BPM = 20
        const val MAX_BPM = 300

        val signatures = listOf(2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8, 7 to 8, 9 to 8, 12 to 8)

        fun defaultAccents(beats: Int, unit: Int): List<Int> {
            val strong: Set<Int> = when {
                unit == 8 && beats % 3 == 0 -> (0 until beats step 3).toSet() // 6/8, 9/8, 12/8
                unit == 8 && beats == 7 -> setOf(0, 2, 4) // 2+2+3
                beats == 5 -> setOf(0, 3) // 3+2
                else -> setOf(0)
            }
            return List(beats) { if (it in strong) ACCENT else NORMAL }
        }

        fun fromJson(s: String, base: MetronomeConfig = MetronomeConfig()): MetronomeConfig? = runCatching {
            val o = JSONObject(s)
            val beats = o.optInt("beats", base.beats)
            val unit = o.optInt("unit", base.unit)
            val arr = o.optJSONArray("accents")
            val acc = if (arr != null) List(arr.length()) { arr.optInt(it, NORMAL) } else defaultAccents(beats, unit)
            base.copy(
                bpm = o.optInt("bpm", base.bpm),
                beats = beats,
                unit = unit,
                accents = acc,
                sub = o.optInt("sub", base.sub),
                vibrate = o.optBoolean("vibrate", base.vibrate),
                flash = o.optBoolean("flash", base.flash),
                pulse = o.optBoolean("pulse", base.pulse),
            ).normalized()
        }.getOrNull()
    }
}

/**
 * Sample-accurate metronome. A writer thread renders the whole stream (silence + pre-synthesized
 * clicks) into a low-latency AudioTrack, counting frames: tick n lands exactly at
 * anchor + round(n · sampleRate · 60 / bpm / subdivision). No Handler, delay or sleep is involved
 * in the audio timing; the blocking write paces the loop on the audio clock. Tempo changes re-anchor
 * on the last played tick, so they apply on the next tick without glitch.
 *
 * A second "clock" thread maps written frames to wall-clock time with [AudioTrack.getTimestamp]
 * (falling back to the playback head) and publishes each tick when it actually leaves the speaker:
 * visual events slightly ahead (display latency), vibration and torch at the sound.
 */
class MetronomeEngine(context: Context) {
    private val app = context.applicationContext

    @Volatile private var cfg = MetronomeConfig()
    @Volatile private var cfgVersion = 0
    @Volatile private var running = false
    private var out: PcmOut? = null
    private var writer: Thread? = null
    private var clock: Thread? = null

    @Volatile var visualSeq = 0L
        private set
    @Volatile var visualBeat = 0
        private set
    @Volatile var visualSub = 0
        private set
    @Volatile var visualAccent = MetronomeConfig.NORMAL
        private set

    private val ringFrame = LongArray(RING)
    private val ringBeat = IntArray(RING)
    private val ringSub = IntArray(RING)
    private val ringAccent = IntArray(RING)
    @Volatile private var ringWrite = 0L

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
    }.getOrNull()?.takeIf { it.hasVibrator() }
    private val vibStrong: VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
    private val vibNormal: VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        else VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
    private val torchLazy = lazy { if (app.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) Torch(app) else null }
    private val torch: Torch? get() = torchLazy.value

    private fun torchOff() {
        if (torchLazy.isInitialized()) torchLazy.value?.set(false)
    }

    fun apply(c: MetronomeConfig) {
        if (c != cfg) {
            cfg = c
            cfgVersion++
        }
    }

    val isRunning: Boolean get() = running

    fun start(): Boolean {
        if (running) return true
        val o = PcmOut.open(app, AudioAttributes.CONTENT_TYPE_SONIFICATION, lowLatency = true, maxChunk = CHUNK, bufferMs = 50) ?: return false
        out = o
        val sounds = Clicks(o.sampleRate)
        running = true
        writer = Thread({ writeLoop(o, sounds) }, "canif-metronome").also { it.start() }
        clock = Thread({ clockLoop(o) }, "canif-metronome-clock").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        writer?.join(500)
        clock?.join(500)
        writer = null
        clock = null
        out?.release()
        out = null
        torchOff()
    }

    private fun writeLoop(o: PcmOut, s: Clicks) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val sr = o.sampleRate
        val buf = FloatArray(CHUNK)
        val vSample = arrayOfNulls<FloatArray>(VOICES)
        val vPos = IntArray(VOICES)
        var c = cfg
        var ver = cfgVersion
        var spt = sr * 60.0 / c.bpm / c.sub // samples per tick
        var pos = 0L
        var anchorFrame = (sr * 0.04).toLong() // short lead so the first click is never truncated
        var anchorTick = 0L
        var nextTick = 0L
        var nextFrame = anchorFrame
        var beat = 0
        var sub = 0
        var lastTickFrame = -1L
        var lastBeatFrame = -1L
        try {
            o.track.play()
        } catch (_: IllegalStateException) {
            running = false
            return
        }
        while (running) {
            if (ver != cfgVersion) {
                ver = cfgVersion
                val nc = cfg
                val structural = nc.beats != c.beats || nc.sub != c.sub
                if (structural || nc.bpm != c.bpm) {
                    val newSpt = sr * 60.0 / nc.bpm / nc.sub
                    var nf: Long
                    if (structural) {
                        // Restart cleanly on the next main beat (new bar if the signature changed).
                        if (nc.beats != c.beats) beat = 0
                        else if (sub != 0) beat = (beat + 1) % nc.beats
                        sub = 0
                        nf = if (lastBeatFrame >= 0) lastBeatFrame + Math.round(newSpt * nc.sub) else nextFrame
                    } else {
                        nf = if (lastTickFrame >= 0) lastTickFrame + Math.round(newSpt) else nextFrame
                    }
                    if (nf < pos) nf = pos
                    spt = newSpt
                    anchorFrame = nf
                    anchorTick = nextTick
                    nextFrame = nf
                }
                c = nc
            }
            Arrays.fill(buf, 0f)
            for (v in 0 until VOICES) {
                val smp = vSample[v] ?: continue
                var p = vPos[v]
                var j = 0
                while (j < CHUNK && p < smp.size) {
                    buf[j] += smp[p]; j++; p++
                }
                vPos[v] = p
                if (p >= smp.size) vSample[v] = null
            }
            val end = pos + CHUNK
            while (nextFrame < end) {
                val off = (nextFrame - pos).toInt().coerceAtLeast(0)
                val accent = if (sub == 0) c.accentOf(beat) else SUBDIVISION
                val smp = when (accent) {
                    MetronomeConfig.ACCENT -> s.accent
                    MetronomeConfig.NORMAL -> s.normal
                    SUBDIVISION -> s.sub
                    else -> null
                }
                if (smp != null) {
                    var slot = 0
                    for (v in 0 until VOICES) if (vSample[v] == null) { slot = v; break }
                    vSample[slot] = smp
                    var p = 0
                    var j = off
                    while (j < CHUNK && p < smp.size) {
                        buf[j] += smp[p]; j++; p++
                    }
                    vPos[slot] = p
                }
                val i = (ringWrite % RING).toInt()
                ringFrame[i] = nextFrame
                ringBeat[i] = beat
                ringSub[i] = sub
                ringAccent[i] = accent
                ringWrite += 1 // volatile write publishes the entry
                if (sub == 0) lastBeatFrame = nextFrame
                lastTickFrame = nextFrame
                sub++
                if (sub >= c.sub) {
                    sub = 0
                    beat++
                    if (beat >= c.beats) beat = 0
                }
                nextTick++
                nextFrame = anchorFrame + Math.round((nextTick - anchorTick) * spt)
            }
            if (o.write(buf, CHUNK) < 0) break
            pos = end
        }
    }

    private fun clockLoop(o: PcmOut) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        val sr = o.sampleRate.toLong()
        val ts = AudioTimestamp()
        var read = 0L
        var visualDone = -1L
        var tsFrame = 0L
        var tsNanos = 0L
        var hasTs = false
        var lastQuery = 0L
        var torchOffAt = 0L
        while (running) {
            val now = System.nanoTime()
            if (!hasTs || now - lastQuery > 40_000_000L) {
                lastQuery = now
                val ok = try {
                    o.track.getTimestamp(ts)
                } catch (_: Exception) {
                    false
                }
                if (ok && ts.nanoTime > 0 && ts.framePosition > 0) {
                    tsFrame = ts.framePosition
                    tsNanos = ts.nanoTime
                    hasTs = true
                } else {
                    val head = o.track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    if (head > 0) {
                        tsFrame = head
                        tsNanos = now + FALLBACK_LATENCY
                        hasTs = true
                    }
                }
            }
            if (torchOffAt != 0L && now >= torchOffAt) {
                torchOff()
                torchOffAt = 0L
            }
            val w = ringWrite
            if (w - read > RING) read = w - RING
            var wake = now + 20_000_000L
            if (!hasTs) {
                wake = now + 4_000_000L
            } else if (read < w) {
                val i = (read % RING).toInt()
                val due = tsNanos + (ringFrame[i] - tsFrame) * 1_000_000_000L / sr
                if (visualDone < read) {
                    if (now >= due - VISUAL_LEAD) {
                        visualBeat = ringBeat[i]
                        visualSub = ringSub[i]
                        visualAccent = ringAccent[i]
                        visualSeq = read + 1
                        visualDone = read
                    } else {
                        wake = min(wake, due - VISUAL_LEAD)
                    }
                }
                if (visualDone >= read) {
                    if (now >= due - HAPTIC_LEAD) {
                        val acc = ringAccent[i]
                        if (ringSub[i] == 0 && acc != MetronomeConfig.MUTE) {
                            val c = cfg
                            if (c.vibrate) runCatching { vibrator?.vibrate(if (acc == MetronomeConfig.ACCENT) vibStrong else vibNormal) }
                            if (c.flash) torch?.let {
                                it.set(true)
                                torchOffAt = System.nanoTime() + if (acc == MetronomeConfig.ACCENT) 90_000_000L else 55_000_000L
                            }
                        }
                        read++
                        continue
                    } else {
                        wake = min(wake, due - HAPTIC_LEAD)
                    }
                }
            }
            if (torchOffAt != 0L) wake = min(wake, torchOffAt)
            val sleep = wake - System.nanoTime()
            if (sleep > 100_000L) LockSupport.parkNanos(sleep)
        }
        torchOff()
    }

    /** Short percussive clicks with clean envelopes (attack ramp, exponential decay, final fade to zero). */
    private class Clicks(sr: Int) {
        val accent = click(sr, 1760.0, 0.050, 0.85f, 9.0, 0.35)
        val normal = click(sr, 1245.0, 0.042, 0.62f, 7.0, 0.30)
        val sub = click(sr, 932.0, 0.026, 0.34f, 4.5, 0.20)

        private fun click(sr: Int, f: Double, dur: Double, gain: Float, decayMs: Double, noise: Double): FloatArray {
            val n = (sr * dur).toInt()
            val out = FloatArray(n)
            val attack = (sr * 0.0006).toInt().coerceAtLeast(1)
            val release = (sr * 0.004).toInt().coerceAtLeast(1)
            val rnd = Random(17)
            var peak = 0.0
            val tmp = DoubleArray(n)
            for (i in 0 until n) {
                val t = i.toDouble() / sr
                var env = exp(-t * 1000.0 / decayMs)
                if (i < attack) env *= i.toDouble() / attack
                if (i > n - release) env *= (n - i).toDouble() / release
                val body = sin(2 * PI * f * t) + 0.45 * sin(2 * PI * f * 2.76 * t) * exp(-t * 1000.0 / (decayMs * 0.35))
                val transient = (rnd.nextDouble() * 2 - 1) * exp(-t * 1000.0 / 1.2) * noise
                val v = env * (body * (1 - noise) + transient)
                tmp[i] = v
                if (abs(v) > peak) peak = abs(v)
            }
            val k = if (peak > 0) gain / peak else 0.0
            for (i in 0 until n) out[i] = (tmp[i] * k).toFloat()
            return out
        }
    }

    private class Torch(context: Context) {
        private val cm = context.getSystemService(CameraManager::class.java)
        private val id: String? = runCatching {
            val ids = cm.cameraIdList
            ids.firstOrNull {
                val ch = cm.getCameraCharacteristics(it)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
        @Volatile private var on = false

        fun set(value: Boolean) {
            val i = id ?: return
            if (value == on) return
            runCatching {
                cm.setTorchMode(i, value)
                on = value
            }
        }
    }

    companion object {
        const val SUBDIVISION = 3
        private const val CHUNK = 256
        private const val VOICES = 4
        private const val RING = 64
        private const val VISUAL_LEAD = 28_000_000L // display pipeline latency compensation
        private const val HAPTIC_LEAD = 6_000_000L
        private const val FALLBACK_LATENCY = 30_000_000L
    }
}
