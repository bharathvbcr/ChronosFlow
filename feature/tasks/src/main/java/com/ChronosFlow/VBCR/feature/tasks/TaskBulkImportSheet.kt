package com.ChronosFlow.VBCR.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosCheckbox
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import com.ChronosFlow.VBCR.core.ui.shell.ChronosModalBottomSheet

/**
 * Reviews a batch of task titles shared/imported from another app before saving. Every candidate is
 * checked by default; the user can deselect any line, then import the rest in one tap. Confirms with
 * the selected titles via [onImport]. See [parseSharedTaskImport]/[splitSharedTaskLines].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBulkImportSheet(
    candidates: List<String>,
    onDismiss: () -> Unit,
    onImport: (List<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val checked = remember(candidates) { mutableStateListOf<Boolean>().apply { addAll(candidates.map { true }) } }
    val selectedCount by remember {
        derivedStateOf { checked.count { it } }
    }

    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        chromeTag = "task-bulk-import-sheet"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Import tasks",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${candidates.size} items shared from another app. Pick which to add.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(candidates) { index, title ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .chronosHapticClick(
                                onClick = { checked[index] = !checked[index] },
                                onClickLabel = bulkImportToggleLabel(title, checked[index]),
                                role = Role.Checkbox
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ChronosCheckbox(
                            checked = checked[index],
                            onCheckedChange = { checked[index] = it }
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChronosTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                ChronosButton(
                    onClick = {
                        val selected = candidates.filterIndexed { index, _ -> checked.getOrElse(index) { false } }
                        if (selected.isNotEmpty()) onImport(selected)
                    },
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (selectedCount == 1) "Import 1 task" else "Import $selectedCount tasks",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

internal fun bulkImportToggleLabel(title: String, currentlyChecked: Boolean): String =
    if (currentlyChecked) "Exclude $title" else "Include $title"
