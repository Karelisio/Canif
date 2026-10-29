package com.karelisio.canif.tools.audio

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.data.PresetEntity
import com.karelisio.canif.tools.audio.MetronomeConfig.Companion.ACCENT
import com.karelisio.canif.tools.audio.MetronomeConfig.Companion.MUTE
import com.karelisio.canif.tools.audio.MetronomeConfig.Companion.NORMAL
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun tempoName(bpm: Int) = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 168 -> "Allegro"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

private fun subLabel(sub: Int) = when (sub) {
    2 -> tr("Croches", "8ths")
    3 -> tr("Triolets", "Triplets")
    4 -> tr("Doubles", "16ths")
    else -> tr("Aucune", "None")
}

/** Tap tempo: average of the last taps (up to 6), reset after 2 s of silence. */
private class TapTempo {
    private val taps = LongArray(6)
    private var count = 0

    fun tap(now: Long): Int? {
        if (count > 0 && now - taps[(count - 1) % taps.size] > 2000) count = 0
        taps[count % taps.size] = now
        count++
        val n = minOf(count, taps.size)
        if (n < 2) return null
        val newest = taps[(count - 1) % taps.size]
        val oldest = taps[(count - n) % taps.size]
        val avg = (newest - oldest).toDouble() / (n - 1)
        return if (avg > 0) (60000.0 / avg).roundToInt().coerceIn(MetronomeConfig.MIN_BPM, MetronomeConfig.MAX_BPM) else null
    }
}

@Composable
fun MetronomeScreen() {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val app = CanifApp.instance
    LaunchedEffect(Unit) { MetronomeController.load() }
    val cfg by MetronomeController.config.collectAsStateWithLifecycle()
    val running by MetronomeController.isRunning.collectAsStateWithLifecycle()
    val hasFlash = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }
    val presets by remember { app.db.presets().observe("metronome") }.collectAsStateWithLifecycle(emptyList())

    var currentBeat by remember { mutableIntStateOf(-1) }
    val pulse = remember { Animatable(1f) }
    val morphP = remember { Animatable(0f) }
    val rot = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    var accentShape by remember { mutableStateOf(false) }
    var flashFirst by remember { mutableStateOf(false) }
    val cfgNow by rememberUpdatedState(cfg)

    // Beat clock: polls the engine every frame; the engine publishes ticks when they reach the speaker.
    LaunchedEffect(running, reduce) {
        if (!running) {
            currentBeat = -1
            return@LaunchedEffect
        }
        var seen = MetronomeController.engine?.visualSeq ?: 0L
        while (true) {
            withFrameNanos { }
            val e = MetronomeController.engine ?: continue
            val s = e.visualSeq
            if (s == seen) continue
            seen = s
            val beat = e.visualBeat
            val sub = e.visualSub
            val acc = e.visualAccent
            if (sub == 0) currentBeat = beat
            if (reduce) continue
            val first = sub == 0 && beat == 0
            val strong = sub == 0 && acc == ACCENT
            val peak = when {
                sub != 0 -> 1.035f
                acc == MUTE -> 1.015f
                first -> 1.2f
                strong -> 1.14f
                else -> 1.09f
            }
            launch {
                pulse.snapTo(peak)
                pulse.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 380f))
            }
            if (sub == 0 && acc != MUTE) {
                accentShape = strong
                launch {
                    morphP.animateTo(1f, tween(55, easing = LinearOutSlowInEasing))
                    morphP.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 240f))
                }
                launch { rot.animateTo(rot.targetValue + 40f, spring(dampingRatio = 0.6f, stiffness = 180f)) }
                if (cfgNow.pulse) {
                    flashFirst = first
                    launch {
                        flash.snapTo(if (first) 0.42f else 0.24f)
                        flash.animateTo(0f, tween(280))
                    }
                }
            }
        }
    }

    val shapeColor by animateColorAsState(
        when {
            !running -> c.primaryContainer
            currentBeat == 0 -> c.tertiary
            else -> c.primary
        },
        spring(stiffness = Spring.StiffnessHigh), label = "beatColor",
    )
    val onShape by animateColorAsState(
        when {
            !running -> c.onPrimaryContainer
            currentBeat == 0 -> c.onTertiary
            else -> c.onPrimary
        },
        spring(stiffness = Spring.StiffnessHigh), label = "beatOn",
    )
    val flashColor = if (flashFirst) c.tertiary else c.primary
    val morphNormal = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Flower) }
    val morphAccent = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val path = remember { Path() }
    var showSave by remember { mutableStateOf(false) }
    val tapper = remember { TapTempo() }

    Box(Modifier.fillMaxSize()) {
        ToolColumn {
            // Pulsing, morphing beat shape.
            Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(230.dp)) {
                    val p = morphP.value.coerceIn(0f, 1f)
                    (if (accentShape) morphAccent else morphNormal).toPath(p, path)
                    val d = size.minDimension * 0.8f * pulse.value
                    withTransform({
                        rotate(rot.value, center)
                        translate(center.x - d / 2, center.y - d / 2)
                        scale(d, d, Offset.Zero)
                    }) { drawPath(path, shapeColor) }
                }
                Text(
                    if (running && currentBeat >= 0) "${currentBeat + 1}" else "${cfg.beats}/${cfg.unit}",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = onShape,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    },
                )
            }

            BeatRow(cfg, if (running) currentBeat else -1) { i ->
                haptics.tick()
                MetronomeController.update { m ->
                    val next = when (m.accentOf(i)) {
                        ACCENT -> NORMAL
                        NORMAL -> MUTE
                        else -> ACCENT
                    }
                    m.copy(accents = m.accents.mapIndexed { j, a -> if (j == i) next else a })
                }
            }
            Text(
                tr("Touchez un temps : accent → normal → muet", "Tap a beat: accent → normal → mute"),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Tempo.
            CanifCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RepeatIconButton(Icons.Rounded.Remove, tr("Ralentir", "Slower"), { MetronomeController.update { it.copy(bpm = it.bpm - 1) } }, enabled = cfg.bpm > MetronomeConfig.MIN_BPM)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        RollingText(
                            cfg.bpm.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            color = c.onSurface,
                        )
                        Text("BPM · ${tempoName(cfg.bpm)}", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                    }
                    RepeatIconButton(Icons.Rounded.Add, tr("Accélérer", "Faster"), { MetronomeController.update { it.copy(bpm = it.bpm + 1) } }, enabled = cfg.bpm < MetronomeConfig.MAX_BPM)
                }
                Slider(
                    value = cfg.bpm.toFloat(),
                    onValueChange = { v ->
                        val b = v.roundToInt()
                        if (b != cfg.bpm) {
                            if (b % 10 == 0) haptics.segment()
                            MetronomeController.setBpm(b)
                        }
                    },
                    valueRange = MetronomeConfig.MIN_BPM.toFloat()..MetronomeConfig.MAX_BPM.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val corner by animateDpAsState(if (running) 20.dp else 32.dp, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "corner")
                Button(
                    onClick = {
                        haptics.confirm()
                        MetronomeController.toggle(context)
                    },
                    shape = RoundedCornerShape(corner),
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) tr("Arrêter", "Stop") else tr("Démarrer", "Start"), style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalButton(
                    onClick = {
                        haptics.click()
                        tapper.tap(SystemClock.uptimeMillis())?.let { MetronomeController.setBpm(it) }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(64.dp),
                ) {
                    Icon(Icons.Rounded.TouchApp, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Tap", "Tap"), style = MaterialTheme.typography.titleMedium)
                }
            }

            SectionTitle(tr("Mesure", "Time signature"))
            ChipChoice(MetronomeConfig.signatures, cfg.beats to cfg.unit, { "${it.first}/${it.second}" }, { (b, u) ->
                MetronomeController.update { it.copy(beats = b, unit = u, accents = MetronomeConfig.defaultAccents(b, u)) }
            })

            SectionTitle(tr("Subdivision", "Subdivision"))
            ChoiceRow(listOf(1, 2, 3, 4), cfg.sub, { subLabel(it) }, { s -> MetronomeController.update { it.copy(sub = s) } })

            SectionTitle(tr("Options", "Options"))
            CanifCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                SwitchRow(tr("Vibrer à chaque temps", "Vibrate on each beat"), cfg.vibrate, { v -> MetronomeController.update { it.copy(vibrate = v) } }, Icons.Rounded.Vibration)
                if (hasFlash) {
                    SwitchRow(tr("Flash de la lampe", "Flashlight blink"), cfg.flash, { v -> MetronomeController.update { it.copy(flash = v) } }, Icons.Rounded.FlashlightOn)
                }
                SwitchRow(
                    tr("Pulsation plein écran", "Full-screen pulse"), cfg.pulse, { v -> MetronomeController.update { it.copy(pulse = v) } }, Icons.Rounded.Fullscreen,
                    supporting = tr("Fonctionne aussi écran éteint (sauf la pulsation).", "Keeps going with the screen off (except the pulse)."),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(tr("Préréglages", "Presets"), Modifier.weight(1f))
                TextButton(onClick = { showSave = true }) {
                    Icon(Icons.Rounded.BookmarkAdd, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Enregistrer", "Save"))
                }
            }
            if (presets.isEmpty()) {
                Text(
                    tr("Enregistrez vos réglages favoris pour les retrouver en un geste.", "Save your favorite settings to recall them in one tap."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
            presets.forEach { p -> PresetRow(p, onApply = {
                haptics.confirm()
                MetronomeConfig.fromJson(p.data, MetronomeController.config.value)?.let { n -> MetronomeController.update { n } }
            }, onDelete = {
                haptics.reject()
                app.appScope.launch { app.db.presets().delete(p.id) }
            }) }
        }
        if (cfg.pulse) {
            Canvas(Modifier.matchParentSize()) {
                val a = flash.value
                if (a > 0f) drawRect(flashColor, alpha = a)
            }
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf("${cfg.bpm} BPM · ${cfg.beats}/${cfg.unit}") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text(tr("Nouveau préréglage", "New preset")) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Nom", "Name")) }, singleLine = true, shape = RoundedCornerShape(20.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    val data = cfg.toJson(withOptions = false)
                    val n = name.trim().ifEmpty { "${cfg.bpm} BPM" }
                    app.appScope.launch { app.db.presets().insert(PresetEntity(kind = "metronome", name = n, data = data)) }
                    haptics.confirm()
                    showSave = false
                }) { Text(tr("Enregistrer", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text(tr("Annuler", "Cancel")) } },
        )
    }
}

@Composable
private fun BeatRow(cfg: MetronomeConfig, current: Int, onTap: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (i in 0 until cfg.beats) {
            val acc = cfg.accentOf(i)
            val isCurrent = i == current
            val h by animateDpAsState(
                when (acc) {
                    ACCENT -> 44.dp
                    NORMAL -> 28.dp
                    else -> 12.dp
                },
                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "h",
            )
            val scale by animateFloatAsState(if (isCurrent) 1.18f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "s")
            val color by animateColorAsState(
                when {
                    acc == MUTE -> c.outlineVariant
                    isCurrent && i == 0 -> c.tertiary
                    isCurrent -> c.primary
                    current < 0 && i == 0 -> c.tertiary.copy(alpha = 0.75f)
                    current < 0 -> c.primary.copy(alpha = 0.6f)
                    i == 0 -> c.tertiaryContainer
                    else -> c.primaryContainer
                },
                spring(stiffness = Spring.StiffnessHigh), label = "c",
            )
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTap(i) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .width(22.dp)
                            .height(h)
                            .clip(RoundedCornerShape(50))
                            .background(color),
                    )
                }
                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (isCurrent) c.onSurface else c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PresetRow(p: PresetEntity, onApply: () -> Unit, onDelete: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val cfg = remember(p.data) { MetronomeConfig.fromJson(p.data) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surfaceContainerHigh)
            .clickable(onClick = onApply)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (cfg != null) {
                Text(
                    "${cfg.bpm} BPM · ${cfg.beats}/${cfg.unit} · ${subLabel(cfg.sub)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, tr("Supprimer", "Delete")) }
    }
}
