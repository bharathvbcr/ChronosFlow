package com.ChronosFlow.VBCR.feature.goals

import com.ChronosFlow.VBCR.core.ui.components.ChronosIconButton

import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilledTonalButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.ChronosFlow.VBCR.core.domain.model.GoalDerivedProgress
import com.ChronosFlow.VBCR.core.domain.model.GoalWithProgress
import com.ChronosFlow.VBCR.core.ui.components.ChronosCommandPaletteAction
import com.ChronosFlow.VBCR.core.ui.components.ChronosEmptyState
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosMetricTile
import com.ChronosFlow.VBCR.core.ui.components.ChronosOptionChips
import com.ChronosFlow.VBCR.core.ui.components.ChronosScreenBackdrop
import com.ChronosFlow.VBCR.core.ui.components.ChronosScreenScaffold
import com.ChronosFlow.VBCR.core.ui.components.OneShotNavTrigger
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionHeader
import com.ChronosFlow.VBCR.core.ui.components.ChronosWarningBanner
import com.ChronosFlow.VBCR.core.ui.shell.LocalChronosShellBottomInset
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val GOAL_FILTER_ALL = "All"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(
    viewModel: GoalViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenCommandPalette: (() -> Unit)? = null,
    openAddSheet: Boolean = false,
    initialAddCapture: String? = null,
    navTargetGeneration: Int = 0
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<GoalSheetTarget?>(null) }
    val normalizedCapture = initialAddCapture?.trim()?.takeIf(String::isNotBlank)
    val shellBottomInset = LocalChronosShellBottomInset.current

    OneShotNavTrigger(openAddSheet, navTargetGeneration, normalizedCapture) {
        sheetTarget = GoalSheetTarget.Add(prefillTitle = normalizedCapture)
    }

    val activeGoals = goals.filter { !it.goal.isCompleted }
    val completedGoals = goals.filter { it.goal.isCompleted }

    // Only offer the category filter once goals span more than one category, so a
    // single-category list never shows a filter row with nothing to choose between.
    val categoriesInUse = remember(goals) { goalCategoriesInUse(goals) }
    var selectedCategory by rememberSaveable { mutableStateOf(GOAL_FILTER_ALL) }
    val effectiveCategory =
        if (selectedCategory == GOAL_FILTER_ALL || selectedCategory in categoriesInUse) {
            selectedCategory
        } else {
            GOAL_FILTER_ALL
        }
    val matchesFilter = { entry: GoalWithProgress ->
        effectiveCategory == GOAL_FILTER_ALL || entry.goal.category == effectiveCategory
    }
    val visibleActive = activeGoals.filter(matchesFilter)
    val visibleCompleted = completedGoals.filter(matchesFilter)
    val today = LocalDate.now()
    val overdueCount = goalsOverdueCount(goals, today)
    // Completed goals stay tucked behind a toggle so active work leads the page.
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    // The goal whose linked-work detail sheet is open (null = closed).
    var detailEntry by remember { mutableStateOf<GoalWithProgress?>(null) }
    val detailLinkedWork by viewModel.detailLinkedWork.collectAsStateWithLifecycle()
    val openGoalDetail: (GoalWithProgress) -> Unit = { entry ->
        detailEntry = entry
        viewModel.openGoalDetail(entry.goal.id)
    }

    ChronosScreenScaffold(
        title = "Goals",
        onBack = onBack,
        actions = { ChronosCommandPaletteAction(onOpenCommandPalette) }
    ) { padding ->
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
                        ChronosMetricTile("Active", activeGoals.size.toString(), Modifier.weight(1f))
                        ChronosMetricTile("Completed", completedGoals.size.toString(), Modifier.weight(1f))
                    }
                }
                if (overdueCount > 0) {
                    item {
                        ChronosWarningBanner(
                            title = "Overdue",
                            message = goalsOverdueMessage(overdueCount)
                        )
                    }
                }
                item {
                    ChronosFilledTonalButton(
                        onClick = { sheetTarget = GoalSheetTarget.Add() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add goal", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (categoriesInUse.size > 1) {
                    item {
                        ChronosOptionChips(
                            label = "Filter",
                            options = listOf(GOAL_FILTER_ALL) + categoriesInUse,
                            selected = effectiveCategory,
                            onSelected = { selectedCategory = it }
                        )
                    }
                }
                when {
                    goals.isEmpty() -> item {
                        ChronosEmptyState(
                            title = "No goals yet",
                            message = "Add a goal, then link tasks and habits so completing them moves you forward."
                        )
                    }
                    visibleActive.isEmpty() && visibleCompleted.isEmpty() -> item {
                        ChronosEmptyState(
                            title = "Nothing in $effectiveCategory",
                            message = "No goals match this category yet. Switch the filter or add a new goal."
                        )
                    }
                    else -> {
                        if (visibleActive.isNotEmpty()) {
                            item {
                                ChronosSectionHeader(
                                    title = "Active",
                                    subtitle = "${visibleActive.size} in progress"
                                )
                            }
                            items(visibleActive, key = { it.goal.id }) { entry ->
                                GoalCard(
                                    entry = entry,
                                    today = today,
                                    onOpenDetail = { openGoalDetail(entry) },
                                    onEdit = { sheetTarget = GoalSheetTarget.Edit(entry.goal) },
                                    onIncrement = { viewModel.adjustProgress(entry.goal, 1) },
                                    onDecrement = { viewModel.adjustProgress(entry.goal, -1) },
                                    onToggleComplete = { viewModel.toggleComplete(entry.goal) }
                                )
                            }
                        }
                        if (visibleCompleted.isNotEmpty()) {
                            item {
                                ChronosSectionHeader(
                                    title = "Completed",
                                    subtitle = "${visibleCompleted.size} achieved",
                                    trailing = {
                                        ChronosTextButton(onClick = { showCompleted = !showCompleted }) {
                                            Text(if (showCompleted) "Hide" else "Show")
                                        }
                                    }
                                )
                            }
                            if (showCompleted) {
                                items(visibleCompleted, key = { it.goal.id }) { entry ->
                                    GoalCard(
                                        entry = entry,
                                        today = today,
                                        onOpenDetail = { openGoalDetail(entry) },
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
        onArchive = if (sheetTarget is GoalSheetTarget.Edit) {
            {
                (sheetTarget as? GoalSheetTarget.Edit)?.let { viewModel.deleteGoal(it.goal) }
                sheetTarget = null
            }
        } else {
            null
        }
    )

    // Keep the detail header live as progress updates, falling back to the captured entry.
    val liveDetailEntry = detailEntry?.let { opened ->
        goals.firstOrNull { it.goal.id == opened.goal.id } ?: opened
    }
    GoalDetailSheet(
        entry = liveDetailEntry,
        linkedWork = detailLinkedWork,
        today = today,
        onDismiss = {
            detailEntry = null
            viewModel.closeGoalDetail()
        },
        onEdit = { goal ->
            detailEntry = null
            viewModel.closeGoalDetail()
            sheetTarget = GoalSheetTarget.Edit(goal)
        }
    )
}

@Composable
private fun GoalCard(
    entry: GoalWithProgress,
    today: LocalDate,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleComplete: () -> Unit
) {
    val goal = entry.goal
    val dueLabel = goalDueLabel(goal.targetDate, goal.isCompleted, today)
    val linkedLabel = goalLinkedWorkLabel(entry.derived)
    ChronosListCard(onClick = onOpenDetail) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (goal.isCompleted) TextDecoration.LineThrough else null
                )
                Text(
                    text = goal.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChronosIconButton(onClick = onToggleComplete) {
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
            ChronosIconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit goal")
            }
        }
        if (dueLabel != null || linkedLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dueLabel != null) {
                    GoalMetaPill(text = dueLabel.text, emphasized = dueLabel.emphasized)
                }
                if (linkedLabel != null) {
                    Text(
                        text = linkedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { goalProgressFraction(entry.totalProgress, goal.targetValue) },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = goalProgressSummary(entry.totalProgress, goal.targetValue),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
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
            ChronosIconButton(onClick = onDecrement) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease progress")
            }
            Text(
                text = goal.progressValue.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ChronosIconButton(onClick = onIncrement) {
                Icon(Icons.Default.Add, contentDescription = "Increase progress")
            }
        }
    }
}

@Composable
private fun GoalMetaPill(text: String, emphasized: Boolean) {
    val container = if (emphasized) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (emphasized) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Distinct goal categories currently in use, sorted for a stable filter row. */
internal fun goalCategoriesInUse(goals: List<GoalWithProgress>): List<String> =
    goals.map { it.goal.category }.distinct().sorted()

/** Active (incomplete) goals whose target date has already passed. */
internal fun goalsOverdueCount(goals: List<GoalWithProgress>, today: LocalDate): Int =
    goals.count { !it.goal.isCompleted && it.goal.targetDate?.isBefore(today) == true }

internal fun goalsOverdueMessage(count: Int): String =
    if (count == 1) {
        "1 active goal is past its target date."
    } else {
        "$count active goals are past their target date."
    }

internal data class GoalDueLabel(val text: String, val emphasized: Boolean)

/**
 * Due-date chip for a goal. Completed goals and goals without a target date show
 * nothing; an approaching or passed deadline is emphasized so it stands out.
 */
internal fun goalDueLabel(
    targetDate: LocalDate?,
    isCompleted: Boolean,
    today: LocalDate
): GoalDueLabel? {
    if (targetDate == null || isCompleted) return null
    val days = ChronoUnit.DAYS.between(today, targetDate)
    return when {
        days < 0L -> GoalDueLabel("Overdue", emphasized = true)
        days == 0L -> GoalDueLabel("Due today", emphasized = true)
        days <= 7L -> GoalDueLabel("Due in ${days}d", emphasized = true)
        else -> GoalDueLabel("Due $targetDate", emphasized = false)
    }
}

/** Hint surfacing progress contributed by linked tasks and habits, if any. */
internal fun goalLinkedWorkLabel(derived: GoalDerivedProgress): String? {
    val total = derived.completedTaskCount + derived.habitCompletionCount
    return if (total <= 0) null else "+$total from linked work"
}

/** Bar fill for a goal, clamped to 0..1; a non-positive target reads as empty. */
internal fun goalProgressFraction(progress: Int, target: Int): Float =
    if (target <= 0) 0f else (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f)

/**
 * Human-readable progress, e.g. "3 of 10 · 30%". Over-achieved counts are capped to
 * the target, and a goal without a measurable target falls back to a raw logged count.
 */
internal fun goalProgressSummary(progress: Int, target: Int): String =
    if (target <= 0) {
        "$progress logged"
    } else {
        val capped = progress.coerceIn(0, target)
        val percent = (goalProgressFraction(progress, target) * 100).roundToInt()
        "$capped of $target · $percent%"
    }
