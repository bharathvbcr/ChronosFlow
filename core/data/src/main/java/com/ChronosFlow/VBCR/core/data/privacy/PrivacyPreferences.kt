package com.ChronosFlow.VBCR.core.data.privacy

import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyPreferences @Inject constructor(
    private val preferences: ChronosPreferencesDataSource
) {
    // Off by default: the watch mirror and focus notifications show real titles/details out of the
    // box. Users opt into redaction via the Settings → Privacy & Sync toggle (which calls the setter
    // below); once set, the stored choice is honoured.
    fun redactSensitiveNotifications(): Boolean =
        preferences.getBoolean(KEY_REDACT_NOTIFICATIONS, defaultValue = false)

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
