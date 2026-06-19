package com.ChronosFlow.VBCR.feature.daydial.delegate

import androidx.fragment.app.FragmentActivity
import com.ChronosFlow.VBCR.core.data.security.AppLockAuthResult
import com.ChronosFlow.VBCR.core.data.security.AppLockAuthenticator
import com.ChronosFlow.VBCR.core.data.security.AppLockManager
import com.ChronosFlow.VBCR.core.data.security.AppLockPreferences
import com.ChronosFlow.VBCR.core.data.security.SensitiveArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AppLockSettingsState(
    val appLockEnabled: Boolean = false,
    val lockOnResume: Boolean = true,
    val requireAuthMedication: Boolean = true,
    val requireAuthReview: Boolean = false,
    val requireAuthDataExport: Boolean = true
)

@Singleton
class DayDialAppLockDelegate @Inject constructor(
    private val preferences: AppLockPreferences,
    private val appLockManager: AppLockManager,
    private val authenticator: AppLockAuthenticator
) {
    val sensitiveSession = appLockManager.sensitiveSession
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppLockSettingsState> = _settings.asStateFlow()

    fun refresh() {
        _settings.value = readSettings()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        preferences.setAppLockEnabled(enabled)
        if (enabled) {
            appLockManager.lockApp()
        } else {
            appLockManager.unlockApp()
        }
        refresh()
    }

    fun setLockOnResume(enabled: Boolean) {
        preferences.setLockOnResume(enabled)
        refresh()
    }

    fun setRequireAuthMedication(enabled: Boolean) {
        preferences.setRequireAuthFor(SensitiveArea.MEDICATION, enabled)
        if (!enabled) appLockManager.unlockSensitiveArea(SensitiveArea.MEDICATION)
        refresh()
    }

    fun setRequireAuthReview(enabled: Boolean) {
        preferences.setRequireAuthFor(SensitiveArea.REVIEW, enabled)
        if (!enabled) appLockManager.unlockSensitiveArea(SensitiveArea.REVIEW)
        refresh()
    }

    fun setRequireAuthDataExport(enabled: Boolean) {
        preferences.setRequireAuthFor(SensitiveArea.DATA_EXPORT, enabled)
        if (!enabled) appLockManager.unlockSensitiveArea(SensitiveArea.DATA_EXPORT)
        refresh()
    }

    fun requiresSensitiveAuth(area: SensitiveArea): Boolean =
        appLockManager.requiresSensitiveAuth(area)

    fun unlockSensitiveArea(
        activity: FragmentActivity,
        area: SensitiveArea,
        onResult: (AppLockAuthResult) -> Unit
    ) {
        authenticator.authenticateForSensitiveArea(activity, area) { result ->
            if (result is AppLockAuthResult.Success) {
                appLockManager.unlockSensitiveArea(area)
            }
            onResult(result)
        }
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        authenticator.canAuthenticate(activity)

    private fun readSettings(): AppLockSettingsState = AppLockSettingsState(
        appLockEnabled = preferences.isAppLockEnabled(),
        lockOnResume = preferences.lockOnResume(),
        requireAuthMedication = preferences.requireAuthFor(SensitiveArea.MEDICATION),
        requireAuthReview = preferences.requireAuthFor(SensitiveArea.REVIEW),
        requireAuthDataExport = preferences.requireAuthFor(SensitiveArea.DATA_EXPORT)
    )
}
