package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.ChronosFlow.VBCR.core.ui.components.ChronosSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.data.health.HealthConnectAvailability
import com.ChronosFlow.VBCR.core.data.health.HealthConnectSleepSyncManager
import com.ChronosFlow.VBCR.core.data.health.HealthConnectSleepSyncOutcome
import com.ChronosFlow.VBCR.core.data.health.HealthConnectSleepSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HealthConnectSleepViewModel @Inject constructor(
    private val manager: HealthConnectSleepSyncManager
) : ViewModel() {
    private val _status = MutableStateFlow(manager.status())
    val status = _status.asStateFlow()
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    // One-shot completion events for an on-demand sync so the UI can give tactile feedback
    // (a confirm tick on success, a reject buzz on failure) when the async pull finishes.
    private val _syncOutcomes = MutableSharedFlow<HealthConnectSleepSyncOutcome>(extraBufferCapacity = 1)
    val syncOutcomes = _syncOutcomes.asSharedFlow()

    val requestPermissions: Set<String> get() = manager.requestPermissions

    fun permissionContract() = manager.permissionRequestContract()

    /** Re-read availability/enabled state, e.g. after returning from the Health Connect screen. */
    fun refresh() {
        _status.value = manager.status()
    }

    fun setEnabled(enabled: Boolean) {
        manager.setEnabled(enabled)
        _status.value = manager.status()
    }

    /** After the permission dialog returns: enable + run an initial import once sleep read is granted. */
    fun onPermissionResult() {
        viewModelScope.launch {
            if (manager.hasSleepReadPermission()) {
                manager.setEnabled(true)
                _status.value = manager.status()
                runNow()
            } else {
                _status.value = manager.status()
            }
        }
    }

    /**
     * One-time pull after a permission grant that does NOT turn on the periodic background import —
     * used by the in-sheet "Sync from Health Connect" button, where the label promises a sync, not
     * a recurring job. The settings card still owns the auto-import toggle via [onPermissionResult].
     */
    fun onPermissionResultRunOnce() {
        viewModelScope.launch {
            _status.value = manager.status()
            if (manager.hasSleepReadPermission()) runNow()
        }
    }

    suspend fun hasSleepReadPermission() = manager.hasSleepReadPermission()
    fun onPermissionGrantResult() = onPermissionResult()
    fun managePermissionsIntent() = manager.managePermissionsIntent()

    fun runNow() {
        if (_isRunning.value) return
        _isRunning.value = true
        viewModelScope.launch {
            val outcome = manager.runSync()
            _status.value = manager.status()
            _isRunning.value = false
            _syncOutcomes.emit(outcome)
        }
    }
}

/**
 * Health Connect sleep-import controls embedded in the Export Data panel. Lets the user grant
 * read access, toggle the periodic background import, and run one on demand. Imported nights never
 * overwrite a night the user logged by hand.
 */
@Composable
internal fun HealthConnectSleepCard(viewModel: HealthConnectSleepViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Confirm the on-demand "Import now" pull with a tick on success / reject buzz on failure once
    // the async sync finishes (mirrors the in-sheet Sync button).
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.syncOutcomes.collect { outcome ->
            haptics.performHapticFeedback(
                if (outcome is HealthConnectSleepSyncOutcome.Failure) {
                    HapticFeedbackType.Reject
                } else {
                    HapticFeedbackType.Confirm
                }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionContract()) {
        viewModel.onPermissionResult()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Text(
            "Sleep from Health Connect",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Automatically import last night's sleep from Health Connect a few times a day. " +
                "Bed/wake times and interruptions are filled in for you; nights you log by hand are " +
                "left untouched.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (status.availability) {
            HealthConnectAvailability.AVAILABLE -> AvailableControls(
                status = status,
                isRunning = isRunning,
                onToggle = { enable ->
                    if (enable) permissionLauncher.launch(viewModel.requestPermissions) else viewModel.setEnabled(false)
                },
                onSyncNow = viewModel::runNow
            )

            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> ProviderActionRow(
                message = "Update Health Connect to import sleep.",
                buttonLabel = "Update Health Connect",
                onClick = { context.openHealthConnectInStore() }
            )

            HealthConnectAvailability.NOT_SUPPORTED -> ProviderActionRow(
                message = "Health Connect isn't set up on this device.",
                buttonLabel = "Get Health Connect",
                onClick = { context.openHealthConnectInStore() }
            )
        }
    }
}

/**
 * Compact one-tap "Sync from Health Connect" control for the Sleep Log sheet. Pulls last night's
 * sleep on demand — granting read access first if it isn't set up yet — WITHOUT turning on the
 * periodic background import (the settings card owns that toggle). The imported night flows back
 * into the open sheet through the reactive sleepTrack stream, so the fields populate themselves.
 */
@Composable
internal fun HealthConnectSleepSyncButton(
    modifier: Modifier = Modifier,
    viewModel: HealthConnectSleepViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(viewModel) {
        viewModel.syncOutcomes.collect { outcome ->
            haptics.performHapticFeedback(
                if (outcome is HealthConnectSleepSyncOutcome.Failure) {
                    HapticFeedbackType.Reject
                } else {
                    HapticFeedbackType.Confirm
                }
            )
        }
    }

    // A one-time pull: after the grant we run once but do NOT enable the recurring background import.
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionContract()) {
        viewModel.onPermissionResultRunOnce()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        when (status.availability) {
            HealthConnectAvailability.AVAILABLE -> {
                ChronosOutlinedButton(
                    onClick = {
                        // Already set up → pull straight away; otherwise request read access first.
                        if (status.enabled) viewModel.runNow() else permissionLauncher.launch(viewModel.requestPermissions)
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRunning) "Syncing…" else "Sync from Health Connect")
                }
                if (status.lastResult.isNotEmpty()) {
                    Text(
                        status.lastResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> ProviderActionRow(
                message = "Update Health Connect to import sleep.",
                buttonLabel = "Update Health Connect",
                onClick = { context.openHealthConnectInStore() }
            )

            HealthConnectAvailability.NOT_SUPPORTED -> ProviderActionRow(
                message = "Health Connect isn't set up on this device.",
                buttonLabel = "Get Health Connect",
                onClick = { context.openHealthConnectInStore() }
            )
        }
    }
}

@Composable
private fun AvailableControls(
    status: HealthConnectSleepSyncStatus,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Import sleep automatically",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        ChronosSwitch(checked = status.enabled, onCheckedChange = onToggle)
    }
    if (status.lastResult.isNotEmpty()) {
        Text(
            status.lastResult,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    ChronosOutlinedButton(
        onClick = onSyncNow,
        enabled = status.enabled && !isRunning,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (isRunning) "Importing…" else "Import now")
    }
}

@Composable
private fun ProviderActionRow(message: String, buttonLabel: String, onClick: () -> Unit) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ChronosOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(buttonLabel)
    }
}

/** Opens the Health Connect listing (its provider onboarding deep link) in the Play Store. */
internal fun android.content.Context.openHealthConnectInStore() {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse(
            "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
        )
        putExtra("overlay", true)
        putExtra("callerId", packageName)
    }
    runCatching { startActivity(intent) }
}
