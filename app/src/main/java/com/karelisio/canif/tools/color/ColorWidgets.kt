package com.karelisio.canif.tools.color

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.material3.toPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Hue rainbow used by hue sliders. */
val HueBrushColors: List<Color> = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }

/**
 * Chunky slider with a gradient track and a floating thumb that shows the current color.
 * Haptic ticks every 5 %.
 */
@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    brush: Brush,
    thumbColor: Color,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 40.dp,
) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val onChange by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(if (dragging) 1.18f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "thumb")
    var lastStep by remember { mutableIntStateOf((value * 20).roundToInt()) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(trackHeight)) {
            val widthPx = constraints.maxWidth.toFloat()
            val thumb = trackHeight
            val thumbPx = with(LocalDensity.current) { thumb.toPx() }
            fun set(x: Float) {
                val v = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
                val step = (v * 20).roundToInt()
                if (step != lastStep) { lastStep = step; haptics.segment() }
                onChange(v)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(brush)
                    .border(1.dp, c.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(trackHeight / 2))
                    .pointerInput(widthPx) { detectTapGestures { set(it.x) } }
                    .pointerInput(widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true; set(it.x) },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, _ -> change.consume(); set(change.position.x) }
                    },
            )
            Box(
                Modifier
                    .offset { IntOffset(((widthPx - thumbPx) * value.coerceIn(0f, 1f)).roundToInt(), 0) }
                    .size(thumb)
                    .graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }
                    .padding(3.dp)
                    .border(4.dp, c.surface, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

private val swatchShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny, MaterialShapes.Cookie6Sided, MaterialShapes.Puffy,
)

/**
 * Color sample that slowly breathes between two Material shapes, hops to the next shape when
 * [shapeKey] changes, and blends its color with a spring.
 */
@Composable
fun MorphSwatch(color: Color, modifier: Modifier = Modifier, size: Dp = 96.dp, shapeKey: Any? = null, calm: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val animated by animateColorAsState(color, spring(stiffness = Spring.StiffnessMediumLow), label = "swatch")
    var index by remember { mutableIntStateOf(0) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(shapeKey) {
        if (shapeKey != null && !reduce) {
            index = (index + 1) % swatchShapes.size
            hop.snapTo(0f)
            hop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val from = swatchShapes[(index + swatchShapes.size - 1) % swatchShapes.size]
    val to = swatchShapes[index]
    val morph = remember(from, to) { Morph(from, to) }
    val t = rememberInfiniteTransition(label = "swatch")
    val spin = t.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "spin")
    val breath = t.animateFloat(0.96f, 1.03f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breath")
    val still = reduce || calm
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = if (still) 1f else breath.value
                scaleX = s; scaleY = s
            }
            .morphFill(
                morph,
                progress = { if (shapeKey == null) 1f else hop.value },
                rotation = { if (still) 0f else spin.value },
                color = { animated },
            ),
    )
}

/**
 * Fills the bounds with a morphing Material shape. Everything is read in the draw phase,
 * so a spinning/morphing shape never recomposes.
 */
fun Modifier.morphFill(morph: Morph, progress: () -> Float, rotation: () -> Float = { 0f }, color: () -> Color): Modifier =
    drawWithCache {
        val matrix = Matrix()
        onDrawBehind {
            val path = morph.toPath(progress().coerceIn(0f, 1f))
            matrix.reset()
            val r = rotation()
            if (r != 0f) {
                matrix.translate(size.width / 2, size.height / 2)
                matrix.rotateZ(r)
                matrix.translate(-size.width / 2, -size.height / 2)
            }
            matrix.scale(size.width, size.height)
            path.transform(matrix)
            drawPath(path, color())
        }
    }

/** A tappable row "label / value" that copies the value with an animated check. */
@Composable
fun CopyRow(label: String, value: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }
    val bg by animateColorAsState(if (done) MaterialTheme.colorScheme.primaryContainer else container, label = "bg")
    Surface(
        onClick = {
            copyToClipboard(context, value)
            haptics.confirm()
            done = true
            scope.launch {
                bounce.snapTo(0.94f)
                bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            }
            scope.launch { delay(1300); done = false }
        },
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(done, transitionSpec = {
                scaleIn(spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) togetherWith scaleOut()
            }, label = "copy") { d ->
                Icon(
                    if (d) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    if (d) tr("Copié", "Copied") else tr("Copier", "Copy"),
                    tint = if (d) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Captured colors, newest first (DataStore, max 18). */
object ColorHistory {
    private const val KEY = "color_history"
    private const val MAX = 18

    val colors: Flow<List<Color>> = CanifApp.instance.settings.string(KEY, "").map { s ->
        s.split(',').mapNotNull { ColorMath.parseHex(it) }
    }

    fun add(c: Color) = CanifApp.instance.appScope.launch {
        val store = CanifApp.instance.settings
        val hex = ColorMath.hex(c)
        val cur = store.string(KEY, "").first()
            .split(',').filter { it.isNotBlank() && it != hex }
        store.setString(KEY, (listOf(hex) + cur).take(MAX).joinToString(","))
    }

    fun clear() = CanifApp.instance.appScope.launch { CanifApp.instance.settings.setString(KEY, "") }
}
