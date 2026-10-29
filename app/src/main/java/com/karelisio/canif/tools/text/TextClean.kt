package com.karelisio.canif.tools.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.nav.ToolColumn

internal class CleanOptions(
    val trim: Boolean = true,
    val spaces: Boolean = true,
    val emptyLines: Boolean = false,
    val collapseEmpty: Boolean = true,
    val duplicates: Boolean = false,
    val invisible: Boolean = true,
)

internal class CleanResult(val text: String, val spaces: Int, val emptyLines: Int, val duplicates: Int, val invisible: Int)

private val Invisible = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
private val OddSpaces = Regex("[\\u00A0\\u2007\\u202F\\t]")

internal fun cleanText(input: String, o: CleanOptions): CleanResult {
    var t = input.replace("\r\n", "\n").replace('\r', '\n')
    var invisible = 0
    if (o.invisible) {
        invisible = Invisible.findAll(t).count()
        t = t.replace(Invisible, "")
    }
    var spaces = 0
    var lines = t.split('\n')
    if (o.spaces) {
        lines = lines.map { l ->
            val n = l.replace(OddSpaces, " ")
            // Keep leading indentation, collapse runs elsewhere.
            val lead = n.takeWhile { it == ' ' }
            val body = n.drop(lead.length).replace(Regex(" {2,}"), " ")
            spaces += l.length - (lead.length + body.length)
            lead + body
        }
    }
    if (o.trim) {
        lines = lines.map { l -> val trimmed = l.trim(); spaces += l.length - trimmed.length; trimmed }
    }
    var empty = 0
    if (o.emptyLines) {
        val before = lines.size
        lines = lines.filter { it.isNotBlank() }
        empty = before - lines.size
    } else if (o.collapseEmpty) {
        val out = ArrayList<String>(lines.size)
        for (l in lines) {
            if (l.isBlank() && (out.isEmpty() || out.last().isBlank())) { empty++; continue }
            out += l
        }
        while (out.isNotEmpty() && out.last().isBlank()) { out.removeAt(out.lastIndex); empty++ }
        lines = out
    }
    var dup = 0
    if (o.duplicates) {
        val seen = HashSet<String>()
        lines = lines.filter { l -> if (l.isBlank()) true else if (seen.add(l.trim())) true else { dup++; false } }
    }
    return CleanResult(lines.joinToString("\n"), spaces, empty, dup, invisible)
}

@Composable
internal fun ToolScope.TextCleanScreen() {
    var text by rememberSaveable { mutableStateOf(incoming?.text ?: "") }
    var trim by rememberSaveable { mutableStateOf(true) }
    var spaces by rememberSaveable { mutableStateOf(true) }
    var emptyLines by rememberSaveable { mutableStateOf(false) }
    var collapse by rememberSaveable { mutableStateOf(true) }
    var dups by rememberSaveable { mutableStateOf(false) }
    var invisible by rememberSaveable { mutableStateOf(true) }
    val r = remember(text, trim, spaces, emptyLines, collapse, dups, invisible) {
        cleanText(text, CleanOptions(trim, spaces, emptyLines, collapse, dups, invisible))
    }
    ToolColumn {
        TextInput(text, { text = it }, tr("Texte à nettoyer", "Text to clean"), minHeight = 140)
        CanifCard {
            OptionSwitch(tr("Espaces en trop", "Extra spaces"), spaces, { spaces = it }, tr("Espaces multiples, tabulations, espaces insécables", "Repeated spaces, tabs, non-breaking spaces"))
            OptionSwitch(tr("Rogner les lignes", "Trim lines"), trim, { trim = it }, tr("Espaces en début et fin de ligne", "Spaces at line start and end"))
            OptionSwitch(tr("Supprimer les lignes vides", "Remove empty lines"), emptyLines, { emptyLines = it })
            if (!emptyLines) OptionSwitch(tr("Fusionner les lignes vides", "Collapse empty lines"), collapse, { collapse = it }, tr("Une seule ligne vide entre les paragraphes", "A single empty line between paragraphs"))
            OptionSwitch(tr("Supprimer les doublons", "Remove duplicate lines"), dups, { dups = it })
            OptionSwitch(tr("Caractères invisibles", "Invisible characters"), invisible, { invisible = it }, tr("Espaces de largeur nulle, traits d'union conditionnels", "Zero-width spaces, soft hyphens"))
        }
        val c = MaterialTheme.colorScheme
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("Espaces retirés", "Spaces removed"), r.spaces.toString(), Modifier.weight(1f), c.primaryContainer)
            StatBlock(tr("Lignes vides", "Empty lines"), r.emptyLines.toString(), Modifier.weight(1f), c.secondaryContainer)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("Doublons", "Duplicates"), r.duplicates.toString(), Modifier.weight(1f), c.tertiaryContainer)
            StatBlock(tr("Invisibles", "Invisible"), r.invisible.toString(), Modifier.weight(1f), c.surfaceContainerHigh)
        }
        TextResult(r.text, tr("Texte nettoyé · ${text.length} → ${r.text.length} caractères", "Cleaned text · ${text.length} → ${r.text.length} characters"))
        ResultActions(r.text)
    }
}
