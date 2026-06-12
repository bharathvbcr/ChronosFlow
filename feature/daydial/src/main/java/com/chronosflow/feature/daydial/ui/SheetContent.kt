package com.chronosflow.feature.daydial

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.ai.genai.NanoModelStatus
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosWarningBanner
import com.chronosflow.feature.daydial.model.AppearanceMode
import com.chronosflow.feature.daydial.model.ReviewDetailSection
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.model.TemplateBlockBlueprint
import com.chronosflow.feature.daydial.ui.ActionGrid
import com.chronosflow.feature.daydial.ui.AiReviewSheet
import com.chronosflow.feature.daydial.ui.CheckboxSetting
import com.chronosflow.feature.daydial.ui.DailyReviewHeader
import com.chronosflow.feature.daydial.ui.JournalEntrySheetContent
import com.chronosflow.feature.daydial.ui.PrivacyModeSelector
import com.chronosflow.feature.daydial.ui.SleepLogSheetContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.core.ui.theme.categoryColor
import com.chronosflow.feature.daydial.CalendarConnectionState
import com.chronosflow.feature.daydial.CalendarPermissionStatus
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun SheetContent(
    target: SheetTarget,
    selectedBlock: TimeBlockUiModel?,
    selectedDate: LocalDate,
    timeBlocks: List<TimeBlockUiModel>,
    missedBlocks: List<TimeBlockUiModel>,
    manualMissedBlockIds: Set<String> = emptySet(),
    suggestedBlocks: List<TimeBlockUiModel>,
    review: DailyReview,
    privacyMode: PrivacyMode,
    isGenerating: Boolean,
    genAiRuntimeStatus: GenAiRuntimeStatus,
    aiPlanResult: String?,
    explainPlan: String?,
    explainPlanSource: AssistGenAiSource?,
    repairPlanResult: String? = null,
    aiPlanGoalPrefill: String? = null,
    aiPlanSuggestedGoals: List<String> = emptyList(),
    focusElapsedSeconds: Long,
    syncStatus: String,
    blockStartReminders: Boolean,
    breakReminders: Boolean,
    missedAlerts: Boolean,
    endDayReviewReminder: Boolean,
    reminderScheduleStatus: String,
    medicationReliabilityStatus: String,
    dynamicColorEnabled: Boolean,
    glassSurfacesEnabled: Boolean,
    appearanceMode: AppearanceMode,
    reduceMotionEnabled: Boolean,
    highContrastEnabled: Boolean,
    calendarPermissionStatus: CalendarPermissionStatus,
    showCalendarPermissionRationale: Boolean,
    calendarConnectionState: CalendarConnectionState,
    onDismiss: () -> Unit,
    onDismissCalendarPermissionRationale: () -> Unit,
    onDeleteBlock: () -> Unit,
    onStartFocus: (String) -> Unit,
    onCreateBlock: (String, Int, Int, String) -> Unit,
    onGeneratePlan: (List<String>) -> Unit,
    onApplyAiSuggestions: () -> Unit,
    onDismissAiSuggestions: () -> Unit,
    onAcceptAiSuggestion: (String) -> Unit,
    onRejectAiSuggestion: (String) -> Unit,
    onModifyAiSuggestion: (String, String, Int, Int) -> Unit,
    onSetPrivacyMode: (PrivacyMode) -> Unit,
    onAdjustFocus: (Int) -> Unit,
    onMarkComplete: (String) -> Unit,
    onMarkMissed: (String) -> Unit,
    onUndoMissed: (String) -> Unit = {},
    onDuplicateBlock: (String) -> Unit,
    onUpdateBlockDetails: (String, String, Int, Int, String, Boolean, Boolean) -> Unit,
    onExportBlockToCalendar: (String) -> Unit,
    onRefreshCalendarExport: (String) -> Unit,
    onRemoveCalendarExport: (String) -> Unit,
    onOpenCalendarSettings: () -> Unit,
    dataExportState: DataExportState,
    onCreateDataExport: () -> Unit,
    onImportBackup: (String) -> Unit,
    onFinishFocus: (String) -> Unit,
    onEndDay: () -> Unit,
    showMessage: (String) -> Unit,
    journalEntry: com.chronosflow.core.domain.model.JournalEntry? = null,
    sleepTrack: com.chronosflow.core.domain.model.SleepTrack? = null,
    moodSummary: String? = null,
    onSaveJournal: (date: LocalDate, body: String, promptType: String?) -> Unit = { _, _, _ -> },
    onSaveSleep: (
        date: LocalDate,
        quality: Int,
        startMinute: Int?,
        endMinute: Int?,
        interruptions: Int,
        notes: String?
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
        when (target) {
            is SheetTarget.BlockEditor -> {
                selectedBlock?.let { block ->
                    val allDayCalendarImport = block.isAllDayCalendarImport()
                    var title by rememberSaveable(block.id) { mutableStateOf(block.title) }
                    var start by rememberSaveable(block.id) { mutableStateOf(formatMinute(block.startMinuteOfDay)) }
                    var duration by rememberSaveable(block.id) { mutableStateOf(block.durationMinutes.toString()) }
                    var category by rememberSaveable(block.id) { mutableStateOf(inferCategory(block).uppercase(Locale.getDefault())) }
                    var locked by rememberSaveable(block.id) { mutableStateOf(block.isLocked) }
                    var protectedBlock by rememberSaveable(block.id) { mutableStateOf(block.isProtected) }

                    Text(
                        block.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (allDayCalendarImport) {
                        ChronosWarningBanner(
                            title = "All-day calendar note",
                            message = sheetBlockAllDayCalendarNoteMessage(block)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onDeleteBlock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = sheetBlockDeleteActionLabel(block) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(sheetBlockDeleteActionLabel(block), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                    DayDialBlockEditorFields(
                        title = title,
                        onTitleChange = { title = it },
                        startText = start,
                        onStartTextChange = { start = it },
                        durationText = duration,
                        onDurationTextChange = { duration = it },
                        category = category,
                        onCategorySelected = { category = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CheckboxSetting("Locked", locked, onCheckedChange = { locked = it })
                    CheckboxSetting("Protected focus", protectedBlock, onCheckedChange = { protectedBlock = it })
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Calendar export",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = calendarConnectionStatusMessage(calendarPermissionStatus, calendarConnectionState),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (calendarConnectionState.isWorking) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        calendarPermissionStatus.permanentlyDenied -> {
                            ChronosWarningBanner(
                                title = if (calendarPermissionStatus.readGranted) {
                                    "Calendar export access is off"
                                } else {
                                    "Calendar access is off"
                                },
                                message = if (calendarPermissionStatus.readGranted) {
                                    "Calendar imports are connected. Open app settings to re-enable linked exports."
                                } else {
                                    "Open app settings to re-enable calendar imports and linked exports."
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = onOpenCalendarSettings, modifier = Modifier.fillMaxWidth()) {
                                Text("Open calendar settings")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        showCalendarPermissionRationale && !calendarPermissionStatus.allGranted -> {
                            ChronosWarningBanner(
                                title = "Calendar permission required",
                                message = "ChronosFlow needs calendar access to export this block and keep later edits synced to the same device event."
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        if (block.calendarEventId == null) {
                                            onExportBlockToCalendar(block.id)
                                        } else {
                                            onRefreshCalendarExport(block.id)
                                        }
                                    },
                                    enabled = !calendarConnectionState.isWorking,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        sheetBlockCalendarActionLabel(
                                            block,
                                            if (block.calendarEventId == null) SheetBlockCalendarAction.Export else SheetBlockCalendarAction.Update
                                        )
                                    )
                                }
                                TextButton(
                                    onClick = onDismissCalendarPermissionRationale,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Not now")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        block.calendarEventId == null -> {
                            OutlinedButton(
                                onClick = { onExportBlockToCalendar(block.id) },
                                enabled = !calendarConnectionState.isWorking,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = sheetBlockCalendarActionLabel(
                                            block,
                                            SheetBlockCalendarAction.Export
                                        )
                                    }
                            ) {
                                Text(
                                    sheetBlockCalendarActionLabel(block, SheetBlockCalendarAction.Export),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        else -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { onRefreshCalendarExport(block.id) },
                                    enabled = !calendarConnectionState.isWorking,
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = sheetBlockCalendarActionLabel(
                                                block,
                                                SheetBlockCalendarAction.Update
                                            )
                                        }
                                ) {
                                    Text(
                                        sheetBlockCalendarActionLabel(block, SheetBlockCalendarAction.Update),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onRemoveCalendarExport(block.id) },
                                    enabled = !calendarConnectionState.isWorking,
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = sheetBlockCalendarActionLabel(
                                                block,
                                                SheetBlockCalendarAction.Remove
                                            )
                                        }
                                ) {
                                    Text(
                                        sheetBlockCalendarActionLabel(block, SheetBlockCalendarAction.Remove),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Premium Action Buttons Row (Start Focus, Complete, Missed)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onStartFocus(block.id) },
                            modifier = Modifier
                                .weight(1.2f)
                                .semantics { contentDescription = sheetBlockStartFocusActionLabel(block) }
                        ) {
                            Text(sheetBlockStartFocusActionLabel(block), fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = { onMarkComplete(block.id) },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = sheetBlockCompleteActionLabel(block) }
                        ) {
                            Text(sheetBlockCompleteActionLabel(block), fontWeight = FontWeight.SemiBold)
                        }
                        if (block.id in manualMissedBlockIds) {
                            OutlinedButton(
                                onClick = {
                                    onUndoMissed(block.id)
                                    showMessage("Missed mark cleared")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = sheetBlockUndoMissedActionLabel(block) }
                            ) {
                                Text(sheetBlockUndoMissedActionLabel(block), fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onMarkMissed(block.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = sheetBlockMissedActionLabel(block) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(sheetBlockMissedActionLabel(block), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onDuplicateBlock(block.id) },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = sheetBlockDuplicateActionLabel(block) }
                        ) {
                            Text(sheetBlockDuplicateActionLabel(block), fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                onUpdateBlockDetails(
                                    block.id,
                                    title.ifBlank { block.title },
                                    parseMinute(start) ?: block.startMinuteOfDay,
                                    duration.toIntOrNull() ?: block.durationMinutes,
                                    category.ifBlank { inferCategory(block) },
                                    locked,
                                    protectedBlock
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = sheetBlockSaveActionLabel(block) }
                        ) {
                            Text(sheetBlockSaveActionLabel(block), fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onDeleteBlock,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = sheetBlockDeleteActionLabel(block) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(sheetBlockDeleteActionLabel(block), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    }
                }
            }
            is SheetTarget.NewBlock -> {
                val defaultStartMinute = DialUtils.snapToIncrement(
                    target.startMinute
                        ?: timeBlocks.lastOrNull()?.let { it.startMinuteOfDay + it.durationMinutes }
                        ?: 9 * 60
                )
                var title by rememberSaveable(target.startMinute, target.title) {
                    mutableStateOf(target.title)
                }
                var start by rememberSaveable(target.startMinute) {
                    mutableStateOf(formatMinute(defaultStartMinute))
                }
                var duration by rememberSaveable(
                    target.startMinute,
                    target.title,
                    target.category,
                    target.durationMinutes
                ) {
                    mutableStateOf(newBlockSheetInitialDurationText(target))
                }
                var category by rememberSaveable(target.category) {
                    mutableStateOf(target.category.uppercase(Locale.getDefault()))
                }

                val startMin = parseMinute(start) ?: defaultStartMinute
                val durMin = duration.toIntOrNull()?.coerceIn(5, 240) ?: 25

                Text(
                    "New Block",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))

                DayDialBlockEditorFields(
                    title = title,
                    onTitleChange = { title = it },
                    startText = start,
                    onStartTextChange = { start = it },
                    durationText = duration,
                    onDurationTextChange = { duration = it },
                    category = category,
                    onCategorySelected = { category = it }
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(sheetNewBlockCancelActionLabel(title, category), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            onCreateBlock(
                                title.ifBlank { "Focus Block" },
                                startMin,
                                durMin,
                                category.uppercase(Locale.getDefault())
                            )
                        },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(sheetNewBlockSaveActionLabel(title, category), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            SheetTarget.QuickAdd -> {
                Text(
                    "Quick Add",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                ActionGrid(
                    listOf(
                        "30m Focus" to { onCreateBlock("Quick Focus", 9 * 60, 30, "WORK") },
                        "15m Break" to { onCreateBlock("Break", 11 * 60, 15, "BREAK") },
                        "Fill next gap" to { onCreateBlock("Recovered Focus", 10 * 60, 25, "RECOVERY") },
                        "Generate remaining day" to { onGeneratePlan(listOf("Generate remaining day for $selectedDate")) }
                    )
                )
            }
            SheetTarget.AiPlan -> {
                var goal by rememberSaveable { mutableStateOf("") }
                var hours by rememberSaveable { mutableStateOf("8") }
                var breakFreq by remember { mutableFloatStateOf(0.5f) }
                var focusPref by remember { mutableFloatStateOf(0.7f) }

                LaunchedEffect(aiPlanGoalPrefill) {
                    val prefill = aiPlanGoalPrefill?.trim().orEmpty()
                    if (prefill.isNotEmpty()) {
                        goal = prefill
                    }
                }

                Text(
                    "AI Planning",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                ChronosWarningBanner(
                    title = GenAiAssistCopy.bannerTitle(privacyMode, genAiRuntimeStatus),
                    message = GenAiAssistCopy.bannerMessage(privacyMode, genAiRuntimeStatus)
                )
                if (aiPlanSuggestedGoals.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ChronosListCard {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Suggested plan goals",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            aiPlanSuggestedGoals.forEach { suggested ->
                                Text(
                                    text = "• $suggested",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = "Review the goal below, then tap Generate Plan.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(goal, { goal = it }, label = { Text("Goal for the day") }, placeholder = { Text("e.g. Finish project X, go for a run") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hours, { hours = it }, label = { Text("Available Work Hours") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(16.dp))
                Text("Break Frequency", style = MaterialTheme.typography.labelMedium)
                Slider(value = breakFreq, onValueChange = { breakFreq = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rare", style = MaterialTheme.typography.labelSmall)
                    Text("Often", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(16.dp))
                Text("Focus Preference", style = MaterialTheme.typography.labelMedium)
                Slider(value = focusPref, onValueChange = { focusPref = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Admin", style = MaterialTheme.typography.labelSmall)
                    Text("Deep Work", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(12.dp))
                PrivacyModeSelector(privacyMode, onSetPrivacyMode)

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onGeneratePlan(
                            buildList {
                                add("Goal:$goal")
                                aiPlanSuggestedGoals.forEach { add(it) }
                                add("Hours:$hours")
                                add("BreakFreq:${(breakFreq * 100).toInt()}%")
                                add("FocusPref:${(focusPref * 100).toInt()}%")
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isGenerating) "Generating Schedule..." else "Generate Plan") }
                if (aiPlanResult != null || explainPlan != null || repairPlanResult != null) {
                    Spacer(Modifier.height(12.dp))
                    ChronosListCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            aiPlanResult?.let {
                                Text("Latest suggestion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            explainPlan?.let { explanation ->
                                Text("Plan explanation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(explanation, style = MaterialTheme.typography.bodySmall)
                                explainPlanSource?.let { source ->
                                    Text(
                                        text = GenAiAssistCopy.assistSourceLabel(source),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            repairPlanResult?.let { repair ->
                                Text("Conflict repair steps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(repair, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (suggestedBlocks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    AiReviewSheet(
                        suggestions = suggestedBlocks,
                        onApplyAll = {
                            onApplyAiSuggestions()
                            showMessage("Applied all AI suggestions")
                            onDismiss()
                        },
                        onDismissAll = {
                            onDismissAiSuggestions()
                            showMessage("Dismissed all AI suggestions")
                            onDismiss()
                        },
                        onAccept = { suggestionId ->
                            onAcceptAiSuggestion(suggestionId)
                            showMessage("Accepted AI suggestion")
                        },
                        onReject = { suggestionId ->
                            onRejectAiSuggestion(suggestionId)
                            showMessage("Rejected AI suggestion")
                        },
                        onModify = { suggestionId, title, startMinute, duration ->
                            onModifyAiSuggestion(suggestionId, title, startMinute, duration)
                            showMessage("Updated AI suggestion")
                        }
                    )
                }
            }
            SheetTarget.MissedBlocks -> {
                Text(
                    "Missed Blocks",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                if (missedBlocks.isEmpty()) {
                    Text("No manually missed blocks.")
                } else {
                    missedBlocks.forEach { block ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    block.title,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = { Text("${formatMinute(block.startMinuteOfDay)} - ${block.durationMinutes}m") },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            onUndoMissed(block.id)
                                            showMessage("Missed mark cleared")
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = sheetMissedListUndoActionLabel(block)
                                        }
                                    ) { Text("Undo") }
                                    TextButton(
                                        onClick = {
                                            onDuplicateBlock(block.id)
                                            showMessage("Duplicated for reschedule")
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = sheetMissedListRescheduleActionLabel(block)
                                        }
                                    ) { Text("Reschedule") }
                                }
                            }
                        )
                    }
                }
            }
            is SheetTarget.ReviewDetails -> {
                val sorted = timeBlocks.sortedBy { it.startMinuteOfDay }
                val title = when (target.section) {
                    ReviewDetailSection.PLANNED -> "Planned Breakdown"
                    ReviewDetailSection.ACTUAL -> "Actual Log"
                    ReviewDetailSection.MISSED -> "Missed Recovery"
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                when (target.section) {
                    ReviewDetailSection.PLANNED -> {
                        Text("Planned total: ${formatMinutesToLabel(review.plannedMinutes)}")
                        Spacer(Modifier.height(8.dp))
                        if (sorted.isEmpty()) {
                            Text("No planned blocks yet.")
                        } else {
                            sorted.forEach { block ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            block.title,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = { Text("${formatMinute(block.startMinuteOfDay)} - ${block.durationMinutes}m - ${inferCategory(block)}") },
                                    trailingContent = {
                                        TextButton(
                                            onClick = { onDuplicateBlock(block.id) },
                                            modifier = Modifier.semantics {
                                                contentDescription = sheetReviewCopyActionLabel(block)
                                            }
                                        ) { Text("Copy") }
                                    }
                                )
                            }
                        }
                    }
                    ReviewDetailSection.ACTUAL -> {
                        Text("Actual total: ${formatMinutesToLabel(review.actualMinutes)}")
                        Spacer(Modifier.height(8.dp))
                        if (review.actualMinutes <= 0) {
                            Text("No completed focus time yet.")
                        } else {
                            sorted.forEach { block ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            block.title,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = { Text("${formatMinute(block.startMinuteOfDay)} - ${block.durationMinutes}m") },
                                    trailingContent = { Text("Logged") }
                                )
                            }
                        }
                    }
                    ReviewDetailSection.MISSED -> {
                        Text("Missed total: ${formatMinutesToLabel(review.missedMinutes)}")
                        Spacer(Modifier.height(8.dp))
                        if (missedBlocks.isEmpty()) {
                            Text("No missed blocks.")
                        } else {
                            missedBlocks.forEach { block ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            block.title,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = { Text("${formatMinute(block.startMinuteOfDay)} - ${block.durationMinutes}m") },
                                    trailingContent = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    onUndoMissed(block.id)
                                                    showMessage("Missed mark cleared")
                                                },
                                                modifier = Modifier.semantics {
                                                    contentDescription = sheetMissedListUndoActionLabel(block)
                                                }
                                            ) { Text("Undo") }
                                            TextButton(
                                                onClick = {
                                                    onDuplicateBlock(block.id)
                                                    showMessage("Copied for recovery")
                                                },
                                                modifier = Modifier.semantics {
                                                    contentDescription = sheetReviewRecoverActionLabel(block)
                                                }
                                            ) { Text("Recover") }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            SheetTarget.ExportData -> {
                DataExportPanel(
                    state = dataExportState,
                    onCreateExport = onCreateDataExport,
                    title = "Export Data"
                )
            }
            SheetTarget.ImportBackup -> {
                var backupText by rememberSaveable { mutableStateOf("") }
                val parsedCount = remember(backupText) { parseBackupBlocks(backupText).size }
                Text(
                    "Import Backup",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = backupText,
                    onValueChange = { backupText = it },
                    label = { Text("Paste ChronosFlow backup text") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("$parsedCount valid block(s) detected", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { onImportBackup(backupText) },
                    enabled = parsedCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import Blocks") }
            }
            SheetTarget.WeeklySummary -> {
                Text(
                    "Weekly Summary",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(buildWeeklySummary(selectedDate = selectedDate, blocks = timeBlocks, missedBlocks = missedBlocks, review = review))
            }
            SheetTarget.Diagnostics -> {
                Text(
                    "Diagnostics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    buildDiagnostics(
                        selectedDate,
                        timeBlocks,
                        missedBlocks,
                        privacyMode,
                        syncStatus,
                        blockStartReminders,
                        breakReminders,
                        missedAlerts,
                        endDayReviewReminder,
                        dynamicColorEnabled,
                        glassSurfacesEnabled,
                        appearanceMode,
                        reduceMotionEnabled,
                        highContrastEnabled
                    )
                )
            }
            SheetTarget.EndOfDayReview -> {
                Text(
                    "Day Review",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                DailyReviewHeader(review)
                Text("Blocks: ${timeBlocks.size}")
                Text("Missed: ${missedBlocks.size}")
                Button(onClick = onEndDay, modifier = Modifier.fillMaxWidth()) { Text("Carry missed to tomorrow") }
            }
            SheetTarget.FocusSettings -> {
                Text(
                    "Focus Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text("Actual focus time: ${formatSeconds(focusElapsedSeconds)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    AssistChip(
                        onClick = { onAdjustFocus(5) },
                        label = { Text("+5m") },
                        modifier = Modifier.semantics { contentDescription = sheetFocusAdjustmentActionLabel(5) }
                    )
                    AssistChip(
                        onClick = { onAdjustFocus(10) },
                        label = { Text("+10m") },
                        modifier = Modifier.semantics { contentDescription = sheetFocusAdjustmentActionLabel(10) }
                    )
                    AssistChip(
                        onClick = { onAdjustFocus(15) },
                        label = { Text("+15m") },
                        modifier = Modifier.semantics { contentDescription = sheetFocusAdjustmentActionLabel(15) }
                    )
                    AssistChip(
                        onClick = { onAdjustFocus(-5) },
                        label = { Text("-5m") },
                        modifier = Modifier.semantics { contentDescription = sheetFocusAdjustmentActionLabel(-5) }
                    )
                    AssistChip(
                        onClick = { onAdjustFocus(-10) },
                        label = { Text("-10m") },
                        modifier = Modifier.semantics { contentDescription = sheetFocusAdjustmentActionLabel(-10) }
                    )
                }
                PrivacyModeSelector(privacyMode, onSetPrivacyMode)
                Text(reminderScheduleStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(medicationReliabilityStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Block starts: ${if (blockStartReminders) "on" else "off"}")
                Text("Breaks: ${if (breakReminders) "on" else "off"}")
                Text("Missed alerts: ${if (missedAlerts) "on" else "off"}")
                Text("End-of-day review: ${if (endDayReviewReminder) "on" else "off"}")
            }
            is SheetTarget.Journal -> JournalEntrySheetContent(
                date = target.date,
                existing = journalEntry,
                moodSummary = moodSummary,
                onSave = { body, promptType ->
                    onSaveJournal(target.date, body, promptType)
                    onDismiss()
                }
            )
            is SheetTarget.SleepLog -> SleepLogSheetContent(
                date = target.date,
                existing = sleepTrack,
                onSave = { quality, startMinute, endMinute, interruptions, notes ->
                    onSaveSleep(target.date, quality, startMinute, endMinute, interruptions, notes)
                    onDismiss()
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.CenterHorizontally)
                .semantics { contentDescription = sheetCloseActionLabel(target, selectedBlock) }
        ) { Text(sheetCloseActionLabel(target, selectedBlock)) }
    }
}

internal enum class SheetBlockCalendarAction {
    Export,
    Update,
    Remove
}

internal fun sheetBlockCalendarActionLabel(
    block: TimeBlockUiModel,
    action: SheetBlockCalendarAction
): String {
    val target = sheetBlockActionTarget(block)
    return when (action) {
        SheetBlockCalendarAction.Export -> "Export $target to calendar"
        SheetBlockCalendarAction.Update -> "Update calendar export for $target"
        SheetBlockCalendarAction.Remove -> "Remove calendar export for $target"
    }
}

internal fun sheetBlockStartFocusActionLabel(block: TimeBlockUiModel): String =
    "Start focus for ${sheetBlockActionTarget(block)}"

internal fun sheetBlockCompleteActionLabel(block: TimeBlockUiModel): String =
    "Complete ${sheetBlockActionTarget(block)}"

internal fun sheetBlockMissedActionLabel(block: TimeBlockUiModel): String =
    "Mark ${sheetBlockActionTarget(block)} missed"

internal fun sheetBlockUndoMissedActionLabel(block: TimeBlockUiModel): String =
    "Undo missed mark for ${sheetBlockActionTarget(block)}"

internal fun sheetBlockDuplicateActionLabel(block: TimeBlockUiModel): String =
    "Duplicate ${sheetBlockActionTarget(block)} block"

internal fun sheetBlockSaveActionLabel(block: TimeBlockUiModel): String =
    "Save changes to ${sheetBlockActionTarget(block)}"

internal fun sheetBlockDeleteActionLabel(block: TimeBlockUiModel): String =
    if (block.isAllDayCalendarImport()) {
        "Remove ${sheetBlockActionTarget(block)} calendar note from DayDial"
    } else {
        "Delete ${sheetBlockActionTarget(block)} block"
    }

internal fun sheetBlockAllDayCalendarNoteMessage(block: TimeBlockUiModel): String =
    "${sheetBlockActionTarget(block)} stays visible all day without blocking schedule time, reminders, or focus suggestions."

internal fun sheetMissedListUndoActionLabel(block: TimeBlockUiModel): String =
    sheetBlockUndoMissedActionLabel(block)

internal fun sheetMissedListRescheduleActionLabel(block: TimeBlockUiModel): String =
    "Reschedule ${sheetBlockActionTarget(block)}"

internal fun sheetReviewCopyActionLabel(block: TimeBlockUiModel): String =
    "Copy ${sheetBlockActionTarget(block)} from planned breakdown"

internal fun sheetReviewRecoverActionLabel(block: TimeBlockUiModel): String =
    "Recover ${sheetBlockActionTarget(block)} from missed recovery"

internal fun sheetFocusAdjustmentActionLabel(minutes: Int): String = when {
    minutes > 0 -> "Add $minutes minutes to focus time"
    minutes < 0 -> "Remove ${-minutes} minutes from focus time"
    else -> "Keep focus time unchanged"
}

internal fun sheetCloseActionLabel(target: SheetTarget, selectedBlock: TimeBlockUiModel?): String = when (target) {
    is SheetTarget.BlockEditor -> selectedBlock?.let { "Close ${sheetBlockActionTarget(it)} editor" } ?: "Close block editor"
    is SheetTarget.NewBlock -> "Close new block"
    is SheetTarget.QuickAdd -> "Close quick add"
    is SheetTarget.AiPlan -> "Close AI planning"
    is SheetTarget.MissedBlocks -> "Close missed blocks"
    is SheetTarget.EndOfDayReview -> "Close day review"
    is SheetTarget.FocusSettings -> "Close focus settings"
    is SheetTarget.ExportData -> "Close export data"
    is SheetTarget.ImportBackup -> "Close import backup"
    is SheetTarget.WeeklySummary -> "Close weekly summary"
    is SheetTarget.Diagnostics -> "Close diagnostics"
    is SheetTarget.Journal -> "Close journal"
    is SheetTarget.SleepLog -> "Close sleep log"
    is SheetTarget.ReviewDetails -> "Close ${target.section.name.lowercase().replaceFirstChar { it.uppercase() }} review"
}

internal fun sheetNewBlockCancelActionLabel(title: String, category: String): String {
    val chip = category.trim()
    return when {
        title.isNotBlank() -> "Cancel ${title.trim()} creation"
        chip.isNotBlank() -> "Cancel ${chip.lowercase(Locale.getDefault())} block creation"
        else -> "Cancel new block"
    }
}

internal fun newBlockSheetInitialDurationText(target: SheetTarget.NewBlock): String =
    (target.durationMinutes?.coerceIn(5, 240) ?: 25).toString()

internal fun sheetNewBlockSaveActionLabel(title: String, category: String): String {
    val chip = category.trim()
    return when {
        title.isNotBlank() -> "Save ${title.trim()} block"
        chip.isNotBlank() -> "Save ${chip.lowercase(Locale.getDefault())} block"
        else -> "Save new block"
    }
}

private fun sheetBlockActionTarget(block: TimeBlockUiModel): String =
    block.title.trim().ifBlank { "Untitled" }

private fun formatMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    val h = (normalized / 60) % 24
    val m = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", h, m)
}

private fun parseMinute(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun formatMinutesToLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
internal fun DataExportPanel(
    state: DataExportState,
    onCreateExport: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = "Export Data"
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            "Exports the local database, schema, settings, and app preference files as JSON.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.lastFileName?.let { fileName ->
            Text(
                fileName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            state.errorMessage ?: state.summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.errorMessage == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Button(
            onClick = onCreateExport,
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isExporting) "Preparing Export" else "Export ChronosFlow Data")
        }
    }
}

private fun parseBackupBlocks(text: String): List<TemplateBlockBlueprint> =
    parseDayDialBackupBlocks(text)

private fun buildWeeklySummary(
    selectedDate: LocalDate,
    blocks: List<TimeBlockUiModel>,
    missedBlocks: List<TimeBlockUiModel>,
    review: DailyReview
): String = buildString {
    val focusBlocks = blocks.count { inferCategory(it).equals("Work", ignoreCase = true) }
    appendLine("Week ending $selectedDate")
    appendLine("Planned: ${formatMinutesToLabel(review.plannedMinutes)}")
    appendLine("Actual: ${formatMinutesToLabel(review.actualMinutes)}")
    appendLine("Missed: ${formatMinutesToLabel(review.missedMinutes)}")
    val completionRate = if (review.plannedMinutes == 0) 0 else ((review.actualMinutes.toFloat() / review.plannedMinutes.toFloat()) * 100).toInt()
    appendLine("Completion: $completionRate%")
    appendLine("Focus blocks: $focusBlocks")
    appendLine("Recovery candidates: ${missedBlocks.size}")
    if (missedBlocks.isNotEmpty()) {
        appendLine("Top recovery:")
        missedBlocks.take(3).forEach { appendLine("- ${it.title} at ${formatMinute(it.startMinuteOfDay)}") }
    }
}

private fun buildDiagnostics(
    selectedDate: LocalDate,
    blocks: List<TimeBlockUiModel>,
    missedBlocks: List<TimeBlockUiModel>,
    privacyMode: PrivacyMode,
    syncStatus: String,
    blockStartReminders: Boolean,
    breakReminders: Boolean,
    missedAlerts: Boolean,
    endDayReviewReminder: Boolean,
    dynamicColorEnabled: Boolean,
    glassSurfacesEnabled: Boolean,
    appearanceMode: AppearanceMode,
    reduceMotionEnabled: Boolean,
    highContrastEnabled: Boolean
): String = buildString {
    appendLine("selectedDate=$selectedDate")
    appendLine("blockCount=${blocks.size}")
    appendLine("missedCount=${missedBlocks.size}")
    appendLine("overlapCount=${findOverlaps(blocks).size}")
    appendLine("largeGapCount=${findLargeGaps(blocks, 45).size}")
    appendLine("privacy=${GenAiAssistCopy.privacyModeLabel(privacyMode)}")
    appendLine("sync=$syncStatus")
    appendLine("reminders=blockStart:$blockStartReminders break:$breakReminders missed:$missedAlerts endDay:$endDayReviewReminder")
    appendLine("appearance=${appearanceMode.label}")
    appendLine("dynamicColor=$dynamicColorEnabled")
    appendLine("glass=$glassSurfacesEnabled")
    appendLine("reduceMotion=$reduceMotionEnabled")
    appendLine("highContrast=$highContrastEnabled")
}

private fun findGaps(blocks: List<TimeBlockUiModel>): List<TimeRangeUi> {
    if (blocks.isEmpty()) return emptyList()
    val sorted = blocks.sortedBy { it.startMinuteOfDay }
    val gaps = mutableListOf<TimeRangeUi>()
    var current = 0
    sorted.forEach { block ->
        if (block.startMinuteOfDay > current + 15) {
            gaps.add(TimeRangeUi(current, block.startMinuteOfDay))
        }
        current = block.startMinuteOfDay + block.durationMinutes
    }
    if (current < 1440 - 15) {
        gaps.add(TimeRangeUi(current, 1440))
    }
    return gaps
}

private fun findLargeGaps(blocks: List<TimeBlockUiModel>, thresholdMinutes: Int): List<TimeRangeUi> =
    findGaps(blocks).filter { gap -> gap.endMinute - gap.startMinute >= thresholdMinutes }

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

private fun formatSeconds(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun inferCategory(block: TimeBlockUiModel): String = when {
    block.medicationPlanId != null -> "Medication"
    block.habitId != null -> "Habit"
    block.taskId != null -> "Task"
    block.title.contains("break", ignoreCase = true) -> "Break"
    block.title.contains("meet", ignoreCase = true) -> "Meeting"
    block.title.contains("work", ignoreCase = true) -> "Work"
    else -> "Plan"
}

@Composable
private fun BlockTimeRangeSummary(
    start: String,
    endTimeStr: String,
    durMin: Int
) {
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Time range",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$start - $endTimeStr",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${durMin}m",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CategoryChipSelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf("WORK", "BREAK", "MEETING", "ROUTINE", "MEDICATION")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val color = categoryColor(cat)
            val isSelected = selectedCategory.uppercase(Locale.getDefault()) == cat
            val baseColor = if (isSelected) color.copy(alpha = 0.18f) else Color.Transparent
            val borderCol = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textCol = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(baseColor)
                    .border(
                        width = 1.dp,
                        color = borderCol,
                        shape = MaterialTheme.shapes.small
                    )
                    .clickable { onCategorySelected(cat) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, RoundedCornerShape(100))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = cat.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textCol
                    )
                }
            }
        }
    }
}
