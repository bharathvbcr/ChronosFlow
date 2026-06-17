package com.chronosflow.feature.habits

import com.chronosflow.core.ui.components.ChronosIconButton

import com.chronosflow.core.ui.components.ChronosButton
import com.chronosflow.core.ui.components.ChronosTextButton
import com.chronosflow.core.ui.components.ChronosOutlinedButton
import com.chronosflow.core.ui.components.ChronosFilledTonalButton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import com.chronosflow.core.ui.motion.chronosHapticClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.chronosflow.core.ui.shell.ChronosModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.ai.HabitAssistRequest
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosLinkOption
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosMetricTile
import com.chronosflow.core.ui.components.ChronosConfirmBottomSheet
import com.chronosflow.core.ui.components.ChronosQuickAddChips
import com.chronosflow.core.ui.components.ChronosCommandPaletteAction
import com.chronosflow.core.ui.components.ChronosPageHeader
import com.chronosflow.core.ui.components.ChronosScreenScaffold
import com.chronosflow.core.ui.components.OneShotNavTrigger
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.shell.LocalChronosShellBottomInset
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitScreen(
    viewModel: HabitViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenCommandPalette: (() -> Unit)? = null,
    openAddSheet: Boolean = false,
    initialAddCapture: String? = null,
    navTargetGeneration: Int = 0
) {
    val activeHabits by viewModel.activeHabits.collectAsStateWithLifecycle()
    val allHabits by viewModel.allHabits.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val goalOptions = remember(goals) { goals.map { ChronosLinkOption(it.id, it.title) } }
    val recentHistoryTemplateIds by viewModel.recentHistoryTemplateIds.collectAsStateWithLifecycle()
    val streaks by viewModel.streaks.collectAsStateWithLifecycle()
    val completionTrend by viewModel.completionTrend.collectAsStateWithLifecycle()
    val repairSuggestions by viewModel.repairSuggestions.collectAsStateWithLifecycle()
    val repairAssistSnapshot by viewModel.repairAssistSnapshot.collectAsStateWithLifecycle()
    val assistState by viewModel.assistState.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<HabitSheetTarget?>(null) }
    val normalizedInitialAddCapture = initialAddCapture?.trim()?.takeIf(String::isNotBlank)
    var habitToArchive by remember { mutableStateOf<Habit?>(null) }
    var habitContextTarget by remember { mutableStateOf<Habit?>(null) }
    val nowMinute = remember { LocalTime.now().hour * 60 + LocalTime.now().minute }
    val shellBottomInset = LocalChronosShellBottomInset.current

    OneShotNavTrigger(openAddSheet, navTargetGeneration, normalizedInitialAddCapture) {
        sheetTarget = HabitSheetTarget.Add(prefillTitle = normalizedInitialAddCapture)
        normalizedInitialAddCapture?.let { capture ->
            viewModel.requestHabitAssist(
                HabitAssistRequest(
                    title = capture,
                    cadence = "Daily",
                    startMinute = 8 * 60,
                    endMinute = 20 * 60,
                    difficulty = 2,
                    isBundled = false
                )
            )
        }
    }

    ChronosScreenScaffold(
        title = "Habits",
        onBack = onBack,
        actions = { ChronosCommandPaletteAction(onOpenCommandPalette) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + shellBottomInset + ChronosSpacing.Medium + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ChronosPageHeader(
                    title = "Habit tracking",
                    subtitle = "Track repeatable windows and keep streaks visible on the day plan.",
                    icon = Icons.Default.Favorite
                )
            }
            item {
                val doneToday = activeHabits.count { it.lastCompletedDate == LocalDate.now() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ChronosMetricTile("Active", activeHabits.size.toString(), Modifier.weight(1f))
                    ChronosMetricTile(
                        "Best streak",
                        (streaks.maxOfOrNull { it.streakCount } ?: 0).toString(),
                        Modifier.weight(1f)
                    )
                    ChronosMetricTile(
                        "Done today",
                        doneToday.toString(),
                        Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            item {
                ChronosFilledTonalButton(
                    onClick = { sheetTarget = HabitSheetTarget.Add() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add habit", fontWeight = FontWeight.SemiBold)
                }
            }
            item { HabitConsistencyCard(trend = completionTrend) }
            item { HabitStreakChart(streaks = streaks) }
            if (repairSuggestions.isNotEmpty()) {
                item(key = "habit_repair_panel") {
                    HabitRepairPanel(
                        suggestions = repairSuggestions,
                        assistSnapshot = repairAssistSnapshot,
                        onComplete = { suggestion -> viewModel.completeHabit(suggestion.habit) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            if (activeHabits.isEmpty()) {
                item(key = "habit_empty_state") {
                    ChronosEmptyState(
                        title = "No active habits",
                        message = "Add one habit to start building a daily streak.",
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "habit_empty_quick_add") {
                    ChronosQuickAddChips(
                        label = "Start quickly",
                        options = listOf("Morning walk", "Meditation", "Read 20 min", "Hydrate"),
                        onSelect = { title ->
                            sheetTarget = HabitSheetTarget.Add(prefillTitle = title)
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                items(activeHabits, key = { it.id }) { habit ->
                    HabitRow(
                        modifier = Modifier.animateItem(),
                        habit = habit,
                        nowMinute = nowMinute,
                        onComplete = { viewModel.completeHabit(habit) },
                        onPause = { viewModel.pauseHabit(habit, days = 1) },
                        onResume = { viewModel.resumeHabit(habit) },
                        onSkip = { viewModel.skipHabitToday(habit) },
                        onDefer = { viewModel.deferHabit(habit, minutes = 60) },
                        onEdit = { sheetTarget = HabitSheetTarget.Edit(habit) },
                        onArchive = { habitToArchive = habit },
                        onOpenContext = { habitContextTarget = habit }
                    )
                }
            }
        }
    }

    HabitFormSheet(
        target = sheetTarget,
        onDismiss = { sheetTarget = null },
        goalOptions = goalOptions,
        initialGoalId = (sheetTarget as? HabitSheetTarget.Edit)?.habit?.goalId,
        onConfirm = { title, cadence, start, end, difficulty, isBundled, schedule, launchTarget, goalId ->
            when (val target = sheetTarget) {
                is HabitSheetTarget.Edit -> viewModel.updateHabit(
                    target.habit,
                    title,
                    cadence,
                    start,
                    end,
                    difficulty,
                    isBundled,
                    schedule,
                    launchTarget,
                    goalId
                )
                is HabitSheetTarget.Add -> viewModel.addHabit(
                    title,
                    cadence,
                    start,
                    end,
                    difficulty,
                    isBundled,
                    schedule,
                    launchTarget,
                    goalId
                )
                null -> Unit
            }
            sheetTarget = null
        },
        historyTemplates = buildHabitHistoryTemplates(
            habits = allHabits,
            currentHabitId = (sheetTarget as? HabitSheetTarget.Edit)?.habit?.id
        ),
        recentHistoryIds = recentHistoryTemplateIds,
        onHistoryTemplateSelected = viewModel::rememberHistoryTemplateSelection,
        onCompleteToday = { habit ->
            viewModel.completeHabit(habit)
            sheetTarget = null
        },
        onArchive = { habit -> habitToArchive = habit },
        onDuplicate = { habit ->
            viewModel.duplicateHabit(habit)
            sheetTarget = null
        },
        assistState = assistState,
        onRequestAssist = viewModel::requestHabitAssist,
        onClearAssist = viewModel::clearHabitAssist
    )

    habitContextTarget?.let { habit ->
        HabitContextActionSheet(
            habit = habit,
            onDismiss = { habitContextTarget = null },
            onComplete = {
                viewModel.completeHabit(habit)
                habitContextTarget = null
            },
            onPause = {
                viewModel.pauseHabit(habit, days = 1)
                habitContextTarget = null
            },
            onResume = {
                viewModel.resumeHabit(habit)
                habitContextTarget = null
            },
            onSkip = {
                viewModel.skipHabitToday(habit)
                habitContextTarget = null
            },
            onDefer = {
                viewModel.deferHabit(habit, minutes = 60)
                habitContextTarget = null
            },
            onEdit = {
                sheetTarget = HabitSheetTarget.Edit(habit)
                habitContextTarget = null
            },
            onArchive = {
                habitToArchive = habit
                habitContextTarget = null
            }
        )
    }

    habitToArchive?.let { habit ->
        ChronosConfirmBottomSheet(
            visible = true,
            title = habitArchiveConfirmTitle(habit),
            message = "\"${habit.title}\" will be hidden from your active list. Streak history stays saved.",
            confirmLabel = "Archive",
            onConfirm = {
                viewModel.archiveHabit(habit)
                habitToArchive = null
                sheetTarget = null
            },
            onDismiss = { habitToArchive = null }
        )
    }
}

internal fun habitArchiveConfirmTitle(habit: Habit): String {
    val habitLabel = habit.title.ifBlank { "this habit" }
    return "Archive \"$habitLabel\"?"
}

@Composable
private fun HabitRepairPanel(
    suggestions: List<HabitRepairSuggestion>,
    assistSnapshot: com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot?,
    onComplete: (HabitRepairSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Missed-habit repair",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            assistSnapshot?.let { snapshot ->
                com.chronosflow.core.ui.components.GenAiAssistBanner(
                    title = snapshot.bannerTitle,
                    message = snapshot.bannerMessage,
                    ready = snapshot.isReady
                )
            }
            suggestions.take(3).forEach { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            suggestion.habit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${suggestion.reason}. ${formatDisplayMinute(suggestion.suggestedStartMinute)}-${formatDisplayMinute(suggestion.suggestedEndMinute)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = com.chronosflow.core.ai.genai.GenAiAssistCopy.routineAssistSourceLabel(suggestion.source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    ChronosFilledTonalButton(
                        onClick = { onComplete(suggestion) },
                        modifier = Modifier.semantics {
                            contentDescription = habitRepairCompleteActionLabel(suggestion)
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Complete")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitRow(
    modifier: Modifier = Modifier,
    habit: Habit,
    nowMinute: Int,
    onComplete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onDefer: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onOpenContext: () -> Unit
) {
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val status = habit.statusLabel(nowMinute)
    val isDue = status == "Due now"
    val isDone = habit.lastCompletedDate == LocalDate.now()
    val schedule = habit.schedule
    val isPaused = schedule?.pausedUntil?.let { !it.isBefore(LocalDate.now()) } == true
    val skippedToday = schedule?.skipDate == LocalDate.now()
    val deferredMinute = schedule?.deferUntilMinuteOfDay
    val adherence = (habit.analytics.adherenceRate * 100).toInt()
    val recentEventSummary = habit.recentEvents.firstOrNull()?.let { event ->
        "${event.type.name.lowercase().replaceFirstChar(Char::uppercase)} · ${event.eventDate}"
    }

    ChronosListCard(
        modifier = modifier
            .fillMaxWidth()
            .chronosHapticClick(
                onClick = onOpenContext,
                onClickLabel = habitContextActionLabel(habit),
                role = Role.Button
            )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = habit.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChronosIconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = habitEditActionLabel(habit))
                    }
                    ChronosIconButton(onClick = onArchive) {
                        Icon(Icons.Default.Archive, contentDescription = habitArchiveActionLabel(habit))
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                status?.let { HabitStatusPill(label = it, emphasized = isDue, success = isDone) }
                if (habit.isBundled) {
                    HabitStatusPill(label = "On day plan", emphasized = false, success = false)
                }
                HabitStatusPill(label = habit.cadence, emphasized = false, success = false)
                val currentStreak = habit.analytics.currentStreak.takeIf { it > 0 } ?: habit.streakCount
                val milestone = habitStreakMilestoneLabel(currentStreak)
                if (milestone != null) {
                    HabitStatusPill(label = milestone, emphasized = true, success = true)
                } else {
                    HabitStatusPill(
                        label = "Streak $currentStreak",
                        emphasized = currentStreak > 0,
                        success = currentStreak > 0
                    )
                }
                HabitStatusPill(label = "Adherence $adherence%", emphasized = adherence >= 80, success = adherence >= 80)
                habit.analytics.bestCompletionMinuteOfDay?.let { minute ->
                    HabitStatusPill(label = "Best ${formatDisplayMinute(minute)}", emphasized = false, success = false)
                }
                if (isPaused) {
                    HabitStatusPill(label = "Paused", emphasized = false, success = false)
                }
                if (skippedToday) {
                    HabitStatusPill(label = "Skipped today", emphasized = false, success = false)
                }
            }
            Text(
                "${formatDisplayMinute(habit.windowStartMinute)} – ${formatDisplayMinute(habit.windowEndMinute)} · difficulty ${habit.difficulty}/5",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HabitWeekStrip(habit = habit)
            recentEventSummary?.let { summary ->
                Text(
                    text = "Recent: $summary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            deferredMinute?.let { minute ->
                Text(
                    text = "Deferred until ${formatDisplayMinute(minute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChronosButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = habitCompleteActionLabel(habit) },
                    enabled = !isDone && !isPaused && !skippedToday
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (isDone) "Completed today" else "Complete")
                }
                AnimatedContent(
                    targetState = isPaused,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val spec = ChronosValueAnimationFactory.selection<Float>(reduceMotion)
                        fadeIn(spec) togetherWith fadeOut(spec)
                    },
                    label = "habitPauseResumeAction"
                ) { paused ->
                    if (paused) {
                        ChronosFilledTonalButton(
                            onClick = onResume,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = habitResumeActionLabel(habit) }
                        ) {
                            Text("Resume")
                        }
                    } else {
                        ChronosOutlinedButton(
                            onClick = onOpenContext,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = habitMoreActionLabel(habit) }
                        ) {
                            Text("More")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitContextActionSheet(
    habit: Habit,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onDefer: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val isDone = habit.lastCompletedDate == LocalDate.now()
    val isPaused = habit.schedule?.pausedUntil?.let { !it.isBefore(LocalDate.now()) } == true
    val skippedToday = habit.schedule?.skipDate == LocalDate.now()
    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        chromeTag = "habit-context-sheet"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                habit.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${formatDisplayMinute(habit.windowStartMinute)} - ${formatDisplayMinute(habit.windowEndMinute)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChronosButton(
                onClick = onComplete,
                enabled = !isDone && !isPaused && !skippedToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = habitCompleteActionLabel(habit) }
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(habitCompleteActionLabel(habit))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChronosFilledTonalButton(
                    onClick = onDefer,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = habitDeferActionLabel(habit) },
                    enabled = !isDone && !isPaused
                ) {
                    Text(habitDeferActionLabel(habit))
                }
                ChronosFilledTonalButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = habitSkipActionLabel(habit) },
                    enabled = !isDone && !isPaused && !skippedToday
                ) {
                    Text(habitSkipActionLabel(habit))
                }
            }
            if (isPaused) {
                ChronosFilledTonalButton(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = habitResumeActionLabel(habit) }
                ) {
                    Text(habitResumeActionLabel(habit))
                }
            } else {
                ChronosFilledTonalButton(
                    onClick = onPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = habitPauseActionLabel(habit) }
                ) {
                    Text(habitPauseActionLabel(habit))
                }
            }
            ChronosOutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = habitEditActionLabel(habit) }
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(habitEditActionLabel(habit))
            }
            ChronosTextButton(
                onClick = onArchive,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = habitArchiveActionLabel(habit) }
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(habitArchiveActionLabel(habit))
            }
        }
    }
}

private val habitStreakMilestoneTiers = listOf(365, 100, 50, 30, 14, 7)

/**
 * A celebratory label for the highest streak milestone [streak] has reached, or null below the
 * first tier (7 days). Surfaced as an emphasized pill so a strong streak feels like an achievement.
 */
internal fun habitStreakMilestoneLabel(streak: Int): String? {
    val tier = habitStreakMilestoneTiers.firstOrNull { streak >= it } ?: return null
    return "🔥 $tier-day streak"
}

internal fun habitContextActionLabel(habit: Habit): String = "Open actions for ${habit.title}"

internal fun habitMoreActionLabel(habit: Habit): String = "More actions for ${habit.title}"

internal fun habitEditActionLabel(habit: Habit): String = "Edit ${habit.title}"

internal fun habitArchiveActionLabel(habit: Habit): String = "Archive ${habit.title}"

internal fun habitCompleteActionLabel(habit: Habit): String {
    return if (habit.lastCompletedDate == LocalDate.now()) {
        "${habit.title} completed today"
    } else {
        "Complete ${habit.title}"
    }
}

internal fun habitRepairCompleteActionLabel(suggestion: HabitRepairSuggestion): String {
    return "Complete repair for ${suggestion.habit.title}"
}

internal fun habitDeferActionLabel(habit: Habit): String = "Defer ${habit.title} for 1 hour"

internal fun habitSkipActionLabel(habit: Habit): String = "Skip ${habit.title} today"

internal fun habitPauseActionLabel(habit: Habit): String = "Pause ${habit.title} for 1 day"

internal fun habitResumeActionLabel(habit: Habit): String = "Resume ${habit.title}"

@Composable
private fun HabitStatusPill(
    label: String,
    emphasized: Boolean,
    success: Boolean
) {
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val targetContainer = when {
        success -> MaterialTheme.colorScheme.tertiaryContainer
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val targetContent = when {
        success -> MaterialTheme.colorScheme.onTertiaryContainer
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotion),
        label = "habitStatusPillContainer"
    )
    val content by animateColorAsState(
        targetValue = targetContent,
        animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotion),
        label = "habitStatusPillContent"
    )
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized || success) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
