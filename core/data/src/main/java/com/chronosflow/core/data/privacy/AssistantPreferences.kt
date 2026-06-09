package com.chronosflow.core.data.privacy

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantPreferences @Inject constructor(
    private val preferences: ChronosPreferencesDataSource
) {
    fun assistantPrivacyModeValue(): String =
        preferences.getString(KEY_ASSISTANT_PRIVACY_MODE, DEFAULT_PRIVACY_MODE)

    fun setAssistantPrivacyModeValue(value: String) {
        preferences.putString(KEY_ASSISTANT_PRIVACY_MODE, value)
    }

    fun preferPreviewNanoModel(): Boolean =
        preferences.getBoolean(KEY_PREFER_PREVIEW_NANO_MODEL, false)

    fun setPreferPreviewNanoModel(enabled: Boolean) {
        preferences.putBoolean(KEY_PREFER_PREVIEW_NANO_MODEL, enabled)
    }

    companion object {
        const val DEFAULT_PRIVACY_MODE = "ON_DEVICE_ONLY"
        const val KEY_ASSISTANT_PRIVACY_MODE = "assistant_privacy_mode"
        const val KEY_PREFER_PREVIEW_NANO_MODEL = "assistant_prefer_preview_nano_model"
    }
}
