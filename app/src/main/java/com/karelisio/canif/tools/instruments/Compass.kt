package com.karelisio.canif.tools.instruments

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.permissions.rememberPermissionRequester
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun cardinal(deg: Float): String {
    val fr = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val en = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val i = (((norm360(deg) + 22.5f) / 45f).toInt()) % 8
    return if (Lang.isFr) fr[i] else en[i]
}

@Composable
fun CompassScreen() {
    LockOrientation()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val hasRotationVector = remember { context.hasSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val rv = rememberSensor(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME, enabled = hasRotationVector)
    val mag = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME, smoothing = 0.12f)
    val acc = rememberSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME, smoothing = 0.12f, enabled = !hasRotationVector)
    val rotation = rememberDisplayRotation()

    // Location / declination for true north.
    var granted by remember { mutableStateOf(AppPermission.LOCATION.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = AppPermission.LOCATION.isGranted(context)
        onPauseOrDispose { }
    }
    val location by rememberApproxLocation(granted)
    val declination: Float? = remember(location) {
        location?.let { GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis()).declination }
    }
    val northPref = rememberStringPref("compass.north", "true")
    val wantTrue = northPref.value == "true"
    val useTrue = wantTrue && declination != null

    val matrix = remember { FloatArray(9) }
    val (ux, uy) = when (rotation) {
        Surface.ROTATION_90 -> 1f to 0f
        Surface.ROTATION_180 -> 0f to -1f
        Surface.ROTATION_270 -> -1f to 0f
        else -> 0f to 1f
    }
    /** Heading of the direction the user faces: screen-up when flat, camera axis when upright. */
    fun heading(): Float {
        val ok = if (hasRotationVector) {
            val v = rv.values
            if (v.size >= 3) { SensorManager.getRotationMatrixFromVector(matrix, v); true } else false
        } else {
            acc.hasData && mag.hasData && SensorManager.getRotationMatrix(matrix, null, acc.values, mag.values)
        }
        if (!ok) return Float.NaN
        val e = matrix[0] * ux + matrix[1] * uy - matrix[2]
        val n = matrix[3] * ux + matrix[4] * uy - matrix[5]
        val h = Math.toDegrees(atan2(e, n).toDouble()).toFloat()
        return norm360(h + if (useTrue) declination ?: 0f else 0f)
    }

    val rose = rememberSpringFollower(stiffness = 55f, dampingRatio = 0.52f, angular = true, eps = 0.02f) {
        val h = heading(); if (h.isNaN()) Float.NaN else -h
    }
    var shown by remember { mutableIntStateOf(-1) }
    var field by remember { mutableIntStateOf(0) }
    LaunchedEffect(useTrue) {
        var lastSector = -1
        while (true) {
            val h = heading()
            field = mag.magnitude.roundToInt()
            if (!h.isNaN()) {
                shown = h.roundToInt() % 360
                val sector = (((h + 22.5f) / 45f).toInt()) % 8
                if (lastSector >= 0 && sector != lastSector) haptics.tick()
                lastSector = sector
            }
            delay(66)
        }
    }

    val c = MaterialTheme.colorScheme
    ToolColumn {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
            CompassRose({ rose.floatValue }, useTrue)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            RollingText(if (shown < 0) "—" else "$shown°", style = MaterialTheme.typography.displayLarge, color = c.onSurface)
            Spacer(Modifier.width(14.dp))
            RollingText(if (shown < 0) "" else cardinal(shown.toFloat()), style = MaterialTheme.typography.displaySmall, color = c.primary, modifier = Modifier.padding(bottom = 8.dp))
        }
        ChoiceRow(listOf(false, true), wantTrue, label = { if (it) tr("Nord vrai", "True north") else tr("Nord magnétique", "Magnetic north") }, onSelect = { northPref.set(it.toString()) })
        AnimatedVisibility(wantTrue && declination == null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            CanifCard(color = c.secondaryContainer) {
                Text(tr("Nord vrai indisponible", "True north unavailable"), style = MaterialTheme.typography.titleMedium, color = c.onSecondaryContainer)
                Text(
                    if (granted) tr("En attente d'une position… Le nord magnétique est affiché.", "Waiting for a location… Showing magnetic north.")
                    else tr("La déclinaison magnétique dépend de votre position. Le nord magnétique est affiché en attendant.", "Magnetic declination depends on your location. Showing magnetic north meanwhile."),
                    style = MaterialTheme.typography.bodyMedium, color = c.onSecondaryContainer,
                )
                if (!granted) {
                    val request = rememberPermissionRequester(AppPermission.LOCATION) { granted = it }
                    FilledTonalButton(onClick = request, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Rounded.LocationOn, null, Modifier.size(18.dp)); Text("  " + tr("Autoriser la localisation", "Allow location"))
                    }
                }
            }
        }
        AnimatedVisibility(mag.unreliable && mag.hasData, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            CalibrationCard()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("Champ", "Field"), "$field µT", Modifier.weight(1f))
            StatBlock(
                tr("Déclinaison", "Declination"),
                declination?.let { "%.1f° %s".format(abs(it), if (it >= 0) "E" else if (Lang.isFr) "O" else "W") } ?: "—",
                Modifier.weight(1f),
            )
        }
        if (mag.hasData && (field < 20 || field > 70)) {
            WarningBanner(tr("Champ inhabituel : éloignez-vous des objets métalliques et aimants.", "Unusual field: move away from metal objects and magnets."))
        }
    }
}

@Composable
private fun CompassRose(rotation: () -> Float, trueNorth: Boolean) {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val big = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
    val small = MaterialTheme.typography.labelMedium
    val fr = Lang.isFr
    val cardinals = remember(big, fr, c) {
        listOf("N", "E", "S", if (fr) "O" else "W").mapIndexed { i, s -> measurer.measure(s, big.copy(color = if (i == 0) c.primary else c.onSurface)) }
    }
    val inter = remember(small, fr, c) {
        (if (fr) listOf("NE", "SE", "SO", "NO") else listOf("NE", "SE", "SW", "NW")).map { measurer.measure(it, small.copy(color = c.onSurfaceVariant)) }
    }
    val degrees = remember(small, c) { (0 until 12).map { measurer.measure((it * 30).toString(), small.copy(color = c.onSurfaceVariant)) } }
    val bezel = c.surfaceContainerHighest
    val face = c.surfaceContainerHigh
    val tick = c.onSurfaceVariant
    val north = c.primary
    val northDark = c.primary.copy(alpha = 0.72f)
    val pointA = c.outline
    val pointB = c.outlineVariant
    val minorA = c.secondary.copy(alpha = 0.55f)
    val minorB = c.secondaryContainer
    val lubber = if (trueNorth) c.tertiary else c.primary
    val hub = c.surface
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val center = size.center
                val R = size.minDimension / 2f
                val ctr = center
                // Precomputed star halves pointing up (rotated at draw time).
                fun half(len: Float, width: Float, left: Boolean) = Path().apply {
                    moveTo(ctr.x, ctr.y - len)
                    lineTo(ctr.x + if (left) -width else width, ctr.y - width)
                    lineTo(ctr.x, ctr.y)
                    close()
                }
                val longL = half(R * 0.46f, R * 0.075f, true)
                val longR = half(R * 0.46f, R * 0.075f, false)
                val shortL = half(R * 0.3f, R * 0.06f, true)
                val shortR = half(R * 0.3f, R * 0.06f, false)
                val lubberPath = Path().apply {
                    moveTo(ctr.x, ctr.y - R * 0.86f)
                    lineTo(ctr.x - R * 0.045f, ctr.y - R * 0.985f)
                    lineTo(ctr.x + R * 0.045f, ctr.y - R * 0.985f)
                    close()
                }
                onDrawBehind {
                    drawCircle(bezel, R)
                    drawCircle(face, R * 0.9f)
                    val rot = rotation().let { if (it.isNaN()) 0f else it }
                    rotate(rot, ctr) {
                        for (i in 0 until 180) {
                            val a = i * 2f - 90f
                            val major = i % 5 == 0
                            val len = if (i % 15 == 0) R * 0.075f else if (major) R * 0.05f else R * 0.025f
                            drawLine(
                                tick.copy(alpha = if (major) 0.9f else 0.45f),
                                polar(ctr, R * 0.86f, a), polar(ctr, R * 0.86f - len, a),
                                if (major) 2.dp.toPx() else 1.dp.toPx(), StrokeCap.Round,
                            )
                        }
                        degrees.forEachIndexed { i, l ->
                            if (i % 3 == 0) return@forEachIndexed
                            withTransform({ rotate(i * 30f, ctr) }) {
                                drawText(l, topLeft = Offset(ctr.x - l.size.width / 2f, ctr.y - R * 0.76f))
                            }
                        }
                        cardinals.forEachIndexed { i, l ->
                            withTransform({ rotate(i * 90f, ctr) }) {
                                drawText(l, topLeft = Offset(ctr.x - l.size.width / 2f, ctr.y - R * 0.79f))
                            }
                        }
                        inter.forEachIndexed { i, l ->
                            withTransform({ rotate(45f + i * 90f, ctr) }) {
                                drawText(l, topLeft = Offset(ctr.x - l.size.width / 2f, ctr.y - R * 0.5f))
                            }
                        }
                        // Star.
                        for (i in 0 until 4) {
                            rotate(45f + i * 90f, ctr) {
                                drawPath(shortL, minorA); drawPath(shortR, minorB)
                            }
                        }
                        for (i in 0 until 4) {
                            rotate(i * 90f, ctr) {
                                if (i == 0) { drawPath(longL, north); drawPath(longR, northDark) } else { drawPath(longL, pointA); drawPath(longR, pointB) }
                            }
                        }
                    }
                    drawPath(lubberPath, lubber)
                    drawCircle(lubber, R * 0.05f, ctr)
                    drawCircle(hub, R * 0.022f, ctr)
                }
            },
    )
}

/** "Calibration needed" card with the figure-eight gesture animated. */
@Composable
private fun CalibrationCard() {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "eight")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p")
    val phase = if (reduce) 0.15f else p
    val trail = c.onErrorContainer.copy(alpha = 0.3f)
    val phone = c.onErrorContainer
    val screen = c.errorContainer
    CanifCard(color = c.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 112.dp, height = 72.dp)) {
                val a = size.width * 0.42f
                fun pt(s: Float): Offset {
                    val th = s * 2f * PI.toFloat()
                    val d = 1f + sin(th) * sin(th)
                    return Offset(center.x + a * cos(th) / d, center.y + a * sin(th) * cos(th) / d * 1.3f)
                }
                var prev = pt(0f)
                for (i in 1..64) {
                    val n = pt(i / 64f)
                    drawLine(trail, prev, n, 3.dp.toPx(), StrokeCap.Round)
                    prev = n
                }
                val pos = pt(phase)
                val ahead = pt(phase + 0.01f)
                val ang = Math.toDegrees(atan2(ahead.y - pos.y, ahead.x - pos.x).toDouble()).toFloat()
                rotate(ang * 0.35f, pos) {
                    val w = 14.dp.toPx(); val h = 24.dp.toPx()
                    drawRoundRect(phone, Offset(pos.x - w / 2, pos.y - h / 2), Size(w, h), CornerRadius(4.dp.toPx()))
                    drawRoundRect(screen, Offset(pos.x - w / 2 + 2.dp.toPx(), pos.y - h / 2 + 3.dp.toPx()), Size(w - 4.dp.toPx(), h - 6.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(tr("Étalonnage nécessaire", "Calibration needed"), style = MaterialTheme.typography.titleMedium, color = c.onErrorContainer)
                Text(
                    tr("Décrivez lentement un 8 dans l'air avec le téléphone, en le faisant pivoter.", "Slowly draw a figure 8 in the air with the phone, rotating it."),
                    style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer,
                )
            }
        }
    }
}
