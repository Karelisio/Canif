package com.karelisio.canif.tools.time

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HourglassBottom
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val timeTools: List<Tool> = listOf(
    Tool(
        id = "stopwatch",
        name = Txt("Chronomètre", "Stopwatch"),
        category = Category.TIME,
        icon = Icons.Rounded.AvTimer,
        keywords = listOf("chrono", "chronometre", "tour", "lap", "temps", "time", "stopwatch", "split"),
        description = Txt("Chronomètre au centième avec tours", "Hundredth-second stopwatch with laps"),
    ) { StopwatchScreen() },
    Tool(
        id = "timers",
        name = Txt("Minuteurs", "Timers"),
        category = Category.TIME,
        icon = Icons.Rounded.HourglassBottom,
        keywords = listOf("minuteur", "compte a rebours", "timer", "countdown", "alarme", "alarm", "cuisine", "kitchen", "oeuf", "egg"),
        description = Txt("Plusieurs minuteurs nommés qui sonnent même appli fermée", "Several named timers that ring even when the app is closed"),
        permissions = listOf(AppPermission.NOTIFICATIONS),
    ) { TimersScreen() },
    Tool(
        id = "intervals",
        name = Txt("Intervalles HIIT", "HIIT intervals"),
        category = Category.TIME,
        icon = Icons.Rounded.FitnessCenter,
        keywords = listOf("hiit", "tabata", "interval", "intervalles", "sport", "workout", "entrainement", "fractionne", "boxe", "round"),
        description = Txt("Travail / repos avec annonces vocales", "Work / rest with voice cues"),
        permissions = listOf(AppPermission.NOTIFICATIONS),
    ) { IntervalsScreen() },
    Tool(
        id = "reflex",
        name = Txt("Test de réflexes", "Reflex test"),
        category = Category.TIME,
        icon = Icons.Rounded.Bolt,
        keywords = listOf("reflexe", "reflex", "reaction", "temps de reaction", "reaction time", "jeu", "game", "rapidite"),
        description = Txt("Mesurez votre temps de réaction", "Measure your reaction time"),
        fullscreen = true,
    ) { ReflexScreen(onBack) },
)
