package com.chronosflow.core.data.security

import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

data class AppLockUiState(
    val appLockEnabled: Boolean = false,
    val lockOnResume: Boolean = true,
    val requireAuthMedication: Boolean = true,
    val requireAuthReview: Boolean = false,
    val requireAuthDataExport: Boolean = true,
    val isAppLocked: Boolean = false,
    val canAuthenticate: Boolean = true,
    val authError: String? = null
)

@Singleton
class AppLockSessionController @Inject constructor(
    private val preferences: AppLockPreferences,
    private val appLockManager: AppLockManager,
    private val authenticator: AppLockAuthenticator
) {
    private val settingsRefresh = MutableStateFlow(0)
    private val authError = MutableStateFlow<String?>(null)
    private val canAuthenticate = MutableStateFlow(true)

    val sensitiveSession = appLockManager.sensitiveSession

    val uiState: Flow<AppLockUiState> = combine(
        appLockManager.appLockState,
        appLockManager.sensitiveSession,
        settingsRefresh,
        authError,
        canAuthenticate
    ) { lockState, _, _, currentAuthError, currentCanAuthenticate ->
        AppLockUiState(
            appLockEnabled = preferences.isAppLockEnabled(),
            lockOnResume = preferences.lockOnResume(),
            requireAuthMedication = preferences.requireAuthFor(SensitiveArea.MEDICATION),
            requireAuthReview = preferences.requireAuthFor(SensitiveArea.REVIEW),
            requireAuthDataExport = preferences.requireAuthFor(SensitiveArea.DATA_EXPORT),
            isAppLocked = lockState == AppLockState.LOCKED,
            canAuthenticate = currentCanAuthenticate,
            authError = currentAuthError
        )
    }

    fun refreshDeviceAuth(activity: FragmentActivity) {
        canAuthenticate.value = authenticator.canAuthenticate(activity)
    }

    fun onColdStart() {
        appLockManager.onColdStart()
    }

    fun onAppForegrounded(fromBackground: Boolean) {
        appLockManager.onAppForegrounded(fromBackground)
    }

    fun onAppBackgrounded() {
        appLockManager.clearSensitiveUnlocks()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        preferences.setAppLockEnabled(enabled)
        if (enabled) {
            appLockManager.lockApp()
        } else {
            appLockManager.unlockApp()
        }
        bumpSettings()
    }

    fun setLockOnResume(enabled: Boolean) {
        preferences.setLockOnResume(enabled)
        bumpSettings()
    }

    fun setRequireAuthFor(area: SensitiveArea, enabled: Boolean) {
        preferences.setRequireAuthFor(area, enabled)
        if (!enabled) {
            appLockManager.unlockSensitiveArea(area)
        }
        bumpSettings()
    }

    fun requiresSensitiveAuth(area: SensitiveArea): Boolean =
        appLockManager.requiresSensitiveAuth(area)

    fun unlockApp(activity: FragmentActivity) {
        authError.value = null
        authenticator.authenticateForAppUnlock(activity) { result ->
            handleAuthResult(result) {
                appLockManager.unlockApp()
            }
        }
    }

    fun unlockSensitiveArea(
        activity: FragmentActivity,
        area: SensitiveArea,
        onResult: (AppLockAuthResult) -> Unit = {}
    ) {
        authError.value = null
        authenticator.authenticateForSensitiveArea(activity, area) { result ->
            handleAuthResult(result) {
                appLockManager.unlockSensitiveArea(area)
            }
            onResult(result)
        }
    }

    private fun handleAuthResult(result: AppLockAuthResult, onSuccess: () -> Unit) {
        when (result) {
            AppLockAuthResult.Success -> {
                authError.value = null
                onSuccess()
                bumpSettings()
            }
            AppLockAuthResult.Cancelled -> Unit
            AppLockAuthResult.Unavailable ->
                authError.value = "Set a screen lock (PIN, pattern, or password) in Android settings."
            is AppLockAuthResult.Error -> authError.value = result.message
        }
    }

    private fun bumpSettings() {
        settingsRefresh.update { it + 1 }
    }
}
