package com.karelisio.canif.tools.calc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.PolygonShape
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Number parsing / formatting shared by the calculators. */
internal object Fmt {
    fun parse(s: String): Double? = s.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun symbols() = DecimalFormatSymbols.getInstance(Lang.locale)

    /** Fixed decimals, grouped. */
    fun num(x: Double, maxDecimals: Int = 2, minDecimals: Int = 0): String {
        if (!x.isFinite()) return "—"
        val df = DecimalFormat("#,##0", symbols())
        df.maximumFractionDigits = maxDecimals
        df.minimumFractionDigits = minDecimals
        val v = if (abs(x) < 0.5 * Math.pow(10.0, -maxDecimals.toDouble())) 0.0 else x
        return df.format(v)
    }

    fun money(x: Double): String = num(x, 2, 2)

    fun signed(x: Double, maxDecimals: Int = 2): String = (if (x > 0) "+" else "") + num(x, maxDecimals)

    /** Significant digits, switching to scientific notation for extreme values. */
    fun sig(x: Double, digits: Int = 7): String {
        if (!x.isFinite()) return "—"
        if (x == 0.0) return "0"
        val a = abs(x)
        if (a >= 1e15 || a < 1e-6) {
            val df = DecimalFormat("0.#####E0", symbols())
            return df.format(x).replace("E", " × 10^")
        }
        val bd = BigDecimal(x).round(MathContext(digits)).stripTrailingZeros()
        val df = DecimalFormat("#,##0", symbols())
        df.maximumFractionDigits = maxOf(0, bd.scale())
        return df.format(bd)
    }

    /** Device currency symbol (€ by default). */
    val currencySymbol: String
        get() = runCatching { Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault()) }.getOrNull()
            ?.takeIf { it.length <= 3 } ?: "€"
}

/** Round "swap" button whose arrows turn half a turn with a bouncy spring at each press. */
@Composable
internal fun SwapButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rot = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    FilledIconButton(
        onClick = {
            haptics.click()
            onClick()
            scope.launch {
                if (reduce) rot.snapTo(rot.value + 180f)
                else rot.animateTo(rot.targetValue + 180f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            }
        },
        modifier = modifier.size(56.dp),
        shape = PolygonShape(MaterialShapes.Cookie4Sided),
    ) {
        Icon(Icons.Rounded.SwapVert, tr("Permuter", "Swap"), Modifier.graphicsLayer { rotationZ = rot.value })
    }
}

/** −/+ stepper with a springy number. */
@Composable
internal fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    val haptics = rememberHaptics()
    val bump = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    fun change(d: Int) {
        val n = (value + d).coerceIn(range)
        if (n == value) { haptics.reject(); return }
        haptics.tick()
        onValueChange(n)
        if (!reduce) scope.launch {
            bump.snapTo(if (d > 0) 1.25f else 0.8f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(c.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
        FilledTonalIconButton(
            onClick = { change(-1) },
            enabled = value > range.first,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Rounded.Remove, tr("Moins", "Less")) }
        Box(Modifier.widthIn(min = 56.dp).graphicsLayer { scaleX = bump.value; scaleY = bump.value }, contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(value.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                if (suffix != null) Text(" $suffix", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
        FilledTonalIconButton(
            onClick = { change(1) },
            enabled = value < range.last,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Filled.Add, tr("Plus", "More")) }
    }
}

/** A labelled value on one line, for detail lists. */
@Composable
internal fun DetailRow(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, strong: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        RollingText(
            value,
            style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}

/** Compact stat tile with a colored dot (chart legend). */
@Composable
internal fun LegendStat(label: String, value: String, dot: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { copyToClipboard(context, value) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(dot))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        RollingText(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Small hint text under a card. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
internal fun CopyIcon(text: String) {
    val context = LocalContext.current
    IconButton(onClick = { copyToClipboard(context, text) }) { Icon(Icons.Rounded.ContentCopy, tr("Copier", "Copy")) }
}

