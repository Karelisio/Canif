package com.karelisio.canif

import android.app.Application
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.settings.SettingsRepository
import com.karelisio.canif.data.CanifDatabase
import com.karelisio.canif.data.ToolRepository
import com.karelisio.canif.system.Shortcuts
import com.karelisio.canif.system.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CanifApp : Application() {
    /** Process-wide scope for fire-and-forget writes (favorites, recents…). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: CanifDatabase by lazy { CanifDatabase.build(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appScope.launch { settings.settings.collect { Lang.preference = it.language } }
        ToolRepository.onFavoritesChanged = { ids ->
            Shortcuts.updateDynamic(this, ids)
            Widgets.refreshFavorites(this)
        }
    }

    companion object {
        lateinit var instance: CanifApp
            private set
    }
}
