package com.karelisio.canif.tools.audio

import android.content.Context
import android.media.MediaRecorder
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
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
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pitch tracking pipeline running on the microphone thread: sliding 4096-sample window with a hop of
 * 1024 (75 % overlap), RMS noise gate with hysteresis, YIN, 5-point median then exponential
 * smoothing in the cents domain. Results are published through volatile fields read once per frame.
 */
class TunerEngine(context: Context) {
    @Volatile var freq = 0f
        private set
    @Volatile var lastVoicedNanos = 0L
        private set
    @Volatile var levelDb = -120f
        private set
    val failed: Boolean get() = mic.failed

    private var yin: YinPitchDetector? = null
    private var window = FloatArray(SIZE)
    private val ring = FloatArray(5)
    private val sorted = FloatArray(5)
    private var ringCount = 0
    private var ringPos = 0
    private var smooth = Float.NaN
    private var gateOpen = false
    private var lastVoiced = 0L

    private val mic = MicCapture(
        context, HOP, MediaRecorder.AudioSource.MIC,
        onStart = { sr -> yin = YinPitchDetector(sr, SIZE, 30f, 1500f, 0.12) },
        onBlock = ::process,
    )

    fun start() = mic.start()
    fun stop() = mic.stop()

    private fun process(block: FloatArray) {
        val y = yin ?: return
        val win = window
        System.arraycopy(win, HOP, win, 0, SIZE - HOP)
        System.arraycopy(block, 0, win, SIZE - HOP, HOP)
        var s = 0.0
        for (i in SIZE / 2 until SIZE) {
            val v = win[i]
            s += v * v
        }
        val db = 20.0 * log10(sqrt(s / (SIZE / 2)).coerceAtLeast(1e-7))
        levelDb = db.toFloat()
        gateOpen = if (gateOpen) db > GATE_CLOSE else db > GATE_OPEN
        val now = System.nanoTime()
        if (gateOpen) {
            val f = y.detect(win)
            if (f in 30f..1500f && y.aperiodicity < 0.2) {
                val cents = (1200.0 * ln(f / 440.0) / LN2).toFloat()
                ring[ringPos] = cents
                ringPos = (ringPos + 1) % ring.size
                if (ringCount < ring.size) ringCount++
                val m = median()
                smooth = if (smooth.isNaN() || abs(m - smooth) > 60f) m else smooth + 0.35f * (m - smooth)
                freq = (440.0 * 2.0.pow(smooth / 1200.0)).toFloat()
                lastVoiced = now
                lastVoicedNanos = now
                return
            }
        }
        if (now - lastVoiced > 300_000_000L) {
            ringCount = 0
            smooth = Float.NaN
        }
    }

    private fun median(): Float {
        val n = ringCount
        for (i in 0 until n) {
            val v = ring[(ringPos - 1 - i + ring.size * 2) % ring.size]
            var j = i - 1
            while (j >= 0 && sorted[j] > v) {
                sorted[j + 1] = sorted[j]; j--
            }
            sorted[j + 1] = v
        }
        return sorted[(n - 1) / 2]
    }

    companion object {
        const val SIZE = 4096
        const val HOP = 1024
        const val GATE_OPEN = -56.0
        const val GATE_CLOSE = -62.0
        private val LN2 = ln(2.0)
    }
}

private const val SPRING_K = 170f // ω0 ≈ 13 rad/s
private const val SPRING_C = 2f * 0.55f * 13f // damping ratio 0.55

@Composable
fun TunerScreen() {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val settingsA4 = LocalAppSettings.current.referenceA4
    val app = CanifApp.instance

    var a4 by rememberSaveable { mutableIntStateOf(settingsA4) }
    LaunchedEffect(settingsA4) { a4 = settingsA4 }
    var tuningId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (tuningId == null) tuningId = app.settings.string("tuner.tuning", "chromatic").first()
    }
    val tuning = Tunings.firstOrNull { it.id == tuningId } ?: Tunings[0]

    val engine = remember { TunerEngine(context) }
    var failed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(engine) {
        engine.start()
        onPauseOrDispose { engine.stop() }
    }

    val needle = remember { mutableFloatStateOf(0f) }
    var voiced by remember { mutableStateOf(false) }
    var noteMidi by remember { mutableIntStateOf(-1) }
    var stringIndex by remember { mutableIntStateOf(-1) }
    var shownCents by remember { mutableIntStateOf(0) }
    var shownFreq by remember { mutableFloatStateOf(0f) }
    var inTune by remember { mutableStateOf(false) }
    val tuned = remember(tuning.id) { mutableStateListOf<Int>() }
    var locked by remember(tuning.id) { mutableIntStateOf(-1) }

    val tuningNow by rememberUpdatedState(tuning)
    val a4Now by rememberUpdatedState(a4)
    val lockedNow by rememberUpdatedState(locked)
    val hapticsNow by rememberUpdatedState(haptics)

    LaunchedEffect(engine, reduce) {
        var last = 0L
        var x = needle.floatValue
        var v = 0f
        var inTuneSince = 0L
        var lastText = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceIn(0f, 0.05f)
                last = t
                val now = System.nanoTime()
                val f = engine.freq
                val isVoiced = f > 0f && now - engine.lastVoicedNanos < 350_000_000L
                var target = x
                if (isVoiced) {
                    val tun = tuningNow
                    val midi = Notes.midiOf(f.toDouble(), a4Now.toDouble())
                    val idx: Int
                    val note: Int
                    if (tun.notes.isEmpty()) {
                        idx = -1
                        note = midi.roundToInt()
                    } else {
                        var best = 0
                        for (i in tun.notes.indices) if (abs(midi - tun.notes[i]) < abs(midi - tun.notes[best])) best = i
                        idx = if (lockedNow in tun.notes.indices) lockedNow else best
                        note = tun.notes[idx]
                    }
                    val cents = ((midi - note) * 100.0).toFloat()
                    target = cents.coerceIn(-50f, 50f)
                    if (note != noteMidi) noteMidi = note
                    if (idx != stringIndex) stringIndex = idx
                    if (now - lastText > 110_000_000L) {
                        shownCents = cents.roundToInt()
                        shownFreq = f
                        lastText = now
                    }
                    val ac = abs(cents)
                    if (ac < 3f) {
                        if (inTuneSince == 0L) inTuneSince = now
                        if (!inTune && now - inTuneSince > 250_000_000L) {
                            inTune = true
                            hapticsNow.confirm()
                            if (idx >= 0 && idx !in tuned) tuned += idx
                        }
                    } else {
                        inTuneSince = 0L
                        if (ac > 6f) inTune = false
                    }
                } else {
                    inTuneSince = 0L
                    if (inTune) inTune = false
                }
                if (voiced != isVoiced) voiced = isVoiced
                if (reduce) {
                    x = target; v = 0f
                } else {
                    var rem = dt
                    while (rem > 0f) {
                        val h = min(rem, 1f / 240f)
                        v += (-SPRING_K * (x - target) - SPRING_C * v) * h
                        x += v * h
                        rem -= h
                    }
                }
                needle.floatValue = x
                if (engine.failed != failed) failed = engine.failed
            }
        }
    }

    ToolColumn {
        if (failed) MicErrorCard()
        CanifCard(color = c.surfaceContainerLow, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            TunerGauge(
                needle = { needle.floatValue },
                voiced = voiced,
                inTune = inTune,
                note = if (noteMidi >= 0) noteMidi else null,
                flats = tuning.flats,
            )
            val hint = when {
                !voiced -> tr("Jouez une note", "Play a note")
                inTune || abs(shownCents) < 3 -> tr("Juste !", "In tune!")
                shownCents < 0 -> if (tuning.notes.isEmpty()) tr("Trop bas", "Too low") else tr("Trop bas : tendez la corde", "Too low: tighten the string")
                else -> if (tuning.notes.isEmpty()) tr("Trop haut", "Too high") else tr("Trop haut : détendez la corde", "Too high: loosen the string")
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (shownFreq > 0f) String.format(Locale.ROOT, "%.1f Hz", shownFreq) else "— Hz",
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = if (voiced) c.onSurface else c.onSurfaceVariant,
                    )
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = if (inTune) c.primary else c.onSurfaceVariant)
                }
                Text(
                    if (voiced) (if (shownCents > 0) "+$shownCents" else "$shownCents") + " c" else "",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (inTune) c.primary else c.onSurface,
                )
            }
        }

        if (tuning.notes.isNotEmpty()) {
            StringRow(
                notes = tuning.notes,
                flats = tuning.flats,
                detected = if (voiced) stringIndex else -1,
                tuned = tuned,
                locked = locked,
                onTap = { i -> haptics.click(); locked = if (locked == i) -1 else i },
            )
            Text(
                tr("Touchez une corde pour la verrouiller.", "Tap a string to lock it."),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionTitle(tr("Accordage", "Tuning"))
        ChipChoice(Tunings, tuning, { it.name.text }, { t ->
            tuningId = t.id
            app.appScope.launch { app.settings.setString("tuner.tuning", t.id) }
        })

        SectionTitle(tr("La de référence", "Reference A4"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val setA4: (Int) -> Unit = { v ->
                val nv = v.coerceIn(432, 445)
                if (nv != a4) {
                    a4 = nv
                    app.appScope.launch { app.settings.setReferenceA4(nv) }
                }
            }
            RepeatIconButton(Icons.Rounded.Remove, tr("Diminuer", "Decrease"), { setA4(a4 - 1) }, enabled = a4 > 432)
            Text(
                "$a4 Hz",
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            RepeatIconButton(Icons.Rounded.Add, tr("Augmenter", "Increase"), { setA4(a4 + 1) }, enabled = a4 < 445)
        }
    }
}

@Composable
private fun TunerGauge(needle: () -> Float, voiced: Boolean, inTune: Boolean, note: Int?, flats: Boolean) {
    val c = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val path = remember { Path() }
    val progress by animateFloatAsState(if (inTune) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "morph")
    val bump by animateFloatAsState(if (inTune) 1.07f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "bump")
    val alpha by animateFloatAsState(if (voiced) 1f else 0.35f, spring(stiffness = Spring.StiffnessLow), label = "alpha")
    val shapeColor by animateColorAsState(
        when {
            inTune -> c.primary
            voiced -> c.secondaryContainer
            else -> c.surfaceContainerHighest
        },
        spring(stiffness = Spring.StiffnessMediumLow), label = "shape",
    )
    val textColor by animateColorAsState(
        when {
            inTune -> c.onPrimary
            voiced -> c.onSecondaryContainer
            else -> c.onSurfaceVariant
        },
        label = "text",
    )
    val tick = c.onSurfaceVariant
    val zero = c.primary
    val needleOff = c.tertiary
    val needleOn = c.primary
    val measurer = rememberTextMeasurer()
    val noteStyle = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold)
    val noteLayout = remember(note, flats, noteStyle, measurer, com.karelisio.canif.core.i18n.Lang.isFr) {
        val text = buildAnnotatedString {
            if (note == null) append("—") else {
                append(Notes.name(note, flats))
                withStyle(SpanStyle(fontSize = 22.sp)) { append(Notes.octave(note).toString()) }
            }
        }
        measurer.measure(text, noteStyle)
    }
    val signStyle = MaterialTheme.typography.titleLarge
    val flatLayout = remember(measurer, signStyle) { measurer.measure("♭", signStyle) }
    val sharpLayout = remember(measurer, signStyle) { measurer.measure("♯", signStyle) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        val cx = size.width / 2
        val cy = size.height * 0.64f
        val radius = min(size.width / 2 - 28.dp.toPx(), size.height * 0.58f)
        val maxAngle = 64f
        fun angleOf(cents: Float) = cents / 50f * maxAngle
        fun point(deg: Float, r: Float): Offset {
            val a = deg * (PI.toFloat() / 180f)
            return Offset(cx + r * sin(a), cy - r * cos(a))
        }
        // In-tune zone.
        val zoneR = radius + 12.dp.toPx()
        drawArc(
            color = zero.copy(alpha = if (inTune) 0.9f else 0.28f),
            startAngle = -90f + angleOf(-3f),
            sweepAngle = angleOf(6f),
            useCenter = false,
            topLeft = Offset(cx - zoneR, cy - zoneR),
            size = Size(zoneR * 2, zoneR * 2),
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
        )
        // Ticks every 5 cents.
        for (i in -10..10) {
            val cents = i * 5f
            val major = i % 2 == 0
            val len = when {
                i == 0 -> 22.dp.toPx()
                major -> 14.dp.toPx()
                else -> 7.dp.toPx()
            }
            val a = angleOf(cents)
            drawLine(
                color = if (i == 0) zero else tick.copy(alpha = if (major) 0.7f else 0.4f),
                start = point(a, radius - len),
                end = point(a, radius),
                strokeWidth = if (i == 0) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val lp = point(-maxAngle - 9f, radius - 4.dp.toPx())
        drawText(flatLayout, tick, Offset(lp.x - flatLayout.size.width / 2, lp.y - flatLayout.size.height / 2))
        val rp = point(maxAngle + 9f, radius - 4.dp.toPx())
        drawText(sharpLayout, tick, Offset(rp.x - sharpLayout.size.width / 2, rp.y - sharpLayout.size.height / 2))

        // Needle (spring-driven, read in the draw phase only).
        val n = needle()
        val closeness = (1f - abs(n) / 25f).coerceIn(0f, 1f)
        val needleColor = lerp(needleOff, needleOn, closeness).copy(alpha = alpha)
        val shapeR = min(radius * 0.42f, 78.dp.toPx())
        val na = angleOf(n.coerceIn(-55f, 55f))
        drawLine(needleColor, point(na, shapeR + 10.dp.toPx()), point(na, radius + 4.dp.toPx()), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(needleColor, radius = 7.dp.toPx(), center = point(na, radius + 4.dp.toPx()))

        // Morphing target: Cookie9Sided → Circle when in tune; slowly turns with the deviation.
        morph.toPath(progress.coerceIn(0f, 1f), path)
        val d = shapeR * 2 * bump
        withTransform({
            rotate(n * 1.4f, Offset(cx, cy))
            translate(cx - d / 2, cy - d / 2)
            scale(d, d, Offset.Zero)
        }) {
            drawPath(path, shapeColor)
        }
        drawText(noteLayout, textColor, Offset(cx - noteLayout.size.width / 2, cy - noteLayout.size.height / 2))
    }
}

@Composable
private fun StringRow(notes: IntArray, flats: Boolean, detected: Int, tuned: List<Int>, locked: Int, onTap: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        notes.forEachIndexed { i, m ->
            val active = i == detected || i == locked
            val isTuned = i in tuned
            val scale by animateFloatAsState(if (i == detected) 1.14f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "s")
            val bg by animateColorAsState(
                when {
                    i == detected -> c.primary
                    isTuned -> c.tertiaryContainer
                    else -> c.surfaceContainerHigh
                },
                label = "bg",
            )
            val fg by animateColorAsState(
                when {
                    i == detected -> c.onPrimary
                    isTuned -> c.onTertiaryContainer
                    else -> c.onSurface
                },
                label = "fg",
            )
            Box(Modifier.padding(vertical = 6.dp).scale(scale)) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { onTap(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        Notes.full(m, flats),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                        color = fg,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 50.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                if (isTuned || i == locked) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (i == locked) c.secondary else c.tertiary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (i == locked) Icons.Rounded.Lock else Icons.Rounded.Check,
                            null,
                            tint = if (i == locked) c.onSecondary else c.onTertiary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}
