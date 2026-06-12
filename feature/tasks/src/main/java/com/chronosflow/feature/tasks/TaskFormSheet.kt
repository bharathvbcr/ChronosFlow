package com.chronosflow.feature.tasks

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chronosflow.core.ai.TaskAssistRequest
import com.chronosflow.core.ai.TaskAssistSuggestion
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.ai.genai.RewriteAssistUiState
import com.chronosflow.core.ai.genai.RewriteStyle
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskAttachment
import com.chronosflow.core.domain.model.TaskAttachmentKind
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import com.chronosflow.core.domain.model.TaskAction
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.TaskChecklistItem
import com.chronosflow.core.domain.model.TaskContactSnapshot
import com.chronosflow.core.domain.model.TaskReminderTrigger
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.ui.components.GenAiAssistBanner
import com.chronosflow.core.ui.components.ChronosAssistSuggestionChips
import com.chronosflow.core.ui.components.ChronosTextRewriteRow
import com.chronosflow.core.ui.components.ChronosCollapsibleSection
import com.chronosflow.core.ui.components.ChronosSpeechInputButton
import com.chronosflow.core.ui.components.ChronosDatePickerField
import com.chronosflow.core.ui.components.ChronosModalActionLabels
import com.chronosflow.core.ui.components.ChronosFormBottomSheet
import com.chronosflow.core.ui.components.commandPaletteSpeechQuery
import com.chronosflow.core.ui.components.ChronosFormSection
import com.chronosflow.core.ui.components.ChronosFormSwitchRow
import com.chronosflow.core.ui.components.ChronosLauncherAppPicker
import com.chronosflow.core.ui.components.ChronosLinkOption
import com.chronosflow.core.ui.components.ChronosLinkPickerField
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosDurationSlider
import com.chronosflow.core.ui.components.ChronosOptionChips
import com.chronosflow.core.ui.components.ChronosQuickAddChips
import com.chronosflow.core.ui.components.ChronosTimePickerField
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.components.withSelectedOption
import com.chronosflow.core.ui.settings.ChronosUiSettingsKeys
import com.chronosflow.core.ui.settings.rememberChronosUiBooleanSetting
import com.chronosflow.core.ui.components.formatChronosPickerDate
import com.chronosflow.core.ui.components.nudgeMinuteText
import com.chronosflow.core.ui.components.parseFlexibleMinute
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal sealed class TaskSheetTarget {
    data class Add(val prefillTitle: String? = null) : TaskSheetTarget()
    data class Edit(val task: Task, val schedule: TaskSchedule? = null) : TaskSheetTarget()
}

private val taskTitleSuggestions = listOf(
    "Email follow-up",
    "Review notes",
    "Prep meeting",
    "Pay bill",
    "Call back",
    "Ship package"
)

private val priorityOptions = listOf(
    0 to "Normal",
    1 to "High",
    2 to "Urgent"
)

private val urgentReminderPresets = mapOf(
    "In 30 min" to -30,
    "In 1 hour" to -60,
    "Today 6:00 PM" to 18 * 60,
    "Tonight 9:00 PM" to 21 * 60,
    "Custom" to null
)

private val durationPresets = listOf(15, 30, 45, 60, 90, 120)
private const val anyDurationOption = "Any length"
internal const val TaskDurationMinMinutes = 5
internal const val TaskDurationMaxMinutes = 240

private val scheduleDateOptions = listOf("Any day", "Today", "Tomorrow", "Custom")

private const val anyPreferredStartOption = "Any time"
private const val customPreferredStartOption = "Custom"
private val preferredStartPresets = listOf(
    9 * 60 to "Morning",
    12 * 60 to "Noon",
    13 * 60 to "Afternoon",
    18 * 60 to "Evening"
)

private const val ACTION_PICK_CONTACTS = "android.intent.action.PICK_CONTACTS"
private const val EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS =
    "android.intent.extra.PICK_CONTACTS_REQUESTED_DATA_FIELDS"
private const val EXTRA_PICK_CONTACTS_MATCH_ALL_DATA_FIELDS =
    "android.intent.extra.PICK_CONTACTS_MATCH_ALL_DATA_FIELDS"

private val recurringCadenceOptions = listOf(
    TaskRecurringCadence.DAILY,
    TaskRecurringCadence.WEEKLY,
    TaskRecurringCadence.MONTHLY_DAY_OF_MONTH,
    TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY
)
private const val TASK_REPEAT_ONE_TIME = "ONE_TIME"

private val recurringOrdinalOptions = listOf(1, 2, 3, 4, -1)

private data class TaskChecklistItemDraft(
    val id: String,
    val label: String,
    val isCompleted: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskFormSheet(
    target: TaskSheetTarget?,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        priority: Int,
        dueDate: Instant?,
        alarmEnabled: Boolean,
        preferredDurationMinutes: Int?,
        preferredStartMinuteOfDay: Int?,
        targetDate: LocalDate?,
        checklist: List<TaskChecklistItem>,
        linkedContact: TaskContactSnapshot?,
        actions: List<TaskAction>,
        attachments: List<TaskAttachment>,
        recurringConfig: TaskRecurringConfig,
        goalId: String?
    ) -> Unit,
    goalOptions: List<ChronosLinkOption> = emptyList(),
    initialGoalId: String? = null,
    onDelete: ((Task) -> Unit)? = null,
    onDuplicate: ((Task) -> Unit)? = null,
    alarmState: TaskAlarmUiState? = null,
    notificationPermissionGranted: Boolean = true,
    exactAlarmPermissionGranted: Boolean = true,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onOpenExactAlarmSettings: (() -> Unit)? = null,
    assistState: TaskAssistUiState = TaskAssistUiState(),
    onRequestAssist: ((TaskAssistRequest) -> Unit)? = null,
    onClearAssist: (() -> Unit)? = null,
    rewriteState: RewriteAssistUiState = RewriteAssistUiState(),
    onRequestRewrite: ((text: String, style: RewriteStyle, styleLabel: String) -> Unit)? = null,
    onClearRewrite: (() -> Unit)? = null
) {
    if (target == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialTask = (target as? TaskSheetTarget.Edit)?.task
    val initialSchedule = (target as? TaskSheetTarget.Edit)?.schedule
    val prefillTitle = (target as? TaskSheetTarget.Add)?.prefillTitle
    val prefillDraft = remember(prefillTitle) {
        taskTranscriptDraft(prefillTitle.orEmpty())
    }
    val taskKey = initialTask?.id ?: "new-${prefillTitle.orEmpty()}"

    var taskTitle by rememberSaveable(taskKey) {
        mutableStateOf(initialTask?.title ?: prefillDraft.title ?: prefillTitle.orEmpty())
    }
    var description by rememberSaveable(taskKey) {
        mutableStateOf(initialTask?.description.orEmpty())
    }
    var taskCaptureContext by rememberSaveable(taskKey) {
        mutableStateOf(prefillTitle.orEmpty())
    }
    var priority by rememberSaveable(taskKey) {
        mutableStateOf(initialTask?.priority ?: prefillDraft.priority ?: 0)
    }
    var alarmEnabled by rememberSaveable(taskKey) {
        mutableStateOf(initialTask?.let { it.priority >= 2 && it.dueDate != null } ?: false)
    }
    var reminderPreset by rememberSaveable(taskKey) {
        mutableStateOf(
            initialTask?.dueDate?.let { due ->
                minuteFromInstant(due)?.let { minute ->
                    resolveUrgentReminderPreset(minute)
                } ?: "Custom"
            } ?: "In 1 hour"
        )
    }
    var reminder by rememberSaveable(taskKey) {
        mutableStateOf(
            initialTask?.dueDate?.let { due ->
                minuteFromInstant(due)?.let { formatDisplayMinute(it) }
            } ?: formatDisplayMinute(defaultUrgentReminderMinute())
        )
    }
    var preferredDurationMinutes by rememberSaveable(taskKey) {
        mutableStateOf(initialTask?.preferredDurationMinutes ?: prefillDraft.durationMinutes)
    }
    var scheduleDateOption by rememberSaveable(taskKey) {
        mutableStateOf(resolveScheduleDateOption(initialTask?.targetDate))
    }
    var customTargetDateIso by rememberSaveable(taskKey) {
        mutableStateOf(
            initialTask?.targetDate
                ?.takeUnless { resolveScheduleDateOption(it) != "Custom" }
                ?.toString()
                .orEmpty()
        )
    }
    var scheduleTimeOption by rememberSaveable(taskKey) {
        mutableStateOf(resolveScheduleTimeOption(initialTask?.preferredStartMinuteOfDay))
    }
    var preferredStartTime by rememberSaveable(taskKey) {
        mutableStateOf(
            initialTask?.preferredStartMinuteOfDay?.let(::formatDisplayMinute).orEmpty()
        )
    }
    var recurringConfig by remember(taskKey) {
        mutableStateOf(
            initialSchedule?.toRecurringConfig()
                ?: TaskRecurringConfig(
                    startsOn = initialTask?.targetDate ?: LocalDate.now()
                )
        )
    }
    var newChecklistItem by rememberSaveable(taskKey) { mutableStateOf("") }
    val checklistItems = remember(taskKey) {
        mutableStateListOf<TaskChecklistItemDraft>().apply {
            addAll(
                initialTask?.checklist?.map { item ->
                    TaskChecklistItemDraft(
                        id = item.id,
                        label = item.label,
                        isCompleted = item.isCompleted
                    )
                }.orEmpty()
            )
        }
    }
    var linkedContact by remember(taskKey) {
        mutableStateOf(initialTask?.linkedContact)
    }
    var selectedGoalId by rememberSaveable(taskKey) {
        mutableStateOf(initialTask?.goalId ?: initialGoalId)
    }
    var goalExpanded by rememberSaveable(taskKey) {
        mutableStateOf((initialTask?.goalId ?: initialGoalId) != null)
    }
    val actionDrafts = remember(taskKey) {
        mutableStateListOf<TaskActionDraft>().apply {
            addAll(initialTask?.actions?.map { action ->
                TaskActionDraft(
                    id = action.id,
                    type = action.type,
                    label = action.label,
                    value = action.value,
                    isPrimary = action.isPrimary
                )
            }.orEmpty())
        }
    }
    val attachmentDrafts = remember(taskKey) {
        mutableStateListOf<TaskAttachmentDraft>().apply {
            addAll(initialTask?.attachments?.map(TaskAttachment::toDraft).orEmpty())
        }
    }
    val appliedSuggestionIds = remember(taskKey, assistState.suggestions) {
        mutableStateListOf<String>()
    }
    // Blank = no explicit choice yet; the pending action type then follows the task
    // context (e.g. "Email follow-up" defaults the editor to EMAIL, not WEBSITE).
    var newActionTypeOverride by rememberSaveable(taskKey) { mutableStateOf("") }
    val newActionTypeName = newActionTypeOverride.ifBlank {
        contextualTaskDefaultActionTypeName(
            contextText = "$taskTitle $description $taskCaptureContext",
            suggestions = assistState.suggestions
        )
    }
    var newActionLabel by rememberSaveable(taskKey) { mutableStateOf("") }
    var newActionValue by rememberSaveable(taskKey) { mutableStateOf("") }
    var connectExpanded by rememberSaveable(taskKey) {
        mutableStateOf(
            initialTask?.linkedContact != null ||
                !initialTask?.actions.isNullOrEmpty() ||
                !initialTask?.attachments.isNullOrEmpty()
        )
    }
    var scheduleExpanded by rememberSaveable(taskKey) {
        mutableStateOf(
            initialTask?.targetDate != null ||
                initialTask?.preferredDurationMinutes != null ||
                initialTask?.preferredStartMinuteOfDay != null ||
                initialSchedule != null
        )
    }
    var priorityReminderExpanded by rememberSaveable(taskKey) {
        mutableStateOf((initialTask?.priority ?: 0) > 0 || initialTask?.dueDate != null)
    }
    var checklistExpanded by rememberSaveable(taskKey) {
        mutableStateOf(!initialTask?.checklist.isNullOrEmpty())
    }
    var recurrenceExpanded by rememberSaveable(taskKey) {
        mutableStateOf(initialSchedule?.toRecurringConfig()?.enabled == true)
    }
    var connectedFilesExpanded by rememberSaveable(taskKey) { mutableStateOf(true) }
    var titleEverFilled by rememberSaveable(taskKey) { mutableStateOf(initialTask?.title?.isNotBlank() == true) }
    var lastAutoAssistCapture by rememberSaveable(taskKey) { mutableStateOf("") }
    LaunchedEffect(taskKey) {
        onClearRewrite?.invoke()
    }
    val android17ContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { sessionUri ->
                coroutineScope.launch {
                    linkedContact = resolveTaskContactSnapshotFromPickerSession(context, sessionUri)
                    connectExpanded = true
                }
            }
        }
    }
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            coroutineScope.launch {
                linkedContact = resolveTaskContactSnapshot(context, contactUri)
                connectExpanded = true
            }
        }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val pickedDrafts = buildTaskAttachmentDrafts(context, listOf(uri))
                val mergedDrafts = normalizeTaskAttachmentDrafts(attachmentDrafts.toList() + pickedDrafts)
                attachmentDrafts.clear()
                attachmentDrafts.addAll(mergedDrafts)
                connectExpanded = true
            }
        }
    }
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val pickedDrafts = buildTaskAttachmentDrafts(context, uris)
                val mergedDrafts = normalizeTaskAttachmentDrafts(attachmentDrafts.toList() + pickedDrafts)
                attachmentDrafts.clear()
                attachmentDrafts.addAll(mergedDrafts)
                connectExpanded = true
            }
        }
    }
    fun launchContactPicker() {
        if (Build.VERSION.SDK_INT >= 37) {
            val pickerIntent = buildAndroid17ContactPickerIntent()
            if (pickerIntent.resolveActivity(context.packageManager) != null) {
                android17ContactPickerLauncher.launch(pickerIntent)
                return
            }
        }
        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasContactPermission) {
            contactPickerLauncher.launch(null)
        } else {
            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun prepareInlineAction(type: TaskActionType, label: String) {
        newActionTypeOverride = type.name
        newActionLabel = label
        newActionValue = ""
        connectExpanded = true
    }

    fun applyAssistSuggestion(suggestion: TaskAssistSuggestion) {
        when (suggestion) {
            is TaskAssistSuggestion.Title -> {
                taskTitle = suggestion.title
            }
            is TaskAssistSuggestion.ActionDraft -> {
                val draft = taskAssistActionDraftFromSuggestion(
                    suggestion = suggestion,
                    isPrimary = actionDrafts.none { it.isPrimary }
                )
                if (draft != null) {
                    if (draft.isPrimary) {
                        actionDrafts.indices.forEach { index ->
                            actionDrafts[index] = actionDrafts[index].copy(isPrimary = false)
                        }
                    }
                    actionDrafts.add(draft)
                    newActionTypeOverride = suggestion.payload.type.name
                    newActionLabel = ""
                    newActionValue = ""
                } else {
                    newActionTypeOverride = suggestion.payload.type.name
                    newActionLabel = suggestion.payload.label
                    newActionValue = suggestion.payload.value
                }
                connectExpanded = true
            }
            is TaskAssistSuggestion.Schedule -> {
                suggestion.payload.targetDate?.let { date ->
                    customTargetDateIso = date.toString()
                    scheduleDateOption = resolveScheduleDateOption(date)
                }
                suggestion.payload.preferredDurationMinutes?.let { duration ->
                    preferredDurationMinutes = duration
                }
                suggestion.payload.preferredStartMinuteOfDay?.let { minute ->
                    preferredStartTime = formatDisplayMinute(minute)
                    scheduleTimeOption = resolveScheduleTimeOption(minute)
                }
                scheduleExpanded = true
            }
            is TaskAssistSuggestion.Checklist -> {
                val knownLabels = checklistItems
                    .map { it.label.trim().lowercase() }
                    .toMutableSet()
                suggestion.items.forEach { item ->
                    val label = item.trim()
                    if (label.isBlank() || !knownLabels.add(label.lowercase())) return@forEach
                    checklistItems.add(
                        TaskChecklistItemDraft(
                            id = UUID.randomUUID().toString(),
                            label = label,
                            isCompleted = false
                        )
                    )
                }
                checklistExpanded = true
            }
            is TaskAssistSuggestion.Priority -> {
                priority = suggestion.priority.coerceIn(0, 2)
                priorityReminderExpanded = true
            }
        }
        supersededTaskAssistSuggestionIds(suggestion, assistState.suggestions).forEach { id ->
            if (id !in appliedSuggestionIds) {
                appliedSuggestionIds.add(id)
            }
        }
        if (assistState.suggestions.all { it.id in appliedSuggestionIds }) {
            onClearAssist?.invoke()
        }
    }

    fun applyAllAssistSuggestions() {
        assistState.suggestions.forEach { suggestion ->
            if (suggestion.id !in appliedSuggestionIds) {
                applyAssistSuggestion(suggestion)
            }
        }
    }

    val isUrgent = priority >= 2
    val parsedReminderMinute = parseFlexibleMinute(reminder)
    val resolvedDueInstant = resolveTaskDueInstant(reminderPreset, parsedReminderMinute)
    val alarmTimeValid = !alarmEnabled || resolvedDueInstant?.isAfter(Instant.now()) == true
    val customTargetDate = customTargetDateIso.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    val parsedPreferredStartMinute =
        taskPreferredStartPickerMinutes(scheduleTimeOption) ?: parseFlexibleMinute(preferredStartTime)
    val scheduleTimeValid = scheduleTimeOption != "Custom" ||
        preferredStartTime.isBlank() ||
        parsedPreferredStartMinute != null
    val scheduleDateValid = scheduleDateOption != "Custom" || customTargetDate != null
    val resolvedTargetDate = when (scheduleDateOption) {
        "Custom" -> customTargetDate
        "Today" -> LocalDate.now()
        "Tomorrow" -> LocalDate.now().plusDays(1)
        else -> null
    }
    val recurringWeekdaysValid = !recurringConfig.enabled ||
        recurringConfig.cadence != TaskRecurringCadence.WEEKLY ||
        recurringConfig.weekdays.isNotEmpty()
    val recurringRemindersValid = !recurringConfig.enabled || recurringConfig.reminderDrafts.all { draft ->
        when (draft.trigger) {
            TaskReminderTrigger.AT_TIME -> (draft.minuteOfDay ?: parsedPreferredStartMinute) != null
            TaskReminderTrigger.BEFORE_OCCURRENCE -> {
                parsedPreferredStartMinute != null && (draft.offsetMinutesBefore ?: 0) > 0
            }
        }
    }
    val normalizedChecklist = checklistItems
        .filter { it.label.isNotBlank() }
        .map { item ->
            TaskChecklistItem(
                id = item.id.ifBlank { UUID.randomUUID().toString() },
                label = item.label.trim(),
                isCompleted = item.isCompleted
            )
        }
    val normalizedActions = normalizeTaskActionDrafts(actionDrafts.toList())
    val existingActionDraftsInvalid = hasInvalidTaskActionDraft(actionDrafts.toList())
    val normalizedAttachmentDrafts = normalizeTaskAttachmentDrafts(attachmentDrafts.toList())
    val existingAttachmentDraftsInvalid = hasInvalidTaskAttachmentDrafts(attachmentDrafts.toList())
    val featuredAttachmentPreview = normalizedAttachmentDrafts
        .firstOrNull { it.kind == TaskAttachmentKind.IMAGE && it.isFeaturedImage }
        ?.toPreviewAttachment()
    val pendingActionDraft = TaskActionDraft(
        id = "pending",
        type = TaskActionType.valueOf(newActionTypeName),
        label = newActionLabel,
        value = newActionValue,
        isPrimary = false
    )
    val pendingActionInvalid = (newActionLabel.isNotBlank() || newActionValue.isNotBlank()) &&
        normalizeTaskActionDraft(pendingActionDraft) == null

    val isValid = taskTitle.isNotBlank() &&
        (!isUrgent || !alarmEnabled || alarmTimeValid) &&
        scheduleTimeValid &&
        scheduleDateValid &&
        recurringWeekdaysValid &&
        recurringRemindersValid &&
        !existingAttachmentDraftsInvalid &&
        !existingActionDraftsInvalid &&
        !pendingActionInvalid
    val validationHint = when {
        taskTitle.isBlank() -> "Enter a task title to continue."
        !scheduleDateValid -> "Pick a target date or switch back to Any day."
        !scheduleTimeValid -> "Use a valid preferred start time like 9:30 AM or 13:30."
        !recurringWeekdaysValid -> "Pick at least one weekday for a weekly recurring task."
        !recurringRemindersValid -> "Recurring reminders need a valid time or lead offset."
        existingAttachmentDraftsInvalid ->
            "Each attachment must keep a valid linked document or imported file copy."
        existingActionDraftsInvalid || pendingActionInvalid ->
            "Each action needs a label and a valid destination."
        isUrgent && alarmEnabled && parsedReminderMinute == null && reminderPreset == "Custom" ->
            "Use a valid reminder time like 6:00 PM or 18:00."
        isUrgent && alarmEnabled && !alarmTimeValid ->
            "Choose a reminder time in the future."
        else -> null
    }
    val hasTaskContextDrafts = linkedContact != null || actionDrafts.isNotEmpty() || attachmentDrafts.isNotEmpty()
    val taskContextQuery = listOf(taskTitle, description, taskCaptureContext)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
    val autoAssistCapture = taskContextQuery.trim()
    LaunchedEffect(autoAssistCapture, assistState.isLoading, onRequestAssist) {
        val requestAssist = onRequestAssist ?: return@LaunchedEffect
        if (assistState.isLoading) return@LaunchedEffect
        if (!shouldAutoRequestAssistForCapture(autoAssistCapture) || autoAssistCapture == lastAutoAssistCapture) {
            return@LaunchedEffect
        }
        delay(AUTO_ASSIST_DEBOUNCE_MS)
        val currentAssistCapture = listOf(taskTitle, description, taskCaptureContext)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        if (currentAssistCapture != autoAssistCapture) return@LaunchedEffect
        lastAutoAssistCapture = autoAssistCapture
        requestAssist(
            TaskAssistRequest(
                title = taskTitle.ifBlank { taskCaptureContext.ifBlank { description } },
                description = listOf(description, taskCaptureContext)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" "),
                priority = priority,
                targetDate = resolvedTargetDate,
                preferredDurationMinutes = preferredDurationMinutes,
                preferredStartMinuteOfDay = parsedPreferredStartMinute,
                checklistLabels = checklistItems.map { it.label }
            )
        )
    }

    LaunchedEffect(taskKey, prefillTitle) {
        if (initialTask == null && !prefillTitle.isNullOrBlank()) {
            taskCaptureContext = prefillTitle
            prefillDraft.scheduleDateOption?.let { option ->
                scheduleDateOption = option
            }
            prefillDraft.preferredStartMinute?.let { minute ->
                preferredStartTime = formatDisplayMinute(minute)
                scheduleTimeOption = resolveScheduleTimeOption(minute)
            }
            if (prefillDraft.priority != null) {
                priorityReminderExpanded = true
            }
            val prefillActionDraft = taskTranscriptActionDraft(
                capture = prefillTitle,
                isPrimary = actionDrafts.none { it.isPrimary }
            )
            val prefillActionType = prefillActionDraft?.type
                ?: taskTranscriptActionType(prefillTitle)
            if (prefillActionDraft != null) {
                if (prefillActionDraft.isPrimary) {
                    actionDrafts.indices.forEach { index ->
                        actionDrafts[index] = actionDrafts[index].copy(isPrimary = false)
                    }
                }
                if (actionDrafts.none { draft ->
                        draft.type == prefillActionDraft.type &&
                            draft.value.equals(prefillActionDraft.value, ignoreCase = true)
                    }
                ) {
                    actionDrafts.add(prefillActionDraft)
                }
                newActionTypeOverride = prefillActionDraft.type.name
                newActionLabel = ""
                newActionValue = ""
                connectExpanded = true
            } else if (prefillActionType != null) {
                newActionTypeOverride = prefillActionType.name
                newActionLabel = ""
                newActionValue = ""
                connectExpanded = true
            }
        }
    }

    val redundantAssistSuggestionIds = redundantTaskAssistSuggestionIds(
        suggestions = assistState.suggestions,
        currentTitle = taskTitle,
        currentPriority = priority,
        currentTargetDate = resolvedTargetDate,
        currentDurationMinutes = preferredDurationMinutes,
        currentStartMinuteOfDay = parsedPreferredStartMinute,
        currentChecklistLabels = checklistItems.map { it.label },
        currentActionKeys = actionDrafts.map { taskActionRedundancyKey(it.type, it.value) }.toSet()
    )
    val visibleAssistSuggestions = assistState.suggestions.filterNot {
        it.id in appliedSuggestionIds || it.id in redundantAssistSuggestionIds
    }
    val autoApplyAssistEnabled = rememberChronosUiBooleanSetting(
        ChronosUiSettingsKeys.KEY_ASSIST_AUTO_APPLY,
        false
    )
    LaunchedEffect(assistState.suggestions, autoApplyAssistEnabled) {
        if (!autoApplyAssistEnabled || target !is TaskSheetTarget.Add) return@LaunchedEffect
        val autoApplicableIds = autoApplicableTaskAssistSuggestionIds(
            suggestions = visibleAssistSuggestions,
            titleBlank = taskTitle.isBlank(),
            priorityUnset = priority == 0,
            scheduleUnset = resolvedTargetDate == null &&
                preferredDurationMinutes == null &&
                parsedPreferredStartMinute == null,
            checklistEmpty = checklistItems.none { it.label.isNotBlank() }
        )
        assistState.suggestions
            .filter { it.id in autoApplicableIds }
            .forEach(::applyAssistSuggestion)
    }
    val contextualAssistSuggestions = assistState.suggestions
    val adaptiveTaskHints = taskModalAdaptiveHints(
        title = taskContextQuery,
        description = description,
        suggestions = contextualAssistSuggestions
    )
    val showTaskContextDetails = shouldShowTaskContextDetails(
        connectExpanded = connectExpanded || adaptiveTaskHints.showContext,
        hasContext = hasTaskContextDrafts || adaptiveTaskHints.showContext
    )
    val showConnectedFilesSection = shouldShowConnectedFilesSection(
        connectExpanded = connectExpanded || adaptiveTaskHints.showContext,
        attachmentCount = attachmentDrafts.size
    )
    val hasTaskSchedulePreferences = preferredDurationMinutes != null ||
        parsedPreferredStartMinute != null ||
        resolvedTargetDate != null ||
        recurringConfig.enabled
    val showTaskScheduleDetails = shouldShowTaskScheduleDetails(
        scheduleExpanded = scheduleExpanded || adaptiveTaskHints.showSchedule,
        hasSchedulePreferences = hasTaskSchedulePreferences || adaptiveTaskHints.showSchedule
    )
    val scheduleDraftSummary = taskScheduleDraftSummary(
        targetDate = resolvedTargetDate,
        preferredDurationMinutes = preferredDurationMinutes,
        preferredStartMinuteOfDay = parsedPreferredStartMinute,
        recurringConfig = recurringConfig
    )
    val checklistStepCount = checklistItems.count { it.label.isNotBlank() }
    val completedChecklistStepCount = checklistItems.count { it.label.isNotBlank() && it.isCompleted }
    val showTaskChecklistDetails = shouldShowTaskChecklistDetails(
        checklistExpanded = checklistExpanded || adaptiveTaskHints.showChecklist,
        checklistCount = checklistStepCount + if (adaptiveTaskHints.showChecklist) 1 else 0
    )
    val hasTaskPrioritySettings = priority > 0 || alarmEnabled
    val showTaskPriorityDetails = shouldShowTaskPriorityDetails(
        priorityExpanded = priorityReminderExpanded || adaptiveTaskHints.showPriority,
        hasPrioritySettings = hasTaskPrioritySettings || adaptiveTaskHints.showPriority
    )
    val priorityDraftSummary = taskPrioritySummary(
        priority = priority,
        alarmEnabled = alarmEnabled,
        dueDate = resolvedDueInstant
    )

    val contextualTaskTitleOptions = contextualTaskTitleSuggestions(
        title = taskTitle,
        description = description,
        suggestions = assistState.suggestions
    )
    val dynamicTaskTitle = taskTitle.ifBlank { initialTask?.title.orEmpty() }
    val editTaskSubtitle = taskEditModalDynamicSubtitle(
        taskTitle = dynamicTaskTitle,
        description = description,
        dueInstant = resolvedDueInstant,
        isUrgent = isUrgent,
        alarmEnabled = alarmEnabled,
        checklistCount = checklistItems.size,
        hasLinkedContact = linkedContact != null,
        actionCount = actionDrafts.size,
        attachmentCount = attachmentDrafts.size
    )
    val editTaskTitle = taskEditModalDynamicTitle(dynamicTaskTitle)

    val title = taskModalDynamicTitle(
        isAddMode = target is TaskSheetTarget.Add,
        taskTitle = taskTitle,
        description = description,
        hints = adaptiveTaskHints,
        suggestions = assistState.suggestions,
        editTitle = editTaskTitle
    )
    val subtitle = taskModalDynamicSubtitle(
        isAddMode = target is TaskSheetTarget.Add,
        taskTitle = taskTitle,
        description = description,
        hints = adaptiveTaskHints,
        hasContext = hasTaskContextDrafts || adaptiveTaskHints.showContext,
        suggestions = assistState.suggestions,
        editSubtitle = editTaskSubtitle
    )
    val actionLabels = taskModalActionLabels(target is TaskSheetTarget.Add)

    ChronosFormBottomSheet(
        visible = true,
        title = title,
        subtitle = subtitle,
        confirmLabel = actionLabels.confirm,
        validationHint = validationHint,
        onDismiss = onDismiss,
        onConfirm = {
            coroutineScope.launch {
                val dueDate = if (isUrgent && alarmEnabled) resolvedDueInstant else null
                val resolvedAttachments = resolveTaskAttachmentDrafts(context, normalizedAttachmentDrafts)
                onConfirm(
                    taskTitle,
                    description,
                    priority,
                    dueDate,
                    isUrgent && alarmEnabled,
                    preferredDurationMinutes,
                    parsedPreferredStartMinute,
                    resolvedTargetDate,
                    normalizedChecklist,
                    linkedContact,
                    normalizedActions,
                    resolvedAttachments,
                    recurringConfig.copy(
                        startsOn = recurringConfig.startsOn,
                        reminderDrafts = recurringConfig.reminderDrafts
                    ),
                    selectedGoalId
                )
            }
        },
        enabled = isValid,
        onDuplicate = if (initialTask != null && onDuplicate != null) {
            { onDuplicate(initialTask) }
        } else {
            null
        },
        duplicateLabel = actionLabels.duplicate,
        onArchive = if (initialTask != null && onDelete != null) {
            { onDelete(initialTask) }
        } else {
            null
        },
        archiveLabel = actionLabels.archive
    ) {
        ChronosFormSection(
            title = "Essentials",
            subtitle = "Keep the core commitment visible while the rest stays contextual."
        ) {
            ChronosQuickAddChips(
                label = "Context titles",
                options = contextualTaskTitleOptions,
                onSelect = { suggestion -> taskTitle = suggestion },
                selected = taskTitle
            )

            OutlinedTextField(
                value = taskTitle,
                onValueChange = {
                    taskTitle = it
                    if (it.isNotBlank()) titleEverFilled = true
                },
                label = { Text("Title") },
                placeholder = { Text("What needs to get done?") },
                singleLine = true,
                isError = titleEverFilled && taskTitle.isBlank(),
                supportingText = if (taskTitle.isBlank()) {
                    { Text("Required") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Optional context or next step") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (onRequestRewrite != null) {
                ChronosTextRewriteRow(
                    text = description,
                    rewriteState = rewriteState,
                    onRequestRewrite = onRequestRewrite,
                    onApplyRewrite = { rewritten ->
                        description = rewritten
                        onClearRewrite?.invoke()
                    },
                    onDismissRewrite = { onClearRewrite?.invoke() },
                    fieldName = "description"
                )
            }

            ChronosSpeechInputButton(
                prompt = "Describe the task, including time, date, duration, contact, file, or link.",
                label = "Dictate task",
                onTranscript = { transcript ->
                    val capture = commandPaletteSpeechQuery(transcript)
                    if (capture.isBlank()) return@ChronosSpeechInputButton
                    taskCaptureContext = capture
                    val transcriptDraft = taskTranscriptDraft(capture)
                    val requestTitle = taskTitle.ifBlank { capture }
                    val visibleTitle = taskTitle.ifBlank { transcriptDraft.title ?: capture }
                    val requestDescription = if (taskTitle.isBlank()) {
                        description
                    } else {
                        appendTaskTranscript(description, capture)
                    }
                    val requestPriority = transcriptDraft.priority?.let { maxOf(priority, it) } ?: priority
                    val requestDurationMinutes = transcriptDraft.durationMinutes ?: preferredDurationMinutes
                    val requestPreferredStartMinute =
                        transcriptDraft.preferredStartMinute ?: parsedPreferredStartMinute
                    val requestTargetDate = when (transcriptDraft.scheduleDateOption ?: scheduleDateOption) {
                        "Today" -> LocalDate.now()
                        "Tomorrow" -> LocalDate.now().plusDays(1)
                        else -> resolvedTargetDate
                    }
                    val transcriptActionDraft = taskTranscriptActionDraft(
                        capture = capture,
                        isPrimary = actionDrafts.none { it.isPrimary }
                    )
                    val transcriptActionType = transcriptActionDraft?.type
                        ?: taskTranscriptActionType(capture)
                    if (taskTitle.isBlank()) {
                        taskTitle = visibleTitle
                    } else {
                        description = requestDescription
                    }
                    transcriptDraft.priority?.let { detectedPriority ->
                        priority = maxOf(priority, detectedPriority)
                        priorityReminderExpanded = true
                    }
                    transcriptDraft.durationMinutes?.let { duration ->
                        preferredDurationMinutes = duration
                    }
                    transcriptDraft.preferredStartMinute?.let { minute ->
                        preferredStartTime = formatDisplayMinute(minute)
                        scheduleTimeOption = resolveScheduleTimeOption(minute)
                    }
                    transcriptDraft.scheduleDateOption?.let { option ->
                        scheduleDateOption = option
                    }
                    if (transcriptActionDraft != null) {
                        if (transcriptActionDraft.isPrimary) {
                            actionDrafts.indices.forEach { index ->
                                actionDrafts[index] = actionDrafts[index].copy(isPrimary = false)
                            }
                        }
                        if (actionDrafts.none { draft ->
                                draft.type == transcriptActionDraft.type &&
                                    draft.value.equals(transcriptActionDraft.value, ignoreCase = true)
                            }
                        ) {
                            actionDrafts.add(transcriptActionDraft)
                        }
                        newActionTypeOverride = transcriptActionDraft.type.name
                        newActionLabel = ""
                        newActionValue = ""
                        connectExpanded = true
                    } else if (transcriptActionType != null) {
                        newActionTypeOverride = transcriptActionType.name
                        newActionLabel = ""
                        newActionValue = ""
                        connectExpanded = true
                    }
                    onClearAssist?.invoke()
                    lastAutoAssistCapture = listOf(requestTitle, requestDescription, capture)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" ")
                    onRequestAssist?.invoke(
                        TaskAssistRequest(
                            title = requestTitle,
                            description = listOf(requestDescription, capture)
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" "),
                            priority = requestPriority,
                            targetDate = requestTargetDate,
                            preferredDurationMinutes = requestDurationMinutes,
                            preferredStartMinuteOfDay = requestPreferredStartMinute,
                            checklistLabels = checklistItems.map { it.label }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (onRequestAssist != null) {
                FilledTonalButton(
                    onClick = {
                        onRequestAssist(
                            TaskAssistRequest(
                                title = taskTitle.ifBlank { taskCaptureContext },
                                description = listOf(description, taskCaptureContext)
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .joinToString(" "),
                                priority = priority,
                                targetDate = resolvedTargetDate,
                                preferredDurationMinutes = preferredDurationMinutes,
                                preferredStartMinuteOfDay = parsedPreferredStartMinute,
                                checklistLabels = checklistItems.map { it.label }.filter { it.isNotBlank() }
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
                            else -> "Suggest details with AI"
                        }
                    )
                }
            }

            assistState.assistSnapshot?.let { snapshot ->
                GenAiAssistBanner(
                    title = snapshot.bannerTitle,
                    message = snapshot.bannerMessage +
                        " Type or dictate the task and AI drafts editable suggestions — title, timing, contacts, links, and checklist. Nothing changes until you tap a suggestion.",
                    ready = snapshot.isReady
                )
            }

            if (assistState.message != null) {
                Text(
                    text = assistState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (visibleAssistSuggestions.isNotEmpty() || assistState.isLoading) {
                ChronosAssistSuggestionChips(
                    suggestions = visibleAssistSuggestions,
                    isLoading = assistState.isLoading,
                    onApply = ::applyAssistSuggestion,
                    label = { it.label },
                    reason = { it.reason },
                    sourceLabel = { GenAiAssistCopy.taskAssistSourceLabel(it.source) },
                    loadingLabel = "Drafting AI suggestions…",
                    onApplyAll = ::applyAllAssistSuggestions
                )
            }
        }

        if (goalOptions.isNotEmpty() || selectedGoalId != null) {
            ChronosCollapsibleSection(
                title = "Goal",
                summary = goalOptions.firstOrNull { it.id == selectedGoalId }?.label
                    ?: "Link this task to a goal",
                expanded = goalExpanded,
                onExpandedChange = { goalExpanded = it }
            ) {
                ChronosLinkPickerField(
                    label = "Link to goal",
                    options = goalOptions,
                    selectedId = selectedGoalId,
                    onSelected = { selectedGoalId = it }
                )
            }
        }

        ChronosCollapsibleSection(
            title = "Schedule",
            summary = scheduleDraftSummary,
            expanded = showTaskScheduleDetails,
            onExpandedChange = { scheduleExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Duration",
                options = contextualTaskDurationPickerOptions(
                    title = taskContextQuery,
                    description = description,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(taskDurationPickerLabel(preferredDurationMinutes)),
                selected = taskDurationPickerLabel(preferredDurationMinutes),
                onSelected = { label ->
                    preferredDurationMinutes = taskDurationPickerMinutes(label)
                },
                optionLabel = { it }
            )
            preferredDurationMinutes?.let { estimatedMinutes ->
                ChronosDurationSlider(
                    durationMinutes = estimatedMinutes,
                    onDurationChange = { preferredDurationMinutes = it },
                    range = TaskDurationMinMinutes..TaskDurationMaxMinutes,
                    label = "Estimated duration"
                )
            }
            ChronosOptionChips(
                label = "Target day",
                options = contextualTaskScheduleDateOptions(
                    title = taskContextQuery,
                    description = description,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(scheduleDateOption),
                selected = scheduleDateOption,
                onSelected = { option -> scheduleDateOption = option },
                optionLabel = { it }
            )
            if (scheduleDateOption == "Custom") {
                ChronosDatePickerField(
                    label = "Target date",
                    value = customTargetDate?.let(::formatChronosPickerDate) ?: "Pick a date",
                    selectedDate = customTargetDate,
                    onDateSelected = { pickedDate ->
                        customTargetDateIso = pickedDate.toString()
                        scheduleDateOption = "Custom"
                    }
                )
            }
            ChronosOptionChips(
                label = "Preferred start",
                options = contextualTaskPreferredStartPickerOptions(
                    title = taskContextQuery,
                    description = description,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(scheduleTimeOption),
                selected = scheduleTimeOption,
                onSelected = { option ->
                    scheduleTimeOption = option
                    val presetMinute = taskPreferredStartPickerMinutes(option)
                    when {
                        option == anyPreferredStartOption -> preferredStartTime = ""
                        presetMinute != null -> preferredStartTime = formatDisplayMinute(presetMinute)
                    }
                },
                optionLabel = { it }
            )
            if (scheduleTimeOption == customPreferredStartOption) {
                ChronosTimePickerField(
                    label = "Preferred start time",
                    value = parsedPreferredStartMinute?.let(::formatDisplayMinute) ?: preferredStartTime.ifBlank { "Pick a time" },
                    selectedMinute = parsedPreferredStartMinute,
                    onTimeSelected = { minute ->
                        preferredStartTime = formatDisplayMinute(minute)
                        scheduleTimeOption = "Custom"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Scheduling summary",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = buildString {
                            append(
                                when (scheduleDateOption) {
                                    "Today" -> "Targeting today"
                                    "Tomorrow" -> "Targeting tomorrow"
                                    "Custom" -> customTargetDate?.let { "Targeting ${formatTaskTargetDateLabel(it)}" }
                                        ?: "No specific day"
                                    else -> "No specific day"
                                }
                            )
                            append(" • ")
                            append(
                                preferredDurationMinutes?.let { "${formatTaskDurationBlock(it)} block" }
                                    ?: "Flexible duration"
                            )
                            parsedPreferredStartMinute?.let {
                                append(" • Start around ${formatDisplayMinute(it)}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        ChronosCollapsibleSection(
            title = "Recurrence",
            summary = if (recurringConfig.enabled) {
                recurringSummary(recurringConfig, parsedPreferredStartMinute)
                    ?: "Repeats — choose a cadence to preview the schedule"
            } else {
                "One-time task — tap to set up repeats"
            },
            expanded = recurrenceExpanded,
            onExpandedChange = { recurrenceExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Repeat suggestion",
                options = contextualTaskRepeatOptions(
                    contextText = taskContextQuery,
                    recurringConfig = recurringConfig
                ).withSelectedOption(taskRepeatOptionValue(recurringConfig)),
                selected = taskRepeatOptionValue(recurringConfig),
                onSelected = { option ->
                    val selectedCadence = taskRepeatOptionCadence(option) ?: recurringConfig.cadence
                    val contextualWeekdays = contextualTaskWeeklyDays("$taskTitle $description")
                    val contextualInterval = if (option != TASK_REPEAT_ONE_TIME) {
                        contextualTaskRecurringInterval("$taskTitle $description", selectedCadence)
                    } else {
                        null
                    }
                    recurringConfig = recurringConfig.copy(
                        enabled = option != TASK_REPEAT_ONE_TIME,
                        cadence = selectedCadence,
                        interval = contextualInterval ?: recurringConfig.interval,
                        weekdays = if (
                            option != TASK_REPEAT_ONE_TIME &&
                            selectedCadence == TaskRecurringCadence.WEEKLY &&
                            contextualWeekdays.isNotEmpty()
                        ) {
                            contextualWeekdays
                        } else {
                            recurringConfig.weekdays
                        }
                    )
                },
                optionLabel = ::taskRepeatOptionLabel
            )
            ChronosFormSwitchRow(
                title = "Repeat this task",
                subtitle = "Use a recurring cadence instead of a one-time task.",
                checked = recurringConfig.enabled,
                onCheckedChange = { enabled ->
                    recurringConfig = recurringConfig.copy(enabled = enabled)
                }
            )
            if (recurringConfig.enabled) {
                ChronosOptionChips(
                    label = "Cadence",
                    options = contextualTaskRecurringCadenceOptions(
                        contextText = taskContextQuery,
                        selectedCadence = recurringConfig.cadence
                    ).withSelectedOption(recurringConfig.cadence.name),
                    selected = recurringConfig.cadence.name,
                    onSelected = { selected ->
                        val selectedCadence = TaskRecurringCadence.valueOf(selected)
                        val contextualWeekdays = contextualTaskWeeklyDays("$taskTitle $description")
                        val contextualInterval = contextualTaskRecurringInterval("$taskTitle $description", selectedCadence)
                        recurringConfig = recurringConfig.copy(
                            cadence = selectedCadence,
                            interval = contextualInterval ?: recurringConfig.interval,
                            weekdays = if (
                                selectedCadence == TaskRecurringCadence.WEEKLY &&
                                contextualWeekdays.isNotEmpty()
                            ) {
                                contextualWeekdays
                            } else {
                                recurringConfig.weekdays
                            }
                        )
                    },
                    optionLabel = { cadenceName ->
                        when (TaskRecurringCadence.valueOf(cadenceName)) {
                            TaskRecurringCadence.DAILY -> "Daily"
                            TaskRecurringCadence.WEEKLY -> "Weekly"
                            TaskRecurringCadence.MONTHLY_DAY_OF_MONTH -> "Monthly date"
                            TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY -> "Monthly weekday"
                        }
                    }
                )
                OutlinedTextField(
                    value = recurringConfig.interval.toString(),
                    onValueChange = { value ->
                        recurringConfig = recurringConfig.copy(interval = value.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                    },
                    label = { Text("Repeat every") },
                    supportingText = {
                        Text(taskRecurringIntervalUnitLabel(recurringConfig.cadence, recurringConfig.interval))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (recurringConfig.cadence == TaskRecurringCadence.WEEKLY) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DayOfWeek.values().forEach { day ->
                            val selected = day in recurringConfig.weekdays
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    recurringConfig = recurringConfig.copy(
                                        weekdays = if (selected) {
                                            recurringConfig.weekdays - day
                                        } else {
                                            recurringConfig.weekdays + day
                                        }
                                    )
                                },
                                label = { Text(taskRecurringWeekdayPickerLabel(day)) }
                            )
                        }
                    }
                }
                if (recurringConfig.cadence == TaskRecurringCadence.MONTHLY_DAY_OF_MONTH) {
                    OutlinedTextField(
                        value = recurringConfig.dayOfMonth.toString(),
                        onValueChange = { value ->
                            recurringConfig = recurringConfig.copy(dayOfMonth = value.toIntOrNull()?.coerceIn(1, 31) ?: 1)
                        },
                        label = { Text("Day of month") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (recurringConfig.cadence == TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY) {
                    ChronosOptionChips(
                        label = "Ordinal",
                        options = recurringOrdinalOptions.map(Int::toString),
                        selected = recurringConfig.ordinal.toString(),
                        onSelected = { value ->
                            recurringConfig = recurringConfig.copy(ordinal = value.toIntOrNull() ?: 1)
                        },
                        optionLabel = { value -> (value.toIntOrNull() ?: 1).displayOrdinalLabel() }
                    )
                    ChronosOptionChips(
                        label = "Weekday",
                        options = DayOfWeek.values().map { it.name },
                        selected = recurringConfig.ordinalWeekday.name,
                        onSelected = { selected ->
                            recurringConfig = recurringConfig.copy(ordinalWeekday = DayOfWeek.valueOf(selected))
                        },
                        optionLabel = { taskRecurringWeekdayPickerLabel(DayOfWeek.valueOf(it)) }
                    )
                }
                ChronosDatePickerField(
                    label = "Starts on",
                    value = formatChronosPickerDate(recurringConfig.startsOn),
                    selectedDate = recurringConfig.startsOn,
                    onDateSelected = { pickedDate ->
                        recurringConfig = recurringConfig.copy(startsOn = pickedDate)
                    }
                )
                ChronosDatePickerField(
                    label = "Ends on",
                    value = recurringConfig.endsOn?.let(::formatChronosPickerDate) ?: "Never",
                    selectedDate = recurringConfig.endsOn,
                    onDateSelected = { pickedDate ->
                        recurringConfig = recurringConfig.copy(endsOn = pickedDate)
                    }
                )
                if (recurringConfig.reminderDrafts.isNotEmpty()) {
                    recurringConfig.reminderDrafts.forEachIndexed { index, draft ->
                        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChronosOptionChips(
                                    label = "Reminder type",
                                    options = taskRecurringReminderTriggerPickerOptions(),
                                    selected = taskRecurringReminderTriggerLabel(draft.trigger),
                                    onSelected = { selected ->
                                        val selectedTrigger = taskRecurringReminderTriggerFromLabel(selected)
                                        recurringConfig = recurringConfig.copy(
                                            reminderDrafts = recurringConfig.reminderDrafts.toMutableList().also { reminders ->
                                                reminders[index] = draft.copy(trigger = selectedTrigger)
                                            }
                                        )
                                    },
                                    optionLabel = { it }
                                )
                                if (draft.trigger == TaskReminderTrigger.AT_TIME) {
                                    ChronosTimePickerField(
                                        label = "Reminder time",
                                        value = (draft.minuteOfDay ?: parsedPreferredStartMinute)?.let(::formatDisplayMinute) ?: "Pick a time",
                                        selectedMinute = draft.minuteOfDay ?: parsedPreferredStartMinute,
                                        onTimeSelected = { minute ->
                                            recurringConfig = recurringConfig.copy(
                                                reminderDrafts = recurringConfig.reminderDrafts.toMutableList().also { reminders ->
                                                    reminders[index] = draft.copy(minuteOfDay = minute)
                                                }
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = (draft.offsetMinutesBefore ?: 30).toString(),
                                        onValueChange = { value ->
                                            recurringConfig = recurringConfig.copy(
                                                reminderDrafts = recurringConfig.reminderDrafts.toMutableList().also { reminders ->
                                                    reminders[index] = draft.copy(offsetMinutesBefore = value.toIntOrNull())
                                                }
                                            )
                                        },
                                        label = { Text("Minutes before") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        recurringConfig = recurringConfig.copy(
                                            reminderDrafts = recurringConfig.reminderDrafts.toMutableList().also { reminders ->
                                                reminders.removeAt(index)
                                            }
                                        )
                                    }
                                ) {
                                    Text("Remove reminder")
                                }
                            }
                        }
                    }
                }
                FilledTonalButton(
                    onClick = {
                        recurringConfig = recurringConfig.copy(
                            reminderDrafts = recurringConfig.reminderDrafts + TaskReminderDraft(
                                trigger = if (parsedPreferredStartMinute != null) {
                                    TaskReminderTrigger.BEFORE_OCCURRENCE
                                } else {
                                    TaskReminderTrigger.AT_TIME
                                },
                                offsetMinutesBefore = if (parsedPreferredStartMinute != null) 30 else null,
                                minuteOfDay = parsedPreferredStartMinute
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add recurring reminder")
                }
                ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Recurring summary",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = recurringSummary(recurringConfig, parsedPreferredStartMinute)
                                ?: "Choose a cadence to preview the repeating schedule.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        ChronosCollapsibleSection(
            title = "Priority / reminder",
            summary = if (hasTaskPrioritySettings) {
                priorityDraftSummary
            } else {
                "Normal priority — open for high-priority work or urgent alarms"
            },
            expanded = showTaskPriorityDetails,
            onExpandedChange = { priorityReminderExpanded = it }
        ) {
            ChronosOptionChips(
                label = "",
                options = contextualTaskPriorityOptions(
                    title = taskContextQuery,
                    description = description,
                    selectedPriority = priority,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(priorityOptions.first { it.first == priority }.second),
                selected = priorityOptions.first { it.first == priority }.second,
                onSelected = { label ->
                    priority = priorityOptions.first { it.second == label }.first
                    if (priority < 2) {
                        alarmEnabled = false
                    }
                },
                optionLabel = { it }
            )
            if (isUrgent) {
                Text(
                    text = "Urgent tasks can schedule an exact alarm so nothing slips.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                alarmState?.let { state ->
                    Text(
                        text = taskAlarmStateMessage(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = taskAlarmStateColor(state)
                    )
                }
            }

            if (isUrgent) {
            ChronosFormSection(
                title = "Urgent alarm",
                subtitle = "Exact alarms fire on time; without permission, ChronosFlow uses a 10-minute fallback window."
            ) {
                ChronosFormSwitchRow(
                    title = "Schedule exact alarm",
                    subtitle = "Get notified when this urgent task is due.",
                    checked = alarmEnabled,
                    onCheckedChange = { alarmEnabled = it }
                )

                if (alarmEnabled) {
                    if (!notificationPermissionGranted && onRequestNotificationPermission != null) {
                        TextButton(onClick = onRequestNotificationPermission) {
                            Text("Enable notification access")
                        }
                    }
                    if (!exactAlarmPermissionGranted && onOpenExactAlarmSettings != null) {
                        TextButton(onClick = onOpenExactAlarmSettings) {
                            Text("Enable exact alarm access")
                        }
                    }
                    ChronosOptionChips(
                        label = "Reminder",
                        options = taskUrgentReminderPickerOptions(),
                        selected = reminderPreset,
                        onSelected = { preset ->
                            reminderPreset = preset
                            taskUrgentReminderPickerMinute(preset)?.let { value ->
                                if (value > 0) {
                                    reminder = formatDisplayMinute(value)
                                }
                            }
                        },
                        optionLabel = { it }
                    )

                    if (reminderPreset == "Custom" || urgentReminderPresets[reminderPreset] == null) {
                        ChronosTimePickerField(
                            label = "Reminder time",
                            value = parsedReminderMinute?.let(::formatDisplayMinute) ?: reminder.ifBlank { "Pick a time" },
                            selectedMinute = parsedReminderMinute,
                            onTimeSelected = { minute ->
                                reminder = formatDisplayMinute(minute)
                                reminderPreset = "Custom"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    reminder = nudgeMinuteText(reminder, -30, parsedReminderMinute ?: 18 * 60)
                                    reminderPreset = "Custom"
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("−30m") }
                            FilledTonalButton(
                                onClick = {
                                    reminder = nudgeMinuteText(reminder, 30, parsedReminderMinute ?: 18 * 60)
                                    reminderPreset = "Custom"
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("+30m") }
                        }
                    }

                    resolvedDueInstant?.let { due ->
                        Text(
                            text = "Alarm at ${formatTaskDueInstant(due)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (onOpenExactAlarmSettings != null && exactAlarmPermissionGranted) {
                        TextButton(onClick = onOpenExactAlarmSettings) {
                            Text("Manage exact alarm settings")
                        }
                    }
                }
            }
            }
        }

        ChronosCollapsibleSection(
            title = "Checklist",
            summary = if (checklistStepCount > 0) {
                taskChecklistSummary(
                    totalCount = checklistStepCount,
                    completedCount = completedChecklistStepCount
                )
            } else {
                "Break the work into concrete steps when this task needs them"
            },
            expanded = showTaskChecklistDetails,
            onExpandedChange = { checklistExpanded = it }
        ) {
            checklistItems.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = { checked ->
                            checklistItems[index] = item.copy(isCompleted = checked)
                        }
                    )
                    OutlinedTextField(
                        value = item.label,
                        onValueChange = { value ->
                            checklistItems[index] = item.copy(label = value)
                        },
                        label = { Text("Step ${index + 1}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(
                        onClick = { checklistItems.removeAt(index) },
                        modifier = Modifier.semantics {
                            contentDescription = taskChecklistRemoveActionLabel(item.label, index)
                        }
                    ) {
                        Text("Remove")
                    }
                }
            }
            ChronosQuickAddChips(
                label = "Suggested steps",
                options = availableTaskChecklistStepOptions(
                    options = contextualTaskChecklistStepOptions(
                        title = taskContextQuery,
                        description = description,
                        suggestions = contextualAssistSuggestions
                    ),
                    existingLabels = checklistItems.map { it.label }
                ),
                onSelect = { label ->
                    val normalizedLabel = label.trim()
                    if (
                        normalizedLabel.isNotEmpty() &&
                        checklistItems.none { it.label.equals(normalizedLabel, ignoreCase = true) }
                    ) {
                        checklistItems.add(
                            TaskChecklistItemDraft(
                                id = UUID.randomUUID().toString(),
                                label = normalizedLabel,
                                isCompleted = false
                            )
                        )
                    }
                }
            )
            OutlinedTextField(
                value = newChecklistItem,
                onValueChange = { newChecklistItem = it },
                label = { Text("New checklist item") },
                placeholder = { Text("Add the next concrete step") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val label = newChecklistItem.trim()
                        if (label.isNotEmpty()) {
                            checklistItems.add(
                                TaskChecklistItemDraft(
                                    id = UUID.randomUUID().toString(),
                                    label = label,
                                    isCompleted = false
                                )
                            )
                            newChecklistItem = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add step")
                }
                if (checklistItems.isNotEmpty()) {
                    TextButton(
                        onClick = { checklistItems.clear() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear all")
                    }
                }
            }
        }

        ChronosFormSection(
            title = "Connect",
            subtitle = taskConnectSectionSubtitle(
                contextText = taskContextQuery,
                suggestions = contextualAssistSuggestions
            )
        ) {
            TaskConnectQuickChips(
                contextText = taskContextQuery,
                suggestions = contextualAssistSuggestions,
                onContact = ::launchContactPicker,
                onPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onFile = {
                    attachmentPickerLauncher.launch(arrayOf("*/*"))
                },
                onLink = { prepareInlineAction(TaskActionType.WEBSITE, "Link") },
                onCall = { prepareInlineAction(TaskActionType.PHONE, "Call") },
                onEmail = { prepareInlineAction(TaskActionType.EMAIL, "Email") },
                onMap = { prepareInlineAction(TaskActionType.MAP, "Map") },
                onApp = { prepareInlineAction(TaskActionType.APP, "Open app") }
            )

        }

        ChronosCollapsibleSection(
            title = "Context details",
            summary = taskContextDraftSummary(
                linkedContact = linkedContact,
                actionCount = actionDrafts.size,
                attachmentCount = attachmentDrafts.size,
                primaryActionLabel = actionDrafts
                    .firstOrNull { it.isPrimary }
                    ?.label
                    ?.takeIf { it.isNotBlank() }
                    ?: actionDrafts.firstOrNull()?.label?.takeIf { it.isNotBlank() },
                primaryAttachmentName = attachmentDrafts
                    .firstOrNull { it.isFeaturedImage }
                    ?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: attachmentDrafts.firstOrNull()?.displayName?.takeIf { it.isNotBlank() }
            ),
            expanded = showTaskContextDetails,
            onExpandedChange = { connectExpanded = it }
        ) {
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = linkedContact?.displayName ?: "No linked contact yet",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (linkedContact == null) {
                        Text(
                            text = "Pick a contact to snapshot their saved phone numbers and email addresses into this task.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        linkedContact?.methods?.forEach { method ->
                            Text(
                                text = buildString {
                                    append(
                                        method.kind.name.lowercase().replaceFirstChar { char ->
                                            if (char.isLowerCase()) char.titlecase() else char.toString()
                                        }
                                    )
                                    method.label?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                                    append(": ${method.value}")
                                    if (method.isPrimary) append(" • primary")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (linkedContact?.methods.isNullOrEmpty()) {
                            Text(
                                text = "This contact did not expose any phone or email entries through the picker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = ::launchContactPicker,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (linkedContact == null) "Pick contact" else "Change contact")
                        }
                        if (linkedContact != null) {
                            TextButton(
                                onClick = { linkedContact = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Remove contact")
                            }
                        }
                    }
                }
            }

            if (actionDrafts.isNotEmpty()) {
                actionDrafts.forEachIndexed { index, draft ->
                    val actionTypeLabel = taskActionTypeLabel(draft.type)
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = actionTypeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = draft.label,
                                onValueChange = { value -> actionDrafts[index] = draft.copy(label = value) },
                                label = { Text("Action label") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = draft.value,
                                onValueChange = { value -> actionDrafts[index] = draft.copy(value = value) },
                                label = { Text("Destination") },
                                placeholder = { Text(taskActionValuePlaceholder(draft.type)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = normalizeTaskActionDraft(draft) == null
                            )
                            if (draft.type == TaskActionType.APP) {
                                ChronosLauncherAppPicker(
                                    selectedLaunchValue = draft.value,
                                    onAppSelected = { option ->
                                        actionDrafts[index] = draft.copy(
                                            label = draft.label
                                                .takeUnless { it.isBlank() || it == "Open app" }
                                                ?: "Open ${option.label}",
                                            value = option.launchValue
                                        )
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        actionDrafts.indices.forEach { actionIndex ->
                                            actionDrafts[actionIndex] = actionDrafts[actionIndex].copy(
                                                isPrimary = actionIndex == index
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = taskActionPrimaryControlLabel(
                                                label = draft.label,
                                                typeLabel = actionTypeLabel,
                                                isPrimary = draft.isPrimary
                                            )
                                        }
                                ) {
                                    Text(if (draft.isPrimary) "Primary action" else "Make primary")
                                }
                                TextButton(
                                    onClick = { actionDrafts.removeAt(index) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = taskActionRemoveControlLabel(
                                                label = draft.label,
                                                typeLabel = actionTypeLabel
                                            )
                                        }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }

            ChronosOptionChips(
                label = "New action type",
                options = contextualTaskActionTypeOptions(
                    contextText = "$taskContextQuery $newActionLabel $newActionValue",
                    selectedTypeName = newActionTypeName,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(newActionTypeName),
                selected = newActionTypeName,
                onSelected = { newActionTypeOverride = it },
                optionLabel = { typeName -> taskActionTypeLabel(TaskActionType.valueOf(typeName)) }
            )
            OutlinedTextField(
                value = newActionLabel,
                onValueChange = { newActionLabel = it },
                label = { Text("New action label") },
                placeholder = { Text(taskActionLabelPlaceholder(TaskActionType.valueOf(newActionTypeName))) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = newActionValue,
                onValueChange = { newActionValue = it },
                label = { Text("New action destination") },
                placeholder = { Text(taskActionValuePlaceholder(TaskActionType.valueOf(newActionTypeName))) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = pendingActionInvalid
            )
            if (TaskActionType.valueOf(newActionTypeName) == TaskActionType.APP) {
                ChronosLauncherAppPicker(
                    selectedLaunchValue = newActionValue,
                    onAppSelected = { option ->
                        newActionLabel = newActionLabel
                            .takeUnless { it.isBlank() || it == "Open app" }
                            ?: "Open ${option.label}"
                        newActionValue = option.launchValue
                    }
                )
            }
            FilledTonalButton(
                onClick = {
                    val newDraft = TaskActionDraft(
                        id = UUID.randomUUID().toString(),
                        type = TaskActionType.valueOf(newActionTypeName),
                        label = newActionLabel,
                        value = newActionValue,
                        isPrimary = actionDrafts.none { it.isPrimary }
                    )
                    if (normalizeTaskActionDraft(newDraft) != null) {
                        if (newDraft.isPrimary) {
                            actionDrafts.indices.forEach { index ->
                                actionDrafts[index] = actionDrafts[index].copy(isPrimary = false)
                            }
                        }
                        actionDrafts.add(newDraft)
                        newActionLabel = ""
                        newActionValue = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add action")
            }
        }

        if (showConnectedFilesSection) {
            ChronosCollapsibleSection(
                title = "Connected files",
                summary = if (attachmentDrafts.isEmpty()) {
                    "No files attached yet"
                } else {
                    "${attachmentDrafts.size} attached" +
                        (attachmentDrafts.firstOrNull { it.isFeaturedImage }
                            ?.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?.let { " · featured: $it" } ?: "")
                },
                expanded = connectedFilesExpanded,
                onExpandedChange = { connectedFilesExpanded = it }
            ) {
            if (featuredAttachmentPreview != null) {
                ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Featured image",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TaskAttachmentPreview(
                            attachment = featuredAttachmentPreview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                    }
                }
            }

            if (attachmentDrafts.isEmpty()) {
                ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "No files or images attached yet",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Link documents with the Storage Access Framework, or import a private copy when you want the task to keep its own file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                attachmentDrafts.forEachIndexed { index, draft ->
                    val previewAttachment = draft.toPreviewAttachment()
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = draft.displayName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = buildString {
                                    append(if (draft.kind == TaskAttachmentKind.IMAGE) "Image" else "File")
                                    append(" • ")
                                    append(if (draft.storageMode == TaskAttachmentStorageMode.LINKED) "Linked" else "Imported")
                                    draft.mimeType?.takeIf { it.isNotBlank() }?.let {
                                        append(" • ")
                                        append(it)
                                    }
                                    draft.sizeBytes?.let {
                                        append(" • ")
                                        append(formatAttachmentSize(it))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!draft.modeLocked) {
                                ChronosOptionChips(
                                    label = "Storage mode",
                                    options = listOf(
                                        TaskAttachmentStorageMode.LINKED.name,
                                        TaskAttachmentStorageMode.IMPORTED.name
                                    ),
                                    selected = draft.storageMode.name,
                                    onSelected = { modeName ->
                                        attachmentDrafts[index] = draft.copy(
                                            storageMode = TaskAttachmentStorageMode.valueOf(modeName)
                                        )
                                    },
                                    optionLabel = { modeName ->
                                        if (modeName == TaskAttachmentStorageMode.LINKED.name) "Link" else "Import copy"
                                    }
                                )
                            }
                            if (draft.kind == TaskAttachmentKind.IMAGE) {
                                FilledTonalButton(
                                    onClick = {
                                        val updated = attachmentDrafts.toList().map { item ->
                                            item.copy(isFeaturedImage = item.id == draft.id)
                                        }
                                        attachmentDrafts.clear()
                                        attachmentDrafts.addAll(normalizeTaskAttachmentDrafts(updated))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentDescription = taskAttachmentFeaturedActionLabel(draft)
                                        }
                                ) {
                                    Text(if (draft.isFeaturedImage) "Featured image" else "Set as featured image")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        if (previewAttachment != null) {
                                            openTaskAttachment(context, previewAttachment)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = taskAttachmentOpenActionLabel(draft)
                                        }
                                ) {
                                    Text("Open")
                                }
                                TextButton(
                                    onClick = {
                                        val updated = attachmentDrafts.toMutableList().apply { removeAt(index) }
                                        attachmentDrafts.clear()
                                        attachmentDrafts.addAll(normalizeTaskAttachmentDrafts(updated))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = taskAttachmentRemoveActionLabel(draft)
                                        }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = { attachmentPickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add files or images")
            }
        }
        }

        Spacer(modifier = Modifier.height(ChronosSpacing.Medium))
    }
}

internal fun taskCollapsedActionCardContentDescription(
    title: String,
    summary: String,
    actionLabel: String,
    expanded: Boolean
): String {
    val currentValue = summary.trim().ifBlank { "Not set" }
    val state = if (expanded) "Expanded" else "Collapsed"
    return "${actionLabel.trim()} for ${title.trim()}. Current value: $currentValue. $state"
}

internal fun taskActionPrimaryControlLabel(
    label: String,
    typeLabel: String,
    isPrimary: Boolean
): String {
    val actionName = taskContextControlTargetName(label, typeLabel)
    return if (isPrimary) {
        "$actionName is the primary task action"
    } else {
        "Make $actionName the primary task action"
    }
}

internal fun taskActionRemoveControlLabel(
    label: String,
    typeLabel: String
): String = "Remove task action ${taskContextControlTargetName(label, typeLabel)}"

internal fun taskAttachmentOpenActionLabel(draft: TaskAttachmentDraft): String {
    return "Open ${taskAttachmentControlTargetName(draft.displayName)} attachment"
}

internal fun taskAttachmentRemoveActionLabel(draft: TaskAttachmentDraft): String {
    return "Remove ${taskAttachmentControlTargetName(draft.displayName)} attachment"
}

internal fun taskAttachmentFeaturedActionLabel(draft: TaskAttachmentDraft): String {
    val attachmentName = taskAttachmentControlTargetName(draft.displayName)
    return if (draft.isFeaturedImage) {
        "$attachmentName is the featured image"
    } else {
        "Set $attachmentName as featured image"
    }
}

internal fun taskChecklistRemoveActionLabel(label: String, index: Int): String {
    val target = label.trim().ifBlank { "${index + 1}" }
    return "Remove checklist step $target"
}

private fun taskContextControlTargetName(label: String, fallbackLabel: String): String {
    return label.trim().ifBlank { fallbackLabel.trim().ifBlank { "this action" } }
}

private fun taskAttachmentControlTargetName(displayName: String): String {
    return displayName.trim().ifBlank { "this attachment" }
}

internal fun taskContextDraftSummary(
    linkedContact: TaskContactSnapshot?,
    actionCount: Int,
    attachmentCount: Int,
    primaryActionLabel: String? = null,
    primaryAttachmentName: String? = null
): String {
    val parts = buildList {
        linkedContact?.displayName?.let { add("Contact: $it") }
        if (actionCount > 0) {
            add(taskContextNamedCountSummary(
                label = "Action",
                fallbackLabel = "action",
                count = actionCount,
                primaryName = primaryActionLabel
            ))
        }
        if (attachmentCount > 0) {
            add(taskContextNamedCountSummary(
                label = "File",
                fallbackLabel = "attachment",
                count = attachmentCount,
                primaryName = primaryAttachmentName
            ))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        ?: "People, photos, files, links, calls, emails, maps, and app shortcuts."
}

private fun taskContextNamedCountSummary(
    label: String,
    fallbackLabel: String,
    count: Int,
    primaryName: String?
): String {
    val name = primaryName?.trim()?.takeIf { it.isNotEmpty() }
    if (name == null) {
        return "$count $fallbackLabel${if (count == 1) "" else "s"}"
    }
    val remainingCount = count - 1
    return if (remainingCount > 0) {
        "$label: $name + $remainingCount more"
    } else {
        "$label: $name"
    }
}

internal fun shouldShowTaskContextDetails(
    connectExpanded: Boolean,
    hasContext: Boolean
): Boolean = when {
    connectExpanded -> true
    hasContext -> false
    else -> false
}

internal fun shouldShowConnectedFilesSection(
    connectExpanded: Boolean,
    attachmentCount: Int
): Boolean = when {
    connectExpanded -> true
    attachmentCount > 0 -> false
    else -> false
}

internal fun shouldShowTaskScheduleDetails(
    scheduleExpanded: Boolean,
    hasSchedulePreferences: Boolean
): Boolean = when {
    scheduleExpanded -> true
    hasSchedulePreferences -> false
    else -> false
}

internal fun taskScheduleDraftSummary(
    targetDate: LocalDate?,
    preferredDurationMinutes: Int?,
    preferredStartMinuteOfDay: Int?,
    recurringConfig: TaskRecurringConfig,
    today: LocalDate = LocalDate.now()
): String {
    val parts = buildList {
        targetDate?.let { date -> add("Target ${formatTaskTargetDateLabel(date, today)}") }
        preferredDurationMinutes?.let { minutes -> add("${formatTaskDurationBlock(minutes)} block") }
        preferredStartMinuteOfDay?.let { minute -> add("Around ${formatDisplayMinute(minute)}") }
        taskRecurrenceDraftSummary(recurringConfig)?.let(::add)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        ?: "Target day, duration, preferred start, recurrence, and reminders."
}

private fun formatTaskDurationBlock(durationMinutes: Int): String {
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${durationMinutes}m"
    }
}

internal fun taskDurationPickerOptions(): List<String> {
    return durationPresets.map(::formatTaskDurationBlock) + anyDurationOption
}

internal fun contextualTaskDurationPickerOptions(
    title: String,
    description: String,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val normalized = "$title $description".lowercase()
    val suggestedOptions = suggestions.mapNotNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.Schedule)
            ?.payload
            ?.preferredDurationMinutes
            ?.takeIf { it in durationPresets }
            ?.let(::formatTaskDurationBlock)
    }
    val actionDrivenOptions = buildList {
        val actionTypes = suggestions.mapNotNull { suggestion ->
            (suggestion as? TaskAssistSuggestion.ActionDraft)?.payload?.type
        }
        if (actionTypes.any { it == TaskActionType.PHONE || it == TaskActionType.EMAIL }) {
            add(formatTaskDurationBlock(15))
        }
        if (
            actionTypes.any {
                it == TaskActionType.WEBSITE ||
                    it == TaskActionType.DOCUMENT ||
                    it == TaskActionType.MAP ||
                    it == TaskActionType.APP ||
                    it == TaskActionType.CUSTOM_DEEP_LINK
            }
        ) {
            add(formatTaskDurationBlock(30))
        }
    }
    val checklistDrivenOptions = buildList {
        val suggestedStepCount = suggestions
            .filterIsInstance<TaskAssistSuggestion.Checklist>()
            .sumOf { suggestion -> suggestion.items.count { it.isNotBlank() } }
        when {
            suggestedStepCount >= 5 -> add(formatTaskDurationBlock(60))
            suggestedStepCount >= 3 -> add(formatTaskDurationBlock(45))
            suggestedStepCount > 0 -> add(formatTaskDurationBlock(30))
        }
    }
    val contextOptions = buildList {
        taskDurationMentionMinutes(normalized)?.let { add(formatTaskDurationBlock(it)) }
        when {
            TASK_QUICK_DURATION_WORDS.any { it in normalized } -> add(formatTaskDurationBlock(15))
            TASK_LONG_DURATION_WORDS.any { it in normalized } -> add(formatTaskDurationBlock(60))
        }
    }

    val contextualOptions = suggestedOptions + actionDrivenOptions + checklistDrivenOptions + contextOptions
    val optionLimit = if (contextualOptions.isNotEmpty()) 5 else taskDurationPickerOptions().size
    return (contextualOptions + taskDurationPickerOptions())
        .distinct()
        .take(optionLimit)
}

internal fun taskDurationPickerLabel(durationMinutes: Int?): String {
    return durationMinutes?.let(::formatTaskDurationBlock) ?: anyDurationOption
}

internal fun taskDurationPickerMinutes(label: String): Int? {
    if (label == anyDurationOption) return null
    durationPresets.firstOrNull { formatTaskDurationBlock(it) == label }?.let { return it }
    // Slider-tuned estimates surface as custom "1h 20m"-style chips; parse them back.
    val match = Regex("""^(?:(\d+)h)?\s*(?:(\d+)m)?$""").matchEntire(label.trim()) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: 0
    return (hours * 60 + minutes).takeIf { it > 0 }
}

private fun taskRecurrenceDraftSummary(config: TaskRecurringConfig): String? {
    if (!config.enabled) return null
    return when (config.cadence) {
        TaskRecurringCadence.DAILY ->
            "Repeats ${if (config.interval == 1) "daily" else "every ${config.interval} days"}"
        TaskRecurringCadence.WEEKLY -> {
            val weekdays = config.weekdays
                .sortedBy { it.value }
                .joinToString(" + ") { it.name.take(3).lowercase().replaceFirstChar(Char::titlecase) }
            if (weekdays.isBlank()) {
                "Repeats weekly"
            } else {
                "Repeats $weekdays"
            }
        }
        TaskRecurringCadence.MONTHLY_DAY_OF_MONTH ->
            "Repeats day ${config.dayOfMonth}"
        TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY ->
            "Repeats ${config.ordinal.displayOrdinalLabel()} ${config.ordinalWeekday.name.lowercase().replaceFirstChar(Char::titlecase)}"
    }
}

internal fun shouldShowTaskChecklistDetails(
    checklistExpanded: Boolean,
    checklistCount: Int
): Boolean = when {
    checklistExpanded -> true
    checklistCount > 0 -> false
    else -> false
}

internal fun taskChecklistSummary(
    totalCount: Int,
    completedCount: Int
): String {
    if (totalCount <= 0) return "No steps yet."
    val stepLabel = "$totalCount step${if (totalCount == 1) "" else "s"}"
    val normalizedCompleted = completedCount.coerceIn(0, totalCount)
    return if (normalizedCompleted > 0) {
        "$stepLabel · $normalizedCompleted complete"
    } else {
        stepLabel
    }
}

internal fun shouldShowTaskPriorityDetails(
    priorityExpanded: Boolean,
    hasPrioritySettings: Boolean
): Boolean = when {
    priorityExpanded -> true
    hasPrioritySettings -> false
    else -> false
}

internal fun taskPrioritySummary(
    priority: Int,
    alarmEnabled: Boolean,
    dueDate: Instant? = null,
    today: LocalDate = LocalDate.now()
): String {
    val label = when {
        priority >= 2 -> "Urgent priority"
        priority == 1 -> "High priority"
        else -> "Normal priority"
    }
    return if (priority >= 2) {
        val alarmLabel = when {
            !alarmEnabled -> "alarm off"
            dueDate != null -> "alarm ${taskPriorityDueDateLabel(dueDate, today)}"
            else -> "exact alarm on"
        }
        "$label · $alarmLabel"
    } else {
        label
    }
}

internal fun contextualTaskPriorityOptions(
    title: String,
    description: String,
    selectedPriority: Int,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val normalized = "$title $description".lowercase()
    val suggestedOptions = suggestions.mapNotNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.Priority)
            ?.priority
            ?.coerceIn(0, 2)
            ?.let { priorityOptions.first { option -> option.first == it }.second }
    }
    val contextOptions = buildList {
        if (listOf("urgent", "critical", "asap", "deadline", "due now", "right now").any { it in normalized }) {
            add("Urgent")
        }
        if (listOf("important", "high priority", "focus").any { it in normalized }) {
            add("High")
        }
        if (listOf("low priority", "someday", "later", "when possible").any { it in normalized }) {
            add("Normal")
        }
    }
    val contextualOptions = suggestedOptions + contextOptions
    val optionLimit = if (contextualOptions.isNotEmpty()) 2 else priorityOptions.size
    return (
        contextualOptions +
            listOf(priorityOptions.first { it.first == selectedPriority.coerceIn(0, 2) }.second) +
            priorityOptions.map { it.second }
        )
        .distinct()
        .take(optionLimit)
}

internal fun contextualTaskChecklistStepOptions(
    title: String,
    description: String,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val normalized = "$title $description".lowercase()
    val suggestedSteps = suggestions
        .filterIsInstance<TaskAssistSuggestion.Checklist>()
        .flatMap { it.items }
    val contextSteps = when {
        Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
            .containsMatchIn(normalized) -> listOf(
            "Confirm attendees and timing",
            "Add agenda, location, or note",
            "Send invite or RSVP"
        )
        Regex("""\b(send|share|forward|attach|review)\s+(document|doc|pdf|file|attachment|proposal|brief)\b""")
            .containsMatchIn(normalized) -> listOf(
            "Find the right file",
            "Review or attach context",
            "Send and capture follow-up"
        )
        Regex("""\b(send|share|forward|open|review)\s+(link|website|url|webpage|page)\b""").containsMatchIn(normalized) ||
            "http://" in normalized ||
            "https://" in normalized -> listOf(
            "Open and verify the link",
            "Add the link to the task",
            "Send or review follow-up"
        )
        Regex("""\b(text|message|sms|dm|ping|ask|tell|send text|send message|send sms|send dm)\b""").containsMatchIn(normalized) -> listOf(
            "Confirm the right recipient",
            "Send the message",
            "Capture any reply"
        )
        "call" in normalized || "phone" in normalized -> listOf(
            "Confirm the right contact",
            "Make the call",
            "Capture follow-up"
        )
        "email" in normalized || "reply" in normalized || "mail" in normalized -> listOf(
            "Draft the message",
            "Attach or link context",
            "Send and note follow-up"
        )
        "pay" in normalized || "bill" in normalized || "invoice" in normalized -> listOf(
            "Confirm amount and due date",
            "Pay from the right account",
            "Save confirmation"
        )
        "return" in normalized && ("package" in normalized || "order" in normalized) -> listOf(
            "Find return label and package",
            "Confirm drop-off location",
            "Drop off the package"
        )
        "buy" in normalized || "grocery" in normalized || "groceries" in normalized || "order" in normalized -> listOf(
            "List needed items",
            "Check what is already at home",
            "Buy the missing items"
        )
        "shop" in normalized -> listOf(
            "Confirm what is needed",
            "Place or pick up the order",
            "Save receipt or tracking"
        )
        "meeting" in normalized || "meet " in normalized || "appointment" in normalized -> listOf(
            "Confirm agenda or purpose",
            "Bring notes or links",
            "Capture follow-up actions"
        )
        "review" in normalized || "prepare" in normalized || "plan" in normalized -> listOf(
            "Open source material",
            "Capture key decisions",
            "Send or file the outcome"
        )
        else -> emptyList()
    }
    return (suggestedSteps + contextSteps)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .take(6)
}

// Suggested-step chips disappear once their step is in the checklist, mirroring how
// applied/redundant assist pills leave the row.
internal fun availableTaskChecklistStepOptions(
    options: List<String>,
    existingLabels: List<String>
): List<String> {
    val known = existingLabels
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    return options.filterNot { it.trim().lowercase() in known }
}

internal fun contextualTaskActionTypeSignals(
    contextText: String,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val normalized = contextText.lowercase()
    val suggestedTypes = suggestions
        .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
        .map { it.payload.type.name }
    val contextTypes = buildList {
        if (Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
                .containsMatchIn(normalized)
        ) {
            add(TaskActionType.EMAIL.name)
        }
        if (Regex("""\b(send|share|forward)\s+(document|doc|pdf|file|attachment|proposal|brief)\b""")
                .containsMatchIn(normalized)
        ) {
            add(TaskActionType.DOCUMENT.name)
        }
        if (Regex("""\b(send|share|forward)\s+(link|website|webpage|url)\b""").containsMatchIn(normalized)) {
            add(TaskActionType.WEBSITE.name)
        }
        if (Regex("""\b(call|phone|ring|text|message|sms|dm|ping|ask|tell|send text|send message|send sms|send dm)\b""").containsMatchIn(normalized)) add(TaskActionType.PHONE.name)
        if (Regex("""\b(email|mail|reply|respond)\b""").containsMatchIn(normalized)) add(TaskActionType.EMAIL.name)
        if (Regex("""\b(map|address|location|venue|directions)\b""").containsMatchIn(normalized)) add(TaskActionType.MAP.name)
        if (Regex("""\b(document|doc|file|pdf|sheet|slides|proposal|brief)\b""").containsMatchIn(normalized)) add(TaskActionType.DOCUMENT.name)
        if (Regex("""\b(link|website|web|url)\b""").containsMatchIn(normalized) || "http://" in normalized || "https://" in normalized) {
            add(TaskActionType.WEBSITE.name)
        }
        if (Regex("""\b(open app|launch app|package:|component:|intent:)\b""").containsMatchIn(normalized)) {
            add(TaskActionType.APP.name)
        }
        if (Regex("""\b(deep link|deeplink)\b""").containsMatchIn(normalized)) add(TaskActionType.CUSTOM_DEEP_LINK.name)
    }
    return suggestedTypes + contextTypes
}

// The pending Connect action defaults to the type the task context implies — AI
// action suggestions win over keyword cues; WEBSITE only as the no-signal fallback.
internal fun contextualTaskDefaultActionTypeName(
    contextText: String,
    suggestions: List<TaskAssistSuggestion>
): String = contextualTaskActionTypeSignals(contextText, suggestions).firstOrNull()
    ?: TaskActionType.WEBSITE.name

internal fun contextualTaskActionTypeOptions(
    contextText: String,
    selectedTypeName: String,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val contextualTypes = contextualTaskActionTypeSignals(contextText, suggestions)
    val optionLimit = if (contextualTypes.isNotEmpty()) 4 else TaskActionType.entries.size
    return (
        contextualTypes +
            listOf(selectedTypeName.takeIf { candidate -> TaskActionType.entries.any { it.name == candidate } }) +
            TaskActionType.entries.map { it.name }
        )
        .filterNotNull()
        .distinct()
        .take(optionLimit)
}

internal fun contextualTaskRepeatOptions(
    contextText: String,
    recurringConfig: TaskRecurringConfig
): List<String> {
    val contextualCadences = contextualTaskRecurringCadenceContextOptions(contextText)
    val selectedOption = taskRepeatOptionValue(recurringConfig)
    val fallbackOptions = if (recurringConfig.enabled) {
        listOf(selectedOption, TASK_REPEAT_ONE_TIME) + recurringCadenceOptions.map { it.name }
    } else {
        listOf(TASK_REPEAT_ONE_TIME) + recurringCadenceOptions.map { it.name }
    }
    val optionLimit = if (contextualCadences.isNotEmpty()) 4 else 5
    return (
        contextualCadences +
            fallbackOptions
        )
        .distinct()
        .take(optionLimit)
}

internal fun contextualTaskRecurringCadenceContextOptions(contextText: String): List<String> {
    val normalized = contextText.lowercase()
    return buildList {
        if (Regex("""\b(daily|every day|every morning|every night|every\s+(?:other|\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+days?)\b""").containsMatchIn(normalized)) {
            add(TaskRecurringCadence.DAILY.name)
        }
        if (
            Regex("""\b(weekly|bi-?weekly|fortnightly|every week|once a week|weekdays?|weekends?|every\s+(?:\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+weeks?|every\s+other\s+(week|mon|monday|tue|tuesday|wed|wednesday|thu|thursday|fri|friday|sat|saturday|sun|sunday)|every\s+(mon|monday|tue|tuesday|wed|wednesday|thu|thursday|fri|friday|sat|saturday|sun|sunday))\b""")
                .containsMatchIn(normalized)
        ) {
            add(TaskRecurringCadence.WEEKLY.name)
        }
        if (Regex("""\b(monthly|quarterly|every month|every\s+(?:other|\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+months?|day of month|on the \d{1,2}(st|nd|rd|th)?)\b""").containsMatchIn(normalized)) {
            add(TaskRecurringCadence.MONTHLY_DAY_OF_MONTH.name)
        }
        if (
            Regex("""\b(first|second|third|fourth|last)\s+(mon|tue|wed|thu|fri|sat|sun|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""")
                .containsMatchIn(normalized)
        ) {
            add(TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY.name)
        }
    }.distinct()
}

internal fun contextualTaskRecurringCadenceOptions(
    contextText: String,
    selectedCadence: TaskRecurringCadence
): List<String> {
    val contextCadences = contextualTaskRecurringCadenceContextOptions(contextText)
    val optionLimit = if (contextCadences.isNotEmpty()) 3 else recurringCadenceOptions.size
    return (
        contextCadences +
            selectedCadence.name +
            recurringCadenceOptions.map { it.name }
        )
        .distinct()
        .take(optionLimit)
}

internal fun contextualTaskRecurringInterval(
    contextText: String,
    cadence: TaskRecurringCadence
): Int? {
    val normalized = contextText.lowercase()
    return when (cadence) {
        TaskRecurringCadence.DAILY ->
            contextualTaskRecurringIntervalForUnit(normalized, "day")
        TaskRecurringCadence.WEEKLY -> when {
            Regex("""\b(bi-?weekly|fortnightly|every\s+other\s+(week|mon|monday|tue|tuesday|wed|wednesday|thu|thursday|fri|friday|sat|saturday|sun|sunday))\b""")
                .containsMatchIn(normalized) -> 2
            else -> contextualTaskRecurringIntervalForUnit(normalized, "week")
        }
        TaskRecurringCadence.MONTHLY_DAY_OF_MONTH,
        TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY -> when {
            Regex("""\bquarterly\b""").containsMatchIn(normalized) -> 3
            else -> contextualTaskRecurringIntervalForUnit(normalized, "month")
        }
    }
}

private fun contextualTaskRecurringIntervalForUnit(
    normalized: String,
    unit: String
): Int? {
    if (Regex("""\bevery\s+other\s+${unit}s?\b""").containsMatchIn(normalized)) return 2
    val intervalValue = Regex("""\bevery\s+(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+${unit}s?\b""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
    return intervalValue?.let(::taskRecurringIntervalValue)
}

private fun taskRecurringIntervalValue(value: String): Int? = when (value.lowercase()) {
    "one" -> 1
    "two" -> 2
    "three" -> 3
    "four" -> 4
    "five" -> 5
    "six" -> 6
    "seven" -> 7
    "eight" -> 8
    "nine" -> 9
    "ten" -> 10
    "eleven" -> 11
    "twelve" -> 12
    else -> value.toIntOrNull()?.coerceAtLeast(1)
}

internal fun taskRepeatOptionValue(config: TaskRecurringConfig): String {
    return if (config.enabled) config.cadence.name else TASK_REPEAT_ONE_TIME
}

internal fun taskRepeatOptionCadence(option: String): TaskRecurringCadence? {
    return TaskRecurringCadence.entries.firstOrNull { it.name == option }
}

internal fun taskRepeatOptionLabel(option: String): String {
    if (option == TASK_REPEAT_ONE_TIME) return "One-time"
    return when (TaskRecurringCadence.valueOf(option)) {
        TaskRecurringCadence.DAILY -> "Daily"
        TaskRecurringCadence.WEEKLY -> "Weekly"
        TaskRecurringCadence.MONTHLY_DAY_OF_MONTH -> "Monthly date"
        TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY -> "Monthly weekday"
    }
}

internal fun contextualTaskWeeklyDays(contextText: String): Set<DayOfWeek> {
    val normalized = contextText.lowercase()
    val days = mutableSetOf<DayOfWeek>()
    if (Regex("""\b(weekday|weekdays|workday|workdays)\b""").containsMatchIn(normalized)) {
        days += listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
    }
    if (Regex("""\b(weekend|weekends)\b""").containsMatchIn(normalized)) {
        days += listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
    if (Regex("""\b(mon|monday)\b""").containsMatchIn(normalized)) days += DayOfWeek.MONDAY
    if (Regex("""\b(tue|tuesday)\b""").containsMatchIn(normalized)) days += DayOfWeek.TUESDAY
    if (Regex("""\b(wed|wednesday)\b""").containsMatchIn(normalized)) days += DayOfWeek.WEDNESDAY
    if (Regex("""\b(thu|thursday)\b""").containsMatchIn(normalized)) days += DayOfWeek.THURSDAY
    if (Regex("""\b(fri|friday)\b""").containsMatchIn(normalized)) days += DayOfWeek.FRIDAY
    if (Regex("""\b(sat|saturday)\b""").containsMatchIn(normalized)) days += DayOfWeek.SATURDAY
    if (Regex("""\b(sun|sunday)\b""").containsMatchIn(normalized)) days += DayOfWeek.SUNDAY
    return days
}

private fun taskPriorityDueDateLabel(instant: Instant, today: LocalDate): String {
    val local = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    val dayLabel = when (local.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> local.toLocalDate().toString()
    }
    return "$dayLabel at ${formatDisplayMinute(local.hour * 60 + local.minute)}"
}

internal fun taskAssistActionDraftFromSuggestion(
    suggestion: TaskAssistSuggestion.ActionDraft,
    isPrimary: Boolean,
    id: String = UUID.randomUUID().toString()
): TaskActionDraft? {
    val draft = TaskActionDraft(
        id = id,
        type = suggestion.payload.type,
        label = suggestion.payload.label,
        value = suggestion.payload.value,
        isPrimary = isPrimary
    )
    return draft.takeIf { normalizeTaskActionDraft(it) != null }
}

private data class TaskModalAdaptiveHints(
    val showContext: Boolean,
    val showSchedule: Boolean,
    val showChecklist: Boolean,
    val showPriority: Boolean,
    val primaryActionType: TaskActionType?,
    val primaryActionLabel: String?
)

private fun taskModalAdaptiveHints(
    title: String,
    description: String,
    suggestions: List<TaskAssistSuggestion>
): TaskModalAdaptiveHints {
    val normalized = "$title $description".lowercase()
    val primaryActionDraft = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.ActionDraft)?.payload
    }
    val primaryActionType = primaryActionDraft?.type ?: contextualTaskPrimaryActionType(normalized)
    return TaskModalAdaptiveHints(
        showContext = primaryActionType != null ||
            TASK_CONTEXT_WORDS.any { it in normalized },
        showSchedule = suggestions.any { it is TaskAssistSuggestion.Schedule } ||
            TASK_SCHEDULE_WORDS.any { it in normalized },
        showChecklist = suggestions.any { it is TaskAssistSuggestion.Checklist } ||
            TASK_CHECKLIST_WORDS.any { it in normalized },
        showPriority = suggestions.any { it is TaskAssistSuggestion.Priority } ||
            TASK_PRIORITY_WORDS.any { it in normalized },
        primaryActionType = primaryActionType,
        primaryActionLabel = primaryActionDraft?.label?.trim()?.lowercase() ?:
            contextualTaskPrimaryActionLabel(normalized)
    )
}

private val TaskModalAdaptiveHints.isMessageTask: Boolean
    get() = primaryActionType == TaskActionType.PHONE &&
        primaryActionLabel?.let { label ->
            Regex("""\b(text|message|sms|dm|ping)\b""").containsMatchIn(label)
        } == true

private fun contextualTaskPrimaryActionType(normalizedText: String): TaskActionType? {
    return when {
        Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
            .containsMatchIn(normalizedText) -> TaskActionType.EMAIL
        Regex("""\b(send|share|forward)\s+(document|doc|pdf|file|attachment|proposal|brief)\b""")
            .containsMatchIn(normalizedText) -> TaskActionType.DOCUMENT
        Regex("""\b(send|share|forward)\s+(link|website|url|webpage|page)\b""")
            .containsMatchIn(normalizedText) -> TaskActionType.WEBSITE
        Regex("""\b(email|mail|reply|respond)\b""").containsMatchIn(normalizedText) -> TaskActionType.EMAIL
        Regex("""\b(call|phone|text|message|sms|dm|ping|ask|tell|send text|send message|send sms|send dm)\b""")
            .containsMatchIn(normalizedText) -> TaskActionType.PHONE
        Regex("""\b(map|route|drive|directions|pickup|pick up|drop off|return|bring|deliver|visit)\b""").containsMatchIn(normalizedText) -> TaskActionType.MAP
        Regex("""\b(document|doc|pdf|file|attachment|attach|proposal|brief)\b""").containsMatchIn(normalizedText) -> TaskActionType.DOCUMENT
        Regex("""\b(link|website|url|webpage|page)\b""").containsMatchIn(normalizedText) -> TaskActionType.WEBSITE
        Regex("""\b(app|open)\b""").containsMatchIn(normalizedText) -> TaskActionType.APP
        else -> null
    }
}

private fun contextualTaskPrimaryActionLabel(normalizedText: String): String? {
    return when {
        Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
            .containsMatchIn(normalizedText) -> "invite"
        Regex("""\b(send|share|forward)\s+(document|doc|pdf|file|attachment|proposal|brief)\b""")
            .containsMatchIn(normalizedText) -> "document"
        Regex("""\b(send|share|forward)\s+(link|website|url|webpage|page)\b""")
            .containsMatchIn(normalizedText) -> "link"
        Regex("""\b(email|mail|reply|respond)\b""").containsMatchIn(normalizedText) -> "email"
        Regex("""\b(text|message|sms|dm|ping|ask|tell|send text|send message|send sms|send dm)\b""")
            .containsMatchIn(normalizedText) -> "message"
        Regex("""\b(call|phone)\b""").containsMatchIn(normalizedText) -> "call"
        Regex("""\b(map|route|drive|directions|pickup|pick up|drop off|return|bring|deliver|visit)\b""").containsMatchIn(normalizedText) -> "map"
        Regex("""\b(document|doc|pdf|file|attachment|attach|proposal|brief)\b""").containsMatchIn(normalizedText) -> "document"
        Regex("""\b(link|website|url|webpage|page)\b""").containsMatchIn(normalizedText) -> "link"
        Regex("""\b(app|open)\b""").containsMatchIn(normalizedText) -> "app"
        else -> null
    }
}

private fun taskAddModalDynamicTitle(
    taskTitle: String,
    description: String,
    hints: TaskModalAdaptiveHints,
    suggestions: List<TaskAssistSuggestion>
): String {
    val aiTitleSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.Title)?.title?.trim()?.takeIf(String::isNotBlank)
    }
    val normalized = "$taskTitle $description".lowercase()
    return when {
        taskTitle.isBlank() && aiTitleSuggestion != null -> "New ${aiTitleSuggestion.trim().replaceFirstChar { it.uppercaseChar() }}"
        hints.isMessageTask -> "New message task"
        Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
            .containsMatchIn(normalized) -> "New invite task"
        hints.primaryActionType == TaskActionType.PHONE -> "New call task"
        hints.primaryActionType == TaskActionType.EMAIL -> "New email task"
        hints.primaryActionType == TaskActionType.MAP -> "New location task"
        hints.primaryActionType == TaskActionType.DOCUMENT -> "New document task"
        hints.primaryActionType == TaskActionType.WEBSITE -> "New linked task"
        hints.primaryActionType == TaskActionType.APP -> "New app task"
        hints.primaryActionType == TaskActionType.CUSTOM_DEEP_LINK -> "New app-link task"
        hints.showPriority -> "New priority task"
        hints.showSchedule && hints.showContext -> "New scheduled task with context"
        hints.showSchedule -> "New scheduled task"
        hints.showChecklist -> "New checklist task"
        hints.showContext -> "New task with context"
        Regex("""\b(call|phone|text|message|sms|dm|ping|ask|tell|send|email|reply)\b""").containsMatchIn(normalized) ->
            "New communication task"
        "review" in normalized || "prepare" in normalized -> "New review task"
        "buy" in normalized || "order" in normalized -> "New purchase task"
        "pay" in normalized || "invoice" in normalized -> "New payment task"
        else -> "New task"
    }
}

private fun taskModalDynamicTitle(
    isAddMode: Boolean,
    taskTitle: String,
    description: String,
    hints: TaskModalAdaptiveHints,
    suggestions: List<TaskAssistSuggestion>,
    editTitle: String
): String {
    return if (isAddMode) {
        taskAddModalDynamicTitle(
            taskTitle = taskTitle,
            description = description,
            hints = hints,
            suggestions = suggestions
        )
    } else {
        editTitle
    }
}

private fun taskAddModalDynamicSubtitle(
    taskTitle: String,
    description: String,
    hints: TaskModalAdaptiveHints,
    hasContext: Boolean,
    suggestions: List<TaskAssistSuggestion>
): String {
    val aiActionSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.ActionDraft)?.label?.trim()?.takeIf(String::isNotBlank)
    }
    val normalized = "$taskTitle $description".lowercase()
    return when {
        hints.isMessageTask ->
            "Message context detected. Add the contact or phone action before saving."
        Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
            .containsMatchIn(normalized) ->
            "Invite context detected. Add the attendee or email action before saving."
        hints.primaryActionType == TaskActionType.PHONE ->
            "Call context detected. Add the contact or phone action before saving."
        hints.primaryActionType == TaskActionType.EMAIL ->
            "Email context detected. Add the recipient or message link before saving."
        hints.primaryActionType == TaskActionType.MAP ->
            "Location context detected. Add map details so the task opens with directions."
        hints.primaryActionType == TaskActionType.DOCUMENT ->
            "Document context detected. Attach the file or link before saving."
        hints.primaryActionType == TaskActionType.WEBSITE ->
            "Link context detected. Add the website action so the task is ready to open."
        hints.primaryActionType == TaskActionType.APP || hints.primaryActionType == TaskActionType.CUSTOM_DEEP_LINK ->
            "App context detected. Add the launch target before saving."
        aiActionSuggestion != null && !hasContext && taskTitle.isBlank() ->
            "Action suggested from AI: $aiActionSuggestion. Add links or contacts to complete setup."
        hasContext -> "Link people, files, or links based on your task content."
        hints.showPriority -> "Add urgency and alarm details before you save."
        hints.showSchedule -> "Schedule details are suggested from task context."
        hints.showChecklist -> "A checklist can be expanded to break this into steps."
        "call" in normalized || "email" in normalized || "reply" in normalized ->
            "Capture response flow details while you add this task."
        "buy" in normalized || "order" in normalized || "pay" in normalized ->
            "Capture billing and timing details while you create this task."
        else -> "Capture the commitment, then protect time on the DayDial."
    }
}

private fun taskScheduleSectionSubtitle(
    title: String,
    description: String,
    targetDate: LocalDate?,
    durationMinutes: Int?,
    preferredStartMinute: Int?,
    suggestions: List<TaskAssistSuggestion>
): String {
    val normalized = "$title $description".lowercase()
    val aiSchedule = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.Schedule)?.payload
    }
    return when {
        aiSchedule?.targetDate != null || targetDate != null ->
            "Date context detected. Confirm the target day before the DayDial places it."
        aiSchedule?.preferredStartMinuteOfDay != null || preferredStartMinute != null ->
            "Start-time context detected. Keep it if the task needs a protected slot."
        aiSchedule?.preferredDurationMinutes != null ->
            "AI suggested a duration. Adjust only if the block size is wrong."
        Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
            .containsMatchIn(normalized) ->
            "Invite task detected. Keep a short block for attendee, agenda, or RSVP details."
        Regex("""\b(call|phone|text|message|sms|dm|ping|ask|tell|send text|send message|send sms|send dm|reply)\b""")
            .containsMatchIn(normalized) ->
            "Communication task detected. A short protected block is usually enough."
        Regex("""\b(review|brief|draft|write|prepare|research|study)\b""").containsMatchIn(normalized) ->
            "Deep-work context detected. Choose a duration that protects focus time."
        Regex("""\b(today|tomorrow|tonight|morning|afternoon|evening|deadline|due|before|after)\b""")
            .containsMatchIn(normalized) ->
            "Schedule words detected. Confirm day and start time before saving."
        durationMinutes != null && durationMinutes <= 15 ->
            "Quick task duration selected. Increase it only if this needs real focus time."
        durationMinutes != null && durationMinutes >= 60 ->
            "Longer task duration selected. The DayDial will treat this as a real block."
        else -> "Save the shape of the work so scheduling can place it faster."
    }
}

private fun taskChecklistSectionSubtitle(
    title: String,
    description: String,
    checklistStepCount: Int,
    suggestions: List<TaskAssistSuggestion>
): String {
    val normalized = "$title $description".lowercase()
    val suggestedStepCount = suggestions
        .filterIsInstance<TaskAssistSuggestion.Checklist>()
        .sumOf { suggestion -> suggestion.items.count { it.isNotBlank() } }
    return when {
        suggestedStepCount > 0 ->
            "AI suggested $suggestedStepCount step${if (suggestedStepCount == 1) "" else "s"}. Apply or edit them before saving."
        checklistStepCount > 0 ->
            "$checklistStepCount step${if (checklistStepCount == 1) "" else "s"} ready. Manage them if the task needs more structure."
        Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|rsvp|save the date)\b""")
            .containsMatchIn(normalized) ->
            "Invite task detected. Checklist steps can cover attendees, agenda, and follow-up."
        Regex("""\b(call|phone|text|message|sms|dm|ping|ask|tell|send text|send message|send sms|send dm|email|reply)\b""")
            .containsMatchIn(normalized) ->
            "Communication task detected. Add follow-up steps only if there is more than one action."
        Regex("""\b(review|prepare|brief|draft|write|research|study|plan)\b""").containsMatchIn(normalized) ->
            "Review or deep-work task detected. Checklist steps can keep the work actionable."
        Regex("""\b(buy|order|pay|invoice|grocery|shop|pickup|pick up|drop off)\b""").containsMatchIn(normalized) ->
            "Errand or payment context detected. Add steps for materials, pickup, or follow-up."
        TASK_CHECKLIST_WORDS.any { it in normalized } ->
            "Step-like wording detected. Open checklist if you want the task broken down."
        else -> "Open this when the task needs concrete steps."
    }
}

private fun taskModalDynamicSubtitle(
    isAddMode: Boolean,
    taskTitle: String,
    description: String,
    hints: TaskModalAdaptiveHints,
    hasContext: Boolean,
    suggestions: List<TaskAssistSuggestion>,
    editSubtitle: String
): String {
    return if (isAddMode) {
        taskAddModalDynamicSubtitle(
            taskTitle = taskTitle,
            description = description,
            hints = hints,
            hasContext = hasContext,
            suggestions = suggestions
        )
    } else {
        editSubtitle
    }
}

private fun taskEditModalDynamicTitle(taskTitle: String): String {
    return if (taskTitle.isNotBlank()) "Edit task: ${taskTitle.trim()}" else "Edit task"
}

private fun taskEditModalDynamicSubtitle(
    taskTitle: String,
    description: String,
    dueInstant: Instant?,
    isUrgent: Boolean,
    alarmEnabled: Boolean,
    checklistCount: Int,
    hasLinkedContact: Boolean,
    actionCount: Int,
    attachmentCount: Int
): String {
    if (taskTitle.isBlank()) return "Editing task details"
    val details = buildList {
        if (description.isNotBlank()) add("notes")
        dueInstant?.let { add("due ${formatTaskDueInstant(it)}") }
        if (isUrgent && alarmEnabled) add("urgent reminder")
        if (checklistCount > 0) add("checklist ($checklistCount)")
        if (hasLinkedContact) add("contact linked")
        if (actionCount > 0) add("actions ($actionCount)")
        if (attachmentCount > 0) add("attachments ($attachmentCount)")
    }.take(3)
    return if (details.isEmpty()) {
        "Editing ${taskTitle.trim()}"
    } else {
        "Editing ${taskTitle.trim()} · ${details.joinToString(" · ")}"
    }
}

private data class TaskTranscriptDraft(
    val title: String? = null,
    val priority: Int? = null,
    val durationMinutes: Int? = null,
    val preferredStartMinute: Int? = null,
    val scheduleDateOption: String? = null
)

private fun taskTranscriptDraft(capture: String): TaskTranscriptDraft {
    val normalized = capture.lowercase()
    return TaskTranscriptDraft(
        title = taskTranscriptTitle(capture, normalized),
        priority = taskTranscriptPriority(normalized),
        durationMinutes = taskTranscriptDurationMinutes(normalized),
        preferredStartMinute = taskTranscriptPreferredStartMinute(capture, normalized),
        scheduleDateOption = taskTranscriptScheduleDateOption(normalized)
    )
}

private fun taskTranscriptTitle(capture: String, normalized: String): String? {
    val actionTitle = when {
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_CALL_WORDS) ->
            taskTranscriptSubjectTitle(capture, "Call") ?: "Call"
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_EMAIL_WORDS) ->
            taskTranscriptSubjectTitle(capture, "Email") ?: "Email"
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_MAP_WORDS) ->
            taskTranscriptSubjectTitle(capture, "Directions to") ?: "Get directions"
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_FILE_WORDS) ->
            taskTranscriptSubjectTitle(capture, "Review") ?: "Review document"
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_LINK_WORDS) ->
            taskTranscriptSubjectTitle(capture, "Open") ?: "Open link"
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_APP_WORDS) -> taskTranscriptAppHint(normalized)
            ?.let { "Open ${it.label}" }
            ?: "Open app"
        else -> null
    }
    if (actionTitle != null) return actionTitle

    val cleaned = TASK_TRANSCRIPT_TITLE_NOISE_PATTERNS.fold(capture.trim()) { value, pattern ->
        pattern.replace(value, " ")
    }
        .replace(Regex("""\s+"""), " ")
        .trim(' ', ',', '.', '-', ':')
    return cleaned
        .takeIf { it.length >= 3 && !it.equals(capture.trim(), ignoreCase = true) }
        ?.replaceFirstChar { char -> char.titlecase() }
}

private fun taskTranscriptSubjectTitle(capture: String, action: String): String? {
    val subject = TASK_TRANSCRIPT_TITLE_NOISE_PATTERNS
        .fold(capture.trim()) { value, pattern -> pattern.replace(value, " ") }
        .replace(TASK_TRANSCRIPT_EMAIL_PATTERN, " ")
        .replace(TASK_TRANSCRIPT_PHONE_PATTERN, " ")
        .replace(TASK_TRANSCRIPT_URL_PATTERN, " ")
        .replace(
            Regex(
                """\b(call|phone|ring|dial|text|message|sms|email|reply|inbox|mail|invite|rsvp|directions?|map|location|address|navigate|open|launch|app|review|doc|document|file|pdf|link|website|webpage|url|to|for|at)\b""",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        .replace(Regex("""\s+"""), " ")
        .trim(' ', ',', '.', '-', ':')
    return subject
        .takeIf { it.length >= 2 }
        ?.replaceFirstChar { char -> char.titlecase() }
        ?.let { "$action $it" }
}

private val TASK_TRANSCRIPT_TIME_PATTERN = Regex(
    """\b(?:at\s+)?(\d{1,2}:\d{2}\s*(?:am|pm)?|\d{1,2}\s*(?:am|pm)|noon|midnight)\b""",
    RegexOption.IGNORE_CASE
)
private val DURATION_CONTEXT_PATTERN = Regex(
    """\b(15|30|45|60|90|120)\s*-?\s*(?:m|min|mins|minute|minutes)\b""",
    RegexOption.IGNORE_CASE
)
private val HOUR_DURATION_CONTEXT_PATTERN = Regex(
    """\b(an?|one|two|\d{1,2})\s*-?\s*(?:h|hr|hrs|hour|hours)(?:\s*(?:and\s*)?(\d{1,2})\s*-?\s*(?:m|min|mins|minute|minutes))?\b""",
    RegexOption.IGNORE_CASE
)
private val CASUAL_DURATION_CONTEXT_PATTERN = Regex(
    """\bhalf\s+an?\s+hour\b""",
    RegexOption.IGNORE_CASE
)

private val TASK_TRANSCRIPT_TITLE_NOISE_PATTERNS = listOf(
    Regex("""\b(urgent|asap|critical|important|low priority|optional)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(today|tomorrow|tonight|morning|afternoon|evening|at night)\b""", RegexOption.IGNORE_CASE),
    TASK_TRANSCRIPT_TIME_PATTERN,
    DURATION_CONTEXT_PATTERN,
    HOUR_DURATION_CONTEXT_PATTERN,
    CASUAL_DURATION_CONTEXT_PATTERN
)

private fun taskTranscriptPriority(normalized: String): Int? = when {
    Regex("""\b(urgent|asap|critical|important|deadline|due today|due tomorrow)\b""")
        .containsMatchIn(normalized) -> 2
    Regex("""\b(low priority|someday|whenever|optional)\b""").containsMatchIn(normalized) -> 0
    else -> null
}

private fun taskTranscriptDurationMinutes(normalized: String): Int? {
    taskDurationMentionMinutes(normalized)?.let { return it }
    return when {
        TASK_QUICK_DURATION_WORDS.any { it in normalized } -> 15
        TASK_LONG_DURATION_WORDS.any { it in normalized } -> 60
        else -> null
    }
}

private fun taskDurationMentionMinutes(normalized: String): Int? {
    if (CASUAL_DURATION_CONTEXT_PATTERN.containsMatchIn(normalized)) return 30
    HOUR_DURATION_CONTEXT_PATTERN.find(normalized)?.let { match ->
        val hours = taskDurationHourValue(match.groupValues.getOrNull(1).orEmpty()) ?: return@let
        val minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it in durationPresets }
    }
    return DURATION_CONTEXT_PATTERN.find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in durationPresets }
}

private fun taskDurationHourValue(value: String): Int? = when (value.lowercase()) {
    "a", "an", "one" -> 1
    "two" -> 2
    else -> value.toIntOrNull()
}

private fun taskTranscriptPreferredStartMinute(capture: String, normalized: String): Int? {
    TASK_TRANSCRIPT_TIME_PATTERN.find(capture)
        ?.groups
        ?.get(1)
        ?.value
        ?.let(::parseFlexibleMinute)
        ?.let { return it }
    return when {
        "morning" in normalized || "breakfast" in normalized -> 9 * 60
        "noon" in normalized || "midday" in normalized -> 12 * 60
        "lunch" in normalized -> 13 * 60
        "afternoon" in normalized -> 13 * 60
        "evening" in normalized || "dinner" in normalized || "tonight" in normalized -> 18 * 60
        else -> null
    }
}

private fun taskTranscriptScheduleDateOption(normalized: String): String? = when {
    "tomorrow" in normalized -> "Tomorrow"
    Regex("""\b(today|tonight)\b""").containsMatchIn(normalized) -> "Today"
    else -> null
}

private fun taskTranscriptActionDraft(capture: String, isPrimary: Boolean): TaskActionDraft? {
    val normalized = capture.lowercase()
    TASK_TRANSCRIPT_EMAIL_PATTERN.find(capture)?.value?.let { email ->
        return TaskActionDraft(
            id = UUID.randomUUID().toString(),
            type = TaskActionType.EMAIL,
            label = "Email",
            value = email,
            isPrimary = isPrimary
        )
    }
    TASK_TRANSCRIPT_PHONE_PATTERN.find(capture)?.value
        ?.takeIf { value -> value.count(Char::isDigit) >= 7 }
        ?.let { phone ->
            return TaskActionDraft(
                id = UUID.randomUUID().toString(),
                type = TaskActionType.PHONE,
                label = "Call",
                value = phone,
                isPrimary = isPrimary
            )
        }
    taskTranscriptAppHint(normalized)?.let { hint ->
        return TaskActionDraft(
            id = UUID.randomUUID().toString(),
            type = TaskActionType.APP,
            label = "Open ${hint.label}",
            value = hint.value,
            isPrimary = isPrimary
        )
    }
    TASK_TRANSCRIPT_URL_PATTERN.find(capture)?.value?.trimEnd('.', ',', ')')?.let { url ->
        val type = if (normalized.containsAnyTaskConnectWord(TASK_CONNECT_FILE_WORDS)) {
            TaskActionType.DOCUMENT
        } else {
            TaskActionType.WEBSITE
        }
        return TaskActionDraft(
            id = UUID.randomUUID().toString(),
            type = type,
            label = if (type == TaskActionType.DOCUMENT) "Open document" else "Open link",
            value = url,
            isPrimary = isPrimary
        )
    }
    taskTranscriptMapQuery(capture, normalized)?.let { query ->
        return TaskActionDraft(
            id = UUID.randomUUID().toString(),
            type = TaskActionType.MAP,
            label = "Open map",
            value = "geo:0,0?q=${Uri.encode(query)}",
            isPrimary = isPrimary
        )
    }
    return null
}

private fun taskTranscriptActionType(capture: String): TaskActionType? {
    val normalized = capture.lowercase()
    return when {
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_CALL_WORDS) -> TaskActionType.PHONE
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_EMAIL_WORDS) -> TaskActionType.EMAIL
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_MAP_WORDS) -> TaskActionType.MAP
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_FILE_WORDS) -> TaskActionType.DOCUMENT
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_LINK_WORDS) -> TaskActionType.WEBSITE
        normalized.containsAnyTaskConnectWord(TASK_CONNECT_APP_WORDS) -> TaskActionType.APP
        else -> null
    }
}

private val TASK_TRANSCRIPT_EMAIL_PATTERN =
    Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE)

private val TASK_TRANSCRIPT_PHONE_PATTERN =
    Regex("""(?<!\w)(?:\+?\d[\d\s().-]{6,}\d)(?!\w)""")

private val TASK_TRANSCRIPT_URL_PATTERN = Regex(
    """\b(?:https?://[^\s]+|www\.[^\s]+|[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?:/[^\s]*)?)\b"""
)

private data class TaskTranscriptAppHint(
    val label: String,
    val value: String,
    val terms: List<String>
)

private val TASK_TRANSCRIPT_APP_HINTS = listOf(
    TaskTranscriptAppHint("Spotify", "spotify://", listOf("spotify")),
    TaskTranscriptAppHint("YouTube", "youtube://", listOf("youtube", "you tube")),
    TaskTranscriptAppHint("Zoom", "zoomus://", listOf("zoom")),
    TaskTranscriptAppHint("Teams", "msteams://", listOf("teams", "microsoft teams")),
    TaskTranscriptAppHint("Slack", "slack://", listOf("slack")),
    TaskTranscriptAppHint("Notion", "notion://", listOf("notion")),
    TaskTranscriptAppHint("Gmail", "googlegmail://", listOf("gmail", "google mail")),
    TaskTranscriptAppHint("Google Calendar", "com.google.android.calendar", listOf("google calendar", "calendar")),
    TaskTranscriptAppHint("Google Drive", "com.google.android.apps.docs", listOf("google drive", "drive")),
    TaskTranscriptAppHint("Google Docs", "com.google.android.apps.docs.editors.docs", listOf("google docs", "docs")),
    TaskTranscriptAppHint("Google Sheets", "com.google.android.apps.docs.editors.sheets", listOf("google sheets", "sheets")),
    TaskTranscriptAppHint("Google Slides", "com.google.android.apps.docs.editors.slides", listOf("google slides", "slides")),
    TaskTranscriptAppHint("Google Maps", "com.google.android.apps.maps", listOf("google maps", "maps"))
)

private fun taskTranscriptAppHint(normalized: String): TaskTranscriptAppHint? {
    if (!Regex("""\b(open|launch|start|join|check)\b""").containsMatchIn(normalized)) {
        return null
    }
    return TASK_TRANSCRIPT_APP_HINTS.firstOrNull { hint ->
        hint.terms.any { term ->
            Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(normalized)
        }
    }
}

private fun taskTranscriptMapQuery(capture: String, normalized: String): String? {
    if (!normalized.containsAnyTaskConnectWord(TASK_CONNECT_MAP_WORDS)) return null
    val query = Regex("""\b(?:to|for|at)\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(capture)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    return query?.takeIf { it.length >= 3 && !it.contains("://") }
}

private fun taskModalActionLabels(isAddMode: Boolean): ChronosModalActionLabels {
    return ChronosModalActionLabels(
        confirm = if (isAddMode) "Add task" else "Save task",
        duplicate = "Duplicate task",
        archive = if (isAddMode) "Archive task" else "Delete task"
    )
}

private fun contextualTaskTitleSuggestions(
    title: String,
    description: String,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val normalized = "$title $description".lowercase()
    val suggestedTitles = suggestions.mapNotNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.Title)?.title?.trim()?.takeIf(String::isNotBlank)
    }
    val contextOptions = buildList {
        if ("call" in normalized || "phone" in normalized) add("Call back")
        if (Regex("""\b(calendar invite|meeting invite|send invite|send calendar invite|send meeting invite|event invite|save the date)\b""")
                .containsMatchIn(normalized)
        ) {
            add("Send calendar invite")
        }
        if ("rsvp" in normalized) add("RSVP to invite")
        if (Regex("""\b(send|share|forward|attach|review)\s+(document|doc|pdf|file|attachment|proposal|brief)\b""")
                .containsMatchIn(normalized)
        ) {
            add("Send document")
        }
        if (Regex("""\b(send|share|forward|open|review)\s+(link|website|url|webpage|page)\b""").containsMatchIn(normalized)) {
            add("Send link")
        }
        if (Regex("""\b(text|message|sms|dm|ping|ask|tell|send text|send message|send sms|send dm)\b""").containsMatchIn(normalized)) add("Send message")
        if ("email" in normalized || "reply" in normalized) add("Email follow-up")
        if ("meeting" in normalized || "standup" in normalized) add("Prep meeting")
        if ("appointment" in normalized) add("Prepare appointment")
        if ("review" in normalized || "notes" in normalized) add("Review notes")
        if ("bill" in normalized || "invoice" in normalized) add("Pay bill")
        if ("grocery" in normalized || "groceries" in normalized) add("Buy groceries")
        if ("buy" in normalized || "shop" in normalized || "order" in normalized) add("Buy needed items")
        if ("return" in normalized && ("package" in normalized || "order" in normalized)) add("Return package")
        if ("ship" in normalized || "package" in normalized) add("Ship package")
    }
    return (suggestedTitles + contextOptions + taskTitleSuggestions)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .take(6)
}

private fun appendTaskTranscript(existing: String, transcript: String): String {
    val cleanTranscript = transcript.trim()
    if (cleanTranscript.isBlank()) return existing
    return listOf(existing.trim(), cleanTranscript)
        .filter(String::isNotBlank)
        .joinToString("\n")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskConnectQuickChips(
    contextText: String,
    suggestions: List<TaskAssistSuggestion>,
    onContact: () -> Unit,
    onPhoto: () -> Unit,
    onFile: () -> Unit,
    onLink: () -> Unit,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    onMap: () -> Unit,
    onApp: () -> Unit
) {
    val suggestedActionTypes = suggestions.mapNotNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.ActionDraft)?.payload?.type
    }.toSet()
    val normalizedContext = contextText.lowercase()
    val chips = listOf(
        TaskConnectQuickChip(
            "Contact",
            onContact,
            taskConnectContextRank(TaskConnectQuickKind.CONTACT, suggestedActionTypes, normalizedContext)
        ),
        TaskConnectQuickChip(
            "Photo",
            onPhoto,
            taskConnectContextRank(TaskConnectQuickKind.PHOTO, suggestedActionTypes, normalizedContext)
        ),
        TaskConnectQuickChip(
            "File",
            onFile,
            taskConnectContextRank(TaskConnectQuickKind.FILE, suggestedActionTypes, normalizedContext)
        ),
        TaskConnectQuickChip(
            "Link",
            onLink,
            taskConnectContextRank(TaskConnectQuickKind.LINK, suggestedActionTypes, normalizedContext)
        ),
        TaskConnectQuickChip(
            "Call",
            onCall,
            taskConnectContextRank(TaskConnectQuickKind.CALL, suggestedActionTypes, normalizedContext)
        ),
        TaskConnectQuickChip(
            "Email",
            onEmail,
            taskConnectContextRank(TaskConnectQuickKind.EMAIL, suggestedActionTypes, normalizedContext)
        ),
        TaskConnectQuickChip(
            "Map",
            onMap,
            taskConnectContextRank(TaskConnectQuickKind.MAP, suggestedActionTypes, normalizedContext)
        ),
        TaskConnectQuickChip(
            "App",
            onApp,
            taskConnectContextRank(TaskConnectQuickKind.APP, suggestedActionTypes, normalizedContext)
        )
    ).sortedWith(compareBy<TaskConnectQuickChip>({ it.rank }, { it.label }))
    val visibleChips = taskConnectVisibleQuickChips(chips)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleChips.forEach { chip ->
            FilterChip(
                selected = chip.rank <= TASK_CONNECT_SUGGESTED_RANK,
                onClick = chip.action,
                label = { Text(chip.displayLabel) }
            )
        }
    }
}

private const val TASK_CONNECT_FALLBACK_VISIBLE_COUNT = 4
private const val TASK_CONNECT_CONTEXTUAL_VISIBLE_COUNT = 5

private fun taskConnectVisibleQuickChips(chips: List<TaskConnectQuickChip>): List<TaskConnectQuickChip> {
    val suggested = chips.filter { it.rank <= TASK_CONNECT_SUGGESTED_RANK }
    if (suggested.isEmpty()) return chips.take(TASK_CONNECT_FALLBACK_VISIBLE_COUNT)
    val fallbackCount = (TASK_CONNECT_CONTEXTUAL_VISIBLE_COUNT - suggested.size).coerceAtLeast(1)
    val fallbacks = chips.filterNot { it.rank <= TASK_CONNECT_SUGGESTED_RANK }.take(fallbackCount)
    return (suggested + fallbacks)
        .distinctBy { it.label }
        .take(TASK_CONNECT_CONTEXTUAL_VISIBLE_COUNT)
}

private fun taskConnectSectionSubtitle(
    contextText: String,
    suggestions: List<TaskAssistSuggestion>
): String {
    val suggestedActionTypes = suggestions.mapNotNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.ActionDraft)?.payload?.type
    }.toSet()
    val normalizedContext = contextText.lowercase()
    return when {
        TaskConnectQuickKind.CALL.matchesSuggestedTaskAction(suggestedActionTypes) ||
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_CALL_WORDS) ->
            "This looks like a call or message task. Add the contact or phone action first."
        TaskConnectQuickKind.EMAIL.matchesSuggestedTaskAction(suggestedActionTypes) ||
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_EMAIL_WORDS) ->
            "This looks email-driven. Add the email action or linked contact first."
        TaskConnectQuickKind.PHOTO.matchesSuggestedTaskAction(suggestedActionTypes) ||
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_PHOTO_WORDS) ->
            "This task mentions an image or receipt. Attach the photo while the context is fresh."
        TaskConnectQuickKind.FILE.matchesSuggestedTaskAction(suggestedActionTypes) ||
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_FILE_WORDS) ->
            "This task points to a file or document. Attach it now so the task is ready."
        TaskConnectQuickKind.LINK.matchesSuggestedTaskAction(suggestedActionTypes) ||
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_LINK_WORDS) ->
            "This task has link context. Add the website action before saving."
        TaskConnectQuickKind.MAP.matchesSuggestedTaskAction(suggestedActionTypes) ||
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_MAP_WORDS) ->
            "This task has a place or route. Add map context before saving."
        TaskConnectQuickKind.APP.matchesSuggestedTaskAction(suggestedActionTypes) ||
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_APP_WORDS) ->
            "This task opens an app or deep link. Add the launch target before saving."
        else -> "Add people, photos, files, links, calls, emails, or maps only when the task needs them."
    }
}

private data class TaskConnectQuickChip(
    val label: String,
    val action: () -> Unit,
    val rank: Int
) {
    val displayLabel: String
        get() = if (rank <= TASK_CONNECT_SUGGESTED_RANK) "Suggested: $label" else label
}

private enum class TaskConnectQuickKind {
    CONTACT,
    PHOTO,
    FILE,
    LINK,
    CALL,
    EMAIL,
    MAP,
    APP
}

private fun taskConnectContextRank(
    kind: TaskConnectQuickKind,
    suggestedTypes: Set<TaskActionType>,
    normalizedContext: String
): Int {
    if (kind.matchesSuggestedTaskAction(suggestedTypes)) return 0
    return when (kind) {
        TaskConnectQuickKind.CALL -> if (normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_CALL_WORDS)) 1 else 20
        TaskConnectQuickKind.CONTACT -> when {
            suggestedTypes.any { it == TaskActionType.PHONE || it == TaskActionType.EMAIL } -> 1
            normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_CONTACT_WORDS) -> 1
            else -> 21
        }
        TaskConnectQuickKind.EMAIL -> if (normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_EMAIL_WORDS)) 1 else 22
        TaskConnectQuickKind.PHOTO -> if (normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_PHOTO_WORDS)) 1 else 23
        TaskConnectQuickKind.LINK -> if (normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_LINK_WORDS)) 1 else 24
        TaskConnectQuickKind.FILE -> if (normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_FILE_WORDS)) 1 else 25
        TaskConnectQuickKind.MAP -> if (normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_MAP_WORDS)) 1 else 26
        TaskConnectQuickKind.APP -> if (normalizedContext.containsAnyTaskConnectWord(TASK_CONNECT_APP_WORDS)) 1 else 27
    }
}

private fun TaskConnectQuickKind.matchesSuggestedTaskAction(suggestedTypes: Set<TaskActionType>): Boolean = when (this) {
    TaskConnectQuickKind.FILE -> TaskActionType.DOCUMENT in suggestedTypes
    TaskConnectQuickKind.LINK -> TaskActionType.WEBSITE in suggestedTypes
    TaskConnectQuickKind.CALL -> TaskActionType.PHONE in suggestedTypes
    TaskConnectQuickKind.EMAIL -> TaskActionType.EMAIL in suggestedTypes
    TaskConnectQuickKind.MAP -> TaskActionType.MAP in suggestedTypes
    TaskConnectQuickKind.APP -> TaskActionType.APP in suggestedTypes || TaskActionType.CUSTOM_DEEP_LINK in suggestedTypes
    TaskConnectQuickKind.CONTACT,
    TaskConnectQuickKind.PHOTO -> false
}

private fun String.containsAnyTaskConnectWord(words: List<String>): Boolean = words.any { word ->
    if (word.any { !it.isLetterOrDigit() }) {
        word in this
    } else {
        Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(this)
    }
}

private const val TASK_CONNECT_SUGGESTED_RANK = 1
private const val AUTO_ASSIST_DEBOUNCE_MS = 650L
internal fun shouldAutoRequestAssistForCapture(capture: String): Boolean {
    val normalized = capture.trim().lowercase()
    if (normalized.length < 6) return false
    if (normalized.split(Regex("\\s+")).size >= 3) return true
    return TASK_CONTEXT_WORDS.any { it in normalized } ||
        TASK_SCHEDULE_WORDS.any { it in normalized } ||
        TASK_CHECKLIST_WORDS.any { it in normalized } ||
        TASK_PRIORITY_WORDS.any { it in normalized }
}

private val TASK_CONTEXT_WORDS = listOf(
    "call",
    "phone",
    "email",
    "reply",
    "link",
    "http",
    "www.",
    "file",
    "doc",
    "pdf",
    "map",
    "address",
    "app",
    "open",
    "text",
    "message",
    "sms",
    "dm",
    "ping",
    "ask",
    "tell",
    "send message",
    "send text",
    "send invite",
    "calendar invite",
    "meeting invite",
    "rsvp"
)
private val TASK_CONNECT_CALL_WORDS = listOf(
    "call",
    "phone",
    "ring",
    "dial",
    "text",
    "message",
    "sms",
    "dm",
    "ping",
    "ask",
    "tell",
    "send text",
    "send message",
    "send sms",
    "send dm"
)
private val TASK_CONNECT_CONTACT_WORDS = listOf(
    "contact",
    "person",
    "mom",
    "dad",
    "mother",
    "father",
    "doctor",
    "dentist",
    "teacher",
    "client",
    "boss",
    "friend",
    "partner",
    "wife",
    "husband",
    "sister",
    "brother",
    "parent"
)
private val TASK_CONNECT_EMAIL_WORDS = listOf("email", "reply", "inbox", "mail", "invite", "calendar invite", "meeting invite", "rsvp")
private val TASK_CONNECT_PHOTO_WORDS = listOf("photo", "picture", "image", "screenshot", "receipt", "scan")
private val TASK_CONNECT_LINK_WORDS = listOf("link", "http", "www.", "url", "website", "webpage")
private val TASK_CONNECT_FILE_WORDS = listOf("file", "doc", "document", "pdf", "attachment", "report", "brief")
private val TASK_CONNECT_MAP_WORDS = listOf(
    "map",
    "location",
    "address",
    "directions",
    "drive",
    "route",
    "pickup",
    "pick up",
    "drop off",
    "return",
    "bring",
    "deliver",
    "visit"
)
private val TASK_CONNECT_APP_WORDS = listOf("app", "open", "launch")
private val TASK_SCHEDULE_WORDS = listOf(
    "today",
    "tomorrow",
    "tonight",
    "morning",
    "afternoon",
    "evening",
    "deadline",
    "due",
    "before",
    "after",
    "minutes",
    "hour"
)
private val TASK_CHECKLIST_WORDS = listOf("steps", "checklist", "first", "then", "prepare", "review", "plan")
private val TASK_PRIORITY_WORDS = listOf(
    "urgent",
    "critical",
    "asap",
    "deadline",
    "important",
    "high priority",
    "due now",
    "right now",
    "now"
)
private val TASK_QUICK_DURATION_WORDS = listOf("call", "text", "reply", "quick", "pay", "pick up", "drop off")
private val TASK_LONG_DURATION_WORDS = listOf("deep work", "write", "draft", "review", "prepare", "plan", "research", "study")

// Title and Priority suggestions replace a single field value, so applying one makes the
// remaining same-kind alternatives stale. Schedule suggestions can each fill different
// fields and Checklist/ActionDraft are additive, so those siblings stay offered.
internal fun supersededTaskAssistSuggestionIds(
    applied: TaskAssistSuggestion,
    suggestions: List<TaskAssistSuggestion>
): Set<String> {
    val replacesSameKind = applied is TaskAssistSuggestion.Title || applied is TaskAssistSuggestion.Priority
    if (!replacesSameKind) return setOf(applied.id)
    return suggestions
        .filter { it::class == applied::class }
        .map { it.id }
        .toSet() + applied.id
}

// With the auto-apply setting on, a fresh Add form fills itself from the first
// suggestion of each kind — but only into fields the user has not set, so manual
// input always wins. Action drafts stay manual; they link external targets.
internal fun autoApplicableTaskAssistSuggestionIds(
    suggestions: List<TaskAssistSuggestion>,
    titleBlank: Boolean,
    priorityUnset: Boolean,
    scheduleUnset: Boolean,
    checklistEmpty: Boolean
): Set<String> {
    var titleOpen = titleBlank
    var priorityOpen = priorityUnset
    var scheduleOpen = scheduleUnset
    var checklistOpen = checklistEmpty
    val ids = mutableSetOf<String>()
    suggestions.forEach { suggestion ->
        when (suggestion) {
            is TaskAssistSuggestion.Title -> if (titleOpen) {
                ids += suggestion.id
                titleOpen = false
            }
            is TaskAssistSuggestion.Priority -> if (priorityOpen) {
                ids += suggestion.id
                priorityOpen = false
            }
            is TaskAssistSuggestion.Schedule -> if (scheduleOpen) {
                ids += suggestion.id
                scheduleOpen = false
            }
            is TaskAssistSuggestion.Checklist -> if (checklistOpen) {
                ids += suggestion.id
                checklistOpen = false
            }
            is TaskAssistSuggestion.ActionDraft -> Unit
        }
    }
    return ids
}

internal fun taskActionRedundancyKey(type: TaskActionType, value: String): String =
    "${type.name}:${value.trim().lowercase()}"

// A suggestion the form already satisfies is a no-op pill — hide it so the row only
// offers changes. Keeps pills honest after a sibling is applied or a field is edited
// by hand, without an extra Gemini round-trip.
internal fun redundantTaskAssistSuggestionIds(
    suggestions: List<TaskAssistSuggestion>,
    currentTitle: String,
    currentPriority: Int,
    currentTargetDate: LocalDate?,
    currentDurationMinutes: Int?,
    currentStartMinuteOfDay: Int?,
    currentChecklistLabels: List<String>,
    currentActionKeys: Set<String>
): Set<String> {
    fun String.normalized() = trim().lowercase()
    val knownChecklistLabels = currentChecklistLabels
        .map { it.normalized() }
        .filter { it.isNotBlank() }
        .toSet()
    return suggestions.filter { suggestion ->
        when (suggestion) {
            is TaskAssistSuggestion.Title ->
                suggestion.title.normalized() == currentTitle.normalized()
            is TaskAssistSuggestion.Priority ->
                suggestion.priority.coerceIn(0, 2) == currentPriority
            is TaskAssistSuggestion.Schedule -> with(suggestion.payload) {
                (targetDate == null || targetDate == currentTargetDate) &&
                    (preferredDurationMinutes == null || preferredDurationMinutes == currentDurationMinutes) &&
                    (preferredStartMinuteOfDay == null || preferredStartMinuteOfDay == currentStartMinuteOfDay)
            }
            is TaskAssistSuggestion.Checklist -> suggestion.items
                .map { it.normalized() }
                .filter { it.isNotBlank() }
                .all { it in knownChecklistLabels }
            is TaskAssistSuggestion.ActionDraft -> {
                val value = suggestion.payload.value.normalized()
                value.isNotBlank() && taskActionRedundancyKey(suggestion.payload.type, value) in currentActionKeys
            }
        }
    }.map { it.id }.toSet()
}

private fun buildAndroid17ContactPickerIntent(): Intent {
    val requestedFields = arrayListOf(
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
    )
    return Intent(ACTION_PICK_CONTACTS).apply {
        putStringArrayListExtra(EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, requestedFields)
        putExtra(EXTRA_PICK_CONTACTS_MATCH_ALL_DATA_FIELDS, false)
    }
}

internal fun taskAlarmId(taskId: String): String = "task:$taskId"

internal fun resolveTaskDueInstant(preset: String, customMinute: Int?): Instant? {
    val relativeMinutes = urgentReminderPresets[preset]
    if (relativeMinutes != null && relativeMinutes < 0) {
        return Instant.now().plusSeconds(-relativeMinutes * 60L)
    }
    if (relativeMinutes != null && relativeMinutes >= 0) {
        return minuteOfDayToInstant(relativeMinutes)
    }
    return customMinute?.let { minuteOfDayToInstant(it) }
}

private fun defaultUrgentReminderMinute(): Int {
    val now = LocalDateTime.now()
    val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
    return nextHour.hour * 60 + nextHour.minute
}

private fun minuteOfDayToInstant(minuteOfDay: Int): Instant {
    val now = LocalDate.now()
    val scheduledLocal = now.atStartOfDay().plusMinutes(minuteOfDay.toLong())
    val scheduledFor = scheduledLocal.atZone(ZoneId.systemDefault()).toInstant()
    return if (scheduledFor.isAfter(Instant.now())) {
        scheduledFor
    } else {
        scheduledFor.plusSeconds(24 * 60 * 60)
    }
}

private fun minuteFromInstant(instant: Instant): Int? {
    val local = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    return local.hour * 60 + local.minute
}

internal fun resolveScheduleDateOption(targetDate: LocalDate?, today: LocalDate = LocalDate.now()): String = when (targetDate) {
    null -> "Any day"
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> "Custom"
}

private fun resolveScheduleTimeOption(preferredStartMinuteOfDay: Int?): String = when (preferredStartMinuteOfDay) {
    null -> anyPreferredStartOption
    9 * 60 -> taskPreferredStartPickerLabel(9 * 60)
    12 * 60 -> taskPreferredStartPickerLabel(12 * 60)
    13 * 60 -> taskPreferredStartPickerLabel(13 * 60)
    18 * 60 -> taskPreferredStartPickerLabel(18 * 60)
    else -> customPreferredStartOption
}

internal fun taskPreferredStartPickerOptions(): List<String> {
    return listOf(anyPreferredStartOption) +
        preferredStartPresets.map { (minute, label) -> "$label ${formatDisplayMinute(minute)}" } +
        customPreferredStartOption
}

internal fun contextualTaskScheduleDateOptions(
    title: String,
    description: String,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val normalized = "$title $description".lowercase()
    val suggestedOptions = suggestions.mapNotNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.Schedule)
            ?.payload
            ?.targetDate
            ?.let { date -> resolveScheduleDateOption(date) }
            ?.takeIf { it in scheduleDateOptions }
    }
    val actionDrivenOptions = buildList {
        val actionTypes = suggestions.mapNotNull { suggestion ->
            (suggestion as? TaskAssistSuggestion.ActionDraft)?.payload?.type
        }
        if (actionTypes.any { it == TaskActionType.PHONE || it == TaskActionType.EMAIL }) {
            add("Today")
        }
    }
    val contextOptions = buildList {
        if ("today" in normalized || "tonight" in normalized) add("Today")
        if ("tomorrow" in normalized) add("Tomorrow")
    }

    val contextualOptions = suggestedOptions + actionDrivenOptions + contextOptions
    val optionLimit = if (contextualOptions.isNotEmpty()) 3 else scheduleDateOptions.size
    return (contextualOptions + scheduleDateOptions)
        .distinct()
        .take(optionLimit)
}

internal fun contextualTaskPreferredStartPickerOptions(
    title: String,
    description: String,
    suggestions: List<TaskAssistSuggestion>
): List<String> {
    val baseOptions = taskPreferredStartPickerOptions()
    val normalized = "$title $description".lowercase()
    val suggestedOptions = suggestions.mapNotNull { suggestion ->
        (suggestion as? TaskAssistSuggestion.Schedule)
            ?.payload
            ?.preferredStartMinuteOfDay
            ?.let(::taskPreferredStartPickerLabel)
            ?.takeIf { it in baseOptions }
    }
    val actionDrivenOptions = buildList {
        val actionTypes = suggestions.mapNotNull { suggestion ->
            (suggestion as? TaskAssistSuggestion.ActionDraft)?.payload?.type
        }
        if (TaskActionType.PHONE in actionTypes) {
            add(taskPreferredStartPickerLabel(10 * 60))
            add(taskPreferredStartPickerLabel(14 * 60))
            add(taskPreferredStartPickerLabel(17 * 60))
        }
        if (TaskActionType.EMAIL in actionTypes) {
            add(taskPreferredStartPickerLabel(9 * 60))
            add(taskPreferredStartPickerLabel(13 * 60))
            add(taskPreferredStartPickerLabel(16 * 60))
        }
    }
    val contextOptions = buildList {
        taskContextPreferredStartMinute(normalized)?.let { add(taskPreferredStartPickerLabel(it)) }
        if ("morning" in normalized || "breakfast" in normalized) add(taskPreferredStartPickerLabel(9 * 60))
        if ("noon" in normalized || "midday" in normalized) add(taskPreferredStartPickerLabel(12 * 60))
        if ("afternoon" in normalized || "lunch" in normalized) {
            add(taskPreferredStartPickerLabel(13 * 60))
        }
        if ("evening" in normalized || "dinner" in normalized || "tonight" in normalized) {
            add(taskPreferredStartPickerLabel(18 * 60))
        }
    }

    val contextualOptions = suggestedOptions + actionDrivenOptions + contextOptions
    val optionLimit = if (contextualOptions.isNotEmpty()) 5 else baseOptions.size
    return (contextualOptions + baseOptions)
        .distinct()
        .take(optionLimit)
}

private fun taskContextPreferredStartMinute(context: String): Int? {
    return TASK_TRANSCRIPT_TIME_PATTERN.find(context)
        ?.groups
        ?.get(1)
        ?.value
        ?.let(::parseFlexibleMinute)
}

internal fun taskPreferredStartPickerLabel(minuteOfDay: Int?): String {
    if (minuteOfDay == null) return anyPreferredStartOption
    val label = preferredStartPresets.firstOrNull { it.first == minuteOfDay }?.second
    return label?.let { "$it ${formatDisplayMinute(minuteOfDay)}" }
        ?: formatDisplayMinute(minuteOfDay)
}

internal fun taskPreferredStartPickerMinutes(label: String): Int? {
    if (label == anyPreferredStartOption || label == customPreferredStartOption) return null
    return preferredStartPresets.firstOrNull { (minute, presetLabel) ->
        label == "$presetLabel ${formatDisplayMinute(minute)}"
    }?.first ?: parseFlexibleMinute(label)
}

internal fun taskUrgentReminderPickerOptions(): List<String> = urgentReminderPresets.keys.toList()

internal fun taskUrgentReminderPickerMinute(label: String): Int? = urgentReminderPresets[label]

internal fun resolveUrgentReminderPreset(minuteOfDay: Int): String =
    urgentReminderPresets.entries.firstOrNull { it.value == minuteOfDay }?.key ?: "Custom"

internal fun taskRecurringReminderTriggerPickerOptions(): List<String> {
    return listOf(
        taskRecurringReminderTriggerLabel(TaskReminderTrigger.AT_TIME),
        taskRecurringReminderTriggerLabel(TaskReminderTrigger.BEFORE_OCCURRENCE)
    )
}

internal fun taskRecurringReminderTriggerLabel(trigger: TaskReminderTrigger): String = when (trigger) {
    TaskReminderTrigger.AT_TIME -> "At time"
    TaskReminderTrigger.BEFORE_OCCURRENCE -> "Before occurrence"
}

internal fun taskRecurringReminderTriggerFromLabel(label: String): TaskReminderTrigger = when (label) {
    taskRecurringReminderTriggerLabel(TaskReminderTrigger.AT_TIME) -> TaskReminderTrigger.AT_TIME
    taskRecurringReminderTriggerLabel(TaskReminderTrigger.BEFORE_OCCURRENCE) -> TaskReminderTrigger.BEFORE_OCCURRENCE
    else -> TaskReminderTrigger.valueOf(label)
}

internal fun taskRecurringWeekdayPickerLabel(day: DayOfWeek): String =
    day.name.take(3).lowercase().replaceFirstChar(Char::titlecase)

internal fun taskRecurringIntervalUnitLabel(cadence: TaskRecurringCadence, interval: Int): String {
    val singular = when (cadence) {
        TaskRecurringCadence.DAILY -> "day"
        TaskRecurringCadence.WEEKLY -> "week"
        TaskRecurringCadence.MONTHLY_DAY_OF_MONTH,
        TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY -> "month"
    }
    return if (interval == 1) singular else "${singular}s"
}

@Composable
private fun taskAlarmStateColor(state: TaskAlarmUiState): Color = when (state.status) {
    TaskAlarmStatus.EXACT -> MaterialTheme.colorScheme.primary
    TaskAlarmStatus.DEGRADED_WINDOW -> MaterialTheme.colorScheme.tertiary
    TaskAlarmStatus.NOTIFICATION_PERMISSION_REQUIRED,
    TaskAlarmStatus.EXACT_PERMISSION_REQUIRED -> MaterialTheme.colorScheme.error
}

private fun taskAlarmStateMessage(state: TaskAlarmUiState): String = when (state.status) {
    TaskAlarmStatus.EXACT ->
        state.scheduledFor?.let { "Exact alarm armed for ${formatTaskDueInstant(it)}" }
            ?: "Exact alarm armed"
    TaskAlarmStatus.DEGRADED_WINDOW ->
        state.scheduledFor?.let { "Fallback alarm window starts at ${formatTaskDueInstant(it)}" }
            ?: "Alarm uses the fallback window"
    TaskAlarmStatus.NOTIFICATION_PERMISSION_REQUIRED ->
        "Notification permission is required before this alarm can fire."
    TaskAlarmStatus.EXACT_PERMISSION_REQUIRED ->
        "Exact alarm access is required to protect this urgent task."
}

internal fun formatTaskDueInstant(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = LocalDate.now()
    val dayLabel = when (local.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> local.toLocalDate().toString()
    }
    return "$dayLabel at ${formatDisplayMinute(local.hour * 60 + local.minute)}"
}

private fun formatTaskTargetDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    else -> formatChronosPickerDate(date)
}

private fun taskActionTypeLabel(type: TaskActionType): String = when (type) {
    TaskActionType.WEBSITE -> "Website"
    TaskActionType.DOCUMENT -> "Document"
    TaskActionType.PHONE -> "Phone"
    TaskActionType.EMAIL -> "Email"
    TaskActionType.MAP -> "Map"
    TaskActionType.APP -> "App"
    TaskActionType.CUSTOM_DEEP_LINK -> "Deep link"
}

internal fun taskActionLabelPlaceholder(type: TaskActionType): String = when (type) {
    TaskActionType.WEBSITE -> "Client website"
    TaskActionType.DOCUMENT -> "Project brief"
    TaskActionType.PHONE -> "Call Alex"
    TaskActionType.EMAIL -> "Email Alex"
    TaskActionType.MAP -> "Office address"
    TaskActionType.APP -> "Open journal app"
    TaskActionType.CUSTOM_DEEP_LINK -> "Open order details"
}

private fun taskActionValuePlaceholder(type: TaskActionType): String = when (type) {
    TaskActionType.WEBSITE -> "example.com"
    TaskActionType.DOCUMENT -> "https://docs.example.com/brief"
    TaskActionType.PHONE -> "+1 555 123 4567"
    TaskActionType.EMAIL -> "alex@example.com"
    TaskActionType.MAP -> "geo:0,0?q=Coffee+shop"
    TaskActionType.APP -> "com.example.journal, component:com.example/.MainActivity, or journal://new"
    TaskActionType.CUSTOM_DEEP_LINK -> "myapp://details/123"
}

private fun TaskAttachmentDraft.toPreviewAttachment(): TaskAttachment? {
    val previewReference = importedPath ?: sourceUri ?: return null
    val previewMode = if (importedPath != null) {
        TaskAttachmentStorageMode.IMPORTED
    } else {
        TaskAttachmentStorageMode.LINKED
    }
    return TaskAttachment(
        id = id,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        kind = kind,
        storageMode = previewMode,
        reference = previewReference,
        persistedUriPermission = persistedUriPermission,
        isFeaturedImage = isFeaturedImage
    )
}

private fun formatAttachmentSize(sizeBytes: Long): String {
    return when {
        sizeBytes >= 1_048_576 -> String.format("%.1f MB", sizeBytes / 1_048_576f)
        sizeBytes >= 1_024 -> String.format("%.1f KB", sizeBytes / 1_024f)
        else -> "$sizeBytes B"
    }
}
