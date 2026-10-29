package com.karelisio.canif.tools.time

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.graphics.shapes.Morph
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.settings.AppSettings
import com.karelisio.canif.ui.components.MorphShape
import com.karelisio.canif.ui.theme.CanifTheme
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** Full-screen alarm shown over the lock screen when a timer finishes. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        // Backup path: launched from the backup notification while the service was not running.
        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: LongArray(0)
        val names = intent.getStringArrayExtra(EXTRA_NAMES) ?: emptyArray()
        val active = RingService.ringing.value.map { it.id }.toSet()
        ids.forEachIndexed { i, id ->
            if (id !in active) RingService.ring(this, id, names.getOrElse(i) { tr("Minuteur", "Timer") })
        }

        val app = application as CanifApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CanifTheme(settings) {
                val list by RingService.ringing.collectAsStateWithLifecycle()
                var hadItems by remember { mutableLongStateOf(0L) }
                LaunchedEffect(list.isEmpty()) {
                    if (list.isNotEmpty()) hadItems = 1L
                    else {
                        if (hadItems == 0L) delay(2500)
                        if (RingService.ringing.value.isEmpty()) finish()
                    }
                }
                RingScreen(
                    list,
                    onStop = { list.forEach { t -> TimerManager.launch { stopRinging(t.id) } } },
                    onAddMinute = { list.forEach { t -> TimerManager.launch { addMinute(t.id) } } },
                )
            }
        }
    }

    companion object {
        const val EXTRA_IDS = "ids"
        const val EXTRA_NAMES = "names"
    }
}

@Composable
private fun RingScreen(list: List<RingingTimer>, onStop: () -> Unit, onAddMinute: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val inf = rememberInfiniteTransition(label = "ring")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val since = list.minOfOrNull { it.since } ?: now
    Box(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            val s = if (reduce) 1f else 0.92f + 0.1f * pulse
                            scaleX = s; scaleY = s
                        }
                        .clip(MorphShape(morph, if (reduce) 0f else pulse, if (reduce) 0f else spin))
                        .background(c.tertiaryContainer),
                )
                Icon(Icons.Rounded.Timer, null, tint = c.onTertiaryContainer, modifier = Modifier.size(88.dp))
            }
            Text(
                if (list.size > 1) tr("Minuteurs terminés", "Timers finished") else tr("Minuteur terminé", "Timer finished"),
                style = MaterialTheme.typography.headlineMedium,
                color = c.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                list.joinToString("\n") { it.name }.ifEmpty { " " },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                "−" + formatCountdown(now - since),
                style = MaterialTheme.typography.titleLarge,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            MorphButton(
                onClick = onStop,
                active = false,
                container = c.primary,
                content = c.onPrimary,
                height = 96.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { ButtonLabel(Icons.Rounded.AlarmOff, tr("Arrêter", "Stop")) }
            MorphButton(
                onClick = onAddMinute,
                active = true,
                container = c.secondaryContainer,
                content = c.onSecondaryContainer,
                height = 72.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { ButtonLabel(Icons.Rounded.MoreTime, tr("+1 min", "+1 min")) }
        }
    }
}
