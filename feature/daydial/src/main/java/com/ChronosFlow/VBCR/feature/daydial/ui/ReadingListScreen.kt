@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.model.ReadingStatus
import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls
import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosCollapsibleSection
import com.ChronosFlow.VBCR.core.ui.components.ChronosDropdownMenuItem
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilterChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosIconButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.shell.ChronosModalBottomSheet
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
internal fun ReadingListPageRoute(
    onMessage: (String) -> Unit = {},
    viewModel: ReadingListViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    val openLink: (ReadingItem) -> Unit = { item ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, item.url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            viewModel.markOpened(item)
        }.onFailure { onMessage("Couldn't open this link") }
    }

    val toRead = items.filter { it.status == ReadingStatus.UNREAD || it.status == ReadingStatus.READING }
    val done = items.filter { it.status == ReadingStatus.DONE }

    ChronosButton(onClick = { showAddSheet = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Add link")
    }

    if (toRead.isEmpty() && done.isEmpty()) {
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = ChronosSpacing.Medium)) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Nothing to read yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Save a link here, or share one from your browser, and get a nudge to read it later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    toRead.forEach { item ->
        ReadingItemRow(item = item, onOpen = openLink, viewModel = viewModel, onMessage = onMessage)
    }

    if (done.isNotEmpty()) {
        var doneExpanded by rememberSaveable { mutableStateOf(false) }
        ChronosCollapsibleSection(
            title = "Read",
            summary = "${done.size} finished",
            expanded = doneExpanded,
            onExpandedChange = { doneExpanded = it }
        ) {
            done.forEach { item ->
                ReadingItemRow(item = item, onOpen = openLink, viewModel = viewModel, onMessage = onMessage)
            }
        }
    }

    if (showAddSheet) {
        ReadingAddSheet(
            initialUrl = "",
            onDismiss = { showAddSheet = false },
            onSave = { url, title, remindAt ->
                viewModel.addUrl(url, title, remindAt)
                showAddSheet = false
                onMessage(if (remindAt != null) "Saved · reminder set" else "Saved to reading list")
            }
        )
    }
}

@Composable
private fun ReadingItemRow(
    item: ReadingItem,
    onOpen: (ReadingItem) -> Unit,
    viewModel: ReadingListViewModel,
    onMessage: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var remindMenuOpen by remember { mutableStateOf(false) }
    ChronosListCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(item) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
            ReadingFavicon(item)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    readingSubtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.reminderAt != null) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = "Reminder set",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box {
                ChronosIconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ChronosDropdownMenuItem(
                        text = { Text("Open link") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        onClick = { menuOpen = false; onOpen(item) }
                    )
                    ChronosDropdownMenuItem(
                        text = { Text("Read with focus") },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            viewModel.readWithFocus(item)
                            onMessage("Focus session started")
                        }
                    )
                    ChronosDropdownMenuItem(
                        text = { Text(if (item.reminderAt != null) "Change reminder" else "Remind me…") },
                        leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                        onClick = { menuOpen = false; remindMenuOpen = true }
                    )
                    if (item.status == ReadingStatus.DONE) {
                        ChronosDropdownMenuItem(
                            text = { Text("Mark unread") },
                            onClick = { menuOpen = false; viewModel.setStatus(item.id, ReadingStatus.UNREAD) }
                        )
                    } else {
                        ChronosDropdownMenuItem(
                            text = { Text("Mark read") },
                            onClick = { menuOpen = false; viewModel.markRead(item) }
                        )
                    }
                    if (item.metadataState == ReadingMetadataState.FAILED) {
                        ChronosDropdownMenuItem(
                            text = { Text("Retry details") },
                            onClick = { menuOpen = false; viewModel.refetchMetadata(item) }
                        )
                    }
                    ChronosDropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; viewModel.delete(item); onMessage("Removed") }
                    )
                }
                DropdownMenu(expanded = remindMenuOpen, onDismissRequest = { remindMenuOpen = false }) {
                    reminderPresets().forEach { (label, at) ->
                        ChronosDropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                remindMenuOpen = false
                                viewModel.remind(item, at)
                                onMessage("Reminder set · $label")
                            }
                        )
                    }
                    if (item.reminderAt != null) {
                        ChronosDropdownMenuItem(
                            text = { Text("Clear reminder") },
                            onClick = { remindMenuOpen = false; viewModel.clearReminder(item); onMessage("Reminder cleared") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingFavicon(item: ReadingItem) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, item.faviconPath) {
        val path = item.faviconPath
        value = if (path != null && File(path).exists()) {
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        } else {
            null
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.domain.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReadingAddSheet(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (url: String, title: String?, remindAt: Instant?) -> Unit
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedReminder by rememberSaveable { mutableStateOf<String?>(null) }
    val presets = remember { reminderPresets() }

    ChronosModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ChronosSpacing.Standard),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
        ) {
            Text("Save to read later", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Remind me", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                presets.forEach { (label, _) ->
                    ChronosFilterChip(
                        selected = selectedReminder == label,
                        onClick = { selectedReminder = if (selectedReminder == label) null else label },
                        label = { Text(label) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChronosOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                ChronosButton(
                    onClick = {
                        val remindAt = presets.firstOrNull { it.first == selectedReminder }?.second
                        onSave(url.trim(), title.trim().takeIf { it.isNotBlank() }, remindAt)
                    },
                    enabled = ReadingUrls.firstUrlIn(url) != null || url.trim().isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun readingSubtitle(item: ReadingItem): String {
    val parts = buildList {
        add(item.domain)
        item.estimatedReadMinutes?.let { add("$it min read") }
        if (item.metadataState == ReadingMetadataState.PENDING) add("fetching…")
    }
    return parts.joinToString(" · ")
}

/** A few "remind me to read later" presets, resolved to concrete [Instant]s. */
private fun reminderPresets(): List<Pair<String, Instant>> {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now().atTime(LocalTime.now())
    val tonight = LocalDate.now().atTime(LocalTime.of(19, 0))
    val eveningBase = if (tonight.isAfter(now)) tonight else LocalDate.now().plusDays(1).atTime(LocalTime.of(19, 0))
    val tomorrowMorning = LocalDate.now().plusDays(1).atTime(LocalTime.of(9, 0))
    return listOf(
        "In 2h" to Instant.now().plusSeconds(2 * 3600),
        "Tonight" to eveningBase.atZone(zone).toInstant(),
        "Tomorrow" to tomorrowMorning.atZone(zone).toInstant()
    )
}
