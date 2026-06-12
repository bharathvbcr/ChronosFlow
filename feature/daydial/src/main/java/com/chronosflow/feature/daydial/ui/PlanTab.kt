package com.chronosflow.feature.daydial.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.components.formatDurationLabel
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.ChronosTransitionFactory
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.core.ui.theme.categoryColor
import com.chronosflow.feature.daydial.TimeBlockUiModel
import com.chronosflow.feature.daydial.TimeRangeUi
import com.chronosflow.feature.daydial.isAllDayCalendarImport
import com.chronosflow.feature.daydial.model.DayQuickItemKind
import com.chronosflow.feature.daydial.model.DayQuickItemUiModel
import com.chronosflow.feature.daydial.model.DayQuickItemsUiState
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.TemplateBlueprint
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAY_IN_MINUTES = 1440
private const val LARGE_GAP_THRESHOLD_MINUTES = 45
private const val PLAN_DATE_SCROLL_RADIUS_DAYS = 7
private const val PLAN_DATE_SELECTED_VISIBLE_OFFSET = 2
private val planDateChipWeekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val planDateChipDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val planDateChipFullFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
private val planCalendarMonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val planCalendarWeekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun PlanTab(
    timeBlocks: List<TimeBlockUiModel>,
    suggestedBlocks: List<TimeBlockUiModel>,
    templates: List<TemplateBlueprint>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onAiPlanRequested: () -> Unit,
    onApplyAiSuggestions: () -> Unit,
    onAcceptAiSuggestion: (String) -> Unit,
    onRejectAiSuggestion: (String) -> Unit,
    onRebalanceDay: () -> Unit,
    onExplainPlan: () -> Unit,
    onRepairConflicts: (String) -> Unit = {},
    onOpenAiSheet: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenMedication: () -> Unit,
    habitsFeatureEnabled: Boolean = true,
    medicationFeatureEnabled: Boolean = true,
    quickItems: DayQuickItemsUiState = DayQuickItemsUiState(),
    onQuickTaskDone: (String) -> Unit = {},
    onQuickHabitDone: (String) -> Unit = {},
    onQuickMedicationTaken: (String, Int?) -> Unit = { _, _ -> },
    onQuickMedicationMissed: (String, Int?) -> Unit = { _, _ -> },
    onCreateBlock: () -> Unit,
    onBlockSelected: (String?) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onDuplicateBlock: (String) -> Unit,
    onApplyTemplate: (TemplateBlueprint) -> Unit,
    onFillGaps: () -> Unit,
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp
) {
    val sortedBlocks = remember(timeBlocks) { timeBlocks.sortedBy { it.startMinuteOfDay } }
    val largeGaps = remember(sortedBlocks) { findLargeGaps(sortedBlocks, LARGE_GAP_THRESHOLD_MINUTES) }
    val overlaps = remember(sortedBlocks) { findOverlaps(sortedBlocks) }
    var fullCalendarVisible by rememberSaveable { mutableStateOf(false) }
    val timelineBottomPadding = dayDialScrollableBottomPadding(
        contentBottomPadding = contentBottomPadding,
        pageBottomPadding = ChronosSpacing.Standard
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ChronosSpacing.Standard,
            top = contentTopPadding + ChronosSpacing.Standard,
            end = ChronosSpacing.Standard,
            bottom = timelineBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Standard)
    ) {
        item {
            DayDialPageHeader(
                title = DayDialTab.PLAN.label,
                subtitle = dayDialPrimaryPageSubtitle(DayDialTab.PLAN),
                icon = DayDialTab.PLAN.icon
            )
        }

        item {
            PlanDateScroller(
                selectedDate = selectedDate,
                onSelectDate = onSelectDate,
                fullCalendarVisible = fullCalendarVisible,
                onFullCalendarVisibleChange = { fullCalendarVisible = it },
                timeBlocks = sortedBlocks,
                quickItems = quickItems,
                onBlockSelected = onBlockSelected,
                onQuickTaskDone = onQuickTaskDone,
                onQuickHabitDone = onQuickHabitDone,
                onQuickMedicationTaken = onQuickMedicationTaken,
                onQuickMedicationMissed = onQuickMedicationMissed
            )
        }

        item {
            PlanPlanningSurface(
                templates = templates,
                onOpenAiSheet = onOpenAiSheet,
                onRebalanceDay = onRebalanceDay,
                onFillGaps = onFillGaps,
                onCreateBlock = onCreateBlock,
                onApplyTemplate = onApplyTemplate
            )
        }

        if (largeGaps.isNotEmpty() || overlaps.isNotEmpty()) {
            item(key = "plan_schedule_attention") {
                PlanScheduleAttentionCard(
                    largeGapCount = largeGaps.size,
                    overlapCount = overlaps.size,
                    onFillGaps = onFillGaps,
                    modifier = Modifier.animateItem(),
                    onRepairConflicts = if (overlaps.isEmpty()) {
                        null
                    } else {
                        { onRepairConflicts(conflictDescription(overlaps)) }
                    }
                )
            }
        }

        if (!fullCalendarVisible) {
            item { ChronosSectionTitle(title = "Timeline") }

            if (timeBlocks.isEmpty()) {
                item(key = "plan_empty_timeline") {
                    ChronosListCard(modifier = Modifier.animateItem().fillMaxWidth()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact),
                            modifier = Modifier.padding(ChronosSpacing.Compact)
                        ) {
                            Text(
                                "No blocks planned",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Generate a balanced day or add blocks manually.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onOpenAiSheet,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("Generate with AI", fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = onCreateBlock,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("Add manually", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            items(sortedBlocks, key = { it.id }) { block ->
                val dismissState = rememberSwipeToDismissBoxState()

                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        onDeleteBlock(block.id)
                    } else if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                        onDuplicateBlock(block.id)
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        val color = when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondary
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                            else -> Color.Transparent
                        }
                        val iconTint = when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onSecondary
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onError
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color, MaterialTheme.shapes.small)
                                .padding(horizontal = 20.dp),
                            contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Icons.Default.ContentCopy else Icons.Default.Delete,
                                contentDescription = null,
                                tint = iconTint
                            )
                        }
                    },
                    modifier = Modifier
                        .animateItem()
                        .padding(vertical = 4.dp)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(label = planDuplicateBlockActionLabel(block)) {
                                    onDuplicateBlock(block.id)
                                    true
                                },
                                CustomAccessibilityAction(label = planDeleteBlockActionLabel(block)) {
                                    onDeleteBlock(block.id)
                                    true
                                }
                            )
                        }
                ) {
                    TimelineBlockItem(block, onClick = { onBlockSelected(block.id) }, showTimeColumn = true)
                }
            }
        }

        if (suggestedBlocks.isNotEmpty()) {
            item(key = "plan_ai_suggestions") {
                PlanAiSuggestionsCard(
                    suggestions = suggestedBlocks.take(3),
                    onOpenAiSheet = onOpenAiSheet,
                    onApplyAiSuggestions = onApplyAiSuggestions,
                    onAcceptAiSuggestion = onAcceptAiSuggestion,
                    onRejectAiSuggestion = onRejectAiSuggestion,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun PlanDateScroller(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    fullCalendarVisible: Boolean,
    onFullCalendarVisibleChange: (Boolean) -> Unit,
    timeBlocks: List<TimeBlockUiModel>,
    quickItems: DayQuickItemsUiState,
    onBlockSelected: (String?) -> Unit,
    onQuickTaskDone: (String) -> Unit,
    onQuickHabitDone: (String) -> Unit,
    onQuickMedicationTaken: (String, Int?) -> Unit,
    onQuickMedicationMissed: (String, Int?) -> Unit
) {
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val dates = remember(selectedDate) { planDateScrollDates(selectedDate) }
    val dateListState = rememberLazyListState(
        initialFirstVisibleItemIndex = planDateInitialFirstVisibleIndex(dates, selectedDate)
    )

    LaunchedEffect(dates, selectedDate) {
        dateListState.scrollToItem(planDateInitialFirstVisibleIndex(dates, selectedDate))
    }

    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateNavHeaderLabel(selectedDate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (fullCalendarVisible) "Calendar and agenda" else "Planning date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { onFullCalendarVisibleChange(!fullCalendarVisible) },
                    modifier = Modifier.semantics {
                        contentDescription = planCalendarToggleActionLabel(fullCalendarVisible)
                    }
                ) {
                    Text(if (fullCalendarVisible) "Hide calendar" else "Full calendar")
                }
            }

            AnimatedContent(
                targetState = fullCalendarVisible,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val spec = ChronosValueAnimationFactory.stateChange<Float>(reduceMotion)
                        (fadeIn(spec) togetherWith fadeOut(spec))
                            .using(SizeTransform(clip = false))
                    }
                },
                label = "planCalendarMode"
            ) { showFullCalendar ->
                if (showFullCalendar) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                        PlanMonthCalendar(
                            selectedDate = selectedDate,
                            onSelectDate = onSelectDate
                        )
                        PlanCalendarAgenda(
                            timeBlocks = timeBlocks,
                            quickItems = quickItems,
                            onBlockSelected = onBlockSelected,
                            onQuickTaskDone = onQuickTaskDone,
                            onQuickHabitDone = onQuickHabitDone,
                            onQuickMedicationTaken = onQuickMedicationTaken,
                            onQuickMedicationMissed = onQuickMedicationMissed
                        )
                    }
                } else {
                    LazyRow(
                        state = dateListState,
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(dates, key = { it.toEpochDay() }) { date ->
                            val isSelected = date == selectedDate
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectDate(date) },
                                modifier = Modifier
                                    .width(78.dp)
                                    .semantics {
                                        contentDescription = planDateChipActionLabel(date, isSelected)
                                    },
                                label = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = planDateChipWeekdayLabel(date),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = planDateChipDateLabel(date),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanMonthCalendar(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = { onSelectDate(selectedDate.minusMonths(1)) },
                modifier = Modifier.semantics {
                    contentDescription = planCalendarMonthNavigationLabel(selectedDate, -1)
                }
            ) {
                Text("<")
            }
            Text(
                text = selectedDate.format(planCalendarMonthFormatter),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = { onSelectDate(selectedDate.plusMonths(1)) },
                modifier = Modifier.semantics {
                    contentDescription = planCalendarMonthNavigationLabel(selectedDate, 1)
                }
            ) {
                Text(">")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            planCalendarWeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AnimatedContent(
            targetState = YearMonth.from(selectedDate),
            transitionSpec = {
                if (reduceMotion) {
                    val spec = ChronosValueAnimationFactory.stateChange<Float>(true)
                    fadeIn(spec) togetherWith fadeOut(spec)
                } else {
                    val forward = targetState.isAfter(initialState)
                    val set = ChronosTransitionFactory.materialSharedAxis(
                        direction = if (forward) {
                            ChronosTransitionDirection.Forward
                        } else {
                            ChronosTransitionDirection.Backward
                        }
                    )
                    set.enter togetherWith set.exit
                }
            },
            label = "planCalendarMonthGrid"
        ) { month ->
            val monthDates = remember(month) { planCalendarMonthGridDates(month.atDay(1)) }
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                monthDates.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                    ) {
                        week.forEach { date ->
                            val isSelected = date == selectedDate
                            val inSelectedMonth = date.month == month.month && date.year == month.year
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectDate(date) },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = planCalendarDayActionLabel(date, isSelected)
                                    },
                                label = {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = if (inSelectedMonth) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    labelColor = if (inSelectedMonth) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCalendarAgenda(
    timeBlocks: List<TimeBlockUiModel>,
    quickItems: DayQuickItemsUiState,
    onBlockSelected: (String?) -> Unit,
    onQuickTaskDone: (String) -> Unit,
    onQuickHabitDone: (String) -> Unit,
    onQuickMedicationTaken: (String, Int?) -> Unit,
    onQuickMedicationMissed: (String, Int?) -> Unit
) {
    val agendaItems = remember(
        timeBlocks,
        quickItems,
        onBlockSelected,
        onQuickTaskDone,
        onQuickHabitDone,
        onQuickMedicationTaken,
        onQuickMedicationMissed
    ) {
        buildPlanCalendarAgendaItems(
            timeBlocks = timeBlocks,
            quickItems = quickItems,
            onBlockSelected = onBlockSelected,
            onQuickTaskDone = onQuickTaskDone,
            onQuickHabitDone = onQuickHabitDone,
            onQuickMedicationTaken = onQuickMedicationTaken,
            onQuickMedicationMissed = onQuickMedicationMissed
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
        Text(
            text = "Selected-day agenda",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (agendaItems.isEmpty()) {
            Text(
                text = "No calendar items, blocks, tasks, habits, or medications for this date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            agendaItems.forEachIndexed { index, item ->
                if (index == 0 || agendaItems[index - 1].sourceLabel != item.sourceLabel) {
                    Text(
                        text = item.sourceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PlanCalendarAgendaItemRow(item)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCalendarAgendaItemRow(item: PlanCalendarAgendaItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.onRowClick != null) {
                    Modifier.clickable(onClickLabel = item.title, role = Role.Button, onClick = item.onRowClick)
                } else {
                    Modifier
                }
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .background(item.accentColor, RoundedCornerShape(2.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.timeLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${item.detail} · ${item.statusText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
            ) {
                if (item.primaryActionLabel != null && item.onPrimaryAction != null && !item.isDone) {
                    TextButton(onClick = item.onPrimaryAction) {
                        Text(item.primaryActionLabel)
                    }
                }
                if (item.secondaryActionLabel != null && item.onSecondaryAction != null && !item.isDone) {
                    TextButton(
                        onClick = item.onSecondaryAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(item.secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanPlanningSurface(
    templates: List<TemplateBlueprint>,
    onOpenAiSheet: () -> Unit,
    onRebalanceDay: () -> Unit,
    onFillGaps: () -> Unit,
    onCreateBlock: () -> Unit,
    onApplyTemplate: (TemplateBlueprint) -> Unit
) {
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
            ) {
                Button(
                    onClick = onOpenAiSheet,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = planGenerateActionLabel()
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate", fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = onRebalanceDay,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = planRebalanceActionLabel()
                        },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Rebalance", fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
            ) {
                OutlinedButton(
                    onClick = onFillGaps,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = planFillGapsActionLabel()
                        },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Fill gaps", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onCreateBlock,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = planCreateBlockActionLabel()
                        },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Add manually", fontWeight = FontWeight.SemiBold)
                }
            }
            if (templates.isNotEmpty()) {
                Text(
                    text = "Templates",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    templates.forEach { template ->
                        FilterChip(
                            selected = false,
                            onClick = { onApplyTemplate(template) },
                            modifier = Modifier.semantics {
                                contentDescription = planTemplateChipActionLabel(template)
                            },
                            label = { Text(template.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanScheduleAttentionCard(
    largeGapCount: Int,
    overlapCount: Int,
    onFillGaps: () -> Unit,
    modifier: Modifier = Modifier,
    onRepairConflicts: (() -> Unit)? = null
) {
    val actionLabel = planScheduleAttentionActionLabel(largeGapCount, overlapCount)
    val buttonText = planScheduleAttentionButtonText(largeGapCount, overlapCount)
    val details = buildList {
        if (largeGapCount > 0) add("$largeGapCount large gap${if (largeGapCount == 1) "" else "s"}")
        if (overlapCount > 0) add("$overlapCount overlap${if (overlapCount == 1) "" else "s"}")
    }.joinToString(" · ")
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Schedule needs attention",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onRepairConflicts != null) {
                TextButton(
                    onClick = onRepairConflicts,
                    modifier = Modifier.semantics {
                        contentDescription = "Repair conflicting blocks with AI"
                    }
                ) {
                    Text("Repair with AI")
                }
            }
            TextButton(
                onClick = onFillGaps,
                modifier = Modifier.semantics {
                    contentDescription = actionLabel
                }
            ) {
                Text(buttonText)
            }
        }
    }
}

internal fun conflictDescription(overlaps: List<TimeRangeUi>): String =
    overlaps.joinToString(prefix = "Overlapping blocks at: ", separator = ", ") { range ->
        "${formatDialMinute(range.startMinute)}-${formatDialMinute(range.endMinute)}"
    }

private fun formatDialMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}

@Composable
private fun PlanAiSuggestionsCard(
    suggestions: List<TimeBlockUiModel>,
    onOpenAiSheet: () -> Unit,
    onApplyAiSuggestions: () -> Unit,
    onAcceptAiSuggestion: (String) -> Unit,
    onRejectAiSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Text(
                text = "AI suggestions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            suggestions.forEach { suggestion ->
                val categColor = categoryColor(suggestion.category)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(categColor, RoundedCornerShape(100))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = planSuggestionTimeText(suggestion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { onRejectAiSuggestion(suggestion.id) },
                        modifier = Modifier.semantics {
                            contentDescription = planSuggestionRejectActionLabel(suggestion)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Reject") }
                    Button(
                        onClick = { onAcceptAiSuggestion(suggestion.id) },
                        modifier = Modifier.semantics {
                            contentDescription = planSuggestionAcceptActionLabel(suggestion)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Accept") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
            ) {
                OutlinedButton(
                    onClick = onOpenAiSheet,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = planSuggestionReviewAllActionLabel(suggestions.size)
                        },
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Review") }
                Button(
                    onClick = onApplyAiSuggestions,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = planSuggestionApplyAllActionLabel(suggestions.size)
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Apply all", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun TimelineBlockItem(block: TimeBlockUiModel, onClick: () -> Unit, showTimeColumn: Boolean = false) {
    val rowHeight = (44.dp + (block.durationMinutes / 6).coerceAtLeast(1).dp).coerceAtMost(88.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = planTimelineBlockActionLabel(block), role = Role.Button) { onClick() }
    ) {
        if (showTimeColumn) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(68.dp)) {
                Text(
                    text = formatMinute(block.startMinuteOfDay),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatMinute(block.startMinuteOfDay + block.durationMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(block.color, block.color.copy(alpha = 0.35f))
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopCenter)
                        .background(block.color, RoundedCornerShape(100))
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(rowHeight)
                        .background(block.color)
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = inferCategory(block),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = block.color
                        )
                        Text(
                            text = "${formatMinute(block.startMinuteOfDay)} – ${formatMinute(block.startMinuteOfDay + block.durationMinutes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

internal fun findGaps(blocks: List<TimeBlockUiModel>): List<TimeRangeUi> {
    if (blocks.size < 2) return emptyList()
    val sorted = blocks.sortedBy { it.startMinuteOfDay }
    val gaps = mutableListOf<TimeRangeUi>()
    for (index in 0 until sorted.size - 1) {
        val current = sorted[index]
        val next = sorted[index + 1]
        val currentEnd = current.startMinuteOfDay + current.durationMinutes
        if (next.startMinuteOfDay > currentEnd + 15) {
            gaps.add(TimeRangeUi(currentEnd, next.startMinuteOfDay))
        }
    }
    return gaps
}

internal fun findLargeGaps(blocks: List<TimeBlockUiModel>, thresholdMinutes: Int): List<TimeRangeUi> =
    findGaps(blocks).filter { gap -> gap.endMinute - gap.startMinute >= thresholdMinutes }

internal fun planGenerateActionLabel(): String =
    "Generate a balanced plan with AI"

internal fun planRebalanceActionLabel(): String =
    "Rebalance today's plan"

internal fun planFillGapsActionLabel(): String =
    "Fill open gaps in today's plan"

internal fun planCreateBlockActionLabel(): String =
    "Add a block manually"

internal fun planDateScrollDates(
    selectedDate: LocalDate,
    radiusDays: Int = PLAN_DATE_SCROLL_RADIUS_DAYS
): List<LocalDate> =
    (-radiusDays..radiusDays).map { offset -> selectedDate.plusDays(offset.toLong()) }

internal fun planDateInitialFirstVisibleIndex(
    dates: List<LocalDate>,
    selectedDate: LocalDate
): Int {
    val selectedIndex = dates.indexOf(selectedDate)
    if (selectedIndex < 0) return 0
    return (selectedIndex - PLAN_DATE_SELECTED_VISIBLE_OFFSET).coerceAtLeast(0)
}

internal fun planDateChipWeekdayLabel(date: LocalDate): String =
    date.format(planDateChipWeekdayFormatter)

internal fun planDateChipDateLabel(date: LocalDate): String =
    date.format(planDateChipDateFormatter)

internal fun planDateChipActionLabel(date: LocalDate, isSelected: Boolean): String {
    val action = if (isSelected) "Selected" else "Select"
    return "$action ${date.format(planDateChipFullFormatter)}"
}

internal fun planCalendarMonthGridDates(selectedDate: LocalDate): List<LocalDate> {
    val firstDayOfMonth = selectedDate.withDayOfMonth(1)
    val lastDayOfMonth = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
    val leadingDays = firstDayOfMonth.dayOfWeek.value % 7
    val trailingDays = 6 - (lastDayOfMonth.dayOfWeek.value % 7)
    val firstVisibleDate = firstDayOfMonth.minusDays(leadingDays.toLong())
    val lastVisibleDate = lastDayOfMonth.plusDays(trailingDays.toLong())
    return generateSequence(firstVisibleDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastVisibleDate) }
        .toList()
}

internal fun planCalendarToggleActionLabel(fullCalendarVisible: Boolean): String =
    if (fullCalendarVisible) "Hide full planning calendar" else "Show full planning calendar"

internal fun planCalendarMonthNavigationLabel(selectedDate: LocalDate, monthOffset: Int): String {
    val targetMonth = selectedDate.plusMonths(monthOffset.toLong())
    return "Go to ${targetMonth.format(planCalendarMonthFormatter)}"
}

internal fun planCalendarDayActionLabel(date: LocalDate, isSelected: Boolean): String {
    val action = if (isSelected) "Selected" else "Select"
    return "$action ${date.format(planDateChipFullFormatter)}"
}

internal fun planCalendarSourceLabel(block: TimeBlockUiModel): String = when {
    block.isAllDayCalendarImport() -> "All-day calendar"
    block.calendarEventId != null -> "Calendar"
    block.taskId != null -> "Task"
    block.habitId != null -> "Habit"
    block.medicationPlanId != null -> "Medication"
    else -> "Plan"
}

internal fun planCalendarSortMinute(block: TimeBlockUiModel): Int =
    if (block.isAllDayCalendarImport()) 0 else block.startMinuteOfDay

internal fun planScheduleAttentionActionLabel(largeGapCount: Int, overlapCount: Int): String {
    val parts = buildList {
        if (largeGapCount > 0) {
            add("$largeGapCount large gap${if (largeGapCount == 1) "" else "s"}")
        }
        if (overlapCount > 0) {
            add("$overlapCount overlap${if (overlapCount == 1) "" else "s"}")
        }
    }
    return "Fix ${parts.joinToString(" and ")} in today's schedule"
}

internal fun planScheduleAttentionButtonText(largeGapCount: Int, overlapCount: Int): String =
    if (largeGapCount > 0 && overlapCount == 0) {
        "Fill gaps with tasks"
    } else {
        "Fix schedule"
    }

internal fun planTemplateChipActionLabel(template: TemplateBlueprint): String =
    "Apply ${template.name} to current plan"

internal fun planSuggestionAcceptActionLabel(suggestion: TimeBlockUiModel): String =
    "Accept ${suggestion.title} suggestion ${planSuggestionTimeDetail(suggestion)}"

internal fun planSuggestionRejectActionLabel(suggestion: TimeBlockUiModel): String =
    "Reject ${suggestion.title} suggestion ${planSuggestionTimeDetail(suggestion)}"

internal fun planSuggestionTimeText(suggestion: TimeBlockUiModel): String =
    "${formatMinute(suggestion.startMinuteOfDay)} - ${
        formatMinute(suggestion.startMinuteOfDay + suggestion.durationMinutes)
    } · ${formatDurationLabel(suggestion.durationMinutes)}"

private fun planSuggestionTimeDetail(suggestion: TimeBlockUiModel): String =
    "from ${formatMinute(suggestion.startMinuteOfDay)} for ${formatDurationMinutes(suggestion.durationMinutes)}"

private fun formatDurationMinutes(durationMinutes: Int): String =
    "$durationMinutes minute${if (durationMinutes == 1) "" else "s"}"

internal fun planSuggestionReviewAllActionLabel(suggestionCount: Int): String =
    "Review $suggestionCount AI suggestion${if (suggestionCount == 1) "" else "s"}"

internal fun planSuggestionApplyAllActionLabel(suggestionCount: Int): String =
    "Apply $suggestionCount AI suggestion${if (suggestionCount == 1) "" else "s"} to today's plan"

internal fun planTimelineBlockActionLabel(block: TimeBlockUiModel): String =
    "Open ${block.title} from ${formatMinute(block.startMinuteOfDay)} to ${
        formatMinute(block.startMinuteOfDay + block.durationMinutes)
    }"

internal fun planDuplicateBlockActionLabel(block: TimeBlockUiModel): String =
    "Duplicate ${block.title} block"

internal fun planDeleteBlockActionLabel(block: TimeBlockUiModel): String =
    "Delete ${block.title} block"

private fun findOverlaps(blocks: List<TimeBlockUiModel>): List<TimeRangeUi> {
    if (blocks.size < 2) return emptyList()
    val sorted = blocks.sortedBy { it.startMinuteOfDay }
    val overlaps = mutableListOf<TimeRangeUi>()
    for (i in 0 until sorted.size - 1) {
        val b1 = sorted[i]
        val b2 = sorted[i + 1]
        val b1End = b1.startMinuteOfDay + b1.durationMinutes
        if (b1End > b2.startMinuteOfDay) {
            overlaps.add(TimeRangeUi(b2.startMinuteOfDay, b1End.coerceAtMost(b2.startMinuteOfDay + b2.durationMinutes)))
        }
    }
    return overlaps
}

private data class PlanCalendarAgendaItem(
    val id: String,
    val title: String,
    val sourceLabel: String,
    val timeLabel: String,
    val detail: String,
    val statusText: String,
    val isDone: Boolean,
    val sortMinute: Int,
    val accentColor: Color,
    val primaryActionLabel: String? = null,
    val onPrimaryAction: (() -> Unit)? = null,
    val secondaryActionLabel: String? = null,
    val onSecondaryAction: (() -> Unit)? = null,
    val onRowClick: (() -> Unit)? = null
)

private const val PLAN_UNSCHEDULED_AGENDA_MINUTE = DAY_IN_MINUTES + 10

private fun buildPlanCalendarAgendaItems(
    timeBlocks: List<TimeBlockUiModel>,
    quickItems: DayQuickItemsUiState,
    onBlockSelected: (String?) -> Unit,
    onQuickTaskDone: (String) -> Unit,
    onQuickHabitDone: (String) -> Unit,
    onQuickMedicationTaken: (String, Int?) -> Unit,
    onQuickMedicationMissed: (String, Int?) -> Unit
): List<PlanCalendarAgendaItem> = buildList {
    addAll(timeBlocks.map { block ->
        val allDayCalendarImport = block.isAllDayCalendarImport()
        PlanCalendarAgendaItem(
            id = "block:${block.id}",
            title = block.title.ifBlank {
                if (allDayCalendarImport) "All-day calendar note" else "Planned block"
            },
            sourceLabel = planCalendarSourceLabel(block),
            timeLabel = if (allDayCalendarImport) {
                "All day"
            } else {
                "${formatMinute(block.startMinuteOfDay)} - ${formatMinute(block.startMinuteOfDay + block.durationMinutes)}"
            },
            detail = if (allDayCalendarImport) "Calendar import" else inferCategory(block),
            statusText = if (allDayCalendarImport) "Non-blocking note" else "Planned",
            isDone = false,
            sortMinute = planCalendarSortMinute(block),
            accentColor = categoryColor(block.category),
            onRowClick = { onBlockSelected(block.id) }
        )
    })
    addAll(quickItems.tasks.map { item ->
        item.toPlanCalendarAgendaItem(
            kind = DayQuickItemKind.TASK,
            primaryActionLabel = if (item.isDone) null else "Complete",
            onPrimaryAction = if (item.isDone) null else ({ onQuickTaskDone(item.id) })
        )
    })
    addAll(quickItems.habits.map { item ->
        item.toPlanCalendarAgendaItem(
            kind = DayQuickItemKind.HABIT,
            primaryActionLabel = if (item.isDone) null else "Complete",
            onPrimaryAction = if (item.isDone) null else ({ onQuickHabitDone(item.id) })
        )
    })
    addAll(quickItems.medications.map { item ->
        item.toPlanCalendarAgendaItem(
            kind = DayQuickItemKind.MEDICATION,
            primaryActionLabel = if (item.isDone) null else "Take",
            onPrimaryAction = if (item.isDone) null else ({
                onQuickMedicationTaken(item.baseMedicationPlanId(), item.scheduledMinuteOfDay)
            }),
            secondaryActionLabel = if (item.isDone) null else "Missed",
            onSecondaryAction = if (item.isDone) null else ({
                onQuickMedicationMissed(item.baseMedicationPlanId(), item.scheduledMinuteOfDay)
            })
        )
    })
}
    .sortedWith(
        compareBy<PlanCalendarAgendaItem> { planCalendarSourceRank(it.sourceLabel) }
            .thenBy { it.sortMinute }
            .thenBy { it.title.lowercase() }
    )

private fun DayQuickItemUiModel.toPlanCalendarAgendaItem(
    kind: DayQuickItemKind,
    primaryActionLabel: String?,
    onPrimaryAction: (() -> Unit)?,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
): PlanCalendarAgendaItem {
    val scheduledMinute = scheduledMinuteOfDay ?: PLAN_UNSCHEDULED_AGENDA_MINUTE
    val sourceLabel = when (kind) {
        DayQuickItemKind.TASK -> "Task"
        DayQuickItemKind.HABIT -> "Habit"
        DayQuickItemKind.MEDICATION -> "Medication"
    }
    return PlanCalendarAgendaItem(
        id = "quick:$id",
        title = title.ifBlank { "Unnamed item" },
        sourceLabel = sourceLabel,
        timeLabel = scheduledMinuteOfDay?.let { formatMinute(it) } ?: "Unscheduled",
        detail = detail.ifBlank { sourceLabel },
        statusText = if (isDone) "Completed" else status,
        isDone = isDone,
        sortMinute = scheduledMinute,
        accentColor = when (kind) {
            DayQuickItemKind.TASK -> Color(0xFF2F6BEA)
            DayQuickItemKind.HABIT -> Color(0xFF5DAA54)
            DayQuickItemKind.MEDICATION -> Color(0xFFD17A2A)
        },
        primaryActionLabel = primaryActionLabel,
        onPrimaryAction = onPrimaryAction,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryAction = onSecondaryAction
    )
}

private fun planCalendarSourceRank(source: String): Int = when (source) {
    "All-day calendar" -> 0
    "Calendar" -> 1
    "Plan" -> 2
    "Task" -> 3
    "Habit" -> 4
    "Medication" -> 5
    else -> 6
}

private fun DayQuickItemUiModel.baseMedicationPlanId(): String = id.substringBefore(":")

private fun formatMinute(minute: Int): String {
    val normalized = ((minute % DAY_IN_MINUTES) + DAY_IN_MINUTES) % DAY_IN_MINUTES
    val hour = normalized / 60
    val m = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(displayHour, m, suffix)
}

private fun inferCategory(block: TimeBlockUiModel): String = when {
    block.medicationPlanId != null -> "Medication"
    block.habitId != null -> "Habit"
    block.taskId != null -> "Task"
    block.title.contains("break", ignoreCase = true) -> "Break"
    block.title.contains("meet", ignoreCase = true) -> "Meeting"
    block.title.contains("work", ignoreCase = true) -> "Work"
    else -> block.category.lowercase().replaceFirstChar { it.uppercase() }
}
