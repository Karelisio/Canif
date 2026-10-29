package com.karelisio.canif.tools.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.nav.ToolColumn
import java.text.BreakIterator
import java.text.NumberFormat
import kotlin.math.roundToLong

internal class TextStats(
    val words: Int,
    val chars: Int,
    val charsNoSpaces: Int,
    val lines: Int,
    val sentences: Int,
    val paragraphs: Int,
    val top: List<Pair<String, Int>>,
)

private val WordRegex = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")

internal fun textStats(t: String): TextStats {
    if (t.isEmpty()) return TextStats(0, 0, 0, 0, 0, 0, emptyList())
    val words = WordRegex.findAll(t).map { it.value }.toList()
    // User-perceived characters (emoji and accents count as one).
    val it = BreakIterator.getCharacterInstance(Lang.locale)
    it.setText(t)
    var chars = 0
    var spaces = 0
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        chars++
        if (t.substring(start, end).isBlank()) spaces++
        start = end
        end = it.next()
    }
    val sentences = Regex("[^.!?…]*[\\p{L}\\p{N}][^.!?…]*(?:[.!?…]+|$)").findAll(t).count()
    val paragraphs = t.split(Regex("\\n\\s*\\n")).count { p -> p.any { c -> c.isLetterOrDigit() } }
    val freq = words.asSequence().map { w -> w.lowercase(Lang.locale) }.filter { w -> w.length >= 4 }
        .groupingBy { w -> w }.eachCount().entries.sortedByDescending { e -> e.value }.take(6).map { e -> e.key to e.value }
    return TextStats(words.size, chars, chars - spaces, t.lines().size, sentences, paragraphs, freq.filter { p -> p.second > 1 })
}

internal fun durationText(seconds: Double): String {
    val s = seconds.roundToLong()
    return when {
        s == 0L -> "0 s"
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min" + if (s % 60 != 0L) " ${"%02d".format(s % 60)} s" else ""
        else -> "${s / 3600} h ${"%02d".format((s % 3600) / 60)}"
    }
}

@Composable
internal fun ToolScope.TextCounterScreen() {
    var text by rememberSaveable { mutableStateOf(incoming?.text ?: "") }
    val s = remember(text) { textStats(text) }
    val nf = remember(Lang.locale) { NumberFormat.getIntegerInstance(Lang.locale) }
    ToolColumn {
        TextInput(text, { text = it }, tr("Votre texte", "Your text"))
        val c = MaterialTheme.colorScheme
        val items = listOf(
            Triple(tr("Mots", "Words"), nf.format(s.words), c.primaryContainer),
            Triple(tr("Caractères", "Characters"), nf.format(s.chars), c.secondaryContainer),
            Triple(tr("Sans espaces", "No spaces"), nf.format(s.charsNoSpaces), c.surfaceContainerHigh),
            Triple(tr("Lignes", "Lines"), nf.format(s.lines), c.surfaceContainerHigh),
            Triple(tr("Phrases", "Sentences"), nf.format(s.sentences), c.surfaceContainerHigh),
            Triple(tr("Paragraphes", "Paragraphs"), nf.format(s.paragraphs), c.surfaceContainerHigh),
            Triple(tr("Lecture (230 mots/min)", "Reading (230 wpm)"), durationText(s.words / 230.0 * 60), c.tertiaryContainer),
            Triple(tr("À voix haute (150 mots/min)", "Speaking (150 wpm)"), durationText(s.words / 150.0 * 60), c.tertiaryContainer),
        )
        items.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { i, (label, value, color) ->
                    StatBlock(label, value, Modifier.weight(1f).cascade("stat-$row-$i", row * 2 + i), color = color)
                }
            }
        }
        if (s.top.isNotEmpty()) {
            SectionTitle(tr("Mots les plus fréquents", "Most frequent words"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.top.forEach { (w, n) -> AssistChip(onClick = {}, label = { Text("$w  ×$n") }) }
            }
        }
    }
}
