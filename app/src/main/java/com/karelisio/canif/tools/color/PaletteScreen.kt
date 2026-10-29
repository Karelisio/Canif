package com.karelisio.canif.tools.color

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ToolScope.PaletteScreen() {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val themeColors = listOf(c.primary, c.secondary, c.tertiary)
    var argb by rememberSaveable { mutableStateOf(c.primary.toArgb()) }
    var harmony by rememberSaveable { mutableStateOf(ColorMath.Harmony.ANALOGOUS) }
    var hexText by remember { mutableStateOf(ColorMath.hex(Color(argb))) }
    var themeIndex by remember { mutableIntStateOf(0) }
    var generation by rememberSaveable { mutableIntStateOf(0) }
    val base = Color(argb)

    fun setBase(col: Color) {
        argb = col.toArgb()
        hexText = ColorMath.hex(col)
        generation++
    }

    val colors = remember(argb, harmony) { ColorMath.palette(base, harmony) }

    ToolColumn {
        // Base color
        Surface(shape = RoundedCornerShape(32.dp), color = c.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorphSwatch(base, size = 88.dp, shapeKey = generation)
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { v ->
                            hexText = v.uppercase().take(9)
                            ColorMath.parseHex(v)?.let { argb = it.toArgb() }
                        },
                        label = { Text(tr("Couleur de base", "Base color")) },
                        singleLine = true,
                        isError = ColorMath.parseHex(hexText) == null,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
                val hsl = ColorMath.toHsl(base)
                GradientSlider(
                    hsl[0] / 360f,
                    { setHue -> argb = ColorMath.fromHsl(setHue * 360f, hsl[1].coerceAtLeast(0.25f), hsl[2]).toArgb(); hexText = ColorMath.hex(Color(argb)) },
                    Brush.horizontalGradient(HueBrushColors),
                    base,
                    tr("Teinte", "Hue"),
                    "${hsl[0].roundToInt()}°",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { haptics.confirm(); setBase(ColorMath.random()) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(8.dp)); Text(tr("Aléatoire", "Random"))
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.click()
                            setBase(themeColors[themeIndex % themeColors.size])
                            themeIndex++
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(tr("Du thème", "From theme"))
                    }
                }
            }
        }

        SectionTitle(tr("Harmonie", "Harmony"))
        ChipChoice(ColorMath.Harmony.entries, harmony, label = { it.label.text }, onSelect = { harmony = it })

        // Strip preview
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)),
        ) {
            colors.forEachIndexed { i, col ->
                val anim by animateColorAsState(col, spring(stiffness = Spring.StiffnessMediumLow), label = "strip$i")
                Box(Modifier.weight(1f).fillMaxHeight().background(anim))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(tr("Couleurs", "Colors"), Modifier.weight(1f))
            TextButton(onClick = {
                copyToClipboard(context, colors.joinToString(", ") { ColorMath.hex(it) })
                haptics.confirm()
            }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Tout copier", "Copy all"))
            }
        }
        colors.forEachIndexed { i, col ->
            PaletteCard(col, Modifier.cascade("palette-$generation-${harmony.name}-$i", i))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PaletteCard(color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    var copied by remember { mutableStateOf(false) }
    val anim by animateColorAsState(color, spring(stiffness = Spring.StiffnessMediumLow), label = "card")
    val on = ColorMath.onColor(color)
    val hex = ColorMath.hex(color)
    Surface(
        onClick = {
            copyToClipboard(context, hex)
            haptics.confirm()
            copied = true
            scope.launch {
                bounce.snapTo(0.92f)
                bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
            }
            scope.launch { delay(1200); copied = false }
        },
        shape = RoundedCornerShape(28.dp),
        color = anim,
        modifier = modifier.fillMaxWidth().height(88.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hex, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace), color = on)
                Text(ColorMath.name(color) + " · " + ColorMath.hslText(color), style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.8f))
            }
            AnimatedContent(copied, transitionSpec = {
                scaleIn(spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) togetherWith scaleOut()
            }, label = "copied") { done ->
                Box(Modifier.size(40.dp).clip(CircleShape).background(on.copy(alpha = if (done) 0.22f else 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy, if (done) tr("Copié", "Copied") else tr("Copier", "Copy"), tint = on, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
