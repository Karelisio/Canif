package com.karelisio.canif.tools.audio

import android.content.Context
import android.media.MediaRecorder
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.min

/**
 * Sound level analysis on the microphone thread: frequency weighting, "fast" exponential time
 * weighting (τ = 125 ms), energetic mean (Leq), min/max, and a 10 s history sampled every 50 ms in a
 * circular buffer. Levels are kept in dBFS; the calibration offset is applied at display time, so
 * changing it also corrects the past values.
 */
class SoundMeterEngine(context: Context) {
    @Volatile var weighting = Weighting.Type.A
    @Volatile var fastDbfs = -120f
        private set
    @Volatile var minDbfs = Float.NaN
        private set
    @Volatile var maxDbfs = Float.NaN
        private set
    @Volatile var leqDbfs = Float.NaN
        private set
    val history = FloatArray(HISTORY)
    @Volatile var historyCount = 0L
        private set
    @Volatile var lastPushNanos = 0L
        private set
    val failed: Boolean get() = mic.failed

    @Volatile private var resetRequested = false
    private var sampleRate = 48000
    private var filter: Weighting? = null
    private var filterType: Weighting.Type? = null
    private var msFast = 0.0
    private var energy = 0.0
    private var samples = 0L
    private var warmup = 0
    private var sinceStep = 0.0

    private val mic = MicCapture(
        context, HOP, MediaRecorder.AudioSource.VOICE_RECOGNITION,
        onStart = { sr -> sampleRate = sr; filterType = null },
        onBlock = ::process,
    )

    fun start() = mic.start()
    fun stop() = mic.stop()
    fun reset() {
        resetRequested = true
    }

    private fun process(block: FloatArray) {
        val sr = sampleRate
        val type = weighting
        if (type != filterType || filter == null) {
            filter = Weighting(sr, type)
            filterType = type
            warmup = (0.3 * sr / HOP).toInt() + 1
        }
        if (resetRequested) {
            resetRequested = false
            energy = 0.0; samples = 0L
            minDbfs = Float.NaN; maxDbfs = Float.NaN; leqDbfs = Float.NaN
            historyCount = 0L
            warmup = (0.2 * sr / HOP).toInt() + 1
        }
        val f = filter!!
        var s = 0.0
        for (i in 0 until HOP) {
            val y = f.process(block[i])
            s += y * y
        }
        val ms = s / HOP
        val dt = HOP.toDouble() / sr
        val alpha = 1.0 - exp(-dt / 0.125)
        msFast += alpha * (ms - msFast)
        val fast = (10.0 * log10(msFast.coerceAtLeast(1e-14))).toFloat()
        fastDbfs = fast
        if (warmup > 0) {
            warmup--
            return
        }
        energy += ms * HOP
        samples += HOP
        leqDbfs = (10.0 * log10((energy / samples).coerceAtLeast(1e-14))).toFloat()
        if (minDbfs.isNaN() || fast < minDbfs) minDbfs = fast
        if (maxDbfs.isNaN() || fast > maxDbfs) maxDbfs = fast
        sinceStep += dt
        if (sinceStep >= STEP) {
            sinceStep -= STEP
            if (sinceStep > STEP) sinceStep = 0.0
            history[(historyCount % HISTORY).toInt()] = fast
            lastPushNanos = System.nanoTime()
            historyCount += 1
        }
    }

    companion object {
        const val HOP = 1024
        const val HISTORY = 200 // 10 s at 50 ms
        const val STEP = 0.05
        const val DEFAULT_OFFSET = 90f
    }
}

private class Landmark(val db: Int, val label: () -> String, val icon: ImageVector)

private val landmarks = listOf(
    Landmark(30, { tr("Chuchotement", "Whisper") }, Icons.Rounded.Hearing),
    Landmark(60, { tr("Conversation", "Conversation") }, Icons.Rounded.Forum),
    Landmark(80, { tr("Trafic", "Traffic") }, Icons.Rounded.DirectionsCar),
    Landmark(105, { tr("Concert", "Concert") }, Icons.Rounded.MusicNote),
)

private fun describe(db: Float): String = when {
    db < 25 -> tr("Silence", "Silence")
    db < 40 -> tr("Très calme", "Very quiet")
    db < 55 -> tr("Calme", "Quiet")
    db < 70 -> tr("Conversation", "Conversation")
    db < 85 -> tr("Bruyant", "Loud")
    db < 100 -> tr("Très bruyant", "Very loud")
    db < 120 -> tr("Dangereux à la longue", "Harmful over time")
    else -> tr("Douloureux", "Painful")
}

private const val Y_MIN = 20f
private const val Y_MAX = 120f

@Composable
fun SoundMeterScreen() {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val app = CanifApp.instance
    val engine = remember { SoundMeterEngine(context) }

    var offset by remember { mutableFloatStateOf(SoundMeterEngine.DEFAULT_OFFSET) }
    var weighting by remember { mutableStateOf(Weighting.Type.A) }
    LaunchedEffect(Unit) {
        offset = app.settings.float("soundmeter.offset", SoundMeterEngine.DEFAULT_OFFSET).first()
        weighting = runCatching { Weighting.Type.valueOf(app.settings.string("soundmeter.weighting", "A").first()) }.getOrDefault(Weighting.Type.A)
    }
    SideEffect { engine.weighting = weighting }
    val offsetNow by rememberUpdatedState(offset)

    LifecycleResumeEffect(engine) {
        engine.start()
        onPauseOrDispose { engine.stop() }
    }

    val frame = remember { mutableLongStateOf(0L) }
    val live = remember { mutableFloatStateOf(0f) } // smoothed dB SPL, per frame
    var shown by remember { mutableFloatStateOf(Float.NaN) }
    var shownMin by remember { mutableFloatStateOf(Float.NaN) }
    var shownMax by remember { mutableFloatStateOf(Float.NaN) }
    var shownLeq by remember { mutableFloatStateOf(Float.NaN) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(engine, reduce) {
        var lastText = 0L
        var lastT = 0L
        while (true) {
            withFrameNanos { t ->
                val target = engine.fastDbfs + offsetNow
                val dt = if (lastT == 0L) 0f else ((t - lastT) / 1e9f).coerceIn(0f, 0.1f)
                lastT = t
                val k = if (reduce) 1f else 1f - exp(-dt / 0.08f)
                live.floatValue += (target - live.floatValue) * k
                frame.longValue = t
                if (t - lastText > 250_000_000L) {
                    lastText = t
                    val o = offsetNow
                    shown = (engine.fastDbfs + o).coerceAtLeast(0f)
                    shownMin = engine.minDbfs + o
                    shownMax = engine.maxDbfs + o
                    shownLeq = engine.leqDbfs + o
                    if (engine.failed != failed) failed = engine.failed
                }
            }
        }
    }

    fun fmt(v: Float) = if (v.isNaN()) "—" else String.format(Locale.ROOT, "%.1f", v.coerceAtLeast(0f))

    ToolColumn {
        if (failed) MicErrorCard()

        // Hero: big number over a shape that grows and bristles with loudness.
        val hero = c.primaryContainer
        val heroOn = c.onPrimaryContainer
        val blob = c.primary.copy(alpha = 0.22f)
        val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst) }
        val path = remember { Path() }
        CanifCard(color = hero, padding = PaddingValues(0.dp)) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(210.dp)) {
                    val lv = ((live.floatValue - 30f) / 80f).coerceIn(0f, 1f)
                    morph.toPath(lv, path)
                    val d = size.minDimension * (0.62f + 0.36f * lv)
                    withTransform({
                        rotate(if (reduce) 0f else frame.longValue / 40_000_000f % 360f, center)
                        translate(center.x - d / 2, center.y - d / 2)
                        scale(d, d, Offset.Zero)
                    }) { drawPath(path, blob) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingText(
                            fmt(shown),
                            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            color = heroOn,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (weighting) {
                                Weighting.Type.A -> "dB(A)"
                                Weighting.Type.C -> "dB(C)"
                                Weighting.Type.Z -> "dB"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = heroOn.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Text(if (shown.isNaN()) "" else describe(shown), style = MaterialTheme.typography.titleMedium, color = heroOn.copy(alpha = 0.85f))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBlock("Min", fmt(shownMin), Modifier.weight(1f))
            StatBlock(tr("Moy. (Leq)", "Avg (Leq)"), fmt(shownLeq), Modifier.weight(1f))
            StatBlock("Max", fmt(shownMax), Modifier.weight(1f))
        }

        LevelGraph(engine, frame, live, offset)

        // Reference levels.
        CanifCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            val cur = if (shown.isNaN()) -1f else shown
            val nearest = if (cur < 0) -1 else landmarks.indices.minBy { abs(landmarks[it].db - cur) }
            landmarks.forEachIndexed { i, l ->
                val active = i == nearest
                val bg by animateColorAsState(if (active) c.secondaryContainer else c.surfaceContainerHigh, label = "lm")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(l.icon, null, tint = if (active) c.onSecondaryContainer else c.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(l.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (active) c.onSecondaryContainer else c.onSurface)
                    Text("≈ ${l.db} dB", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                }
            }
        }

        SectionTitle(tr("Pondération", "Weighting"))
        ChoiceRow(Weighting.Type.entries.toList(), weighting, {
            when (it) {
                Weighting.Type.A -> tr("A (oreille)", "A (ear)")
                Weighting.Type.C -> tr("C (forts)", "C (loud)")
                Weighting.Type.Z -> tr("Z (plate)", "Z (flat)")
            }
        }, { w ->
            weighting = w
            engine.reset()
            app.appScope.launch { app.settings.setString("soundmeter.weighting", w.name) }
        })

        SectionTitle(tr("Calibration", "Calibration"))
        var saveJob by remember { mutableStateOf<Job?>(null) }
        val setOffset: (Float) -> Unit = { v ->
            offset = (Math.round(v.coerceIn(40f, 140f) * 2f) / 2f)
            saveJob?.cancel()
            val value = offset
            saveJob = app.appScope.launch {
                delay(400)
                app.settings.setFloat("soundmeter.offset", value)
            }
        }
        CanifCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepeatIconButton(Icons.Rounded.Remove, tr("Diminuer", "Decrease"), { setOffset(offset - 0.5f) })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format(Locale.ROOT, "%+.1f dB", offset),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    )
                    Text(tr("décalage dBFS → dB SPL", "dBFS → dB SPL offset"), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                }
                RepeatIconButton(Icons.Rounded.Add, tr("Augmenter", "Increase"), { setOffset(offset + 0.5f) })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "Valeurs approximatives : le micro d'un téléphone n'est pas étalonné. Ajustez le décalage en comparant avec un sonomètre de référence.",
                    "Approximate values: a phone microphone is not calibrated. Adjust the offset against a reference sound level meter.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )
            if (offset != SoundMeterEngine.DEFAULT_OFFSET) {
                TextButton(onClick = { haptics.click(); setOffset(SoundMeterEngine.DEFAULT_OFFSET) }) {
                    Text(tr("Rétablir +90 dB", "Restore +90 dB"))
                }
            }
        }

        FilledTonalButton(
            onClick = {
                haptics.confirm()
                engine.reset()
                shownMin = Float.NaN; shownMax = Float.NaN; shownLeq = Float.NaN
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.RestartAlt, null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Réinitialiser min / moy. / max", "Reset min / avg / max"))
        }
    }
}

@Composable
private fun LevelGraph(
    engine: SoundMeterEngine,
    frame: androidx.compose.runtime.MutableLongState,
    live: androidx.compose.runtime.MutableFloatState,
    offset: Float,
) {
    val c = MaterialTheme.colorScheme
    val line = c.primary
    val fillTop = c.primary.copy(alpha = 0.30f)
    val fillBottom = c.primary.copy(alpha = 0.02f)
    val grid = c.outlineVariant
    val labelColor = c.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = remember(measurer, labelStyle, Lang.isFr) {
        landmarks.map { l -> l.db to measurer.measure("${l.db} · ${l.label()}", labelStyle) }
    }
    val stroke = remember { Path() }
    val fill = remember { Path() }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }
    val brush = remember(fillTop, fillBottom) { Brush.verticalGradient(listOf(fillTop, fillBottom)) }
    CanifCard(color = c.surfaceContainerLow, padding = PaddingValues(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            frame.longValue // redraw every frame
            val w = size.width
            val h = size.height
            fun y(db: Float) = h - ((db - Y_MIN) / (Y_MAX - Y_MIN)).coerceIn(0f, 1f) * h
            for ((db, layout) in labels) {
                val yy = y(db.toFloat())
                drawLine(grid, Offset(0f, yy), Offset(w, yy), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                drawText(layout, labelColor, Offset(4.dp.toPx(), yy - layout.size.height - 1.dp.toPx()))
            }
            val count = engine.historyCount
            val n = min(count, SoundMeterEngine.HISTORY.toLong()).toInt()
            if (n < 2) return@Canvas
            val step = w / (SoundMeterEngine.HISTORY - 1)
            val frac = ((System.nanoTime() - engine.lastPushNanos) / (SoundMeterEngine.STEP * 1e9)).toFloat().coerceIn(0f, 1f)
            val hist = engine.history
            stroke.rewind()
            fill.rewind()
            var firstX = 0f
            for (i in 0 until n) {
                val idx = ((count - n + i) % SoundMeterEngine.HISTORY).toInt()
                val x = w - (n - 1 - i + frac) * step
                val yy = y(hist[idx] + offset)
                if (i == 0) {
                    stroke.moveTo(x, yy); fill.moveTo(x, h); fill.lineTo(x, yy); firstX = x
                } else {
                    stroke.lineTo(x, yy); fill.lineTo(x, yy)
                }
            }
            // Live head joins the last sample to the current level at the right edge.
            val headY = y(live.floatValue)
            stroke.lineTo(w, headY)
            fill.lineTo(w, headY)
            fill.lineTo(w, h)
            fill.lineTo(firstX, h)
            fill.close()
            drawPath(fill, brush)
            drawPath(stroke, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(line, radius = 5.dp.toPx(), center = Offset(w - 2.dp.toPx(), headY))
        }
    }
}
