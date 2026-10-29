package com.karelisio.canif.tools.color

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.tools.qr.CameraFeed
import com.karelisio.canif.tools.qr.CameraToggleButton
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.min

/** Averages a small square at the center of each YUV frame and converts it to RGB, smoothed over time. */
private class CenterColorAnalyzer(private val out: MutableStateFlow<Color>) : ImageAnalysis.Analyzer {
    @Volatile var frozen = false
    private var sr = -1f
    private var sg = 0f
    private var sb = 0f

    override fun analyze(image: ImageProxy) {
        try {
            if (frozen || image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) return
            val w = image.width
            val h = image.height
            val yP = image.planes[0]; val uP = image.planes[1]; val vP = image.planes[2]
            val yBuf = yP.buffer; val uBuf = uP.buffer; val vBuf = vP.buffer
            val half = (min(w, h) * 0.025f).toInt().coerceAtLeast(4)
            val cx = w / 2; val cy = h / 2
            var ys = 0L; var us = 0L; var vs = 0L; var n = 0
            var y = cy - half
            while (y < cy + half) {
                var x = cx - half
                while (x < cx + half) {
                    ys += yBuf.get(y * yP.rowStride + x * yP.pixelStride).toInt() and 0xFF
                    val ux = x / 2; val uy = y / 2
                    us += uBuf.get(uy * uP.rowStride + ux * uP.pixelStride).toInt() and 0xFF
                    vs += vBuf.get(uy * vP.rowStride + ux * vP.pixelStride).toInt() and 0xFF
                    n++
                    x += 2
                }
                y += 2
            }
            if (n == 0) return
            val yy = ys.toFloat() / n
            val u = us.toFloat() / n - 128f
            val v = vs.toFloat() / n - 128f
            val r = (yy + 1.402f * v).coerceIn(0f, 255f) / 255f
            val g = (yy - 0.344136f * u - 0.714136f * v).coerceIn(0f, 255f) / 255f
            val b = (yy + 1.772f * u).coerceIn(0f, 255f) / 255f
            if (sr < 0f) { sr = r; sg = g; sb = b } else {
                val k = 0.35f
                sr += (r - sr) * k; sg += (g - sg) * k; sb += (b - sb) * k
            }
            out.value = Color(sr, sg, sb)
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }
}

@Composable
fun ToolScope.ColorPickerScreen() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    val flow = remember { MutableStateFlow(c.primary) }
    val analyzer = remember { CenterColorAnalyzer(flow) }
    val live by flow.collectAsStateWithLifecycle()
    var frozen by remember { mutableStateOf<Color?>(null) }
    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    val history by ColorHistory.colors.collectAsStateWithLifecycle(emptyList())

    val color = frozen ?: live
    val shown by animateColorAsState(color, spring(stiffness = Spring.StiffnessMediumLow), label = "shown")
    val hex = ColorMath.hex(color)
    val name = ColorMath.name(color)

    fun toggleFreeze() {
        if (frozen == null) {
            frozen = live
            analyzer.frozen = true
            ColorHistory.add(live)
            haptics.confirm()
        } else {
            frozen = null
            analyzer.frozen = false
            haptics.click()
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(c.surfaceContainerHighest)
                .clickable(onClickLabel = tr("Figer", "Freeze")) { toggleFreeze() },
        ) {
            CameraFeed(analyzer, Modifier.fillMaxSize(), torch = torch, analysisSize = android.util.Size(640, 480), onFlashUnit = { hasFlash = it })
            Reticle(shown, frozen != null, Modifier.align(Alignment.Center))
            if (hasFlash) {
                CameraToggleButton(
                    torch, { torch = it }, Icons.Rounded.FlashlightOn, Icons.Rounded.FlashlightOff, tr("Lampe", "Torch"),
                    Modifier.align(Alignment.TopEnd).padding(12.dp), size = 48.dp,
                )
            }
            val badge by animateFloatAsState(if (frozen != null) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "badge")
            if (badge > 0.01f) {
                Surface(
                    shape = CircleShape,
                    color = c.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).graphicsLayer { scaleX = badge; scaleY = badge; alpha = badge.coerceIn(0f, 1f) },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Figée", "Frozen"), color = c.onPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(32.dp), color = c.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorphSwatch(color, size = 84.dp, shapeKey = name)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                        RollingText(hex, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace), color = c.onSurface)
                    }
                    IconButton(onClick = { copyToClipboard(context, hex); haptics.confirm() }) { Icon(Icons.Rounded.ContentCopy, tr("Copier", "Copy")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatChip(ColorMath.rgbText(color), Modifier.weight(1f))
                    FormatChip(ColorMath.hslText(color), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { toggleFreeze() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(if (frozen == null) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (frozen == null) tr("Figer", "Freeze") else tr("Reprendre", "Resume"))
                    }
                    FilledTonalButton(
                        onClick = { copyToClipboard(context, hex); haptics.confirm() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Copier", "Copy"))
                    }
                }
                if (history.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Couleurs capturées", "Captured colors"), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = { haptics.reject(); ColorHistory.clear() }, Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, tr("Effacer", "Clear"), Modifier.size(18.dp))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(history, key = { _, it -> ColorMath.hex(it) }) { _, h ->
                            Box(
                                Modifier
                                    .animateItem()
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(h)
                                    .border(1.dp, c.outlineVariant, CircleShape)
                                    .clickable {
                                        haptics.click()
                                        frozen = h
                                        analyzer.frozen = true
                                        copyToClipboard(context, ColorMath.hex(h))
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatChip(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    Surface(
        onClick = { copyToClipboard(context, text); haptics.confirm() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Center crosshair: a ring tinted with the sampled color around the sampling area. */
@Composable
private fun Reticle(color: Color, frozen: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (frozen) 0.82f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), label = "reticle")
    val outline = c.scrim
    Canvas(modifier.size(120.dp)) {
        val r = size.minDimension / 2 * 0.62f * scale
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(outline.copy(alpha = 0.35f), radius = r + 9.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        drawCircle(color, radius = r, center = center, style = Stroke(12.dp.toPx()))
        drawCircle(Color.White, radius = r - 6.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, radius = r + 6.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        val g = 10.dp.toPx()
        val l = 8.dp.toPx()
        listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
            drawLine(Color.White, center + d * g, center + d * (g + l), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
