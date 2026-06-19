package com.ChronosFlow.VBCR.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by the delete intent of a grouped reminder when the user swipes it away, so the
 * [ReminderNotificationGroups] summary is rebuilt or removed to match what remains in the shade.
 */
class ReminderGroupDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderNotificationGroups.refreshSummary(context.applicationContext)
    }
}
