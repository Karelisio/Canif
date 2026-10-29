package com.karelisio.canif.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.ui.components.AnimatedCheck
import com.karelisio.canif.ui.components.AnimatedKnifeLogo
import com.karelisio.canif.ui.components.BlobIcon
import com.karelisio.canif.ui.components.CategoryBadge
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.permissions.rememberPermissionRequester
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

private sealed interface Page {
    data object Welcome : Page
    data object Tools : Page
    data class Permission(val p: AppPermission) : Page
    data object Ready : Page
}

/** First-launch introduction: presents Canif, then asks every permission, one per page. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = remember {
        buildList {
            add(Page.Welcome)
            add(Page.Tools)
            AppPermission.entries.filter { it.applicable }.forEach { add(Page.Permission(it)) }
            add(Page.Ready)
        }
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val next: () -> Unit = { scope.launch { if (pager.currentPage < pages.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) else onDone() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(tr("Passer", "Skip")) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Parallax + depth while swiping.
                        val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                        alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.6f
                        val s = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.12f
                        scaleX = s; scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (val page = pages[index]) {
                    Page.Welcome -> WelcomePage(next)
                    Page.Tools -> ToolsPage(next)
                    is Page.Permission -> PermissionPage(page.p, next)
                    Page.Ready -> ReadyPage(onDone)
                }
            }
        }
        PagerDots(pager, Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
    }
}

@Composable
private fun PagerDots(pager: PagerState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pager.pageCount) { i ->
            val selected = pager.currentPage == i
            val w by animateFloatAsState(if (selected) 24f else 8f, spring(dampingRatio = 0.6f), label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(w.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun PageLayout(
    illustration: @Composable () -> Unit,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(260.dp), contentAlignment = Alignment.Center) { illustration() }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        actions()
    }
}

@Composable
private fun WelcomePage(next: () -> Unit) = PageLayout(
    illustration = { AnimatedKnifeLogo(size = 240.dp, startDelayMs = 250, staggerMs = 160) },
    title = tr("Bienvenue dans Canif", "Welcome to Canif"),
    body = tr(
        "Des dizaines d'outils pratiques dans une seule appli. Hors ligne, sans compte, sans pub, sans tracking.",
        "Dozens of handy tools in a single app. Offline, no account, no ads, no tracking.",
    ),
    actions = { Button(onClick = next) { Text(tr("Découvrir", "Discover")) } },
)

/** Category badges orbiting around the center. */
@Composable
private fun ToolsPage(next: () -> Unit) = PageLayout(
    illustration = {
        val reduce = LocalReduceMotion.current
        val t = rememberInfiniteTransition(label = "orbit")
        val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "a")
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            val cats = Category.entries
            cats.forEachIndexed { i, c ->
                val a = Math.toRadians((if (reduce) 0f else angle) + i * 360.0 / cats.size)
                val r = 105f
                CategoryBadge(
                    c, c.icon,
                    Modifier.offset { IntOffset((cos(a) * r * density).toInt(), (sin(a) * r * density).toInt()) },
                    size = 48.dp,
                    rotation = if (reduce) 0f else -angle * 2,
                )
            }
            Text("${cats.size}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
    },
    title = tr("Tout est à portée de main", "Everything at hand"),
    body = tr(
        "Calculs, instruments, audio, texte, images, jeux, QR codes, couleurs, temps et urgence. Mettez vos outils préférés en favoris.",
        "Math, instruments, audio, text, images, games, QR codes, colors, time and emergency. Star your favorite tools.",
    ),
    actions = { Button(onClick = next) { Text(tr("Continuer", "Continue")) } },
)

@Composable
private fun PermissionPage(permission: AppPermission, next: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    @Suppress("UNUSED_EXPRESSION") tick
    val granted = permission.isGranted(context)
    val request = rememberPermissionRequester(permission) { ok -> tick++; if (ok) haptics.confirm() }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(granted) {
        if (granted) {
            pop.snapTo(0.85f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
        }
    }
    PageLayout(
        illustration = {
            Box(contentAlignment = Alignment.BottomEnd) {
                BlobIcon(
                    permission.icon,
                    Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                    size = 200.dp,
                    color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    tint = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )
                AnimatedCheck(granted, Modifier.padding(8.dp), size = 56.dp)
            }
        },
        title = permission.title.text,
        body = permission.rationale.text,
        actions = {
            AnimatedContent(granted, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "perm") { ok ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ok) {
                        Button(onClick = next) { Text(tr("Continuer", "Continue")) }
                    } else {
                        Button(onClick = request) { Text(tr("Autoriser", "Allow")) }
                        TextButton(onClick = next) { Text(tr("Plus tard", "Later")) }
                    }
                }
            }
        },
    )
}

@Composable
private fun ReadyPage(onDone: () -> Unit) = PageLayout(
    illustration = { AnimatedCheck(true, size = 160.dp) },
    title = tr("C'est prêt !", "All set!"),
    body = tr(
        "Vous pourrez revoir les autorisations à tout moment dans les réglages.",
        "You can review permissions anytime in the settings.",
    ),
    actions = { Button(onClick = onDone) { Text(tr("Commencer", "Get started")) } },
)
