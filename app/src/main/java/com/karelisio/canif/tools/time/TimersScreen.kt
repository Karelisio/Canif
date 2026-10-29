package com.karelisio.canif.tools.time

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.data.TimerEntity
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.MorphingBlob
import com.karelisio.canif.ui.components.NumberField
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.theme.LocalReduceMotion

private val presetMinutes = listOf(1, 3, 5, 10, 15, 30)

private fun remainingOf(t: TimerEntity, now: Long): Long = when {
    t.running -> (t.endAt - now).coerceAtLeast(0)
    else -> t.remainingMs.coerceAtLeast(0)
}

@Composable
fun TimersScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val timers by remember { CanifApp.instance.db.timers().observe() }.collectAsStateWithLifecycle(emptyList())
    val ringing by RingService.ringing.collectAsStateWithLifecycle()
    val ringingIds = remember(ringing) { ringing.map { it.id }.toSet() }
    val haptics = rememberHaptics()

    // Make sure every running timer has its alarm (app update, permission just granted…).
    LaunchedEffect(Unit) {
        TimeNotifications.ensureChannels(context)
        TimerManager.rescheduleAll()
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anyRunning = timers.any { it.running }
    LaunchedEffect(anyRunning) {
        now = System.currentTimeMillis()
        while (anyRunning) withFrameMillis { now = System.currentTimeMillis() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "warnings") {
                ReliabilityCards(exactAlarms = true, fullScreen = true, onChanged = { TimerManager.launch { rescheduleAll() } })
            }
            item(key = "create") {
                CreateTimerCard { name, ms, start ->
                    haptics.confirm()
                    TimerManager.launch { create(name, ms, start) }
                }
            }
            if (timers.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .animateItem(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MorphingBlob(size = 120.dp, from = MaterialShapes.Cookie9Sided, to = MaterialShapes.Cookie12Sided) {
                            Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr("Aucun minuteur. Touchez une durée pour en lancer un.", "No timer yet. Tap a duration to start one."),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item(key = "title") { SectionTitle(tr("Mes minuteurs", "My timers"), Modifier.animateItem()) }
            }
            items(timers.asReversed(), key = { it.id }) { t ->
                TimerCard(
                    t,
                    now = { now },
                    ringing = t.id in ringingIds,
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMedium),
                    ),
                )
            }
            item(key = "nav") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun CreateTimerCard(onCreate: (name: String, ms: Long, start: Boolean) -> Unit) {
    val haptics = rememberHaptics()
    var custom by rememberSaveable { mutableStateOf(false) }
    var h by rememberSaveable { mutableStateOf("") }
    var m by rememberSaveable { mutableStateOf("") }
    var s by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val customMs = ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
    CanifCard {
        Text(tr("Nouveau minuteur", "New timer"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            presetMinutes.forEach { min ->
                FilledTonalButton(
                    onClick = { onCreate("$min min", min * 60_000L, true) },
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text("$min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Text("min", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { haptics.tick(); custom = !custom }) {
            Icon(if (custom) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            Spacer(Modifier.width(6.dp))
            Text(tr("Durée personnalisée", "Custom duration"))
        }
        AnimatedVisibility(custom, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(h, { h = it.take(2) }, tr("Heures", "Hours"), Modifier.weight(1f), suffix = "h", decimal = false)
                    NumberField(m, { m = it.take(3) }, tr("Minutes", "Minutes"), Modifier.weight(1f), suffix = "min", decimal = false)
                    NumberField(s, { s = it.take(4) }, tr("Secondes", "Seconds"), Modifier.weight(1f), suffix = "s", decimal = false)
                }
                OutlinedTextField(
                    name, { name = it.take(40) },
                    label = { Text(tr("Nom (facultatif)", "Name (optional)")) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FullRow {
                    OutlinedButton(
                        onClick = { onCreate(name.trim().ifEmpty { formatShortDuration(customMs) }, customMs, false); name = "" },
                        enabled = customMs >= 1000,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Ajouter", "Add"))
                    }
                    MorphButton(
                        onClick = { onCreate(name.trim().ifEmpty { formatShortDuration(customMs) }, customMs, true); name = "" },
                        active = false,
                        enabled = customMs >= 1000,
                        height = 56.dp,
                        modifier = Modifier.weight(1f),
                    ) { ButtonLabel(Icons.Rounded.PlayArrow, tr("Démarrer", "Start")) }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(t: TimerEntity, now: () -> Long, ringing: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val finished = !t.running && t.remainingMs <= 0
    val text by remember(t) { derivedStateOf { formatCountdown(remainingOf(t, now())) } }
    val ringColor by animateColorAsState(
        when {
            finished -> c.tertiary
            t.running -> c.primary
            else -> c.outline
        },
        spring(stiffness = Spring.StiffnessMediumLow), label = "ring",
    )
    val container by animateColorAsState(
        if (finished) c.tertiaryContainer else c.surfaceContainerHigh,
        spring(stiffness = Spring.StiffnessMediumLow), label = "container",
    )
    val pulse = if (finished && !reduce) {
        val inf = rememberInfiniteTransition(label = "done")
        inf.animateFloat(1f, 1.06f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    var rename by remember { mutableStateOf(false) }

    CanifCard(modifier, color = container, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(124.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse },
                contentAlignment = Alignment.Center,
            ) {
                ProgressRing(
                    progress = {
                        val r = remainingOf(t, now())
                        if (finished) 1f else r.toFloat() / maxOf(t.durationMs, r).coerceAtLeast(1)
                    },
                    modifier = Modifier.fillMaxSize(),
                    color = ringColor,
                    stroke = 10.dp,
                    head = !finished,
                )
                Text(
                    text,
                    style = if (text.length > 5) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (finished) c.onTertiaryContainer else c.onSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (finished) c.onTertiaryContainer else c.onSurface,
                    modifier = Modifier.clickable { rename = true },
                )
                Text(
                    when {
                        finished -> tr("Terminé !", "Done!")
                        t.running -> tr("En cours · ", "Running · ") + formatShortDuration(t.durationMs)
                        else -> tr("En pause · ", "Paused · ") + formatShortDuration(t.durationMs)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (finished) c.onTertiaryContainer else c.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MorphButton(
                        onClick = {
                            TimerManager.launch {
                                when {
                                    ringing -> stopRinging(t.id)
                                    else -> toggle(t.id)
                                }
                            }
                        },
                        active = t.running,
                        height = 52.dp,
                        container = when {
                            ringing -> c.tertiary
                            t.running -> c.secondaryContainer
                            else -> c.primary
                        },
                        content = when {
                            ringing -> c.onTertiary
                            t.running -> c.onSecondaryContainer
                            else -> c.onPrimary
                        },
                    ) {
                        AnimatedContent(
                            when {
                                ringing -> 2
                                t.running -> 1
                                else -> 0
                            },
                            transitionSpec = { scaleIn(spring(dampingRatio = 0.5f)) togetherWith scaleOut() },
                            label = "icon",
                        ) { s ->
                            Icon(
                                when (s) {
                                    2 -> Icons.Rounded.AlarmOff
                                    1 -> Icons.Rounded.Pause
                                    else -> if (finished) Icons.Rounded.Replay else Icons.Rounded.PlayArrow
                                },
                                when (s) {
                                    2 -> tr("Arrêter", "Stop")
                                    1 -> tr("Pause", "Pause")
                                    else -> tr("Démarrer", "Start")
                                },
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    FilledTonalIconButton(onClick = { haptics.click(); TimerManager.launch { addMinute(t.id) } }) {
                        Icon(Icons.Rounded.MoreTime, tr("+1 min", "+1 min"))
                    }
                    AnimatedVisibility(!t.running && !finished && t.remainingMs != t.durationMs, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        IconButton(onClick = { haptics.click(); TimerManager.launch { reset(t.id) } }) {
                            Icon(Icons.Rounded.Replay, tr("Réinitialiser", "Reset"))
                        }
                    }
                    IconButton(onClick = { haptics.reject(); TimerManager.launch { delete(t.id) } }) {
                        Icon(Icons.Rounded.DeleteOutline, tr("Supprimer", "Delete"))
                    }
                }
            }
        }
    }

    if (rename) {
        var value by remember { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(tr("Renommer", "Rename")) },
            text = {
                OutlinedTextField(value, { value = it.take(40) }, singleLine = true, shape = RoundedCornerShape(20.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = value.trim()
                    if (v.isNotEmpty()) TimerManager.launch { rename(t.id, v) }
                    rename = false
                }) { Text(tr("OK", "OK")) }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(tr("Annuler", "Cancel")) } },
        )
    }
}
