package com.karelisio.canif.tools.time

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.permissions.rememberPermissionRequester
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- formatting

/** Stopwatch format: mm:ss.cc, or h:mm:ss.cc past one hour. */
fun formatStopwatch(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val cs = (t / 10) % 100
    val s = (t / 1000) % 60
    val m = (t / 60_000) % 60
    val h = t / 3_600_000
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
}

/** Countdown format, rounded up to the second: mm:ss or h:mm:ss. */
fun formatCountdown(ms: Long): String {
    val total = ((ms.coerceAtLeast(0) + 999) / 1000)
    val s = total % 60
    val m = (total / 60) % 60
    val h = total / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Short human duration: "5 min", "1 h 30", "45 s". */
fun formatShortDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return when {
        h > 0 && m > 0 -> "$h h ${"%02d".format(m)}"
        h > 0 -> "$h h"
        m > 0 && s > 0 -> "$m min $s s"
        m > 0 -> "$m min"
        else -> "$s s"
    }
}

// ---------------------------------------------------------------- visuals

/**
 * Circular progress ring drawn on a Canvas. [progress] is read in the draw phase only,
 * so it can change every frame without recomposing anything.
 */
@Composable
fun ProgressRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    stroke: Dp = 14.dp,
    head: Boolean = true,
) {
    Canvas(modifier) {
        val w = stroke.toPx()
        val d = size.minDimension - w
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        val arcSize = Size(d, d)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
        val p = progress().coerceIn(0f, 1f)
        if (p > 0f) {
            drawArc(color, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (head) {
                val a = Math.toRadians((-90f + 360f * p).toDouble())
                val r = d / 2
                val c = Offset(size.width / 2 + (r * cos(a)).toFloat(), size.height / 2 + (r * sin(a)).toFloat())
                drawCircle(color, w * 0.85f, c)
                drawCircle(track, w * 0.35f, c)
            }
        }
    }
}

/**
 * Expressive button whose shape morphs: a pill when idle, a softer square when [active],
 * squeezing further while pressed. Color and width follow with springs.
 */
@Composable
fun MorphButton(
    onClick: () -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
    container: Color = if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primary,
    content: Color = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary,
    height: Dp = 72.dp,
    enabled: Boolean = true,
    body: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        when {
            pressed -> height * 0.22f
            active -> height * 0.32f
            else -> height / 2
        },
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "corner",
    )
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "scale")
    val bg by animateColorAsState(container, spring(stiffness = Spring.StiffnessMediumLow), label = "bg")
    val fg by animateColorAsState(content, spring(stiffness = Spring.StiffnessMediumLow), label = "fg")
    Surface(
        onClick = { haptics.click(); onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = bg.copy(alpha = if (enabled) 1f else 0.4f),
        contentColor = fg,
        interactionSource = interaction,
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = body,
        )
    }
}

@Composable
fun ButtonLabel(icon: ImageVector, text: String?) {
    Icon(icon, contentDescription = text, modifier = Modifier.size(28.dp))
    if (text != null) {
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---------------------------------------------------------------- reliability cards

@Composable
fun WarningCard(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    val c = MaterialTheme.colorScheme
    CanifCard(color = c.errorContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = c.onErrorContainer, modifier = Modifier.padding(top = 2.dp).size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.onErrorContainer)
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer.copy(alpha = 0.85f))
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

fun canUseFullScreenIntent(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

/**
 * Warning cards for everything that can keep a timer from ringing on time:
 * exact alarms, battery optimization (Honor / MagicOS kills apps) and full-screen alerts.
 */
@Composable
fun ReliabilityCards(exactAlarms: Boolean, fullScreen: Boolean, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val refresh = { tick++; onChanged() }
    val requestExact = rememberPermissionRequester(AppPermission.EXACT_ALARMS) { refresh() }
    val requestBattery = rememberPermissionRequester(AppPermission.BATTERY) { refresh() }
    @Suppress("UNUSED_EXPRESSION") tick
    val exactMissing = exactAlarms && AppPermission.EXACT_ALARMS.applicable && !AppPermission.EXACT_ALARMS.isGranted(context)
    val batteryMissing = !AppPermission.BATTERY.isGranted(context)
    val fsiMissing = fullScreen && !canUseFullScreenIntent(context)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(exactMissing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            WarningCard(
                AppPermission.EXACT_ALARMS.icon,
                tr("Alarmes exactes désactivées", "Exact alarms are off"),
                tr(
                    "Sans elles, Android peut retarder la sonnerie de plusieurs minutes quand le téléphone est en veille.",
                    "Without them, Android may delay the alarm by several minutes while the phone sleeps.",
                ),
                tr("Autoriser", "Allow"),
                requestExact,
            )
        }
        AnimatedVisibility(batteryMissing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            WarningCard(
                AppPermission.BATTERY.icon,
                tr("Optimisation de la batterie active", "Battery optimization is on"),
                tr(
                    "Certains téléphones (Honor / MagicOS, Huawei, Xiaomi…) coupent les applis en arrière-plan. Désactivez l'optimisation pour Canif.",
                    "Some phones (Honor / MagicOS, Huawei, Xiaomi…) kill background apps. Turn optimization off for Canif.",
                ),
                tr("Désactiver", "Turn off"),
                requestBattery,
            )
        }
        AnimatedVisibility(fsiMissing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            WarningCard(
                AppPermission.NOTIFICATIONS.icon,
                tr("Alertes plein écran refusées", "Full-screen alerts denied"),
                tr(
                    "Autorisez-les pour que la sonnerie s'affiche sur l'écran verrouillé.",
                    "Allow them so the alarm shows up on the lock screen.",
                ),
                tr("Autoriser", "Allow"),
            ) {
                val pkg = Uri.parse("package:${context.packageName}")
                runCatching {
                    check(Build.VERSION.SDK_INT >= 34)
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { runCatching { context.startActivity(AppPermission.appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
        }
    }
}

/** Ensures every row fills the width; small helper for button rows. */
@Composable
fun FullRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
