package com.karelisio.canif

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.karelisio.canif.core.tools.SharedPayload
import com.karelisio.canif.ui.components.KnifeIntro
import com.karelisio.canif.ui.nav.CanifRoot
import com.karelisio.canif.ui.nav.Routes
import com.karelisio.canif.ui.theme.CanifTheme

class MainActivity : ComponentActivity() {
    private var navController: NavHostController? = null
    private var sharedPayload by mutableStateOf<SharedPayload?>(null)
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !ready }
        // Hand over directly to the in-app unfolding animation.
        if (Build.VERSION.SDK_INT >= 31) splash.setOnExitAnimationListener { it.remove() }

        val coldLauncherStart = savedInstanceState == null && intent?.action == Intent.ACTION_MAIN
        sharedPayload = parseShare(intent)
        val app = application as CanifApp

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            ready = true
            var intro by rememberSaveable { mutableStateOf(coldLauncherStart) }
            CanifTheme(s) {
                val nav = rememberNavController().also { navController = it }
                val start = remember { if (s.onboardingDone) Routes.HOME else Routes.ONBOARDING }
                Box(Modifier.fillMaxSize()) {
                    CanifRoot(nav, start, { sharedPayload }, intro)
                    if (intro) KnifeIntro { intro = false }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseShare(intent)?.let { sharedPayload = it; navController?.let { nav -> routeShare(nav, it) } }
        if (intent.data?.scheme == "canif") navController?.handleDeepLink(intent)
    }

    private fun parseShare(intent: Intent?): SharedPayload? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                SharedPayload(
                    text = intent.getStringExtra(Intent.EXTRA_TEXT),
                    uris = listOfNotNull(uri),
                    mimeType = intent.type,
                )
            }
            Intent.ACTION_SEND_MULTIPLE -> SharedPayload(
                uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty(),
                mimeType = intent.type,
            )
            else -> null
        }
    }

    companion object {
        fun routeShare(nav: NavHostController, payload: SharedPayload) {
            nav.navigate(Routes.SHARE) { launchSingleTop = true }
        }
    }
}
