package com.ChronosFlow.VBCR.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ChronosFlow.VBCR.AppLockViewModel
import com.ChronosFlow.VBCR.core.data.security.SensitiveArea
import com.ChronosFlow.VBCR.core.ui.security.SensitiveContentGate

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
        // Fail-open when no device credential is enrolled (canAuthenticate=false). Show a
        // dismissible warning banner so the user understands their sensitive data is unprotected
        // — but still render the content to avoid a permanent lockout (the original intent).
        if (!uiState.canAuthenticate && viewModel.requiresSensitiveAuth(area)) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "$title is unprotected — no device screen lock is set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                ChronosButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_SECURITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set screen lock to protect $title")
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        } else {
            content()
        }
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
