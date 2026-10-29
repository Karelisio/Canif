package com.karelisio.canif.tools.random

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.tools.calc.Stepper
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.PolygonShape
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

private val DieTypes = listOf(4, 6, 8, 10, 12, 20)

private fun dieShape(sides: Int): RoundedPolygon = when (sides) {
    4 -> MaterialShapes.Triangle
    6 -> MaterialShapes.Square
    8 -> MaterialShapes.Diamond
    10 -> MaterialShapes.Gem
    12 -> MaterialShapes.Pentagon
    else -> MaterialShapes.Cookie6Sided
}

private class Roll(val id: Long, val sides: Int, val values: List<Int>) {
    val total get() = values.sum()
}

/** Shake detector: |a| above ~2.5 g, debounced. Registered only while the screen is resumed. */
@Composable
internal fun ShakeEffect(enabled: Boolean, thresholdG: Float = 2.5f, debounceMs: Long = 1200, onShake: () -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(onShake)
    LifecycleResumeEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val now = SystemClock.elapsedRealtime()
                if (g > thresholdG && now - last > debounceMs) {
                    last = now
                    current()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (enabled && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose { sm.unregisterListener(listener) }
    }
}

@Composable
internal fun DiceScreen() {
    var count by rememberSaveable { mutableIntStateOf(2) }
    var sides by rememberSaveable { mutableIntStateOf(6) }
    var values by rememberSaveable { mutableStateOf(List(10) { Random.nextInt(1, 7) }) }
    var rollKey by remember { mutableIntStateOf(0) }
    var rolling by remember { mutableStateOf(false) }
    val history = remember { mutableStateOf(listOf<Roll>()) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val hasAccel = remember { (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }

    fun roll() {
        if (rolling) return
        rolling = true
        haptics.click()
        val v = List(10) { Random.nextInt(1, sides + 1) }
        values = v
        rollKey++
        scope.launch {
            delay(if (reduce) 0 else 450L + count * 80L)
            rolling = false
            haptics.confirm()
            history.value = (listOf(Roll(SystemClock.elapsedRealtimeNanos(), sides, v.take(count))) + history.value).take(8)
        }
    }

    ShakeEffect(enabled = true) { roll() }

    ToolColumn {
        ChoiceRow(DieTypes, sides, label = { "d$it" }, onSelect = { if (!rolling) { sides = it; values = values.map { v -> v.coerceAtMost(it) } } })
        Stepper(count, { count = it }, 1..10, tr("Nombre de dés", "Number of dice"))

        CanifCard(color = MaterialTheme.colorScheme.surfaceContainer) {
            val size: Dp = when {
                count == 1 -> 150.dp
                count <= 4 -> 104.dp
                else -> 78.dp
            }
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                repeat(count) { i ->
                    Die(sides, values[i], rollKey, i, size, Modifier.padding(vertical = 6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Total", "Total"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                RollingText(
                    if (rolling) "…" else values.take(count).sum().toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Button(
            onClick = { roll() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shapes = ButtonDefaults.shapes(),
            enabled = !rolling,
        ) {
            Icon(Icons.Rounded.Casino, null)
            Text("  " + tr("Lancer", "Roll"), style = MaterialTheme.typography.titleMedium)
        }
        if (hasAccel) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Vibration, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  " + tr("Ou secouez le téléphone", "Or shake your phone"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(history.value.isNotEmpty(), enter = expandVertically() + fadeIn()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle(tr("Historique", "History"))
                history.value.forEachIndexed { i, r ->
                    Row(
                        Modifier
                            .cascade(r.id, 0)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (i == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${r.values.size}d${r.sides}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                        Text(r.values.joinToString(" + "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("= ${r.total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Die(sides: Int, value: Int, rollKey: Int, index: Int, size: Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val rot = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    var shown by remember { mutableIntStateOf(value) }
    val (bg, fg) = when (sides) {
        4 -> c.primaryContainer to c.onPrimaryContainer
        6 -> c.secondaryContainer to c.onSecondaryContainer
        8 -> c.tertiaryContainer to c.onTertiaryContainer
        10 -> c.primary to c.onPrimary
        12 -> c.secondary to c.onSecondary
        else -> c.tertiary to c.onTertiary
    }
    LaunchedEffect(rollKey) {
        if (rollKey == 0 || reduce) { shown = value; return@LaunchedEffect }
        val spinTo = (if (Random.nextBoolean()) 1 else -1) * Random.nextInt(270, 720).toFloat()
        launch {
            rot.snapTo(spinTo)
            rot.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessVeryLow + 30f * index))
        }
        launch {
            lift.animateTo(1f, tween(220 + index * 20))
            lift.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
        }
        // Faces flicker, slowing down, then freeze on the final value.
        val frames = 8 + index
        for (f in 0 until frames) {
            shown = Random.nextInt(1, sides + 1)
            delay(30L + f * 5L)
        }
        shown = value
        scale.snapTo(1.18f)
        haptics.tick()
        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(value, sides) { if (rollKey == 0 || reduce) shown = value }
    val shape = remember(sides) { PolygonShape(dieShape(sides)) }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rot.value
                translationY = -lift.value * size.toPx() * 0.35f
                scaleX = scale.value
                scaleY = scale.value
                shadowElevation = 6.dp.toPx() + lift.value * 10.dp.toPx()
                this.shape = shape
                clip = true
            }
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (sides == 6) {
            Pips(shown.coerceIn(1, 6), fg, Modifier.fillMaxSize().padding(size * 0.2f))
        } else {
            Text(
                shown.toString(),
                color = fg,
                fontSize = (size.value * if (sides == 4) 0.3f else 0.36f).sp,
                fontWeight = FontWeight.Bold,
                modifier = if (sides == 4) Modifier.padding(top = size * 0.18f) else Modifier,
            )
        }
    }
}

@Composable
private fun Pips(n: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.11f
        fun p(x: Float, y: Float) = drawCircle(color, r, Offset(size.width * x, size.height * y))
        val l = 0.15f
        val m = 0.5f
        val h = 0.85f
        when (n) {
            1 -> p(m, m)
            2 -> { p(l, l); p(h, h) }
            3 -> { p(l, l); p(m, m); p(h, h) }
            4 -> { p(l, l); p(h, l); p(l, h); p(h, h) }
            5 -> { p(l, l); p(h, l); p(m, m); p(l, h); p(h, h) }
            else -> { p(l, l); p(h, l); p(l, m); p(h, m); p(l, h); p(h, h) }
        }
    }
}
