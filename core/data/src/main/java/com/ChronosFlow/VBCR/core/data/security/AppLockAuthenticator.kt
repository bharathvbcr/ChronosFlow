package com.ChronosFlow.VBCR.core.data.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

sealed class AppLockAuthResult {
    data object Success : AppLockAuthResult()
    data object Cancelled : AppLockAuthResult()
    data class Error(val message: String) : AppLockAuthResult()
    data object Unavailable : AppLockAuthResult()
}

@Singleton
class AppLockAuthenticator @Inject constructor() {

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(activity).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateForAppUnlock(
        activity: FragmentActivity,
        onResult: (AppLockAuthResult) -> Unit
    ) {
        showPrompt(
            activity = activity,
            title = "Unlock ChronosFlow",
            subtitle = "Confirm your identity to open the app",
            onResult = onResult
        )
    }

    fun authenticateForSensitiveArea(
        activity: FragmentActivity,
        area: SensitiveArea,
        onResult: (AppLockAuthResult) -> Unit
    ) {
        val subtitle = when (area) {
            SensitiveArea.MEDICATION -> "Confirm your identity to view medications"
            SensitiveArea.REVIEW -> "Confirm your identity to view insights and mood"
            SensitiveArea.DATA_EXPORT -> "Confirm your identity to export data"
        }
        showPrompt(
            activity = activity,
            title = "Protected content",
            subtitle = subtitle,
            onResult = onResult
        )
    }

    private fun showPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (AppLockAuthResult) -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(AppLockAuthResult.Unavailable)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(AppLockAuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onResult(AppLockAuthResult.Cancelled)
                } else {
                    onResult(AppLockAuthResult.Error(errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                // Per-attempt failure; the system prompt stays open for retry. Do nothing.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }
}
