package com.chronosflow.feature.daydial

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chronosflow.core.data.backup.AutoBackupStatus
import com.chronosflow.core.data.backup.ChronosAutoBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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
            manager.runBackup()
            _status.value = manager.status()
            _isRunning.value = false
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
    val context = LocalContext.current

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.onFolderChosen(uri.toString())
        }
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
            Switch(
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
            OutlinedButton(
                onClick = { folderLauncher.launch(null) },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (status.hasFolder) "Change folder" else "Choose folder")
            }
            OutlinedButton(
                onClick = viewModel::runNow,
                enabled = status.hasFolder && !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isRunning) "Backing up…" else "Back up now")
            }
        }
    }
}

private fun AutoBackupStatus.folderLabel(): String {
    if (!hasFolder) return "No folder selected"
    val decoded = Uri.decode(Uri.parse(folderUri).lastPathSegment ?: return "Folder selected")
    val name = decoded.substringAfterLast('/').substringAfterLast(':')
    return if (name.isBlank()) "Folder selected" else "Backing up to $name"
}
