package com.karelisio.canif.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.karelisio.canif.core.tools.SharedPayload
import com.karelisio.canif.core.tools.ToolRegistry
import com.karelisio.canif.ui.components.CascadeState
import com.karelisio.canif.ui.components.LocalCascade
import com.karelisio.canif.ui.components.LocalNavScope
import com.karelisio.canif.ui.components.LocalSharedScope
import com.karelisio.canif.ui.home.HomeScreen
import com.karelisio.canif.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val TOOL = "tool/{id}?src={src}"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val ONBOARDING = "onboarding"
    const val SHARE = "share"
    fun tool(id: String, src: String = "ext") = "tool/$id?src=$src"
}

private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(260, delayMillis = 40)) + scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.94f)
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(200)) + scaleOut(tween(320), targetScale = 1.03f)
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 1.03f)
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    // Predictive back: the screen shrinks and fades as the gesture progresses.
    fadeOut(tween(260)) + scaleOut(tween(320), targetScale = 0.9f)
}

@Composable
fun CanifNavHost(
    nav: NavHostController,
    startDestination: String,
    sharedPayload: () -> SharedPayload?,
    showIntroLogo: Boolean,
    extraDestinations: androidx.navigation.NavGraphBuilder.() -> Unit = {},
) {
    val homeCascade = remember { CascadeState() }
    SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = startDestination,
                enterTransition = enter,
                exitTransition = exit,
                popEnterTransition = popEnter,
                popExitTransition = popExit,
            ) {
                composable(Routes.HOME) {
                    CompositionLocalProvider(LocalNavScope provides this, LocalCascade provides homeCascade) {
                        HomeScreen(
                            onOpen = { tool, src -> nav.navigate(Routes.tool(tool.id, src)) { launchSingleTop = true } },
                            onSettings = { nav.navigate(Routes.SETTINGS) },
                            showIntroLogo = showIntroLogo,
                        )
                    }
                }
                composable(
                    Routes.TOOL,
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("src") { type = NavType.StringType; defaultValue = "ext" },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = "canif://tool/{id}" }),
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val src = entry.arguments?.getString("src") ?: "ext"
                    val tool = ToolRegistry.get(id)
                    CompositionLocalProvider(LocalNavScope provides this, LocalCascade provides remember { CascadeState() }) {
                        if (tool == null) {
                            Box(Modifier.fillMaxSize())
                        } else {
                            ToolHost(
                                tool = tool,
                                source = src,
                                incoming = remember(id) { sharedPayload() },
                                onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                                navigate = { nav.navigate(Routes.tool(it)) },
                            )
                        }
                    }
                }
                composable(Routes.SETTINGS) {
                    CompositionLocalProvider(LocalNavScope provides this) {
                        SettingsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) })
                    }
                }
                extraDestinations()
            }
        }
    }
}
