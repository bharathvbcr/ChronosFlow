package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType

object ReminderNotificationChannels {
    const val DEFAULT_CHANNEL_ID = "chronos_reminders"
    const val CRITICAL_CHANNEL_ID = "chronos_reminders_critical"
    // v2 of the current-block channel. The original "chronos_current_block" was IMPORTANCE_LOW
    // (silent) and Android won't let an existing channel's importance be raised, so the unified
    // "now" live notification — which alerts once when a block begins, then persists silently —
    // lives on a fresh DEFAULT-importance id. The old silent channel is deleted in ensureCreated.
    const val CURRENT_BLOCK_CHANNEL_ID = "chronos_now_live"
    private const val LEGACY_CURRENT_BLOCK_CHANNEL_ID = "chronos_current_block"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        NotificationChannelGroups.ensureCreated(manager)
        val defaultChannel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Planner nudges, block starts, and daily review reminders"
            group = NotificationChannelGroups.REMINDERS
        }
        val criticalChannel = NotificationChannel(
            CRITICAL_CHANNEL_ID,
            "Critical reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Medication doses and urgent task deadlines"
            group = NotificationChannelGroups.REMINDERS
        }
        // The single live "now" notification: alerts once when a block begins (DEFAULT importance
        // makes a sound), then re-posts silently via setOnlyAlertOnce for the rest of the block.
        val currentBlockChannel = NotificationChannel(
            CURRENT_BLOCK_CHANNEL_ID,
            context.getString(R.string.current_block_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.current_block_channel_description)
            group = NotificationChannelGroups.FOCUS
        }
        manager.createNotificationChannel(defaultChannel)
        manager.createNotificationChannel(criticalChannel)
        manager.createNotificationChannel(currentBlockChannel)
        // Remove the retired silent current-block channel so it stops cluttering settings.
        manager.deleteNotificationChannel(LEGACY_CURRENT_BLOCK_CHANNEL_ID)
    }

    fun channelIdFor(
        receiverClass: Class<*>,
        requestType: AlarmRequestType?
    ): String {
        if (receiverClass == MedicationAlarmReceiver::class.java) return CRITICAL_CHANNEL_ID
        return when (requestType) {
            AlarmRequestType.MEDICATION,
            AlarmRequestType.URGENT_TASK -> CRITICAL_CHANNEL_ID
            else -> DEFAULT_CHANNEL_ID
        }
    }

    fun compatPriorityFor(channelId: String): Int {
        return when (channelId) {
            CRITICAL_CHANNEL_ID -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }
}
