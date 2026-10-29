package com.karelisio.canif.tools.instruments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

private enum class LevelMode { AUTO, FLAT, EDGE }

private const val LEVEL_TOLERANCE = 0.3f
private const val LEVEL_EXIT = 0.45f
private const val FLAT_RANGE = 10f
private const val EDGE_RANGE = 8f

private fun deg(r: Float) = Math.toDegrees(r.toDouble()).toFloat()

@Composable
fun LevelScreen() {
    LockOrientation()
    KeepScreenOn()
    val haptics = rememberHaptics()
    val gravity = rememberScreenGravity(0.05f)
    var choice by rememberSaveable { mutableStateOf(LevelMode.AUTO) }
    var autoFlat by remember { mutableStateOf(true) }
    var verticalVial by remember { mutableStateOf(false) }
    val flat = when (choice) { LevelMode.AUTO -> autoFlat; LevelMode.FLAT -> true; LevelMode.EDGE -> false }

    val offX = rememberFloatPref("level.off.fx", 0f)
    val offY = rememberFloatPref("level.off.fy", 0f)
    val offH = rememberFloatPref("level.off.eh", 0f)
    val offV = rememberFloatPref("level.off.ev", 0f)

    // Calibrated angles, computed on demand (no allocation).
    val flatNow by rememberUpdatedState(flat)
    val verticalNow by rememberUpdatedState(verticalVial)
    fun angleX(g: FloatArray) = deg(atan2(g[0], g[2])) - offX.value
    fun angleY(g: FloatArray) = deg(atan2(g[1], g[2])) - offY.value
    fun angleEdge(g: FloatArray, vertical: Boolean) =
        if (vertical) deg(atan2(g[1], abs(g[0]))) - offV.value else deg(atan2(g[0], abs(g[1]))) - offH.value

    // Bubble physics: mass–spring–damper toward the equilibrium point, integrated every frame.
    val bx = remember { mutableFloatStateOf(0f) }
    val by = remember { mutableFloatStateOf(0f) }
    val speed = remember { mutableFloatStateOf(0f) }
    val dir = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var vx = 0f
        var vy = 0f
        var last = 0L
        val k = 95f
        val c = 2f * 0.72f * sqrt(k)
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val g = gravity()
            var tx: Float
            var ty: Float
            if (flatNow) {
                tx = angleX(g) / FLAT_RANGE
                ty = -angleY(g) / FLAT_RANGE
                val m = hypot(tx, ty)
                if (m > 1.15f) { tx = tx / m * 1.15f; ty = ty / m * 1.15f }
            } else if (verticalNow) {
                tx = 0f; ty = (-angleEdge(g, true) / EDGE_RANGE).coerceIn(-1.15f, 1.15f)
            } else {
                tx = (angleEdge(g, false) / EDGE_RANGE).coerceIn(-1.15f, 1.15f); ty = 0f
            }
            var x = bx.floatValue
            var y = by.floatValue
            val n = 4
            val h = dt / n
            repeat(n) {
                vx += (k * (tx - x) - c * vx) * h
                vy += (k * (ty - y) - c * vy) * h
                x += vx * h
                y += vy * h
                // The bubble is held by the vial wall: project back and absorb most of the impact.
                if (flatNow) {
                    val m = hypot(x, y)
                    if (m > 1f) {
                        val nx = x / m
                        val ny = y / m
                        x = nx; y = ny
                        val radial = vx * nx + vy * ny
                        if (radial > 0f) { vx -= 1.3f * radial * nx; vy -= 1.3f * radial * ny }
                    }
                } else {
                    if (x > 1f) { x = 1f; if (vx > 0) vx *= -0.25f }
                    if (x < -1f) { x = -1f; if (vx < 0) vx *= -0.25f }
                    if (y > 1f) { y = 1f; if (vy > 0) vy *= -0.25f }
                    if (y < -1f) { y = -1f; if (vy < 0) vy *= -0.25f }
                }
            }
            bx.floatValue = x
            by.floatValue = y
            speed.floatValue = hypot(vx, vy)
            if (speed.floatValue > 0.02f) dir.floatValue = deg(atan2(vy, vx))
        }
    }

    // Readouts and state, sampled at a readable rate.
    var shownX by remember { mutableFloatStateOf(0f) }
    var shownY by remember { mutableFloatStateOf(0f) }
    var shownEdge by remember { mutableFloatStateOf(0f) }
    var isLevel by remember { mutableStateOf(false) }
    var lying by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            val g = gravity()
            val norm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).coerceAtLeast(0.1f)
            val zr = abs(g[2]) / norm
            if (zr > 0.82f) autoFlat = true else if (zr < 0.55f) autoFlat = false
            lying = zr > 0.6f
            if (abs(g[0]) > abs(g[1]) * 1.25f) verticalVial = true else if (abs(g[1]) > abs(g[0]) * 1.25f) verticalVial = false
            shownX = angleX(g); shownY = angleY(g); shownEdge = angleEdge(g, verticalVial)
            val worst = if (flatNow) maxOf(abs(shownX), abs(shownY)) else abs(shownEdge)
            val nowLevel = if (isLevel) worst < LEVEL_EXIT else worst < LEVEL_TOLERANCE
            if (nowLevel && !isLevel) haptics.confirm()
            isLevel = nowLevel
            delay(80)
        }
    }

    val c = MaterialTheme.colorScheme
    val bubbleColor by animateColorAsState(if (isLevel) c.primary else c.tertiary, spring(stiffness = Spring.StiffnessMediumLow), label = "bubble")
    val targetColor by animateColorAsState(if (isLevel) c.primary else c.outline, label = "target")

    ToolColumn {
        ChoiceRow(
            LevelMode.entries, choice,
            label = { when (it) { LevelMode.AUTO -> tr("Auto", "Auto"); LevelMode.FLAT -> tr("À plat", "Flat"); LevelMode.EDGE -> tr("Sur la tranche", "On edge") } },
            onSelect = { choice = it },
        )
        AnimatedContent(
            targetState = if (flat) 0 else if (verticalVial) 2 else 1,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.9f)) },
            label = "level-mode",
        ) { m ->
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
                when (m) {
                    0 -> FlatVial({ bx.floatValue }, { by.floatValue }, { speed.floatValue }, { dir.floatValue }, { bubbleColor }, { targetColor })
                    else -> EdgeVial(m == 2, { bx.floatValue }, { by.floatValue }, { speed.floatValue }, { bubbleColor }, { targetColor })
                }
            }
        }
        if ((choice == LevelMode.FLAT && !lying) || (choice == LevelMode.EDGE && lying)) {
            Text(
                if (choice == LevelMode.FLAT) tr("Posez le téléphone à plat, écran vers le haut.", "Lay the phone flat, screen up.")
                else tr("Posez le téléphone sur sa tranche.", "Stand the phone on its edge."),
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CanifCard(color = if (isLevel) c.primaryContainer else c.surfaceContainerHigh) {
            if (flat) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AngleReadout(tr("Gauche ↔ droite", "Left ↔ right"), shownX, isLevel)
                    AngleReadout(tr("Avant ↔ arrière", "Front ↔ back"), shownY, isLevel)
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AngleReadout(tr("Inclinaison", "Tilt"), shownEdge, isLevel, big = true)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(isLevel, label = "lvl") { ok ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ok) Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(20.dp).padding(end = 4.dp))
                        Text(
                            if (ok) tr("À niveau", "Level") else tr("Tolérance ±0,3°", "Tolerance ±0.3°"),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ok) c.primary else c.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val calibrated = if (flat) offX.value != 0f || offY.value != 0f else (if (verticalVial) offV.value else offH.value) != 0f
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val g = gravity()
                    if (flat) {
                        offX.set(deg(atan2(g[0], g[2]))); offY.set(deg(atan2(g[1], g[2])))
                    } else if (verticalVial) offV.set(deg(atan2(g[1], abs(g[0]))))
                    else offH.set(deg(atan2(g[0], abs(g[1]))))
                    haptics.confirm()
                },
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)); Text("  " + tr("Calibrer", "Calibrate"))
            }
            OutlinedButton(
                onClick = {
                    if (flat) { offX.set(0f); offY.set(0f) } else if (verticalVial) offV.set(0f) else offH.set(0f)
                    haptics.click()
                },
                enabled = calibrated,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp)); Text("  " + tr("Réinitialiser", "Reset"))
            }
        }
        Text(
            tr(
                "Pour calibrer, posez le téléphone sur une surface parfaitement plane de référence puis touchez « Calibrer ». La correction est mémorisée pour chaque mode.",
                "To calibrate, put the phone on a reference flat surface and tap “Calibrate”. The correction is remembered for each mode.",
            ),
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
        )
    }
}

@Composable
private fun AngleReadout(label: String, value: Float, level: Boolean, big: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        val v = if (abs(value) < 0.05f) 0f else value
        RollingText(
            "%.1f°".format(v),
            style = if (big) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
            color = if (level) c.primary else c.onSurface,
        )
    }
}

/** Bullseye vial: 2D bubble in a domed disc. */
@Composable
private fun FlatVial(
    x: () -> Float, y: () -> Float, speed: () -> Float, dir: () -> Float,
    bubble: () -> Color, target: () -> Color,
) {
    val c = MaterialTheme.colorScheme
    val housing = c.surfaceContainerHighest
    val liquidIn = c.primaryContainer.copy(alpha = 0.55f)
    val liquidOut = c.primaryContainer
    val ring = c.outlineVariant
    val cross = c.outline.copy(alpha = 0.45f)
    val highlight = Color.White.copy(alpha = 0.55f)
    val labelColor = c.onSurfaceVariant
    Spacer(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawWithCache {
                val center = size.center
                val r = size.minDimension / 2f
                val liquid = Brush.radialGradient(listOf(liquidIn, liquidOut), center, r)
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                onDrawBehind {
                    drawCircle(housing, r)
                    drawCircle(liquid, r * 0.92f)
                    val R = r * 0.92f
                    val rb = R * 0.17f
                    val travel = R - rb - 4.dp.toPx()
                    // Angle rings (2°, 5°) and crosshair.
                    for (a in listOf(2f, 5f)) drawCircle(ring, rb + travel * a / FLAT_RANGE, style = Stroke(1.5.dp.toPx()))
                    drawLine(cross, Offset(center.x - R, center.y), Offset(center.x + R, center.y), 1.dp.toPx(), pathEffect = dash)
                    drawLine(cross, Offset(center.x, center.y - R), Offset(center.x, center.y + R), 1.dp.toPx(), pathEffect = dash)
                    drawCircle(labelColor.copy(alpha = 0.25f), R, style = Stroke(2.dp.toPx()))
                    // Bubble (deformed along its velocity).
                    val p = Offset(center.x + x() * travel, center.y + y() * travel)
                    val s = (speed() * 0.09f).coerceAtMost(0.22f)
                    val col = bubble()
                    withTransform({
                        rotate(dir(), p)
                        scale(1f + s, 1f - s * 0.55f, p)
                    }) {
                        drawCircle(Color.Black.copy(alpha = 0.10f), rb * 1.06f, p + Offset(0f, 3.dp.toPx()))
                        drawCircle(col.copy(alpha = 0.92f), rb, p)
                        drawCircle(col, rb, p, style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(highlight, rb * 0.32f, p + Offset(-rb * 0.32f, -rb * 0.36f))
                    // Target ring on top.
                    drawCircle(target(), rb + 5.dp.toPx(), style = Stroke(3.dp.toPx()))
                }
            },
    )
}

/** Tubular vial for edge mode, horizontal or vertical. */
@Composable
private fun EdgeVial(
    vertical: Boolean, x: () -> Float, y: () -> Float, speed: () -> Float,
    bubble: () -> Color, target: () -> Color,
) {
    val c = MaterialTheme.colorScheme
    val housing = c.surfaceContainerHighest
    val liquidA = c.primaryContainer.copy(alpha = 0.6f)
    val liquidB = c.primaryContainer
    val tick = c.outline
    val highlight = Color.White.copy(alpha = 0.5f)
    Spacer(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawWithCache {
                val center = size.center
                val long = size.minDimension * 0.94f
                val thick = size.minDimension * 0.2f
                val tubeSize = if (vertical) Size(thick, long) else Size(long, thick)
                val tl = Offset(center.x - tubeSize.width / 2, center.y - tubeSize.height / 2)
                val inner = 8.dp.toPx()
                val innerSize = Size(tubeSize.width - 2 * inner, tubeSize.height - 2 * inner)
                val innerTl = tl + Offset(inner, inner)
                val liquid = if (vertical) Brush.horizontalGradient(listOf(liquidB, liquidA, liquidB), innerTl.x, innerTl.x + innerSize.width)
                else Brush.verticalGradient(listOf(liquidB, liquidA, liquidB), innerTl.y, innerTl.y + innerSize.height)
                onDrawBehind {
                    drawRoundRect(housing, tl, tubeSize, CornerRadius(thick / 2))
                    drawRoundRect(liquid, innerTl, innerSize, CornerRadius((thick - 2 * inner) / 2))
                    val bubbleLen = thick * 1.5f
                    val bubbleThick = (thick - 2 * inner) * 0.7f
                    val travel = (long - 2 * inner) / 2f - bubbleLen / 2f - 4.dp.toPx()
                    // Degree graduations, placed where the bubble ends sit at each degree.
                    val markOff = bubbleLen / 2f + 3.dp.toPx()
                    for (i in 1..EDGE_RANGE.toInt()) {
                        val d = markOff + travel * i / EDGE_RANGE
                        val len = if (i % 5 == 0) thick * 0.24f else thick * 0.13f
                        drawTick(vertical, d, len, thick, inner, tick.copy(alpha = 0.6f))
                        drawTick(vertical, -d, len, thick, inner, tick.copy(alpha = 0.6f))
                    }
                    // Bubble capsule.
                    val pos = if (vertical) Offset(center.x, center.y + y() * travel) else Offset(center.x + x() * travel, center.y)
                    val s = (speed() * 0.07f).coerceAtMost(0.18f)
                    val bl = bubbleLen * (1f + s)
                    val bt = bubbleThick * (1f - s * 0.4f)
                    val bs = if (vertical) Size(bt, bl) else Size(bl, bt)
                    val col = bubble()
                    drawRoundRect(col.copy(alpha = 0.92f), Offset(pos.x - bs.width / 2, pos.y - bs.height / 2), bs, CornerRadius(bt / 2))
                    val hs = if (vertical) Size(bt * 0.25f, bl * 0.55f) else Size(bl * 0.55f, bt * 0.25f)
                    val hp = if (vertical) Offset(pos.x - bt * 0.28f, pos.y - hs.height / 2) else Offset(pos.x - hs.width / 2, pos.y - bt * 0.28f)
                    drawRoundRect(highlight, hp - Offset(if (vertical) hs.width / 2 else 0f, if (vertical) 0f else hs.height / 2), hs, CornerRadius(minOf(hs.width, hs.height) / 2))
                    // Center marks.
                    val tc = target()
                    for (sgn in listOf(-1f, 1f)) {
                        if (vertical) drawLine(tc, Offset(center.x - thick / 2 + inner, center.y + sgn * markOff), Offset(center.x + thick / 2 - inner, center.y + sgn * markOff), 3.dp.toPx(), StrokeCap.Round)
                        else drawLine(tc, Offset(center.x + sgn * markOff, center.y - thick / 2 + inner), Offset(center.x + sgn * markOff, center.y + thick / 2 - inner), 3.dp.toPx(), StrokeCap.Round)
                    }
                }
            },
    )
}

private fun DrawScope.drawTick(vertical: Boolean, d: Float, len: Float, thick: Float, inner: Float, color: Color) {
    val w = 1.5.dp.toPx()
    if (vertical) {
        val y = center.y + d
        drawLine(color, Offset(center.x - thick / 2 + inner, y), Offset(center.x - thick / 2 + inner + len, y), w)
        drawLine(color, Offset(center.x + thick / 2 - inner, y), Offset(center.x + thick / 2 - inner - len, y), w)
    } else {
        val x = center.x + d
        drawLine(color, Offset(x, center.y - thick / 2 + inner), Offset(x, center.y - thick / 2 + inner + len), w)
        drawLine(color, Offset(x, center.y + thick / 2 - inner), Offset(x, center.y + thick / 2 - inner - len), w)
    }
}
