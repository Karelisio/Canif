package com.karelisio.canif.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.canif.ui.theme.LocalReduceMotion

/** A living blob: slowly morphs between two Material shapes while rotating. */
@Composable
fun MorphingBlob(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    from: RoundedPolygon = MaterialShapes.Cookie9Sided,
    to: RoundedPolygon = MaterialShapes.Flower,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    periodMs: Int = 2600,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val morph = remember(from, to) { Morph(from, to) }
    val t = rememberInfiniteTransition(label = "blob")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs), RepeatMode.Reverse), label = "p")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs * 6, easing = LinearEasing)), label = "r")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(MorphShape(morph, if (reduce) 0f else p, if (reduce) 0f else r))
                .background(color),
        )
        content()
    }
}

@Composable
fun BlobIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 160.dp, color: Color = MaterialTheme.colorScheme.primaryContainer, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    MorphingBlob(modifier, size, color = color) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.38f))
    }
}

/** Check mark drawn progressively (0..1), used when a permission gets granted. */
@Composable
fun AnimatedCheck(visible: Boolean, modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = MaterialTheme.colorScheme.primary, onColor: Color = MaterialTheme.colorScheme.onPrimary) {
    val p by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 520 else 150), label = "check")
    if (p == 0f) return
    Canvas(modifier.size(size)) {
        drawCircle(color, radius = this.size.minDimension / 2 * (0.6f + 0.4f * p.coerceAtMost(1f)), alpha = p.coerceIn(0f, 1f))
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.28f, this@Canvas.size.height * 0.52f)
            lineTo(this@Canvas.size.width * 0.44f, this@Canvas.size.height * 0.67f)
            lineTo(this@Canvas.size.width * 0.73f, this@Canvas.size.height * 0.36f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        measure.getSegment(0f, measure.length * ((p - 0.3f) / 0.7f).coerceIn(0f, 1f), seg, true)
        drawPath(seg, onColor, style = Stroke(width = this.size.minDimension * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Suppress("unused")
private val Zero = Offset.Zero
