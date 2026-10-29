package com.karelisio.canif.tools.time

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.data.PresetEntity
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.MorphShape
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class BuiltIn(val name: String, val config: HiitConfig)

private val builtIns = listOf(
    BuiltIn("Tabata", HiitConfig(prepS = 10, workS = 20, restS = 10, rounds = 8, sets = 1)),
    BuiltIn("40 / 20", HiitConfig(prepS = 10, workS = 40, restS = 20, rounds = 10, sets = 1)),
    BuiltIn("30 / 30", HiitConfig(prepS = 10, workS = 30, restS = 30, rounds = 10, sets = 1)),
    BuiltIn("45 / 15", HiitConfig(prepS = 10, workS = 45, restS = 15, rounds = 8, sets = 2, setRestS = 90)),
    BuiltIn("Boxe 3′ / 1′", HiitConfig(prepS = 10, workS = 180, restS = 60, rounds = 12, sets = 1)),
)

private const val KIND = "hiit"

@Composable
fun IntervalsScreen() {
    val state by HiitEngine.state.collectAsStateWithLifecycle()
    AnimatedContent(
        state != null,
        transitionSpec = {
            (fadeIn() + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f))
                .togetherWith(fadeOut() + scaleOut(targetScale = 1.04f))
        },
        label = "hiit",
    ) { running ->
        if (running) HiitRun() else HiitSetup()
    }
}

// ---------------------------------------------------------------- setup

@Composable
private fun HiitSetup() {
    val context = LocalContext.current
    val app = CanifApp.instance
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var cfg by remember { mutableStateOf(HiitConfig()) }
    var voice by remember { mutableStateOf(true) }
    var beeps by remember { mutableStateOf(true) }
    var vibration by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        HiitConfig.fromJson(app.settings.string("hiit_last", "").first())?.let { cfg = it }
        voice = app.settings.bool("hiit_voice", true).first()
        beeps = app.settings.bool("hiit_beeps", true).first()
        vibration = app.settings.bool("hiit_vibration", true).first()
        loaded = true
    }
    val saved by remember { app.db.presets().observe(KIND) }.collectAsStateWithLifecycle(emptyList())
    var saveDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<PresetEntity?>(null) }

    fun update(c: HiitConfig) {
        cfg = c
        if (loaded) scope.launch { app.settings.setString("hiit_last", c.toJson()) }
    }

    ToolColumn {
        ReliabilityCards(exactAlarms = false, fullScreen = false)
        SectionTitle(tr("Préréglages", "Presets"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            builtIns.forEach { b ->
                FilterChip(
                    selected = cfg == b.config,
                    onClick = { haptics.tick(); update(b.config) },
                    label = { Text(b.name) },
                )
            }
            saved.forEach { p ->
                val c = HiitConfig.fromJson(p.data) ?: return@forEach
                FilterChip(
                    selected = cfg == c,
                    onClick = { haptics.tick(); update(c) },
                    label = { Text(p.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.Close, tr("Supprimer", "Delete"),
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .combinedClickable(onClick = { haptics.click(); toDelete = p }),
                        )
                    },
                )
            }
        }

        CanifCard {
            Stepper(tr("Préparation", "Get ready"), cfg.prepS, 0, 300, 5, ::secondsLabel) { update(cfg.copy(prepS = it)) }
            Stepper(tr("Travail", "Work"), cfg.workS, 5, 3600, 5, ::secondsLabel, MaterialTheme.colorScheme.tertiary) { update(cfg.copy(workS = it)) }
            Stepper(tr("Repos", "Rest"), cfg.restS, 0, 3600, 5, ::secondsLabel, MaterialTheme.colorScheme.primary) { update(cfg.copy(restS = it)) }
            Stepper(tr("Rounds", "Rounds"), cfg.rounds, 1, 99, 1, { "$it" }) { update(cfg.copy(rounds = it)) }
            Stepper(tr("Séries", "Sets"), cfg.sets, 1, 20, 1, { "$it" }) { update(cfg.copy(sets = it)) }
            AnimatedVisibility(cfg.sets > 1, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Stepper(tr("Repos entre séries", "Rest between sets"), cfg.setRestS, 0, 900, 15, ::secondsLabel) { update(cfg.copy(setRestS = it)) }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(voice, { haptics.tick(); voice = !voice; scope.launch { app.settings.setBool("hiit_voice", voice) } },
                label = { Text(tr("Voix", "Voice")) }, leadingIcon = { Icon(Icons.Rounded.RecordVoiceOver, null, Modifier.size(18.dp)) })
            FilterChip(beeps, { haptics.tick(); beeps = !beeps; scope.launch { app.settings.setBool("hiit_beeps", beeps) } },
                label = { Text(tr("Bips", "Beeps")) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(18.dp)) })
            FilterChip(vibration, { haptics.tick(); vibration = !vibration; scope.launch { app.settings.setBool("hiit_vibration", vibration) } },
                label = { Text(tr("Vibration", "Vibration")) }, leadingIcon = { Icon(Icons.Rounded.Vibration, null, Modifier.size(18.dp)) })
        }

        CanifCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Text(tr("Durée totale", "Total time"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            RollingText(formatCountdown(cfg.totalMs), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                tr("${cfg.rounds * cfg.sets} rounds de travail", "${cfg.rounds * cfg.sets} work rounds"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }

        FullRow {
            OutlinedButton(onClick = { saveDialog = true }, modifier = Modifier.weight(1f).height(64.dp)) {
                Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Enregistrer", "Save"))
            }
            MorphButton(
                onClick = {
                    haptics.confirm()
                    HiitEngine.cues = HiitCues(voice, beeps, vibration)
                    HiitEngine.start(context, cfg)
                },
                active = false,
                modifier = Modifier.weight(1.5f),
            ) { ButtonLabel(Icons.Rounded.PlayArrow, tr("Démarrer", "Start")) }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("${cfg.workS}/${cfg.restS} ×${cfg.rounds}") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text(tr("Enregistrer le préréglage", "Save preset")) },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, shape = RoundedCornerShape(20.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) app.appScope.launch { app.db.presets().insert(PresetEntity(kind = KIND, name = n, data = cfg.toJson())) }
                    haptics.confirm()
                    saveDialog = false
                }) { Text(tr("Enregistrer", "Save")) }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text(tr("Annuler", "Cancel")) } },
        )
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("Supprimer « ${p.name} » ?", "Delete “${p.name}”?")) },
            confirmButton = {
                TextButton(onClick = {
                    app.appScope.launch { app.db.presets().delete(p.id) }
                    haptics.reject()
                    toDelete = null
                }) { Text(tr("Supprimer", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Annuler", "Cancel")) } },
        )
    }
}

private fun secondsLabel(s: Int): String = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else tr("$s s", "$s s")

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    format: (Int) -> String,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    onChange: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { haptics.tick(); onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
        ) { Icon(Icons.Rounded.Remove, tr("Moins", "Less")) }
        Box(Modifier.width(84.dp), contentAlignment = Alignment.Center) {
            RollingText(format(value), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = accent)
        }
        FilledTonalIconButton(
            onClick = { haptics.tick(); onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
        ) { Icon(Icons.Rounded.Add, tr("Plus", "More")) }
    }
}

// ---------------------------------------------------------------- run

private fun shapeOf(kind: PhaseKind): RoundedPolygon = when (kind) {
    PhaseKind.PREP -> MaterialShapes.Cookie9Sided
    PhaseKind.WORK -> MaterialShapes.SoftBurst
    PhaseKind.REST -> MaterialShapes.Cookie12Sided
    PhaseKind.SET_REST -> MaterialShapes.Flower
    PhaseKind.DONE -> MaterialShapes.Sunny
}

@Composable
private fun HiitRun() {
    val st = HiitEngine.state.collectAsStateWithLifecycle().value ?: return
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(st.paused, st.finished) {
        now = SystemClock.elapsedRealtime()
        while (!st.paused && !st.finished) withFrameMillis { now = SystemClock.elapsedRealtime() }
    }
    val s = st
    val kind = if (s.finished) PhaseKind.DONE else s.phase.kind
    val seconds by remember { derivedStateOf { ((HiitEngine.state.value?.remaining(now) ?: 0) + 999) / 1000 } }

    // Haptic tick on the last three seconds.
    LaunchedEffect(seconds) { if (seconds in 1..3 && !s.paused) haptics.tick() }

    val (main, container, onContainer) = when (kind) {
        PhaseKind.WORK -> Triple(c.tertiary, c.tertiaryContainer, c.onTertiaryContainer)
        PhaseKind.REST, PhaseKind.SET_REST -> Triple(c.primary, c.primaryContainer, c.onPrimaryContainer)
        PhaseKind.PREP -> Triple(c.secondary, c.secondaryContainer, c.onSecondaryContainer)
        PhaseKind.DONE -> Triple(c.primary, c.primaryContainer, c.onPrimaryContainer)
    }
    val ringColor by animateColorAsState(main, spring(stiffness = Spring.StiffnessMediumLow), label = "ringc")
    val blobColor by animateColorAsState(container, spring(stiffness = Spring.StiffnessMediumLow), label = "blobc")
    val textColor by animateColorAsState(onContainer, spring(stiffness = Spring.StiffnessMediumLow), label = "textc")

    // Shape morph between phases.
    var fromShape by remember { mutableStateOf(shapeOf(kind)) }
    var toShape by remember { mutableStateOf(shapeOf(kind)) }
    val morphP = remember { Animatable(1f) }
    LaunchedEffect(kind) {
        if (shapeOf(kind) != toShape) {
            fromShape = toShape
            toShape = shapeOf(kind)
            morphP.snapTo(0f)
            morphP.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
        }
    }
    val morph = remember(fromShape, toShape) { Morph(fromShape, toShape) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 380.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                ProgressRing(
                    progress = {
                        val cur = HiitEngine.state.value ?: return@ProgressRing 1f
                        if (cur.finished) 1f else cur.remaining(now).toFloat() / cur.phase.durationMs.coerceAtLeast(1)
                    },
                    modifier = Modifier.fillMaxSize(),
                    color = ringColor,
                    stroke = 18.dp,
                )
                Box(
                    Modifier
                        .fillMaxSize(0.72f)
                        .graphicsLayer {
                            val cur = HiitEngine.state.value
                            var sc = 1f
                            if (!reduce && cur != null && !cur.paused && !cur.finished) {
                                val rem = cur.remaining(now)
                                if (rem in 1..3000) {
                                    val frac = (rem % 1000) / 1000f // 1 → 0 within each second
                                    sc = 1f + 0.14f * frac * frac
                                }
                            }
                            scaleX = sc; scaleY = sc
                            rotationZ = if (reduce) 0f else (now % 24_000L) / 24_000f * 360f
                        }
                        .clip(MorphShape(morph, morphP.value))
                        .background(blobColor),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        kind,
                        transitionSpec = {
                            (slideInVertically(spring(dampingRatio = 0.7f)) { it / 2 } + fadeIn())
                                .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "phase",
                    ) { k ->
                        Text(k.label.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = textColor)
                    }
                    if (s.finished) {
                        Icon(Icons.Rounded.Check, null, tint = textColor, modifier = Modifier.size(96.dp))
                    } else {
                        RollingText(
                            if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else seconds.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                            color = textColor,
                        )
                        val p = s.phase
                        Text(
                            tr("Round ", "Round ") + "${p.round.coerceAtLeast(1)}/${s.config.rounds}" +
                                if (s.config.sets > 1) tr(" · Série ${p.set}/${s.config.sets}", " · Set ${p.set}/${s.config.sets}") else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Overall progress.
            Column(Modifier.fillMaxWidth()) {
                val track = c.surfaceContainerHighest
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    val cur = HiitEngine.state.value
                    val p = cur?.overall(now) ?: 1f
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    drawRoundRect(ringColor, size = Size(size.width * p, size.height), cornerRadius = r)
                }
                Spacer(Modifier.height(6.dp))
                val totalLeft by remember {
                    derivedStateOf {
                        val cur = HiitEngine.state.value
                        if (cur == null || cur.finished) 0L
                        else cur.remaining(now) + cur.phases.drop(cur.index + 1).sumOf { it.durationMs }
                    }
                }
                Text(
                    tr("Reste ", "Left ") + formatCountdown(totalLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }

            if (s.finished) {
                MorphButton(
                    onClick = { HiitEngine.stop() },
                    active = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(Icons.Rounded.Check, tr("Terminer", "Finish")) }
            } else {
                FullRow {
                    FilledTonalIconButton(onClick = { haptics.reject(); HiitEngine.stop() }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.Stop, tr("Arrêter", "Stop"), Modifier.size(28.dp))
                    }
                    MorphButton(
                        onClick = { HiitEngine.togglePause() },
                        active = !s.paused,
                        container = if (s.paused) c.primary else c.secondaryContainer,
                        content = if (s.paused) c.onPrimary else c.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        AnimatedContent(s.paused, transitionSpec = { scaleIn(spring(dampingRatio = 0.5f)) togetherWith scaleOut() }, label = "pp") { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (p) ButtonLabel(Icons.Rounded.PlayArrow, tr("Reprendre", "Resume")) else ButtonLabel(Icons.Rounded.Pause, tr("Pause", "Pause"))
                            }
                        }
                    }
                    FilledTonalIconButton(onClick = { haptics.click(); HiitEngine.skip() }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.SkipNext, tr("Passer", "Skip"), Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
