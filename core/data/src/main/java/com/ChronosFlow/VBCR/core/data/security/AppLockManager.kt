package com.ChronosFlow.VBCR.core.data.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLockState {
    UNLOCKED,
    LOCKED
}

@Singleton
class AppLockManager @Inject constructor(
    private val preferences: AppLockPreferences
) {
    private val _appLockState = MutableStateFlow(initialAppLockState())
    val appLockState: StateFlow<AppLockState> = _appLockState.asStateFlow()

    private val sensitiveAreaUnlockTimes = mutableMapOf<SensitiveArea, Long>()
    private val unlockedSensitiveAreas: Set<SensitiveArea> get() = sensitiveAreaUnlockTimes.keys
    private val _sensitiveSession = MutableStateFlow(0)
    val sensitiveSession: StateFlow<Int> = _sensitiveSession.asStateFlow()

    fun requiresAppUnlock(): Boolean =
        preferences.isAppLockEnabled() && _appLockState.value == AppLockState.LOCKED

    fun requiresSensitiveAuth(area: SensitiveArea): Boolean {
        if (!preferences.requireAuthFor(area)) return false
        if (_appLockState.value == AppLockState.LOCKED) return true
        val unlockedAt = sensitiveAreaUnlockTimes[area] ?: return true
        if (System.currentTimeMillis() - unlockedAt > SENSITIVE_SESSION_TIMEOUT_MS) {
            sensitiveAreaUnlockTimes.remove(area)
            bumpSensitiveSession()
            return true
        }
        return false
    }

    fun onAppForegrounded(fromBackground: Boolean) {
        if (!preferences.isAppLockEnabled()) return
        if (fromBackground && preferences.lockOnResume()) {
            lockApp()
        }
    }

    fun onColdStart() {
        if (preferences.isAppLockEnabled()) {
            lockApp()
        }
    }

    fun unlockApp(unlockSensitiveAreas: Boolean = true) {
        _appLockState.value = AppLockState.UNLOCKED
        if (unlockSensitiveAreas) {
            unlockAllSensitiveAreas()
        }
    }

    fun lockApp() {
        if (!preferences.isAppLockEnabled()) return
        _appLockState.value = AppLockState.LOCKED
        clearSensitiveUnlocks()
    }

    fun unlockSensitiveArea(area: SensitiveArea) {
        sensitiveAreaUnlockTimes[area] = System.currentTimeMillis()
        bumpSensitiveSession()
    }

    fun clearSensitiveUnlocks() {
        sensitiveAreaUnlockTimes.clear()
        bumpSensitiveSession()
    }

    private fun bumpSensitiveSession() {
        _sensitiveSession.value = _sensitiveSession.value + 1
    }

    private fun unlockAllSensitiveAreas() {
        val now = System.currentTimeMillis()
        SensitiveArea.entries.forEach { sensitiveAreaUnlockTimes[it] = now }
    }

    private fun initialAppLockState(): AppLockState =
        if (preferences.isAppLockEnabled()) AppLockState.LOCKED else AppLockState.UNLOCKED

    private companion object {
        const val SENSITIVE_SESSION_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
