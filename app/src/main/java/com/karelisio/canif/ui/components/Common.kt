package com.karelisio.canif.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

/** The tool's icon inside its category shape. */
@Composable
fun ToolIcon(tool: Tool, modifier: Modifier = Modifier, size: Dp = 56.dp, rotation: Float = 0f) {
    CategoryBadge(tool.category, tool.icon, modifier, size, rotation)
}

@Composable
fun CategoryBadge(category: Category, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 56.dp, rotation: Float = 0f) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(PolygonShape(category.shape, rotation))
            .background(category.container(c)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = category.onContainer(c), modifier = Modifier.size(size * 0.46f))
    }
}

/** Rounded card used for results and tool sections. */
@Composable
fun CanifCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Big highlighted result with an animated value and an optional copy action. */
@Composable
fun ResultCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    copyable: Boolean = true,
    emphasized: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    CanifCard(modifier, color = if (emphasized) c.primaryContainer else c.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (emphasized) c.onPrimaryContainer else c.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                RollingText(
                    value,
                    style = if (emphasized) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                    color = if (emphasized) c.onPrimaryContainer else c.onSurface,
                )
                if (supporting != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = if (emphasized) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant)
                }
            }
            if (copyable) {
                IconButton(onClick = { copyToClipboard(context, value) }) {
                    Icon(Icons.Rounded.ContentCopy, tr("Copier", "Copy"))
                }
            }
        }
    }
}

/** Small stat block (label + rolling value), used in rows. */
@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        RollingText(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Connected expressive button group for a single choice. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, o ->
            ToggleButton(
                checked = o == selected,
                onCheckedChange = { haptics.tick(); onSelect(o) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Text(label(o), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Wrapping chips for longer option lists. */
@Composable
fun <T> ChipChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { haptics.tick(); onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Press feedback: springy scale-down while pressed. */
@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press")
    return scale(s)
}

@Composable
fun animatedContainer(active: Boolean): Color {
    val c = MaterialTheme.colorScheme
    val v by animateColorAsState(if (active) c.primaryContainer else c.surfaceContainerHigh, label = "container")
    return v
}

fun copyToClipboard(context: Context, text: String, label: String = "Canif") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own clipboard confirmation.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, tr("Copié", "Copied"), Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, text: String, title: String = "Canif") {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun HSpacer(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpacer(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun RowScope.Weighted(content: @Composable () -> Unit) = Box(Modifier.weight(1f)) { content() }
