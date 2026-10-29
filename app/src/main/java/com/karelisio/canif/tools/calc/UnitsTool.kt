package com.karelisio.canif.tools.calc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalAppSettings

@Composable
internal fun UnitsScreen() {
    val system = LocalAppSettings.current.units
    var qId by rememberSaveable { mutableStateOf(Quantities.first().id) }
    val q = Quantities.first { it.id == qId }
    var fromId by rememberSaveable(qId) { mutableStateOf(q.defaults(system).first) }
    var toId by rememberSaveable(qId) { mutableStateOf(q.defaults(system).second) }
    var input by rememberSaveable { mutableStateOf("1") }
    val haptics = rememberHaptics()

    val from = q.unit(fromId)
    val to = q.unit(toId)
    val value = Fmt.parse(input)
    val base = value?.let { from.toBase(it) }

    ToolColumn {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Quantities.forEach { item ->
                FilterChip(
                    selected = item.id == qId,
                    onClick = { haptics.tick(); qId = item.id },
                    label = { Text(item.name.text) },
                    leadingIcon = { Icon(item.icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }

        NumberField(input, { input = it }, tr("Valeur", "Value"), suffix = from.symbol.text)

        Box(contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitSelector(tr("De", "From"), q, from) { fromId = it }
                UnitSelector(tr("Vers", "To"), q, to) { toId = it }
            }
            SwapButton(onClick = { val f = fromId; fromId = toId; toId = f }, Modifier.align(Alignment.CenterEnd).padding(end = 52.dp))
        }

        val result = base?.let { to.fromBase(it) }
        AnimatedContent(
            qId,
            transitionSpec = { (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.9f) + fadeIn()) togetherWith fadeOut() },
            label = "unitResult",
        ) { _ ->
            ResultCard(
                label = "${from.name.text} → ${to.name.text}",
                value = result?.let { Fmt.sig(it) + " " + to.symbol.text } ?: "—",
                supporting = when {
                    result == null -> null
                    from.approx || to.approx -> tr("Valeur approximative (densité moyenne)", "Approximate value (average density)")
                    else -> "1 ${from.symbol.text} = ${Fmt.sig(to.fromBase(from.toBase(1.0)))} ${to.symbol.text}"
                },
            )
        }

        if (base != null) {
            SectionTitle(tr("Toutes les conversions", "All conversions"))
            CanifCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                q.units.forEachIndexed { i, u ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val selected = u.id == toId
                    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "row")
                    Row(
                        Modifier
                            .cascade("$qId-${u.id}", i)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .clickable { haptics.tick(); toId = u.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (u.approx) Text(tr("approx.", "approx."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            Fmt.sig(u.fromBase(base)) + " " + u.symbol.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitSelector(label: String, q: Quantity, unit: MeasureUnit, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surfaceContainerHigh)
                .clickable { open = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 64.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                AnimatedContent(unit, label = "unitName") { u ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(u.symbol.text, style = MaterialTheme.typography.titleLarge, color = c.primary, fontWeight = FontWeight.SemiBold)
                        Text("  " + u.name.text, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = c.onSurfaceVariant)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            q.units.forEach { u ->
                DropdownMenuItem(
                    text = { Text("${u.name.text}  ·  ${u.symbol.text}") },
                    onClick = { onPick(u.id); open = false },
                )
            }
        }
    }
}
