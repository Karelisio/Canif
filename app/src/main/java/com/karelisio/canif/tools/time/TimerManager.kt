package com.karelisio.canif.tools.time

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.karelisio.canif.CanifApp
import com.karelisio.canif.R
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.data.TimerEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.DateFormat
import java.util.Date

/**
 * Single source of truth for countdown timers. The Room row is the state; every change
 * re-schedules (or cancels) the timer's alarm and refreshes the "running" notification.
 *
 * States: running (endAt = wall-clock end), paused (remainingMs > 0), finished (remainingMs == 0).
 * Alarms use the wall clock (RTC_WAKEUP), so they stay valid across process death and reboots
 * (re-scheduled by [TimeBootReceiver]).
 */
object TimerManager {
    private val mutex = Mutex()
    private val app get() = CanifApp.instance
    private val dao get() = app.db.timers()
    const val MINUTE = 60_000L

    /** Fire-and-forget from the UI or a receiver. */
    fun launch(block: suspend TimerManager.() -> Unit): Job = app.appScope.launch { block() }

    private fun now() = System.currentTimeMillis()

    suspend fun create(name: String, durationMs: Long, start: Boolean = true): Long = mutex.withLock {
        val d = durationMs.coerceIn(1000L, 100L * 3_600_000L)
        val t = TimerEntity(
            name = name,
            durationMs = d,
            remainingMs = d,
            endAt = if (start) now() + d else 0,
            running = start,
        )
        val id = dao.insert(t)
        if (start) schedule(t.copy(id = id))
        refreshOngoing()
        id
    }

    suspend fun start(id: Long) = mutex.withLock {
        val t = dao.get(id) ?: return@withLock
        if (t.running) return@withLock
        val rest = if (t.remainingMs <= 0) t.durationMs else t.remainingMs
        val n = t.copy(running = true, remainingMs = rest, endAt = now() + rest)
        dao.upsert(n)
        schedule(n)
        RingService.dismiss(app, id)
        refreshOngoing()
    }

    suspend fun pause(id: Long) = mutex.withLock {
        val t = dao.get(id) ?: return@withLock
        if (!t.running) return@withLock
        val rest = (t.endAt - now()).coerceAtLeast(1000L)
        dao.upsert(t.copy(running = false, remainingMs = rest, endAt = 0))
        cancelAlarm(id)
        refreshOngoing()
    }

    suspend fun toggle(id: Long) {
        val t = dao.get(id) ?: return
        if (t.running) pause(id) else start(id)
    }

    suspend fun addMinute(id: Long) = mutex.withLock {
        val t = dao.get(id) ?: return@withLock
        val n = when {
            t.running -> t.copy(endAt = t.endAt + MINUTE)
            t.remainingMs <= 0 -> t.copy(running = true, remainingMs = MINUTE, endAt = now() + MINUTE)
            else -> t.copy(remainingMs = t.remainingMs + MINUTE)
        }
        dao.upsert(n)
        if (n.running) schedule(n)
        RingService.dismiss(app, id)
        refreshOngoing()
    }

    suspend fun reset(id: Long) = mutex.withLock {
        val t = dao.get(id) ?: return@withLock
        dao.upsert(t.copy(running = false, remainingMs = t.durationMs, endAt = 0))
        cancelAlarm(id)
        RingService.dismiss(app, id)
        refreshOngoing()
    }

    suspend fun delete(id: Long) = mutex.withLock {
        cancelAlarm(id)
        dao.delete(id)
        RingService.dismiss(app, id)
        refreshOngoing()
    }

    suspend fun rename(id: Long, name: String) = mutex.withLock {
        val t = dao.get(id) ?: return@withLock
        dao.upsert(t.copy(name = name))
        refreshOngoing()
    }

    /** Ringing stopped by the user: the timer goes back to its full duration, ready to restart. */
    suspend fun stopRinging(id: Long) {
        val t = dao.get(id)
        if (t != null && !t.running && t.remainingMs <= 0) reset(id) else RingService.dismiss(app, id)
    }

    /**
     * Called when the alarm goes off. Returns the timer if it really is due (and marks it finished),
     * or null for a stale alarm (paused, deleted, extended…), which is then re-scheduled if needed.
     */
    suspend fun fired(id: Long): TimerEntity? = mutex.withLock {
        val t = dao.get(id) ?: return@withLock null
        if (!t.running) return@withLock null
        if (t.endAt - now() > 1500) {
            schedule(t) // Fired early (inexact fallback, clock change…): try again at the right time.
            return@withLock null
        }
        val done = t.copy(running = false, remainingMs = 0)
        dao.upsert(done)
        refreshOngoing()
        done
    }

    /** After a reboot, an app update or an exact-alarm permission change. Overdue timers fire right away. */
    suspend fun rescheduleAll() = mutex.withLock {
        val n = now()
        for (t in dao.all()) {
            if (!t.running) continue
            if (t.endAt <= n) schedule(t, at = n + 1500) else schedule(t)
        }
        refreshOngoing()
    }

    // ------------------------------------------------------------------ alarms

    private fun firePending(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, (id % Int.MAX_VALUE).toInt(),
            Intent(context, TimerReceiver::class.java)
                .setAction(TimerReceiver.ACTION_FIRE)
                .setData(Uri.parse("canif-timer://fire/$id"))
                .putExtra(TimerReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun schedule(t: TimerEntity, at: Long = t.endAt) {
        val context = app
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePending(context, t.id)
        try {
            if (canExact(context)) {
                // setAlarmClock is the most reliable path (honored in Doze and by aggressive OEMs like MagicOS).
                val show = TimeNotifications.openTool(context, "timers", 40)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(id: Long) {
        app.getSystemService(AlarmManager::class.java).cancel(firePending(app, id))
    }

    // ------------------------------------------------------------------ ongoing notification

    /** Countdown notification (system chronometer, no wake-ups) while at least one timer runs. */
    private suspend fun refreshOngoing() {
        val context = app
        val running = dao.all().filter { it.running }.sortedBy { it.endAt }
        if (running.isEmpty()) {
            TimeNotifications.cancel(context, TimeNotifications.ID_ONGOING)
            return
        }
        val first = running.first()
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        val b = NotificationCompat.Builder(context, TimeNotifications.CH_ONGOING)
            .setSmallIcon(R.drawable.time_ic_notif_timer)
            .setContentTitle(first.name)
            .setContentText(
                if (running.size > 1) tr("+ ${running.size - 1} autre(s) minuteur(s)", "+ ${running.size - 1} more timer(s)")
                else tr("Sonne à ${fmt.format(Date(first.endAt))}", "Rings at ${fmt.format(Date(first.endAt))}"),
            )
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(first.endAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(TimeNotifications.openTool(context, "timers", 42))
            .addAction(0, tr("Pause", "Pause"), TimeNotifications.action(context, TimerReceiver.ACTION_PAUSE, first.id))
            .addAction(0, tr("+1 min", "+1 min"), TimeNotifications.action(context, TimerReceiver.ACTION_ADD_MINUTE, first.id))
        if (running.size > 1) {
            val style = NotificationCompat.InboxStyle()
            running.take(6).forEach { style.addLine("${it.name} — ${fmt.format(Date(it.endAt))}") }
            b.setStyle(style)
        }
        TimeNotifications.notify(context, TimeNotifications.ID_ONGOING, b.build())
    }
}
