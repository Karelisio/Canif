package com.karelisio.canif.tools.color

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.nav.ToolColumn
import kotlin.math.roundToInt

private enum class Space { RGB, HSL, HSV }

@Composable
fun ToolScope.ColorConvertScreen() {
    val c = MaterialTheme.colorScheme
    val initial = c.primary
    var argb by rememberSaveable { mutableStateOf(initial.toArgb()) }
    val color = Color(argb)
    // Hue/saturation kept separately so they survive grey colors where they are undefined.
    var hue by rememberSaveable { mutableFloatStateOf(ColorMath.toHsl(initial)[0]) }
    var space by rememberSaveable { mutableStateOf(Space.RGB) }
    var hexText by remember { mutableStateOf(ColorMath.hex(initial)) }

    fun set(new: Color, keepHue: Boolean = false) {
        argb = new.toArgb()
        if (!keepHue) {
            val hsl = ColorMath.toHsl(new)
            if (hsl[1] > 0.001f) hue = hsl[0]
        }
        hexText = ColorMath.hex(new)
    }

    val animated by animateColorAsState(color, spring(stiffness = Spring.StiffnessMedium), label = "preview")
    val on = ColorMath.onColor(color)

    ToolColumn {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(animated),
        ) {
            MorphSwatch(on.copy(alpha = 0.16f), Modifier.align(Alignment.TopEnd).padding(14.dp), size = 72.dp, shapeKey = argb)
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(ColorMath.name(color), style = MaterialTheme.typography.titleMedium, color = on)
                Text(ColorMath.hex(color), style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace), color = on)
            }
        }

        OutlinedTextField(
            value = hexText,
            onValueChange = { v ->
                hexText = v.uppercase().take(9)
                ColorMath.parseHex(v)?.let { parsed ->
                    argb = parsed.toArgb()
                    ColorMath.toHsl(parsed).let { if (it[1] > 0.001f) hue = it[0] }
                }
            },
            label = { Text(tr("Hexadécimal", "Hexadecimal")) },
            singleLine = true,
            isError = ColorMath.parseHex(hexText) == null,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        ChoiceRow(Space.entries, space, label = { it.name }, onSelect = { space = it })

        AnimatedContent(space, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sliders") { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s) {
                    Space.RGB -> {
                        val r = color.red; val g = color.green; val b = color.blue
                        GradientSlider(r, { set(Color(it, g, b)) }, Brush.horizontalGradient(listOf(Color(0f, g, b), Color(1f, g, b))), color, tr("Rouge", "Red"), "${color.r8}")
                        GradientSlider(g, { set(Color(r, it, b)) }, Brush.horizontalGradient(listOf(Color(r, 0f, b), Color(r, 1f, b))), color, tr("Vert", "Green"), "${color.g8}")
                        GradientSlider(b, { set(Color(r, g, it)) }, Brush.horizontalGradient(listOf(Color(r, g, 0f), Color(r, g, 1f))), color, tr("Bleu", "Blue"), "${color.b8}")
                    }
                    Space.HSL -> {
                        val hsl = ColorMath.toHsl(color)
                        val sat = hsl[1]; val l = hsl[2]
                        GradientSlider(hue / 360f, { hue = it * 360f; set(ColorMath.fromHsl(hue, sat, l), keepHue = true) }, Brush.horizontalGradient(HueBrushColors), ColorMath.fromHsl(hue, 1f, 0.5f), tr("Teinte", "Hue"), "${hue.roundToInt()}°")
                        GradientSlider(sat, { set(ColorMath.fromHsl(hue, it, l), keepHue = true) }, Brush.horizontalGradient(listOf(ColorMath.fromHsl(hue, 0f, l), ColorMath.fromHsl(hue, 1f, l))), color, tr("Saturation", "Saturation"), "${(sat * 100).roundToInt()} %")
                        GradientSlider(l, { set(ColorMath.fromHsl(hue, sat, it), keepHue = true) }, Brush.horizontalGradient(listOf(Color.Black, ColorMath.fromHsl(hue, sat, 0.5f), Color.White)), color, tr("Luminosité", "Lightness"), "${(l * 100).roundToInt()} %")
                    }
                    Space.HSV -> {
                        val hsv = ColorMath.toHsv(color)
                        val sat = hsv[1]; val v = hsv[2]
                        GradientSlider(hue / 360f, { hue = it * 360f; set(ColorMath.fromHsv(hue, sat, v), keepHue = true) }, Brush.horizontalGradient(HueBrushColors), ColorMath.fromHsv(hue, 1f, 1f), tr("Teinte", "Hue"), "${hue.roundToInt()}°")
                        GradientSlider(sat, { set(ColorMath.fromHsv(hue, it, v), keepHue = true) }, Brush.horizontalGradient(listOf(ColorMath.fromHsv(hue, 0f, v), ColorMath.fromHsv(hue, 1f, v))), color, tr("Saturation", "Saturation"), "${(sat * 100).roundToInt()} %")
                        GradientSlider(v, { set(ColorMath.fromHsv(hue, sat, it), keepHue = true) }, Brush.horizontalGradient(listOf(Color.Black, ColorMath.fromHsv(hue, sat, 1f))), color, tr("Valeur", "Value"), "${(v * 100).roundToInt()} %")
                    }
                }
            }
        }

        SectionTitle(tr("Formats (touchez pour copier)", "Formats (tap to copy)"))
        CopyRow("HEX", ColorMath.hex(color))
        CopyRow("RGB", ColorMath.rgbText(color))
        CopyRow("HSL", ColorMath.hslText(color))
        CopyRow("HSV", ColorMath.hsvText(color))
        CopyRow("Android", "0xFF" + ColorMath.hex(color).removePrefix("#"))
        Spacer(Modifier.height(16.dp))
    }
}
