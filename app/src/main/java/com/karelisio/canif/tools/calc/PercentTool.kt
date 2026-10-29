package com.karelisio.canif.tools.calc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.nav.ToolColumn
import kotlin.math.abs

private enum class PercentMode { OF, CHANGE, DISCOUNT, ORIGINAL }

@Composable
internal fun PercentScreen() {
    var mode by rememberSaveable { mutableStateOf(PercentMode.OF) }
    // Two fields per mode, kept separately so switching tabs never mixes meanings.
    var vals by rememberSaveable { mutableStateOf(List(PercentMode.entries.size * 2) { "" }) }
    ToolColumn {
        ChoiceRow(
            PercentMode.entries, mode,
            label = {
                when (it) {
                    PercentMode.OF -> tr("x % de y", "x % of y")
                    PercentMode.CHANGE -> tr("Variation", "Change")
                    PercentMode.DISCOUNT -> tr("Remise", "Discount")
                    PercentMode.ORIGINAL -> tr("Prix barré", "Original")
                }
            },
            onSelect = { mode = it },
        )
        AnimatedContent(
            mode,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 3 * dir } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 3 * dir } + fadeOut())
            },
            label = "percentMode",
        ) { m ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val ia = m.ordinal * 2
                val a = vals[ia]
                val b = vals[ia + 1]
                val setA: (String) -> Unit = { v -> vals = vals.toMutableList().also { it[ia] = v } }
                val setB: (String) -> Unit = { v -> vals = vals.toMutableList().also { it[ia + 1] = v } }
                val x = Fmt.parse(a)
                val y = Fmt.parse(b)
                val cur = Fmt.currencySymbol
                when (m) {
                    PercentMode.OF -> {
                        Explain(tr("Combien font x % d'une valeur ?", "How much is x % of a value?"))
                        NumberField(a, setA, tr("Pourcentage", "Percentage"), suffix = "%")
                        NumberField(b, setB, tr("De la valeur", "Of the value"))
                        val r = if (x != null && y != null) x / 100 * y else null
                        ResultCard(
                            tr("Résultat", "Result"),
                            r?.let { Fmt.sig(it, 10) } ?: "—",
                            supporting = if (x != null && y != null) tr("${Fmt.num(x, 4)} % de ${Fmt.num(y, 4)}", "${Fmt.num(x, 4)} % of ${Fmt.num(y, 4)}") else null,
                        )
                        if (x != null && y != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatBlock(tr("Valeur + x %", "Value + x %"), Fmt.sig(y * (1 + x / 100), 10), Modifier.weight(1f))
                                StatBlock(tr("Valeur − x %", "Value − x %"), Fmt.sig(y * (1 - x / 100), 10), Modifier.weight(1f))
                            }
                        }
                    }
                    PercentMode.CHANGE -> {
                        Explain(tr("Évolution en % entre deux valeurs", "Percentage change between two values"))
                        NumberField(a, setA, tr("Valeur de départ", "Initial value"))
                        NumberField(b, setB, tr("Valeur d'arrivée", "Final value"))
                        val r = if (x != null && y != null && x != 0.0) (y - x) / abs(x) * 100 else null
                        ResultCard(
                            tr("Variation", "Change"),
                            r?.let { Fmt.signed(it, 2) + " %" } ?: "—",
                            supporting = when {
                                x == 0.0 -> tr("La valeur de départ ne peut pas être 0", "The initial value can't be 0")
                                r == null -> null
                                r > 0 -> tr("Hausse", "Increase")
                                r < 0 -> tr("Baisse", "Decrease")
                                else -> tr("Aucun changement", "No change")
                            },
                        )
                        if (x != null && y != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatBlock(tr("Différence", "Difference"), Fmt.signed(y - x, 4), Modifier.weight(1f))
                                StatBlock(tr("Rapport", "Ratio"), if (x != 0.0) "× " + Fmt.num(y / x, 4) else "—", Modifier.weight(1f))
                            }
                        }
                    }
                    PercentMode.DISCOUNT -> {
                        Explain(tr("Prix après une remise", "Price after a discount"))
                        NumberField(a, setA, tr("Prix initial", "Original price"), suffix = cur)
                        NumberField(b, setB, tr("Remise", "Discount"), suffix = "%")
                        val r = if (x != null && y != null) x * (1 - y / 100) else null
                        ResultCard(
                            tr("Prix remisé", "Discounted price"),
                            r?.let { Fmt.money(it) + " " + cur } ?: "—",
                            supporting = if (r != null && x != null) tr("Vous économisez ${Fmt.money(x - r)} $cur", "You save ${Fmt.money(x - r)} $cur") else null,
                        )
                    }
                    PercentMode.ORIGINAL -> {
                        Explain(tr("Retrouver le prix d'origine à partir du prix remisé", "Find the original price from the discounted one"))
                        NumberField(a, setA, tr("Prix remisé", "Discounted price"), suffix = cur)
                        NumberField(b, setB, tr("Remise appliquée", "Discount applied"), suffix = "%")
                        val valid = x != null && y != null && y < 100
                        val r = if (valid) x / (1 - y / 100) else null
                        ResultCard(
                            tr("Prix d'origine", "Original price"),
                            r?.let { Fmt.money(it) + " " + cur } ?: "—",
                            supporting = when {
                                y != null && y >= 100 -> tr("La remise doit être inférieure à 100 %", "The discount must be under 100 %")
                                r != null && x != null -> tr("Remise de ${Fmt.money(r - x)} $cur", "Discount of ${Fmt.money(r - x)} $cur")
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Explain(text: String) {
    CanifCard(color = MaterialTheme.colorScheme.surfaceContainer, padding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

