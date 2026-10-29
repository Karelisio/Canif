package com.karelisio.canif.tools.audio

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.rememberHaptics
import kotlinx.coroutines.delay

/** Icon button that steps once on tap and repeats, accelerating, while held. */
@Composable
fun RepeatIconButton(
    icon: ImageVector,
    contentDescription: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    var steppedByPress by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        steppedByPress = true
        step(); haptics.tick()
        delay(420)
        var interval = 120L
        while (true) {
            step(); haptics.tick()
            delay(interval)
            interval = (interval - 10).coerceAtLeast(30L)
        }
    }
    FilledTonalIconButton(
        onClick = { if (!steppedByPress) step(); steppedByPress = false },
        interactionSource = interaction,
        enabled = enabled,
        modifier = modifier.size(size),
    ) { Icon(icon, contentDescription) }
}

/** Text variant of [RepeatIconButton]. */
@Composable
fun RepeatTextButton(label: String, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    var steppedByPress by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        steppedByPress = true
        step(); haptics.tick()
        delay(420)
        var interval = 120L
        while (true) {
            step(); haptics.tick()
            delay(interval)
            interval = (interval - 10).coerceAtLeast(30L)
        }
    }
    FilledTonalButton(
        onClick = { if (!steppedByPress) step(); steppedByPress = false },
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
        modifier = modifier,
    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
}

/** A full-width row with a label and a switch, the whole row toggles. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null, supporting: String? = null) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .toggleable(checked, role = Role.Switch) { haptics.tick(); onChange(it) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun MicErrorCard() {
    CanifCard(color = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.MicOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                tr("Micro indisponible : il est peut-être utilisé par une autre appli.", "Microphone unavailable: another app may be using it."),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
