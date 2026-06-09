package com.chronosflow.core.data.security

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

    private val unlockedSensitiveAreas = mutableSetOf<SensitiveArea>()
    private val _sensitiveSession = MutableStateFlow(0)
    val sensitiveSession: StateFlow<Int> = _sensitiveSession.asStateFlow()

    fun requiresAppUnlock(): Boolean =
        preferences.isAppLockEnabled() && _appLockState.value == AppLockState.LOCKED

    fun requiresSensitiveAuth(area: SensitiveArea): Boolean {
        if (!preferences.requireAuthFor(area)) return false
        if (_appLockState.value == AppLockState.LOCKED) return true
        return area !in unlockedSensitiveAreas
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
        unlockedSensitiveAreas.add(area)
        bumpSensitiveSession()
    }

    fun clearSensitiveUnlocks() {
        unlockedSensitiveAreas.clear()
        bumpSensitiveSession()
    }

    private fun bumpSensitiveSession() {
        _sensitiveSession.value = _sensitiveSession.value + 1
    }

    private fun unlockAllSensitiveAreas() {
        SensitiveArea.entries.forEach { unlockedSensitiveAreas.add(it) }
    }

    private fun initialAppLockState(): AppLockState =
        if (preferences.isAppLockEnabled()) AppLockState.LOCKED else AppLockState.UNLOCKED
}
