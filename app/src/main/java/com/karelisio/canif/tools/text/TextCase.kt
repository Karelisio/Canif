package com.karelisio.canif.tools.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.nav.ToolColumn
import java.text.Normalizer
import java.util.Locale

internal enum class CaseMode { UPPER, LOWER, TITLE, SENTENCE, SWAP, CAMEL, SNAKE, KEBAB }

private val FrSmall = setOf(
    "a", "à", "au", "aux", "de", "des", "du", "d", "en", "et", "la", "le", "les", "l", "ou", "où", "par", "pour", "sur", "sous",
    "un", "une", "dans", "avec", "sans", "chez", "entre", "vers", "ni", "mais", "que", "qu", "se", "s", "ce", "son", "sa", "ses",
)
private val EnSmall = setOf(
    "a", "an", "the", "and", "but", "or", "nor", "for", "so", "yet", "as", "at", "by", "in", "of", "off", "on", "per", "to", "up", "via", "vs", "from", "into", "with",
)

private fun String.cap(locale: Locale) = if (isEmpty()) this else substring(0, 1).uppercase(locale) + substring(1)

/** Title case: small words stay lowercase (except first/last), elisions keep their apostrophe (l'Arbre), hyphens split words. */
internal fun titleCase(text: String, fr: Boolean): String {
    val locale = if (fr) Locale.FRENCH else Locale.ENGLISH
    val small = if (fr) FrSmall else EnSmall
    return text.lines().joinToString("\n") { line ->
        val tokens = Regex("[\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+").findAll(line).map { it.value }.toList()
        val wordIdx = tokens.indices.filter { tokens[it][0].isLetterOrDigit() }
        val first = wordIdx.firstOrNull()
        val last = wordIdx.lastOrNull()
        val sb = StringBuilder()
        tokens.forEachIndexed { i, tk ->
            if (!tk[0].isLetterOrDigit()) { sb.append(tk); return@forEachIndexed }
            val lower = tk.lowercase(locale)
            val prev = tokens.getOrNull(i - 1)
            val afterSentence = prev != null && Regex("[.!?:]\\s*$").containsMatchIn(prev)
            // Elided articles (l', d', qu') stay lowercase in French, the following word is capitalised.
            val elided = fr && tokens.getOrNull(i + 1)?.let { it == "'" || it == "’" } == true && lower in setOf("l", "d", "qu", "j", "m", "n", "s", "t", "c")
            val keepSmall = (lower in small || elided) && i != first && i != last && !afterSentence
            // Words already in all caps (acronyms) are kept.
            sb.append(
                when {
                    tk.length > 1 && tk.all { !it.isLetter() || it.isUpperCase() } -> tk
                    keepSmall -> lower
                    elided && i == first -> lower.cap(locale)
                    else -> lower.cap(locale)
                },
            )
        }
        sb.toString()
    }
}

/** Sentence case: lowercase everything, then capitalise the first letter of each sentence and line. */
internal fun sentenceCase(text: String, locale: Locale): String {
    val lower = text.lowercase(locale)
    val sb = StringBuilder(lower.length)
    var capNext = true
    for (ch in lower) {
        if (capNext && ch.isLetter()) { sb.append(ch.uppercase(locale)); capNext = false; continue }
        sb.append(ch)
        if (ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == '\n') capNext = true
        else if (ch.isLetterOrDigit()) capNext = false
    }
    // English pronoun "I".
    return if (locale.language == "en") sb.toString().replace(Regex("\\bi\\b"), "I") else sb.toString()
}

private fun words(text: String): List<String> {
    val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    // Split on non-alphanumerics and on lower→Upper transitions (camelCase input).
    return plain.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) }
}

internal fun convertCase(text: String, mode: CaseMode): String {
    val locale = Lang.locale
    return when (mode) {
        CaseMode.UPPER -> text.uppercase(locale)
        CaseMode.LOWER -> text.lowercase(locale)
        CaseMode.TITLE -> titleCase(text, Lang.isFr)
        CaseMode.SENTENCE -> sentenceCase(text, locale)
        CaseMode.SWAP -> buildString(text.length) {
            for (ch in text) append(if (ch.isUpperCase()) ch.lowercaseChar() else if (ch.isLowerCase()) ch.uppercaseChar() else ch)
        }
        CaseMode.CAMEL -> words(text).mapIndexed { i, w -> if (i == 0) w else w.cap(Locale.ROOT) }.joinToString("")
        CaseMode.SNAKE -> words(text).joinToString("_")
        CaseMode.KEBAB -> words(text).joinToString("-")
    }
}

@Composable
internal fun ToolScope.TextCaseScreen() {
    var text by rememberSaveable { mutableStateOf(incoming?.text ?: "") }
    var mode by rememberSaveable { mutableStateOf(CaseMode.UPPER) }
    val out = remember(text, mode, Lang.isFr) { convertCase(text, mode) }
    ToolColumn {
        TextInput(text, { text = it }, tr("Texte à transformer", "Text to transform"), minHeight = 120)
        SectionTitle(tr("Casse", "Case"))
        ChipChoice(
            CaseMode.entries.take(5), mode,
            label = {
                when (it) {
                    CaseMode.UPPER -> tr("MAJUSCULES", "UPPERCASE")
                    CaseMode.LOWER -> tr("minuscules", "lowercase")
                    CaseMode.TITLE -> tr("Titre", "Title Case")
                    CaseMode.SENTENCE -> tr("Phrase", "Sentence case")
                    else -> tr("iNVERSER", "sWAP cASE")
                }
            },
            onSelect = { mode = it },
        )
        SectionTitle(tr("Développeur", "Developer"))
        ChipChoice(
            CaseMode.entries.drop(5), mode,
            label = {
                when (it) {
                    CaseMode.CAMEL -> "camelCase"
                    CaseMode.SNAKE -> "snake_case"
                    else -> "kebab-case"
                }
            },
            onSelect = { mode = it },
        )
        AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "case") { m ->
            TextResult(if (m == mode) out else convertCase(text, m), tr("Résultat", "Result"))
        }
        ResultActions(out)
    }
}
