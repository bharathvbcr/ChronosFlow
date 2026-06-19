package com.ChronosFlow.VBCR.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Bundles individual reminder notifications under a single collapsible group with an
 * auto-maintained [NotificationCompat.InboxStyle] summary, so several reminders collapse
 * into one stack instead of cluttering the shade.
 *
 * Each reminder is tagged with [GROUP_KEY]; the summary is (re)built or removed by
 * [refreshSummary], which callers invoke after posting or cancelling a reminder.
 */
internal object ReminderNotificationGroups {
    const val GROUP_KEY = "com.ChronosFlow.VBCR.core.notifications.REMINDERS"
    private const val SUMMARY_NOTIFICATION_ID = 4300
    private const val DELETE_REQUEST_CODE = 4301

    /** Delete intent that re-syncs the summary when a grouped reminder is swiped away. */
    fun deleteIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DELETE_REQUEST_CODE,
            Intent(context, ReminderGroupDeleteReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Header sub-text label that categorises a reminder at a glance. */
    fun categoryLabel(isMedication: Boolean, isHabit: Boolean, isTask: Boolean): String = when {
        isMedication -> "Medication"
        isHabit -> "Habit"
        isTask -> "Task"
        else -> "Reminder"
    }

    /** Glyph drawn inside the round category badge ([NotificationIcons.categoryBadge]). */
    @androidx.annotation.DrawableRes
    fun categoryIconRes(isMedication: Boolean, isHabit: Boolean, isTask: Boolean): Int = when {
        isMedication -> R.drawable.ic_notif_medication
        isHabit -> R.drawable.ic_notif_habit
        isTask -> R.drawable.ic_notif_task
        else -> R.drawable.ic_chronos_alarm
    }

    /**
     * Rebuilds the group summary from the reminders currently in the shade, or removes it when
     * fewer than two remain (a lone reminder reads better on its own).
     */
    fun refreshSummary(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ReminderNotificationChannels.ensureCreated(context)

        val children = manager.activeNotifications.filter { sbn ->
            sbn.id != SUMMARY_NOTIFICATION_ID && sbn.notification.group == GROUP_KEY
        }
        if (children.size < 2) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }

        val titles = children.mapNotNull { sbn ->
            sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        }
        val count = children.size
        val summaryText = "$count reminders"
        val inbox = NotificationCompat.InboxStyle().setSummaryText(summaryText)
        titles.forEach { inbox.addLine(it) }

        val summary = NotificationCompat.Builder(context, ReminderNotificationChannels.DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chronos_alarm)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle("ChronosFlow reminders")
            .setContentText(summaryText)
            .setStyle(inbox)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setDeleteIntent(deleteIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }
}
