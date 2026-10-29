package com.karelisio.canif.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.karelisio.canif.BuildConfig
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.settings.ThemeMode
import com.karelisio.canif.core.settings.UnitSystem
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.Routes
import com.karelisio.canif.ui.theme.LocalAppSettings
import com.karelisio.canif.update.UpdateSettingsRow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val s = LocalAppSettings.current
    val repo = CanifApp.instance.settings
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(tr("Réglages", "Settings")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour", "Back")) } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Group(tr("Apparence", "Appearance")) {
                Label(tr("Thème", "Theme"))
                ChoiceRow(
                    ThemeMode.entries, s.theme,
                    { when (it) { ThemeMode.SYSTEM -> tr("Auto", "Auto"); ThemeMode.LIGHT -> tr("Clair", "Light"); ThemeMode.DARK -> tr("Sombre", "Dark") } },
                    { scope.launch { repo.setTheme(it) } },
                )
                SwitchRow(tr("Couleurs dynamiques (Material You)", "Dynamic colors (Material You)"), s.dynamicColor) { scope.launch { repo.setDynamicColor(it) } }
                Label(tr("Langue", "Language"))
                ChoiceRow(listOf("system", "fr", "en"), s.language, { when (it) { "fr" -> "Français"; "en" -> "English"; else -> tr("Système", "System") } }, { scope.launch { repo.setLanguage(it) } })
            }
            Group(tr("Comportement", "Behavior")) {
                SwitchRow(tr("Retours haptiques", "Haptic feedback"), s.haptics) { haptics.confirm(); scope.launch { repo.setHaptics(it) } }
                Label(tr("Unités par défaut", "Default units"))
                ChoiceRow(UnitSystem.entries, s.units, { if (it == UnitSystem.METRIC) tr("Métrique", "Metric") else tr("Impérial", "Imperial") }, { scope.launch { repo.setUnits(it) } })
                var a4 by remember(s.referenceA4) { mutableFloatStateOf(s.referenceA4.toFloat()) }
                Label(tr("La de référence : ${a4.toInt()} Hz", "Reference A: ${a4.toInt()} Hz"))
                Slider(
                    value = a4,
                    onValueChange = { if (it.toInt() != a4.toInt()) haptics.tick(); a4 = it },
                    onValueChangeFinished = { scope.launch { repo.setReferenceA4(a4.toInt()) } },
                    valueRange = 432f..445f,
                    steps = 12,
                )
            }
            Group(tr("Système", "System")) {
                LinkRow(Icons.Rounded.Shield, tr("Autorisations", "Permissions"), tr("État de chaque autorisation", "Status of each permission")) { onOpen(Routes.PERMISSIONS) }
                LinkRow(Icons.Rounded.Info, tr("Revoir la présentation", "Replay the introduction"), null) { onOpen(Routes.ONBOARDING) }
                UpdateSettingsRow()
            }
            Text(
                "Canif ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " + tr("hors ligne, sans pub, sans tracking", "offline, no ads, no tracking"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@Composable
fun LinkRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
    }
}

