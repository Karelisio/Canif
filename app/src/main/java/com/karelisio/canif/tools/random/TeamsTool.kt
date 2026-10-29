package com.karelisio.canif.tools.random

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.tools.calc.Stepper
import com.karelisio.canif.tools.text.ResultActions
import com.karelisio.canif.tools.text.TextInput
import com.karelisio.canif.ui.components.PolygonShape
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom

private const val NAMES_KEY = "teams_names"

/** Shuffles the names and deals them round-robin, so team sizes differ by at most one. */
internal fun makeTeams(names: List<String>, teams: Int): List<List<String>> {
    val shuffled = names.shuffled(SecureRandom())
    return List(teams) { t -> shuffled.filterIndexed { i, _ -> i % teams == t } }
}

@Composable
internal fun TeamsScreen() {
    val settings = CanifApp.instance.settings
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    var teamCount by rememberSaveable { mutableIntStateOf(2) }
    var teams by rememberSaveable { mutableStateOf(listOf<List<String>>()) }
    var drawId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (text == null) text = settings.string(NAMES_KEY, "").first() }
    val names = (text ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() }
    val maxTeams = names.size.coerceIn(2, 20)

    ToolColumn {
        TextInput(
            text ?: "",
            { v -> text = v; scope.launch { settings.setString(NAMES_KEY, v) } },
            tr("Participants (un par ligne)", "Participants (one per line)"),
            minHeight = 140,
        )
        Text(
            tr("${names.size} participants", "${names.size} participants"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper(teamCount.coerceAtMost(maxTeams), { teamCount = it }, 2..maxTeams, tr("Équipes", "Teams"))
        Button(
            onClick = {
                haptics.confirm()
                teams = makeTeams(names, teamCount.coerceAtMost(maxTeams))
                drawId++
            },
            enabled = names.size >= 2,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.Groups, null)
            Text("  " + tr("Former les équipes", "Make teams"), style = MaterialTheme.typography.titleMedium)
        }

        if (teams.isNotEmpty()) {
            SectionTitle(tr("Équipes", "Teams"))
            val c = MaterialTheme.colorScheme
            val colors = listOf(
                c.primaryContainer to c.onPrimaryContainer,
                c.secondaryContainer to c.onSecondaryContainer,
                c.tertiaryContainer to c.onTertiaryContainer,
            )
            val shapes = listOf(MaterialShapes.Cookie4Sided, MaterialShapes.Pentagon, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Gem, MaterialShapes.Flower)
            var order = 0
            teams.forEachIndexed { t, members ->
                val (bg, fg) = colors[t % colors.size]
                Column(
                    Modifier
                        .cascade("team-$drawId-$t", order++)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(bg)
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(PolygonShape(shapes[t % shapes.size])).background(fg),
                            contentAlignment = Alignment.Center,
                        ) { Text("${t + 1}", color = bg, fontWeight = FontWeight.Bold) }
                        Text(
                            tr("Équipe ${t + 1}", "Team ${t + 1}"),
                            style = MaterialTheme.typography.titleLarge, color = fg,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        Text("${members.size}", style = MaterialTheme.typography.titleMedium, color = fg.copy(alpha = 0.7f))
                    }
                    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEachIndexed { i, m ->
                            Text(
                                m,
                                Modifier
                                    .cascade("m-$drawId-$t-$i", order++)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            ResultActions(
                teams.mapIndexed { t, m -> tr("Équipe ${t + 1} : ", "Team ${t + 1}: ") + m.joinToString(", ") }.joinToString("\n"),
            )
        }
    }
}
