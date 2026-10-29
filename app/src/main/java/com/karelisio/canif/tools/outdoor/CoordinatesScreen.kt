package com.karelisio.canif.tools.outdoor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.settings.UnitSystem
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.MorphShape
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.components.shareText
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalAppSettings
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun dms(value: Double, pos: String, neg: String): String {
    val a = abs(value)
    val d = a.toInt()
    val mFull = (a - d) * 60
    val m = mFull.toInt()
    val s = (mFull - m) * 60
    return "%d°%02d′%04.1f″ %s".format(Locale.US, d, m, s, if (value >= 0) pos else neg)
}

private fun better(new: Location, old: Location?): Boolean {
    if (old == null) return true
    val dt = new.time - old.time
    if (dt > 10_000) return true
    if (dt < -10_000) return false
    if (new.accuracy <= old.accuracy) return true
    return new.provider == old.provider && dt > 0
}

private fun locationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)
}

@SuppressLint("MissingPermission")
@Composable
fun ToolScope.CoordinatesScreen() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    val imperial = LocalAppSettings.current.units == UnitSystem.IMPERIAL
    var location by remember { mutableStateOf<Location?>(null) }
    var satsUsed by remember { mutableIntStateOf(0) }
    var satsSeen by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(locationEnabled(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hadFix by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        enabled = locationEnabled(context)
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (better(l, location)) location = l
            }
            override fun onProviderEnabled(provider: String) { enabled = true }
            override fun onProviderDisabled(provider: String) { enabled = locationEnabled(context) }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        val gnss = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satsSeen = status.satelliteCount
                satsUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
            }
        }
        val handler = Handler(Looper.getMainLooper())
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (p in lm.allProviders) {
                    lm.getLastKnownLocation(p)?.let { l -> if (System.currentTimeMillis() - l.time < 120_000 && better(l, location)) location = l }
                    lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
                }
            }
            lm.registerGnssStatusCallback(gnss, handler)
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
        onPauseOrDispose {
            lm.removeUpdates(listener)
            runCatching { lm.unregisterGnssStatusCallback(gnss) }
        }
    }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }

    val loc = location
    val elapsedNow = remember(now) { SystemClock.elapsedRealtimeNanos() }
    val ageS = loc?.let { ((elapsedNow - it.elapsedRealtimeNanos) / 1_000_000_000L).coerceAtLeast(0) }
    val acquiring = loc == null || loc.accuracy > 25f || (ageS ?: 0) > 20
    LaunchedEffect(loc != null && !acquiring) {
        if (loc != null && !acquiring && !hadFix) { hadFix = true; haptics.confirm() }
    }

    fun dist(m: Float) = if (imperial) "${(m * 3.28084f).roundToInt()} ft" else "${m.roundToInt()} m"
    val decimal = loc?.let { "%.6f, %.6f".format(Locale.US, it.latitude, it.longitude) }
    val dmsText = loc?.let { dms(it.latitude, "N", "S") + "  " + dms(it.longitude, "E", tr("O", "W")) }
    val osm = loc?.let { "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f".format(Locale.US, it.latitude, it.longitude, it.latitude, it.longitude) }

    ToolColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Radar(acquiring = acquiring && enabled, hasFix = loc != null)
        }
        AnimatedContent(
            when { !enabled -> 0; loc == null -> 1; acquiring -> 2; else -> 3 },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "status",
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { s ->
            Text(
                when (s) {
                    0 -> tr("Localisation désactivée", "Location is off")
                    1 -> tr("Recherche de la position…", "Looking for your position…")
                    2 -> tr("Affinage de la précision…", "Refining accuracy…")
                    else -> tr("Position trouvée", "Position found")
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (s == 3) c.primary else c.onSurfaceVariant,
            )
        }

        if (!enabled) {
            CanifCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOff, null, tint = c.error)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Activez la localisation du téléphone pour obtenir vos coordonnées.", "Turn on device location to get your coordinates."), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(tr("Ouvrir les réglages", "Open settings")) }
            }
        }

        if (loc != null) {
            ResultCard(
                tr("Latitude, longitude", "Latitude, longitude"),
                decimal!!,
                supporting = "± " + dist(loc.accuracy),
            )
            ResultCard(tr("Degrés, minutes, secondes", "Degrees, minutes, seconds"), dmsText!!, emphasized = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                StatBlock(tr("Précision", "Accuracy"), "± " + dist(loc.accuracy), Modifier.weight(1f))
                StatBlock(tr("Altitude", "Altitude"), if (loc.hasAltitude()) dist(loc.altitude.toFloat()) else "—", Modifier.weight(1f))
                StatBlock(tr("Satellites", "Satellites"), if (satsSeen > 0) "$satsUsed / $satsSeen" else "—", Modifier.weight(1f))
                StatBlock(
                    tr("Mise à jour", "Updated"),
                    when {
                        ageS == null -> "—"
                        ageS < 2 -> tr("à l'instant", "just now")
                        ageS < 60 -> tr("il y a $ageS s", "$ageS s ago")
                        else -> tr("il y a ${ageS / 60} min", "${ageS / 60} min ago")
                    },
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { haptics.confirm(); copyToClipboard(context, decimal) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text(tr("Copier", "Copy")) }
                FilledTonalButton(
                    onClick = { haptics.click(); shareText(context, osm!!, tr("Partager ma position", "Share my location")) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text(tr("Partager", "Share")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { haptics.tick(); copyToClipboard(context, dmsText) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(tr("Copier en DMS", "Copy as DMS")) }
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Carte", "Map")) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Sweeping radar while acquiring; the center morphs into a location badge once a fix is in. */
@Composable
private fun Radar(acquiring: Boolean, hasFix: Boolean) {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "radar")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "angle")
    val ping by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ping")
    val sweep by animateFloatAsState(if (acquiring) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "sweep")
    val badge by animateFloatAsState(if (hasFix) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "badge")
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst) }
    val ring = c.primary
    val sweepColor = c.primary
    val bg = c.primaryContainer.copy(alpha = 0.35f)
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(bg, r)
            for (k in 1..3) drawCircle(ring.copy(alpha = 0.25f), r * k / 3f, style = Stroke(1.5.dp.toPx()))
            drawLine(ring.copy(alpha = 0.18f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.dp.toPx())
            drawLine(ring.copy(alpha = 0.18f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())
            if (sweep > 0.01f && !reduce) {
                rotate(angle) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to Color.Transparent, 0.75f to Color.Transparent, 1f to sweepColor.copy(alpha = 0.55f * sweep),
                            center = center,
                        ),
                        r,
                    )
                }
                drawCircle(ring.copy(alpha = (1f - ping) * 0.6f * sweep), r * ping, style = Stroke(3.dp.toPx()))
            }
        }
        val s = 0.55f + 0.45f * badge
        Box(
            Modifier
                .size(84.dp)
                .graphicsLayer { scaleX = s; scaleY = s; rotationZ = if (reduce) 0f else (1f - badge) * -90f }
                .clip(MorphShape(morph, badge.coerceIn(0f, 1f)))
                .background(if (hasFix) c.primary else c.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MyLocation, null, tint = if (hasFix) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(36.dp))
        }
    }
}
