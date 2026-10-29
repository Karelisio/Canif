package com.karelisio.canif.tools.outdoor

import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Sos
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val outdoorTools: List<Tool> = listOf(
    Tool(
        id = "flashlight",
        name = Txt("Lampe torche", "Flashlight"),
        category = Category.OUTDOOR,
        icon = Icons.Rounded.FlashlightOn,
        keywords = listOf("lampe", "torche", "flash", "flashlight", "torch", "lumière", "light", "led"),
        description = Txt("Le flash en lampe, intensité réglable", "The flash as a torch, adjustable brightness"),
        features = listOf(Tool.FEATURE_FLASH),
    ) { FlashlightScreen() },
    Tool(
        id = "sos",
        name = Txt("SOS", "SOS"),
        category = Category.OUTDOOR,
        icon = Icons.Rounded.Sos,
        keywords = listOf("sos", "urgence", "emergency", "détresse", "distress", "morse", "secours", "help", "signal"),
        description = Txt("Signal de détresse lumineux en morse", "Light distress signal in Morse"),
    ) { SosScreen() },
    Tool(
        id = "morse",
        name = Txt("Texte vers morse", "Text to Morse"),
        category = Category.OUTDOOR,
        icon = Icons.Rounded.GraphicEq,
        keywords = listOf("morse", "code", "traduire", "translate", "signal", "lumière", "light", "télégraphe", "telegraph"),
        description = Txt("Traduit et émet en morse", "Translates and signals in Morse"),
        accepts = listOf("text/plain"),
    ) { MorseScreen() },
    Tool(
        id = "screen-light",
        name = Txt("Écran lumineux", "Screen light"),
        category = Category.OUTDOOR,
        icon = Icons.Rounded.LightMode,
        keywords = listOf("écran", "screen", "lumière", "light", "veilleuse", "night", "rouge", "red", "lampe", "lamp", "couleur", "color"),
        description = Txt("L'écran en lumière douce de la couleur voulue", "Your screen as a soft light of any color"),
        fullscreen = true,
    ) { ScreenLightScreen() },
    Tool(
        id = "coordinates",
        name = Txt("Coordonnées GPS", "GPS coordinates"),
        category = Category.OUTDOOR,
        icon = Icons.Rounded.MyLocation,
        keywords = listOf("gps", "coordonnées", "coordinates", "position", "latitude", "longitude", "localisation", "location", "dms", "altitude", "osm"),
        description = Txt("Votre position, à copier ou partager", "Your position, to copy or share"),
        features = listOf(PackageManager.FEATURE_LOCATION),
        permissions = listOf(AppPermission.LOCATION),
    ) { CoordinatesScreen() },
)
