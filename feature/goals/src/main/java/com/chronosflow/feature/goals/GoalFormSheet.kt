package com.chronosflow.feature.goals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.chronosflow.core.domain.model.Goal
import com.chronosflow.core.ui.components.ChronosCollapsibleSection
import com.chronosflow.core.ui.components.ChronosDatePickerField
import com.chronosflow.core.ui.components.ChronosFormBottomSheet
import com.chronosflow.core.ui.components.ChronosOptionChips
import java.time.LocalDate

/** Target describing whether the sheet creates a new goal or edits an existing one. */
sealed interface GoalSheetTarget {
    data class Add(val prefillTitle: String? = null) : GoalSheetTarget
    data class Edit(val goal: Goal) : GoalSheetTarget
}

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
    var detailsExpanded by rememberSaveable(targetKey) { mutableStateOf(false) }

    val targetDate = targetDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val parsedTargetValue = targetValueText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val titleValid = title.isNotBlank()

    val categoryOptions = remember {
        (GoalViewModel.CATEGORY_OPTIONS + listOfNotNull(existing?.category))
            .distinct()
    }

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
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Goal") },
            placeholder = { Text("Read 12 books this year") },
            singleLine = true,
            isError = title.isNotEmpty() && !titleValid,
            modifier = Modifier.fillMaxWidth()
        )
        ChronosOptionChips(
            label = "Category",
            options = categoryOptions,
            selected = category,
            onSelected = { category = it }
        )
        OutlinedTextField(
            value = targetValueText,
            onValueChange = { input -> targetValueText = input.filter(Char::isDigit).take(5) },
            label = { Text("Target count") },
            supportingText = { Text("How many completions/units count as done (e.g. 12 books).") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ChronosCollapsibleSection(
            title = "Details",
            summary = buildString {
                append(targetDate?.let { "Due $it" } ?: "No target date")
                if (description.isNotBlank()) append(" • has notes")
            },
            expanded = detailsExpanded,
            onExpandedChange = { detailsExpanded = it }
        ) {
            ChronosDatePickerField(
                label = "Target date",
                value = targetDate?.toString() ?: "Pick a date",
                selectedDate = targetDate,
                onDateSelected = { picked -> targetDateIso = picked.toString() },
                modifier = Modifier.fillMaxWidth()
            )
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
    }
}
