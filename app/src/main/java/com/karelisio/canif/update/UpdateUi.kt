package com.karelisio.canif.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.BuildConfig
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.ui.components.BlobIcon
import com.karelisio.canif.ui.permissions.rememberPermissionRequester
import com.karelisio.canif.ui.settings.LinkRow
import kotlinx.coroutines.launch

/** Bottom sheet shown when a new version exists: changelog, animated download, install. */
@Composable
fun UpdateSheetHost() {
    val state by Updater.state.collectAsStateWithLifecycle()
    var forcedOpen by remember { mutableStateOf(false) }
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    } ?: return
    if (Updater.dismissedVersion == release.version && state is UpdateState.Available && !forcedOpen) return
    UpdateSheet(state, release, onDismiss = { Updater.dismissedVersion = release.version; forcedOpen = false })
}

@Composable
private fun UpdateSheet(state: UpdateState, release: Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val start = { scope.launch { Updater.downloadAndInstall(context.applicationContext, release) } }
    val requestInstall = rememberPermissionRequester(AppPermission.INSTALL_APPS) { ok -> if (ok) start() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BlobIcon(Icons.Rounded.SystemUpdate, size = 96.dp)
            Text(tr("Nouvelle version ${release.version}", "New version ${release.version}"), style = MaterialTheme.typography.headlineSmall)
            Text(
                tr("Vous avez la ${BuildConfig.VERSION_NAME}", "You have ${BuildConfig.VERSION_NAME}"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (release.changelog.isNotBlank()) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        renderChangelog(release.changelog),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            }
            AnimatedContent(state, contentKey = { it::class }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "upd") { s ->
                when (s) {
                    is UpdateState.Downloading -> {
                        val p by animateFloatAsState(s.progress, spring(dampingRatio = 0.9f, stiffness = 120f), label = "dl")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.padding(4.dp))
                            Text(tr("Téléchargement… ${(p * 100).toInt()} %", "Downloading… ${(p * 100).toInt()}%"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularWavyProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Installation…", "Installing…"))
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (s is UpdateState.Failed) {
                            Text(tr("Échec : ${s.message}", "Failed: ${s.message}"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.padding(4.dp))
                        }
                        Button(
                            onClick = {
                                if (AppPermission.INSTALL_APPS.isGranted(context)) start() else requestInstall()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(tr("Mettre à jour", "Update")) }
                        TextButton(onClick = onDismiss) { Text(tr("Plus tard", "Later")) }
                    }
                }
            }
        }
    }
}

/** Minimal Markdown: headings, bullets and **bold**. */
private fun renderChangelog(md: String): AnnotatedString = buildAnnotatedString {
    md.lines().forEachIndexed { i, raw ->
        val line = raw.trimEnd()
        if (i > 0) append('\n')
        when {
            line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.trimStart('#', ' ')) }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                append("  •  "); appendBold(line.trimStart().drop(2))
            }
            else -> appendBold(line)
        }
    }
}

private fun AnnotatedString.Builder.appendBold(text: String) {
    val parts = text.split("**")
    parts.forEachIndexed { i, p -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p) }
}

/** Settings entry: manual check with live status. */
@Composable
fun UpdateSettingsRow() {
    val state by Updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val subtitle = when (val s = state) {
        UpdateState.Checking -> tr("Recherche…", "Checking…")
        UpdateState.UpToDate -> tr("Canif est à jour", "Canif is up to date")
        is UpdateState.Available -> tr("Version ${s.release.version} disponible", "Version ${s.release.version} available")
        is UpdateState.Downloading -> tr("Téléchargement…", "Downloading…")
        is UpdateState.Installing -> tr("Installation…", "Installing…")
        is UpdateState.Failed -> tr("Échec de la vérification", "Check failed")
        UpdateState.Idle -> tr("Version ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}")
    }
    LinkRow(Icons.Rounded.SystemUpdate, tr("Rechercher des mises à jour", "Check for updates"), subtitle) {
        Updater.dismissedVersion = null
        scope.launch { Updater.check() }
    }
}

