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
        preferences.getBoolean(KEY_REDACT_WIDGET_MEDICATION, defaultValue = false)

    fun setRedactMedicationOnWidgets(enabled: Boolean) {
        preferences.putBoolean(KEY_REDACT_WIDGET_MEDICATION, enabled)
    }

    fun redactCommandPaletteHistory(): Boolean =
        preferences.getBoolean(KEY_REDACT_COMMAND_SEARCH, defaultValue = false)

    fun setRedactCommandPaletteHistory(enabled: Boolean) {
        preferences.putBoolean(KEY_REDACT_COMMAND_SEARCH, enabled)
    }

    /**
     * Whether medication names may be shared with the DevTime companion app via InteropProvider.
     * Defaults to false (opt-in) — medication data is never shared without explicit user consent.
     */
    fun isMedicationSharingEnabled(): Boolean =
        preferences.getBoolean(KEY_INTEROP_SHARING_MEDICATIONS, defaultValue = false)

    fun setMedicationSharingEnabled(enabled: Boolean) {
        preferences.putBoolean(KEY_INTEROP_SHARING_MEDICATIONS, enabled)
    }

    /**
     * Whether ChronosFlow accepts inbound handoffs (reading-list saves, inbox captures, tasks) that
     * a trusted peer app (Curio) writes via InteropProvider. The INBOUND counterpart of medication
     * sharing: it only adds user-initiated items in and never exposes existing data, and inbound
     * writes are already restricted to the pinned peer signing cert, so it defaults to true. This is
     * a user kill-switch. Must mirror ChronosPreferencesDataSource.isInboundHandoffAccepted (same key
     * + default) — both the provider and this UI read/write the same preference.
     */
    fun isInboundHandoffAccepted(): Boolean =
        preferences.getBoolean(KEY_INTEROP_INBOUND_ACCEPTED, defaultValue = true)

    fun setInboundHandoffAccepted(accepted: Boolean) {
        preferences.putBoolean(KEY_INTEROP_INBOUND_ACCEPTED, accepted)
    }

    companion object {
        const val KEY_REDACT_NOTIFICATIONS = "privacy_redact_notifications"
        const val KEY_REDACT_WIDGET_MEDICATION = "privacy_redact_widget_medication"
        const val KEY_REDACT_COMMAND_SEARCH = "privacy_redact_command_search"
        const val KEY_INTEROP_SHARING_MEDICATIONS = "interop.sharing.medications.enabled"
        const val KEY_INTEROP_INBOUND_ACCEPTED = "interop.inbound.accepted"
    }
}
