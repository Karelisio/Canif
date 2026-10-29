package com.karelisio.canif.tools.instruments

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.permissions.findActivity
import kotlin.math.floor
import kotlin.math.roundToInt

/** ISO/IEC 7810 ID-1 card. */
private const val CARD_W_MM = 53.98f
private const val CARD_H_MM = 85.60f
private const val CARD_R_MM = 3.18f

@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
fun RulerScreen(onBack: () -> Unit) {
    LockOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    KeepScreenOn()
    HideSystemBars()
    val dm = LocalContext.current.resources.displayMetrics
    val factor = rememberFloatPref("ruler.factor", 1f)
    var calibrating by rememberSaveable { mutableStateOf(false) }
    BackHandler(calibrating) { calibrating = false }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedContent(calibrating, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "ruler") { cal ->
            if (cal) {
                CardCalibration(
                    ydpi = dm.ydpi, xdpi = dm.xdpi, initial = factor.value,
                    onSave = { factor.set(it); calibrating = false },
                    onCancel = { calibrating = false },
                )
            } else {
                RulerView(dm.ydpi * factor.value / 25.4f, onBack, onCalibrate = { calibrating = true }, calibrated = factor.value != 1f)
            }
        }
    }
}

@Composable
private fun RulerView(pxPerMm: Float, onBack: () -> Unit, onCalibrate: () -> Unit, calibrated: Boolean) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = c.onSurface)
    val unitStyle = MaterialTheme.typography.labelMedium.copy(color = c.primary)
    val labels = remember(labelStyle) { (0..60).map { measurer.measure(it.toString(), labelStyle) } }
    val cmUnit = remember(unitStyle) { measurer.measure("cm", unitStyle) }
    val inUnit = remember(unitStyle) { measurer.measure("in", unitStyle) }
    val pxPerInch = pxPerMm * 25.4f
    var cursor by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    if (cursor.isNaN()) cursor = pxPerMm * 50f
    var heightPx by remember { mutableFloatStateOf(0f) }
    var lastCm by remember { mutableStateOf(-1) }
    fun moveTo(y: Float) {
        cursor = y.coerceIn(0f, heightPx.coerceAtLeast(1f))
        val cm = floor(cursor / pxPerMm / 5f).toInt()
        if (cm != lastCm) { if (lastCm >= 0) haptics.tick(); lastCm = cm }
    }

    val strip = c.surfaceContainer
    val tickColor = c.onSurface
    val accent = c.primary
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(pxPerMm) { detectTapGestures { moveTo(it.y) } }
            .pointerInput(pxPerMm) { detectVerticalDragGestures(onDragStart = { moveTo(it.y) }) { ch, dy -> ch.consume(); moveTo(cursor + dy) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stripW = 72.dp.toPx()
            val w = size.width
            val h = size.height
            drawRoundRect(strip, Offset(-40.dp.toPx(), 0f), Size(stripW + 40.dp.toPx(), h), CornerRadius(28.dp.toPx()))
            drawRoundRect(strip, Offset(w - stripW, 0f), Size(stripW + 40.dp.toPx(), h), CornerRadius(28.dp.toPx()))
            // Metric scale (left), one tick per millimetre.
            val mmCount = (h / pxPerMm).toInt()
            for (i in 0..mmCount) {
                val y = i * pxPerMm
                val len = when { i % 10 == 0 -> 30.dp; i % 5 == 0 -> 20.dp; else -> 11.dp }.toPx()
                drawLine(tickColor.copy(alpha = if (i % 10 == 0) 1f else 0.75f), Offset(0f, y), Offset(len, y), if (i % 10 == 0) 2.dp.toPx() else 1.dp.toPx())
                if (i % 10 == 0 && i > 0 && i / 10 < labels.size) {
                    val l = labels[i / 10]
                    drawText(l, topLeft = Offset(36.dp.toPx(), y - l.size.height / 2f))
                }
            }
            // Imperial scale (right), down to 1/16 in.
            val sixteenth = pxPerInch / 16f
            val count = (h / sixteenth).toInt()
            for (i in 0..count) {
                val y = i * sixteenth
                val len = when { i % 16 == 0 -> 30.dp; i % 8 == 0 -> 22.dp; i % 4 == 0 -> 16.dp; i % 2 == 0 -> 11.dp; else -> 7.dp }.toPx()
                drawLine(tickColor.copy(alpha = if (i % 16 == 0) 1f else 0.75f), Offset(w, y), Offset(w - len, y), if (i % 16 == 0) 2.dp.toPx() else 1.dp.toPx())
                if (i % 16 == 0 && i > 0 && i / 16 < labels.size) {
                    val l = labels[i / 16]
                    drawText(l, topLeft = Offset(w - 36.dp.toPx() - l.size.width, y - l.size.height / 2f))
                }
            }
            drawText(cmUnit, topLeft = Offset(14.dp.toPx(), h - cmUnit.size.height - 18.dp.toPx()))
            drawText(inUnit, topLeft = Offset(w - 14.dp.toPx() - inUnit.size.width, h - inUnit.size.height - 18.dp.toPx()))
            // Cursor.
            drawLine(accent.copy(alpha = 0.18f), Offset(0f, cursor), Offset(w, cursor), 12.dp.toPx())
            drawLine(accent, Offset(0f, cursor), Offset(w, cursor), 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(accent, 6.dp.toPx(), Offset(stripW, cursor))
            drawCircle(accent, 6.dp.toPx(), Offset(w - stripW, cursor))
        }
        // Measurement pill that follows the cursor.
        val mm = cursor / pxPerMm
        val pillH = with(density) { 64.dp.toPx() }
        Surface(
            color = c.primaryContainer,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (cursor - pillH - with(density) { 14.dp.toPx() }).let { if (it < with(density) { 80.dp.toPx() }) cursor + with(density) { 14.dp.toPx() } else it }.roundToInt()) },
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (mm >= 10f) "%.1f cm".format(mm / 10f) else "%.1f mm".format(mm),
                    style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer,
                )
                Text("%.2f in".format(mm / 25.4f), style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour", "Back"))
            }
            FilledTonalIconButton(onClick = { haptics.click(); onCalibrate() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.CreditCard, tr("Calibrer", "Calibrate"))
            }
        }
        if (!calibrated) {
            Text(
                tr("Non calibrée", "Not calibrated"),
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 80.dp),
            )
        }
    }
}

@Composable
private fun CardCalibration(ydpi: Float, xdpi: Float, initial: Float, onSave: (Float) -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var f by remember { mutableFloatStateOf(initial) }
    val cardFill = c.primaryContainer
    val chip = c.tertiary
    val outline = c.primary
    val guide = c.outline
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val topPx = with(density) { 56.dp.toPx() }
        val cardHpx = CARD_H_MM * ydpi / 25.4f
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { ch, dy ->
                        ch.consume()
                        f = (f * (1f + dy / (cardHpx * f))).coerceIn(0.7f, 1.4f)
                    }
                },
        ) {
            val w = CARD_W_MM * xdpi / 25.4f * f
            val h = CARD_H_MM * ydpi / 25.4f * f
            val r = CARD_R_MM * xdpi / 25.4f * f
            val left = (size.width - w) / 2f
            val tl = Offset(left, topPx)
            val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
            drawLine(guide, Offset(0f, topPx), Offset(size.width, topPx), 1.dp.toPx(), pathEffect = dash)
            drawLine(guide, Offset(0f, topPx + h), Offset(size.width, topPx + h), 1.dp.toPx(), pathEffect = dash)
            drawRoundRect(cardFill, tl, Size(w, h), CornerRadius(r))
            drawRoundRect(outline, tl, Size(w, h), CornerRadius(r), style = Stroke(2.dp.toPx()))
            // Chip, drawn where it sits on a card held upright.
            val mm = xdpi / 25.4f * f
            drawRoundRect(chip.copy(alpha = 0.8f), Offset(left + w - 19f * mm, topPx + 10f * mm), Size(9f * mm, 12f * mm), CornerRadius(2f * mm))
            drawRoundRect(chip.copy(alpha = 0.35f), Offset(left + 6f * mm, topPx + h - 20f * mm), Size(4f * mm, 14f * mm), CornerRadius(1.5f * mm))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(c.surfaceContainerHigh, RoundedCornerShape(28.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("Posez une carte bancaire sur l'écran, coin en haut contre la ligne, puis ajustez le rectangle à sa taille (glissez verticalement).",
                    "Lay a bank card on the screen, top edge on the line, then resize the rectangle to match it (drag vertically)."),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { haptics.tick(); f = (f - 0.002f).coerceIn(0.7f, 1.4f) }) { Icon(Icons.Rounded.Remove, "−") }
                Slider(value = f, onValueChange = { f = it }, valueRange = 0.7f..1.4f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                FilledTonalIconButton(onClick = { haptics.tick(); f = (f + 0.002f).coerceIn(0.7f, 1.4f) }) { Icon(Icons.Rounded.Add, "+") }
            }
            Text("× %.3f".format(f), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text(tr("Annuler", "Cancel")) }
                OutlinedButton(onClick = { haptics.click(); f = 1f }) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp)); Text(" " + tr("Défaut", "Default"))
                }
                Button(onClick = { haptics.confirm(); onSave(f) }) { Text(tr("Enregistrer", "Save")) }
            }
        }
    }
}
