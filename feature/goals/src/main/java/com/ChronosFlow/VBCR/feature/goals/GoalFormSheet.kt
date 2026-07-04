package com.ChronosFlow.VBCR.feature.goals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.domain.model.Goal
import com.ChronosFlow.VBCR.core.ui.components.CardEditorScaffold
import com.ChronosFlow.VBCR.core.ui.components.CardEditorSection
import com.ChronosFlow.VBCR.core.ui.components.ChronosAssistChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosDatePickerField
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilterChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosFormBottomSheet
import com.ChronosFlow.VBCR.core.ui.components.ChronosOptionChips
import com.ChronosFlow.VBCR.core.ui.components.EditorQuickAttribute
import java.time.LocalDate

/** Target describing whether the sheet creates a new goal or edits an existing one. */
sealed interface GoalSheetTarget {
    data class Add(val prefillTitle: String? = null) : GoalSheetTarget
    data class Edit(val goal: Goal) : GoalSheetTarget
}

/** Common target counts offered as one-tap chips so users skip the number keyboard. */
internal val GoalTargetQuickPicks = listOf(5, 10, 12, 30, 50, 100)

/** Keyword → category rules backing [suggestGoalCategory], most specific first. */
private val GoalCategoryKeywordRules: List<Pair<List<String>, String>> = listOf(
    listOf("run", "gym", "workout", "exercise", "weight", "sleep", "steps", "diet", "yoga", "hydrate") to "Health",
    listOf("read", "book", "learn", "study", "course", "language", "skill") to "Learning",
    listOf("save", "money", "budget", "invest", "debt", "spend") to "Finance",
    listOf("work", "project", "career", "client", "launch", "ship") to "Work",
    listOf("habit", "daily", "meditate", "journal", "streak") to "Habits"
)

/** Suggests a category from keywords in the [title], or null when nothing recognisable matches. */
internal fun suggestGoalCategory(title: String): String? {
    val lower = title.lowercase()
    return GoalCategoryKeywordRules
        .firstOrNull { (keywords, _) -> keywords.any { lower.contains(it) } }
        ?.second
}

/**
 * First sensible whole number embedded in a goal title (e.g. "Read 12 books" → 12), so the
 * target count can be offered without retyping. Null when there's no number in a usable range.
 */
internal fun goalTargetFromTitle(title: String): Int? =
    Regex("\\d+").findAll(title)
        .mapNotNull { it.value.toIntOrNull() }
        .firstOrNull { it in 1..99999 }

/** One-tap target-date options relative to [today], so users skip the calendar for common horizons. */
internal fun goalDeadlinePresets(today: LocalDate): List<Pair<String, LocalDate>> = listOf(
    "1 month" to today.plusMonths(1),
    "3 months" to today.plusMonths(3),
    "6 months" to today.plusMonths(6),
    "End of year" to LocalDate.of(today.year, 12, 31)
)

/** Warns when an edited target count is below the progress already logged, or null otherwise. */
internal fun goalTargetBelowProgressWarning(target: Int, progress: Int): String? =
    if (target in 1 until progress) {
        "Target is below your logged progress ($progress)."
    } else {
        null
    }

/** Warns when a chosen target date has already passed, or null when the date is fine. */
internal fun goalTargetDateWarning(targetDate: LocalDate?, today: LocalDate): String? =
    if (targetDate != null && targetDate.isBefore(today)) {
        "This date is in the past — the goal will show as overdue."
    } else {
        null
    }

/**
 * Selectable category chips: the presets, the edited goal's own category, and any custom one
 * the user is typing — trimmed, de-blanked, and de-duplicated so a typed preset never doubles up.
 * The progress helpers used by the edit-mode bar ([goalProgressSummary]/[goalProgressFraction])
 * live in GoalScreen.kt.
 */
internal fun goalCategoryOptions(
    presets: List<String>,
    existingCategory: String?,
    customCategory: String
): List<String> =
    (presets + listOfNotNull(existingCategory) + listOfNotNull(customCategory.trim().takeIf { it.isNotBlank() }))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

@Composable
internal fun GoalFormSheet(
    target: GoalSheetTarget?,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String?,
        category: String,
        targetValue: Int,
        targetDate: LocalDate?
    ) -> Unit,
    onArchive: (() -> Unit)? = null
) {
    val editing = target as? GoalSheetTarget.Edit
    val existing = editing?.goal
    val prefillTitle = (target as? GoalSheetTarget.Add)?.prefillTitle

    val targetKey = existing?.id ?: prefillTitle ?: "new"

    var title by rememberSaveable(targetKey) { mutableStateOf(existing?.title ?: prefillTitle.orEmpty()) }
    var description by rememberSaveable(targetKey) { mutableStateOf(existing?.description.orEmpty()) }
    var category by rememberSaveable(targetKey) {
        mutableStateOf(existing?.category ?: GoalViewModel.DEFAULT_CATEGORY)
    }
    var targetValueText by rememberSaveable(targetKey) {
        mutableStateOf((existing?.targetValue ?: 1).toString())
    }
    var targetDateIso by rememberSaveable(targetKey) {
        mutableStateOf(existing?.targetDate?.toString())
    }
    var customCategory by rememberSaveable(targetKey) { mutableStateOf("") }
    var lastChipCategory by rememberSaveable(targetKey) {
        mutableStateOf(existing?.category ?: GoalViewModel.DEFAULT_CATEGORY)
    }

    // GOALS-7: reset draft when a new Add session opens (each dismiss→reopen cycle).
    // lastAddOpen uses rememberSaveable so config changes don't accidentally trigger a reset.
    var lastAddOpen by rememberSaveable { mutableStateOf(false) }
    val isAddOpen = target is GoalSheetTarget.Add
    LaunchedEffect(target) {
        if (isAddOpen && !lastAddOpen) {
            title = prefillTitle.orEmpty()
            description = ""
            category = GoalViewModel.DEFAULT_CATEGORY
            customCategory = ""
            lastChipCategory = GoalViewModel.DEFAULT_CATEGORY
            targetValueText = "1"
            targetDateIso = null
        }
        lastAddOpen = isAddOpen
    }

    val targetDate = targetDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val parsedTargetValue = targetValueText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val titleValid = title.isNotBlank()

    val categoryOptions = goalCategoryOptions(
        presets = GoalViewModel.CATEGORY_OPTIONS,
        existingCategory = existing?.category,
        customCategory = customCategory
    )

    ChronosFormBottomSheet(
        visible = target != null,
        title = if (existing != null) "Edit goal" else "New goal",
        subtitle = "Track a measurable objective. Linked tasks and habit completions count toward it.",
        confirmLabel = if (existing != null) "Save" else "Create",
        enabled = titleValid,
        validationHint = "Add a goal title to continue.",
        onDismiss = onDismiss,
        onArchive = onArchive,
        archiveLabel = "Delete goal",
        onConfirm = {
            if (!titleValid) return@ChronosFormBottomSheet
            onConfirm(
                title,
                description.ifBlank { null },
                category,
                parsedTargetValue,
                targetDate
            )
        }
    ) {
        CardEditorScaffold(
            kind = "goal",
            stateKey = targetKey,
            attributes = listOf(
                EditorQuickAttribute(
                    id = "details",
                    title = "Deadline",
                    value = targetDate?.let { "Due $it" },
                    onReveal = {},
                    onClear = { targetDateIso = null },
                )
            ),
            sections = buildList {
                add(
                    CardEditorSection(
                        id = "category",
                        title = "Category",
                        summary = category,
                        hasValue = true,
                    ) {
                        ChronosOptionChips(
                            label = "Category",
                            options = categoryOptions,
                            selected = category,
                            onSelected = { selection ->
                                category = selection
                                lastChipCategory = selection
                                customCategory = ""
                            }
                        )
                        OutlinedTextField(
                            value = customCategory,
                            onValueChange = { input ->
                                customCategory = input
                                val trimmed = input.trim()
                                when {
                                    trimmed.isBlank() -> category = lastChipCategory
                                    trimmed.length >= 2 -> category = trimmed
                                }
                            },
                            label = { Text("Custom category") },
                            placeholder = { Text("e.g. Travel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        suggestGoalCategory(title)?.takeIf { it != category }?.let { suggested ->
                            ChronosAssistChip(
                                onClick = { category = suggested },
                                label = { Text("Suggested: $suggested") }
                            )
                        }
                    }
                )
                add(
                    CardEditorSection(
                        id = "target",
                        title = "Target",
                        summary = "Count: $parsedTargetValue",
                        hasValue = true,
                    ) {
                        OutlinedTextField(
                            value = targetValueText,
                            onValueChange = { input -> targetValueText = input.filter(Char::isDigit).take(5) },
                            label = { Text("Target count") },
                            supportingText = { Text("How many completions/units count as done (e.g. 12 books).") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GoalTargetQuickPicks.forEach { pick ->
                                ChronosFilterChip(
                                    selected = parsedTargetValue == pick,
                                    onClick = { targetValueText = pick.toString() },
                                    label = { Text(pick.toString()) }
                                )
                            }
                        }
                        goalTargetFromTitle(title)?.takeIf { it != parsedTargetValue }?.let { suggested ->
                            ChronosAssistChip(
                                onClick = { targetValueText = suggested.toString() },
                                label = { Text("Use $suggested from title") }
                            )
                        }
                    }
                )
                add(
                    CardEditorSection(
                        id = "details",
                        title = "Details",
                        summary = buildString {
                            append(targetDate?.let { "Due $it" } ?: "No target date")
                            if (description.isNotBlank()) append(" • has notes")
                        },
                        hasValue = targetDateIso != null || description.isNotBlank(),
                    ) {
                        ChronosDatePickerField(
                            label = "Target date",
                            value = targetDate?.toString() ?: "Pick a date",
                            selectedDate = targetDate,
                            onDateSelected = { picked -> targetDateIso = picked.toString() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            goalDeadlinePresets(LocalDate.now()).forEach { (label, date) ->
                                ChronosFilterChip(
                                    selected = targetDate == date,
                                    onClick = { targetDateIso = date.toString() },
                                    label = { Text(label) }
                                )
                            }
                        }
                        val today = LocalDate.now()
                        val pastWarning = goalTargetDateWarning(targetDate, today)
                        if (pastWarning != null) {
                            Text(
                                text = pastWarning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (targetDate != null) {
                            goalDueLabel(targetDate = targetDate, isCompleted = false, today = today)?.let { due ->
                                Text(
                                    text = due.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (due.emphasized) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Notes") },
                            placeholder = { Text("Why this matters, milestones, etc.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Tip: link tasks and habits to this goal from their edit screens to track progress automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                if (existing != null) {
                    add(
                        CardEditorSection(
                            id = "progress",
                            title = "Progress",
                            summary = goalProgressSummary(existing.progressValue, existing.targetValue),
                            hasValue = true,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Progress · ${goalProgressSummary(existing.progressValue, existing.targetValue)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { goalProgressFraction(existing.progressValue, existing.targetValue) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                goalTargetBelowProgressWarning(parsedTargetValue, existing.progressValue)?.let { warning ->
                                    Text(
                                        text = warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            },
            essentials = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal") },
                    placeholder = { Text("Read 12 books this year") },
                    singleLine = true,
                    isError = title.isNotEmpty() && !titleValid,
                    modifier = Modifier.fillMaxWidth()
                )
            },
        )
    }
}
