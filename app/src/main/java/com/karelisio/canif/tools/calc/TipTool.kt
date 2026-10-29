package com.karelisio.canif.tools.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlin.math.ceil
import kotlin.math.roundToInt

private val TipPresets = listOf(0, 5, 10, 15, 20)

@Composable
internal fun TipScreen() {
    var bill by rememberSaveable { mutableStateOf("") }
    var tip by rememberSaveable { mutableFloatStateOf(10f) }
    var people by rememberSaveable { mutableIntStateOf(2) }
    var round by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val cur = Fmt.currencySymbol

    val amount = Fmt.parse(bill)
    val tipPct = tip.roundToInt()

    ToolColumn {
        NumberField(bill, { bill = it }, tr("Montant de l'addition", "Bill amount"), suffix = cur)

        CanifCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Pourboire", "Tip"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                RollingText("$tipPct %", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            ChipChoice(TipPresets, TipPresets.firstOrNull { it == tipPct } ?: -1, label = { "$it %" }, onSelect = { tip = it.toFloat() }, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = tip,
                onValueChange = { v -> if (v.roundToInt() != tip.roundToInt()) haptics.tick(); tip = v.roundToInt().toFloat() },
                valueRange = 0f..30f,
                steps = 29,
            )
        }

        Stepper(people, { people = it }, 1..50, tr("Personnes", "People"))

        CanifCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Arrondir au supérieur", "Round up"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Chaque part est arrondie à l'unité", "Each share is rounded to a whole unit"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(round, { haptics.click(); round = it })
            }
        }

        if (amount != null && amount >= 0) {
            var tipAmount = amount * tipPct / 100
            var total = amount + tipAmount
            var each = total / people
            if (round) {
                // Round each share up to the cent first to avoid 12.000000001 → 13.
                each = ceil((each * 100).roundToInt() / 100.0)
                total = each * people
                tipAmount = total - amount
            }
            ResultCard(
                tr("Par personne", "Per person"),
                Fmt.money(each) + " " + cur,
                supporting = if (people > 1) tr("$people parts égales", "$people equal shares") else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LegendStat(tr("Pourboire", "Tip"), Fmt.money(tipAmount) + " " + cur, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                LegendStat(tr("Total", "Total"), Fmt.money(total) + " " + cur, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            if (round && amount > 0) {
                Hint(tr("Pourboire effectif : ${Fmt.num(tipAmount / amount * 100, 1)} %", "Effective tip: ${Fmt.num(tipAmount / amount * 100, 1)} %"))
            }
        } else {
            SectionTitle(tr("Saisissez le montant pour voir le partage", "Enter the amount to see the split"))
        }
    }
}
