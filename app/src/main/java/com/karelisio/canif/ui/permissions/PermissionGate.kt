package com.karelisio.canif.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.ui.components.BlobIcon

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Returns a function that asks for [permission] the right way (dialog or settings page) and
 * reports the new state. Permanently refused runtime permissions open the app details page.
 */
@Composable
fun rememberPermissionRequester(permission: AppPermission, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    var asked by remember { mutableStateOf(false) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        callback(permission.isGranted(context))
    }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val ok = permission.isGranted(context)
        if (!ok && asked) {
            val activity = context.findActivity()
            val blocked = activity != null && permission.runtime.none { activity.shouldShowRequestPermissionRationale(it) }
            if (blocked) settings.launch(AppPermission.appDetails(context))
        }
        callback(ok)
    }
    return {
        if (permission.isSpecial) {
            runCatching { settings.launch(permission.settingsIntent(context)) }
                .onFailure { settings.launch(AppPermission.appDetails(context)) }
        } else {
            asked = true
            runtime.launch(permission.runtime.toTypedArray())
        }
    }
}

/** Shows [content] once every permission is granted, otherwise an animated explanation. */
@Composable
fun PermissionGate(permissions: List<AppPermission>, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val needed = permissions.filter { it.applicable }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val missing = needed.firstOrNull { !it.isGranted(context) }
    if (missing == null) content() else PermissionExplainer(missing, onResult = { tick++ })
}

@Composable
fun PermissionExplainer(permission: AppPermission, onResult: (Boolean) -> Unit, onLater: (() -> Unit)? = null) {
    val request = rememberPermissionRequester(permission, onResult)
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        BlobIcon(permission.icon)
        Text(permission.title.text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            permission.rationale.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Button(onClick = request) { Text(tr("Autoriser", "Allow")) }
        if (onLater != null) TextButton(onClick = onLater) { Text(tr("Plus tard", "Later")) }
    }
}
