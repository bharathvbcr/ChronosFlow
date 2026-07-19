@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ChronosFlow.VBCR.core.domain.model.CaptureSource
import com.ChronosFlow.VBCR.core.domain.model.InboxItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls
import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosEmptyState
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilledTonalButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosSwitch
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.shell.ChronosModalBottomSheet
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing

@Composable
internal fun InboxPageRoute(
    onMessage: (String) -> Unit = {},
    onShowUndoSnackbar: (message: String, onUndo: () -> Unit) -> Unit = { message, _ -> onMessage(message) },
    viewModel: InboxViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCaptureSheet by rememberSaveable { mutableStateOf(false) }

    ChronosButton(onClick = { showCaptureSheet = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Capture")
    }

    if (items.isEmpty()) {
        ChronosEmptyState(
            title = "Inbox zero",
            message = "Dump a quick thought or link here, then turn it into a task or reading item later.",
            modifier = Modifier.fillMaxWidth(),
            action = {
                ChronosButton(
                    onClick = { showCaptureSheet = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Capture", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    items.forEach { item ->
        InboxItemRow(
            item = item,
            onOpen = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, it.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { onMessage("Couldn't open this link") }
            },
            onReading = { viewModel.triageToReading(item); onMessage("Saved to reading list") },
            onTask = { viewModel.triageToTask(item); onMessage("Added to tasks") },
            onDelete = {
                viewModel.discard(item)
                onShowUndoSnackbar("Removed") { viewModel.restore(item) }
            }
        )
    }

    if (showCaptureSheet) {
        QuickCaptureSheet(
            initialText = "",
            onDismiss = { showCaptureSheet = false },
            onCaptureToInbox = { text ->
                viewModel.capture(text, CaptureSource.MANUAL)
                showCaptureSheet = false
                onMessage("Captured to inbox")
            },
            onSaveToReading = { text ->
                viewModel.captureAsReading(text, CaptureSource.MANUAL)
                showCaptureSheet = false
                onMessage("Saved to reading list")
            }
        )
    }
}

@Composable
private fun InboxItemRow(
    item: InboxItem,
    onOpen: (String) -> Unit,
    onReading: () -> Unit,
    onTask: () -> Unit,
    onDelete: () -> Unit
) {
    val url = item.url
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (url != null) {
                Text(
                    ReadingUrls.domainOf(url),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (url != null) {
                    ChronosFilledTonalButton(onClick = onReading, modifier = Modifier.weight(1f)) {
                        Text("Read later")
                    }
                }
                ChronosOutlinedButton(onClick = onTask, modifier = Modifier.weight(1f)) {
                    Text("Make task")
                }
                if (url != null) {
                    ChronosTextButton(onClick = { onOpen(url) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open link")
                    }
                }
                ChronosTextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun QuickCaptureSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onCaptureToInbox: (String) -> Unit,
    onSaveToReading: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val isUrl = remember(text) { ReadingUrls.firstUrlIn(text) != null }
    var saveToReading by rememberSaveable { mutableStateOf(false) }
    // Default the toggle on the first time a URL is detected.
    LaunchedEffect(isUrl) { if (isUrl) saveToReading = true }

    ChronosModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ChronosSpacing.Standard),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
        ) {
            Text("Quick capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Thought or link") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            if (isUrl) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save to Reading List", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "This looks like a link",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ChronosSwitch(checked = saveToReading, onCheckedChange = { saveToReading = it })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChronosOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                ChronosButton(
                    onClick = {
                        val value = text.trim()
                        if (value.isEmpty()) return@ChronosButton
                        if (isUrl && saveToReading) onSaveToReading(value) else onCaptureToInbox(value)
                    },
                    enabled = text.trim().isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text(if (isUrl && saveToReading) "Read later" else "Capture") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
