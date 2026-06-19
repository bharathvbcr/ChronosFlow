package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule

/**
 * Shared preference keys for Day Dial reminder toggles.
 * Must stay aligned with [rememberDayDialSettingsState].
 */
object DayDialReminderSettingsKeys {
    const val BLOCK_START_REMINDERS = "block_start_reminders"
    const val BREAK_REMINDERS = "break_reminders"
    const val MISSED_ALERTS = "missed_alerts"
    const val END_DAY_REVIEW_REMINDER = "end_day_review_reminder"
    const val SLEEP_JOURNAL_LOG_REMINDER = "sleep_journal_log_reminder"

    const val DEFAULT_BLOCK_START_REMINDERS = true
    const val DEFAULT_BREAK_REMINDERS = false
    // Off by default: the always-on live "now" notification already shows whether the current block
    // is on track, so the separate +10m "Progress check" reminder was redundant noise.
    const val DEFAULT_MISSED_ALERTS = false
    const val DEFAULT_END_DAY_REVIEW_REMINDER = false
    const val DEFAULT_SLEEP_JOURNAL_LOG_REMINDER = false
}

data class DayDialReminderSettings(
    val blockStartReminders: Boolean,
    val breakReminders: Boolean,
    val missedAlerts: Boolean,
    val endDayReviewReminder: Boolean,
    // Evening nudge to log last night's sleep and capture today's journal; deep-links to the
    // sleep log sheet (see DayDialReminderDelegate).
    val sleepJournalLogReminder: Boolean,
    val sleepScheduleEnabled: Boolean,
    val sleepScheduleStartMinute: Int,
    val sleepScheduleEndMinute: Int,
    // The end-day review reminder deep-links to the journal sheet, so it only schedules
    // when the Journal feature is enabled (see DayDialReminderDelegate).
    val journalEnabled: Boolean = true,
    // The sleep & journal log reminder deep-links to the sleep log sheet; it only schedules when
    // at least one of the Sleep / Journal capture surfaces is enabled (see DayDialReminderDelegate).
    val sleepEnabled: Boolean = true
) {
    companion object {
        val DEFAULT = DayDialReminderSettings(
            blockStartReminders = DayDialReminderSettingsKeys.DEFAULT_BLOCK_START_REMINDERS,
            breakReminders = DayDialReminderSettingsKeys.DEFAULT_BREAK_REMINDERS,
            missedAlerts = DayDialReminderSettingsKeys.DEFAULT_MISSED_ALERTS,
            endDayReviewReminder = DayDialReminderSettingsKeys.DEFAULT_END_DAY_REVIEW_REMINDER,
            sleepJournalLogReminder = DayDialReminderSettingsKeys.DEFAULT_SLEEP_JOURNAL_LOG_REMINDER,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = SleepSchedule.DEFAULT_START_MINUTE,
            sleepScheduleEndMinute = SleepSchedule.DEFAULT_END_MINUTE,
            journalEnabled = true,
            sleepEnabled = true
        )
    }
}
