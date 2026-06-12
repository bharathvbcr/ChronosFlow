package com.chronosflow.feature.tasks

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.ai.TaskAssistRequest
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.notifications.TaskContextCommand
import com.chronosflow.core.notifications.TaskContextCommandKind
import com.chronosflow.core.notifications.TaskContextCommandResolver
import com.chronosflow.core.notifications.TaskContextCommandSet
import com.chronosflow.core.notifications.TaskContextCommandTarget
import com.chronosflow.core.notifications.TaskContextInternalAction
import com.chronosflow.core.notifications.launchTaskContextCommand
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosLinkOption
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosMetricTile
import com.chronosflow.core.ui.shell.ChronosModalBottomSheet
import com.chronosflow.core.ui.components.ChronosCommandPaletteAction
import com.chronosflow.core.ui.components.ChronosPageHeader
import com.chronosflow.core.ui.components.ChronosScreenScaffold
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.shell.ChronosSnackbarHost
import com.chronosflow.core.ui.shell.LocalChronosShellBottomInset
import com.chronosflow.core.ui.theme.ChronosSpacing
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class TaskFilter(val label: String) {
    OPEN("Open"),
    ALL("All"),
    DONE("Done")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenDayDial: (() -> Unit)? = null,
    onOpenCommandPalette: (() -> Unit)? = null,
    initialContextTaskId: String? = null,
    openInitialContextSheet: Boolean = false,
    openAddSheet: Boolean = false,
    initialAddCapture: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val goalOptions = remember(goals) { goals.map { ChronosLinkOption(it.id, it.title) } }
    val taskSchedulesByTaskId by viewModel.taskSchedulesByTaskId.collectAsStateWithLifecycle()
    val scheduleStatus by viewModel.scheduleStatus.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val showExactAlarmPermissionAction by viewModel.showExactAlarmPermissionAction.collectAsStateWithLifecycle()
    val showNotificationPermissionAction by viewModel.showNotificationPermissionAction.collectAsStateWithLifecycle()
    val notificationPermissionGranted by viewModel.notificationPermissionGranted.collectAsStateWithLifecycle()
    val exactAlarmPermissionGranted by viewModel.exactAlarmPermissionGranted.collectAsStateWithLifecycle()
    val alarmStates by viewModel.urgentTaskAlarmStates.collectAsStateWithLifecycle()
    val assistState by viewModel.assistState.collectAsStateWithLifecycle()
    val rewriteState by viewModel.rewriteState.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<TaskSheetTarget?>(null) }
    var commandSheetTask by remember { mutableStateOf<Task?>(null) }
    var initialContextConsumed by rememberSaveable(initialContextTaskId, openInitialContextSheet) {
        mutableStateOf(false)
    }
    val normalizedInitialAddCapture = initialAddCapture?.trim()?.takeIf(String::isNotBlank)
    var initialAddConsumed by rememberSaveable(openAddSheet, normalizedInitialAddCapture) {
        mutableStateOf(false)
    }
    var filter by rememberSaveable { mutableStateOf(TaskFilter.OPEN) }
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshAlarmCapabilities()
        viewModel.dismissNotificationPermissionAction()
    }
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(notificationPermissionsForSdk())
        } else {
            viewModel.refreshAlarmCapabilities()
        }
    }
    val visibleTasks = remember(tasks, filter) {
        when (filter) {
            TaskFilter.OPEN -> tasks.filterNot { it.isCompleted }
            TaskFilter.ALL -> tasks
            TaskFilter.DONE -> tasks.filter { it.isCompleted }
        }.sortedWith(compareByDescending<Task> { it.priority }.thenBy { it.createdAt })
    }
    val openCount = tasks.count { !it.isCompleted }
    val doneCount = tasks.size - openCount
    val urgentCount = tasks.count { !it.isCompleted && it.priority >= 2 }
    val assistantSummary = remember(tasks, taskSchedulesByTaskId) {
        buildTaskAssistantSummary(tasks, taskSchedulesByTaskId)
    }
    val shellBottomInset = LocalChronosShellBottomInset.current

    LaunchedEffect(status) {
        val message = status ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatus()
    }

    LaunchedEffect(scheduleStatus) {
        val message = scheduleStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearScheduleStatus()
    }

    LaunchedEffect(tasks, initialContextTaskId, openInitialContextSheet, initialContextConsumed) {
        if (!openInitialContextSheet || initialContextConsumed || initialContextTaskId == null) return@LaunchedEffect
        tasks.firstOrNull { it.id == initialContextTaskId }?.let { task ->
            commandSheetTask = task
            initialContextConsumed = true
        }
    }

    LaunchedEffect(openAddSheet, normalizedInitialAddCapture, initialAddConsumed) {
        if (!openAddSheet || initialAddConsumed) return@LaunchedEffect
        sheetTarget = TaskSheetTarget.Add(prefillTitle = normalizedInitialAddCapture)
        normalizedInitialAddCapture?.let { capture ->
            viewModel.requestTaskAssist(TaskAssistRequest(title = capture))
        }
        initialAddConsumed = true
    }

    fun openTaskContext(task: Task) {
        val commandSet = taskExternalCommandSet(task)
        val obviousCommand = commandSet.obviousExternalCommand
        if (obviousCommand != null && taskShouldLaunchObviousExternalCommandDirectly(commandSet)) {
            if (!launchTaskContextCommand(context, obviousCommand)) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("No app can open ${obviousCommand.label}")
                }
            }
        } else {
            commandSheetTask = task
        }
    }

    fun handleTaskContextCommand(task: Task, command: TaskContextCommand) {
        when (val target = command.target) {
            is TaskContextCommandTarget.Internal -> when (target.action) {
                TaskContextInternalAction.SCHEDULE -> viewModel.scheduleTaskToday(task.id)
                TaskContextInternalAction.EDIT -> sheetTarget = TaskSheetTarget.Edit(task)
                TaskContextInternalAction.COMPLETE -> viewModel.toggleTask(task.id)
            }
            else -> {
                if (!launchTaskContextCommand(context, command)) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("No app can open ${command.label}")
                    }
                }
            }
        }
        commandSheetTask = null
    }

    ChronosScreenScaffold(
        title = "Tasks",
        onBack = onBack,
        actions = { ChronosCommandPaletteAction(onOpenCommandPalette) },
        snackbarHost = { ChronosSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + shellBottomInset + ChronosSpacing.Medium
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ChronosPageHeader(
                    title = "Task command center",
                    subtitle = "Capture commitments, schedule priority work, and arm urgent tasks with exact alarms.",
                    icon = Icons.Default.Checklist
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChronosMetricTile(label = "Open", value = openCount.toString(), modifier = Modifier.weight(1f))
                    ChronosMetricTile(
                        label = "Urgent",
                        value = urgentCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.error
                    )
                    ChronosMetricTile(
                        label = "Done",
                        value = doneCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = { sheetTarget = TaskSheetTarget.Add() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add task", fontWeight = FontWeight.SemiBold)
                }
            }

            if (showNotificationPermissionAction || (!notificationPermissionGranted && urgentCount > 0)) {
                item(key = "attention_notification") {
                    TaskAttentionCard(
                        title = "Notifications are off",
                        message = "Urgent task alarms need notification access.",
                        actionLabel = "Enable",
                        onAction = requestNotificationPermission,
                        modifier = Modifier.animateItem(),
                        onDismiss = viewModel::dismissNotificationPermissionAction
                    )
                }
            }

            if (showExactAlarmPermissionAction || (!exactAlarmPermissionGranted && urgentCount > 0)) {
                item(key = "attention_exact_alarm") {
                    TaskAttentionCard(
                        title = "Exact alarms are off",
                        message = "Urgent tasks use a 10-minute fallback until exact alarms are enabled.",
                        actionLabel = "Settings",
                        onAction = viewModel::openExactAlarmSettings,
                        modifier = Modifier.animateItem(),
                        onDismiss = viewModel::dismissExactAlarmPermissionAction
                    )
                }
            }

            scheduleStatus?.let { statusMessage ->
                item(key = "attention_schedule_status") {
                    TaskAttentionCard(
                        title = "Scheduling update",
                        message = statusMessage,
                        actionLabel = "OK",
                        onAction = viewModel::clearScheduleStatus,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item {
                ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Assistant triage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = assistantSummary.headline,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = assistantSummary.nextStep,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        TaskFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    if (onOpenDayDial != null) {
                        OutlinedButton(onClick = onOpenDayDial) {
                            Icon(Icons.Default.Today, contentDescription = null)
                            Text("DayDial")
                        }
                    }
                }
            }

            if (visibleTasks.isEmpty()) {
                item(key = "tasks_empty_state") {
                    EmptyTasks(
                        filter = filter,
                        onAdd = { sheetTarget = TaskSheetTarget.Add() },
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                items(visibleTasks, key = { it.id }) { task ->
                    TaskItem(
                        modifier = Modifier.animateItem(),
                        task = task,
                        schedule = taskSchedulesByTaskId[task.id],
                        alarmState = alarmStates[task.id],
                        onToggle = { viewModel.toggleTask(task.id) },
                        onEdit = { sheetTarget = TaskSheetTarget.Edit(task, taskSchedulesByTaskId[task.id]) },
                        onDelete = { viewModel.deleteTask(task) },
                        onSchedule = { viewModel.scheduleTaskToday(task.id) },
                        onDuplicate = { viewModel.duplicateTask(task) },
                        onOpenContext = { openTaskContext(task) }
                    )
                }
            }
        }
    }

    TaskFormSheet(
        target = sheetTarget,
        onDismiss = { sheetTarget = null },
        goalOptions = goalOptions,
        initialGoalId = (sheetTarget as? TaskSheetTarget.Edit)?.task?.goalId,
        onConfirm = { title, desc, priority, dueDate, alarmEnabled, preferredDurationMinutes, preferredStartMinuteOfDay, targetDate, checklist, linkedContact, actions, attachments, recurringConfig, goalId ->
            when (val target = sheetTarget) {
                is TaskSheetTarget.Add -> viewModel.addTask(
                    title = title,
                    description = desc,
                    priority = priority,
                    dueDate = dueDate,
                    alarmEnabled = alarmEnabled,
                    preferredDurationMinutes = preferredDurationMinutes,
                    preferredStartMinuteOfDay = preferredStartMinuteOfDay,
                    targetDate = targetDate,
                    checklist = checklist,
                    linkedContact = linkedContact,
                    actions = actions,
                    attachments = attachments,
                    recurringConfig = recurringConfig,
                    goalId = goalId
                )
                is TaskSheetTarget.Edit -> viewModel.updateTask(
                    task = target.task,
                    title = title,
                    description = desc,
                    priority = priority,
                    dueDate = dueDate,
                    alarmEnabled = alarmEnabled,
                    preferredDurationMinutes = preferredDurationMinutes,
                    preferredStartMinuteOfDay = preferredStartMinuteOfDay,
                    targetDate = targetDate,
                    checklist = checklist,
                    linkedContact = linkedContact,
                    actions = actions,
                    attachments = attachments,
                    recurringConfig = recurringConfig,
                    goalId = goalId
                )
                null -> Unit
            }
            sheetTarget = null
        },
        onDelete = { task ->
            viewModel.deleteTask(task)
            sheetTarget = null
        },
        onDuplicate = { task ->
            viewModel.duplicateTask(task)
            sheetTarget = null
        },
        alarmState = (sheetTarget as? TaskSheetTarget.Edit)?.task?.id?.let(alarmStates::get),
        notificationPermissionGranted = notificationPermissionGranted,
        exactAlarmPermissionGranted = exactAlarmPermissionGranted,
        onRequestNotificationPermission = requestNotificationPermission,
        onOpenExactAlarmSettings = viewModel::openExactAlarmSettings,
        assistState = assistState,
        onRequestAssist = viewModel::requestTaskAssist,
        onClearAssist = viewModel::clearTaskAssist,
        rewriteState = rewriteState,
        onRequestRewrite = viewModel::rewriteTaskDescription,
        onClearRewrite = viewModel::clearTaskRewrite
    )

    commandSheetTask?.let { task ->
        TaskContextCommandSheet(
            task = task,
            schedule = taskSchedulesByTaskId[task.id],
            onDismiss = { commandSheetTask = null },
            onCommand = { command -> handleTaskContextCommand(task, command) }
        )
    }
}

@Composable
private fun EmptyTasks(filter: TaskFilter, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    ChronosEmptyState(
        title = if (filter == TaskFilter.DONE) "No completed tasks" else "No tasks here",
        message = if (filter == TaskFilter.DONE) {
            "Finished work will collect here for review."
        } else {
            "Add the next concrete commitment, then protect time for it on the dial."
        },
        modifier = modifier.fillMaxWidth(),
        action = { Button(onClick = onAdd) { Text("Add task") } }
    )
}

@Composable
private fun TaskAttentionCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
                onDismiss?.let { dismiss ->
                    TextButton(
                        onClick = dismiss,
                        modifier = Modifier.semantics {
                            contentDescription = taskAttentionDismissActionLabel(title)
                        }
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskItem(
    modifier: Modifier = Modifier,
    task: Task,
    schedule: TaskSchedule?,
    alarmState: TaskAlarmUiState?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSchedule: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenContext: () -> Unit
) {
    val commandSet = remember(task) { taskExternalCommandSet(task) }
    val primaryExternalCommand = commandSet.primaryExternalCommand
    val actionCue = taskContextCue(task, schedule)
    
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .semantics {
                            contentDescription = taskCompletionToggleLabel(task)
                        }
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            onClickLabel = taskContextActionLabel(task, commandSet),
                            role = Role.Button,
                            onClick = onOpenContext
                        )
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
                        val titleColor by animateColorAsState(
                            targetValue = if (task.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotion),
                            label = "taskTitleColor"
                        )
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                            color = titleColor,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PriorityBadge(priority = task.priority)
                    }
                    
                    task.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (task.priority >= 2 && !task.isCompleted) {
                            val color by animateColorAsState(
                                targetValue = alarmStateColor(alarmState),
                                animationSpec = ChronosValueAnimationFactory.stateChange(
                                    rememberChronosUiSettings().reduceMotionEnabled
                                ),
                                label = "taskAlarmStateColor"
                            )
                            TaskMetadataBadge(
                                text = alarmStateLabel(task, alarmState),
                                icon = Icons.Default.Alarm,
                                iconColor = color,
                                textColor = color,
                                containerColor = color.copy(alpha = 0.08f)
                            )
                        }
                        
                        taskDialGapLabel(task, schedule)?.let { label ->
                            TaskMetadataBadge(
                                text = label,
                                textColor = MaterialTheme.colorScheme.error,
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            )
                        }
                        
                        taskScheduleSummary(task, schedule)?.let { summary ->
                            TaskMetadataBadge(
                                text = summary,
                                icon = Icons.Default.Today,
                                iconColor = MaterialTheme.colorScheme.primary,
                                textColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        }
                        
                        taskConnectionSummary(task)?.let { summary ->
                            TaskMetadataBadge(
                                text = summary,
                                icon = Icons.Default.Link,
                                iconColor = MaterialTheme.colorScheme.secondary,
                                textColor = MaterialTheme.colorScheme.secondary,
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                            )
                        }
                    }
                    
                    Text(
                        text = actionCue,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!task.isCompleted) {
                        FilledTonalButton(
                            onClick = onSchedule,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .semantics {
                                    contentDescription = taskScheduleActionContentLabel(task, schedule)
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = taskScheduleActionLabel(task, schedule),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    if (primaryExternalCommand != null && !task.isCompleted) {
                        OutlinedButton(
                            onClick = onOpenContext,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = primaryExternalCommand.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = taskDuplicateActionLabel(task),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = taskEditActionLabel(task),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = taskDeleteActionLabel(task),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMetadataBadge(
    text: String,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = iconColor
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskContextCommandSheet(
    task: Task,
    schedule: TaskSchedule?,
    onDismiss: () -> Unit,
    onCommand: (TaskContextCommand) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val commands = remember(task, schedule) {
        taskContextCommands(task, schedule)
    }
    val commandCountText = when (commands.size) {
        0 -> "No quick actions"
        1 -> "1 quick action"
        else -> "${commands.size} quick actions"
    }
    val primaryCommand = commands.firstOrNull()
    val remainingCommands = commands.drop(1)

    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        chromeTag = "task-context-sheet"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "$commandCountText for ${task.title.ifBlank { "this task" }}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (primaryCommand != null) {
                Text(
                    text = "Best ${if (commands.size == 1) "quick action" else "next action"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                TaskCommandRow(
                    command = primaryCommand,
                    emphasized = true,
                    onCommand = onCommand
                )
            }
            if (remainingCommands.isNotEmpty()) {
                Text(
                    text = "${remainingCommands.size} more ${if (remainingCommands.size == 1) "option" else "options"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                remainingCommands.forEach { command ->
                    TaskCommandRow(
                        command = command,
                        emphasized = false,
                        onCommand = onCommand
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = taskContextSheetDismissActionLabel(task)
                    }
            ) {
                Text(taskContextSheetCloseLabel(task))
            }
        }
    }
}

private fun taskContextSheetCloseLabel(task: Task): String {
    val taskTitle = task.title.ifBlank { "this task" }
    return "Close quick actions for $taskTitle"
}

@Composable
private fun TaskCommandRow(
    command: TaskContextCommand,
    emphasized: Boolean,
    onCommand: (TaskContextCommand) -> Unit
) {
    val accent = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = commandClickLabel(command),
                role = Role.Button,
                onClick = { onCommand(command) }
            ),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = commandIcon(command.kind),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = command.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                    text = commandDescription(command),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = command.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun commandIcon(kind: TaskContextCommandKind): ImageVector = when (kind) {
    TaskContextCommandKind.CALL -> Icons.Default.Call
    TaskContextCommandKind.EMAIL -> Icons.Default.Email
    TaskContextCommandKind.LINK -> Icons.Default.Link
    TaskContextCommandKind.MAP -> Icons.Default.Map
    TaskContextCommandKind.APP -> Icons.Default.Apps
    TaskContextCommandKind.IMAGE -> Icons.Default.Image
    TaskContextCommandKind.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
    TaskContextCommandKind.SCHEDULE -> Icons.Default.EventAvailable
    TaskContextCommandKind.EDIT -> Icons.Default.Edit
    TaskContextCommandKind.COMPLETE -> Icons.Default.CheckCircle
}

internal fun commandDescription(command: TaskContextCommand): String = when (command.kind) {
    TaskContextCommandKind.CALL -> "Call linked contact"
    TaskContextCommandKind.EMAIL -> "Email linked contact"
    TaskContextCommandKind.LINK -> "Open linked action"
    TaskContextCommandKind.MAP -> "Open mapped location"
    TaskContextCommandKind.APP -> "Open app shortcut"
    TaskContextCommandKind.IMAGE -> "Open attached photo"
    TaskContextCommandKind.FILE -> "Open attached file"
    TaskContextCommandKind.SCHEDULE -> {
        val target = command.target as? TaskContextCommandTarget.Internal
        if (target?.action == TaskContextInternalAction.SCHEDULE && command.shortLabel == "Add occurrence") {
            "Add another protected DayDial slot"
        } else if (target?.action == TaskContextInternalAction.SCHEDULE && command.label == "Schedule on target DayDial") {
            "Protect time on target DayDial"
        } else {
            "Protect time on today's DayDial"
        }
    }
    TaskContextCommandKind.EDIT -> "Change title, timing, connections, or files"
    TaskContextCommandKind.COMPLETE -> {
        val target = command.target as? TaskContextCommandTarget.Internal
        if (target?.action == TaskContextInternalAction.COMPLETE && command.shortLabel == "Reopen") {
            "Move this task back to Open"
        } else {
            "Move this task to Done"
        }
    }
}

private fun commandClickLabel(command: TaskContextCommand): String = "${command.shortLabel}: ${command.label}"

@Composable
private fun PriorityBadge(priority: Int) {
    val color = when {
        priority >= 2 -> MaterialTheme.colorScheme.error
        priority == 1 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.18f),
        contentColor = color
    ) {
        Text(
            text = priorityLabel(priority),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun priorityLabel(priority: Int): String = when {
    priority >= 2 -> "Urgent"
    priority == 1 -> "High"
    else -> "Normal"
}

internal fun taskContextActionLabel(task: Task): String {
    return taskContextActionLabel(task, taskExternalCommandSet(task))
}

private fun taskContextActionLabel(task: Task, commandSet: TaskContextCommandSet): String {
    val obviousCommand = commandSet.obviousExternalCommand
    return if (obviousCommand != null && taskShouldLaunchObviousExternalCommandDirectly(commandSet)) {
        "${obviousCommand.label} for ${task.title}"
    } else {
        "Open actions for ${task.title}"
    }
}

internal fun taskContextCue(task: Task): String {
    return taskContextCue(task, schedule = null)
}

internal fun taskContextCue(task: Task, schedule: TaskSchedule?): String {
    val commandSet = taskExternalCommandSet(task)
    val primaryCommand = commandSet.primaryExternalCommand
    if (primaryCommand != null) {
        if (taskShouldLaunchObviousExternalCommandDirectly(commandSet)) {
            return "Tap to ${primaryCommand.label}"
        }
        return "Tap for actions · ${primaryCommand.shortLabel}"
    }
    return when {
        task.isCompleted -> "Tap for actions · Reopen"
        schedule != null -> "Tap for actions · Edit and add occurrence"
        else -> "Tap for actions · Edit and schedule"
    }
}

internal fun taskShouldLaunchObviousExternalCommandDirectly(task: Task): Boolean {
    return taskShouldLaunchObviousExternalCommandDirectly(taskExternalCommandSet(task))
}

private fun taskShouldLaunchObviousExternalCommandDirectly(commandSet: TaskContextCommandSet): Boolean {
    return commandSet.obviousExternalCommand != null
}

private fun taskExternalCommandSet(task: Task): TaskContextCommandSet {
    return TaskContextCommandResolver.resolve(
        task = task,
        includeInternalCommands = false,
        includeCompleteCommand = false
    )
}

internal fun taskCompletionToggleLabel(task: Task): String {
    val action = if (task.isCompleted) "incomplete" else "complete"
    return "Mark ${task.title} $action"
}

internal fun taskScheduleActionLabel(
    task: Task,
    schedule: TaskSchedule?
): String = when {
    task.isCompleted -> "Completed"
    schedule != null -> "Add occurrence"
    else -> "Schedule"
}

internal fun taskScheduleActionContentLabel(
    task: Task,
    schedule: TaskSchedule?
): String = when {
    task.isCompleted -> "${task.title} is completed"
    schedule != null -> "Add another DayDial occurrence for ${task.title}"
    else -> "Schedule ${task.title} on ${taskScheduleDialLabel(task)}"
}

internal fun taskDialGapLabel(task: Task, schedule: TaskSchedule?): String? {
    return if (!task.isCompleted && schedule == null) {
        "Not on ${taskScheduleDialLabel(task).replace("DayDial", "dial")}"
    } else {
        null
    }
}

private fun taskScheduleDialLabel(task: Task): String =
    if (task.targetDate != null) "target DayDial" else "today's DayDial"

internal fun taskContextCommands(
    task: Task,
    schedule: TaskSchedule?
): List<TaskContextCommand> {
    val commands = TaskContextCommandResolver.resolve(
        task = task,
        includeInternalCommands = true,
        includeCompleteCommand = true
    ).commands

    val stateAwareCommands = commands.map { command ->
        val target = command.target as? TaskContextCommandTarget.Internal
        if (schedule != null && target?.action == TaskContextInternalAction.SCHEDULE) {
            command.copy(
                label = "Add DayDial occurrence",
                shortLabel = "Add occurrence"
            )
        } else if (schedule == null && target?.action == TaskContextInternalAction.SCHEDULE && task.targetDate != null) {
            command.copy(label = "Schedule on target DayDial")
        } else {
            command
        }
    }

    if (!task.isCompleted) return stateAwareCommands

    val externalCommands = stateAwareCommands.filter { it.isExternal }
    val internalCommands = stateAwareCommands
        .filterNot { it.isExternal }
        .sortedBy { command ->
            val target = command.target as? TaskContextCommandTarget.Internal
            if (target?.action == TaskContextInternalAction.COMPLETE) 0 else 1
        }
    return externalCommands + internalCommands
}

internal fun taskDuplicateActionLabel(task: Task): String = "Duplicate ${task.title}"

internal fun taskEditActionLabel(task: Task): String = "Edit ${task.title}"

internal fun taskDeleteActionLabel(task: Task): String = "Delete ${task.title}"

internal fun taskAttentionDismissActionLabel(title: String): String = "Dismiss ${title.trim()} alert"

internal fun taskContextSheetDismissActionLabel(task: Task): String = "Close actions for ${task.title}"

internal fun taskScheduleSummary(
    task: Task,
    schedule: TaskSchedule?,
    today: LocalDate = LocalDate.now()
): String? {
    recurringSummary(schedule)?.let { recurrenceSummary ->
        val nextOccurrence = schedule?.nextOccurrenceDate?.let { taskNextOccurrenceSummary(it, today) }
        return listOfNotNull(recurrenceSummary, nextOccurrence).joinToString(" • ")
    }
    val parts = buildList {
        task.targetDate?.let { add(taskTargetDateSummary(it, today)) }
        task.preferredDurationMinutes?.let { add("${taskDurationSummary(it)} block") }
        task.preferredStartMinuteOfDay?.let { add("Around ${formatDisplayMinute(it)}") }
        if (task.checklist.isNotEmpty()) {
            add("${task.checklist.count { !it.isCompleted }} steps left")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun taskTargetDateSummary(targetDate: LocalDate, today: LocalDate): String {
    return when (targetDate) {
        today -> "Target today"
        today.plusDays(1) -> "Target tomorrow"
        else -> "Target $targetDate"
    }
}

private fun taskNextOccurrenceSummary(nextOccurrenceDate: LocalDate, today: LocalDate): String {
    return when (nextOccurrenceDate) {
        today -> "Next today"
        today.plusDays(1) -> "Next tomorrow"
        else -> "Next $nextOccurrenceDate"
    }
}

private fun taskDurationSummary(durationMinutes: Int): String {
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${durationMinutes}m"
    }
}

internal fun taskConnectionSummary(task: Task): String? {
    val parts = buildList {
        task.linkedContact?.displayName?.let { add("Contact: $it") }
        if (task.actions.isNotEmpty()) {
            val primaryLabel = task.actions.firstOrNull { it.isPrimary }?.label
                ?: task.actions.firstOrNull()?.label
            add(taskNamedConnectionPart(
                label = "Action",
                fallbackSingular = "action",
                fallbackPlural = "actions",
                count = task.actions.size,
                primaryName = primaryLabel
            ))
        }
        if (task.attachments.isNotEmpty()) {
            val featuredName = task.attachments.firstOrNull { it.isFeaturedImage }?.displayName
                ?: task.attachments.firstOrNull()?.displayName
            add(taskNamedConnectionPart(
                label = "File",
                fallbackSingular = "attachment",
                fallbackPlural = "attachments",
                count = task.attachments.size,
                primaryName = featuredName
            ))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun taskNamedConnectionPart(
    label: String,
    fallbackSingular: String,
    fallbackPlural: String,
    count: Int,
    primaryName: String?
): String {
    val name = primaryName?.takeIf { it.isNotBlank() }
    return when {
        name != null && count > 1 -> "$label: $name + ${count - 1} more"
        name != null -> "$label: $name"
        count == 1 -> "1 $fallbackSingular"
        else -> "$count $fallbackPlural"
    }
}

private fun alarmStateLabel(task: Task, alarmState: TaskAlarmUiState?): String {
    return when (alarmState?.status) {
        TaskAlarmStatus.EXACT ->
            alarmState.scheduledFor?.let(::formatTaskDueInstant) ?: "Exact alarm armed"
        TaskAlarmStatus.DEGRADED_WINDOW ->
            alarmState.scheduledFor?.let { "${formatTaskDueInstant(it)} fallback" } ?: "Fallback window"
        TaskAlarmStatus.NOTIFICATION_PERMISSION_REQUIRED -> "Enable notifications"
        TaskAlarmStatus.EXACT_PERMISSION_REQUIRED -> "Needs exact alarm access"
        null -> task.dueDate?.let(::formatTaskDueInstant) ?: "Alarm not armed"
    }
}

@Composable
private fun alarmStateColor(alarmState: TaskAlarmUiState?): Color = when (alarmState?.status) {
    TaskAlarmStatus.EXACT -> MaterialTheme.colorScheme.primary
    TaskAlarmStatus.DEGRADED_WINDOW -> MaterialTheme.colorScheme.tertiary
    TaskAlarmStatus.NOTIFICATION_PERMISSION_REQUIRED,
    TaskAlarmStatus.EXACT_PERMISSION_REQUIRED -> MaterialTheme.colorScheme.error
    null -> MaterialTheme.colorScheme.error
}

private fun notificationPermissionsForSdk(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 37) {
        arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            "android.permission.POST_PROMOTED_NOTIFICATIONS"
        )
    } else {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    }
}
