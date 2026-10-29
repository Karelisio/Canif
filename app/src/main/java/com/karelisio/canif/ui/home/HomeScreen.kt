package com.karelisio.canif.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool
import com.karelisio.canif.core.tools.ToolRegistry
import com.karelisio.canif.data.ToolRepository
import com.karelisio.canif.system.Shortcuts
import com.karelisio.canif.ui.components.AnimatedKnifeLogo
import com.karelisio.canif.ui.components.CategoryBadge
import com.karelisio.canif.ui.components.ToolIcon
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.fanUnfold
import com.karelisio.canif.ui.components.pressScale
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.components.sharedBoundsKey
import com.karelisio.canif.ui.components.sharedElementKey
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Opens a tool; [source] identifies the tile for the shared element transition. */
typealias OpenTool = (tool: Tool, source: String) -> Unit

@Composable
fun HomeScreen(onOpen: OpenTool, onSettings: () -> Unit, showIntroLogo: Boolean) {
    val context = LocalContext.current
    val favoriteIds by ToolRepository.favorites.collectAsStateWithLifecycle(emptyList())
    val recentIds by ToolRepository.recents.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember { ToolRegistry.byCategory(context) }
    val favorites = remember(favoriteIds) { favoriteIds.mapNotNull { ToolRegistry.get(it) }.filter { it.isAvailable(context) } }
    val recents = remember(recentIds) { recentIds.mapNotNull { ToolRegistry.get(it) }.filter { it.isAvailable(context) }.take(8) }
    val results = remember(query) { ToolRegistry.search(context, query) }
    var menuFor by remember { mutableStateOf<Pair<Tool, String>?>(null) }

    val bars = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bars.calculateTopPadding() + 8.dp, bottom = nav.calculateBottomPadding() + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        fullLine("header") { Header(onSettings, showIntroLogo) }
        fullLine("search") { SearchField(query) { query = it } }

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                fullLine("empty") { EmptySearch() }
            } else {
                itemsIndexed(results, key = { _, t -> "s-" + t.id }) { i, tool ->
                    ToolTile(
                        tool, "srch", onOpen,
                        onLongPress = { menuFor = tool to "srch" },
                        modifier = Modifier.animateItem().cascade("s-${tool.id}-$query", i),
                    )
                }
            }
        } else {
            fullLine("fav-title") { SectionHeader(tr("Favoris", "Favorites")) }
            fullLine("fav") {
                if (favorites.isEmpty()) FavoritesHint()
                else FavoritesRow(favorites, onOpen, onMenu = { menuFor = it to "fav" })
            }
            if (recents.isNotEmpty()) {
                fullLine("rec-title") { SectionHeader(tr("Récents", "Recent")) }
                fullLine("rec") { RecentsRow(recents, onOpen) }
            }
            fullLine("cat-title") { SectionHeader(tr("Tous les outils", "All tools")) }
            categoryItems(categories, expanded, onToggle = { expanded = if (expanded == it.name) null else it.name }, onOpen, onMenu = { menuFor = it to "cat" })
        }
    }

    // Content scrolls under the transparent status bar: keep the clock readable.
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    )

    menuFor?.let { (tool, _) ->
        ToolMenu(tool, tool.id in favoriteIds, onDismiss = { menuFor = null }, context)
    }
}

private fun LazyGridScope.fullLine(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.animateItem()) { content() } }
}

private fun LazyGridScope.categoryItems(
    categories: Map<Category, List<Tool>>,
    expanded: String?,
    onToggle: (Category) -> Unit,
    onOpen: OpenTool,
    onMenu: (Tool) -> Unit,
) {
    // Closed categories go two per row whatever the grid density (large fonts, small screens);
    // the open one takes a full row and its tools fan out below it.
    val rows = mutableListOf<List<Category>>()
    var pending: Category? = null
    categories.keys.forEach { c ->
        if (c.name == expanded) {
            pending?.let { rows += listOf(it); pending = null }
            rows += listOf(c)
        } else if (pending == null) {
            pending = c
        } else {
            rows += listOf(pending!!, c); pending = null
        }
    }
    pending?.let { rows += listOf(it) }

    rows.forEachIndexed { i, row ->
        item(key = "cat-" + row.joinToString("-") { it.name }, span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.animateItem().cascade("cat-" + row.first().name, i + 3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { c ->
                    CategoryCard(c, categories.getValue(c).size, c.name == expanded, { onToggle(c) }, Modifier.weight(1f))
                }
                if (row.size == 1 && row.first().name != expanded) Spacer(Modifier.weight(1f))
            }
        }
        val open = row.firstOrNull { it.name == expanded }
        if (open != null) {
            itemsIndexed(categories.getValue(open), key = { _, t -> "t-" + t.id }) { ti, tool ->
                FanTile(ti) {
                    ToolTile(tool, "cat", onOpen, onLongPress = { onMenu(tool) })
                }
            }
        }
    }
}

/** Each tool swings out of the category like a blade, one after the other, on a spring. */
@Composable
private fun FanTile(index: Int, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(45L * index)
            p.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
    }
    Box(Modifier.fanUnfold(p.value)) { content() }
}

@Composable
private fun Header(onSettings: () -> Unit, showIntroLogo: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedKnifeLogo(size = 56.dp, startDelayMs = if (showIntroLogo) 900 else 150)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Canif", style = MaterialTheme.typography.displaySmall)
            Text(
                tr("Votre boîte à outils de poche", "Your pocket toolbox"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, tr("Réglages", "Settings")) }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(tr("Rechercher un outil…", "Search a tool…")) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, tr("Effacer", "Clear")) }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun EmptySearch() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(tr("Aucun outil trouvé", "No tool found"), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FavoritesHint() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.StarOutline, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Text(
                tr("Appui long sur un outil pour l'ajouter ici.", "Long-press a tool to pin it here."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tool tile: shaped icon + name. Shared bounds with the tool screen. */
@Composable
fun ToolTile(
    tool: Tool,
    source: String,
    onOpen: OpenTool,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .pressScale(pressed)
            .sharedBoundsKey("tool-$source-${tool.id}")
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = { haptics.click(); onOpen(tool, source) },
                onLongClick = { haptics.longPress(); onLongPress() },
                hapticFeedbackEnabled = false,
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(tool, Modifier.sharedElementKey("icon-$source-${tool.id}"), size = if (compact) 48.dp else 58.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            tool.name.text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryCard(category: Category, count: Int, open: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val rot by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "chev")
    val badgeRot by animateFloatAsState(if (open) 45f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), label = "badge")
    Surface(
        onClick = { haptics.click(); onClick() },
        shape = RoundedCornerShape(if (open) 32.dp else 28.dp),
        color = if (open) category.container(c) else c.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(Modifier.heightIn(min = 92.dp).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category, category.icon, size = 40.dp, rotation = badgeRot)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    category.title.text,
                    style = MaterialTheme.typography.titleSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (open) category.onContainer(c) else c.onSurface,
                )
                Text(
                    if (count > 1) tr("$count outils", "$count tools") else tr("$count outil", "$count tool"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (open) category.onContainer(c).copy(alpha = 0.75f) else c.onSurfaceVariant,
                )
            }
            if (open) Icon(Icons.Rounded.ExpandMore, null, Modifier.graphicsLayer { rotationZ = rot }, tint = category.onContainer(c))
        }
    }
}

@Composable
private fun RecentsRow(tools: List<Tool>, onOpen: OpenTool) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(tools, key = { it.id }) { tool ->
            ToolTile(tool, "rec", onOpen, onLongPress = {}, modifier = Modifier.width(84.dp).animateItem(), compact = true)
        }
    }
}

/**
 * Favorites row, reorderable by drag & drop: long-press a tile, then move it.
 * A long-press without movement opens the tool menu instead.
 */
@Composable
private fun FavoritesRow(tools: List<Tool>, onOpen: OpenTool, onMenu: (Tool) -> Unit) {
    val haptics = rememberHaptics()
    var items by remember(tools) { mutableStateOf(tools) }
    val listState = rememberLazyListState()
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items, key = { it.id }) { tool ->
            val isDragged = dragging == tool.id
            val lift by animateFloatAsState(if (isDragged) 1.1f else 1f, spring(dampingRatio = 0.5f), label = "lift")
            Box(
                Modifier
                    .width(92.dp)
                    .zIndex(if (isDragged) 1f else 0f)
                    .then(if (isDragged) Modifier else Modifier.animateItem())
                    .graphicsLayer {
                        translationX = if (isDragged) dragOffset else 0f
                        scaleX = lift; scaleY = lift
                    }
                    .pointerInput(tool.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            dragging = tool.id
                            dragOffset = 0f
                            haptics.longPress()
                            var moved = false
                            drag(long.id) { change ->
                                val dx = change.positionChange().x
                                dragOffset += dx
                                if (!moved && abs(dragOffset) > 18f) moved = true
                                change.consume()
                                val info = listState.layoutInfo.visibleItemsInfo
                                val current = info.firstOrNull { it.key == tool.id } ?: return@drag
                                val center = current.offset + dragOffset + current.size / 2f
                                val target = info.firstOrNull { it.key != tool.id && center > it.offset && center < it.offset + it.size }
                                if (target != null) {
                                    val from = items.indexOfFirst { it.id == tool.id }
                                    val to = items.indexOfFirst { it.id == target.key }
                                    if (from >= 0 && to >= 0) {
                                        items = items.toMutableList().apply { add(to, removeAt(from)) }
                                        dragOffset += current.offset - target.offset
                                        haptics.segment()
                                    }
                                }
                            }
                            if (moved) {
                                ToolRepository.reorderFavorites(items.map { it.id })
                            } else {
                                onMenu(tool)
                            }
                            scope.launch { dragging = null; dragOffset = 0f }
                        }
                    },
            ) {
                ToolTile(tool, "fav", onOpen, onLongPress = {})
            }
        }
    }
}

@Composable
private fun ToolMenu(tool: Tool, isFavorite: Boolean, onDismiss: () -> Unit, context: Context) {
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolIcon(tool, size = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(tool.name.text, style = MaterialTheme.typography.titleLarge)
                    Text(tool.category.title.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent = { Text(if (isFavorite) tr("Retirer des favoris", "Remove from favorites") else tr("Ajouter aux favoris", "Add to favorites")) },
                leadingContent = { Icon(if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, tint = MaterialTheme.colorScheme.tertiary) },
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { haptics.confirm(); ToolRepository.toggleFavorite(tool.id); onDismiss() },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            if (Shortcuts.canPin(context)) {
                ListItem(
                    headlineContent = { Text(tr("Épingler sur l'écran d'accueil", "Pin to home screen")) },
                    leadingContent = { Icon(Icons.Rounded.AddToHomeScreen, null) },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { Shortcuts.requestPin(context, tool); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

