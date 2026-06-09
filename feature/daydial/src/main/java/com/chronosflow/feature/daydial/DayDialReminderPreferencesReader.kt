package com.chronosflow.feature.daydial

import android.content.Context
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.ui.settings.readChronosUiBooleanSetting
import com.chronosflow.core.ui.settings.readChronosUiIntSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayDialReminderPreferencesReader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun read(): DayDialReminderSettings {
        return DayDialReminderSettings(
            blockStartReminders = context.readChronosUiBooleanSetting(
                DayDialReminderSettingsKeys.BLOCK_START_REMINDERS,
                DayDialReminderSettingsKeys.DEFAULT_BLOCK_START_REMINDERS
            ),
            breakReminders = context.readChronosUiBooleanSetting(
                DayDialReminderSettingsKeys.BREAK_REMINDERS,
                DayDialReminderSettingsKeys.DEFAULT_BREAK_REMINDERS
            ),
            missedAlerts = context.readChronosUiBooleanSetting(
                DayDialReminderSettingsKeys.MISSED_ALERTS,
                DayDialReminderSettingsKeys.DEFAULT_MISSED_ALERTS
            ),
            endDayReviewReminder = context.readChronosUiBooleanSetting(
                DayDialReminderSettingsKeys.END_DAY_REVIEW_REMINDER,
                DayDialReminderSettingsKeys.DEFAULT_END_DAY_REVIEW_REMINDER
            ),
            sleepScheduleEnabled = context.readChronosUiBooleanSetting(
                SleepSchedule.KEY_ENABLED,
                false
            ),
            sleepScheduleStartMinute = context.readChronosUiIntSetting(
                SleepSchedule.KEY_START_MINUTE,
                SleepSchedule.DEFAULT_START_MINUTE
            ),
            sleepScheduleEndMinute = context.readChronosUiIntSetting(
                SleepSchedule.KEY_END_MINUTE,
                SleepSchedule.DEFAULT_END_MINUTE
            )
        )
    }
}
