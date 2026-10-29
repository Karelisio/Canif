package com.karelisio.canif.tools.calc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlin.math.pow

/** Compounding periods per year. */
private enum class Compounding(val perYear: Int) { YEARLY(1), HALF(2), QUARTERLY(4), MONTHLY(12), DAILY(365) }

internal class GrowthPoint(val year: Int, val balance: Double, val invested: Double)

/**
 * Month-by-month simulation. The nominal annual rate compounded [perYear] times is turned into the
 * equivalent monthly rate, contributions are added at the end of each period.
 */
internal fun simulateGrowth(initial: Double, contribution: Double, contributionsPerYear: Int, ratePct: Double, perYear: Int, years: Int): List<GrowthPoint> {
    val monthly = (1 + ratePct / 100 / perYear).pow(perYear / 12.0) - 1
    var bal = initial
    var invested = initial
    val out = ArrayList<GrowthPoint>(years + 1)
    out += GrowthPoint(0, bal, invested)
    val every = 12 / contributionsPerYear
    for (m in 1..years * 12) {
        bal *= 1 + monthly
        if (m % every == 0) { bal += contribution; invested += contribution }
        if (m % 12 == 0) out += GrowthPoint(m / 12, bal, invested)
    }
    return out
}

@Composable
internal fun CompoundScreen() {
    var initial by rememberSaveable { mutableStateOf("10000") }
    var contrib by rememberSaveable { mutableStateOf("100") }
    var monthlyContrib by rememberSaveable { mutableStateOf(true) }
    var rate by rememberSaveable { mutableStateOf("4") }
    var years by rememberSaveable { mutableStateOf("15") }
    var comp by rememberSaveable { mutableStateOf(Compounding.YEARLY) }
    val cur = Fmt.currencySymbol

    val p0 = Fmt.parse(initial) ?: 0.0
    val c = Fmt.parse(contrib) ?: 0.0
    val r = Fmt.parse(rate)
    val y = Fmt.parse(years)?.toInt()?.coerceIn(0, 100)
    val points = remember(p0, c, monthlyContrib, r, y, comp) {
        if (r != null && y != null && y > 0 && r > -100) simulateGrowth(p0, c, if (monthlyContrib) 12 else 1, r, comp.perYear, y) else null
    }

    ToolColumn {
        NumberField(initial, { initial = it }, tr("Capital initial", "Initial capital"), suffix = cur)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(contrib, { contrib = it }, tr("Versement", "Contribution"), Modifier.weight(1f), suffix = cur)
            ChoiceRow(listOf(true, false), monthlyContrib, label = { if (it) tr("/ mois", "/ month") else tr("/ an", "/ year") }, onSelect = { monthlyContrib = it }, modifier = Modifier.weight(1f).padding(top = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(rate, { rate = it }, tr("Taux annuel", "Annual rate"), Modifier.weight(1f), suffix = "%")
            NumberField(years, { years = it.filter { ch -> ch.isDigit() } }, tr("Durée", "Duration"), Modifier.weight(1f), suffix = tr("ans", "years"), decimal = false)
        }
        SectionTitle(tr("Capitalisation", "Compounding"))
        ChipChoice(
            Compounding.entries, comp,
            label = {
                when (it) {
                    Compounding.YEARLY -> tr("Annuelle", "Yearly")
                    Compounding.HALF -> tr("Semestrielle", "Half-yearly")
                    Compounding.QUARTERLY -> tr("Trimestrielle", "Quarterly")
                    Compounding.MONTHLY -> tr("Mensuelle", "Monthly")
                    Compounding.DAILY -> tr("Quotidienne", "Daily")
                }
            },
            onSelect = { comp = it },
        )

        val last = points?.lastOrNull()
        ResultCard(
            tr("Valeur finale", "Final value"),
            last?.let { Fmt.money(it.balance) + " " + cur } ?: "—",
            supporting = last?.let { tr("après ${it.year} ans", "after ${it.year} years") },
        )
        if (points == null || last == null) return@ToolColumn
        val cs = MaterialTheme.colorScheme
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendStat(tr("Total versé", "Total invested"), Fmt.money(last.invested), cs.secondary, Modifier.weight(1f))
            LegendStat(tr("Intérêts gagnés", "Interest earned"), Fmt.money(last.balance - last.invested), cs.primary, Modifier.weight(1f))
        }
        SectionTitle(tr("Croissance", "Growth"))
        CanifCard { GrowthChart(points) }
        Hint(tr("Simulation à taux constant, avant impôts et frais. Versements en fin de période.", "Constant rate, before taxes and fees. Contributions at the end of each period."))
    }
}

@Composable
private fun GrowthChart(points: List<GrowthPoint>) {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(points.size) {
        if (!reduce) { reveal.snapTo(0f); reveal.animateTo(1f, tween(1100)) }
    }
    val max = points.maxOf { maxOf(it.balance, it.invested) }.takeIf { it > 0 } ?: 1.0
    val min = minOf(0.0, points.minOf { minOf(it.balance, it.invested) })
    // Paths are built once per size/data (drawWithCache); only the reveal is read per frame.
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .drawWithCache {
                val n = points.size - 1
                fun x(i: Int) = if (n == 0) 0f else size.width * i / n
                fun yv(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
                val total = Path()
                val invested = Path()
                val line = Path()
                points.forEachIndexed { i, p ->
                    if (i == 0) {
                        total.moveTo(x(0), size.height); total.lineTo(x(0), yv(p.balance))
                        invested.moveTo(x(0), size.height); invested.lineTo(x(0), yv(p.invested))
                        line.moveTo(x(0), yv(p.balance))
                    } else {
                        total.lineTo(x(i), yv(p.balance)); invested.lineTo(x(i), yv(p.invested)); line.lineTo(x(i), yv(p.balance))
                    }
                }
                total.lineTo(x(n), size.height); total.close()
                invested.lineTo(x(n), size.height); invested.close()
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val investedColor = c.secondary.copy(alpha = 0.35f)
                onDrawBehind {
                    val rv = reveal.value
                    clipRect(right = size.width * rv) {
                        drawPath(total, c.primaryContainer)
                        drawPath(invested, investedColor)
                        drawPath(line, c.primary, style = stroke)
                    }
                    val idx = (rv * n).toInt().coerceIn(0, n)
                    val o = Offset(x(idx), yv(points[idx].balance))
                    drawCircle(c.primary, 6.dp.toPx(), o)
                    drawCircle(c.surface, 3.dp.toPx(), o)
                }
            },
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(tr("Aujourd'hui", "Today"), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(tr("${points.last().year} ans", "${points.last().year} years"), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(tr("Valeur totale", "Total value"), c.primary)
        LegendDot(tr("Versements", "Contributions"), c.secondary)
    }
}
