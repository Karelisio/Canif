package com.karelisio.canif.tools.outdoor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.tools.color.GradientSlider
import com.karelisio.canif.tools.color.HueBrushColors
import com.karelisio.canif.ui.components.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private class LightPreset(val name: Txt, val hue: Float, val sat: Float, val intensity: Float)

private val presets = listOf(
    LightPreset(Txt("Blanc", "White"), 0f, 0f, 1f),
    LightPreset(Txt("Blanc chaud", "Warm white"), 32f, 0.32f, 1f),
    LightPreset(Txt("Bougie", "Candle"), 26f, 0.75f, 0.9f),
    LightPreset(Txt("Rouge nuit", "Night red"), 0f, 1f, 0.55f),
    LightPreset(Txt("Ambre", "Amber"), 40f, 1f, 1f),
    LightPreset(Txt("Vert", "Green"), 130f, 0.8f, 0.9f),
    LightPreset(Txt("Bleu froid", "Cool blue"), 205f, 0.25f, 1f),
    LightPreset(Txt("Rose", "Pink"), 330f, 0.45f, 1f),
)

@Composable
fun ToolScope.ScreenLightScreen() {
    val haptics = rememberHaptics()
    val settings = CanifApp.instance.settings
    var hue by remember { mutableFloatStateOf(32f) }
    var sat by remember { mutableFloatStateOf(0.32f) }
    var intensity by remember { mutableFloatStateOf(1f) }
    var controls by remember { mutableStateOf(true) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        hue = settings.float("screenlight_hue", 32f).first()
        sat = settings.float("screenlight_sat", 0.32f).first()
        intensity = settings.float("screenlight_intensity", 1f).first()
    }
    DisposableEffect(Unit) {
        onDispose {
            val h = hue; val s = sat; val i = intensity
            CanifApp.instance.appScope.launch {
                settings.setFloat("screenlight_hue", h); settings.setFloat("screenlight_sat", s); settings.setFloat("screenlight_intensity", i)
            }
        }
    }
    LaunchedEffect(hudTick) {
        if (hud != null) { delay(900); hud = null }
    }

    MaxBrightness()
    ImmersiveMode()
    BackHandler { onBack() }

    val light = Color.hsv(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), (0.04f + 0.96f * intensity).coerceIn(0f, 1f))
    val onLight = if (intensity > 0.5f && sat < 0.6f) Color.Black else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(light) }
            .pointerInput(Unit) { detectTapGestures { haptics.click(); controls = !controls } }
            .pointerInput(Unit) {
                var axis = 0 // 0 undecided, 1 vertical, 2 horizontal
                var accX = 0f; var accY = 0f
                var lastStep = -1
                detectDragGestures(
                    onDragStart = { axis = 0; accX = 0f; accY = 0f },
                ) { change, drag ->
                    change.consume()
                    accX += drag.x; accY += drag.y
                    if (axis == 0 && (abs(accX) > 24f || abs(accY) > 24f)) axis = if (abs(accY) >= abs(accX)) 1 else 2
                    when (axis) {
                        1 -> {
                            intensity = (intensity - drag.y / (size.height * 0.75f)).coerceIn(0f, 1f)
                            val step = (intensity * 20).roundToInt()
                            if (step != lastStep) { lastStep = step; haptics.segment() }
                            hud = tr("Intensité", "Brightness") + " ${(intensity * 100).roundToInt()} %"
                        }
                        2 -> {
                            if (sat < 0.5f) sat = 0.85f
                            hue = ((hue + drag.x / size.width * 240f) % 360f + 360f) % 360f
                            val step = (hue / 15f).toInt()
                            if (step != lastStep) { lastStep = step; haptics.segment() }
                            hud = tr("Teinte", "Hue") + " ${hue.roundToInt()}°"
                        }
                    }
                    hudTick++
                }
            },
    ) {
        // Drag feedback
        val hudAlpha by animateFloatAsState(if (hud != null) 1f else 0f, label = "hud")
        if (hudAlpha > 0.01f) {
            Surface(
                shape = CircleShape,
                color = onLight.copy(alpha = 0.16f),
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = hudAlpha; scaleX = 0.9f + 0.1f * hudAlpha; scaleY = 0.9f + 0.1f * hudAlpha },
            ) {
                Text(hud.orEmpty(), color = onLight, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }

        AnimatedVisibility(
            controls,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Row(Modifier.safeDrawingPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { haptics.click(); onBack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour", "Back"))
                }
                Spacer(Modifier.width(12.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SwipeVertical, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("↕ intensité · ↔ teinte · touchez pour masquer", "↕ brightness · ↔ hue · tap to hide"),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            controls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                modifier = Modifier.safeDrawingPadding().padding(12.dp).widthIn(max = 560.dp).fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                        items(presets) { p ->
                            val col = Color.hsv(p.hue, p.sat, 0.04f + 0.96f * p.intensity)
                            val selected = abs(p.hue - hue) < 1f && abs(p.sat - sat) < 0.02f && abs(p.intensity - intensity) < 0.02f
                            val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.45f), label = "preset")
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(if (selected) 16.dp else 26.dp))
                                        .background(col)
                                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(if (selected) 16.dp else 26.dp))
                                        .pointerInput(p) {
                                            detectTapGestures {
                                                haptics.confirm()
                                                hue = p.hue; sat = p.sat; intensity = p.intensity
                                            }
                                        },
                                )
                                Text(p.name.text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientSlider(hue / 360f, { hue = it * 360f; if (sat < 0.05f) sat = 0.6f }, Brush.horizontalGradient(HueBrushColors), Color.hsv(hue % 360f, 1f, 1f), tr("Teinte", "Hue"), "${hue.roundToInt()}°", trackHeight = 34.dp)
                        GradientSlider(sat, { sat = it }, Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue % 360f, 1f, 1f))), light, tr("Saturation", "Saturation"), "${(sat * 100).roundToInt()} %", trackHeight = 34.dp)
                        GradientSlider(intensity, { intensity = it }, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue % 360f, sat, 1f))), light, tr("Intensité", "Brightness"), "${(intensity * 100).roundToInt()} %", trackHeight = 34.dp)
                    }
                }
            }
        }
    }
}
