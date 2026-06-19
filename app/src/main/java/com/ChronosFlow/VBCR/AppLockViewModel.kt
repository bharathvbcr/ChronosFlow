package com.ChronosFlow.VBCR

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.data.security.AppLockAuthResult
import com.ChronosFlow.VBCR.core.data.security.AppLockSessionController
import com.ChronosFlow.VBCR.core.data.security.AppLockUiState
import com.ChronosFlow.VBCR.core.data.security.SensitiveArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockSessionController: AppLockSessionController
) : ViewModel() {

    val sensitiveSession = appLockSessionController.sensitiveSession

    val uiState: StateFlow<AppLockUiState> = appLockSessionController.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppLockUiState()
    )

    fun refreshDeviceAuth(activity: FragmentActivity) {
        appLockSessionController.refreshDeviceAuth(activity)
    }

    fun onColdStart() {
        appLockSessionController.onColdStart()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        appLockSessionController.setAppLockEnabled(enabled)
    }

    fun setLockOnResume(enabled: Boolean) {
        appLockSessionController.setLockOnResume(enabled)
    }

    fun setRequireAuthMedication(enabled: Boolean) {
        appLockSessionController.setRequireAuthFor(SensitiveArea.MEDICATION, enabled)
    }

    fun setRequireAuthReview(enabled: Boolean) {
        appLockSessionController.setRequireAuthFor(SensitiveArea.REVIEW, enabled)
    }

    fun setRequireAuthDataExport(enabled: Boolean) {
        appLockSessionController.setRequireAuthFor(SensitiveArea.DATA_EXPORT, enabled)
    }

    fun requiresSensitiveAuth(area: SensitiveArea): Boolean =
        appLockSessionController.requiresSensitiveAuth(area)

    fun unlockApp(activity: FragmentActivity) {
        appLockSessionController.unlockApp(activity)
    }

    fun unlockSensitiveArea(
        activity: FragmentActivity,
        area: SensitiveArea,
        onResult: (AppLockAuthResult) -> Unit = {}
    ) {
        appLockSessionController.unlockSensitiveArea(activity, area, onResult)
    }
}
