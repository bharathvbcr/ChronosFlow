package com.chronosflow.core.notifications

import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.os.Build

/**
 * Groups the app's notification channels under named sections so the system
 * "App info → Notifications" screen reads as "Focus" and "Reminders & alerts"
 * rather than a flat list of channels. Create groups before assigning channels to them.
 */
internal object NotificationChannelGroups {
    const val FOCUS = "chronos_group_focus"
    const val REMINDERS = "chronos_group_reminders"

    fun ensureCreated(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(FOCUS, "Focus"),
                NotificationChannelGroup(REMINDERS, "Reminders & alerts")
            )
        )
    }
}
