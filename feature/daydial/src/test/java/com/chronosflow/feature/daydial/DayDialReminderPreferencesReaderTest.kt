package com.chronosflow.feature.daydial

import android.content.Context
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.ui.settings.ChronosUiSettingsKeys
import com.chronosflow.core.ui.settings.clearChronosUiSettingsStore
import com.chronosflow.core.ui.settings.writeChronosUiBooleanSetting
import com.chronosflow.core.ui.settings.writeChronosUiIntSetting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DayDialReminderPreferencesReaderTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        runTest {
            context.clearChronosUiSettingsStore()
        }
        context.getSharedPreferences(ChronosUiSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(DayDialReminderSettingsKeys.BLOCK_START_REMINDERS, false)
            .putBoolean(DayDialReminderSettingsKeys.BREAK_REMINDERS, true)
            .putBoolean(DayDialReminderSettingsKeys.MISSED_ALERTS, false)
            .putBoolean(DayDialReminderSettingsKeys.END_DAY_REVIEW_REMINDER, true)
            .putBoolean(SleepSchedule.KEY_ENABLED, true)
            .putInt(SleepSchedule.KEY_START_MINUTE, 22 * 60)
            .putInt(SleepSchedule.KEY_END_MINUTE, 6 * 60)
            .apply()
    }

    @Test
    fun `reads reminder toggles from day dial ui preferences`() {
        val settings = DayDialReminderPreferencesReader(context).read()

        assertFalse(settings.blockStartReminders)
        assertTrue(settings.breakReminders)
        assertFalse(settings.missedAlerts)
        assertTrue(settings.endDayReviewReminder)
        assertTrue(settings.sleepScheduleEnabled)
        assertEquals(22 * 60, settings.sleepScheduleStartMinute)
        assertEquals(6 * 60, settings.sleepScheduleEndMinute)
    }

    @Test
    fun `reads reminder settings persisted in DataStore`() = runTest {
        context.writeChronosUiBooleanSetting(DayDialReminderSettingsKeys.BLOCK_START_REMINDERS, false)
        context.writeChronosUiBooleanSetting(DayDialReminderSettingsKeys.BREAK_REMINDERS, false)
        context.writeChronosUiBooleanSetting(DayDialReminderSettingsKeys.MISSED_ALERTS, true)
        context.writeChronosUiBooleanSetting(DayDialReminderSettingsKeys.END_DAY_REVIEW_REMINDER, true)
        context.writeChronosUiBooleanSetting(SleepSchedule.KEY_ENABLED, true)
        context.writeChronosUiIntSetting(SleepSchedule.KEY_START_MINUTE, 21 * 60)
        context.writeChronosUiIntSetting(SleepSchedule.KEY_END_MINUTE, 6 * 60)

        val settings = DayDialReminderPreferencesReader(context).read()

        assertFalse(settings.blockStartReminders)
        assertFalse(settings.breakReminders)
        assertTrue(settings.missedAlerts)
        assertTrue(settings.endDayReviewReminder)
        assertTrue(settings.sleepScheduleEnabled)
        assertEquals(21 * 60, settings.sleepScheduleStartMinute)
        assertEquals(6 * 60, settings.sleepScheduleEndMinute)
    }
}
