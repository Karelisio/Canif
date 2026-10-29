package com.karelisio.canif.tools.random

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Attractions
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.data.PresetEntity
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.PolygonShape
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.random.Random

private const val SEP = "\u001F"
private const val KEY = "wheel_options"

private fun sectorAt(rotation: Float, n: Int): Int {
    if (n == 0) return 0
    val sweep = 360f / n
    val a = (((-rotation) % 360f) + 360f) % 360f
    return floor(a / sweep).toInt().coerceIn(0, n - 1)
}

@Composable
internal fun WheelScreen() {
    val settings = CanifApp.instance.settings
    val presetsDao = CanifApp.instance.db.presets()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val defaults = listOf(tr("Pizza", "Pizza"), tr("Sushis", "Sushi"), tr("Burger", "Burger"), tr("Salade", "Salad"), tr("Tacos", "Tacos"), tr("Pâtes", "Pasta"))
    var options by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var winner by remember { mutableStateOf<Int?>(null) }
    var spinning by remember { mutableStateOf(false) }
    var spinId by remember { mutableIntStateOf(0) }
    var saveDialog by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val pointerKick = remember { Animatable(0f) }
    val presets by presetsDao.observe("wheel").collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(Unit) {
        if (options == null) {
            val saved = settings.string(KEY, "").first()
            options = if (saved.isBlank()) defaults else saved.split(SEP).filter { it.isNotBlank() }
        }
    }
    val opts = options ?: defaults
    fun update(list: List<String>) {
        options = list
        winner = null
        scope.launch { settings.setString(KEY, list.joinToString(SEP)) }
    }

    val sound = remember { WheelSound() }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    // Haptic tick, click sound and pointer flick each time a sector boundary passes under the pointer.
    LaunchedEffect(opts.size) {
        snapshotFlow { sectorAt(rotation.value, opts.size) }.distinctUntilChanged().collect {
            if (spinning) {
                haptics.segment()
                sound.tick()
                if (!reduce) launch {
                    pointerKick.snapTo(-22f)
                    pointerKick.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessHigh))
                }
            }
        }
    }

    fun spin(velocity: Float) {
        if (opts.size < 2) return
        val token = ++spinId
        spinning = true
        winner = null
        scope.launch {
            try {
                if (reduce) {
                    rotation.snapTo(rotation.value + Random.nextFloat() * 360f + 720f)
                } else {
                    rotation.animateDecay(velocity, exponentialDecay(frictionMultiplier = 0.45f, absVelocityThreshold = 6f))
                }
                winner = sectorAt(rotation.value, opts.size)
                haptics.confirm()
                sound.win()
                // Keep the angle small to avoid float precision loss after many spins.
                rotation.snapTo(rotation.value % 360f)
            } finally {
                // A newer spin (or a finger grabbing the wheel) may have interrupted this one.
                if (spinId == token) spinning = false
            }
        }
    }

    ToolColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            WheelCanvas(
                options = opts,
                winner = winner,
                rotation = { rotation.value },
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(top = 18.dp)
                    .pointerInput(opts.size) {
                        val tracker = VelocityTracker()
                        var lastAngle = 0f
                        var acc = 0f
                        detectDragGestures(
                            onDragStart = { p ->
                                spinId++
                                spinning = false
                                acc = rotation.value
                                scope.launch { rotation.stop() }
                                tracker.resetTracking()
                                lastAngle = Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())).toFloat()
                            },
                            onDrag = { change, _ ->
                                val p = change.position
                                val a = Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())).toFloat()
                                var d = a - lastAngle
                                if (d > 180) d -= 360
                                if (d < -180) d += 360
                                lastAngle = a
                                change.consume()
                                acc += d
                                val target = acc
                                scope.launch { rotation.snapTo(target) }
                                // Track the accumulated angle as a 1D position (x), time in ms → degrees/s.
                                tracker.addPosition(change.uptimeMillis, Offset(acc, 0f))
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().x
                                if (abs(v) > 250f) { haptics.click(); spin(v.coerceIn(-4000f, 4000f)) }
                            },
                        )
                    },
            )
            // Pointer at the top.
            val c = MaterialTheme.colorScheme
            Spacer(
                Modifier
                    .size(34.dp, 40.dp)
                    .graphicsLayer { rotationZ = pointerKick.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f) }
                    .drawWithCache {
                        val path = Path().apply {
                            moveTo(size.width * 0.12f, 0f)
                            lineTo(size.width * 0.88f, 0f)
                            quadraticTo(size.width, 0f, size.width * 0.92f, size.height * 0.15f)
                            lineTo(size.width * 0.56f, size.height * 0.95f)
                            quadraticTo(size.width * 0.5f, size.height * 1.05f, size.width * 0.44f, size.height * 0.95f)
                            lineTo(size.width * 0.08f, size.height * 0.15f)
                            quadraticTo(0f, 0f, size.width * 0.12f, 0f)
                            close()
                        }
                        onDrawBehind {
                            drawPath(path, c.inverseSurface)
                            drawPath(path, c.surface, style = Stroke(2.dp.toPx()))
                        }
                    },
            )
        }

        AnimatedContent(
            winner,
            transitionSpec = { (scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f) + fadeIn()) togetherWith fadeOut() },
            label = "winner",
        ) { w ->
            val c = MaterialTheme.colorScheme
            if (w != null && w < opts.size) {
                CanifCard(color = c.tertiaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(PolygonShape(MaterialShapes.SoftBurst)).background(c.tertiary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Celebration, null, tint = c.onTertiary)
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(tr("Résultat", "Result"), style = MaterialTheme.typography.labelLarge, color = c.onTertiaryContainer)
                            Text(opts[w], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = c.onTertiaryContainer)
                        }
                    }
                }
            } else {
                Text(
                    if (spinning) tr("Ça tourne…", "Spinning…") else tr("Lancez la roue ou faites-la tourner du doigt", "Spin the wheel or flick it with your finger"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        Button(
            onClick = { haptics.click(); spin((if (Random.nextBoolean()) 1 else -1) * Random.nextInt(1500, 2600).toFloat()) },
            enabled = !spinning && opts.size >= 2,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.Attractions, null)
            Text("  " + tr("Lancer la roue", "Spin the wheel"), style = MaterialTheme.typography.titleMedium)
        }

        SectionTitle(tr("Options (${opts.size})", "Options (${opts.size})"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fun add() {
                val t = input.trim()
                if (t.isNotEmpty() && opts.size < 24) { haptics.tick(); update(opts + t); input = "" }
            }
            OutlinedTextField(
                input, { input = it.take(40) },
                placeholder = { Text(tr("Ajouter une option", "Add an option")) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = { add() }, enabled = input.isNotBlank() && opts.size < 24, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Add, tr("Ajouter", "Add"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEachIndexed { i, o ->
                InputChip(
                    selected = winner == i,
                    onClick = {},
                    label = { Text(o, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close, tr("Retirer", "Remove"),
                            Modifier.size(18.dp).clip(RoundedCornerShape(50)).pointerInput(i, o) {
                                detectTapGestures { if (!spinning) { haptics.tick(); update(opts.filterIndexed { j, _ -> j != i }) } }
                            },
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }

        SectionTitle(tr("Listes enregistrées", "Saved lists"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p -> PresetChip(p, onLoad = { list -> haptics.confirm(); update(list) }, onDelete = { scope.launch { presetsDao.delete(p.id) } }) }
            AssistChip(
                onClick = { saveDialog = true },
                label = { Text(tr("Enregistrer cette liste", "Save this list")) },
                leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp)) },
                shape = RoundedCornerShape(14.dp),
            )
        }
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text(tr("Nom de la liste", "List name")) },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, shape = RoundedCornerShape(20.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim().ifEmpty { tr("Ma roue", "My wheel") }
                    scope.launch { presetsDao.insert(PresetEntity(kind = "wheel", name = n, data = JSONArray(opts).toString())) }
                    haptics.confirm()
                    saveDialog = false
                }) { Text(tr("Enregistrer", "Save")) }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text(tr("Annuler", "Cancel")) } },
        )
    }
}

@Composable
private fun PresetChip(p: PresetEntity, onLoad: (List<String>) -> Unit, onDelete: () -> Unit) {
    val list = remember(p.data) { runCatching { JSONArray(p.data).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList()) }
    InputChip(
        selected = false,
        onClick = { if (list.size >= 2) onLoad(list) },
        label = { Text("${p.name} (${list.size})") },
        trailingIcon = {
            Icon(
                Icons.Filled.Close, tr("Supprimer", "Delete"),
                Modifier.size(18.dp).clip(RoundedCornerShape(50)).pointerInput(p.id) { detectTapGestures { onDelete() } },
            )
        },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun WheelCanvas(options: List<String>, winner: Int?, rotation: () -> Float, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val palette = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.primary to c.onPrimary,
        c.secondary to c.onSecondary,
        c.tertiary to c.onTertiary,
    )
    val dimmed = c.surfaceContainerHighest
    val onDimmed = c.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    Box(modifier, contentAlignment = Alignment.Center) {
        // The wheel is drawn once (cached) and turned with a cheap layer rotation.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation() }
                .drawWithCache {
                    val n = options.size.coerceAtLeast(1)
                    val sweep = 360f / n
                    val radius = size.minDimension / 2f
                    val maxText = (radius * 0.62f).toInt().coerceAtLeast(1)
                    val fontSize = when {
                        n <= 6 -> 16.sp
                        n <= 12 -> 13.sp
                        else -> 11.sp
                    }
                    val layouts = options.mapIndexed { i, o ->
                        // Last sector never shares the first one's color.
                        var ci = i % palette.size
                        if (i == n - 1 && n > 1 && ci == 0) ci = 2
                        val (bg, fg) = palette[ci]
                        val dim = winner != null && winner != i
                        Triple(
                            if (dim) dimmed else bg,
                            measurer.measure(
                                o, TextStyle(color = if (dim) onDimmed else fg, fontSize = fontSize, fontWeight = labelStyle.fontWeight),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                constraints = Constraints(maxWidth = maxText),
                            ),
                            dim,
                        )
                    }
                    onDrawBehind {
                        val ctr = Offset(size.width / 2, size.height / 2)
                        val topLeft = Offset(ctr.x - radius, ctr.y - radius)
                        layouts.forEachIndexed { i, (bg, text, _) ->
                            val start = -90f + i * sweep
                            drawArc(bg, start, sweep, true, topLeft, Size(radius * 2, radius * 2))
                            rotate(start + sweep / 2, ctr) {
                                drawText(text, topLeft = Offset(ctr.x + radius * 0.92f - text.size.width, ctr.y - text.size.height / 2f))
                            }
                        }
                        // Sector separators and rim.
                        for (i in 0 until n) {
                            rotate(-90f + i * sweep, ctr) {
                                drawLine(c.surface, ctr, Offset(ctr.x + radius, ctr.y), 2.dp.toPx())
                            }
                        }
                        drawCircle(c.surface, radius, ctr, style = Stroke(5.dp.toPx()))
                        for (i in 0 until n) {
                            rotate(-90f + i * sweep, ctr) { drawCircle(c.surface, 3.dp.toPx(), Offset(ctr.x + radius - 7.dp.toPx(), ctr.y)) }
                        }
                    }
                },
        )
        // Hub.
        Box(
            Modifier.size(56.dp).clip(PolygonShape(MaterialShapes.Cookie9Sided)).background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp).clip(PolygonShape(MaterialShapes.Cookie9Sided)).background(c.primary))
        }
    }
}

