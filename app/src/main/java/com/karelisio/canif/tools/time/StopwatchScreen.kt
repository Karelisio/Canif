package com.karelisio.canif.tools.time

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.rememberHaptics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Process-wide stopwatch state: survives rotation and leaving the screen, and is persisted
 * so it also survives process death (based on elapsedRealtime, with a wall-clock fallback after reboot).
 */
object StopwatchState {
    var running by mutableStateOf(false)
        private set
    private var startedAt = 0L      // elapsedRealtime of the last start
    private var startedWall = 0L    // wall clock of the last start (reboot fallback)
    var accumulated by mutableLongStateOf(0L)
        private set
    /** Cumulative split times. */
    val laps = mutableStateListOf<Long>()
    private var loaded = false

    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long = accumulated + if (running) now - startedAt else 0

    fun start() {
        if (running) return
        startedAt = SystemClock.elapsedRealtime()
        startedWall = System.currentTimeMillis()
        running = true
        save()
    }

    fun pause() {
        if (!running) return
        accumulated = elapsed()
        running = false
        save()
    }

    fun reset() {
        running = false
        accumulated = 0
        laps.clear()
        save()
    }

    fun lap() {
        if (!running) return
        laps.add(elapsed())
        save()
    }

    suspend fun load() {
        if (loaded) return
        loaded = true
        val raw = CanifApp.instance.settings.string(KEY, "").first()
        if (raw.isBlank()) return
        runCatching {
            val o = JSONObject(raw)
            val bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val sameBoot = kotlin.math.abs(bootWall - o.getLong("bootWall")) < 10_000
            accumulated = o.getLong("acc")
            laps.clear()
            val a = o.getJSONArray("laps")
            for (i in 0 until a.length()) laps.add(a.getLong(i))
            if (o.getBoolean("running")) {
                startedWall = o.getLong("startWall")
                startedAt = if (sameBoot) o.getLong("start")
                else SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedWall).coerceAtLeast(0)
                running = true
            }
        }
    }

    private fun save() {
        val o = JSONObject()
            .put("running", running)
            .put("start", startedAt)
            .put("startWall", startedWall)
            .put("acc", accumulated)
            .put("bootWall", System.currentTimeMillis() - SystemClock.elapsedRealtime())
            .put("laps", JSONArray(laps.toList()))
        CanifApp.instance.appScope.launch { CanifApp.instance.settings.setString(KEY, o.toString()) }
    }

    private const val KEY = "stopwatch_state"
}

@Composable
fun StopwatchScreen() {
    val sw = StopwatchState
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { sw.load() }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sw.running) {
        now = SystemClock.elapsedRealtime()
        while (sw.running) withFrameMillis { now = SystemClock.elapsedRealtime() }
    }
    val elapsed = sw.elapsed(now)
    val lapDurations = remember(sw.laps.size) { sw.laps.mapIndexed { i, v -> v - (if (i == 0) 0L else sw.laps[i - 1]) } }
    val best = if (lapDurations.size >= 2) lapDurations.withIndex().minBy { it.value }.index else -1
    val worst = if (lapDurations.size >= 2) lapDurations.withIndex().maxBy { it.value }.index else -1

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 340.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                StopwatchDial({ sw.elapsed(now) }, sw.laps, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatStopwatch(elapsed),
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                        color = c.onSurface,
                    )
                    AnimatedVisibility(sw.laps.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        Text(
                            tr("Tour ", "Lap ") + "${sw.laps.size + 1} · " + formatStopwatch(elapsed - (sw.laps.lastOrNull() ?: 0L)),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            FullRow {
                val canSecondary = sw.running || elapsed > 0
                MorphButton(
                    onClick = {
                        if (sw.running) { haptics.tick(); sw.lap() } else { haptics.reject(); sw.reset() }
                    },
                    active = sw.running,
                    enabled = canSecondary,
                    container = c.secondaryContainer,
                    content = c.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    AnimatedContent(sw.running, transitionSpec = { scaleIn(spring(dampingRatio = 0.5f)) togetherWith scaleOut() }, label = "sec") { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r) ButtonLabel(Icons.Rounded.Flag, tr("Tour", "Lap")) else ButtonLabel(Icons.Rounded.RestartAlt, tr("Réinit.", "Reset"))
                        }
                    }
                }
                MorphButton(
                    onClick = { if (sw.running) sw.pause() else sw.start() },
                    active = sw.running,
                    modifier = Modifier.weight(1.4f),
                ) {
                    AnimatedContent(sw.running, transitionSpec = { scaleIn(spring(dampingRatio = 0.5f)) togetherWith scaleOut() }, label = "main") { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                r -> ButtonLabel(Icons.Rounded.Pause, tr("Pause", "Pause"))
                                elapsed > 0 -> ButtonLabel(Icons.Rounded.PlayArrow, tr("Reprendre", "Resume"))
                                else -> ButtonLabel(Icons.Rounded.PlayArrow, tr("Démarrer", "Start"))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val n = lapDurations.size
                itemsIndexed(lapDurations.asReversed(), key = { i, _ -> n - i }) { i, d ->
                    val index = n - 1 - i
                    LapRow(
                        number = index + 1,
                        duration = d,
                        split = sw.laps[index],
                        kind = when (index) {
                            best -> 1
                            worst -> 2
                            else -> 0
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            placementSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LapRow(number: Int, duration: Long, split: Long, kind: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when (kind) {
            1 -> c.tertiaryContainer
            2 -> c.errorContainer
            else -> c.surfaceContainerHigh
        },
        label = "lap",
    )
    val fg = when (kind) {
        1 -> c.onTertiaryContainer
        2 -> c.onErrorContainer
        else -> c.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Tour ", "Lap ") + number, style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f)) {
            if (kind != 0) {
                Text(
                    if (kind == 1) tr("Meilleur", "Best") else tr("Pire", "Worst"),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(fg.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatStopwatch(duration), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = fg, textAlign = TextAlign.End)
            Text(formatStopwatch(split), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = fg.copy(alpha = 0.7f))
        }
    }
}

/** 60 ticks, a sweeping seconds arc that restarts every minute, an orbiting head and lap markers. */
@Composable
private fun StopwatchDial(elapsed: () -> Long, laps: List<Long>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val primary = c.primary
    val track = c.surfaceContainerHighest
    val tick = c.outlineVariant
    val tickStrong = c.outline
    val lapColor = c.tertiary
    Canvas(modifier) {
        val e = elapsed()
        val stroke = 14.dp.toPx()
        val d = size.minDimension - stroke
        val r = d / 2
        val center = Offset(size.width / 2, size.height / 2)
        val tl = Offset(center.x - r, center.y - r)
        // Ticks inside the ring.
        val inner = r - stroke * 1.4f
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90)
            val len = if (i % 5 == 0) stroke * 0.9f else stroke * 0.45f
            val cosA = cos(a).toFloat()
            val sinA = sin(a).toFloat()
            drawLine(
                if (i % 5 == 0) tickStrong else tick,
                Offset(center.x + inner * cosA, center.y + inner * sinA),
                Offset(center.x + (inner - len) * cosA, center.y + (inner - len) * sinA),
                strokeWidth = if (i % 5 == 0) 2.5f.dp.toPx() else 1.5f.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
        val minuteFrac = (e % 60_000L) / 60_000f
        // The whole arc turns a little more every minute, so each lap of the dial feels alive.
        val minutes = (e / 60_000L).toInt()
        rotate(minutes * 30f, center) {
            if (minuteFrac > 0f) drawArc(primary, -90f, 360f * minuteFrac, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            val a = Math.toRadians((-90f + 360f * minuteFrac).toDouble())
            val head = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            if (e > 0) {
                drawCircle(primary, stroke * 0.9f, head)
                drawCircle(track, stroke * 0.35f, head)
            }
        }
        // Lap markers (position within their minute), on the rotated frame of the current minute.
        for (l in laps) {
            if (l / 60_000L != e / 60_000L) continue
            val a = Math.toRadians(((l % 60_000L) / 60_000.0) * 360.0 - 90 + minutes * 30.0)
            drawCircle(lapColor, stroke * 0.32f, Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
        }
    }
}
