package com.karelisio.canif.tools.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Process
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalAppSettings
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Wave { SINE, SQUARE, TRIANGLE, SAW }

private fun naive(w: Wave, phase: Double): Double = when (w) {
    Wave.SINE -> sin(2 * PI * phase)
    Wave.SQUARE -> if (phase < 0.5) 1.0 else -1.0
    Wave.TRIANGLE -> 1.0 - 4.0 * abs(phase - 0.5)
    Wave.SAW -> 2.0 * phase - 1.0
}

/**
 * Continuous-phase oscillator streamed to an AudioTrack. Frequency and amplitude are smoothed per
 * sample (one-pole, ~12 ms), waveform changes crossfade, square/saw are band-limited with PolyBLEP,
 * and stopping fades out before the track is released: no clicks anywhere.
 */
class ToneEngine(context: Context) {
    private val app = context.applicationContext
    @Volatile var frequency = 440.0
    @Volatile var volume = 0.5f
    @Volatile var wave = Wave.SINE
    @Volatile private var playing = false
    @Volatile var failed = false
        private set
    private val lock = Any()
    private var threadRunning = false
    private var thread: Thread? = null

    fun play() {
        synchronized(lock) {
            playing = true
            if (!threadRunning) {
                threadRunning = true
                failed = false
                thread = Thread({ loop() }, "canif-tone").also { it.start() }
            }
        }
    }

    /** Fades out then releases the track (asynchronously). */
    fun stop() {
        playing = false
    }

    fun release() {
        playing = false
        thread?.join(400)
    }

    private fun blep(t: Double, dt: Double): Double = when {
        t < dt -> {
            val x = t / dt
            x + x - x * x - 1.0
        }
        t > 1.0 - dt -> {
            val x = (t - 1.0) / dt
            x * x + x + x + 1.0
        }
        else -> 0.0
    }

    private fun osc(w: Wave, phase: Double, dt: Double): Double = when (w) {
        Wave.SINE -> sin(2 * PI * phase)
        Wave.TRIANGLE -> 1.0 - 4.0 * abs(phase - 0.5)
        Wave.SAW -> 0.7 * ((2.0 * phase - 1.0) - blep(phase, dt))
        Wave.SQUARE -> {
            var p2 = phase + 0.5
            if (p2 >= 1.0) p2 -= 1.0
            0.55 * ((if (phase < 0.5) 1.0 else -1.0) + blep(phase, dt) - blep(p2, dt))
        }
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val o = PcmOut.open(app, AudioAttributes.CONTENT_TYPE_MUSIC, lowLatency = false, maxChunk = CHUNK, bufferMs = 60)
        if (o == null) {
            failed = true
            synchronized(lock) { threadRunning = false; playing = false }
            return
        }
        val sr = o.sampleRate
        val buf = FloatArray(CHUNK)
        val kF = 1.0 - exp(-1.0 / (0.012 * sr))
        val kA = 1.0 - exp(-1.0 / (0.010 * sr))
        val xfStep = 1.0 / (0.012 * sr)
        var phase = 0.0
        var inc = frequency / sr
        var amp = 0.0
        var cur = wave
        var prev = cur
        var xf = 1.0
        try {
            o.track.play()
            while (true) {
                val targetInc = (frequency / sr).coerceIn(1.0 / sr, 0.49)
                val v = volume.coerceIn(0f, 1f).toDouble()
                val targetAmp = if (playing) 0.92 * v * v else 0.0
                val w = wave
                if (w != cur) {
                    prev = cur; cur = w; xf = 0.0
                }
                for (i in 0 until CHUNK) {
                    inc += (targetInc - inc) * kF
                    amp += (targetAmp - amp) * kA
                    var s = osc(cur, phase, inc)
                    if (xf < 1.0) {
                        s = s * xf + osc(prev, phase, inc) * (1.0 - xf)
                        xf += xfStep
                    }
                    buf[i] = (s * amp).toFloat()
                    phase += inc
                    if (phase >= 1.0) phase -= 1.0
                }
                if (o.write(buf, CHUNK) < 0) break
                if (!playing && amp < 1e-5) {
                    val exit = synchronized(lock) {
                        if (!playing) {
                            threadRunning = false
                            true
                        } else false
                    }
                    if (exit) {
                        // Let the faded tail play out before releasing the track.
                        java.util.Arrays.fill(buf, 0f)
                        repeat((sr * 0.07 / CHUNK).toInt() + 1) { o.write(buf, CHUNK) }
                        break
                    }
                }
            }
        } catch (_: Throwable) {
            failed = true
        } finally {
            synchronized(lock) { threadRunning = false }
            o.release()
        }
    }

    companion object {
        private const val CHUNK = 512
    }
}

private const val F_MIN = 20.0
private const val F_MAX = 20000.0

private fun freqToPos(f: Double) = (ln(f / F_MIN) / ln(F_MAX / F_MIN)).toFloat()
private fun posToFreq(p: Float) = F_MIN * (F_MAX / F_MIN).pow(p.toDouble())

private fun fmtFreq(f: Double): String = when {
    f < 100 -> String.format(Locale.ROOT, "%.2f", f)
    f < 1000 -> String.format(Locale.ROOT, "%.1f", f)
    else -> String.format(Locale.ROOT, "%.0f", f)
}

@Composable
fun ToneGeneratorScreen() {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val a4 = LocalAppSettings.current.referenceA4
    val app = CanifApp.instance
    val engine = remember { ToneEngine(context) }

    var freq by remember { mutableDoubleStateOf(440.0) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var wave by remember { mutableStateOf(Wave.SINE) }
    var playing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(fmtFreq(440.0)) }

    LaunchedEffect(Unit) {
        freq = app.settings.float("tone.freq", 440f).first().toDouble().coerceIn(F_MIN, F_MAX)
        volume = app.settings.float("tone.volume", 0.5f).first().coerceIn(0f, 1f)
        wave = runCatching { Wave.valueOf(app.settings.string("tone.wave", Wave.SINE.name).first()) }.getOrDefault(Wave.SINE)
        text = fmtFreq(freq)
    }
    SideEffect {
        engine.frequency = freq
        engine.volume = volume
        engine.wave = wave
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        engine.stop()
        playing = false
    }
    DisposableEffect(engine) {
        onDispose {
            engine.release()
            app.appScope.launch {
                app.settings.setFloat("tone.freq", engine.frequency.toFloat())
                app.settings.setFloat("tone.volume", engine.volume)
                app.settings.setString("tone.wave", engine.wave.name)
            }
        }
    }

    fun setFreq(f: Double, updateText: Boolean = true) {
        freq = f.coerceIn(F_MIN, F_MAX)
        if (updateText) text = fmtFreq(freq)
    }

    // Waveform animation state (read in the draw phase).
    val phase = remember { mutableFloatStateOf(0f) }
    val shownAmp by animateFloatAsState(if (playing) 0.25f + 0.75f * volume else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "amp")
    val cycles by animateFloatAsState(1.2f + 6f * freqToPos(freq), spring(stiffness = Spring.StiffnessMediumLow), label = "cycles")
    LaunchedEffect(playing, reduce) {
        if (!playing || reduce) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) phase.floatValue = (phase.floatValue + (t - last) / 1e9f * 0.8f) % 1f
                last = t
            }
        }
    }
    val path = remember { Path() }

    ToolColumn {
        CanifCard(color = c.primaryContainer) {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(
                    fmtFreq(freq),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = c.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text("Hz", style = MaterialTheme.typography.headlineSmall, color = c.onPrimaryContainer.copy(alpha = 0.8f))
            }
            val midi = Notes.midiOf(freq, a4.toDouble())
            val n = midi.roundToInt()
            val cents = ((midi - n) * 100).roundToInt()
            Text(
                "≈ ${Notes.full(n)} " + (if (cents >= 0) "+$cents" else "$cents") + " c",
                style = MaterialTheme.typography.titleMedium,
                color = c.onPrimaryContainer.copy(alpha = 0.8f),
            )
            val waveColor = c.primary
            val glow = c.primary.copy(alpha = 0.18f)
            val axis = c.onPrimaryContainer.copy(alpha = 0.15f)
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val mid = size.height / 2
                drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
                val a = shownAmp * (size.height / 2 - 8.dp.toPx())
                val cyc = cycles
                val ph = phase.floatValue
                val steps = 160
                path.rewind()
                for (i in 0..steps) {
                    val x = i.toFloat() / steps
                    var p = (x * cyc + ph).toDouble()
                    p -= kotlin.math.floor(p)
                    val y = mid - (naive(wave, p) * a).toFloat()
                    if (i == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
                }
                drawPath(path, glow, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, waveColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        Slider(
            value = freqToPos(freq),
            onValueChange = { setFreq(posToFreq(it).let { f -> if (f < 100) (f * 10).roundToInt() / 10.0 else f.roundToInt().toDouble() }) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("20 Hz", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            Text("1 kHz", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            Text("20 kHz", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val semitone = 2.0.pow(1.0 / 12.0)
            RepeatTextButton(tr("−½ ton", "−1 semi"), { setFreq(freq / semitone) }, Modifier.weight(1f))
            RepeatTextButton("−1 Hz", { setFreq(freq - 1) }, Modifier.weight(1f))
            RepeatTextButton("+1 Hz", { setFreq(freq + 1) }, Modifier.weight(1f))
            RepeatTextButton(tr("+½ ton", "+1 semi"), { setFreq(freq * semitone) }, Modifier.weight(1f))
        }
        NumberField(
            text,
            { t ->
                text = t
                t.toDoubleOrNull()?.takeIf { it in F_MIN..F_MAX }?.let { setFreq(it, updateText = false) }
            },
            tr("Fréquence", "Frequency"),
            suffix = "Hz",
        )
        val quick = listOf(50.0, 100.0, a4.toDouble(), 1000.0, 5000.0, 10000.0, 15000.0)
        ChipChoice<Double?>(
            quick,
            quick.firstOrNull { abs(it - freq) < 0.01 },
            { v -> if (v == null) "" else if (v >= 1000) "${(v / 1000).roundToInt()} kHz" else if (v == a4.toDouble()) tr("La $a4", "A $a4") else "${v.roundToInt()} Hz" },
            { v -> if (v != null) setFreq(v) },
        )

        SectionTitle(tr("Forme d'onde", "Waveform"))
        ChoiceRow(Wave.entries.toList(), wave, {
            when (it) {
                Wave.SINE -> tr("Sinus", "Sine")
                Wave.SQUARE -> tr("Carré", "Square")
                Wave.TRIANGLE -> tr("Triangle", "Triangle")
                Wave.SAW -> tr("Scie", "Saw")
            }
        }, { wave = it })

        SectionTitle(tr("Volume", "Volume"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = c.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Slider(volume, { volume = it }, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text("${(volume * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        }

        AnimatedVisibility(freq >= 8000 || (freq >= 4000 && volume > 0.7f), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            CanifCard(color = c.errorContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = c.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tr(
                            "Les hautes fréquences fatiguent l'oreille, même quand on les entend à peine. Gardez un volume modéré, surtout au casque.",
                            "High frequencies tire your ears even when barely audible. Keep the volume moderate, especially with headphones.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onErrorContainer,
                    )
                }
            }
        }
        if (freq < 60) {
            Text(
                tr("Les très basses fréquences sont peu reproduites par le haut-parleur d'un téléphone.", "Very low frequencies are barely reproduced by a phone speaker."),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )
        }
        if (engine.failed) {
            Text(tr("Sortie audio indisponible.", "Audio output unavailable."), color = c.error)
        }

        val corner by animateDpAsState(if (playing) 20.dp else 32.dp, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "corner")
        Button(
            onClick = {
                haptics.confirm()
                playing = !playing
                if (playing) engine.play() else engine.stop()
            },
            shape = RoundedCornerShape(corner),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (playing) tr("Arrêter", "Stop") else tr("Jouer", "Play"), style = MaterialTheme.typography.titleMedium)
        }
    }
}
