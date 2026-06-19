package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilledTonalButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ai.RoutineAssistRequest
import com.ChronosFlow.VBCR.core.ai.RoutineAssistStep
import com.ChronosFlow.VBCR.core.ai.RoutineAssistSuggestion
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ui.components.ChronosAssistSuggestionChips
import com.ChronosFlow.VBCR.core.ui.components.ChronosFormBottomSheet
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.GenAiAssistBanner
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlockBlueprint
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlueprint
import java.util.Locale
import java.util.UUID

internal class DayDialTemplateState(
    val allTemplates: List<TemplateBlueprint>,
    val templateToEdit: TemplateBlueprint?,
    val isTemplateEditorOpen: Boolean,
    val isCreatingTemplate: Boolean,
    val editTemplateName: String,
    val draftBlocks: List<TemplateBlockDraft>,
    val setEditTemplateName: (String) -> Unit,
    val updateDraftBlockTitle: (String, String) -> Unit,
    val updateDraftBlockStart: (String, String) -> Unit,
    val updateDraftBlockDuration: (String, String) -> Unit,
    val updateDraftBlockCategory: (String, String) -> Unit,
    val applyAssistSteps: (List<RoutineAssistStep>) -> Unit,
    val addDraftBlock: () -> Unit,
    val removeDraftBlock: (String) -> Unit,
    val moveDraftBlockUp: (String) -> Unit,
    val moveDraftBlockDown: (String) -> Unit,
    val dismissTemplateEditor: () -> Unit,
    val saveEditedTemplate: () -> Unit,
    val saveCurrentAsTemplate: () -> Unit,
    val applyTemplate: (TemplateBlueprint) -> Unit,
    val applyTemplateToday: (TemplateBlueprint) -> Unit,
    val applyTemplateTomorrow: (TemplateBlueprint) -> Unit,
    val routineCompletionFor: (TemplateBlueprint) -> RoutineCompletionSummary?,
    val importBackupText: (String) -> Unit,
    val editTemplate: (TemplateBlueprint) -> Unit,
    val duplicateTemplate: (TemplateBlueprint) -> Unit,
    val deleteTemplate: (() -> Unit)?
)

/** How many routine-seeded blocks for a given routine are done out of the total scheduled today. */
internal data class RoutineCompletionSummary(
    val doneCount: Int,
    val totalCount: Int
)

internal data class TemplateBlockDraft(
    val id: String,
    val title: String,
    val startText: String,
    val durationText: String,
    val category: String
)

internal data class TemplateSaveResult(
    val templates: List<TemplateBlueprint>,
    val customTemplates: List<TemplateBlueprint>,
    val savedTemplate: TemplateBlueprint
)

private enum class TemplateEditorMode {
    CREATE,
    EDIT
}

internal fun moveTemplateDraftBlock(
    drafts: List<TemplateBlockDraft>,
    index: Int,
    direction: Int
): List<TemplateBlockDraft> {
    val targetIndex = index + direction
    if (index !in drafts.indices || targetIndex !in drafts.indices) return drafts
    val updated = drafts.toMutableList()
    val moved = updated.removeAt(index)
    updated.add(targetIndex, moved)
    return updated
}

internal fun normalizeTemplateDraftBlocks(drafts: List<TemplateBlockDraft>): List<TemplateBlockBlueprint>? {
    if (drafts.isEmpty()) return emptyList()
    return drafts.map { draft ->
        val title = draft.title.trim()
        val startMinute = parseMinuteOfDay(draft.startText) ?: return null
        val durationMinutes = draft.durationText.toIntOrNull()?.coerceIn(5, 240) ?: return null
        if (title.isBlank()) return null
        TemplateBlockBlueprint(
            title = title,
            startMinute = startMinute,
            durationMinutes = durationMinutes,
            category = draft.category.uppercase(Locale.getDefault())
        )
    }
}

internal fun applyTemplateBlueprint(
    template: TemplateBlueprint,
    createBlock: (title: String, startMinute: Int, durationMinutes: Int, category: String) -> Unit
) {
    template.blocks.forEach { block ->
        createBlock(block.title, block.startMinute, block.durationMinutes, block.category)
    }
}

internal fun saveTemplateEdit(
    isCreatingTemplate: Boolean,
    builtInTemplates: List<TemplateBlueprint>,
    customTemplates: List<TemplateBlueprint>,
    templateToEdit: TemplateBlueprint?,
    editedName: String,
    draftBlocks: List<TemplateBlockDraft>,
    idGenerator: () -> String = { UUID.randomUUID().toString() }
): TemplateSaveResult? {
    val normalizedName = editedName.trim()
    if (normalizedName.isBlank()) return null
    val normalizedBlocks = normalizeTemplateDraftBlocks(draftBlocks)?.takeIf { it.isNotEmpty() } ?: return null
    val savedTemplate = if (isCreatingTemplate) {
        TemplateBlueprint(
            id = idGenerator(),
            name = normalizedName,
            blocks = normalizedBlocks
        )
    } else {
        val template = templateToEdit ?: return null
        template.copy(name = normalizedName, blocks = normalizedBlocks)
    }
    val nextCustomTemplates = upsertTemplate(customTemplates, savedTemplate)
    return TemplateSaveResult(
        templates = mergeTemplates(builtInTemplates, nextCustomTemplates),
        customTemplates = nextCustomTemplates,
        savedTemplate = savedTemplate
    )
}

@Composable
internal fun rememberDayDialTemplateState(
    sortedBlocks: List<TimeBlockUiModel>,
    customTemplates: List<TemplateBlueprint>,
    createBlock: (title: String, startMinute: Int, durationMinutes: Int, category: String) -> Unit,
    onPersistTemplate: (TemplateBlueprint) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onApplyTemplateToDate: (TemplateBlueprint, java.time.LocalDate) -> Unit,
    routineCompletions: Map<String, RoutineCompletionSummary>,
    showMessage: (String) -> Unit
): DayDialTemplateState {
    val builtInTemplates = remember { builtInDayDialTemplates() }
    val builtInTemplateIds = remember(builtInTemplates) { builtInTemplates.map { it.id }.toSet() }
    var templateEditorMode by remember { mutableStateOf<TemplateEditorMode?>(null) }
    var editTemplateId by remember { mutableStateOf<String?>(null) }
    var editTemplateName by remember { mutableStateOf("") }
    var draftBlocks by remember { mutableStateOf<List<TemplateBlockDraft>>(emptyList()) }
    val allTemplates = remember(builtInTemplates, customTemplates) {
        mergeTemplates(builtInTemplates, customTemplates)
    }
    val templateToEdit = remember(editTemplateId, allTemplates) {
        allTemplates.firstOrNull { it.id == editTemplateId }
    }

    fun updateDraftBlock(draftId: String, transform: (TemplateBlockDraft) -> TemplateBlockDraft) {
        draftBlocks = draftBlocks.map { current ->
            if (current.id == draftId) transform(current) else current
        }
    }

    val dismissTemplateEditor = {
        templateEditorMode = null
        editTemplateId = null
        editTemplateName = ""
        draftBlocks = emptyList()
    }

    return DayDialTemplateState(
        allTemplates = allTemplates,
        templateToEdit = templateToEdit,
        isTemplateEditorOpen = templateEditorMode != null,
        isCreatingTemplate = templateEditorMode == TemplateEditorMode.CREATE,
        editTemplateName = editTemplateName,
        draftBlocks = draftBlocks,
        setEditTemplateName = { editTemplateName = it },
        updateDraftBlockTitle = { draftId, value ->
            updateDraftBlock(draftId) { current -> current.copy(title = value) }
        },
        updateDraftBlockStart = { draftId, value ->
            updateDraftBlock(draftId) { current -> current.copy(startText = value) }
        },
        updateDraftBlockDuration = { draftId, value ->
            updateDraftBlock(draftId) { current -> current.copy(durationText = value) }
        },
        updateDraftBlockCategory = { draftId, value ->
            updateDraftBlock(draftId) { current -> current.copy(category = value) }
        },
        applyAssistSteps = { steps ->
            draftBlocks = steps.map { step ->
                TemplateBlockDraft(
                    id = UUID.randomUUID().toString(),
                    title = step.title,
                    startText = formatMinuteOfDay(step.startMinute),
                    durationText = step.durationMinutes.coerceIn(5, 240).toString(),
                    category = step.category.uppercase(Locale.getDefault())
                )
            }
        },
        addDraftBlock = {
            val nextStartMinute = nextDraftBlockStartMinute(draftBlocks)
            draftBlocks = draftBlocks + defaultTemplateDraftBlock(nextStartMinute)
        },
        removeDraftBlock = { draftId ->
            draftBlocks = draftBlocks.filterNot { current -> current.id == draftId }
        },
        moveDraftBlockUp = { draftId ->
            val index = draftBlocks.indexOfFirst { it.id == draftId }
            draftBlocks = moveTemplateDraftBlock(draftBlocks, index = index, direction = -1)
        },
        moveDraftBlockDown = { draftId ->
            val index = draftBlocks.indexOfFirst { it.id == draftId }
            draftBlocks = moveTemplateDraftBlock(draftBlocks, index = index, direction = 1)
        },
        dismissTemplateEditor = dismissTemplateEditor,
        saveEditedTemplate = {
            if (editTemplateName.trim().isBlank()) {
                showMessage("Template name can't be blank")
                return@DayDialTemplateState
            }
            if (draftBlocks.isEmpty()) {
                showMessage("Add at least one block to save this template")
                return@DayDialTemplateState
            }
            val result = saveTemplateEdit(
                isCreatingTemplate = templateEditorMode == TemplateEditorMode.CREATE,
                builtInTemplates = builtInTemplates,
                customTemplates = customTemplates,
                templateToEdit = templateToEdit,
                editedName = editTemplateName,
                draftBlocks = draftBlocks
            )
            if (result == null) {
                showMessage("Fix invalid template blocks before saving")
                return@DayDialTemplateState
            }
            onPersistTemplate(result.savedTemplate)
            showMessage("Saved ${result.savedTemplate.name}")
            dismissTemplateEditor()
        },
        saveCurrentAsTemplate = {
            if (sortedBlocks.isEmpty()) {
                showMessage("No blocks to save into a template")
                return@DayDialTemplateState
            }
            templateEditorMode = TemplateEditorMode.CREATE
            editTemplateId = null
            editTemplateName = suggestDayDialTemplateName(allTemplates.map { it.name })
            draftBlocks = sortedBlocks.map { block ->
                TemplateBlockDraft(
                    id = UUID.randomUUID().toString(),
                    title = block.title,
                    startText = formatMinuteOfDay(block.startMinuteOfDay),
                    durationText = block.durationMinutes.toString(),
                    category = inferDayDialCategory(block).uppercase(Locale.getDefault())
                )
            }
        },
        applyTemplate = { template ->
            applyTemplateBlueprint(template, createBlock)
            showMessage("Applied ${template.name}")
        },
        applyTemplateToday = { template ->
            onApplyTemplateToDate(template, java.time.LocalDate.now())
            showMessage("Applied ${template.name} today")
        },
        applyTemplateTomorrow = { template ->
            onApplyTemplateToDate(template, java.time.LocalDate.now().plusDays(1))
            showMessage("Applied ${template.name} tomorrow")
        },
        routineCompletionFor = { template -> routineCompletions[template.id] },
        importBackupText = { backupText ->
            val importedBlocks = parseDayDialBackupBlocks(backupText)
            if (importedBlocks.isEmpty()) {
                showMessage("No valid blocks found in backup")
                return@DayDialTemplateState
            }
            importedBlocks.forEach { block ->
                createBlock(block.title, block.startMinute, block.durationMinutes, block.category)
            }
            showMessage("Imported ${importedBlocks.size} block(s)")
        },
        editTemplate = { template ->
            templateEditorMode = TemplateEditorMode.EDIT
            editTemplateId = template.id
            editTemplateName = template.name
            draftBlocks = template.blocks.map(::draftFromTemplateBlock)
        },
        duplicateTemplate = { template ->
            val copy = template.copy(
                id = UUID.randomUUID().toString(),
                name = "${template.name} Copy"
            )
            onPersistTemplate(copy)
            showMessage("Duplicated ${template.name}")
        },
        deleteTemplate = if (templateToEdit != null && templateToEdit.id !in builtInTemplateIds) {
            {
                onDeleteTemplate(templateToEdit.id)
                showMessage("Deleted ${templateToEdit.name}")
                dismissTemplateEditor()
            }
        } else {
            null
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDialTemplateEditorSheet(
    templateState: DayDialTemplateState,
    assistState: RoutineAssistUiState = RoutineAssistUiState(),
    onRequestAssist: ((RoutineAssistRequest) -> Unit)? = null,
    onClearAssist: (() -> Unit)? = null
) {
    if (!templateState.isTemplateEditorOpen) return
    val templateName = templateState.templateToEdit?.name?.trim()?.takeIf { it.isNotBlank() }
    val blockCount = templateState.draftBlocks.size
    val blockLabel = "$blockCount block${if (blockCount == 1) "" else "s"}"
    val categories = templateState.draftBlocks
        .map { it.category.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val categorySuffix = when {
        categories.isEmpty() -> ""
        categories.size == 1 -> " focused on ${categories[0].lowercase()}."
        else -> " focused across ${categories.size} categories."
    }
    val title = if (templateState.isCreatingTemplate) {
        "Save Template"
    } else {
        templateName?.let { "Edit \"$it\"" } ?: "Edit Template"
    }
    val supportingText = if (templateState.isCreatingTemplate) {
        if (blockCount == 0) {
            "Add at least one block to build this template."
        } else {
            "Save this $blockLabel as a reusable day blueprint$categorySuffix"
        }
    } else {
        if (blockCount == 0) {
            "Rename this template and rebuild its blocks before saving."
        } else {
            "Update ${templateName ?: "the selected template"} with $blockLabel$categorySuffix"
        }
    }

    ChronosFormBottomSheet(
        visible = templateState.isTemplateEditorOpen,
        title = title,
        subtitle = supportingText,
        confirmLabel = "Save",
        onDismiss = templateState.dismissTemplateEditor,
        onConfirm = templateState.saveEditedTemplate,
        enabled = templateState.editTemplateName.trim().isNotEmpty() &&
                templateState.draftBlocks.isNotEmpty(),
        onArchive = if (!templateState.isCreatingTemplate) templateState.deleteTemplate else null,
        archiveLabel = "Delete template"
    ) {
        OutlinedTextField(
            value = templateState.editTemplateName,
            onValueChange = templateState.setEditTemplateName,
            label = { Text("Template name") },
            modifier = Modifier.fillMaxWidth()
        )

        if (onRequestAssist != null) {
            val appliedAssistIds = remember(assistState.suggestions) { mutableStateListOf<String>() }
            val visibleAssistSuggestions = assistState.suggestions.filter { it.id !in appliedAssistIds }

            fun applyRoutineAssistSuggestion(suggestion: RoutineAssistSuggestion) {
                when (suggestion) {
                    is RoutineAssistSuggestion.Title -> templateState.setEditTemplateName(suggestion.title)
                    is RoutineAssistSuggestion.Steps -> templateState.applyAssistSteps(suggestion.steps)
                }
                appliedAssistIds.add(suggestion.id)
                if (assistState.suggestions.all { it.id in appliedAssistIds }) {
                    onClearAssist?.invoke()
                }
            }

            ChronosFilledTonalButton(
                onClick = {
                    onRequestAssist(
                        RoutineAssistRequest(
                            title = templateState.editTemplateName,
                            stepCount = templateState.draftBlocks.size
                        )
                    )
                },
                enabled = !assistState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        assistState.isLoading -> "Drafting suggestions…"
                        visibleAssistSuggestions.isNotEmpty() -> "Refresh suggestions"
                        else -> "Suggest routine with AI"
                    }
                )
            }
            assistState.assistSnapshot?.let { snapshot ->
                GenAiAssistBanner(
                    title = snapshot.bannerTitle,
                    message = snapshot.bannerMessage +
                        " Type or dictate the routine and AI drafts an editable name and step outline. Nothing changes until you tap a suggestion.",
                    ready = snapshot.isReady
                )
            }
            assistState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (visibleAssistSuggestions.isNotEmpty() || assistState.isLoading) {
                ChronosAssistSuggestionChips(
                    suggestions = visibleAssistSuggestions,
                    isLoading = assistState.isLoading,
                    onApply = ::applyRoutineAssistSuggestion,
                    label = { it.label },
                    reason = { it.reason },
                    sourceLabel = { GenAiAssistCopy.routineAssistSourceLabel(it.source) },
                    loadingLabel = "Drafting AI suggestions…"
                )
            }
        }

        if (templateState.draftBlocks.isEmpty()) {
            Text(
                text = "No blocks yet. Add a block to build this template.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        templateState.draftBlocks.forEachIndexed { index, draft ->
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Block ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    DayDialBlockEditorFields(
                        title = draft.title,
                        onTitleChange = { templateState.updateDraftBlockTitle(draft.id, it) },
                        startText = draft.startText,
                        onStartTextChange = { templateState.updateDraftBlockStart(draft.id, it) },
                        durationText = draft.durationText,
                        onDurationTextChange = { templateState.updateDraftBlockDuration(draft.id, it) },
                        category = draft.category,
                        onCategorySelected = { templateState.updateDraftBlockCategory(draft.id, it) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChronosTextButton(
                        onClick = { templateState.moveDraftBlockUp(draft.id) },
                        modifier = Modifier.semantics {
                            contentDescription = templateDraftBlockMoveUpActionLabel(draft, index)
                        }
                    ) {
                        Text(templateDraftBlockMoveUpActionLabel(draft, index))
                    }
                    ChronosTextButton(
                        onClick = { templateState.moveDraftBlockDown(draft.id) },
                        modifier = Modifier.semantics {
                            contentDescription = templateDraftBlockMoveDownActionLabel(draft, index)
                        }
                    ) {
                        Text(templateDraftBlockMoveDownActionLabel(draft, index))
                    }
                    ChronosTextButton(
                        onClick = { templateState.removeDraftBlock(draft.id) },
                        modifier = Modifier.semantics {
                            contentDescription = templateDraftBlockDeleteActionLabel(draft, index)
                        }
                    ) {
                        Text(
                            templateDraftBlockDeleteActionLabel(draft, index),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        }

        ChronosTextButton(onClick = templateState.addDraftBlock) {
            Text(templateDraftAddBlockActionLabel(blockCount))
        }
    }
}

internal fun templateDraftAddBlockActionLabel(blockCount: Int): String {
    return if (blockCount <= 1) {
        "Add block"
    } else {
        "Add another block"
    }
}

internal fun templateDraftBlockMoveUpActionLabel(draft: TemplateBlockDraft, index: Int): String {
    return "Move ${templateDraftBlockActionTarget(draft, index)} up"
}

internal fun templateDraftBlockMoveDownActionLabel(draft: TemplateBlockDraft, index: Int): String {
    return "Move ${templateDraftBlockActionTarget(draft, index)} down"
}

internal fun templateDraftBlockDeleteActionLabel(draft: TemplateBlockDraft, index: Int): String {
    return "Delete ${templateDraftBlockActionTarget(draft, index)}"
}

private fun templateDraftBlockActionTarget(draft: TemplateBlockDraft, index: Int): String {
    val title = draft.title.trim().ifBlank { "Untitled" }
    return "$title block ${index + 1}"
}

internal fun suggestDayDialTemplateName(existingNames: Collection<String>): String {
    val baseName = "My Day Template"
    val normalizedExisting = existingNames
        .map { name -> name.trim().lowercase(Locale.getDefault()) }
        .toSet()
    if (baseName.lowercase(Locale.getDefault()) !in normalizedExisting) {
        return baseName
    }
    var suffix = 2
    while (true) {
        val candidate = "$baseName $suffix"
        if (candidate.lowercase(Locale.getDefault()) !in normalizedExisting) {
            return candidate
        }
        suffix++
    }
}

private fun mergeTemplates(
    builtInTemplates: List<TemplateBlueprint>,
    customTemplates: List<TemplateBlueprint>
): List<TemplateBlueprint> {
    val overridesById = customTemplates.associateBy { it.id }
    val mergedBuiltIns = builtInTemplates.map { template ->
        overridesById[template.id] ?: template
    }
    val customOnly = customTemplates.filter { custom ->
        builtInTemplates.none { template -> template.id == custom.id }
    }
    return mergedBuiltIns + customOnly
}

private fun upsertTemplate(
    templates: List<TemplateBlueprint>,
    updated: TemplateBlueprint
): List<TemplateBlueprint> {
    return if (templates.any { it.id == updated.id }) {
        templates.map { current -> if (current.id == updated.id) updated else current }
    } else {
        templates + updated
    }
}

private fun draftFromTemplateBlock(block: TemplateBlockBlueprint): TemplateBlockDraft =
    TemplateBlockDraft(
        id = UUID.randomUUID().toString(),
        title = block.title,
        startText = formatMinuteOfDay(block.startMinute),
        durationText = block.durationMinutes.toString(),
        category = block.category
    )

private fun nextDraftBlockStartMinute(drafts: List<TemplateBlockDraft>): Int {
    val lastDraft = drafts.lastOrNull() ?: return 9 * 60
    val startMinute = parseMinuteOfDay(lastDraft.startText) ?: return 9 * 60
    val durationMinutes = lastDraft.durationText.toIntOrNull()?.coerceIn(5, 240) ?: 25
    return (startMinute + durationMinutes) % 1440
}

private fun defaultTemplateDraftBlock(startMinute: Int): TemplateBlockDraft =
    TemplateBlockDraft(
        id = UUID.randomUUID().toString(),
        title = "New Block",
        startText = formatMinuteOfDay(startMinute),
        durationText = "25",
        category = "WORK"
    )

internal fun builtInDayDialTemplates(): List<TemplateBlueprint> = listOf(
    TemplateBlueprint(
        id = "tpl_workday",
        name = "Workday",
        blocks = listOf(
            TemplateBlockBlueprint("Morning Routine", 8 * 60, 45, "PERSONAL"),
            TemplateBlockBlueprint("Deep Work", 9 * 60, 90, "WORK"),
            TemplateBlockBlueprint("Admin", 11 * 60, 45, "WORK"),
            TemplateBlockBlueprint("Lunch", 12 * 60 + 30, 45, "PERSONAL"),
            TemplateBlockBlueprint("Meetings", 14 * 60, 90, "MEETING")
        )
    ),
    TemplateBlueprint(
        id = "tpl_study",
        name = "Study Day",
        blocks = listOf(
            TemplateBlockBlueprint("Warm-up Focus", 9 * 60, 45, "WORK"),
            TemplateBlockBlueprint("Deep Study", 10 * 60, 90, "WORK"),
            TemplateBlockBlueprint("Break", 11 * 60 + 45, 15, "BREAK"),
            TemplateBlockBlueprint("Read", 12 * 60 + 15, 75, "WORK"),
            TemplateBlockBlueprint("Review", 16 * 60, 60, "WORK")
        )
    ),
    TemplateBlueprint(
        id = "tpl_weekend",
        name = "Weekend",
        blocks = listOf(
            TemplateBlockBlueprint("Morning Stretch", 8 * 60 + 30, 30, "PERSONAL"),
            TemplateBlockBlueprint("Personal Admin", 10 * 60, 60, "WORK"),
            TemplateBlockBlueprint("Deep Focus", 14 * 60, 120, "WORK"),
            TemplateBlockBlueprint("Evening Wind-down", 19 * 60, 60, "PERSONAL")
        )
    ),
    TemplateBlueprint(
        id = "tpl_recovery",
        name = "Recovery",
        blocks = listOf(
            TemplateBlockBlueprint("Low Energy Work", 10 * 60, 50, "WORK"),
            TemplateBlockBlueprint("Recovery Break", 12 * 60, 40, "RECOVERY"),
            TemplateBlockBlueprint("Stretch", 16 * 60 + 30, 30, "PERSONAL"),
            TemplateBlockBlueprint("Light Admin", 20 * 60, 30, "WORK")
        )
    )
)
