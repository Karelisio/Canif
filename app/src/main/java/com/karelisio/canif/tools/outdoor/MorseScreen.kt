package com.karelisio.canif.tools.outdoor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlin.math.roundToInt

@Composable
fun ToolScope.MorseScreen() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    val hasTorch = remember { Torch.isAvailable(context) }

    var text by rememberSaveable { mutableStateOf(incoming?.text ?: "SOS") }
    var wpm by rememberSaveable { mutableIntStateOf(12) }
    var useTorch by rememberSaveable { mutableStateOf(false) }
    var useScreen by rememberSaveable { mutableStateOf(true) }
    var loop by rememberSaveable { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var lit by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(-1) }

    val isMorse = Morse.looksLikeMorse(text)
    val decoded = if (isMorse) Morse.decode(text) else ""
    val items = remember(text) { Morse.encode(if (isMorse) decoded else text) }
    val unit = Morse.unitMs(wpm)

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val torch = useTorch && hasTorch
        try {
            do {
                Morse.play(Morse.timeline(items, trailingGap = if (loop) 7 else 0), Morse.unitMs(wpm)) { on, i ->
                    if (on != lit && torch) Torch.set(context, on)
                    lit = on
                    if (i >= 0) current = i
                }
            } while (loop)
        } finally {
            lit = false
            current = -1
            if (torch) Torch.set(context, false)
            playing = false
        }
    }
    LaunchedEffect(text) { playing = false }
    DisposableEffect(Unit) { onDispose { if (Torch.isOn.value) Torch.off(context) } }
    MaxBrightness(playing && useScreen)

    ToolColumn {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(tr("Texte ou morse (. - /)", "Text or Morse (. - /)")) },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (isMorse) {
            Surface(shape = RoundedCornerShape(24.dp), color = c.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Morse décodé", "Decoded Morse"), style = MaterialTheme.typography.labelMedium, color = c.onTertiaryContainer)
                        Text(decoded, style = MaterialTheme.typography.headlineSmall, color = c.onTertiaryContainer)
                    }
                    TextButton(onClick = { copyToClipboard(context, decoded); haptics.confirm() }) { Icon(Icons.Rounded.ContentCopy, tr("Copier", "Copy")) }
                }
            }
        }

        // Signal lamp
        val panel by animateColorAsState(if (lit) Color.White else c.surfaceContainerHighest, tween(if (lit) 30 else 60), label = "panel")
        Box(
            Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(32.dp)).background(panel),
            contentAlignment = Alignment.Center,
        ) {
            val ch = items.getOrNull(current)
            if (ch != null && !ch.isSpace) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ch.char.toString(), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = if (lit) Color.Black else c.onSurface)
                    Text(Morse.pretty(ch.code), style = MaterialTheme.typography.titleLarge, color = if (lit) Color.Black else c.onSurfaceVariant)
                }
            } else {
                Text(
                    if (playing) "" else tr("Signal lumineux", "Light signal"),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onSurfaceVariant,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$wpm " + tr("mots/min", "WPM"), style = MaterialTheme.typography.titleMedium)
                Text(tr("Unité ${unit} ms", "Unit ${unit} ms"), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                Slider(
                    value = wpm.toFloat(),
                    onValueChange = { v -> val n = v.roundToInt(); if (n != wpm) { wpm = n; haptics.segment() } },
                    valueRange = 5f..30f,
                )
            }
            Spacer(Modifier.width(8.dp))
            GlowButton(
                on = playing,
                onClick = { haptics.confirm(); if (items.isNotEmpty()) playing = !playing },
                size = 84.dp,
                offShape = MaterialShapes.Cookie6Sided,
                onShape = MaterialShapes.Square,
                glow = if (lit) 1f else 0.25f,
                contentDescription = if (playing) tr("Arrêter", "Stop") else tr("Lire", "Play"),
            ) {
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    null,
                    tint = if (playing) c.onPrimary else c.onSurface,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 8.dp)) {
                if (hasTorch) SwitchRow(Icons.Rounded.FlashlightOn, tr("Lampe torche", "Flashlight"), useTorch) { haptics.tick(); useTorch = it; playing = false }
                SwitchRow(Icons.Rounded.PhoneAndroid, tr("Écran (luminosité max)", "Screen (max brightness)"), useScreen) { haptics.tick(); useScreen = it }
                SwitchRow(Icons.Rounded.Repeat, tr("En boucle", "Loop"), loop) { haptics.tick(); loop = it }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(tr("Traduction", "Translation"), Modifier.weight(1f))
            TextButton(onClick = {
                copyToClipboard(context, items.joinToString(" ") { if (it.isSpace) "/" else it.code })
                haptics.confirm()
            }, enabled = items.isNotEmpty()) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Copier le morse", "Copy Morse"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, item -> MorseChip(item, i == current) }
        }
        if (items.isEmpty()) {
            Text(tr("Rien à traduire.", "Nothing to translate."), color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MorseChip(item: Morse.Item, active: Boolean) {
    val c = MaterialTheme.colorScheme
    if (item.isSpace) {
        Box(Modifier.height(52.dp).width(14.dp), contentAlignment = Alignment.Center) {
            Text("/", color = c.outline, style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val bg by animateColorAsState(if (active) c.primary else c.surfaceContainerHigh, spring(stiffness = Spring.StiffnessHigh), label = "bg")
    val fg by animateColorAsState(if (active) c.onPrimary else c.onSurface, spring(stiffness = Spring.StiffnessHigh), label = "fg")
    val s by animateFloatAsState(if (active) 1.1f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "s")
    Column(
        Modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(item.char.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
        Text(Morse.pretty(item.code), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = fg.copy(alpha = 0.85f))
    }
}
