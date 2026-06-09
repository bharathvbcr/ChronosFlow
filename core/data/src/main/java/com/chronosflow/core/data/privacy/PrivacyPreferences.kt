package com.chronosflow.core.data.privacy

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyPreferences @Inject constructor(
    private val preferences: ChronosPreferencesDataSource
) {
    fun redactSensitiveNotifications(): Boolean =
        preferences.getBoolean(KEY_REDACT_NOTIFICATIONS, defaultValue = true)

    fun setRedactSensitiveNotifications(enabled: Boolean) {
        preferences.putBoolean(KEY_REDACT_NOTIFICATIONS, enabled)
    }

    fun redactMedicationOnWidgets(): Boolean =
        preferences.getBoolean(KEY_REDACT_WIDGET_MEDICATION, defaultValue = true)

    fun setRedactMedicationOnWidgets(enabled: Boolean) {
        preferences.putBoolean(KEY_REDACT_WIDGET_MEDICATION, enabled)
    }

    fun redactCommandPaletteHistory(): Boolean =
        preferences.getBoolean(KEY_REDACT_COMMAND_SEARCH, defaultValue = true)

    fun setRedactCommandPaletteHistory(enabled: Boolean) {
        preferences.putBoolean(KEY_REDACT_COMMAND_SEARCH, enabled)
    }

    companion object {
        const val KEY_REDACT_NOTIFICATIONS = "privacy_redact_notifications"
        const val KEY_REDACT_WIDGET_MEDICATION = "privacy_redact_widget_medication"
        const val KEY_REDACT_COMMAND_SEARCH = "privacy_redact_command_search"
    }
}
