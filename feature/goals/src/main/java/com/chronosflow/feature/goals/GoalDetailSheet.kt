package com.chronosflow.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.chronosflow.core.domain.model.Goal
import com.chronosflow.core.domain.model.GoalLinkedHabit
import com.chronosflow.core.domain.model.GoalLinkedTask
import com.chronosflow.core.domain.model.GoalLinkedWork
import com.chronosflow.core.domain.model.GoalWithProgress
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosFormBottomSheet
import com.chronosflow.core.ui.components.ChronosSectionHeader
import java.time.LocalDate

@Composable
internal fun GoalDetailSheet(
    entry: GoalWithProgress?,
    linkedWork: GoalLinkedWork,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: (Goal) -> Unit
) {
    if (entry == null) return
    val goal = entry.goal
    val completedTaskCount = linkedWork.tasks.count { it.isCompleted }

    ChronosFormBottomSheet(
        visible = true,
        title = goal.title,
        subtitle = goalProgressSummary(entry.totalProgress, goal.targetValue),
        confirmLabel = "Edit goal",
        dismissLabel = "Close",
        onDismiss = onDismiss,
        onConfirm = { onEdit(goal) }
    ) {
        LinearProgressIndicator(
            progress = { goalProgressFraction(entry.totalProgress, goal.targetValue) },
            modifier = Modifier.fillMaxWidth()
        )

        goal.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (linkedWork.isEmpty) {
            ChronosEmptyState(
                title = "No linked work yet",
                message = "Link tasks and habits to this goal from their edit screens — completing them then moves this goal forward automatically."
            )
        }

        if (linkedWork.tasks.isNotEmpty()) {
            ChronosSectionHeader(
                title = "Tasks",
                subtitle = "$completedTaskCount of ${linkedWork.tasks.size} complete"
            )
            linkedWork.tasks.forEach { task ->
                GoalLinkedTaskRow(task = task, today = today)
            }
        }

        if (linkedWork.habits.isNotEmpty()) {
            ChronosSectionHeader(
                title = "Habits",
                subtitle = "${linkedWork.habits.size} linked"
            )
            linkedWork.habits.forEach { habit ->
                GoalLinkedHabitRow(habit = habit)
            }
        }
    }
}

@Composable
private fun GoalLinkedTaskRow(task: GoalLinkedTask, today: LocalDate) {
    val due = goalDueLabel(task.targetDate, task.isCompleted, today)
    val subtitle = listOfNotNull(taskPriorityLabel(task.priority), due?.text)
        .joinToString(" · ")
        .ifBlank { null }
    GoalLinkedRow(
        leading = {
            Icon(
                imageVector = if (task.isCompleted) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.RadioButtonUnchecked
                },
                contentDescription = if (task.isCompleted) "Completed" else "Not completed",
                tint = if (task.isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        title = task.title,
        titleDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
        subtitle = subtitle
    )
}

@Composable
private fun GoalLinkedHabitRow(habit: GoalLinkedHabit) {
    val streakText = when {
        !habit.isActive -> "Paused"
        habit.streakCount > 0 -> "${habit.streakCount}-day streak · ${cadenceLabel(habit.cadence)}"
        else -> cadenceLabel(habit.cadence)
    }
    GoalLinkedRow(
        leading = {
            Icon(
                imageVector = Icons.Default.Loop,
                contentDescription = null,
                tint = if (habit.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        title = habit.title,
        titleDecoration = null,
        subtitle = streakText
    )
}

@Composable
private fun GoalLinkedRow(
    leading: @Composable () -> Unit,
    title: String,
    titleDecoration: TextDecoration?,
    subtitle: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = titleDecoration,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun taskPriorityLabel(priority: Int): String? = when {
    priority >= 2 -> "Urgent"
    priority == 1 -> "High"
    else -> null
}

/** Turns a raw cadence token like "DAILY" into a readable "Daily". */
internal fun cadenceLabel(cadence: String): String =
    cadence.trim().lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "Habit" }
