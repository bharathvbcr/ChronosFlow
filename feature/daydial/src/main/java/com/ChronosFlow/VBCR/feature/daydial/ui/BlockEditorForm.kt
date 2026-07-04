package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.components.CardEditorScaffold
import com.ChronosFlow.VBCR.core.ui.components.CardEditorSection
import com.ChronosFlow.VBCR.core.ui.components.EditorQuickAttribute
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import com.ChronosFlow.VBCR.feature.daydial.ui.CheckboxSetting

/** Chip summary for planner attributes (locked / protected focus). Mirrors iOS `plannerChipValue`. */
internal fun blockPlannerChipValue(locked: Boolean, isProtected: Boolean): String? {
    val parts = buildList {
        if (locked) add("Locked")
        if (isProtected) add("Protected")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** One-line summary for the collapsible Time section header. */
internal fun blockTimeSectionSummary(startText: String, durationText: String): String {
    val startMinute = parseMinuteOfDay(startText) ?: return "Set start and duration"
    val durationMinutes = durationText.toIntOrNull()
        ?.coerceIn(BlockEditorMinDurationMinutes, BlockEditorMaxDurationMinutes)
        ?: 25
    val endMinute = blockEditorEndMinute(startMinute, durationMinutes)
    return "${formatMinuteOfDay(startMinute)} – ${formatMinuteOfDay(endMinute)} · ${formatDurationLabel(durationMinutes)}"
}

@Composable
internal fun BlockEditorEssentialsFields(
    title: String,
    onTitleChange: (String) -> Unit,
    category: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    titleEnabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            enabled = titleEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BlockEditorCategoryChipSelector(
            selectedCategory = category,
            onCategorySelected = onCategorySelected,
            enabled = titleEnabled,
        )
    }
}

@Composable
internal fun BlockEditorProgressiveForm(
    stateKey: String,
    title: String,
    onTitleChange: (String) -> Unit,
    startText: String,
    onStartTextChange: (String) -> Unit,
    durationText: String,
    onDurationTextChange: (String) -> Unit,
    category: String,
    onCategorySelected: (String) -> Unit,
    locked: Boolean = false,
    onLockedChange: ((Boolean) -> Unit)? = null,
    isProtected: Boolean = false,
    onProtectedChange: ((Boolean) -> Unit)? = null,
    onFieldEdited: () -> Unit = {},
    titleEnabled: Boolean = true,
    fieldsEnabled: Boolean = true,
    calendarChipValue: String? = null,
    onClearCalendar: (() -> Unit)? = null,
    calendarSectionSummary: String? = null,
    calendarDefaultExpandedHint: Boolean = false,
    calendarSection: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val hasPlannerValue = locked || isProtected
    val hasCalendarValue = calendarChipValue != null || calendarDefaultExpandedHint

    val attributes = buildList {
        if (onLockedChange != null) {
            add(
                EditorQuickAttribute(
                    id = "planner",
                    title = "Planner",
                    value = blockPlannerChipValue(locked, isProtected),
                    onReveal = {},
                    onClear = if (hasPlannerValue) {
                        {
                            onLockedChange(false)
                            onProtectedChange?.invoke(false)
                            onFieldEdited()
                        }
                    } else {
                        null
                    },
                )
            )
        }
        if (calendarSection != null) {
            add(
                EditorQuickAttribute(
                    id = "calendar",
                    title = "Calendar",
                    value = calendarChipValue,
                    onReveal = {},
                    onClear = onClearCalendar,
                )
            )
        }
    }

    val sections = buildList {
        add(
            CardEditorSection(
                id = "time",
                title = "Time",
                summary = blockTimeSectionSummary(startText, durationText),
                alwaysPrimary = true,
                hasValue = true,
                adaptiveHint = true,
            ) {
                BlockEditorTimeFields(
                    startText = startText,
                    onStartTextChange = {
                        onStartTextChange(it)
                        onFieldEdited()
                    },
                    durationText = durationText,
                    onDurationTextChange = {
                        onDurationTextChange(it)
                        onFieldEdited()
                    },
                    enabled = fieldsEnabled,
                )
            }
        )
        if (onLockedChange != null) {
            add(
                CardEditorSection(
                    id = "planner",
                    title = "Planner",
                    summary = blockPlannerChipValue(locked, isProtected) ?: "Movable · default energy",
                    hasValue = hasPlannerValue,
                ) {
                    CheckboxSetting("Locked", locked) {
                        onLockedChange(it)
                        onFieldEdited()
                    }
                    if (onProtectedChange != null) {
                        CheckboxSetting("Protected focus", isProtected) {
                            onProtectedChange(it)
                            onFieldEdited()
                        }
                    }
                }
            )
        }
        calendarSection?.let { section ->
            add(
                CardEditorSection(
                    id = "calendar",
                    title = "Calendar export",
                    summary = calendarSectionSummary ?: calendarChipValue ?: "Not exported",
                    hasValue = hasCalendarValue,
                    adaptiveHint = calendarDefaultExpandedHint,
                    content = section,
                )
            )
        }
    }

    CardEditorScaffold(
        kind = "block",
        stateKey = stateKey,
        attributes = attributes,
        sections = sections,
        essentials = {
            BlockEditorEssentialsFields(
                title = title,
                onTitleChange = {
                    onTitleChange(it)
                    onFieldEdited()
                },
                category = category,
                onCategorySelected = {
                    onCategorySelected(it)
                    onFieldEdited()
                },
                titleEnabled = titleEnabled,
            )
        },
    )
}
