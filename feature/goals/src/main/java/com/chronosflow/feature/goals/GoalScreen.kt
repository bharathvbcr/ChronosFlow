package com.chronosflow.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.domain.model.GoalWithProgress
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosMetricTile
import com.chronosflow.core.ui.components.ChronosScreenBackdrop
import com.chronosflow.core.ui.components.ChronosScreenScaffold
import com.chronosflow.core.ui.components.ChronosSectionHeader
import com.chronosflow.core.ui.shell.LocalChronosShellBottomInset
import com.chronosflow.core.ui.theme.ChronosSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(
    viewModel: GoalViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    openAddSheet: Boolean = false,
    initialAddCapture: String? = null
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<GoalSheetTarget?>(null) }
    val normalizedCapture = initialAddCapture?.trim()?.takeIf(String::isNotBlank)
    var initialAddConsumed by rememberSaveable(openAddSheet, normalizedCapture) { mutableStateOf(false) }
    val shellBottomInset = LocalChronosShellBottomInset.current

    LaunchedEffect(openAddSheet, normalizedCapture, initialAddConsumed) {
        if (!openAddSheet || initialAddConsumed) return@LaunchedEffect
        sheetTarget = GoalSheetTarget.Add(prefillTitle = normalizedCapture)
        initialAddConsumed = true
    }

    val activeCount = goals.count { !it.goal.isCompleted }
    val completedCount = goals.count { it.goal.isCompleted }

    ChronosScreenScaffold(title = "Goals", onBack = onBack) { padding ->
        ChronosScreenBackdrop(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentPadding = PaddingValues(bottom = shellBottomInset + ChronosSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ChronosSectionHeader(
                        title = "Goals",
                        subtitle = "Long-term objectives that your daily tasks and habits roll up into."
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ChronosMetricTile("Active", activeCount.toString(), Modifier.weight(1f))
                        ChronosMetricTile("Completed", completedCount.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { sheetTarget = GoalSheetTarget.Add() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add goal", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (goals.isEmpty()) {
                    item {
                        ChronosEmptyState(
                            title = "No goals yet",
                            message = "Add a goal, then link tasks and habits so completing them moves you forward."
                        )
                    }
                } else {
                    items(goals, key = { it.goal.id }) { entry ->
                        GoalCard(
                            entry = entry,
                            onEdit = { sheetTarget = GoalSheetTarget.Edit(entry.goal) },
                            onIncrement = { viewModel.adjustProgress(entry.goal, 1) },
                            onDecrement = { viewModel.adjustProgress(entry.goal, -1) },
                            onToggleComplete = { viewModel.toggleComplete(entry.goal) }
                        )
                    }
                }
            }
        }
    }

    GoalFormSheet(
        target = sheetTarget,
        onDismiss = { sheetTarget = null },
        onConfirm = { title, description, category, targetValue, targetDate ->
            when (val current = sheetTarget) {
                is GoalSheetTarget.Edit -> viewModel.updateGoal(
                    current.goal, title, description, category, targetValue, targetDate
                )
                else -> viewModel.addGoal(title, description, category, targetValue, targetDate)
            }
            sheetTarget = null
        },
        onArchive = (sheetTarget as? GoalSheetTarget.Edit)?.let { edit ->
            {
                viewModel.deleteGoal(edit.goal)
                sheetTarget = null
            }
        }
    )
}

@Composable
private fun GoalCard(
    entry: GoalWithProgress,
    onEdit: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleComplete: () -> Unit
) {
    val goal = entry.goal
    ChronosListCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (goal.isCompleted) TextDecoration.LineThrough else null
                )
                Text(
                    text = buildString {
                        append(goal.category)
                        append(" • ")
                        append("${entry.totalProgress}/${goal.targetValue}")
                        goal.targetDate?.let { append(" • due $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleComplete) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (goal.isCompleted) "Mark active" else "Mark complete",
                    tint = if (goal.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit goal")
            }
        }
        LinearProgressIndicator(
            progress = { entry.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Manual progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDecrement) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease progress")
            }
            Text(
                text = goal.progressValue.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onIncrement) {
                Icon(Icons.Default.Add, contentDescription = "Increase progress")
            }
        }
    }
}
