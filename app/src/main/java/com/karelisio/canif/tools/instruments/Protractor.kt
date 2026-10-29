package com.karelisio.canif.tools.instruments

import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.permissions.PermissionGate
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

private enum class TiltRef { HORIZONTAL, VERTICAL, SURFACE }

@Composable
fun ProtractorScreen() {
    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val chooser: @Composable () -> Unit = {
        if (hasCamera) {
            ChoiceRow(listOf(0, 1), mode, label = { if (it == 0) tr("Inclinaison", "Tilt") else tr("Caméra", "Camera") }, onSelect = { mode = it })
        }
    }
    AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "protractor") { m ->
        if (m == 0) {
            ToolColumn { chooser(); TiltProtractor() }
        } else {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                chooser()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    PermissionGate(listOf(AppPermission.CAMERA)) { CameraProtractor() }
                }
            }
        }
    }
}

private fun fold90(a: Float): Float {
    val x = abs(wrap180(a))
    return if (x > 90f) 180f - x else x
}

@Composable
private fun TiltProtractor() {
    LockOrientation()
    val haptics = rememberHaptics()
    val gravity = rememberScreenGravity(0.06f)
    var ref by rememberSaveable { mutableStateOf(TiltRef.HORIZONTAL) }
    var zero by rememberSaveable { mutableStateOf<Float?>(null) }
    var hold by rememberSaveable { mutableStateOf(false) }
    var held by remember { mutableFloatStateOf(90f) }

    fun phi(g: FloatArray) = Math.toDegrees(atan2(g[0], g[1]).toDouble()).toFloat()
    fun beta(g: FloatArray) = Math.toDegrees(atan2(hypot(g[0], g[1]), abs(g[2])).toDouble()).toFloat()
    /** Needle direction on the canvas (0° = right, clockwise), pointing to the ground. */
    fun needleTarget(): Float {
        val g = gravity()
        return if (ref == TiltRef.SURFACE) 90f + beta(g) else Math.toDegrees(atan2(g[1], -g[0]).toDouble()).toFloat()
    }

    val needle = rememberSpringFollower(stiffness = 110f, dampingRatio = 0.42f, angular = true, eps = 0.02f) {
        if (hold) held else needleTarget()
    }
    var reading by remember { mutableFloatStateOf(0f) }
    var flatWarning by remember { mutableStateOf(false) }
    LaunchedEffect(ref, zero, hold) {
        var lastFive = Int.MIN_VALUE
        while (true) {
            if (!hold) {
                val g = gravity()
                val n = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).coerceAtLeast(0.1f)
                flatWarning = ref != TiltRef.SURFACE && abs(g[2]) / n > 0.85f
                val z = zero
                reading = when {
                    z != null && ref == TiltRef.SURFACE -> beta(g) - z
                    z != null -> wrap180(phi(g) - z)
                    ref == TiltRef.HORIZONTAL -> 90f - fold90(phi(g))
                    ref == TiltRef.VERTICAL -> fold90(phi(g))
                    else -> beta(g)
                }
                held = needleTarget()
                val five = (reading / 5f).toInt()
                if (lastFive != Int.MIN_VALUE && five != lastFive) haptics.segment()
                lastFive = five
            }
            delay(70)
        }
    }

    val c = MaterialTheme.colorScheme
    ChoiceRow(
        TiltRef.entries, ref,
        label = { when (it) { TiltRef.HORIZONTAL -> tr("Horizontale", "Horizontal"); TiltRef.VERTICAL -> tr("Verticale", "Vertical"); TiltRef.SURFACE -> tr("À plat", "Flat") } },
        onSelect = { ref = it; zero = null },
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
            ProtractorDial(needle = { needle.floatValue }, hold = hold)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 120.dp)) {
                val v = if (abs(reading) < 0.05f) 0f else reading
                RollingText("%.1f°".format(v), style = MaterialTheme.typography.displayMedium, color = if (hold) c.tertiary else c.onSurface)
                Text(
                    when {
                        zero != null -> tr("Relatif au zéro", "Relative to zero")
                        ref == TiltRef.HORIZONTAL -> tr("par rapport à l'horizontale", "from horizontal")
                        ref == TiltRef.VERTICAL -> tr("par rapport à la verticale", "from vertical")
                        else -> tr("pente de la surface", "surface slope")
                    },
                    style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant,
                )
            }
        }
        if (flatWarning) {
            Text(
                tr("Tenez le téléphone debout, ou choisissez « À plat » pour mesurer une pente.", "Hold the phone upright, or choose “Flat” to measure a slope."),
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { haptics.click(); hold = !hold }, modifier = Modifier.weight(1f)) {
                Icon(if (hold) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null, Modifier.size(18.dp))
                Text("  " + if (hold) tr("Libérer", "Release") else tr("Figer", "Hold"))
            }
            OutlinedButton(
                onClick = {
                    haptics.confirm()
                    if (zero != null) zero = null else {
                        val g = gravity()
                        zero = if (ref == TiltRef.SURFACE) beta(g) else phi(g)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(if (zero != null) Icons.Rounded.RestartAlt else Icons.Rounded.TrackChanges, null, Modifier.size(18.dp))
                Text("  " + if (zero != null) tr("Absolu", "Absolute") else tr("Zéro ici", "Zero here"))
            }
        }
        Text(
            tr(
                "Appuyez un bord du téléphone contre l'objet. « Zéro ici » mesure l'angle entre deux surfaces : posez sur la première, touchez, puis posez sur la seconde.",
                "Press an edge of the phone against the object. “Zero here” measures the angle between two surfaces: place on the first, tap, then on the second.",
            ),
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProtractorDial(needle: () -> Float, hold: Boolean) {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium.copy(color = c.onSurfaceVariant)
    val labels = remember(style) { (0..11).map { i -> val a = i * 30f; a to measurer.measure(fold90(a - 90f).toInt().toString(), style) } }
    val face = c.surfaceContainerHigh
    val rim = c.surfaceContainerHighest
    val tick = c.onSurfaceVariant
    val needleColor by animateColorAsState(if (hold) c.tertiary else c.primary, label = "needle")
    val sector = c.primary.copy(alpha = 0.16f)
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val center = size.center
                onDrawBehind {
                    val R = size.minDimension / 2f
                    val ctr = center
                    drawCircle(rim, R)
                    drawCircle(face, R * 0.93f)
                    for (i in 0 until 360) {
                        val len = when { i % 10 == 0 -> R * 0.1f; i % 5 == 0 -> R * 0.065f; else -> R * 0.035f }
                        val w = if (i % 10 == 0) 2.dp.toPx() else 1.dp.toPx()
                        drawLine(tick.copy(alpha = if (i % 10 == 0) 0.9f else 0.45f), polar(ctr, R * 0.9f, i.toFloat()), polar(ctr, R * 0.9f - len, i.toFloat()), w)
                    }
                    labels.forEach { (a, l) ->
                        val p = polar(ctr, R * 0.68f, a)
                        drawText(l, topLeft = Offset(p.x - l.size.width / 2f, p.y - l.size.height / 2f))
                    }
                    // Phone axes.
                    drawLine(tick.copy(alpha = 0.25f), Offset(ctr.x, ctr.y - R * 0.6f), Offset(ctr.x, ctr.y + R * 0.6f), 1.dp.toPx())
                    drawLine(tick.copy(alpha = 0.25f), Offset(ctr.x - R * 0.6f, ctr.y), Offset(ctr.x + R * 0.6f, ctr.y), 1.dp.toPx())
                    val a = needle()
                    val sweep = wrap180(a - 90f)
                    drawArc(sector, 90f, sweep, true, Offset(ctr.x - R * 0.82f, ctr.y - R * 0.82f), Size(R * 1.64f, R * 1.64f))
                    rotate(a, ctr) {
                        drawLine(needleColor, Offset(ctr.x - R * 0.2f, ctr.y), Offset(ctr.x + R * 0.88f, ctr.y), 4.dp.toPx(), StrokeCap.Round)
                        drawCircle(needleColor, 7.dp.toPx(), Offset(ctr.x + R * 0.88f, ctr.y))
                    }
                    drawCircle(needleColor, 10.dp.toPx(), ctr)
                    drawCircle(face, 4.dp.toPx(), ctr)
                }
            },
    )
}

@Composable
private fun CameraProtractor() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build()
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                provider = future.get()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbind(preview) } }
    }

    // Handles in normalized coordinates: vertex, arm A, arm B.
    var vx by rememberSaveable { mutableFloatStateOf(0.3f) }
    var vy by rememberSaveable { mutableFloatStateOf(0.7f) }
    var ax by rememberSaveable { mutableFloatStateOf(0.85f) }
    var ay by rememberSaveable { mutableFloatStateOf(0.7f) }
    var bx by rememberSaveable { mutableFloatStateOf(0.65f) }
    var by by rememberSaveable { mutableFloatStateOf(0.25f) }
    var active by remember { mutableIntStateOf(-1) }

    val c = MaterialTheme.colorScheme
    val arm = c.primary
    val halo = Color.Black.copy(alpha = 0.35f)
    val handleFill = c.primaryContainer
    val vertexFill = c.tertiaryContainer
    val vertexStroke = c.tertiary
    val arcFill = c.primary.copy(alpha = 0.28f)

    var size by remember { mutableStateOf(Size(1f, 1f)) }
    fun angle(): Float {
        val a1 = atan2((ay - vy) * size.height, (ax - vx) * size.width)
        val a2 = atan2((by - vy) * size.height, (bx - vx) * size.width)
        return abs(wrap180(Math.toDegrees((a2 - a1).toDouble()).toFloat()))
    }
    val theta = angle()
    var lastStep by remember { mutableIntStateOf(-1) }
    LaunchedEffect((theta / 5f).toInt()) {
        val s = (theta / 5f).toInt()
        if (lastStep >= 0 && s != lastStep && active >= 0) haptics.tick()
        lastStep = s
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surfaceContainerHighest),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p ->
                                val w = this.size.width.toFloat(); val h = this.size.height.toFloat()
                                val pts = listOf(vx to vy, ax to ay, bx to by)
                                active = pts.indices.minBy { i -> hypot(pts[i].first * w - p.x, pts[i].second * h - p.y) }
                                haptics.click()
                            },
                            onDragEnd = { active = -1 },
                            onDragCancel = { active = -1 },
                        ) { ch, d ->
                            ch.consume()
                            val w = this.size.width.toFloat(); val h = this.size.height.toFloat()
                            when (active) {
                                0 -> { vx = (vx + d.x / w).coerceIn(0f, 1f); vy = (vy + d.y / h).coerceIn(0f, 1f) }
                                1 -> { ax = (ax + d.x / w).coerceIn(0f, 1f); ay = (ay + d.y / h).coerceIn(0f, 1f) }
                                2 -> { bx = (bx + d.x / w).coerceIn(0f, 1f); by = (by + d.y / h).coerceIn(0f, 1f) }
                            }
                        }
                    },
            ) {
                val v = Offset(vx * this.size.width, vy * this.size.height)
                val a = Offset(ax * this.size.width, ay * this.size.height)
                val b = Offset(bx * this.size.width, by * this.size.height)
                val a1 = Math.toDegrees(atan2(a.y - v.y, a.x - v.x).toDouble()).toFloat()
                val a2 = Math.toDegrees(atan2(b.y - v.y, b.x - v.x).toDouble()).toFloat()
                val r = 56.dp.toPx()
                drawArc(arcFill, a1, wrap180(a2 - a1), true, Offset(v.x - r, v.y - r), Size(2 * r, 2 * r))
                for (end in listOf(a, b)) {
                    drawLine(halo, v, end, 7.dp.toPx(), StrokeCap.Round)
                    drawLine(arm, v, end, 3.5.dp.toPx(), StrokeCap.Round)
                }
                listOf(a, b).forEachIndexed { i, p ->
                    val big = active == i + 1
                    drawCircle(halo, (if (big) 20 else 16).dp.toPx(), p)
                    drawCircle(handleFill, (if (big) 17 else 13).dp.toPx(), p)
                    drawCircle(arm, (if (big) 17 else 13).dp.toPx(), p, style = Stroke(3.dp.toPx()))
                }
                drawCircle(halo, (if (active == 0) 18 else 14).dp.toPx(), v)
                drawCircle(vertexFill, (if (active == 0) 15 else 11).dp.toPx(), v)
                drawCircle(vertexStroke, (if (active == 0) 15 else 11).dp.toPx(), v, style = Stroke(3.dp.toPx()))
            }
        }
        CanifCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(tr("Angle", "Angle"), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                    RollingText("%.1f°".format(theta), style = MaterialTheme.typography.displaySmall, color = c.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(tr("Supplémentaire", "Supplement"), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                    RollingText("%.1f°".format(180f - theta), style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(
                tr("Faites glisser le sommet et les deux bras sur l'image.", "Drag the vertex and both arms over the image."),
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            )
        }
    }
}
