package com.karelisio.canif.tools.random

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.random.Random

@Composable
internal fun CoinScreen() {
    val rot = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    var heads by rememberSaveable { mutableIntStateOf(0) }
    var tails by rememberSaveable { mutableIntStateOf(0) }
    var last by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = heads (face)
    var recent by rememberSaveable { mutableStateOf(listOf<Boolean>()) }
    var flipping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val c = MaterialTheme.colorScheme

    fun flip() {
        if (flipping) return
        flipping = true
        haptics.click()
        val isHeads = Random.nextBoolean()
        scope.launch {
            val turns = Random.nextInt(4, 7)
            val base = floor(rot.value / 360f) * 360f
            val target = base + turns * 360f + if (isHeads) 0f else 180f
            if (reduce) {
                rot.snapTo(target)
            } else {
                val spin = async { rot.animateTo(target, tween(1250, easing = LinearOutSlowInEasing)) }
                // Ballistic toss: up fast, slow at the top, accelerating down.
                lift.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
                lift.animateTo(0f, tween(560, easing = FastOutLinearInEasing))
                spin.await()
                squash.snapTo(0.82f)
                haptics.confirm()
                squash.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMedium))
            }
            if (reduce) haptics.confirm()
            if (isHeads) heads++ else tails++
            last = isHeads
            recent = (recent + isHeads).takeLast(24)
            flipping = false
        }
    }

    ToolColumn {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { flip() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Shadow on the "table", shrinking as the coin rises.
            Spacer(
                Modifier
                    .padding(bottom = 18.dp)
                    .size(170.dp, 26.dp)
                    .drawBehind {
                        val k = 1f - 0.6f * lift.value
                        val w = size.width * k
                        drawOval(c.scrim.copy(alpha = 0.18f * k), Offset((size.width - w) / 2, 0f), Size(w, size.height * k))
                    },
            )
            Box(
                Modifier
                    .padding(bottom = 30.dp)
                    .size(190.dp)
                    .graphicsLayer {
                        rotationY = rot.value
                        cameraDistance = 14f * density
                        translationY = -lift.value * 130.dp.toPx()
                        val s = (1f + 0.22f * lift.value) * squash.value
                        scaleX = (1f + 0.22f * lift.value) * (2f - squash.value)
                        scaleY = s
                    },
            ) {
                CoinFace(
                    heads = true,
                    Modifier.graphicsLayer { alpha = if (isBack(rot.value)) 0f else 1f },
                )
                CoinFace(
                    heads = false,
                    Modifier.graphicsLayer {
                        rotationY = 180f
                        alpha = if (isBack(rot.value)) 1f else 0f
                    },
                )
            }
        }

        val resultText = when (last) {
            null -> tr("Touchez la pièce", "Tap the coin")
            true -> tr("Face !", "Heads!")
            false -> tr("Pile !", "Tails!")
        }
        Text(
            if (flipping) "…" else resultText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = c.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Button(onClick = { flip() }, enabled = !flipping, modifier = Modifier.fillMaxWidth().height(64.dp), shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Toll, null)
            Text("  " + tr("Lancer la pièce", "Flip the coin"), style = MaterialTheme.typography.titleMedium)
        }

        val total = heads + tails
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("Face", "Heads"), "$heads" + if (total > 0) " · ${heads * 100 / total} %" else "", Modifier.weight(1f), c.primaryContainer)
            StatBlock(tr("Pile", "Tails"), "$tails" + if (total > 0) " · ${tails * 100 / total} %" else "", Modifier.weight(1f), c.tertiaryContainer)
        }
        if (recent.isNotEmpty()) {
            CanifCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Derniers lancers", "Recent flips"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    RollingText(tr("$total lancers", "$total flips"), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                }
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { h ->
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (h) c.primary else c.tertiary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (h) tr("F", "H") else tr("P", "T"), style = MaterialTheme.typography.labelSmall, color = if (h) c.onPrimary else c.onTertiary)
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { haptics.click(); heads = 0; tails = 0; recent = emptyList(); last = null },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(ButtonDefaults.IconSize))
                Text("  " + tr("Remettre à zéro", "Reset"))
            }
        }
    }
}

private fun isBack(deg: Float): Boolean {
    val r = ((deg % 360f) + 360f) % 360f
    return r > 90f && r < 270f
}

@Composable
private fun CoinFace(heads: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val bg = if (heads) c.primary else c.tertiary
    val fg = if (heads) c.onPrimary else c.onTertiary
    val rim = if (heads) c.primaryContainer else c.tertiaryContainer
    Box(
        modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(bg)
            .border(8.dp, rim, CircleShape)
            .drawBehind {
                // Engraved inner ring and notches.
                val r = size.minDimension / 2
                drawCircle(fg.copy(alpha = 0.35f), r * 0.78f, style = Stroke(2.dp.toPx()))
                val notches = 48
                for (i in 0 until notches) {
                    val a = Math.toRadians(i * 360.0 / notches)
                    val cx = center.x + (r * 0.86f) * kotlin.math.cos(a).toFloat()
                    val cy = center.y + (r * 0.86f) * kotlin.math.sin(a).toFloat()
                    drawCircle(fg.copy(alpha = 0.25f), 1.6.dp.toPx(), Offset(cx, cy))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (heads) Icon(Icons.Rounded.Star, null, tint = fg, modifier = Modifier.size(56.dp))
            else Text("1", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = fg)
            Text(
                if (heads) tr("FACE", "HEADS") else tr("PILE", "TAILS"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
                letterSpacing = 3.sp,
            )
        }
    }
}
