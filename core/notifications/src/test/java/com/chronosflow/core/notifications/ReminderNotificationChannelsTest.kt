package com.chronosflow.core.notifications

import androidx.core.app.NotificationCompat
import com.chronosflow.core.domain.model.AlarmRequestType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderNotificationChannelsTest {
    @Test
    fun `medication receiver uses critical channel`() {
        assertEquals(
            ReminderNotificationChannels.CRITICAL_CHANNEL_ID,
            ReminderNotificationChannels.channelIdFor(
                receiverClass = MedicationAlarmReceiver::class.java,
                requestType = null
            )
        )
    }

    @Test
    fun `urgent task uses critical channel on generic receiver`() {
        assertEquals(
            ReminderNotificationChannels.CRITICAL_CHANNEL_ID,
            ReminderNotificationChannels.channelIdFor(
                receiverClass = AlarmReceiver::class.java,
                requestType = AlarmRequestType.URGENT_TASK
            )
        )
    }

    @Test
    fun `planner nudges use default channel`() {
        assertEquals(
            ReminderNotificationChannels.DEFAULT_CHANNEL_ID,
            ReminderNotificationChannels.channelIdFor(
                receiverClass = AlarmReceiver::class.java,
                requestType = AlarmRequestType.BLOCK_START
            )
        )
    }

    @Test
    fun `compat priority matches channel importance tier`() {
        assertEquals(
            NotificationCompat.PRIORITY_HIGH,
            ReminderNotificationChannels.compatPriorityFor(ReminderNotificationChannels.CRITICAL_CHANNEL_ID)
        )
        assertEquals(
            NotificationCompat.PRIORITY_DEFAULT,
            ReminderNotificationChannels.compatPriorityFor(ReminderNotificationChannels.DEFAULT_CHANNEL_ID)
        )
    }
}
