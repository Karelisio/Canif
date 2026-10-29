package com.karelisio.canif.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Geometry of the Canif knife, in the 108×108 adaptive-icon viewport.
 * Each tool is drawn closed (horizontal, hidden under the handle) and rotates around the pivot.
 * The same paths are used by the launcher icon (res/drawable/ic_launcher_foreground.xml).
 */
object KnifeGeometry {
    const val VIEWPORT = 108f
    val pivot = Offset(33f, 66f)
    const val HANDLE = "M33,57 H75 A9,9 0 0 1 75,75 H33 A9,9 0 0 1 33,57 Z"
    /** Blade, screwdriver, bottle opener — with their unfolded angles. */
    val tools = listOf(
        "M33,61 H75 C79,61 84,63 85,66 C77,69.5 58,71 33,71 Z" to -52f,
        "M33,63 H76 A3,3 0 0 1 76,69 H33 Z" to -32f,
        "M33,62 H66 C70,62 73,64 73,66.5 C73,69 70,70.5 67,70.5 L64,68 H33 Z" to -13f,
    )
}

private val handlePath by lazy { PathParser().parsePathString(KnifeGeometry.HANDLE).toPath() }
private val toolPaths by lazy { KnifeGeometry.tools.map { PathParser().parsePathString(it.first).toPath() to it.second } }

/** The knife drawn with theme colors. [progress] holds the unfold progress (0 closed, 1 open, may overshoot) of each tool. */
@Composable
fun KnifeLogo(progress: List<Float>, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val c = MaterialTheme.colorScheme
    val toolColors = listOf(c.primary, c.secondary, c.outline)
    val handle = c.tertiary
    val rivet = c.onTertiary
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / KnifeGeometry.VIEWPORT
        scale(s, s, pivot = Offset.Zero) {
            toolPaths.forEachIndexed { i, (path, angle) ->
                rotate(angle * progress.getOrElse(i) { 1f }, pivot = KnifeGeometry.pivot) {
                    drawPath(path, toolColors[i % toolColors.size])
                }
            }
            drawPath(handlePath, handle)
            drawCircle(rivet, 3f, KnifeGeometry.pivot)
            drawCircle(rivet, 2.4f, Offset(75f, 66f))
        }
    }
}

/** Unfolds the knife tool by tool with springs. */
@Composable
fun AnimatedKnifeLogo(modifier: Modifier = Modifier, size: Dp = 96.dp, startDelayMs: Long = 0, staggerMs: Long = 110) {
    val reduce = LocalReduceMotion.current
    val anims = remember { List(KnifeGeometry.tools.size) { Animatable(if (reduce) 1f else 0f) } }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(startDelayMs)
        coroutineScope {
            anims.mapIndexed { i, a ->
                async {
                    delay(i * staggerMs)
                    a.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f))
                }
            }.awaitAll()
        }
    }
    KnifeLogo(anims.map { it.value }, modifier, size)
}

/**
 * In-app continuation of the system splash: the closed knife unfolds its tools one by one,
 * then the overlay lifts away to reveal the home screen.
 */
@Composable
fun KnifeIntro(onFinished: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val anims = remember { List(KnifeGeometry.tools.size) { Animatable(0f) } }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduce) {
            onFinished(); return@LaunchedEffect
        }
        coroutineScope {
            anims.mapIndexed { i, a ->
                async {
                    delay(80L + i * 120L)
                    a.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 240f))
                }
            }.awaitAll()
        }
        exit.animateTo(1f, tween(320))
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        KnifeLogo(
            anims.map { it.value },
            Modifier.graphicsLayer {
                val sc = 1f + exit.value * 0.25f
                scaleX = sc; scaleY = sc
            },
            size = 240.dp,
        )
    }
}

/** Fan-out entrance used when opening a category: the tile swings out of a hinge like a knife tool. */
fun Modifier.fanUnfold(progress: Float, angle: Float = -64f): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0.08f, 0.92f)
    rotationZ = (1f - progress) * angle
    alpha = progress.coerceIn(0f, 1f)
    val sc = 0.55f + 0.45f * progress
    scaleX = sc; scaleY = sc
}

