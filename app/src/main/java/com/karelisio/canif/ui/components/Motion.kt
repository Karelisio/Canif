package com.karelisio.canif.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.toPath
import com.karelisio.canif.ui.theme.LocalAppSettings
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** A shape that morphs between two normalized Material polygons. */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        val m = Matrix()
        if (rotation != 0f) {
            m.translate(size.width / 2, size.height / 2)
            m.rotateZ(rotation)
            m.translate(-size.width / 2, -size.height / 2)
        }
        m.scale(size.width, size.height)
        path.transform(m)
        return Outline.Generic(path)
    }
}

/** A plain normalized polygon used as a shape (no morph). */
class PolygonShape(private val polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    private val morph = Morph(polygon, polygon)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        MorphShape(morph, 0f, rotation).createOutline(size, layoutDirection, density)
}

/** Subtle haptic vocabulary, honoring the in-app "haptics" setting. */
class Haptics(private val view: View, private val enabled: Boolean) {
    private fun perform(c: Int) {
        if (enabled) view.performHapticFeedback(c)
    }
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun click() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun segment() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    val isEnabled get() = enabled
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalAppSettings.current.haptics
    return remember(view, enabled) { Haptics(view, enabled) }
}

/**
 * Animated numeric text: every character rolls vertically to its new value.
 * Characters are keyed from the right so units stay aligned while the number grows.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    if (LocalReduceMotion.current) {
        Text(text, modifier, style = style, color = color)
        return
    }
    Row(modifier) {
        val n = text.length
        text.forEachIndexed { i, c ->
            key(n - i) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { if (up) it else -it } + fadeIn())
                            .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
                            .using(SizeTransform(clip = true))
                    },
                    label = "digit",
                ) { ch -> Text(ch.toString(), style = style, color = color) }
            }
        }
    }
}

/** Keeps track of items that already played their entrance, so the cascade only runs once per screen. */
class CascadeState {
    internal val played = HashSet<Any>()
}

val LocalCascade = staticCompositionLocalOf { CascadeState() }

/** Staggered entrance (fade + rise + scale) used by grids and lists. */
@Composable
fun Modifier.cascade(key: Any, index: Int): Modifier {
    val state = LocalCascade.current
    val reduce = LocalReduceMotion.current
    val already = remember(key) { reduce || key in state.played }
    val anim = remember(key) { Animatable(if (already) 1f else 0f) }
    LaunchedEffect(key) {
        if (!already) {
            state.played += key
            delay(28L * index.coerceAtMost(14))
            anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        }
    }
    return graphicsLayer {
        val p = anim.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 48f.dp(this)
        scaleX = 0.92f + 0.08f * p
        scaleY = 0.92f + 0.08f * p
    }
}

private fun Float.dp(d: Density) = this * d.density
