package com.karelisio.canif.tools.random

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Attractions
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Scoreboard
import androidx.compose.material.icons.rounded.Toll
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val randomTools: List<Tool> = listOf(
    Tool(
        id = "dice",
        name = Txt("Dés", "Dice"),
        category = Category.RANDOM,
        icon = Icons.Rounded.Casino,
        keywords = listOf("dé", "dés", "dice", "die", "d6", "d20", "jeu", "game", "lancer", "roll"),
        description = Txt("Jusqu'à 10 dés de d4 à d20, secouez pour lancer", "Up to 10 dice from d4 to d20, shake to roll"),
    ) { DiceScreen() },
    Tool(
        id = "coin",
        name = Txt("Pile ou face", "Coin flip"),
        category = Category.RANDOM,
        icon = Icons.Rounded.Toll,
        keywords = listOf("pièce", "coin", "pile", "face", "heads", "tails", "flip"),
        description = Txt("Une pièce en 3D, avec compteurs", "A 3D coin, with counters"),
    ) { CoinScreen() },
    Tool(
        id = "wheel",
        name = Txt("Roue de décision", "Decision wheel"),
        category = Category.RANDOM,
        icon = Icons.Rounded.Attractions,
        keywords = listOf("roue", "wheel", "décision", "decision", "choix", "choice", "tirage", "spin", "fortune"),
        description = Txt("Faites tourner la roue pour décider", "Spin the wheel to decide"),
    ) { WheelScreen() },
    Tool(
        id = "random-number",
        name = Txt("Nombre aléatoire", "Random number"),
        category = Category.RANDOM,
        icon = Icons.Rounded.Pin,
        keywords = listOf("aléatoire", "random", "nombre", "number", "tirage", "draw", "loto", "lottery", "rng"),
        description = Txt("Tirage de nombres, avec ou sans doublons", "Draw numbers, with or without duplicates"),
    ) { RandomNumberScreen() },
    Tool(
        id = "teams",
        name = Txt("Tirage d'équipes", "Team picker"),
        category = Category.RANDOM,
        icon = Icons.Rounded.Groups,
        keywords = listOf("équipes", "teams", "groupes", "groups", "répartir", "split", "joueurs", "players"),
        description = Txt("Répartir des participants au hasard", "Split people into random teams"),
    ) { TeamsScreen() },
    Tool(
        id = "scoreboard",
        name = Txt("Tableau des scores", "Scoreboard"),
        category = Category.RANDOM,
        icon = Icons.Rounded.Scoreboard,
        keywords = listOf("score", "points", "compteur", "counter", "match", "jeu", "game", "joueurs", "players"),
        description = Txt("De 2 à 4 joueurs, plein écran", "2 to 4 players, full screen"),
        fullscreen = true,
    ) { ScoreboardScreen() },
)
