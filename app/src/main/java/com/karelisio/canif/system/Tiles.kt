package com.karelisio.canif.system

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.karelisio.canif.R
import com.karelisio.canif.core.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick settings tile that opens a tool. */
abstract class OpenToolTile(private val toolId: String, private val iconRes: Int, private val label: () -> String) : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            this.icon = Icon.createWithResource(this@OpenToolTile, iconRes)
            this.label = label()
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Shortcuts.toolIntent(this, toolId)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, toolId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

class LevelTile : OpenToolTile("level", R.drawable.tile_level, { tr("Niveau", "Level") })
class TunerTile : OpenToolTile("tuner", R.drawable.tile_tuner, { tr("Accordeur", "Tuner") })
class MetronomeTile : OpenToolTile("metronome", R.drawable.tile_metronome, { tr("Métronome", "Metronome") })

/** Toggleable flashlight tile. */
class LampTile : TileService() {
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun onStartListening() {
        TorchControl.observe(applicationContext)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        job = scope?.launch { TorchControl.isOn.collect { render(it) } }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        TorchControl.toggle(applicationContext)
        render(TorchControl.isOn.value)
        Widgets.refreshLamp(applicationContext)
    }

    private fun render(on: Boolean) {
        val available = TorchControl.cameraId(this) != null
        qsTile?.apply {
            icon = Icon.createWithResource(this@LampTile, R.drawable.tile_flashlight)
            label = tr("Lampe", "Flashlight")
            state = when {
                !available -> Tile.STATE_UNAVAILABLE
                on -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (on) tr("Allumée", "On") else tr("Éteinte", "Off")
            updateTile()
        }
    }
}
