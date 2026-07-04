package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType

/** Reason recorded when separate med/task/habit OS alarms are omitted under fold mode. */
internal const val FOLDED_REMINDER_SKIP_REASON = "Reminder folded into live activity"

/**
 * Guards for whether separate med/task/habit notification schedules should be omitted when fold
 * mode is active — mirrors iOS [com.chronosflow.core.ReminderFoldScheduling] /
 * `ChronosNotifications.skipsSeparateRemindersWhenFolded`.
 */
internal object ReminderFoldScheduling {
    fun skipsSeparateRemindersWhenFolded(context: Context): Boolean {
        val uiPrefs = context.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        val blockLive = if (uiPrefs.contains(BLOCK_LIVE_UI_KEY)) {
            uiPrefs.getBoolean(BLOCK_LIVE_UI_KEY, true)
        } else {
            val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(LEGACY_ENABLED_KEY, true)
            uiPrefs.edit().putBoolean(BLOCK_LIVE_UI_KEY, legacy).apply()
            legacy
        }
        val fold = uiPrefs.getBoolean(FOLD_UI_KEY, DEFAULT_FOLD_REMINDERS)
        return blockLive && fold
    }

    fun isSeparateFoldableReminder(request: AlarmRequest): Boolean = when (request.type) {
        AlarmRequestType.MEDICATION, AlarmRequestType.URGENT_TASK -> true
        AlarmRequestType.BLOCK_START ->
            request.blockId?.startsWith("habit-") == true || isHabitReminderAlarmId(request.id)
        else -> false
    }

    /** Inexact/exact alarm paths that bypass [AlarmRequest] still use stable id patterns. */
    fun isSeparateFoldableReminderAlarmId(id: String): Boolean =
        id.startsWith("task:") || isHabitReminderAlarmId(id)

    private fun isHabitReminderAlarmId(id: String): Boolean =
        id.contains(":habit-") && id.endsWith(":start")

    private const val UI_PREFS_NAME = "daydial_ui_settings"
    private const val BLOCK_LIVE_UI_KEY = "notifications.currentBlockLive"
    private const val FOLD_UI_KEY = "notifications.foldReminders"
    private const val DEFAULT_FOLD_REMINDERS = true
    private const val LEGACY_PREFS_NAME = "chronos_current_block_notification"
    private const val LEGACY_ENABLED_KEY = "enabled"
}
