package com.ChronosFlow.VBCR.feature.daydial

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiBooleanSetting
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiIntSetting
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
            sleepJournalLogReminder = context.readChronosUiBooleanSetting(
                DayDialReminderSettingsKeys.SLEEP_JOURNAL_LOG_REMINDER,
                DayDialReminderSettingsKeys.DEFAULT_SLEEP_JOURNAL_LOG_REMINDER
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
            ),
            journalEnabled = context.readChronosUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_JOURNAL_ENABLED,
                true
            ),
            sleepEnabled = context.readChronosUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED,
                true
            )
        )
    }
}
