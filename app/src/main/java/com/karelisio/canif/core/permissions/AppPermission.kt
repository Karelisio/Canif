package com.karelisio.canif.core.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.karelisio.canif.core.i18n.Txt

/**
 * Every authorization Canif may ask for. Runtime ones use the permission dialog,
 * special ones open a system settings page and are re-checked when the user comes back.
 */
enum class AppPermission(
    val icon: ImageVector,
    val title: Txt,
    val rationale: Txt,
    val runtime: List<String>,
) {
    CAMERA(
        Icons.Rounded.CameraAlt,
        Txt("Caméra", "Camera"),
        Txt("Pour scanner les QR codes, la pipette de couleurs et le rapporteur en réalité augmentée.", "To scan QR codes, pick colors and use the camera protractor."),
        listOf(Manifest.permission.CAMERA),
    ),
    MICROPHONE(
        Icons.Rounded.Mic,
        Txt("Micro", "Microphone"),
        Txt("Pour l'accordeur et le sonomètre. Le son est analysé en direct, jamais enregistré.", "For the tuner and the sound meter. Audio is analysed live, never recorded."),
        listOf(Manifest.permission.RECORD_AUDIO),
    ),
    LOCATION(
        Icons.Rounded.LocationOn,
        Txt("Localisation précise", "Precise location"),
        Txt("Pour la vitesse GPS, l'altimètre, les coordonnées et le nord vrai. Rien ne quitte le téléphone.", "For GPS speed, altitude, coordinates and true north. Nothing leaves your phone."),
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    ),
    NOTIFICATIONS(
        Icons.Rounded.Notifications,
        Txt("Notifications", "Notifications"),
        Txt("Pour les minuteurs et le métronome en arrière-plan.", "For timers and the metronome running in the background."),
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
    ),
    EXACT_ALARMS(
        Icons.Rounded.AlarmOn,
        Txt("Alarmes exactes", "Exact alarms"),
        Txt("Pour que les minuteurs sonnent pile à l'heure, même téléphone en veille.", "So timers ring right on time, even when the phone sleeps."),
        emptyList(),
    ),
    BATTERY(
        Icons.Rounded.BatteryChargingFull,
        Txt("Pas d'optimisation batterie", "No battery optimization"),
        Txt("Pour que le métronome et les minuteurs ne soient pas coupés en arrière-plan (indispensable sur Honor / MagicOS).", "So the metronome and timers are not killed in the background (essential on Honor / MagicOS)."),
        emptyList(),
    ),
    INSTALL_APPS(
        Icons.Rounded.InstallMobile,
        Txt("Installer les mises à jour", "Install updates"),
        Txt("Pour installer les nouvelles versions de Canif directement depuis l'appli.", "To install new versions of Canif right from the app."),
        emptyList(),
    );

    /** Whether this permission makes sense on this Android version. */
    val applicable: Boolean
        get() = when (this) {
            NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33
            EXACT_ALARMS -> Build.VERSION.SDK_INT >= 31
            else -> true
        }

    val isSpecial: Boolean get() = runtime.isEmpty()

    fun isGranted(context: Context): Boolean = when (this) {
        EXACT_ALARMS -> Build.VERSION.SDK_INT < 31 ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        BATTERY -> (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
        INSTALL_APPS -> context.packageManager.canRequestPackageInstalls()
        LOCATION -> granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        else -> runtime.all { granted(context, it) }
    }

    /** Settings screen for special permissions. */
    fun settingsIntent(context: Context): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (this) {
            EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else appDetails(context)
            BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            INSTALL_APPS -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg)
            else -> appDetails(context)
        }
    }

    companion object {
        fun appDetails(context: Context) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

        fun forManifest(permission: String): AppPermission? = entries.firstOrNull { permission in it.runtime }

        private fun granted(context: Context, p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }
}
