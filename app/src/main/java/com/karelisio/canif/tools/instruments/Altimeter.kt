package com.karelisio.canif.tools.instruments

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.settings.UnitSystem
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.permissions.PermissionGate
import com.karelisio.canif.ui.theme.LocalAppSettings
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt

private const val STANDARD_P0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE
private const val M_TO_FT = 3.28084f

@Composable
fun AltimeterScreen() {
    val context = LocalContext.current
    val hasBaro = remember { context.hasSensor(Sensor.TYPE_PRESSURE) }
    var gps by rememberSaveable { mutableStateOf(!hasBaro) }
    val history = remember(gps) { RingBuffer(300) }
    ToolColumn {
        if (hasBaro) {
            ChoiceRow(listOf(false, true), gps, label = { if (it) "GPS" else tr("Baromètre", "Barometer") }, onSelect = { gps = it })
        }
        AnimatedContent(gps, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "alti") { useGps ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (useGps) PermissionGate(listOf(AppPermission.LOCATION)) { GpsAltimeter(history) }
                else BaroAltimeter(history)
            }
        }
    }
}

@Composable
private fun AltitudeCard(altitudeM: Float?, history: RingBuffer, caption: String) {
    val c = MaterialTheme.colorScheme
    val imperial = LocalAppSettings.current.units == UnitSystem.IMPERIAL
    val factor = if (imperial) M_TO_FT else 1f
    val animated by animateFloatAsState((altitudeM ?: 0f) * factor, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "alt")
    // One history point per second.
    val current by androidx.compose.runtime.rememberUpdatedState(altitudeM)
    LaunchedEffect(history) {
        while (true) {
            current?.let { history.add(it * factor) }
            delay(1000)
        }
    }
    CanifCard(color = c.primaryContainer) {
        Text(tr("Altitude", "Altitude"), style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer)
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(if (altitudeM == null) "—" else "%,d".format(Lang.locale, animated.roundToInt()), style = MaterialTheme.typography.displayLarge, color = c.onPrimaryContainer)
            Text(if (imperial) " ft" else " m", style = MaterialTheme.typography.headlineSmall, color = c.onPrimaryContainer, modifier = Modifier.padding(bottom = 10.dp))
        }
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = 0.8f))
    }
    SectionTitle(tr("Historique (5 min)", "History (5 min)"))
    LiveGraph(history, Modifier.fillMaxWidth().height(140.dp), minSpan = if (imperial) 10f else 3f)
}

@Composable
private fun BaroAltimeter(history: RingBuffer) {
    val haptics = rememberHaptics()
    val pressure = rememberSensor(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_UI, smoothing = 0.8f)
    val p0 = rememberFloatPref("alti.p0", STANDARD_P0)
    var p by remember { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(Unit) {
        while (true) {
            if (pressure.hasData) p = pressure[0]
            delay(250)
        }
    }
    val altitude = if (p.isNaN()) null else SensorManager.getAltitude(p0.value, p)
    AltitudeCard(
        altitude, history,
        if (p0.value == STANDARD_P0) tr("Atmosphère standard (1013,25 hPa) — calibrez pour plus de justesse", "Standard atmosphere (1013.25 hPa) — calibrate for accuracy")
        else tr("Calibré : niveau de la mer %.1f hPa", "Calibrated: sea level %.1f hPa").format(p0.value),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBlock(tr("Pression", "Pressure"), if (p.isNaN()) "—" else "%.1f hPa".format(p), Modifier.weight(1f))
        StatBlock(tr("Niveau de la mer", "Sea level"), "%.1f hPa".format(p0.value), Modifier.weight(1f))
    }
    SectionTitle(tr("Calibration", "Calibration"))
    CanifCard {
        var seaLevel by rememberSaveable { mutableStateOf("") }
        var known by rememberSaveable { mutableStateOf("") }
        val imperial = LocalAppSettings.current.units == UnitSystem.IMPERIAL
        Text(
            tr("Saisissez la pression au niveau de la mer (QNH, donnée par la météo locale) ou une altitude connue.",
                "Enter the sea-level pressure (QNH, from your local weather) or a known altitude."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            NumberField(seaLevel, { seaLevel = it }, tr("Pression au niveau de la mer", "Sea-level pressure"), Modifier.weight(1f), suffix = "hPa")
            FilledTonalIconButton(
                onClick = {
                    seaLevel.toFloatOrNull()?.takeIf { it in 850f..1100f }?.let { p0.set(it); haptics.confirm(); seaLevel = "" } ?: haptics.reject()
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Icon(Icons.Rounded.Check, tr("Appliquer", "Apply")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            NumberField(known, { known = it }, tr("Altitude connue", "Known altitude"), Modifier.weight(1f), suffix = if (imperial) "ft" else "m")
            FilledTonalIconButton(
                onClick = {
                    val h = known.toFloatOrNull()?.let { if (imperial) it / M_TO_FT else it }
                    if (h != null && !p.isNaN() && h in -500f..9000f) {
                        p0.set(p / (1f - h / 44330f).pow(5.255f)); haptics.confirm(); known = ""
                    } else haptics.reject()
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Icon(Icons.Rounded.Check, tr("Appliquer", "Apply")) }
        }
        if (p0.value != STANDARD_P0) {
            TextButton(onClick = { p0.set(STANDARD_P0); haptics.click() }) {
                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp)); Text(" " + tr("Atmosphère standard", "Standard atmosphere"))
            }
        }
    }
}

@Composable
private fun GpsAltimeter(history: RingBuffer) {
    val context = LocalContext.current
    var location by remember { mutableStateOf<Location?>(null) }
    var enabled by remember { mutableStateOf(isProviderEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = isProviderEnabled(context)
        onPauseOrDispose { }
    }
    rememberLocationUpdates(enabled = true) { location = it; enabled = true }
    val loc = location
    val msl = loc != null && Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude()
    val altitude: Float? = when {
        loc == null || !loc.hasAltitude() -> null
        msl && Build.VERSION.SDK_INT >= 34 -> loc.mslAltitudeMeters.toFloat()
        else -> loc.altitude.toFloat()
    }
    val vAcc: Float? = when {
        loc == null -> null
        msl && Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitudeAccuracy() -> loc.mslAltitudeAccuracyMeters
        loc.hasVerticalAccuracy() -> loc.verticalAccuracyMeters
        else -> null
    }
    if (!enabled) {
        WarningBanner(tr("La localisation est désactivée.", "Location is turned off."))
        FilledTonalButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }) {
            Text(tr("Activer la localisation", "Turn on location"))
        }
    }
    val imperial = LocalAppSettings.current.units == UnitSystem.IMPERIAL
    AltitudeCard(
        altitude, history,
        when {
            loc == null -> tr("Recherche du signal GPS…", "Searching for GPS signal…")
            msl -> tr("Altitude GPS au-dessus du niveau de la mer", "GPS altitude above sea level")
            else -> tr("Altitude GPS (ellipsoïde WGS 84, peut différer de quelques dizaines de mètres)", "GPS altitude (WGS 84 ellipsoid, may differ by tens of metres)")
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBlock(
            tr("Précision verticale", "Vertical accuracy"),
            vAcc?.let { "± " + if (imperial) "${(it * M_TO_FT).roundToInt()} ft" else "${it.roundToInt()} m" } ?: "—",
            Modifier.weight(1f),
        )
        StatBlock(
            tr("Précision horizontale", "Horizontal accuracy"),
            loc?.takeIf { it.hasAccuracy() }?.let { "± " + if (imperial) "${(it.accuracy * M_TO_FT).roundToInt()} ft" else "${it.accuracy.roundToInt()} m" } ?: "—",
            Modifier.weight(1f),
        )
    }
}
