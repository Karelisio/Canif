package com.karelisio.canif.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Minimal system-wide torch control for the quick settings tile and the lamp widget. */
object TorchControl {
    private val _on = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _on
    private var registered = false

    private fun manager(context: Context) = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun cameraId(context: Context): String? = runCatching {
        val m = manager(context)
        m.cameraIdList.firstOrNull { id ->
            val c = m.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: m.cameraIdList.firstOrNull { m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    fun observe(context: Context) {
        if (registered) return
        registered = true
        val id = cameraId(context) ?: return
        manager(context.applicationContext).registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) _on.value = enabled
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun set(context: Context, on: Boolean): Boolean {
        val id = cameraId(context) ?: return false
        return runCatching { manager(context).setTorchMode(id, on); _on.value = on }.isSuccess
    }

    fun toggle(context: Context): Boolean = set(context, !_on.value).let { _on.value }
}
