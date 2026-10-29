package com.karelisio.canif.tools.random

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialShapes
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.tools.calc.Stepper
import com.karelisio.canif.tools.text.OptionSwitch
import com.karelisio.canif.tools.text.ResultActions
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.MorphShape
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import androidx.graphics.shapes.Morph
import java.security.SecureRandom

private val rng = SecureRandom()

private fun randomIn(min: Long, max: Long): Long {
    val span = max - min + 1
    if (span <= 0) { // range wider than Long: plain rejection
        while (true) { val r = rng.nextLong(); if (r in min..max) return r }
    }
    // Rejection sampling for an unbiased result.
    val limit = Long.MAX_VALUE - Long.MAX_VALUE % span
    while (true) {
        val r = rng.nextLong() and Long.MAX_VALUE
        if (r < limit) return min + r % span
    }
}

/** Draws [count] numbers in [min, max], optionally all different. */
internal fun drawNumbers(min: Long, max: Long, count: Int, unique: Boolean): List<Long> {
    if (!unique) return List(count) { randomIn(min, max) }
    val span = max - min + 1
    return if (span <= 20_000) {
        (min..max).shuffled(rng).take(count)
    } else {
        val set = LinkedHashSet<Long>()
        while (set.size < count) set += randomIn(min, max)
        set.toList()
    }
}

@Composable
internal fun RandomNumberScreen() {
    var minS by rememberSaveable { mutableStateOf("1") }
    var maxS by rememberSaveable { mutableStateOf("100") }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var unique by rememberSaveable { mutableStateOf(true) }
    var sorted by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var drawId by rememberSaveable { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme

    val min = minS.toLongOrNull()
    val max = maxS.toLongOrNull()
    val span = if (min != null && max != null && max >= min) max - min + 1 else 0
    val error = when {
        min == null || max == null -> tr("Saisissez deux nombres entiers", "Enter two whole numbers")
        max < min -> tr("Le maximum doit être supérieur au minimum", "The maximum must be greater than the minimum")
        unique && span in 1 until count -> tr("Pas assez de valeurs pour $count tirages sans doublon", "Not enough values for $count unique draws")
        else -> null
    }

    ToolColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(minS, { minS = it.filter { ch -> ch.isDigit() || ch == '-' } }, tr("Minimum", "Minimum"), Modifier.weight(1f), decimal = false)
            NumberField(maxS, { maxS = it.filter { ch -> ch.isDigit() || ch == '-' } }, tr("Maximum", "Maximum"), Modifier.weight(1f), decimal = false)
        }
        Stepper(count, { count = it }, 1..100, tr("Quantité", "Quantity"))
        CanifCard {
            OptionSwitch(tr("Sans doublons", "No duplicates"), unique, { unique = it })
            OptionSwitch(tr("Trier les résultats", "Sort results"), sorted, { sorted = it })
        }

        val shown = if (sorted) result.sorted() else result
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                shown.size <= 1,
                transitionSpec = { (scaleIn(spring(dampingRatio = 0.6f)) + fadeIn()) togetherWith fadeOut() },
                label = "rnLayout",
            ) { single ->
                if (single) {
                    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided) }
                    val progress by animateFloatAsState((drawId % 2).toFloat(), spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "morph")
                    val turn by animateFloatAsState(drawId * 40f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "turn")
                    Box(
                        Modifier.size(180.dp).clip(MorphShape(morph, progress, turn)).background(c.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        RollingText(
                            shown.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.onPrimaryContainer,
                        )
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEachIndexed { i, n ->
                            Box(
                                Modifier
                                    .cascade("$drawId-$i", i)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (i % 2 == 0) c.secondaryContainer else c.tertiaryContainer)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                RollingText(n.toString(), style = MaterialTheme.typography.titleLarge, color = if (i % 2 == 0) c.onSecondaryContainer else c.onTertiaryContainer)
                            }
                        }
                    }
                }
            }
        }
        if (error != null) {
            Text(error, color = c.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Button(
            onClick = {
                if (min != null && max != null) {
                    haptics.confirm()
                    result = drawNumbers(min, max, count, unique)
                    drawId++
                }
            },
            enabled = error == null,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.Pin, null)
            Text("  " + tr("Tirer", "Draw"), style = MaterialTheme.typography.titleMedium)
        }
        if (shown.isNotEmpty()) ResultActions(shown.joinToString(", "))
    }
}
