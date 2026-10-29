package com.karelisio.canif.tools.text

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.SortByAlpha
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

private val PlainText = listOf("text/plain")

val textTools: List<Tool> = listOf(
    Tool(
        id = "text-counter",
        name = Txt("Compteur", "Counter"),
        category = Category.TEXT,
        icon = Icons.Rounded.Numbers,
        keywords = listOf("mots", "words", "caractères", "characters", "compter", "count", "lignes", "lines", "lecture", "reading time"),
        description = Txt("Mots, caractères, phrases, temps de lecture", "Words, characters, sentences, reading time"),
        accepts = PlainText,
    ) { TextCounterScreen() },
    Tool(
        id = "text-case",
        name = Txt("Casse", "Case"),
        category = Category.TEXT,
        icon = Icons.Rounded.Abc,
        keywords = listOf("majuscules", "uppercase", "minuscules", "lowercase", "titre", "title", "casse", "case", "camelcase", "snake"),
        description = Txt("Majuscules, minuscules, titre, phrase…", "Upper, lower, title, sentence case…"),
        accepts = PlainText,
    ) { TextCaseScreen() },
    Tool(
        id = "text-clean",
        name = Txt("Nettoyage", "Cleanup"),
        category = Category.TEXT,
        icon = Icons.Rounded.CleaningServices,
        keywords = listOf("nettoyer", "clean", "espaces", "spaces", "lignes vides", "empty lines", "doublons", "duplicates", "trim"),
        description = Txt("Espaces en trop, lignes vides, doublons", "Extra spaces, empty lines, duplicates"),
        accepts = PlainText,
    ) { TextCleanScreen() },
    Tool(
        id = "text-lines",
        name = Txt("Tri de lignes", "Sort lines"),
        category = Category.TEXT,
        icon = Icons.Rounded.SortByAlpha,
        keywords = listOf("trier", "sort", "lignes", "lines", "alphabétique", "alphabetical", "mélanger", "shuffle", "inverser", "reverse"),
        description = Txt("Trier, inverser, mélanger, dédoublonner", "Sort, reverse, shuffle, deduplicate"),
        accepts = PlainText,
    ) { TextLinesScreen() },
    Tool(
        id = "text-diff",
        name = Txt("Comparaison", "Compare"),
        category = Category.TEXT,
        icon = Icons.Rounded.Difference,
        keywords = listOf("diff", "comparer", "compare", "différences", "differences", "versions"),
        description = Txt("Différences entre deux textes", "Differences between two texts"),
        accepts = PlainText,
    ) { TextDiffScreen() },
)
