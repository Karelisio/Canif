package com.karelisio.canif.tools.outdoor

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.delay

/** S O S as symbols, with the gap (in units) that follows each one. */
private val SOS_SYMBOLS = listOf(1, 1, 1, 3, 3, 3, 1, 1, 1)
private val SOS_GAPS = listOf(1, 1, 3, 1, 1, 3, 1, 1, 7)
private const val SOS_UNIT_MS = 200L

@Composable
fun ToolScope.SosScreen() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    val hasTorch = remember { Torch.isAvailable(context) }
    var active by remember { mutableStateOf(false) }
    var useTorch by rememberSaveable { mutableStateOf(hasTorch) }
    var useScreen by rememberSaveable { mutableStateOf(true) }
    var lit by remember { mutableStateOf(false) }
    var symbol by remember { mutableIntStateOf(-1) }

    LaunchedEffect(active, useTorch) {
        if (!active) { lit = false; symbol = -1; return@LaunchedEffect }
        val torch = useTorch
        try {
            val start = SystemClock.elapsedRealtime()
            var t = 0L
            while (true) {
                for (k in SOS_SYMBOLS.indices) {
                    symbol = k
                    lit = true
                    if (torch) Torch.set(context, true)
                    t += SOS_SYMBOLS[k] * SOS_UNIT_MS
                    delay((start + t - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    lit = false
                    if (torch) Torch.set(context, false)
                    t += SOS_GAPS[k] * SOS_UNIT_MS
                    delay((start + t - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                }
            }
        } finally {
            lit = false
            if (torch) Torch.set(context, false)
        }
    }
    DisposableEffect(Unit) { onDispose { if (Torch.isOn.value) Torch.off(context) } }
    KeepScreenOn(active)

    if (active && useScreen) {
        FlashOverlay(lit = { lit }, onStop = { haptics.click(); active = false }) {
            val fg = if (lit) Color.Black else Color.White
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SOS", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black), color = fg)
                Spacer(Modifier.height(24.dp))
                SosPattern(symbol, fg, fg.copy(alpha = 0.3f))
            }
            Text(
                tr("Touchez l'écran pour arrêter", "Tap the screen to stop"),
                style = MaterialTheme.typography.titleMedium,
                color = fg.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            )
        }
    }

    ToolColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlowButton(
                on = active,
                onClick = { haptics.confirm(); active = !active },
                size = 200.dp,
                offShape = MaterialShapes.SoftBurst,
                glow = if (lit) 1f else 0.35f,
                onColor = c.error,
                offColor = c.errorContainer,
                contentDescription = tr("Démarrer ou arrêter le SOS", "Start or stop SOS"),
            ) {
                val tint by animateColorAsState(if (active) c.onError else c.onErrorContainer, label = "tint")
                val pulse by animateFloatAsState(if (lit) 1.12f else 1f, tween(90), label = "pulse")
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }) {
                    Text("SOS", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black), color = tint)
                    Text(if (active) tr("Arrêter", "Stop") else tr("Démarrer", "Start"), style = MaterialTheme.typography.titleMedium, color = tint)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SosPattern(symbol, c.error, c.outlineVariant)
        }
        Text(
            tr("· · · — — — · · ·  —  signal international de détresse, répété en boucle.", "· · · — — — · · ·  —  the international distress signal, looped."),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 8.dp)) {
                if (hasTorch) SwitchRow(Icons.Rounded.FlashlightOn, tr("Lampe torche", "Flashlight"), useTorch) { haptics.tick(); useTorch = it }
                SwitchRow(Icons.Rounded.PhoneAndroid, tr("Écran clignotant plein écran", "Full screen flashing"), useScreen) { haptics.tick(); useScreen = it }
            }
        }

        FilledTonalButton(
            onClick = {
                haptics.click()
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.Call, null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Composer le 112 (urgences)", "Dial 112 (emergency)"))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

/** Dots and dashes of SOS, the one being sent grows and lights up. */
@Composable
private fun SosPattern(current: Int, active: Color, idle: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SOS_SYMBOLS.forEachIndexed { k, units ->
            val on = k == current
            val w by animateDpAsState(if (units == 1) 16.dp else 40.dp, label = "w")
            val s by animateFloatAsState(if (on) 1.3f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh), label = "s")
            val col by animateColorAsState(if (on) active else idle, tween(80), label = "c")
            Box(
                Modifier
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .size(width = w, height = 16.dp)
                    .clip(CircleShape)
                    .background(col),
            )
            if (k == 2 || k == 5) Spacer(Modifier.width(8.dp))
        }
    }
}
