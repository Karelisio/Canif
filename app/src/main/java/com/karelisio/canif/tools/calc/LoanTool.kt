package com.karelisio.canif.tools.calc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlin.math.pow

internal class LoanMonth(val index: Int, val payment: Double, val interest: Double, val principal: Double, val balance: Double)
internal class LoanYear(val year: Int, val months: List<LoanMonth>) {
    val interest = months.sumOf { it.interest }
    val principal = months.sumOf { it.principal }
    val balance = months.last().balance
}

internal class LoanResult(val monthly: Double, val total: Double, val interest: Double, val years: List<LoanYear>)

internal fun computeLoan(capital: Double, annualRatePct: Double, months: Int): LoanResult? {
    if (capital <= 0 || months <= 0 || months > 1200 || annualRatePct < 0) return null
    val r = annualRatePct / 100 / 12
    val m = if (r == 0.0) capital / months else capital * r / (1 - (1 + r).pow(-months))
    var bal = capital
    val list = ArrayList<LoanMonth>(months)
    for (i in 1..months) {
        val interest = bal * r
        var principal = m - interest
        if (i == months) principal = bal // absorb rounding drift
        bal -= principal
        list += LoanMonth(i, interest + principal, interest, principal, maxOf(0.0, bal))
    }
    val years = list.chunked(12).mapIndexed { i, ms -> LoanYear(i + 1, ms) }
    val total = list.sumOf { it.payment }
    return LoanResult(m, total, total - capital, years)
}

/** Machine-friendly number for an input field (dot decimal, no grouping). */
internal fun plainNumber(x: Double, decimals: Int = 2): String =
    java.math.BigDecimal(x).setScale(decimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

@Composable
internal fun LoanScreen() {
    var capital by rememberSaveable { mutableStateOf("200000") }
    var rate by rememberSaveable { mutableStateOf("3.5") }
    var duration by rememberSaveable { mutableStateOf("20") }
    var inYears by rememberSaveable { mutableStateOf(true) }
    val cur = Fmt.currencySymbol

    val months = Fmt.parse(duration)?.let { if (inYears) (it * 12).toInt() else it.toInt() } ?: 0
    val p = Fmt.parse(capital)
    val r = Fmt.parse(rate)
    val res = remember(p, r, months) { if (p != null && r != null) computeLoan(p, r, months) else null }

    ToolColumn {
        NumberField(capital, { capital = it }, tr("Capital emprunté", "Loan amount"), suffix = cur)
        NumberField(rate, { rate = it }, tr("Taux annuel (hors assurance)", "Annual rate"), suffix = "%")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(duration, { duration = it }, tr("Durée", "Term"), Modifier.weight(1f))
            ChoiceRow(listOf(true, false), inYears, label = { if (it) tr("Ans", "Years") else tr("Mois", "Months") }, onSelect = { v ->
                // Keep the same term when switching unit.
                Fmt.parse(duration)?.let { d ->
                    if (v && !inYears) duration = plainNumber(d / 12)
                    if (!v && inYears) duration = (d * 12).toInt().toString()
                }
                inYears = v
            }, modifier = Modifier.weight(1f))
        }

        ResultCard(
            tr("Mensualité", "Monthly payment"),
            res?.let { Fmt.money(it.monthly) + " " + cur } ?: "—",
            supporting = res?.let { tr("sur $months mois", "over $months months") },
        )
        if (res == null) return@ToolColumn

        val c = MaterialTheme.colorScheme
        CanifCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(capitalShare = (p!! / res.total).toFloat(), Modifier.size(120.dp))
                Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendStat(tr("Capital", "Principal"), Fmt.money(p) + " " + cur, c.primary, Modifier.fillMaxWidth())
                    LegendStat(tr("Intérêts", "Interest"), Fmt.money(res.interest) + " " + cur, c.tertiary, Modifier.fillMaxWidth())
                }
            }
            Box(Modifier.padding(top = 12.dp)) {
                DetailRow(tr("Coût total", "Total cost"), Fmt.money(res.total) + " " + cur, strong = true)
            }
        }

        SectionTitle(tr("Remboursement par année", "Repayment per year"))
        CanifCard { YearBars(res.years) }

        SectionTitle(tr("Tableau d'amortissement", "Amortization schedule"))
        var open by rememberSaveable(res.years.size) { mutableIntStateOf(-1) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            res.years.forEach { y -> YearRow(y, open == y.year, cur) { open = if (open == y.year) -1 else y.year } }
        }
        Hint(tr("Calcul à taux fixe, mensualités constantes, hors assurance et frais.", "Fixed rate, constant monthly payments, excluding insurance and fees."))
    }
}

@Composable
private fun Donut(capitalShare: Float, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val sweep = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow)) }
    val share by animateFloatAsState(capitalShare.coerceIn(0f, 1f), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow), label = "share")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension * 0.16f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val total = 360f * sweep.value
            val gap = 6f
            val a1 = (total * share - gap).coerceAtLeast(0.1f)
            val a2 = (total * (1 - share) - gap).coerceAtLeast(0.1f)
            drawArc(c.primary, -90f, a1, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(c.tertiary, -90f + total * share + gap / 2, a2, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(Fmt.num(share * 100.0, 0) + " %", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun YearBars(years: List<LoanYear>) {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val grow = remember(years.size) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(years.size) { grow.animateTo(1f, tween(900)) }
    val max = years.maxOf { it.interest + it.principal }.takeIf { it > 0 } ?: 1.0
    // Pre-computed fractions so the draw phase only reads the animation value.
    val bars = remember(years) { years.map { (it.principal / max).toFloat() to (it.interest / max).toFloat() } }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val n = bars.size
        val slot = size.width / n
        val bw = (slot * 0.7f).coerceAtMost(28.dp.toPx())
        val rad = CornerRadius(minOf(bw / 2, 6.dp.toPx()))
        bars.forEachIndexed { i, (pr, ir) ->
            // Bars rise one after the other.
            val local = ((grow.value * (n + 4) - i) / 4f).coerceIn(0f, 1f)
            val x = i * slot + (slot - bw) / 2
            val hp = size.height * pr * local
            val hi = size.height * ir * local
            if (hi > 0.5f) drawRoundRect(c.tertiary, Offset(x, size.height - hp - hi), Size(bw, hi + rad.x), rad)
            drawRoundRect(c.primary, Offset(x, size.height - hp), Size(bw, hp), rad)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(tr("Année 1", "Year 1"), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(tr("Année ${years.size}", "Year ${years.size}"), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(tr("Capital remboursé", "Principal repaid"), c.primary)
        LegendDot(tr("Intérêts", "Interest"), c.tertiary)
    }
}

@Composable
internal fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun YearRow(y: LoanYear, expanded: Boolean, cur: String, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "arrow")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (expanded) 28.dp else 20.dp))
            .background(if (expanded) c.secondaryContainer else c.surfaceContainerHigh)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { haptics.tick(); onToggle() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Année ${y.year}", "Year ${y.year}"), style = MaterialTheme.typography.titleMedium)
                Text(
                    tr("Capital ${Fmt.money(y.principal)} · Intérêts ${Fmt.money(y.interest)}", "Principal ${Fmt.money(y.principal)} · Interest ${Fmt.money(y.interest)}"),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(tr("Reste dû", "Balance"), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                Text(Fmt.money(y.balance) + " " + cur, style = MaterialTheme.typography.titleSmall)
            }
            Icon(Icons.Rounded.ExpandMore, null, Modifier.padding(start = 8.dp).graphicsLayer { rotationZ = arrow })
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp))) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    listOf(tr("Mois", "Month"), tr("Capital", "Principal"), tr("Intérêts", "Interest"), tr("Reste", "Balance")).forEachIndexed { i, h ->
                        Text(h, style = MaterialTheme.typography.labelMedium, color = c.onSecondaryContainer.copy(alpha = 0.7f), modifier = Modifier.weight(if (i == 0) 0.6f else 1f))
                    }
                }
                y.months.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${m.index}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.6f))
                        Text(Fmt.money(m.principal), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(Fmt.money(m.interest), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(Fmt.money(m.balance), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
