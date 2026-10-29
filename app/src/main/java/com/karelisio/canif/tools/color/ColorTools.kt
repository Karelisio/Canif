package com.karelisio.canif.tools.color

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val colorTools: List<Tool> = listOf(
    Tool(
        id = "color-picker",
        name = Txt("Pipette", "Color picker"),
        category = Category.COLOR,
        icon = Icons.Rounded.Colorize,
        keywords = listOf("pipette", "couleur", "color", "colour", "caméra", "camera", "picker", "eyedropper", "hex", "rgb", "nom", "name"),
        description = Txt("La couleur de ce que vise la caméra", "The color of what the camera sees"),
        features = listOf(Tool.FEATURE_CAMERA),
        permissions = listOf(AppPermission.CAMERA),
    ) { ColorPickerScreen() },
    Tool(
        id = "color-convert",
        name = Txt("Conversion couleurs", "Color converter"),
        category = Category.COLOR,
        icon = Icons.Rounded.Tune,
        keywords = listOf("couleur", "color", "hex", "rgb", "hsl", "hsv", "convertir", "convert", "code"),
        description = Txt("HEX, RGB, HSL et HSV", "HEX, RGB, HSL and HSV"),
    ) { ColorConvertScreen() },
    Tool(
        id = "palette",
        name = Txt("Palettes", "Palettes"),
        category = Category.COLOR,
        icon = Icons.Rounded.Palette,
        keywords = listOf("palette", "couleurs", "colors", "harmonie", "harmony", "complémentaire", "complementary", "analogue", "triadique", "monochrome", "design"),
        description = Txt("Palettes harmonieuses à partir d'une couleur", "Harmonious palettes from one color"),
    ) { PaletteScreen() },
)
