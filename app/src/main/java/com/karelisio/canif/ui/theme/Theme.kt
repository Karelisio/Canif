package com.karelisio.canif.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.karelisio.canif.core.settings.AppSettings
import com.karelisio.canif.core.settings.ThemeMode

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** True when the user disabled animations system-wide ("Remove animations"). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun CanifTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> SteelDark
        else -> SteelLight
    }
    val tinted = if (settings.tintedSurfaces) scheme.tinted(dark) else scheme
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalAppSettings provides settings, LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = tinted,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            typography = CanifTypography,
            content = content,
        )
    }
}

/**
 * Stronger Material You flavor: surfaces take on the palette's primary container instead of near white
 * (or near black). Low containers stay lighter than the background so cards still read as layers.
 */
fun ColorScheme.tinted(dark: Boolean): ColorScheme {
    val tint = primaryContainer
    fun Color.mix(light: Float, darkAmount: Float) = lerp(this, tint, if (dark) darkAmount else light)
    return copy(
        background = background.mix(0.5f, 0.22f),
        surface = surface.mix(0.5f, 0.22f),
        surfaceBright = surfaceBright.mix(0.4f, 0.3f),
        surfaceDim = surfaceDim.mix(0.6f, 0.18f),
        surfaceContainerLowest = surfaceContainerLowest.mix(0.1f, 0.12f),
        surfaceContainerLow = surfaceContainerLow.mix(0.22f, 0.28f),
        surfaceContainer = surfaceContainer.mix(0.4f, 0.33f),
        surfaceContainerHigh = surfaceContainerHigh.mix(0.6f, 0.38f),
        surfaceContainerHighest = surfaceContainerHighest.mix(0.7f, 0.42f),
        surfaceVariant = surfaceVariant.mix(0.4f, 0.3f),
    )
}
