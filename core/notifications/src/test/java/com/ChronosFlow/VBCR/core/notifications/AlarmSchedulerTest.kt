package com.ChronosFlow.VBCR.core.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AlarmSchedulerTest {
    private lateinit var context: Context
    private lateinit var scheduler: AlarmScheduler
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", false)
            .apply()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        
        scheduler = AlarmScheduler(context)
    }

    @Test
    fun `scheduleExactAlarm sets alarm and persists state`() {
        val id = "test-alarm-1"
        val time = Instant.now().plusSeconds(3600)
        val title = "Test Title"
        val message = "Test Message"

        val result = scheduler.scheduleExactAlarm(id, time, title, message)

        assertTrue(result is AlarmScheduleResult.Scheduled)
        assertTrue((result as AlarmScheduleResult.Scheduled).exact)
        
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertEquals(1, scheduledAlarms.size)
    }

    @Test
    fun `cancelAlarm removes alarm and clears state`() {
        val id = "to-cancel"
        val time = Instant.now().plusSeconds(3600)
        scheduler.scheduleExactAlarm(id, time, "T", "M")
        
        scheduler.cancelAlarm(id)
        assertEquals(0, scheduler.activeReminderCount())
    }

    @Test
    fun `scheduleAlarmRequest uses correct receiver class`() {
        val request = AlarmRequest(
            id = "med-1",
            type = AlarmRequestType.MEDICATION,
            scheduledFor = Instant.now().plusSeconds(3600),
            title = "Meds",
            message = "Take meds",
            medicationPlanId = "plan-1",
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        scheduler.scheduleAlarmRequest(request)

        val alarm = shadowAlarmManager.scheduledAlarms[0]
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(MedicationAlarmReceiver::class.java.name, intent.component?.className)
    }

    @Test
    fun `scheduleAlarmRequest skips separate reminders when fold mode is active`() {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", true)
            .apply()

        val request = AlarmRequest(
            id = "med-fold",
            type = AlarmRequestType.MEDICATION,
            scheduledFor = Instant.now().plusSeconds(3600),
            title = "Meds",
            message = "Take meds",
            medicationPlanId = "plan-1",
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val result = scheduler.scheduleAlarmRequest(request)

        assertTrue(result is AlarmScheduleResult.Skipped)
        assertEquals(FOLDED_REMINDER_SKIP_REASON, (result as AlarmScheduleResult.Skipped).reason)
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `scheduleInexactAlarm skips habit reminders when fold mode is active`() {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", true)
            .apply()

        val result = scheduler.scheduleInexactAlarm(
            "daydial:2026-07-03:habit-h1:start",
            Instant.now().plusSeconds(3600),
            "Habit",
            "Starts now"
        )

        assertTrue(result is AlarmScheduleResult.Skipped)
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }
}
