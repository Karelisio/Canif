package com.karelisio.canif.tools.time

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karelisio.canif.R
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.system.Shortcuts

/** Notification channels and helpers of the Time tools. Channels are created on the fly. */
object TimeNotifications {
    /** Ringing timer, posted by [RingService] which plays the sound itself (channel is silent). */
    const val CH_RING = "timers_ring"
    /** Backup ringing notification with its own looping alarm sound, used if the service cannot start. */
    const val CH_BACKUP = "timers_backup"
    /** Running timers countdown. */
    const val CH_ONGOING = "timers_ongoing"
    /** Interval training session. */
    const val CH_HIIT = "hiit"

    const val ID_ONGOING = 4100
    const val ID_RING = 4200
    const val ID_HIIT = 4300
    const val ID_MISSED = 4400
    fun backupId(timerId: Long) = 5000 + (timerId % 100_000).toInt()

    val alarmAudio: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ring = NotificationChannel(CH_RING, tr("Minuteurs", "Timers"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = tr("Sonnerie des minuteurs terminés", "Alarm of finished timers")
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val backup = NotificationChannel(CH_BACKUP, tr("Minuteurs (secours)", "Timers (backup)"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = tr("Sonnerie de secours si le service ne peut pas démarrer", "Backup alarm when the service cannot start")
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val ongoing = NotificationChannel(CH_ONGOING, tr("Minuteurs en cours", "Running timers"), NotificationManager.IMPORTANCE_LOW).apply {
            description = tr("Compte à rebours des minuteurs actifs", "Countdown of active timers")
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val hiit = NotificationChannel(CH_HIIT, tr("Intervalles HIIT", "HIIT intervals"), NotificationManager.IMPORTANCE_LOW).apply {
            description = tr("Séance d'entraînement en cours", "Workout in progress")
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(ring, backup, ongoing, hiit))
    }

    fun openTool(context: Context, toolId: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, Shortcuts.toolIntent(context, toolId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun action(context: Context, action: String, timerId: Long = -1L): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action.hashCode() * 31 + timerId).toInt(),
            Intent(context, TimerReceiver::class.java).setAction(action).putExtra(TimerReceiver.EXTRA_ID, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun ringActivity(context: Context, ids: LongArray = LongArray(0), names: Array<String> = emptyArray()): PendingIntent =
        PendingIntent.getActivity(
            context, 77 + ids.sum().toInt(),
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(RingActivity.EXTRA_IDS, ids)
                .putExtra(RingActivity.EXTRA_NAMES, names),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @SuppressLint("MissingPermission")
    fun notify(context: Context, id: Int, n: Notification) {
        ensureChannels(context)
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /** The backup alarm: loud (looping channel sound), full-screen, with Stop / +1 min. */
    fun backupRing(context: Context, timerId: Long, name: String): Notification =
        NotificationCompat.Builder(context, CH_BACKUP)
            .setSmallIcon(R.drawable.time_ic_notif_timer)
            .setContentTitle(tr("Minuteur terminé", "Timer finished"))
            .setContentText(name)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(ringActivity(context, longArrayOf(timerId), arrayOf(name)), true)
            .setContentIntent(ringActivity(context, longArrayOf(timerId), arrayOf(name)))
            .addAction(0, tr("Arrêter", "Stop"), action(context, TimerReceiver.ACTION_STOP, timerId))
            .addAction(0, tr("+1 min", "+1 min"), action(context, TimerReceiver.ACTION_ADD_MINUTE, timerId))
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }

    fun missed(context: Context, names: String) {
        val n = NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.time_ic_notif_timer)
            .setContentTitle(tr("Minuteur terminé", "Timer finished"))
            .setContentText(names)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openTool(context, "timers", 41))
            .build()
        notify(context, ID_MISSED, n)
    }
}
