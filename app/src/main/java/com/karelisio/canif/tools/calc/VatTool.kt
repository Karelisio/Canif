package com.karelisio.canif.tools.calc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.nav.ToolColumn

private val FrenchRates = listOf("20", "10", "5.5", "2.1", "custom")

@Composable
internal fun VatScreen() {
    var fromNet by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableStateOf("") }
    var rateChoice by rememberSaveable { mutableStateOf("20") }
    var custom by rememberSaveable { mutableStateOf("") }
    val cur = Fmt.currencySymbol

    ToolColumn {
        ChoiceRow(
            listOf(true, false), fromNet,
            label = { if (it) tr("HT → TTC", "Net → Gross") else tr("TTC → HT", "Gross → Net") },
            onSelect = { fromNet = it },
        )
        NumberField(
            amount, { amount = it },
            if (fromNet) tr("Montant hors taxe (HT)", "Net amount (excl. VAT)") else tr("Montant toutes taxes (TTC)", "Gross amount (incl. VAT)"),
            suffix = cur,
        )
        SectionTitle(tr("Taux de TVA", "VAT rate"))
        ChipChoice(
            FrenchRates, rateChoice,
            label = { if (it == "custom") tr("Autre", "Other") else Fmt.num(it.toDouble(), 1) + " %" },
            onSelect = { rateChoice = it },
        )
        AnimatedVisibility(rateChoice == "custom", enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            NumberField(custom, { custom = it }, tr("Taux personnalisé", "Custom rate"), suffix = "%")
        }

        val rate = if (rateChoice == "custom") Fmt.parse(custom) else rateChoice.toDouble()
        val v = Fmt.parse(amount)
        val net: Double?
        val gross: Double?
        if (v != null && rate != null && rate > -100) {
            if (fromNet) { net = v; gross = v * (1 + rate / 100) } else { gross = v; net = v / (1 + rate / 100) }
        } else { net = null; gross = null }
        val vat = if (net != null && gross != null) gross - net else null

        ResultCard(
            if (fromNet) tr("Montant TTC", "Gross amount") else tr("Montant HT", "Net amount"),
            (if (fromNet) gross else net)?.let { Fmt.money(it) + " " + cur } ?: "—",
            supporting = vat?.let { tr("dont TVA : ", "VAT: ") + Fmt.money(it) + " " + cur },
        )
        if (net != null && gross != null && vat != null) {
            val c = MaterialTheme.colorScheme
            val share by animateFloatAsState(
                if (gross != 0.0) (net / gross).toFloat().coerceIn(0f, 1f) else 1f,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "vatShare",
            )
            CanifCard {
                Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                    val r = CornerRadius(size.height / 2)
                    val gap = 4.dp.toPx()
                    val w1 = (size.width - gap) * share
                    drawRoundRect(c.primary, Offset.Zero, Size(w1, size.height), r)
                    drawRoundRect(c.tertiary, Offset(w1 + gap, 0f), Size(size.width - w1 - gap, size.height), r)
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendStat(tr("HT", "Net"), Fmt.money(net), c.primary, Modifier.weight(1f))
                    LegendStat(tr("TVA", "VAT"), Fmt.money(vat), c.tertiary, Modifier.weight(1f))
                    LegendStat(tr("TTC", "Gross"), Fmt.money(gross), c.secondary, Modifier.weight(1f))
                }
            }
        }
        Hint(tr("Taux français : 20 % normal, 10 % intermédiaire, 5,5 % réduit, 2,1 % particulier.", "French rates: 20% standard, 10% intermediate, 5.5% reduced, 2.1% special."))
    }
}

