package com.karelisio.canif.tools.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.launch
import java.text.Collator
import kotlin.random.Random

internal enum class LineOp { AZ, ZA, NATURAL, LENGTH, REVERSE, SHUFFLE, DEDUP }

/** Natural order: "file2" < "file10", numbers compared by value, text with the locale collator. */
internal class NaturalComparator(private val collator: Collator) : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a).map { it.value }.toList()
        val cb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val r = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny).takeIf { it != 0 } ?: (x.length - y.length)
            } else collator.compare(x, y)
            if (r != 0) return r
        }
        return ca.size - cb.size
    }
}

internal fun processLines(text: String, op: LineOp, ignoreCase: Boolean, skipEmpty: Boolean, seed: Long): List<String> {
    var lines = text.replace("\r\n", "\n").split('\n')
    if (skipEmpty) lines = lines.filter { it.isNotBlank() }
    val collator = Collator.getInstance(Lang.locale).apply { strength = if (ignoreCase) Collator.SECONDARY else Collator.TERTIARY }
    return when (op) {
        LineOp.AZ -> lines.sortedWith(collator)
        LineOp.ZA -> lines.sortedWith(collator).reversed()
        LineOp.NATURAL -> lines.sortedWith(NaturalComparator(collator))
        LineOp.LENGTH -> lines.sortedWith(compareBy<String> { it.length }.thenComparing(collator))
        LineOp.REVERSE -> lines.reversed()
        LineOp.SHUFFLE -> lines.shuffled(Random(seed))
        LineOp.DEDUP -> {
            val seen = HashSet<String>()
            lines.filter { seen.add(if (ignoreCase) it.lowercase(Lang.locale) else it) }
        }
    }
}

@Composable
internal fun ToolScope.TextLinesScreen() {
    var text by rememberSaveable { mutableStateOf(incoming?.text ?: "") }
    var op by rememberSaveable { mutableStateOf(LineOp.AZ) }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var skipEmpty by rememberSaveable { mutableStateOf(true) }
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong()) }
    val haptics = rememberHaptics()
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val out = remember(text, op, ignoreCase, skipEmpty, seed, Lang.isFr) { processLines(text, op, ignoreCase, skipEmpty, seed) }
    val outText = out.joinToString("\n")
    ToolColumn {
        TextInput(text, { text = it }, tr("Une entrée par ligne", "One item per line"), minHeight = 140)
        SectionTitle(tr("Opération", "Operation"))
        ChipChoice(
            LineOp.entries, op,
            label = {
                when (it) {
                    LineOp.AZ -> "A → Z"
                    LineOp.ZA -> "Z → A"
                    LineOp.NATURAL -> tr("Naturel (1, 2, 10)", "Natural (1, 2, 10)")
                    LineOp.LENGTH -> tr("Longueur", "Length")
                    LineOp.REVERSE -> tr("Inverser l'ordre", "Reverse order")
                    LineOp.SHUFFLE -> tr("Mélanger", "Shuffle")
                    LineOp.DEDUP -> tr("Sans doublons", "Remove duplicates")
                }
            },
            onSelect = { op = it },
        )
        CanifCard {
            OptionSwitch(tr("Ignorer la casse", "Ignore case"), ignoreCase, { ignoreCase = it })
            OptionSwitch(tr("Ignorer les lignes vides", "Skip empty lines"), skipEmpty, { skipEmpty = it })
        }
        AnimatedVisibility(op == LineOp.SHUFFLE, enter = expandVertically(spring(dampingRatio = 0.7f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            FilledTonalButton(
                onClick = {
                    haptics.confirm(); seed = Random.nextLong()
                    scope.launch { spin.animateTo(spin.targetValue + 360f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(ButtonDefaults.IconSize).graphicsLayer { rotationZ = spin.value })
                Text("  " + tr("Mélanger à nouveau", "Shuffle again"))
            }
        }
        val removed = (if (skipEmpty) text.split('\n').count { it.isNotBlank() } else text.split('\n').size) - out.size
        TextResult(
            outText,
            tr("${out.size} lignes", "${out.size} lines") + if (removed > 0) tr(" · $removed doublons retirés", " · $removed duplicates removed") else "",
        )
        ResultActions(outText)
    }
}
