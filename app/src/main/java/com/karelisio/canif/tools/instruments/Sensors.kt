package com.karelisio.canif.tools.instruments

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.ui.permissions.findActivity
import com.karelisio.canif.ui.theme.LocalAppSettings
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Sensors
// ---------------------------------------------------------------------------------------------

fun Context.sensorManager(): SensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

fun Context.hasSensor(type: Int): Boolean = sensorManager().getDefaultSensor(type) != null

/**
 * Live reading of one sensor. [values] is replaced (new array) on every event, so reading it
 * inside a draw lambda only invalidates the draw phase. [accuracy] follows SENSOR_STATUS_*.
 */
@Stable
class SensorReading internal constructor(val type: Int, val sensor: Sensor?) {
    var values: FloatArray by mutableStateOf(FloatArray(0), neverEqualPolicy())
        internal set
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        internal set
    /** Event timestamp (ns, SystemClock.elapsedRealtimeNanos base). */
    var timestamp by mutableLongStateOf(0L)
        internal set

    val available: Boolean get() = sensor != null
    /** Flips to true once, on the first event (cheap to observe in composition). */
    var hasData by mutableStateOf(false)
        internal set
    operator fun get(i: Int): Float = values.let { if (i < it.size) it[i] else 0f }

    /** Euclidean norm of the first three components. */
    val magnitude: Float get() = values.let { if (it.size >= 3) sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) else 0f }

    val unreliable: Boolean
        get() = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

/**
 * First-order low-pass filter driven by the real time between samples, so smoothing stays the
 * same whatever the sensor rate. [tau] is the time constant in seconds (0 = no filtering).
 */
class LowPass(var tau: Float) {
    private var state: FloatArray? = null
    private var lastNs = 0L

    fun reset() { state = null }

    fun filter(input: FloatArray, timestampNs: Long, count: Int = input.size): FloatArray {
        val s = state
        if (s == null || s.size != count || tau <= 0f) {
            lastNs = timestampNs
            return input.copyOf(count).also { state = it.copyOf() }
        }
        val dt = ((timestampNs - lastNs) / 1e9f).coerceIn(0f, 0.5f)
        lastNs = timestampNs
        val a = 1f - exp(-dt / tau)
        for (i in 0 until count) s[i] += a * (input[i] - s[i])
        return s.copyOf()
    }
}

/**
 * Registers [type] while the screen is resumed (lifecycle-aware, no leak) and exposes the latest
 * low-passed values. [smoothing] is the filter time constant in seconds.
 */
@Composable
fun rememberSensor(
    type: Int,
    rate: Int = SensorManager.SENSOR_DELAY_GAME,
    smoothing: Float = 0f,
    enabled: Boolean = true,
): SensorReading {
    val context = LocalContext.current
    val sm = remember(context) { context.sensorManager() }
    val reading = remember(type) { SensorReading(type, sm.getDefaultSensor(type)) }
    val filter = remember(type) { LowPass(smoothing) }
    filter.tau = smoothing
    LifecycleResumeEffect(type, rate, enabled) {
        val sensor = reading.sensor
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val n = when (type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> e.values.size.coerceAtMost(5)
                    else -> e.values.size
                }
                reading.values = filter.filter(e.values, e.timestamp, n)
                if (!reading.hasData) reading.hasData = true
                reading.timestamp = e.timestamp
                reading.accuracy = e.accuracy
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
                reading.accuracy = accuracy
            }
        }
        if (sensor != null && enabled) {
            filter.reset()
            sm.registerListener(listener, sensor, rate)
        }
        onPauseOrDispose { sm.unregisterListener(listener) }
    }
    return reading
}

/** Raw sensor events as a cold flow (registered while collected). */
fun sensorFlow(context: Context, type: Int, rate: Int = SensorManager.SENSOR_DELAY_GAME): Flow<SensorEvent> = callbackFlow {
    val sm = context.sensorManager()
    val sensor = sm.getDefaultSensor(type)
    val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { trySend(e) }
        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }
    if (sensor != null) sm.registerListener(listener, sensor, rate)
    awaitClose { sm.unregisterListener(listener) }
}

/**
 * Gravity in *screen* coordinates (x right, y up, z out of the screen), in m/s².
 * Uses TYPE_GRAVITY when present, otherwise a low-passed accelerometer.
 */
@Composable
fun rememberScreenGravity(smoothing: Float = 0.06f): () -> FloatArray {
    val context = LocalContext.current
    val hasGravity = remember { context.hasSensor(Sensor.TYPE_GRAVITY) }
    val reading = rememberSensor(
        if (hasGravity) Sensor.TYPE_GRAVITY else Sensor.TYPE_ACCELEROMETER,
        SensorManager.SENSOR_DELAY_GAME,
        smoothing = if (hasGravity) smoothing else smoothing.coerceAtLeast(0.12f),
    )
    val rotation = rememberDisplayRotation()
    val out = remember { FloatArray(3) }
    return remember(reading, rotation) {
        {
            val v = reading.values
            if (v.size >= 3) toScreen(v[0], v[1], v[2], rotation, out) else { out[0] = 0f; out[1] = 0f; out[2] = SensorManager.GRAVITY_EARTH }
            out
        }
    }
}

/** Maps a device-axis vector to screen axes for the current display rotation. */
fun toScreen(x: Float, y: Float, z: Float, rotation: Int, out: FloatArray): FloatArray {
    when (rotation) {
        Surface.ROTATION_90 -> { out[0] = -y; out[1] = x }
        Surface.ROTATION_180 -> { out[0] = -x; out[1] = -y }
        Surface.ROTATION_270 -> { out[0] = y; out[1] = -x }
        else -> { out[0] = x; out[1] = y }
    }
    out[2] = z
    return out
}

@Composable
fun rememberDisplayRotation(): Int {
    val view = LocalView.current
    @Suppress("DEPRECATION")
    return remember(view) { view.display?.rotation ?: Surface.ROTATION_0 }
}

/** Freezes the activity orientation while the instrument is shown (sensor axes stay stable). */
@Composable
fun LockOrientation(orientation: Int = ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose { if (previous != null) activity.requestedOrientation = previous }
    }
}

@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

// ---------------------------------------------------------------------------------------------
// Physics
// ---------------------------------------------------------------------------------------------

/** Mass–spring–damper integrated with semi-implicit Euler and sub-steps (stable at any frame rate). */
class SpringSim(var stiffness: Float, var dampingRatio: Float) {
    var x = 0f
    var v = 0f

    fun step(target: Float, dt: Float) {
        val c = 2f * dampingRatio * sqrt(stiffness)
        val n = (dt / 0.004f).toInt().coerceIn(1, 12)
        val h = dt / n
        repeat(n) {
            val a = stiffness * (target - x) - c * v
            v += a * h
            x += v * h
        }
    }

    fun settled(target: Float, eps: Float) = abs(target - x) < eps && abs(v) < eps * 4f
}

fun wrap180(d: Float): Float {
    var a = d % 360f
    if (a > 180f) a -= 360f
    if (a < -180f) a += 360f
    return a
}

fun norm360(d: Float): Float = ((d % 360f) + 360f) % 360f

/**
 * A value that physically follows [target] each frame (withFrameNanos), read it in the draw
 * phase. When [angular], the target is reached through the shortest path (359° → 0°) and the
 * returned value is continuous (unbounded degrees). The loop sleeps once settled.
 */
@Composable
fun rememberSpringFollower(
    stiffness: Float = 140f,
    dampingRatio: Float = 0.55f,
    angular: Boolean = false,
    eps: Float = 0.01f,
    target: () -> Float,
): FloatState {
    val reduce = LocalReduceMotion.current
    val state = remember { mutableFloatStateOf(Float.NaN) }
    val targetFn by rememberUpdatedState(target)
    val sim = remember { SpringSim(stiffness, dampingRatio) }
    sim.stiffness = stiffness
    sim.dampingRatio = if (reduce) 1f else dampingRatio
    LaunchedEffect(angular) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val raw = targetFn()
                if (raw.isNaN()) return@withFrameNanos
                if (state.floatValue.isNaN()) {
                    sim.x = raw; sim.v = 0f
                }
                val t = if (angular) sim.x + wrap180(raw - sim.x) else raw
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                sim.step(t, dt)
                state.floatValue = sim.x
            }
            val t0 = targetFn()
            val tt = if (angular) sim.x + wrap180(t0 - sim.x) else t0
            if (!t0.isNaN() && sim.settled(tt, eps)) {
                sim.x = tt; sim.v = 0f; state.floatValue = tt
                // Sleep until the target moves again.
                snapshotFlow { targetFn() }.first { n -> !n.isNaN() && abs(if (angular) wrap180(n - sim.x) else n - sim.x) > eps }
                last = 0L
            }
        }
    }
    return state
}

// ---------------------------------------------------------------------------------------------
// Preferences
// ---------------------------------------------------------------------------------------------

class FloatPref(private val state: State<Float>, private val setter: (Float) -> Unit) {
    val value: Float get() = state.value
    fun set(v: Float) = setter(v)
}

@Composable
fun rememberFloatPref(key: String, default: Float): FloatPref {
    val settings = CanifApp.instance.settings
    val flow = remember(key) { settings.float(key, default) }
    val state = flow.collectAsState(initial = default)
    return remember(key, state) { FloatPref(state) { v -> CanifApp.instance.appScope.launch { settings.setFloat(key, v) } } }
}

class StringPref(private val state: State<String>, private val setter: (String) -> Unit) {
    val value: String get() = state.value
    fun set(v: String) = setter(v)
}

@Composable
fun rememberStringPref(key: String, default: String): StringPref {
    val settings = CanifApp.instance.settings
    val flow = remember(key, default) { settings.string(key, default) }
    val state = flow.collectAsState(initial = default)
    return remember(key, state) { StringPref(state) { v -> CanifApp.instance.appScope.launch { settings.setString(key, v) } } }
}

// ---------------------------------------------------------------------------------------------
// Location
// ---------------------------------------------------------------------------------------------

fun Context.locationManager(): LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

/** Freshest last known fix among all providers (needs the permission, else null). */
@SuppressLint("MissingPermission")
fun lastKnownLocation(context: Context): Location? {
    if (!AppPermission.LOCATION.isGranted(context) &&
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return null
    val lm = context.locationManager()
    return runCatching {
        lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }.getOrNull()
}

/**
 * Last known location, refreshed once with a single fresh fix when [granted].
 * Used for the declination (true north) — a coarse fix is plenty.
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberApproxLocation(granted: Boolean): State<Location?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Location?>(null) }
    DisposableEffect(granted) {
        val signal = CancellationSignal()
        if (granted) {
            state.value = lastKnownLocation(context) ?: state.value
            val lm = context.locationManager()
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            if (provider != null) runCatching {
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (loc != null) state.value = loc
                }
            }
        }
        onDispose { signal.cancel() }
    }
    return state
}

/** Continuous location updates from [provider] while resumed. */
@SuppressLint("MissingPermission")
@Composable
fun rememberLocationUpdates(
    enabled: Boolean,
    provider: String = LocationManager.GPS_PROVIDER,
    intervalMs: Long = 1000L,
    onLocation: (Location) -> Unit,
) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onLocation)
    LifecycleResumeEffect(enabled, provider, intervalMs) {
        val lm = context.locationManager()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = callback(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        val ok = enabled && AppPermission.LOCATION.isGranted(context) &&
            runCatching { lm.requestLocationUpdates(provider, intervalMs, 0f, listener, Looper.getMainLooper()) }.isSuccess
        onPauseOrDispose { if (ok) lm.removeUpdates(listener) }
    }
}

fun isProviderEnabled(context: Context, provider: String = LocationManager.GPS_PROVIDER): Boolean =
    runCatching { context.locationManager().isProviderEnabled(provider) }.getOrDefault(false)

// ---------------------------------------------------------------------------------------------
// Vibration
// ---------------------------------------------------------------------------------------------

/** Direct vibrator access for proportional pulses (honors the in-app haptics setting). */
class PulseVibrator(context: Context, private val enabled: Boolean) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val usable: Boolean get() = enabled && vibrator?.hasVibrator() == true

    /** One pulse; [strength] in 0..1. */
    fun pulse(durationMs: Long, strength: Float) {
        val v = vibrator ?: return
        if (!usable) return
        val amp = if (v.hasAmplitudeControl()) (30 + strength.coerceIn(0f, 1f) * 225).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        runCatching { v.vibrate(VibrationEffect.createOneShot(durationMs, amp)) }
    }

    fun cancel() { runCatching { vibrator?.cancel() } }
}

@Composable
fun rememberPulseVibrator(): PulseVibrator {
    val context = LocalContext.current
    val enabled = LocalAppSettings.current.haptics
    val v = remember(enabled) { PulseVibrator(context.applicationContext, enabled) }
    DisposableEffect(v) { onDispose { v.cancel() } }
    return v
}
