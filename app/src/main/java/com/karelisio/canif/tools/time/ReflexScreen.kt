package com.karelisio.canif.tools.time

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.MorphShape
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class ReflexPhase { IDLE, WAITING, GO, RESULT, EARLY, SUMMARY }

private const val ATTEMPTS = 5
private const val BEST_KEY = "reflex_best"

@Composable
fun ReflexScreen(onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val settings = CanifApp.instance.settings
    val best by remember { settings.long(BEST_KEY, 0L) }.collectAsStateWithLifecycle(0L)

    var phase by remember { mutableStateOf(ReflexPhase.IDLE) }
    var round by remember { mutableIntStateOf(0) } // increments each new wait, restarts the wait effect
    var goAt by remember { mutableLongStateOf(0L) }
    var last by remember { mutableLongStateOf(0L) }
    var newRecord by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<Long>() }
    val shown = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }

    val waitMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided) }
    val goMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val goP = remember { Animatable(0f) }

    // Random wait, then the screen flips abruptly.
    LaunchedEffect(round) {
        if (phase != ReflexPhase.WAITING) return@LaunchedEffect
        goP.snapTo(0f)
        delay(Random.nextLong(1500, 5000))
        if (phase != ReflexPhase.WAITING) return@LaunchedEffect
        goAt = 0L
        phase = ReflexPhase.GO
        // Time origin = start of the frame that draws the change.
        withFrameNanos { }
        goAt = SystemClock.uptimeMillis()
        if (!reduce) goP.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) else goP.snapTo(1f)
    }

    fun startWait() {
        phase = ReflexPhase.WAITING
        round++
    }

    fun onTap(downAt: Long) {
        when (phase) {
            ReflexPhase.IDLE, ReflexPhase.RESULT, ReflexPhase.EARLY -> {
                haptics.click()
                startWait()
            }
            ReflexPhase.SUMMARY -> {
                haptics.click()
                results.clear()
                newRecord = false
                startWait()
            }
            ReflexPhase.WAITING -> {
                haptics.reject()
                phase = ReflexPhase.EARLY
                scope.launch {
                    shake.snapTo(0f)
                    shake.animateTo(0f, keyframes {
                        durationMillis = 420
                        -18f at 60
                        16f at 130
                        -12f at 200
                        8f at 270
                        -4f at 340
                    })
                }
            }
            ReflexPhase.GO -> {
                if (goAt == 0L) { // Tap before the change was even on screen.
                    haptics.reject()
                    phase = ReflexPhase.EARLY
                    return
                }
                val ms = (downAt - goAt).coerceAtLeast(0)
                last = ms
                results.add(ms)
                haptics.confirm()
                if (best == 0L || ms < best) {
                    newRecord = true
                    scope.launch { settings.setLong(BEST_KEY, ms) }
                }
                phase = if (results.size >= ATTEMPTS) ReflexPhase.SUMMARY else ReflexPhase.RESULT
                scope.launch {
                    shown.snapTo(0f)
                    shown.animateTo(
                        (if (phase == ReflexPhase.SUMMARY) results.average().toLong() else ms).toFloat(),
                        tween(if (reduce) 0 else 650, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    val bg = when (phase) {
        ReflexPhase.IDLE, ReflexPhase.SUMMARY -> c.surface
        ReflexPhase.WAITING -> c.secondaryContainer
        ReflexPhase.GO -> c.tertiary
        ReflexPhase.RESULT -> c.primaryContainer
        ReflexPhase.EARLY -> c.errorContainer
    }
    val fg = when (phase) {
        ReflexPhase.IDLE, ReflexPhase.SUMMARY -> c.onSurface
        ReflexPhase.WAITING -> c.onSecondaryContainer
        ReflexPhase.GO -> c.onTertiary
        ReflexPhase.RESULT -> c.onPrimaryContainer
        ReflexPhase.EARLY -> c.onErrorContainer
    }
    val blob = when (phase) {
        ReflexPhase.IDLE, ReflexPhase.SUMMARY -> c.primaryContainer
        ReflexPhase.WAITING -> c.secondary
        ReflexPhase.GO -> c.onTertiary
        ReflexPhase.RESULT -> c.primary
        ReflexPhase.EARLY -> c.error
    }
    val inf = rememberInfiniteTransition(label = "wait")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breathe")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "spin")

    Box(Modifier.fillMaxSize()) {
        // Whole-screen tap area. The color change is instant on purpose (fair measurement).
        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onTap(down.uptimeMillis)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .systemBarsPadding()
                    .padding(24.dp)
                    .widthIn(max = 480.dp)
                    .graphicsLayer { translationX = shake.value * density },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val shape = when (phase) {
                        ReflexPhase.GO -> MorphShape(goMorph, goP.value, if (reduce) 0f else goP.value * 45f)
                        ReflexPhase.WAITING -> MorphShape(waitMorph, if (reduce) 0f else breathe, if (reduce) 0f else spin)
                        else -> MorphShape(waitMorph, 0f, 0f)
                    }
                    Box(
                        Modifier
                            .size(200.dp)
                            .graphicsLayer {
                                val s = if (phase == ReflexPhase.GO) 0.8f + 0.2f * goP.value else 1f
                                scaleX = s; scaleY = s
                            }
                            .clip(shape)
                            .background(blob),
                    )
                    Icon(
                        when (phase) {
                            ReflexPhase.IDLE -> Icons.Rounded.TouchApp
                            ReflexPhase.WAITING -> Icons.Rounded.HourglassTop
                            ReflexPhase.GO -> Icons.Rounded.Bolt
                            ReflexPhase.RESULT -> Icons.Rounded.Bolt
                            ReflexPhase.EARLY -> Icons.Rounded.PanTool
                            ReflexPhase.SUMMARY -> Icons.Rounded.EmojiEvents
                        },
                        null,
                        tint = when (phase) {
                            ReflexPhase.GO -> c.tertiary
                            ReflexPhase.IDLE, ReflexPhase.SUMMARY -> c.onPrimaryContainer
                            ReflexPhase.WAITING -> c.onSecondary
                            ReflexPhase.RESULT -> c.onPrimary
                            ReflexPhase.EARLY -> c.onError
                        },
                        modifier = Modifier.size(72.dp),
                    )
                }

                AnimatedContent(
                    phase,
                    transitionSpec = {
                        if (targetState == ReflexPhase.GO || initialState == ReflexPhase.GO) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else (fadeIn() + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.9f)) togetherWith fadeOut()
                    },
                    label = "texts",
                ) { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (p) {
                            ReflexPhase.IDLE -> {
                                Title(tr("Test de réflexes", "Reflex test"), fg)
                                Body(tr("Touchez l'écran pour commencer. Attendez le changement de couleur, puis touchez le plus vite possible. $ATTEMPTS essais.", "Tap to start. Wait for the color to change, then tap as fast as you can. $ATTEMPTS tries."), fg)
                            }
                            ReflexPhase.WAITING -> {
                                Title(tr("Attendez…", "Wait…"), fg)
                                Body(tr("Ne touchez pas encore", "Don't tap yet"), fg)
                            }
                            ReflexPhase.GO -> Title(tr("Touchez !", "Tap!"), fg, big = true)
                            ReflexPhase.EARLY -> {
                                Title(tr("Trop tôt !", "Too soon!"), fg)
                                Body(tr("Touchez pour réessayer", "Tap to try again"), fg)
                            }
                            ReflexPhase.RESULT -> {
                                Title("${shown.value.toInt()} ms", fg, big = true)
                                Body(tr("Essai ${results.size}/$ATTEMPTS · touchez pour continuer", "Try ${results.size}/$ATTEMPTS · tap to continue"), fg)
                            }
                            ReflexPhase.SUMMARY -> {
                                Body(tr("Moyenne", "Average"), fg)
                                Title("${shown.value.toInt()} ms", fg, big = true)
                                Body(
                                    tr("Meilleur essai : ${results.minOrNull() ?: 0} ms", "Best try: ${results.minOrNull() ?: 0} ms"),
                                    fg,
                                )
                                if (newRecord) Body(tr("Nouveau record !", "New record!"), c.tertiary)
                                Body(tr("Touchez pour rejouer", "Tap to play again"), fg)
                            }
                        }
                    }
                }

                // Attempt dots.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(ATTEMPTS) { i ->
                        val done = i < results.size
                        Box(
                            Modifier
                                .size(if (done) 14.dp else 10.dp)
                                .clip(CircleShape)
                                .background(if (done) fg else fg.copy(alpha = 0.25f)),
                        )
                    }
                }
                if (best > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Record : $best ms", "Record: $best ms"),
                        style = MaterialTheme.typography.labelLarge,
                        color = fg.copy(alpha = 0.75f),
                    )
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .systemBarsPadding()
                .padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = fg),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour", "Back"))
        }
    }
}

@Composable
private fun Title(text: String, color: androidx.compose.ui.graphics.Color, big: Boolean = false) {
    Text(
        text,
        style = if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Body(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color.copy(alpha = 0.85f), textAlign = TextAlign.Center)
}
