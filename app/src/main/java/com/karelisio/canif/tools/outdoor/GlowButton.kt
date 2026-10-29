package com.karelisio.canif.tools.outdoor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.canif.tools.color.morphFill
import com.karelisio.canif.ui.theme.LocalReduceMotion

/**
 * Huge expressive switch: an M3 shape that morphs into a circle when [on], with a living halo.
 * [glow] (0..1) scales the halo, e.g. with the torch strength or a blinking signal.
 */
@Composable
fun GlowButton(
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    offShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    onShape: RoundedPolygon = MaterialShapes.Circle,
    glow: Float = 1f,
    onColor: Color = MaterialTheme.colorScheme.primary,
    offColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val morph = remember(offShape, onShape) { Morph(offShape, onShape) }
    val progress = animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow), label = "morph")
    val color = animateColorAsState(if (on) onColor else offColor, spring(stiffness = Spring.StiffnessMediumLow), label = "color")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "press")
    val halo = animateFloatAsState(if (on) glow.coerceIn(0f, 1f) else 0f, spring(stiffness = Spring.StiffnessLow), label = "halo")

    val t = rememberInfiniteTransition(label = "halo")
    val wave = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wave")
    val spin = t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "spin")

    Box(modifier.size(size * 1.6f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val h = halo.value
            if (h <= 0.01f) return@Canvas
            val base = size.toPx() / 2
            val maxR = this.size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    listOf(onColor.copy(alpha = 0.45f * h), onColor.copy(alpha = 0.12f * h), Color.Transparent),
                    center = center,
                    radius = maxR,
                ),
                radius = maxR,
            )
            if (!reduce) {
                for (k in 0 until 3) {
                    val p = (wave.value + k / 3f) % 1f
                    val r = base + (maxR - base) * p
                    drawCircle(onColor.copy(alpha = (1f - p) * 0.5f * h), radius = r, style = Stroke(width = (3f + 5f * (1f - p)).dp.toPx()))
                }
            }
        }
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = press.value; scaleY = press.value }
                .morphFill(
                    morph,
                    progress = { progress.value },
                    rotation = { if (reduce) 0f else spin.value * (1f - progress.value.coerceIn(0f, 1f)) },
                    color = { color.value },
                )
                .clickable(interactionSource = interaction, indication = null, role = Role.Switch, onClickLabel = contentDescription) { onClick() },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
