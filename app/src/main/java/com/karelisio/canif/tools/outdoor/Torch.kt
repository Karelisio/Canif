package com.karelisio.canif.tools.outdoor

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide torch control, usable from screens, a Quick Settings tile or a widget.
 * The state follows the system, so a torch switched off elsewhere is reflected here.
 */
object Torch {
    private val _isOn = MutableStateFlow(false)
    /** Whether the torch is currently lit. */
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _level = MutableStateFlow(1)
    /** Current strength level (1 when strength control is not supported). */
    val level: StateFlow<Int> = _level.asStateFlow()

    @Volatile private var cameraId: String? = null
    @Volatile private var initialized = false
    @Volatile private var maxLevelCache = 1
    @Volatile private var defaultLevelCache = 1

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) _isOn.value = enabled
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) _isOn.value = false
        }

        override fun onTorchStrengthLevelChanged(id: String, newStrengthLevel: Int) {
            if (id == cameraId) _level.value = newStrengthLevel
        }
    }

    private fun manager(context: Context) = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val m = manager(context)
            runCatching {
                val ids = m.cameraIdList
                val withFlash = ids.filter { m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                cameraId = withFlash.firstOrNull {
                    m.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: withFlash.firstOrNull()
                cameraId?.let { id ->
                    if (Build.VERSION.SDK_INT >= 33) {
                        val ch = m.getCameraCharacteristics(id)
                        maxLevelCache = ch.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                        defaultLevelCache = ch.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1
                        _level.value = runCatching { m.getTorchStrengthLevel(id) }.getOrDefault(defaultLevelCache)
                    }
                }
                m.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            }
            initialized = true
        }
    }

    fun isAvailable(context: Context): Boolean {
        init(context)
        return cameraId != null
    }

    /** Highest strength level; above 1 means the intensity can be adjusted (Android 13+). */
    fun maxLevel(context: Context): Int {
        init(context)
        return maxLevelCache
    }

    fun defaultLevel(context: Context): Int {
        init(context)
        return defaultLevelCache
    }

    val supportsStrength: Boolean get() = maxLevelCache > 1

    /** Switches the torch; returns false when the flash is busy (camera in use) or missing. */
    fun set(context: Context, on: Boolean): Boolean {
        init(context)
        val id = cameraId ?: return false
        return try {
            manager(context).setTorchMode(id, on)
            _isOn.value = on
            true
        } catch (_: CameraAccessException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun on(context: Context) = set(context, true)

    fun off(context: Context) = set(context, false)

    fun toggle(context: Context): Boolean = set(context, !_isOn.value)

    /** Lights the torch at [level] (1..[maxLevel]); falls back to plain on when unsupported. */
    fun setLevel(context: Context, level: Int): Boolean {
        init(context)
        val id = cameraId ?: return false
        if (Build.VERSION.SDK_INT < 33 || maxLevelCache <= 1) return set(context, true)
        return try {
            val l = level.coerceIn(1, maxLevelCache)
            manager(context).turnOnTorchWithStrengthLevel(id, l)
            _level.value = l
            _isOn.value = true
            true
        } catch (_: Exception) {
            false
        }
    }
}
