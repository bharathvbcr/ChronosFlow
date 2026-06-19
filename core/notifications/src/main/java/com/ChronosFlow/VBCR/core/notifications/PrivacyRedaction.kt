package com.ChronosFlow.VBCR.core.notifications

/**
 * Redacts sensitive titles for lock screen, widgets, and promoted notifications.
 */
object PrivacyRedaction {
    const val GENERIC_FOCUS_TITLE = "Focus session active"
    const val GENERIC_MEDICATION_TITLE = "Medication reminder"
    const val GENERIC_HABIT_TITLE = "Health routine"

    fun focusNotificationTitle(
        blockTitle: String?,
        redactSensitiveTitles: Boolean
    ): String = if (redactSensitiveTitles || blockTitle.isNullOrBlank()) {
        GENERIC_FOCUS_TITLE
    } else {
        blockTitle
    }

    fun medicationWidgetLabel(
        planName: String?,
        redactMedicationNames: Boolean
    ): String = if (redactMedicationNames || planName.isNullOrBlank()) {
        GENERIC_MEDICATION_TITLE
    } else {
        planName
    }
}
