package com.karelisio.canif.tools.text

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.components.shareText

/** Multi-line input with paste / clear actions. */
@Composable
internal fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minHeight: Int = 160,
    maxHeight: Int = 360,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp, max = maxHeight.dp),
        trailingIcon = {
            Column {
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let {
                        haptics.click(); onValueChange(it)
                    }
                }) { Icon(Icons.Rounded.ContentPaste, tr("Coller", "Paste")) }
                AnimatedVisibility(value.isNotEmpty(), enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    IconButton(onClick = { haptics.click(); onValueChange("") }) { Icon(Icons.Filled.Clear, tr("Effacer", "Clear")) }
                }
            }
        },
    )
}

/** Copy + Share buttons for a text result. */
@Composable
internal fun ResultActions(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = { haptics.confirm(); copyToClipboard(context, text) },
            enabled = text.isNotEmpty(),
            modifier = Modifier.weight(1f),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(ButtonDefaults.IconSize))
            Text("  " + tr("Copier", "Copy"))
        }
        FilledTonalButton(
            onClick = { haptics.click(); shareText(context, text, tr("Partager", "Share")) },
            enabled = text.isNotEmpty(),
            modifier = Modifier.weight(1f),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Filled.Share, null, Modifier.size(ButtonDefaults.IconSize))
            Text("  " + tr("Partager", "Share"))
        }
    }
}

/** Card showing a text result (selectable), with an optional caption. */
@Composable
internal fun TextResult(text: AnnotatedString, caption: String? = null, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh, mono: Boolean = false) {
    CanifCard(color = color, padding = PaddingValues(20.dp), modifier = Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))) {
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        }
        SelectionContainer {
            Text(
                if (text.isEmpty()) AnnotatedString(tr("Le résultat s'affichera ici", "The result will appear here")) else text,
                style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        }
    }
}

@Composable
internal fun TextResult(text: String, caption: String? = null) = TextResult(AnnotatedString(text), caption)

/** A switch row inside an options card. */
@Composable
internal fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, { haptics.click(); onChange(it) })
    }
}
