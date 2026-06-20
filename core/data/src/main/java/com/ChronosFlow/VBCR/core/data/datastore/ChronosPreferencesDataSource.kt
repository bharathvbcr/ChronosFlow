package com.ChronosFlow.VBCR.core.data.datastore

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class ChronosPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }

    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return preferences.getLong(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    fun getAll(): Map<String, *> = preferences.all

    /**
     * Whether medication names may be shared with the DevTime companion app via [InteropProvider].
     * Defaults to false (opt-in) so medication data is never shared without explicit user consent.
     */
    fun isMedicationSharingEnabled(): Boolean =
        preferences.getBoolean(KEY_INTEROP_SHARING_MEDICATIONS, false)

    /**
     * Master consent gate for DevTime/Meridian interop (PRIV-006).
     * Defaults to false — InteropSyncWorker will not be scheduled and InteropProvider will not
     * serve any data until the user explicitly grants consent via the disclosure dialog.
     */
    fun isInteropConsentGranted(): Boolean =
        preferences.getBoolean(KEY_INTEROP_CONSENT_GRANTED, false)

    fun setInteropConsentGranted(granted: Boolean) {
        preferences.edit().putBoolean(KEY_INTEROP_CONSENT_GRANTED, granted).apply()
    }

    /**
     * Whether the user has opted in to sending schedule data to Google cloud AI (Gemini).
     * Defaults to false so no user data is ever sent to a remote model without explicit consent.
     */
    fun isCloudAiEnabled(): Boolean =
        preferences.getBoolean(KEY_CLOUD_AI_ENABLED, false)

    fun setCloudAiEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CLOUD_AI_ENABLED, enabled).apply()
    }

    /** Emits the current value of [key] and re-emits whenever it changes. */
    fun observeLong(key: String, defaultValue: Long = 0L): Flow<Long> = callbackFlow {
        trySend(preferences.getLong(key, defaultValue))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) trySend(preferences.getLong(key, defaultValue))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val PREFERENCES_NAME = "chronos_preferences"
        const val KEY_INTEROP_SHARING_MEDICATIONS = "interop.sharing.medications.enabled"
        const val KEY_INTEROP_CONSENT_GRANTED = "interop.consent.granted"
        const val KEY_CLOUD_AI_ENABLED = "ai.cloud.enabled"
    }
}
