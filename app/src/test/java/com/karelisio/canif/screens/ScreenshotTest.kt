package com.karelisio.canif.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.settings.AppSettings
import com.karelisio.canif.core.settings.ThemeMode
import com.karelisio.canif.core.tools.ToolRegistry
import com.karelisio.canif.ui.home.HomeScreen
import com.karelisio.canif.ui.nav.ToolHost
import com.karelisio.canif.ui.onboarding.OnboardingScreen
import com.karelisio.canif.ui.settings.SettingsScreen
import com.karelisio.canif.ui.theme.CanifTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens to PNG (screenshots/), to review the visual design without a device.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest(private val screen: String, private val dark: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun render() {
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val app = ApplicationProvider.getApplicationContext<CanifApp>()
        shadowOf(app).grantPermissions(
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        java.util.Locale.setDefault(java.util.Locale.FRENCH)
        Lang.preference = "fr"
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CanifTheme(AppSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false, onboardingDone = true)) {
                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        "home" -> HomeScreen(onOpen = { _, _ -> }, onSettings = {}, showIntroLogo = false)
                        "onboarding" -> OnboardingScreen(onDone = {})
                        "settings" -> SettingsScreen(onBack = {}, onOpen = {})
                        else -> ToolHost(ToolRegistry.get(screen)!!, "x", null, onBack = {}, navigate = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2500)
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(dir, "$screen${if (dark) "-dark" else ""}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun params(): List<Array<Any>> {
            val light = listOf(
                "home", "onboarding", "settings",
                "percent", "units", "currency", "dates", "vat", "loan", "compound", "tip",
                "level", "ruler", "protractor", "compass", "luxmeter", "metal", "altimeter", "speed", "sound-meter",
                "tuner", "metronome", "tone-gen",
                "text-counter", "text-case", "text-diff",
                "images", "img2pdf", "pdf-merge",
                "dice", "coin", "wheel", "random-number", "teams", "scoreboard",
                "qr-gen", "color-picker", "color-convert", "palette",
                "stopwatch", "timers", "intervals", "reflex",
                "flashlight", "sos", "morse", "screen-light", "coordinates",
            )
            val dark = listOf("home", "tuner", "metronome", "level", "compass", "dice", "palette", "timers")
            return light.map { arrayOf<Any>(it, false) } + dark.map { arrayOf<Any>(it, true) }
        }
    }
}
