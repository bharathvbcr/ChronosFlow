package com.chronosflow.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chronosflow.core.domain.model.AlarmRequestType

object ReminderNotificationChannels {
    const val DEFAULT_CHANNEL_ID = "chronos_reminders"
    const val CRITICAL_CHANNEL_ID = "chronos_reminders_critical"
    const val CURRENT_BLOCK_CHANNEL_ID = "chronos_current_block"

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
        val currentBlockChannel = NotificationChannel(
            CURRENT_BLOCK_CHANNEL_ID,
            "Current block",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent live progress for the time block happening now"
        }
        manager.createNotificationChannel(defaultChannel)
        manager.createNotificationChannel(criticalChannel)
        manager.createNotificationChannel(currentBlockChannel)
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
