package com.chronosflow.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.chronosflow.AppLockViewModel
import com.chronosflow.core.data.security.SensitiveArea
import com.chronosflow.core.ui.security.SensitiveContentGate

@Composable
fun SensitiveRouteGate(
    area: SensitiveArea,
    title: String,
    message: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val viewModelStoreOwner = activity ?: checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel: AppLockViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sensitiveSession by viewModel.sensitiveSession.collectAsStateWithLifecycle()
    // Fail open when the device has no enrolled credential (no PIN/pattern/password/biometric):
    // the gate can't be satisfied — BiometricPrompt would have nothing to check — so enforcing it
    // would permanently lock the user out of their own data (e.g. medications, which require auth
    // by default). This mirrors the master App-lock toggle, which likewise refuses to engage
    // without a device screen lock. Once the user sets a screen lock, protection resumes.
    val requiresAuth = remember(area, sensitiveSession, uiState.isAppLocked, uiState.canAuthenticate) {
        uiState.canAuthenticate && viewModel.requiresSensitiveAuth(area)
    }

    LaunchedEffect(activity) {
        activity?.let(viewModel::refreshDeviceAuth)
    }

    if (!requiresAuth) {
        content()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SensitiveContentGate(
            title = title,
            message = message,
            canAuthenticate = uiState.canAuthenticate && activity != null,
            errorMessage = uiState.authError,
            onUnlock = {
                activity?.let { viewModel.unlockSensitiveArea(it, area) }
            }
        )
    }
}
