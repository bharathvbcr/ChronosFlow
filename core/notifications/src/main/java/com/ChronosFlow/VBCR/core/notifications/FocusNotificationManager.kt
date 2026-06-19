package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Static configuration for the live focus-session notification channel. The notification itself is
 * built and posted by [com.ChronosFlow.VBCR.feature.focus.FocusService] through
 * [FocusProgressNotificationRenderer]; this holds only the shared channel id, notification id, and
 * the channel-creation helper used at app/service startup.
 */
object FocusNotificationManager {
    const val FOCUS_CHANNEL_ID = "chronos_focus_timer"
    const val FOCUS_NOTIFICATION_ID = 4201

    fun createFocusNotificationChannel(
        context: Context,
        notificationManager: NotificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        NotificationChannelGroups.ensureCreated(notificationManager)
        val channel = NotificationChannel(
            FOCUS_CHANNEL_ID,
            context.getString(R.string.focus_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.focus_notification_channel_description)
            group = NotificationChannelGroups.FOCUS
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
