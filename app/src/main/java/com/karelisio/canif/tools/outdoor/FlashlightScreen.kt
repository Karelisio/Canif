package com.karelisio.canif.tools.outdoor

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.rememberHaptics
import kotlin.math.roundToInt

@Composable
fun ToolScope.FlashlightScreen() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    val on by Torch.isOn.collectAsStateWithLifecycle()
    val level by Torch.level.collectAsStateWithLifecycle()
    val maxLevel = remember { Torch.maxLevel(context) }
    var chosen by remember { mutableIntStateOf(Torch.defaultLevel(context).coerceAtLeast(1)) }
    var sliderValue by remember { mutableFloatStateOf(chosen.toFloat()) }

    DisposableEffect(Unit) { onDispose { if (Torch.isOn.value) Torch.off(context) } }

    fun toggle() {
        val ok = if (on) Torch.off(context) else if (maxLevel > 1) Torch.setLevel(context, chosen) else Torch.on(context)
        if (ok) haptics.confirm() else {
            haptics.reject()
            Toast.makeText(context, tr("Lampe indisponible (caméra utilisée ?)", "Torch unavailable (camera in use?)"), Toast.LENGTH_SHORT).show()
        }
    }

    val glow = if (maxLevel > 1) 0.35f + 0.65f * (level.toFloat() / maxLevel) else 1f

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            GlowButton(
                on = on,
                onClick = { toggle() },
                size = 210.dp,
                offShape = MaterialShapes.Cookie9Sided,
                glow = glow,
                contentDescription = tr("Allumer ou éteindre", "Turn on or off"),
            ) {
                val tint by animateColorAsState(if (on) c.onPrimary else c.onSurfaceVariant, label = "tint")
                AnimatedContent(on, transitionSpec = {
                    scaleIn(spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f) togetherWith scaleOut(targetScale = 0.6f)
                }, label = "icon") { lit ->
                    Icon(if (lit) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, null, tint = tint, modifier = Modifier.size(88.dp))
                }
            }
            AnimatedContent(on, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "state") { lit ->
                Text(
                    if (lit) tr("Allumée", "On") else tr("Éteinte", "Off"),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (lit) c.primary else c.onSurfaceVariant,
                )
            }
            Text(
                tr("Touchez la forme pour allumer ou éteindre", "Tap the shape to switch on or off"),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )

            AnimatedVisibility(maxLevel > 1, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Intensité", "Brightness"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${(sliderValue / maxLevel * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium, color = c.primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.BrightnessLow, null, tint = c.onSurfaceVariant)
                            Slider(
                                value = sliderValue,
                                onValueChange = { v ->
                                    sliderValue = v
                                    val l = v.roundToInt().coerceIn(1, maxLevel)
                                    if (l != chosen) {
                                        chosen = l
                                        haptics.segment()
                                        if (on) Torch.setLevel(context, l)
                                    }
                                },
                                valueRange = 1f..maxLevel.toFloat(),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Icon(Icons.Rounded.BrightnessHigh, null, tint = c.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                QuickLink(Icons.Rounded.Sos, "SOS", Modifier.weight(1f)) { navigate("sos") }
                QuickLink(Icons.Rounded.GraphicEq, tr("Morse", "Morse"), Modifier.weight(1f)) { navigate("morse") }
                QuickLink(Icons.Rounded.LightMode, tr("Écran", "Screen"), Modifier.weight(1f)) { navigate("screen-light") }
            }
        }
    }
}

@Composable
private fun QuickLink(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    FilledTonalButton(onClick = { haptics.click(); onClick() }, shapes = ButtonDefaults.shapes(), modifier = modifier.height(56.dp)) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}
