package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.data.backup.AutoBackupOutcome
import com.ChronosFlow.VBCR.core.data.backup.AutoBackupStatus
import com.ChronosFlow.VBCR.core.data.backup.ChronosAutoBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val manager: ChronosAutoBackupManager
) : ViewModel() {
    private val _status = MutableStateFlow(manager.status())
    val status = _status.asStateFlow()
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    // One-shot completion events for on-demand backup/restore so the UI can give tactile feedback
    // (a confirm tick on success, a reject buzz on failure) once the async work finishes.
    private val _backupOutcomes = MutableSharedFlow<AutoBackupOutcome>(extraBufferCapacity = 1)
    val backupOutcomes = _backupOutcomes.asSharedFlow()

    fun onFolderChosen(uriString: String) {
        manager.configureFolder(Uri.parse(uriString))
        _status.value = manager.status()
    }

    fun setEnabled(enabled: Boolean) {
        manager.setEnabled(enabled)
        _status.value = manager.status()
    }

    fun runNow() {
        if (_isRunning.value) return
        _isRunning.value = true
        viewModelScope.launch {
            val outcome = manager.runBackup()
            _status.value = manager.status()
            _isRunning.value = false
            _backupOutcomes.emit(outcome)
        }
    }

    private val _restoreResult = MutableStateFlow("")
    val restoreResult = _restoreResult.asStateFlow()
    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()

    fun restoreFrom(uriString: String) {
        if (_isRestoring.value) return
        _isRestoring.value = true
        viewModelScope.launch {
            val outcome = manager.restoreBackup(Uri.parse(uriString))
            _restoreResult.value = when (outcome) {
                is AutoBackupOutcome.Success -> outcome.fileName
                is AutoBackupOutcome.Failure -> "Restore failed: ${outcome.message}"
            }
            _isRestoring.value = false
            _backupOutcomes.emit(outcome)
        }
    }
}

/**
 * Scheduled folder-backup controls embedded in the Export Data panel. Lets the user pick a folder
 * (e.g. one synced to Drive/Dropbox), toggle daily backups, and run one on demand.
 */
@Composable
internal fun AutoBackupCard(viewModel: AutoBackupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val restoreResult by viewModel.restoreResult.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Backup/restore run async behind a "Backing up…"/"Restoring…" label, so confirm the result
    // with a tick on success and a reject buzz on failure once the work finishes.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.backupOutcomes.collect { outcome ->
            haptics.performHapticFeedback(
                if (outcome is AutoBackupOutcome.Failure) {
                    HapticFeedbackType.Reject
                } else {
                    HapticFeedbackType.Confirm
                }
            )
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.onFolderChosen(uri.toString())
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreFrom(uri.toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Text(
            "Automatic backup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Save a daily copy of all your data to a folder you choose. Point it at a synced " +
                "folder (Drive, Dropbox) to keep an off-device copy. The latest " +
                "${ChronosAutoBackupManager.MAX_RETAINED_BACKUPS} backups are kept.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Daily backup",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            ChronosSwitch(
                checked = status.enabled,
                enabled = status.hasFolder,
                onCheckedChange = viewModel::setEnabled
            )
        }

        Text(
            text = status.folderLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = if (status.hasFolder) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.SemiBold
        )
        if (status.lastResult.isNotEmpty()) {
            Text(
                status.lastResult,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChronosOutlinedButton(
                onClick = { folderLauncher.launch(null) },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (status.hasFolder) "Change folder" else "Choose folder")
            }
            ChronosOutlinedButton(
                onClick = viewModel::runNow,
                enabled = status.hasFolder && !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isRunning) "Backing up…" else "Back up now")
            }
        }

        ChronosOutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("application/json")) },
            enabled = !isRestoring,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRestoring) "Restoring…" else "Restore from backup")
        }
        if (restoreResult.isNotEmpty()) {
            Text(
                restoreResult,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun AutoBackupStatus.folderLabel(): String {
    if (!hasFolder) return "No folder selected"
    val decoded = Uri.decode(Uri.parse(folderUri).lastPathSegment ?: return "Folder selected")
    val name = decoded.substringAfterLast('/').substringAfterLast(':')
    return if (name.isBlank()) "Folder selected" else "Backing up to $name"
}

