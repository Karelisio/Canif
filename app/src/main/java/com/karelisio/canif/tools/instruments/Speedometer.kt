package com.karelisio.canif.tools.instruments

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.settings.UnitSystem
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalAppSettings
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

private enum class SpeedUnit(val label: String, val perMs: Float, val distLabel: String, val perMeter: Float) {
    KMH("km/h", 3.6f, "km", 0.001f),
    MPH("mph", 2.236936f, "mi", 0.000621371f),
    KN("kn", 1.943844f, "NM", 0.000539957f),
}

/** Dial scales: max, major step, minor ticks per major. */
private val scales = listOf(
    Triple(40f, 5f, 5), Triple(60f, 10f, 5), Triple(120f, 20f, 4), Triple(160f, 20f, 4),
    Triple(240f, 40f, 4), Triple(320f, 40f, 4),
)

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun SpeedScreen() {
    KeepScreenOn()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val defaultUnit = if (LocalAppSettings.current.units == UnitSystem.IMPERIAL) SpeedUnit.MPH else SpeedUnit.KMH
    val unitPref = rememberStringPref("speed.unit", defaultUnit.name)
    val unit = runCatching { SpeedUnit.valueOf(unitPref.value) }.getOrDefault(defaultUnit)

    var speedMs by remember { mutableFloatStateOf(0f) }
    var maxMs by rememberSaveable { mutableFloatStateOf(0f) }
    var distance by rememberSaveable { mutableFloatStateOf(0f) }
    var movingMs by rememberSaveable { mutableLongStateOf(0L) }
    var elapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var accuracy by remember { mutableFloatStateOf(Float.NaN) }
    var lastFixAt by remember { mutableLongStateOf(0L) }
    var satellites by remember { mutableIntStateOf(-1) }
    var anchor by remember { mutableStateOf<Location?>(null) }
    var previous by remember { mutableStateOf<Location?>(null) }
    var enabled by remember { mutableStateOf(isProviderEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = isProviderEnabled(context)
        onPauseOrDispose { }
    }

    rememberLocationUpdates(enabled = true, intervalMs = 1000L) { loc ->
        enabled = true
        lastFixAt = SystemClock.elapsedRealtime()
        accuracy = if (loc.hasAccuracy()) loc.accuracy else Float.NaN
        val prev = previous
        val s = when {
            loc.hasSpeed() -> loc.speed
            prev != null && loc.time > prev.time -> prev.distanceTo(loc) / ((loc.time - prev.time) / 1000f)
            else -> 0f
        }.let { if (it < 0.4f) 0f else it }
        previous = loc
        val good = !loc.hasAccuracy() || loc.accuracy <= 30f
        speedMs = s
        if (good) {
            maxMs = max(maxMs, s)
            // Distance: only count displacements larger than the noise of the fix.
            val a = anchor
            if (a == null) anchor = loc else {
                val d = a.distanceTo(loc)
                if (d >= max(3f, loc.accuracy * 0.6f) && s > 0f) {
                    distance += d
                    anchor = loc
                }
            }
        }
    }
    // Satellites in use.
    LifecycleResumeEffect(Unit) {
        val lm = context.locationManager()
        val cb = object : GnssStatusCompat.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                satellites = used
            }
        }
        @SuppressLint("MissingPermission")
        val ok = runCatching { LocationManagerCompat.registerGnssStatusCallback(lm, ContextCompat.getMainExecutor(context), cb) }.getOrDefault(false)
        onPauseOrDispose { if (ok) LocationManagerCompat.unregisterGnssStatusCallback(lm, cb) }
    }
    // Trip clock (ticks while the screen is shown and a fix exists).
    LaunchedEffect(Unit) {
        var last = SystemClock.elapsedRealtime()
        while (true) {
            delay(500)
            val now = SystemClock.elapsedRealtime()
            val dt = now - last
            last = now
            if (lastFixAt != 0L) {
                elapsedMs += dt
                if (speedMs > 0f) movingMs += dt
            }
            if (lastFixAt != 0L && now - lastFixAt > 6000) speedMs = 0f
        }
    }

    val shown = speedMs * unit.perMs
    val maxShown = maxMs * unit.perMs
    val scale = scales.firstOrNull { it.first >= max(shown, maxShown) * 1.05f } ?: scales.last()
    val needle = rememberSpringFollower(stiffness = 60f, dampingRatio = 0.6f, eps = 0.02f) { speedMs * unit.perMs }
    val hasFix = lastFixAt != 0L
    val c = MaterialTheme.colorScheme
    val dot by animateColorAsState(if (hasFix) c.primary else c.error, label = "fix")

    ToolColumn {
        if (!enabled) {
            WarningBanner(tr("La localisation est désactivée.", "Location is turned off."))
            FilledTonalButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }) {
                Text(tr("Activer la localisation", "Turn on location"))
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            NeedleDial({ needle.floatValue }, scale.first, scale.second, scale.third, Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 18.dp)) {
                        RollingText(if (hasFix) "${shown.roundToInt()}" else "—", style = MaterialTheme.typography.displayLarge)
                        Text(unit.label, style = MaterialTheme.typography.titleMedium, color = c.primary)
                    }
                }
            }
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(c.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.GpsFixed, null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                buildString {
                    append(if (hasFix) (if (accuracy.isNaN()) "GPS" else "GPS ± ${accuracy.roundToInt()} m") else tr("Recherche du signal GPS…", "Searching for GPS…"))
                    if (satellites >= 0) append(" · ").append(satellites).append(tr(" sat.", " sats"))
                },
                style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant,
            )
        }
        ChoiceRow(SpeedUnit.entries, unit, label = { it.label }, onSelect = { unitPref.set(it.name) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("Max", "Top"), "${maxShown.roundToInt()} ${unit.label}", Modifier.weight(1f))
            StatBlock(tr("Moyenne", "Average"), if (movingMs > 0) "${(distance / (movingMs / 1000f) * unit.perMs).roundToInt()} ${unit.label}" else "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("Distance", "Distance"), "%.2f %s".format(distance * unit.perMeter, unit.distLabel), Modifier.weight(1f))
            StatBlock(tr("Durée", "Duration"), formatDuration(elapsedMs), Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = { haptics.confirm(); maxMs = 0f; distance = 0f; movingMs = 0L; elapsedMs = 0L; anchor = null },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp)); Text("  " + tr("Remettre à zéro", "Reset trip"))
        }
        Text(
            tr("La vitesse provient du GPS : elle est fiable en extérieur, ciel dégagé. La moyenne est calculée sur le temps en mouvement.",
                "Speed comes from GPS: reliable outdoors with open sky. The average uses moving time only."),
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
        )
    }
}
