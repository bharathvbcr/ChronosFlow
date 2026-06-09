package com.chronosflow.feature.daydial

import com.chronosflow.core.domain.model.SleepSchedule

/**
 * Shared preference keys for Day Dial reminder toggles.
 * Must stay aligned with [rememberDayDialSettingsState].
 */
object DayDialReminderSettingsKeys {
    const val BLOCK_START_REMINDERS = "block_start_reminders"
    const val BREAK_REMINDERS = "break_reminders"
    const val MISSED_ALERTS = "missed_alerts"
    const val END_DAY_REVIEW_REMINDER = "end_day_review_reminder"

    const val DEFAULT_BLOCK_START_REMINDERS = true
    const val DEFAULT_BREAK_REMINDERS = false
    const val DEFAULT_MISSED_ALERTS = true
    const val DEFAULT_END_DAY_REVIEW_REMINDER = false
}

data class DayDialReminderSettings(
    val blockStartReminders: Boolean,
    val breakReminders: Boolean,
    val missedAlerts: Boolean,
    val endDayReviewReminder: Boolean,
    val sleepScheduleEnabled: Boolean,
    val sleepScheduleStartMinute: Int,
    val sleepScheduleEndMinute: Int
) {
    companion object {
        val DEFAULT = DayDialReminderSettings(
            blockStartReminders = DayDialReminderSettingsKeys.DEFAULT_BLOCK_START_REMINDERS,
            breakReminders = DayDialReminderSettingsKeys.DEFAULT_BREAK_REMINDERS,
            missedAlerts = DayDialReminderSettingsKeys.DEFAULT_MISSED_ALERTS,
            endDayReviewReminder = DayDialReminderSettingsKeys.DEFAULT_END_DAY_REVIEW_REMINDER,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = SleepSchedule.DEFAULT_START_MINUTE,
            sleepScheduleEndMinute = SleepSchedule.DEFAULT_END_MINUTE
        )
    }
}
