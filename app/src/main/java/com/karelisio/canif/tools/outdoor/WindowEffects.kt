package com.karelisio.canif.tools.outdoor

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.karelisio.canif.ui.permissions.findActivity

/** The window hosting the current composable (a dialog's own window, or the activity's). */
@Composable
fun currentWindow(): Window? {
    val view = LocalView.current
    val context = LocalContext.current
    return (view.parent as? DialogWindowProvider)?.window ?: context.findActivity()?.window
}

/**
 * While [enabled]: window brightness forced to max and screen kept on. Restored on dispose.
 */
@Composable
fun MaxBrightness(enabled: Boolean = true, keepScreenOn: Boolean = true) {
    val window = currentWindow()
    DisposableEffect(window, enabled) {
        val w = window
        if (w == null || !enabled) return@DisposableEffect onDispose { }
        val previous = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        if (keepScreenOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            w.attributes = w.attributes.apply { screenBrightness = previous }
            if (keepScreenOn) w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Keeps the screen awake while composed. */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (enabled) view.keepScreenOn = true
        onDispose { if (enabled) view.keepScreenOn = false }
    }
}

/** Hides status and navigation bars while composed (swipe from an edge shows them briefly). */
@Composable
fun ImmersiveMode(enabled: Boolean = true) {
    val window = currentWindow()
    val view = LocalView.current
    DisposableEffect(window, enabled) {
        val w = window
        if (w == null || !enabled) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(w, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
