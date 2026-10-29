package com.karelisio.canif.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.tools.SharedPayload
import com.karelisio.canif.ui.components.LocalNavScope
import com.karelisio.canif.ui.onboarding.OnboardingScreen
import com.karelisio.canif.ui.settings.PermissionsScreen
import com.karelisio.canif.ui.share.ShareTargetScreen
import com.karelisio.canif.update.UpdateSheetHost
import com.karelisio.canif.update.Updater
import kotlinx.coroutines.launch

@Composable
fun CanifRoot(nav: NavHostController, start: String, sharedPayload: () -> SharedPayload?, showIntro: Boolean) {
    LaunchedEffect(Unit) {
        if (sharedPayload() != null && start == Routes.HOME) nav.navigate(Routes.SHARE)
        Updater.checkIfDue()
    }
    UpdateSheetHost()
    CanifNavHost(nav, start, sharedPayload, showIntro) {
        composable(Routes.ONBOARDING) {
            CompositionLocalProvider(LocalNavScope provides this) {
                OnboardingScreen(onDone = {
                    CanifApp.instance.appScope.launch { CanifApp.instance.settings.setOnboardingDone(true) }
                    nav.navigate(if (sharedPayload() != null) Routes.SHARE else Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
        }
        composable(Routes.PERMISSIONS) {
            CompositionLocalProvider(LocalNavScope provides this) {
                PermissionsScreen(onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.SHARE) {
            CompositionLocalProvider(LocalNavScope provides this) {
                ShareTargetScreen(
                    payload = sharedPayload(),
                    onOpen = { id -> nav.navigate(Routes.tool(id)) { popUpTo(Routes.SHARE) { inclusive = true } } },
                    onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                )
            }
        }
    }
}
