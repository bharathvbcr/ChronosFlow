package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.notifications.TaskContextCommandKind
import com.ChronosFlow.VBCR.core.notifications.launchAppTarget
import com.ChronosFlow.VBCR.core.notifications.launchTaskContextCommand
import com.ChronosFlow.VBCR.core.ui.components.ChronosAssistChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionTitle
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickContextActionUiModel
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemKind
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemUiModel
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemsUiState
import com.ChronosFlow.VBCR.feature.daydial.model.dayQuickContextActionLabel
import com.ChronosFlow.VBCR.feature.daydial.model.dayQuickEmptyActionLabel
import com.ChronosFlow.VBCR.feature.daydial.model.dayQuickGroupOpenActionLabel
import com.ChronosFlow.VBCR.feature.daydial.model.dayQuickGroupSummaryLabel
import com.ChronosFlow.VBCR.feature.daydial.model.dayQuickPrimaryActionLabel
import com.ChronosFlow.VBCR.feature.daydial.model.dayQuickSecondaryActionLabel

@Composable
internal fun DayQuickItemsSection(
    quickItems: DayQuickItemsUiState,
    modifier: Modifier = Modifier,
    title: String = "Today's items",
    habitsEnabled: Boolean = true,
    medicationEnabled: Boolean = true,
    onOpenTasks: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onOpenMedication: () -> Unit = {},
    onTaskDone: (String) -> Unit,
    onHabitDone: (String) -> Unit,
    onMedicationTaken: (String, Int?) -> Unit,
    onMedicationMissed: (String, Int?) -> Unit,
    showContextualActions: Boolean = false,
    onContextActionFailed: (String) -> Unit = {}
) {
    val visibleQuickItemsEmpty = quickItems.tasks.isEmpty() &&
        (!habitsEnabled || quickItems.habits.isEmpty()) &&
        (!medicationEnabled || quickItems.medications.isEmpty())

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        ChronosSectionTitle(title = title)
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            if (visibleQuickItemsEmpty) {
                DayQuickEmptyState(
                    habitsEnabled = habitsEnabled,
                    medicationEnabled = medicationEnabled,
                    onOpenTasks = onOpenTasks,
                    onOpenHabits = onOpenHabits,
                    onOpenMedication = onOpenMedication
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                    DayQuickItemGroup(
                        label = "Tasks",
                        kind = DayQuickItemKind.TASK,
                        items = quickItems.tasks,
                        onOpenGroup = onOpenTasks,
                        onTaskDone = onTaskDone,
                        onHabitDone = onHabitDone,
                        onMedicationTaken = onMedicationTaken,
                        onMedicationMissed = onMedicationMissed,
                        showContextualActions = showContextualActions,
                        onContextActionFailed = onContextActionFailed
                    )
                    if (habitsEnabled) {
                        DayQuickItemGroup(
                            label = "Habits",
                            kind = DayQuickItemKind.HABIT,
                            items = quickItems.habits,
                            onOpenGroup = onOpenHabits,
                            onTaskDone = onTaskDone,
                            onHabitDone = onHabitDone,
                            onMedicationTaken = onMedicationTaken,
                            onMedicationMissed = onMedicationMissed,
                            showContextualActions = showContextualActions,
                            onContextActionFailed = onContextActionFailed
                        )
                    }
                    if (medicationEnabled) {
                        DayQuickItemGroup(
                            label = "Meds",
                            kind = DayQuickItemKind.MEDICATION,
                            items = quickItems.medications,
                            onOpenGroup = onOpenMedication,
                            onTaskDone = onTaskDone,
                            onHabitDone = onHabitDone,
                            onMedicationTaken = onMedicationTaken,
                            onMedicationMissed = onMedicationMissed,
                            showContextualActions = false,
                            onContextActionFailed = onContextActionFailed
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayQuickEmptyState(
    habitsEnabled: Boolean,
    medicationEnabled: Boolean,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenMedication: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
        Text(
            text = dayQuickEmptyMessage(habitsEnabled, medicationEnabled),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            DayQuickEmptyActionButton(
                label = "Tasks",
                kind = DayQuickItemKind.TASK,
                onClick = onOpenTasks
            )
            if (habitsEnabled) {
                DayQuickEmptyActionButton(
                    label = "Habits",
                    kind = DayQuickItemKind.HABIT,
                    onClick = onOpenHabits
                )
            }
            if (medicationEnabled) {
                DayQuickEmptyActionButton(
                    label = "Meds",
                    kind = DayQuickItemKind.MEDICATION,
                    onClick = onOpenMedication
                )
            }
        }
    }
}

private fun dayQuickEmptyMessage(habitsEnabled: Boolean, medicationEnabled: Boolean): String = when {
    habitsEnabled && medicationEnabled -> "No tasks, habits, or meds for this date"
    habitsEnabled -> "No tasks or habits for this date"
    medicationEnabled -> "No tasks or meds for this date"
    else -> "No tasks for this date"
}

@Composable
private fun DayQuickEmptyActionButton(
    label: String,
    kind: DayQuickItemKind,
    onClick: () -> Unit
) {
    ChronosOutlinedButton(
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = dayQuickEmptyActionLabel(kind)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Icon(
            imageVector = dayQuickItemIcon(kind),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun DayQuickItemGroup(
    label: String,
    kind: DayQuickItemKind,
    items: List<DayQuickItemUiModel>,
    onOpenGroup: () -> Unit,
    onTaskDone: (String) -> Unit,
    onHabitDone: (String) -> Unit,
    onMedicationTaken: (String, Int?) -> Unit,
    onMedicationMissed: (String, Int?) -> Unit,
    showContextualActions: Boolean,
    onContextActionFailed: (String) -> Unit
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dayQuickGroupSummaryLabel(items),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChronosTextButton(
                onClick = onOpenGroup,
                modifier = Modifier.semantics {
                    contentDescription = dayQuickGroupOpenActionLabel(kind)
                }
            ) {
                Text("View all")
            }
        }
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            DayQuickItemRow(
                item = item,
                onPrimary = {
                    when (item.kind) {
                        DayQuickItemKind.TASK -> onTaskDone(item.id)
                        DayQuickItemKind.HABIT -> onHabitDone(item.id)
                        DayQuickItemKind.MEDICATION -> onMedicationTaken(item.baseMedicationPlanId(), item.scheduledMinuteOfDay)
                    }
                },
                onSecondary = {
                    if (item.kind == DayQuickItemKind.MEDICATION) {
                        onMedicationMissed(item.baseMedicationPlanId(), item.scheduledMinuteOfDay)
                    }
                },
                showContextualActions = showContextualActions,
                onContextActionFailed = onContextActionFailed
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayQuickItemRow(
    item: DayQuickItemUiModel,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    showContextualActions: Boolean,
    onContextActionFailed: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = dayQuickItemIcon(item.kind),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChronosAssistChip(
                onClick = {},
                label = { Text(item.status) },
                leadingIcon = if (item.isDone) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
                enabled = false,
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = if (item.isDone) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    disabledLabelColor = if (item.isDone) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            ChronosButton(
                onClick = onPrimary,
                enabled = !item.isDone,
                modifier = Modifier.semantics {
                    contentDescription = dayQuickPrimaryActionLabel(item)
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(if (item.kind == DayQuickItemKind.MEDICATION) "Taken" else "Done")
            }
            if (item.kind == DayQuickItemKind.MEDICATION) {
                ChronosOutlinedButton(
                    onClick = onSecondary,
                    enabled = !item.isDone,
                    modifier = Modifier.semantics {
                        contentDescription = dayQuickSecondaryActionLabel(item)
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Missed")
                }
            }
            if (showContextualActions) {
                item.contextAction?.let { action ->
                    DayQuickContextActionPill(
                        item = item,
                        action = action,
                        onClick = {
                            val launched = launchDayQuickContextAction(context, action)
                            if (!launched) {
                                onContextActionFailed("No app can open ${action.label}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayQuickContextActionPill(
    item: DayQuickItemUiModel,
    action: DayQuickContextActionUiModel,
    onClick: () -> Unit
) {
    ChronosAssistChip(
        onClick = onClick,
        label = { Text(action.label) },
        leadingIcon = {
            Icon(
                imageVector = dayQuickContextActionIcon(action),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = Modifier.semantics {
            contentDescription = dayQuickContextActionLabel(item)
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

private fun DayQuickItemUiModel.baseMedicationPlanId(): String =
    id.substringBefore(":")

private fun dayQuickItemIcon(kind: DayQuickItemKind): ImageVector = when (kind) {
    DayQuickItemKind.TASK -> Icons.Default.Checklist
    DayQuickItemKind.HABIT -> Icons.Default.Favorite
    DayQuickItemKind.MEDICATION -> Icons.Default.Medication
}

private fun launchDayQuickContextAction(context: Context, action: DayQuickContextActionUiModel): Boolean {
    action.taskCommand?.let { command -> return launchTaskContextCommand(context, command) }
    action.appLaunchTarget?.let { target -> return launchAppTarget(context, target) }
    return false
}

private fun dayQuickContextActionIcon(action: DayQuickContextActionUiModel): ImageVector {
    return when (action.taskCommand?.kind) {
        TaskContextCommandKind.CALL -> Icons.Default.Call
        TaskContextCommandKind.EMAIL -> Icons.Default.Email
        TaskContextCommandKind.LINK -> Icons.Default.Link
        TaskContextCommandKind.MAP -> Icons.Default.Map
        TaskContextCommandKind.APP -> Icons.Default.Apps
        TaskContextCommandKind.IMAGE -> Icons.Default.Image
        TaskContextCommandKind.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
        TaskContextCommandKind.SCHEDULE,
        TaskContextCommandKind.EDIT,
        TaskContextCommandKind.COMPLETE -> Icons.Default.Checklist
        null -> Icons.Default.Apps
    }
}
