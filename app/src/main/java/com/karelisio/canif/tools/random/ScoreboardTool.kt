package com.karelisio.canif.tools.random

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NAMES = "scoreboard_names"
private const val SEP = "\u001F"

@Composable
internal fun ToolScope.ScoreboardScreen() {
    val settings = CanifApp.instance.settings
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var players by rememberSaveable { mutableIntStateOf(2) }
    var scores by rememberSaveable { mutableStateOf(List(4) { 0 }) }
    var names by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var faceToFace by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    // Keep the screen on while scoring.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        if (names == null) {
            val saved = settings.string(NAMES, "").first().split(SEP)
            names = List(4) { i -> saved.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "" }
        }
    }
    fun nameOf(i: Int) = names?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: tr("Joueur ${i + 1}", "Player ${i + 1}")
    fun add(i: Int, d: Int) {
        scores = scores.toMutableList().also { it[i] = it[i] + d }
        if (d > 0) haptics.tick() else haptics.reject()
    }

    val c = MaterialTheme.colorScheme
    val palette = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.primary to c.onPrimary,
    )

    @Composable
    fun P(i: Int, modifier: Modifier, rotated: Boolean = false) {
        val (bg, fg) = palette[i]
        PlayerPanel(
            name = nameOf(i), score = scores[i], bg = bg, fg = fg, rotated = rotated,
            onDelta = { d -> add(i, d) },
            onRename = { haptics.longPress(); renaming = i },
            modifier = modifier,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .systemBarsPadding()
            .padding(8.dp),
    ) {
        val gap = Arrangement.spacedBy(8.dp)
        when (players) {
            2 -> Column(Modifier.fillMaxSize(), verticalArrangement = gap) {
                P(0, Modifier.weight(1f).fillMaxWidth(), rotated = faceToFace)
                P(1, Modifier.weight(1f).fillMaxWidth())
            }
            3 -> Column(Modifier.fillMaxSize(), verticalArrangement = gap) {
                P(0, Modifier.weight(1f).fillMaxWidth(), rotated = faceToFace)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = gap) {
                    P(1, Modifier.weight(1f).fillMaxSize())
                    P(2, Modifier.weight(1f).fillMaxSize())
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = gap) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = gap) {
                    P(0, Modifier.weight(1f).fillMaxSize(), rotated = faceToFace)
                    P(1, Modifier.weight(1f).fillMaxSize(), rotated = faceToFace)
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = gap) {
                    P(2, Modifier.weight(1f).fillMaxSize())
                    P(3, Modifier.weight(1f).fillMaxSize())
                }
            }
        }

        // Discreet floating controls at the junction of the panels.
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(50),
            color = c.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour", "Back")) }
                TextButton(onClick = { haptics.click(); players = if (players >= 4) 2 else players + 1 }) {
                    Text(tr("$players joueurs", "$players players"), style = MaterialTheme.typography.labelLarge)
                }
                IconToggleButton(checked = faceToFace, onCheckedChange = { haptics.click(); faceToFace = it }) {
                    Icon(Icons.Rounded.ScreenRotation, tr("Face à face", "Face to face"))
                }
                IconButton(onClick = { haptics.click(); confirmReset = true }) { Icon(Icons.Rounded.RestartAlt, tr("Remettre à zéro", "Reset")) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Remettre les scores à zéro ?", "Reset all scores?")) },
            confirmButton = {
                TextButton(onClick = { haptics.confirm(); scores = List(4) { 0 }; confirmReset = false }) { Text(tr("Remettre à zéro", "Reset")) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Annuler", "Cancel")) } },
        )
    }
    renaming?.let { i ->
        var value by remember(i) { mutableStateOf(names?.getOrNull(i).orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr("Renommer", "Rename")) },
            text = {
                OutlinedTextField(
                    value, { value = it.take(24) }, singleLine = true, shape = RoundedCornerShape(20.dp),
                    placeholder = { Text(tr("Joueur ${i + 1}", "Player ${i + 1}")) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val list = (names ?: List(4) { "" }).toMutableList().also { it[i] = value.trim() }
                    names = list
                    scope.launch { settings.setString(NAMES, list.joinToString(SEP)) }
                    renaming = null
                }) { Text(tr("OK", "OK")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(tr("Annuler", "Cancel")) } },
        )
    }
}

@Composable
private fun PlayerPanel(
    name: String,
    score: Int,
    bg: Color,
    fg: Color,
    rotated: Boolean,
    onDelta: (Int) -> Unit,
    onRename: () -> Unit,
    modifier: Modifier,
) {
    val delta by rememberUpdatedState(onDelta)
    val rename by rememberUpdatedState(onRename)
    val reduce = LocalReduceMotion.current
    val bump = remember { Animatable(1f) }
    val float = remember { Animatable(0f) }
    var lastDelta by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val turn by androidx.compose.animation.core.animateFloatAsState(
        if (rotated) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "turn",
    )

    fun fire(d: Int) {
        delta(d)
        lastDelta = d
        if (reduce) return
        scope.launch {
            bump.snapTo(if (d > 0) 1.12f else 0.88f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
        scope.launch {
            float.snapTo(1f)
            float.animateTo(0f, tween(700))
        }
    }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(bg)
            .graphicsLayer { rotationZ = turn }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { fire(1) }, onLongPress = { rename() })
            }
            .pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = {
                        if (acc > threshold) fire(-1) else if (acc < -threshold) fire(1)
                    },
                ) { change, dy -> change.consume(); acc += dy }
            },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val big = with(density) { (minOf(maxWidth, maxHeight) * 0.5f).toSp() }
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp, start = 20.dp, end = 20.dp),
        )
        Box(Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }) {
            RollingText(score.toString(), style = TextStyle(fontSize = big, fontWeight = FontWeight.Black, color = fg))
        }
        // "+1" / "−1" floating away.
        Text(
            if (lastDelta > 0) "+1" else "−1",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val p = float.value
                    alpha = p
                    translationY = -(1f - p) * 60.dp.toPx() - size.height * 2.2f
                    translationX = maxWidth.toPx() * 0.28f
                },
        )
        Text(
            tr("Toucher +1 · glisser ↓ −1 · appui long : renommer", "Tap +1 · swipe ↓ −1 · long press: rename"),
            style = MaterialTheme.typography.labelSmall,
            color = fg.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
