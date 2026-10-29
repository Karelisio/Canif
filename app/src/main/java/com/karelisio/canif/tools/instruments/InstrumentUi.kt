package com.karelisio.canif.tools.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Fixed-size ring buffer of samples; [version] changes on every add so draw lambdas refresh. */
@Stable
class RingBuffer(val capacity: Int) {
    private val data = FloatArray(capacity)
    private var head = 0
    var size = 0
        private set
    var version by mutableIntStateOf(0)
        private set

    fun add(v: Float) {
        data[head] = v
        head = (head + 1) % capacity
        if (size < capacity) size++
        version++
    }

    /** i = 0 is the oldest sample. */
    operator fun get(i: Int): Float = data[(head - size + i + capacity * 2) % capacity]

    fun clear() { size = 0; head = 0; version++ }

    fun min(): Float { var m = Float.POSITIVE_INFINITY; for (i in 0 until size) m = min(m, get(i)); return m }
    fun max(): Float { var m = Float.NEGATIVE_INFINITY; for (i in 0 until size) m = max(m, get(i)); return m }
}

/**
 * Live line graph with a soft gradient fill. Everything is read in the draw phase.
 * When [fixedMin]/[fixedMax] are null, the range follows the data with [minSpan].
 */
@Composable
fun LiveGraph(
    buffer: RingBuffer,
    modifier: Modifier = Modifier,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    minSpan: Float = 1f,
    baseline: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val c = MaterialTheme.colorScheme
    val grid = c.outlineVariant.copy(alpha = 0.5f)
    val baseColor = c.tertiary
    val line = remember { Path() }
    val fill = remember { Path() }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainerHighest.copy(alpha = 0.6f))
            .drawWithCache {
                val brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)))
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    @Suppress("UNUSED_VARIABLE") val v = buffer.version
                    val w = size.width
                    val h = size.height
                    val pad = 10.dp.toPx()
                    for (i in 1..3) {
                        val y = h * i / 4f
                        drawLine(grid, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                    }
                    val n = buffer.size
                    if (n < 2) return@onDrawBehind
                    var lo = fixedMin ?: buffer.min()
                    var hi = fixedMax ?: buffer.max()
                    baseline?.invoke()?.let { b -> if (fixedMin == null) lo = min(lo, b); if (fixedMax == null) hi = max(hi, b) }
                    if (hi - lo < minSpan) {
                        val mid = (hi + lo) / 2f
                        lo = mid - minSpan / 2f; hi = mid + minSpan / 2f
                    }
                    val span = hi - lo
                    fun yOf(v: Float) = h - pad - ((v - lo) / span).coerceIn(0f, 1f) * (h - 2 * pad)
                    val step = w / (buffer.capacity - 1)
                    val x0 = w - (n - 1) * step
                    line.reset(); fill.reset()
                    for (i in 0 until n) {
                        val x = x0 + i * step
                        val y = yOf(buffer[i])
                        if (i == 0) { line.moveTo(x, y); fill.moveTo(x, h); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
                    }
                    fill.lineTo(w, h); fill.close()
                    drawPath(fill, brush)
                    baseline?.invoke()?.let { b ->
                        val y = yOf(b)
                        drawLine(baseColor, Offset(0f, y), Offset(w, y), 1.5.dp.toPx(), pathEffect = dash)
                    }
                    drawPath(line, color, style = stroke)
                    drawCircle(color, 4.5.dp.toPx(), Offset(w - 0f, yOf(buffer[n - 1])))
                }
            },
    )
}

/** Converts a dial fraction (0..1) to an angle in degrees, Canvas convention (0° = 3 o'clock). */
fun dialAngle(fraction: Float, startDeg: Float, sweepDeg: Float) = startDeg + sweepDeg * fraction

fun DrawScope.polar(center: Offset, radius: Float, deg: Float): Offset {
    val r = Math.toRadians(deg.toDouble())
    return Offset(center.x + radius * cos(r).toFloat(), center.y + radius * sin(r).toFloat())
}

/**
 * Elegant arc gauge: track, animated progress arc with round caps, fine ticks and optional
 * markers. [fraction] is read in the draw phase.
 */
@Composable
fun ArcGauge(
    fraction: () -> Float,
    modifier: Modifier = Modifier,
    startDeg: Float = 135f,
    sweepDeg: Float = 270f,
    ticks: Int = 54,
    markers: List<Pair<Float, String>> = emptyList(),
    progressColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.onSurfaceVariant)
    val labels = remember(markers, labelStyle) { markers.map { it.first to measurer.measure(it.second, labelStyle) } }
    val track = c.surfaceContainerHighest
    val tickColor = c.outline
    val glow = progressColor.copy(alpha = 0.18f)
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeCompat()) {
            val stroke = size.minDimension * 0.075f
            val r = size.minDimension / 2f - stroke - 18.dp.toPx()
            val center = this.center
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(2 * r, 2 * r)
            drawArc(track, startDeg, sweepDeg, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val f = fraction().coerceIn(0f, 1f)
            if (f > 0.001f) {
                drawArc(glow, startDeg, sweepDeg * f, false, topLeft, arcSize, style = Stroke(stroke * 1.9f, cap = StrokeCap.Round))
                drawArc(progressColor, startDeg, sweepDeg * f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Ticks inside the arc.
            val inner = r - stroke * 0.9f
            for (i in 0..ticks) {
                val a = dialAngle(i / ticks.toFloat(), startDeg, sweepDeg)
                val major = i % 9 == 0
                val len = if (major) stroke * 0.7f else stroke * 0.35f
                drawLine(
                    if (i / ticks.toFloat() <= f) progressColor.copy(alpha = 0.8f) else tickColor.copy(alpha = if (major) 0.8f else 0.4f),
                    polar(center, inner, a), polar(center, inner - len, a),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            // Markers outside the arc.
            labels.forEach { (pos, layout) ->
                val a = dialAngle(pos, startDeg, sweepDeg)
                drawCircle(if (pos <= f) progressColor else tickColor, 2.5.dp.toPx(), polar(center, r, a))
                val p = polar(center, r + stroke / 2f + 10.dp.toPx(), a)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
            // End knob.
            val knob = polar(center, r, dialAngle(f, startDeg, sweepDeg))
            drawCircle(c.surface, stroke * 0.42f, knob)
            drawCircle(progressColor, stroke * 0.28f, knob)
        }
        content()
    }
}

/**
 * Needle dial (speedometer style): graduated scale up to [max], spring needle read in the draw
 * phase through [value].
 */
@Composable
fun NeedleDial(
    value: () -> Float,
    max: Float,
    majorStep: Float,
    minorPerMajor: Int,
    modifier: Modifier = Modifier,
    startDeg: Float = 135f,
    sweepDeg: Float = 270f,
    redFrom: Float? = null,
    content: @Composable () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style: TextStyle = MaterialTheme.typography.titleSmall.copy(color = c.onSurface)
    val labels: List<Pair<Float, TextLayoutResult>> = remember(max, majorStep, style) {
        val list = ArrayList<Pair<Float, TextLayoutResult>>()
        var v = 0f
        while (v <= max + 0.001f) {
            list += v to measurer.measure(if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v), style)
            v += majorStep
        }
        list
    }
    val face = c.surfaceContainerHigh
    val rim = c.surfaceContainerHighest
    val tick = c.onSurfaceVariant
    val accent = c.primary
    val hot = c.error
    val needle = c.tertiary
    val path = remember { Path() }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeCompat()) {
            val R = size.minDimension / 2f
            val center = this.center
            drawCircle(rim, R)
            drawCircle(face, R * 0.94f)
            val f = (value() / max).coerceIn(0f, 1.02f)
            // Progress band.
            val br = R * 0.84f
            val bw = R * 0.05f
            val tl = Offset(center.x - br, center.y - br)
            val bs = Size(2 * br, 2 * br)
            drawArc(rim, startDeg, sweepDeg, false, tl, bs, style = Stroke(bw, cap = StrokeCap.Round))
            if (redFrom != null) {
                val rf = (redFrom / max).coerceIn(0f, 1f)
                drawArc(hot.copy(alpha = 0.35f), startDeg + sweepDeg * rf, sweepDeg * (1 - rf), false, tl, bs, style = Stroke(bw, cap = StrokeCap.Round))
            }
            if (f > 0.002f) drawArc(accent, startDeg, sweepDeg * f.coerceAtMost(1f), false, tl, bs, style = Stroke(bw, cap = StrokeCap.Round))
            // Graduations.
            val majors = labels.size - 1
            val total = majors * minorPerMajor
            val tr = R * 0.76f
            for (i in 0..total) {
                val frac = (i * majorStep / minorPerMajor) / max
                if (frac > 1.0001f) break
                val a = dialAngle(frac, startDeg, sweepDeg)
                val major = i % minorPerMajor == 0
                val len = if (major) R * 0.09f else R * 0.045f
                drawLine(
                    tick.copy(alpha = if (major) 0.95f else 0.5f),
                    polar(center, tr, a), polar(center, tr - len, a),
                    strokeWidth = if (major) 2.5.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            labels.forEach { (v, layout) ->
                val a = dialAngle(v / max, startDeg, sweepDeg)
                val p = polar(center, R * 0.56f, a)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
            // Needle.
            val a = dialAngle(f, startDeg, sweepDeg)
            rotate(a, center) {
                path.reset()
                path.apply {
                    moveTo(center.x - R * 0.12f, center.y - R * 0.028f)
                    lineTo(center.x + R * 0.78f, center.y - R * 0.006f)
                    quadraticTo(center.x + R * 0.8f, center.y, center.x + R * 0.78f, center.y + R * 0.006f)
                    lineTo(center.x - R * 0.12f, center.y + R * 0.028f)
                    close()
                }
                drawPath(path, Color.Black.copy(alpha = 0.12f), style = Stroke(R * 0.02f))
                drawPath(path, needle)
            }
            drawCircle(needle, R * 0.075f, center)
            drawCircle(face, R * 0.035f, center)
        }
        content()
    }
}

/** matchParentSize without needing a BoxScope receiver. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(1f))

/** Rounded warning banner. */
@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = c.onErrorContainer, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer)
    }
}
