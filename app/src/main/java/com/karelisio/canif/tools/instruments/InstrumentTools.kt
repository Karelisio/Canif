package com.karelisio.canif.tools.instruments

import android.content.pm.PackageManager
import android.hardware.Sensor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.WbSunny
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

private val hasBarometer: Boolean by lazy {
    runCatching { CanifApp.instance.hasSensor(Sensor.TYPE_PRESSURE) }.getOrDefault(false)
}

val instrumentTools: List<Tool> = listOf(
    Tool(
        id = "level",
        name = Txt("Niveau à bulle", "Bubble level"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.HorizontalRule,
        keywords = listOf("niveau", "bulle", "horizontal", "aplomb", "inclinaison", "level", "spirit", "bubble", "tilt", "plumb"),
        description = Txt("À plat ou sur la tranche, au dixième de degré.", "Flat or on its edge, to a tenth of a degree."),
        sensors = listOf(Sensor.TYPE_ACCELEROMETER),
    ) { LevelScreen() },
    Tool(
        id = "ruler",
        name = Txt("Règle", "Ruler"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.Straighten,
        keywords = listOf("règle", "mesure", "centimètre", "millimètre", "pouce", "longueur", "ruler", "measure", "cm", "inch", "length"),
        description = Txt("Règle à l'écran, calibrée avec une carte bancaire.", "On-screen ruler, calibrated with a bank card."),
        fullscreen = true,
    ) { RulerScreen(onBack) },
    Tool(
        id = "protractor",
        name = Txt("Rapporteur", "Protractor"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.Architecture,
        keywords = listOf("rapporteur", "angle", "degré", "pente", "inclinomètre", "caméra", "protractor", "degree", "slope", "inclinometer", "camera"),
        description = Txt("Angle par inclinaison ou sur l'image de la caméra.", "Angles by tilt or over the camera image."),
        sensors = listOf(Sensor.TYPE_ACCELEROMETER),
    ) { ProtractorScreen() },
    Tool(
        id = "compass",
        name = Txt("Boussole", "Compass"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.Explore,
        keywords = listOf("boussole", "nord", "cap", "orientation", "magnétique", "compass", "north", "heading", "bearing", "magnetic"),
        description = Txt("Nord magnétique et nord vrai.", "Magnetic and true north."),
        sensors = listOf(Sensor.TYPE_MAGNETIC_FIELD),
    ) { CompassScreen() },
    Tool(
        id = "luxmeter",
        name = Txt("Luxmètre", "Light meter"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.WbSunny,
        keywords = listOf("lux", "lumière", "luminosité", "éclairage", "luxmètre", "light", "brightness", "illuminance", "meter"),
        description = Txt("Éclairement en lux avec repères.", "Illuminance in lux with references."),
        sensors = listOf(Sensor.TYPE_LIGHT),
    ) { LuxMeterScreen() },
    Tool(
        id = "metal",
        name = Txt("Détecteur de métaux", "Metal detector"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.Radar,
        keywords = listOf("métal", "détecteur", "aimant", "magnétique", "champ", "tesla", "metal", "detector", "magnet", "magnetic", "field", "stud"),
        description = Txt("Champ magnétique en µT, avec vibration.", "Magnetic field in µT, with vibration."),
        sensors = listOf(Sensor.TYPE_MAGNETIC_FIELD),
    ) { MetalDetectorScreen() },
    Tool(
        id = "altimeter",
        name = Txt("Altimètre", "Altimeter"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.Terrain,
        keywords = listOf("altitude", "altimètre", "baromètre", "pression", "hpa", "montagne", "altimeter", "barometer", "pressure", "elevation"),
        description = Txt("Baromètre ou GPS.", "Barometer or GPS."),
        features = if (hasBarometer) emptyList() else listOf(PackageManager.FEATURE_LOCATION_GPS),
        permissions = if (hasBarometer) emptyList() else listOf(AppPermission.LOCATION),
    ) { AltimeterScreen() },
    Tool(
        id = "speed",
        name = Txt("Vitesse GPS", "GPS speed"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.Speed,
        keywords = listOf("vitesse", "compteur", "gps", "km/h", "distance", "speed", "speedometer", "mph", "odometer"),
        description = Txt("Compteur de vitesse, distance et vitesse max.", "Speedometer, distance and top speed."),
        features = listOf(PackageManager.FEATURE_LOCATION_GPS),
        permissions = listOf(AppPermission.LOCATION),
    ) { SpeedScreen() },
)
