package com.karelisio.canif.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.SharedPayload
import com.karelisio.canif.core.tools.Tool
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.data.ToolRepository
import com.karelisio.canif.ui.components.HSpacer
import com.karelisio.canif.ui.components.ToolIcon
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.components.sharedBoundsKey
import com.karelisio.canif.ui.components.sharedElementKey
import com.karelisio.canif.ui.permissions.PermissionGate

@Composable
fun ToolHost(
    tool: Tool,
    source: String,
    incoming: SharedPayload?,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    LaunchedEffect(tool.id) { ToolRepository.recordUse(tool.id) }
    val scope = remember(tool, incoming) { ToolScope(tool, onBack, incoming, navigate) }
    Surface(
        Modifier
            .fillMaxSize()
            .sharedBoundsKey("tool-$source-${tool.id}"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (tool.fullscreen) {
            PermissionGate(tool.permissions) { tool.content(scope) }
        } else {
            val favorites by ToolRepository.favorites.collectAsStateWithLifecycle(emptyList())
            val isFav = tool.id in favorites
            val haptics = rememberHaptics()
            val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            Scaffold(
                modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    MediumFlexibleTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ToolIcon(tool, Modifier.sharedElementKey("icon-$source-${tool.id}"), size = 36.dp)
                                HSpacer(12.dp)
                                Text(tool.name.text, maxLines = 1)
                            }
                        },
                        subtitle = { Text(tool.category.title.text) },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour", "Back")) }
                        },
                        actions = {
                            IconButton(onClick = { haptics.confirm(); ToolRepository.toggleFavorite(tool.id) }) {
                                AnimatedContent(
                                    isFav,
                                    transitionSpec = {
                                        scaleIn(spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) togetherWith scaleOut()
                                    },
                                    label = "fav",
                                ) { fav ->
                                    if (fav) Icon(Icons.Rounded.Star, tr("Retirer des favoris", "Remove from favorites"), tint = MaterialTheme.colorScheme.tertiary)
                                    else Icon(Icons.Rounded.StarOutline, tr("Ajouter aux favoris", "Add to favorites"))
                                }
                            }
                        },
                        scrollBehavior = scroll,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                },
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    PermissionGate(tool.permissions) { tool.content(scope) }
                }
            }
        }
    }
}

/** Standard scrollable body of a tool screen: comfortable padding, centered on large screens. */
@Composable
fun ToolColumn(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    spacing: Int = 14,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            content = content,
        )
    }
}
