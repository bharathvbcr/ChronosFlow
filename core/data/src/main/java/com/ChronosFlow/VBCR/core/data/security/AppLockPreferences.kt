package com.ChronosFlow.VBCR.core.data.security

import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockPreferences @Inject constructor(
    private val preferences: ChronosPreferencesDataSource
) {
    fun isAppLockEnabled(): Boolean =
        preferences.getBoolean(KEY_APP_LOCK_ENABLED, defaultValue = false)

    fun setAppLockEnabled(enabled: Boolean) {
        preferences.putBoolean(KEY_APP_LOCK_ENABLED, enabled)
        if (enabled) {
            if (!preferences.getBoolean(KEY_LOCK_ON_RESUME, defaultValue = true)) {
                preferences.putBoolean(KEY_LOCK_ON_RESUME, true)
            }
            if (!preferences.getBoolean(KEY_REQUIRE_AUTH_MEDICATION, defaultValue = true)) {
                preferences.putBoolean(KEY_REQUIRE_AUTH_MEDICATION, true)
            }
        }
    }

    fun lockOnResume(): Boolean =
        preferences.getBoolean(KEY_LOCK_ON_RESUME, defaultValue = true)

    fun setLockOnResume(enabled: Boolean) {
        preferences.putBoolean(KEY_LOCK_ON_RESUME, enabled)
    }

    fun requireAuthFor(area: SensitiveArea): Boolean = when (area) {
        SensitiveArea.MEDICATION ->
            preferences.getBoolean(KEY_REQUIRE_AUTH_MEDICATION, defaultValue = true)
        SensitiveArea.REVIEW ->
            preferences.getBoolean(KEY_REQUIRE_AUTH_REVIEW, defaultValue = false)
        SensitiveArea.DATA_EXPORT ->
            preferences.getBoolean(KEY_REQUIRE_AUTH_DATA_EXPORT, defaultValue = true)
    }

    fun setRequireAuthFor(area: SensitiveArea, enabled: Boolean) {
        when (area) {
            SensitiveArea.MEDICATION ->
                preferences.putBoolean(KEY_REQUIRE_AUTH_MEDICATION, enabled)
            SensitiveArea.REVIEW ->
                preferences.putBoolean(KEY_REQUIRE_AUTH_REVIEW, enabled)
            SensitiveArea.DATA_EXPORT ->
                preferences.putBoolean(KEY_REQUIRE_AUTH_DATA_EXPORT, enabled)
        }
    }

    companion object {
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_LOCK_ON_RESUME = "app_lock_on_resume"
        const val KEY_REQUIRE_AUTH_MEDICATION = "app_lock_require_medication"
        const val KEY_REQUIRE_AUTH_REVIEW = "app_lock_require_review"
        const val KEY_REQUIRE_AUTH_DATA_EXPORT = "app_lock_require_data_export"
    }
}
