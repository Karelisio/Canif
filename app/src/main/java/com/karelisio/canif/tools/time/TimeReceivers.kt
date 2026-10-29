package com.karelisio.canif.tools.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.canif.CanifApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Alarm deliveries and notification actions (not exported). */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val pending = goAsync()
        CanifApp.instance.appScope.launch {
            try {
                withTimeoutOrNull(8_000) { handle(app, intent.action, id) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String?, id: Long) {
        when (action) {
            ACTION_FIRE -> {
                val t = TimerManager.fired(id) ?: return
                if (!RingService.ring(context, t.id, t.name)) {
                    // The foreground service could not start (background restrictions):
                    // ring through an insistent full-screen notification instead.
                    TimeNotifications.notify(context, TimeNotifications.backupId(t.id), TimeNotifications.backupRing(context, t.id, t.name))
                }
            }
            ACTION_STOP -> {
                val ids = if (id >= 0) listOf(id) else RingService.ringing.value.map { it.id }
                ids.forEach { TimerManager.stopRinging(it) }
            }
            ACTION_ADD_MINUTE -> {
                val ids = if (id >= 0) listOf(id) else RingService.ringing.value.map { it.id }
                ids.forEach { TimerManager.addMinute(it) }
            }
            ACTION_PAUSE -> if (id >= 0) TimerManager.pause(id)
            ACTION_HIIT_TOGGLE -> HiitEngine.togglePause()
            ACTION_HIIT_STOP -> HiitEngine.stop()
        }
    }

    companion object {
        const val EXTRA_ID = "timer_id"
        const val ACTION_FIRE = "com.karelisio.canif.timer.FIRE"
        const val ACTION_STOP = "com.karelisio.canif.timer.STOP"
        const val ACTION_ADD_MINUTE = "com.karelisio.canif.timer.ADD_MINUTE"
        const val ACTION_PAUSE = "com.karelisio.canif.timer.PAUSE"
        const val ACTION_HIIT_TOGGLE = "com.karelisio.canif.hiit.TOGGLE"
        const val ACTION_HIIT_STOP = "com.karelisio.canif.hiit.STOP"
    }
}

/** Re-schedules timer alarms after a reboot, an app update or an exact-alarm permission change. */
class TimeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            Intent.ACTION_TIME_CHANGED,
            -> Unit
            else -> return
        }
        val pending = goAsync()
        CanifApp.instance.appScope.launch {
            try {
                withTimeoutOrNull(8_000) { TimerManager.rescheduleAll() }
            } finally {
                pending.finish()
            }
        }
    }
}
