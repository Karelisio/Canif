package com.karelisio.canif.tools.instruments

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val fullScales = listOf(300f, 120f, 50f)

@Composable
fun MetalDetectorScreen() {
    KeepScreenOn()
    val haptics = rememberHaptics()
    val vibrator = rememberPulseVibrator()
    val mag = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME, smoothing = 0.05f)
    val buffer = remember { RingBuffer(240) }
    var baseline by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var sensitivity by rememberSaveable { mutableIntStateOf(1) }
    var vibrate by rememberSaveable { mutableStateOf(true) }
    val fullScale = fullScales[sensitivity]

    // Feed the graph at sensor rate; the first half second sets the baseline automatically.
    val deviation = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var sum = 0f
        var n = 0
        snapshotFlow { mag.values }.collect { v ->
            if (v.size < 3) return@collect
            val m = mag.magnitude
            buffer.add(m)
            if (baseline.isNaN()) {
                sum += m; n++
                if (n >= 25) baseline = sum / n
            } else deviation.floatValue = abs(m - baseline)
        }
    }
    var shownTotal by remember { mutableIntStateOf(0) }
    var shownDev by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            shownTotal = mag.magnitude.roundToInt()
            shownDev = deviation.floatValue.roundToInt()
            delay(100)
        }
    }
    // Progressive vibration: pulse rate and strength follow the deviation.
    LaunchedEffect(vibrate, vibrator, fullScale) {
        while (true) {
            val d = deviation.floatValue
            val n = (d / fullScale).coerceIn(0f, 1f)
            if (vibrate && vibrator.usable && d > fullScale * 0.06f) {
                vibrator.pulse((16 + 26 * n).toLong(), 0.2f + 0.8f * n)
                delay((720 - 650 * n).toLong())
            } else delay(90)
        }
    }

    val gauge = rememberSpringFollower(stiffness = 160f, dampingRatio = 0.6f, eps = 0.001f) { deviation.floatValue / fullScale }
    val level = shownDev / fullScale
    val c = MaterialTheme.colorScheme
    val progress by animateColorAsState(
        when { level > 0.6f -> c.error; level > 0.15f -> c.tertiary; else -> c.primary },
        label = "metal",
    )

    ToolColumn {
        ArcGauge({ gauge.floatValue }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), progressColor = progress) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(if (mag.hasData) "$shownTotal" else "—", style = MaterialTheme.typography.displayLarge)
                Text("µT", style = MaterialTheme.typography.titleMedium, color = c.primary)
                Spacer(Modifier.size(6.dp))
                Text(
                    when {
                        level > 0.6f -> tr("Métal tout proche !", "Metal very close!")
                        level > 0.15f -> tr("Métal détecté", "Metal detected")
                        else -> tr("Rien à signaler", "Nothing nearby")
                    },
                    style = MaterialTheme.typography.labelLarge, color = progress,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("Écart", "Deviation"), "+$shownDev µT", Modifier.weight(1f))
            StatBlock(tr("Base", "Baseline"), if (baseline.isNaN()) "—" else "${baseline.roundToInt()} µT", Modifier.weight(1f))
        }
        LiveGraph(
            buffer, Modifier.fillMaxWidth().height(140.dp),
            minSpan = 10f, baseline = { if (baseline.isNaN()) mag.magnitude else baseline }, color = progress,
        )
        SectionTitle(tr("Sensibilité", "Sensitivity"))
        ChoiceRow(listOf(0, 1, 2), sensitivity, label = { listOf(tr("Faible", "Low"), tr("Moyenne", "Medium"), tr("Forte", "High"))[it] }, onSelect = { sensitivity = it })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { baseline = mag.magnitude; haptics.confirm() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.TrackChanges, null, Modifier.size(18.dp)); Text("  " + tr("Calibrer ici", "Calibrate here"))
            }
            if (vibrator.usable || !vibrate) {
                FilledTonalIconToggleButton(checked = vibrate, onCheckedChange = { vibrate = it; haptics.tick() }) {
                    Icon(Icons.Rounded.Vibration, tr("Vibration", "Vibration"))
                }
            }
        }
        Text(
            tr(
                "Éloignez le téléphone de tout métal, touchez « Calibrer ici », puis balayez lentement la zone. Le capteur est souvent en haut du téléphone. Ne détecte que les métaux ferreux et les aimants.",
                "Hold the phone away from metal, tap “Calibrate here”, then sweep slowly. The sensor is usually at the top of the phone. Only ferrous metals and magnets are detected.",
            ),
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
        )
    }
}
