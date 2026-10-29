package com.karelisio.canif.tools.instruments

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

private class LuxRef(val lux: Float, val short: Txt, val name: Txt, val range: String)

private val luxRefs = listOf(
    LuxRef(1f, Txt("Nuit", "Night"), Txt("Nuit, pleine lune", "Night, full moon"), "0,1 – 1"),
    LuxRef(150f, Txt("Intérieur", "Indoor"), Txt("Salon, couloir", "Living room, hallway"), "50 – 300"),
    LuxRef(500f, Txt("Bureau", "Office"), Txt("Bureau, lecture", "Office, reading"), "300 – 1 000"),
    LuxRef(10_000f, Txt("Couvert", "Overcast"), Txt("Jour couvert", "Overcast day"), "1 000 – 25 000"),
    LuxRef(100_000f, Txt("Soleil", "Sun"), Txt("Plein soleil", "Full sunlight"), "> 30 000"),
)

/** 0.1 lx → 0, 100 000 lx → 1 on a log scale. */
private fun luxFraction(lux: Float) = ((log10(max(lux, 0.1f)) + 1f) / 6f).coerceIn(0f, 1f)

private fun formatLux(lux: Float): String = when {
    lux < 10f -> "%.1f".format(Lang.locale, lux)
    else -> "%,d".format(Lang.locale, lux.roundToInt())
}

private fun refIndex(lux: Float) = when {
    lux < 20f -> 0
    lux < 300f -> 1
    lux < 1000f -> 2
    lux < 30_000f -> 3
    else -> 4
}

@Composable
fun LuxMeterScreen() {
    val haptics = rememberHaptics()
    val light = rememberSensor(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_UI)
    var lux by remember { mutableFloatStateOf(0f) }
    var minLux by remember { mutableFloatStateOf(Float.NaN) }
    var maxLux by remember { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(Unit) {
        while (true) {
            if (light.hasData) {
                val v = light[0]
                lux = v
                minLux = if (minLux.isNaN()) v else minOf(minLux, v)
                maxLux = if (maxLux.isNaN()) v else maxOf(maxLux, v)
            }
            delay(120)
        }
    }
    val gauge = rememberSpringFollower(stiffness = 70f, dampingRatio = 0.75f, eps = 0.0005f) {
        if (light.hasData) luxFraction(light[0]) else 0f
    }
    val c = MaterialTheme.colorScheme
    val current = refIndex(lux)
    val markers = remember(Lang.isFr) { luxRefs.map { luxFraction(it.lux) to it.short.text } }

    ToolColumn {
        ArcGauge({ gauge.floatValue }, Modifier.fillMaxWidth().padding(horizontal = 8.dp), markers = markers) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(if (light.hasData) formatLux(lux) else "—", style = MaterialTheme.typography.displayLarge, color = c.onSurface)
                Text("lux", style = MaterialTheme.typography.titleMedium, color = c.primary)
                Spacer(Modifier.size(6.dp))
                Text(luxRefs[current].name.text, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatBlock("Min", if (minLux.isNaN()) "—" else formatLux(minLux), Modifier.weight(1f))
            StatBlock("Max", if (maxLux.isNaN()) "—" else formatLux(maxLux), Modifier.weight(1f))
            OutlinedButton(onClick = { haptics.click(); minLux = Float.NaN; maxLux = Float.NaN }) {
                Icon(Icons.Rounded.RestartAlt, tr("Réinitialiser", "Reset"))
            }
        }
        SectionTitle(tr("Repères", "References"))
        CanifCard(padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
            luxRefs.forEachIndexed { i, r ->
                val active = i == current
                val bg by animateColorAsState(if (active) c.primaryContainer else c.surfaceContainerHigh, label = "ref")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) c.primary else c.outlineVariant))
                    Spacer(Modifier.width(12.dp))
                    Text(r.name.text, style = MaterialTheme.typography.bodyLarge, color = if (active) c.onPrimaryContainer else c.onSurface, modifier = Modifier.weight(1f))
                    Text(r.range + " lx", style = MaterialTheme.typography.labelLarge, color = if (active) c.onPrimaryContainer else c.onSurfaceVariant)
                }
            }
        }
        Text(
            tr("Le capteur de lumière se trouve en général en haut de l'écran, près de l'écouteur. Ne le masquez pas avec le doigt.",
                "The light sensor usually sits at the top of the screen, near the earpiece. Keep your finger off it."),
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
        )
    }
}
