package com.karelisio.canif.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.SharedPayload
import com.karelisio.canif.core.tools.ToolRegistry
import com.karelisio.canif.ui.components.ToolIcon
import com.karelisio.canif.ui.components.cascade

/** "Open with" picker for content received from the Share menu. */
@Composable
fun ShareTargetScreen(payload: SharedPayload?, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val mime = when {
        payload == null -> null
        payload.uris.isNotEmpty() -> payload.mimeType ?: "*/*"
        payload.text != null -> "text/plain"
        else -> payload.mimeType
    }
    val tools = remember(mime) { ToolRegistry.available(context).filter { it.accepts(mime) } }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(tr("Ouvrir avec…", "Open with…")) },
                subtitle = {
                    val n = payload?.uris?.size ?: 0
                    Text(
                        when {
                            n > 1 -> tr("$n fichiers reçus", "$n files received")
                            n == 1 -> tr("1 fichier reçu", "1 file received")
                            else -> tr("Texte reçu", "Text received")
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, tr("Fermer", "Close")) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tools.isEmpty()) {
                item {
                    Text(tr("Aucun outil ne peut ouvrir ce contenu.", "No tool can open this content."), style = MaterialTheme.typography.bodyLarge)
                }
            }
            itemsIndexed(tools, key = { _, t -> t.id }) { i, tool ->
                ListItem(
                    headlineContent = { Text(tool.name.text, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(tool.category.title.text) },
                    leadingContent = { ToolIcon(tool, size = 48.dp) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .cascade(tool.id, i)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onOpen(tool.id) },
                )
            }
        }
    }
}
