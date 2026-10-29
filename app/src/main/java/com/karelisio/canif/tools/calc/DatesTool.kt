package com.karelisio.canif.tools.calc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.PolygonShape
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

private enum class DateMode { DIFF, ADD, AGE }
private enum class DateStep { DAYS, WEEKS, MONTHS, YEARS }

internal fun LocalDate.format(style: FormatStyle = FormatStyle.FULL): String =
    format(DateTimeFormatter.ofLocalizedDate(style).withLocale(Lang.locale)).replaceFirstChar { it.titlecase(Lang.locale) }

internal fun plural(n: Long, fr: String, frPl: String, en: String, enPl: String): String =
    "${Fmt.num(n.toDouble(), 0)} " + if (Lang.isFr) (if (kotlin.math.abs(n) >= 2) frPl else fr) else (if (kotlin.math.abs(n) == 1L) en else enPl)

internal fun periodText(p: Period): String {
    val parts = buildList {
        if (p.years != 0) add(plural(p.years.toLong(), "an", "ans", "year", "years"))
        if (p.months != 0) add(plural(p.months.toLong(), "mois", "mois", "month", "months"))
        if (p.days != 0 || isEmpty()) add(plural(p.days.toLong(), "jour", "jours", "day", "days"))
    }
    return parts.joinToString(", ")
}

/** Working days (Mon–Fri) in [a, b). */
private fun workingDays(a: LocalDate, b: LocalDate): Long {
    val (s, e) = if (a <= b) a to b else b to a
    val total = ChronoUnit.DAYS.between(s, e)
    val weeks = total / 7
    var count = weeks * 5
    var d = s.plusDays(weeks * 7)
    while (d < e) {
        if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) count++
        d = d.plusDays(1)
    }
    return count
}

@Composable
internal fun DatesScreen() {
    var mode by rememberSaveable { mutableStateOf(DateMode.DIFF) }
    val today = remember { LocalDate.now() }
    var start by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var end by rememberSaveable { mutableStateOf(today.plusDays(30).toEpochDay()) }
    var base by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var amount by rememberSaveable { mutableStateOf("30") }
    var step by rememberSaveable { mutableStateOf(DateStep.DAYS) }
    var subtract by rememberSaveable { mutableStateOf(false) }
    var birth by rememberSaveable { mutableStateOf(today.minusYears(30).toEpochDay()) }

    ToolColumn {
        ChoiceRow(
            DateMode.entries, mode,
            label = {
                when (it) {
                    DateMode.DIFF -> tr("Écart", "Between")
                    DateMode.ADD -> tr("Ajouter", "Add")
                    DateMode.AGE -> tr("Âge", "Age")
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
            label = "dateMode",
        ) { m ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (m) {
                    DateMode.DIFF -> {
                        val a = LocalDate.ofEpochDay(start)
                        val b = LocalDate.ofEpochDay(end)
                        DateField(tr("Du", "From"), a, Icons.Rounded.Today) { start = it.toEpochDay() }
                        DateField(tr("Au", "To"), b, Icons.Rounded.Event) { end = it.toEpochDay() }
                        val days = ChronoUnit.DAYS.between(a, b)
                        val p = if (a <= b) Period.between(a, b) else Period.between(b, a)
                        ResultCard(
                            tr("Écart", "Difference"),
                            plural(days, "jour", "jours", "day", "days"),
                            supporting = periodText(p) + if (days < 0) tr(" (dans le passé)", " (in the past)") else "",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val w = kotlin.math.abs(days)
                            StatBlock(tr("Semaines", "Weeks"), "${w / 7}" + if (w % 7 != 0L) tr(" s. ${w % 7} j", " w ${w % 7} d") else "", Modifier.weight(1f))
                            StatBlock(tr("Jours ouvrés", "Working days"), Fmt.num(workingDays(a, b).toDouble(), 0), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatBlock(tr("Heures", "Hours"), Fmt.num(kotlin.math.abs(days) * 24.0, 0), Modifier.weight(1f))
                            StatBlock(tr("Mois (approx.)", "Months (approx.)"), Fmt.num(kotlin.math.abs(days) / 30.436875, 1), Modifier.weight(1f))
                        }
                    }
                    DateMode.ADD -> {
                        val d = LocalDate.ofEpochDay(base)
                        DateField(tr("Date de départ", "Start date"), d, Icons.Rounded.Today) { base = it.toEpochDay() }
                        ChoiceRow(listOf(false, true), subtract, label = { if (it) tr("− Retirer", "− Subtract") else tr("+ Ajouter", "+ Add") }, onSelect = { subtract = it })
                        NumberField(amount, { amount = it.filter { c -> c.isDigit() } }, tr("Nombre", "Amount"), decimal = false)
                        ChipChoice(
                            DateStep.entries, step,
                            label = {
                                when (it) {
                                    DateStep.DAYS -> tr("Jours", "Days")
                                    DateStep.WEEKS -> tr("Semaines", "Weeks")
                                    DateStep.MONTHS -> tr("Mois", "Months")
                                    DateStep.YEARS -> tr("Années", "Years")
                                }
                            },
                            onSelect = { step = it },
                        )
                        val n = (amount.toLongOrNull() ?: 0L).coerceAtMost(100_000L).let { if (subtract) -it else it }
                        val r = runCatching {
                            when (step) {
                                DateStep.DAYS -> d.plusDays(n)
                                DateStep.WEEKS -> d.plusWeeks(n)
                                DateStep.MONTHS -> d.plusMonths(n)
                                DateStep.YEARS -> d.plusYears(n)
                            }
                        }.getOrNull()
                        ResultCard(
                            tr("Résultat", "Result"),
                            r?.format(FormatStyle.LONG) ?: "—",
                            supporting = r?.let {
                                val wd = it.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Lang.locale).replaceFirstChar { c -> c.titlecase(Lang.locale) }
                                val diff = ChronoUnit.DAYS.between(today, it)
                                "$wd · " + when {
                                    diff == 0L -> tr("aujourd'hui", "today")
                                    diff > 0 -> tr("dans ", "in ") + plural(diff, "jour", "jours", "day", "days")
                                    else -> tr("il y a ", "") + plural(-diff, "jour", "jours", "day", "days") + tr("", " ago")
                                }
                            },
                        )
                    }
                    DateMode.AGE -> {
                        val b = LocalDate.ofEpochDay(birth)
                        DateField(tr("Date de naissance", "Date of birth"), b, Icons.Rounded.Cake) { birth = it.toEpochDay() }
                        if (b > today) {
                            ResultCard(tr("Âge", "Age"), "—", supporting = tr("La date est dans le futur", "The date is in the future"))
                        } else {
                            val p = Period.between(b, today)
                            ResultCard(
                                tr("Âge exact", "Exact age"),
                                plural(p.years.toLong(), "an", "ans", "year", "years"),
                                supporting = periodText(p),
                            )
                            var next = b.withYear(today.year)
                            if (next < today) next = b.withYear(today.year + 1)
                            val inDays = ChronoUnit.DAYS.between(today, next)
                            val turning = next.year - b.year
                            BirthdayCard(next, inDays, turning)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatBlock(tr("Jours vécus", "Days lived"), Fmt.num(ChronoUnit.DAYS.between(b, today).toDouble(), 0), Modifier.weight(1f))
                                StatBlock(tr("Mois vécus", "Months lived"), Fmt.num(ChronoUnit.MONTHS.between(b, today).toDouble(), 0), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatBlock(tr("Semaines", "Weeks"), Fmt.num(ChronoUnit.WEEKS.between(b, today).toDouble(), 0), Modifier.weight(1f))
                                StatBlock(tr("Né(e) un", "Born on a"), b.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Lang.locale), Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BirthdayCard(next: LocalDate, inDays: Long, turning: Int) {
    val c = MaterialTheme.colorScheme
    CanifCard(color = c.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(PolygonShape(MaterialShapes.Sunny)).background(c.tertiary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Cake, null, tint = c.onTertiary) }
            Column(Modifier.padding(start = 16.dp)) {
                Text(tr("Prochain anniversaire", "Next birthday"), style = MaterialTheme.typography.labelLarge, color = c.onTertiaryContainer)
                Text(
                    if (inDays == 0L) tr("C'est aujourd'hui ! 🎉", "It's today! 🎉") else tr("Dans ", "In ") + plural(inDays, "jour", "jours", "day", "days"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onTertiaryContainer,
                )
                Text(
                    next.format(FormatStyle.FULL) + " · " + tr("$turning ans", "turning $turning"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** A tappable date that opens the Material 3 date picker. */
@Composable
internal fun DateField(label: String, date: LocalDate, icon: androidx.compose.ui.graphics.vector.ImageVector, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainerHigh)
            .clickable { haptics.click(); open = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(PolygonShape(MaterialShapes.Cookie4Sided)).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.onSecondaryContainer)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            AnimatedContent(date, label = "date") { d -> Text(d.format(FormatStyle.FULL), style = MaterialTheme.typography.titleMedium) }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1800..2200,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    haptics.confirm()
                    open = false
                }) { Text(tr("OK", "OK")) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Annuler", "Cancel")) } },
        ) { DatePicker(state) }
    }
}
