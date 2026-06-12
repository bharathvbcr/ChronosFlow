package com.chronosflow.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronosflow.core.ai.TaskAssistPlanner
import com.chronosflow.core.ai.TaskAssistRequest
import com.chronosflow.core.ai.TaskAssistSuggestion
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot
import com.chronosflow.core.ai.genai.refreshAssistUiSnapshot
import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.Goal
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskAction
import com.chronosflow.core.domain.model.TaskAttachment
import com.chronosflow.core.domain.model.TaskChecklistItem
import com.chronosflow.core.domain.model.TaskContactSnapshot
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.GoalRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import com.chronosflow.core.domain.usecase.AddTaskUseCase
import com.chronosflow.core.domain.usecase.GetTasksUseCase
import com.chronosflow.core.domain.usecase.ResolveNextTaskOccurrenceUseCase
import com.chronosflow.core.domain.usecase.ScheduleTaskIntoDayUseCase
import com.chronosflow.core.domain.usecase.SyncRecurringTaskAlarmsUseCase
import com.chronosflow.core.domain.usecase.ToggleTaskCompletionUseCase
import com.chronosflow.core.notifications.AlarmCapabilityRefresher
import com.chronosflow.core.notifications.AlarmScheduleResult
import com.chronosflow.core.notifications.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskAssistUiState(
    val isLoading: Boolean = false,
    val suggestions: List<TaskAssistSuggestion> = emptyList(),
    val message: String? = null,
    val assistSnapshot: GenAiAssistUiSnapshot? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskViewModel @Inject constructor(
    private val getTasksUseCase: GetTasksUseCase,
    private val addTaskUseCase: AddTaskUseCase,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val scheduleTaskIntoDayUseCase: ScheduleTaskIntoDayUseCase,
    private val taskRepository: TaskRepository,
    private val goalRepository: GoalRepository,
    private val taskScheduleRepository: TaskScheduleRepository,
    private val alarmRequestRepository: AlarmRequestRepository,
    private val sleepScheduleRepository: SleepScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
    private val resolveNextTaskOccurrenceUseCase: ResolveNextTaskOccurrenceUseCase,
    private val syncRecurringTaskAlarmsUseCase: SyncRecurringTaskAlarmsUseCase,
    private val taskAssistPlanner: TaskAssistPlanner,
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val alarmCapabilityRefresher: AlarmCapabilityRefresher
) : ViewModel() {

    init {
        viewModelScope.launch {
            alarmCapabilityRefresher.refreshes.collect {
                refreshAlarmCapabilities()
            }
        }
    }

    val tasks = getTasksUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<Goal>> = goalRepository.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskSchedulesByTaskId = tasks
        .flatMapLatest { taskList ->
            if (taskList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    taskList.map { task ->
                        taskScheduleRepository.observeTaskSchedule(task.id)
                            .map { schedule -> task.id to schedule }
                    }
                ) { entries ->
                    entries
                        .mapNotNull { (taskId, schedule) -> schedule?.let { taskId to it } }
                        .toMap()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _scheduleStatus = MutableStateFlow<String?>(null)
    val scheduleStatus = _scheduleStatus.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _assistState = MutableStateFlow(TaskAssistUiState())
    val assistState = _assistState.asStateFlow()

    private val _showExactAlarmPermissionAction = MutableStateFlow(false)
    val showExactAlarmPermissionAction = _showExactAlarmPermissionAction.asStateFlow()

    private val _showNotificationPermissionAction = MutableStateFlow(false)
    val showNotificationPermissionAction = _showNotificationPermissionAction.asStateFlow()

    private val _notificationPermissionGranted = MutableStateFlow(alarmScheduler.canPostReminders())
    val notificationPermissionGranted = _notificationPermissionGranted.asStateFlow()

    private val _exactAlarmPermissionGranted = MutableStateFlow(alarmScheduler.canScheduleExactAlarms())
    val exactAlarmPermissionGranted = _exactAlarmPermissionGranted.asStateFlow()

    internal val urgentTaskAlarmStates = alarmRequestRepository
        .observeRequestsByType(AlarmRequestType.URGENT_TASK)
        .map { requests ->
            requests
                .sortedBy { it.updatedAt }
                .mapNotNull { request ->
                    request.taskIdOrNull()?.let { taskId ->
                        taskId to request.toTaskAlarmUiState()
                    }
                }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addTask(
        title: String,
        description: String? = null,
        priority: Int = 0,
        dueDate: Instant? = null,
        alarmEnabled: Boolean = false,
        preferredDurationMinutes: Int? = null,
        preferredStartMinuteOfDay: Int? = null,
        targetDate: LocalDate? = null,
        checklist: List<TaskChecklistItem> = emptyList(),
        linkedContact: TaskContactSnapshot? = null,
        actions: List<TaskAction> = emptyList(),
        attachments: List<TaskAttachment> = emptyList(),
        recurringConfig: TaskRecurringConfig = TaskRecurringConfig(),
        goalId: String? = null
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val created = addTaskUseCase(
                title = title.trim(),
                description = description?.trim()?.ifBlank { null },
                priority = priority.coerceIn(0, 2),
                dueDate = dueDate,
                preferredDurationMinutes = preferredDurationMinutes,
                preferredStartMinuteOfDay = preferredStartMinuteOfDay,
                targetDate = targetDate,
                checklist = checklist,
                linkedContact = linkedContact,
                actions = actions,
                attachments = attachments
            )
            val task = if (created.goalId != goalId) {
                created.copy(goalId = goalId).also { taskRepository.saveTask(it) }
            } else {
                created
            }
            val schedule = buildTaskScheduleFromConfig(
                taskId = task.id,
                existingSchedule = null,
                config = recurringConfig,
                occurrenceMinuteOfDay = preferredStartMinuteOfDay,
                resolver = resolveNextTaskOccurrenceUseCase
            )
            if (schedule != null) {
                taskScheduleRepository.saveTaskSchedule(schedule)
                syncRecurringTaskAlarmsUseCase(task, schedule)
                schedulePersistedTaskAlarms(task.id)
            }
            if (priority >= 2 && alarmEnabled && dueDate != null) {
                _status.value = scheduleUrgentAlarm(task.copy(dueDate = dueDate))
            }
        }
    }

    fun updateTask(
        task: Task,
        title: String,
        description: String?,
        priority: Int,
        dueDate: Instant? = null,
        alarmEnabled: Boolean = false,
        preferredDurationMinutes: Int? = null,
        preferredStartMinuteOfDay: Int? = null,
        targetDate: LocalDate? = null,
        checklist: List<TaskChecklistItem> = emptyList(),
        linkedContact: TaskContactSnapshot? = null,
        actions: List<TaskAction> = emptyList(),
        attachments: List<TaskAttachment> = emptyList(),
        recurringConfig: TaskRecurringConfig = TaskRecurringConfig(),
        goalId: String? = null
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val updated = task.copy(
                title = title.trim(),
                description = description?.trim()?.ifBlank { null },
                priority = priority.coerceIn(0, 2),
                dueDate = if (priority >= 2 && alarmEnabled) dueDate else null,
                preferredDurationMinutes = preferredDurationMinutes,
                preferredStartMinuteOfDay = preferredStartMinuteOfDay,
                targetDate = targetDate,
                checklist = checklist,
                linkedContact = linkedContact,
                actions = actions,
                attachments = attachments,
                goalId = goalId,
                updatedAt = Instant.now()
            )
            taskRepository.saveTask(updated)
            val existingSchedule = taskScheduleRepository.getTaskSchedule(task.id)
            val newSchedule = buildTaskScheduleFromConfig(
                taskId = task.id,
                existingSchedule = existingSchedule,
                config = recurringConfig,
                occurrenceMinuteOfDay = preferredStartMinuteOfDay,
                resolver = resolveNextTaskOccurrenceUseCase
            )
            if (newSchedule != null) {
                taskScheduleRepository.saveTaskSchedule(newSchedule)
                syncRecurringTaskAlarmsUseCase(updated, newSchedule)
                schedulePersistedTaskAlarms(task.id)
            } else if (existingSchedule != null) {
                taskScheduleRepository.deleteTaskSchedule(task.id)
                cancelTaskAlarms(task.id)
            }
            syncUrgentAlarm(updated, alarmEnabled)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            cancelTaskAlarms(task.id)
            taskScheduleRepository.deleteTaskSchedule(task.id)
            taskRepository.deleteTask(task)
            _status.value = "Task deleted"
        }
    }

    fun toggleTask(taskId: String) {
        viewModelScope.launch {
            val task = taskRepository.getTaskById(taskId) ?: return@launch
            val scheduleBeforeToggle = taskScheduleRepository.getTaskSchedule(taskId)
            toggleTaskCompletionUseCase(taskId)
            val updatedTask = taskRepository.getTaskById(taskId) ?: task
            if (scheduleBeforeToggle != null) {
                val updatedSchedule = taskScheduleRepository.getTaskSchedule(taskId)
                when {
                    updatedSchedule?.nextOccurrenceDate != null -> {
                        syncRecurringTaskAlarmsUseCase(updatedTask, updatedSchedule)
                        schedulePersistedTaskAlarms(taskId)
                    }
                    else -> cancelTaskAlarms(taskId)
                }
                return@launch
            }
            if (updatedTask.isCompleted) {
                cancelTaskAlarms(updatedTask.id)
            } else if (updatedTask.priority >= 2 && updatedTask.dueDate?.isAfter(Instant.now()) == true) {
                _status.value = scheduleUrgentAlarm(updatedTask)
            }
        }
    }

    fun scheduleTaskToday(taskId: String) {
        viewModelScope.launch {
            val result = scheduleTaskIntoDayUseCase(taskId)
            _scheduleStatus.value = result.message
        }
    }

    fun clearScheduleStatus() {
        _scheduleStatus.value = null
    }

    fun clearStatus() {
        _status.value = null
    }

    fun requestTaskAssist(request: TaskAssistRequest) {
        viewModelScope.launch {
            val snapshot = runCatching { genAiAssistCoordinator.refreshAssistUiSnapshot() }.getOrNull()
            _assistState.value = TaskAssistUiState(isLoading = true, assistSnapshot = snapshot)
            val suggestions = runCatching { taskAssistPlanner.suggest(request) }
                .onFailure { error ->
                    _assistState.value = TaskAssistUiState(
                        message = error.message ?: "Task suggestions are unavailable",
                        assistSnapshot = snapshot
                    )
                }
                .getOrDefault(emptyList())
            if (suggestions.isNotEmpty()) {
                _assistState.value = TaskAssistUiState(
                    suggestions = suggestions,
                    assistSnapshot = snapshot,
                    message = snapshot?.takeIf { it.aiDisabled }?.let { GenAiAssistCopy.disabledAssistMessage() }
                )
            } else if (_assistState.value.isLoading) {
                _assistState.value = TaskAssistUiState(
                    message = "No suggestions available",
                    assistSnapshot = snapshot
                )
            }
        }
    }

    fun clearTaskAssist() {
        _assistState.value = TaskAssistUiState()
    }

    fun refreshAlarmCapabilities() {
        _notificationPermissionGranted.value = alarmScheduler.canPostReminders()
        _exactAlarmPermissionGranted.value = alarmScheduler.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings() {
        alarmScheduler.routeToExactAlarmSetting()
    }

    fun dismissExactAlarmPermissionAction() {
        _showExactAlarmPermissionAction.value = false
    }

    fun dismissNotificationPermissionAction() {
        _showNotificationPermissionAction.value = false
    }

    fun duplicateTask(task: Task) {
        viewModelScope.launch {
            val duplicated = task.copy(
                id = UUID.randomUUID().toString(),
                isCompleted = false,
                dueDate = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                checklist = task.checklist.map { item ->
                    item.copy(id = UUID.randomUUID().toString(), isCompleted = false)
                },
                linkedContact = task.linkedContact?.let { contact ->
                    contact.copy(
                        methods = contact.methods.map { method ->
                            method.copy(id = UUID.randomUUID().toString())
                        }
                    )
                },
                actions = task.actions.map { action ->
                    action.copy(id = UUID.randomUUID().toString())
                },
                attachments = task.attachments.map { attachment ->
                    attachment.copy(id = UUID.randomUUID().toString())
                }
            )
            taskRepository.saveTask(duplicated)
            _status.value = "Task duplicated"
        }
    }

    private suspend fun syncUrgentAlarm(task: Task, alarmEnabled: Boolean) {
        cancelTaskAlarms(task.id)
        if (task.priority >= 2 && alarmEnabled && task.dueDate != null) {
            _status.value = scheduleUrgentAlarm(task)
        } else if (task.priority >= 2 && !alarmEnabled) {
            _status.value = "Urgent task saved without alarm"
        }
    }

    private suspend fun scheduleUrgentAlarm(task: Task): String {
        val dueDate = task.dueDate ?: return "No reminder time set"
        val sleepSchedule = sleepScheduleRepository.getSleepSchedule()
        val request = AlarmRequest(
            id = taskAlarmId(task.id),
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = dueDate,
            title = task.title,
            message = task.description?.takeIf { it.isNotBlank() } ?: "Urgent task due now",
            medicationPlanId = null,
            blockId = task.id,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val localDateTime = dueDate.atZone(java.time.ZoneId.systemDefault())
        val minuteOfDay = localDateTime.hour * 60 + localDateTime.minute
        val result = if (sleepSchedule.contains(minuteOfDay)) {
            AlarmScheduleResult.Skipped(request.id, "Alarm falls inside the sleep schedule")
        } else {
            alarmScheduler.scheduleAlarmRequest(request)
        }
        alarmRequestRepository.saveAlarmRequest(request.withScheduleResult(result))
        _showExactAlarmPermissionAction.value = result is AlarmScheduleResult.Scheduled && !result.exact ||
            result is AlarmScheduleResult.ExactDenied
        _showNotificationPermissionAction.value = result is AlarmScheduleResult.PermissionDenied
        refreshAlarmCapabilities()
        return when (result) {
            is AlarmScheduleResult.Scheduled -> if (result.exact) {
                "Exact alarm scheduled for ${formatTaskDueInstant(dueDate)}"
            } else {
                "Alarm scheduled with a 10-minute fallback window"
            }
            is AlarmScheduleResult.ExactDenied -> "Exact alarm permission is required for urgent task alarms"
            is AlarmScheduleResult.PermissionDenied -> "Notification permission is required for task alarms"
            is AlarmScheduleResult.Skipped -> if (result.reason.contains("sleep schedule", ignoreCase = true)) {
                "Alarm skipped during sleep hours"
            } else {
                "Alarm skipped: ${result.reason}"
            }
        }
    }

    private suspend fun cancelTaskAlarms(taskId: String) {
        alarmScheduler.cancelAlarm(taskAlarmId(taskId))
        val matchingRequests = alarmRequestRepository
            .observeRequestsByType(AlarmRequestType.URGENT_TASK)
            .first()
            .filter { request ->
                request.blockId == taskId || request.id == taskAlarmId(taskId) || request.id.startsWith("${taskAlarmId(taskId)}:")
            }
        matchingRequests.forEach { request ->
            alarmScheduler.cancelAlarm(request.id)
            alarmRequestRepository.saveAlarmRequest(
                request.copy(
                    deliveryState = AlarmDeliveryState.CANCELLED,
                    updatedAt = Instant.now()
                )
            )
        }
    }

    private suspend fun schedulePersistedTaskAlarms(taskId: String) {
        val matchingRequests = alarmRequestRepository
            .observeRequestsByType(AlarmRequestType.URGENT_TASK)
            .first()
            .filter { request ->
                (request.blockId == taskId || request.id == taskAlarmId(taskId) || request.id.startsWith("${taskAlarmId(taskId)}:")) &&
                    request.deliveryState != AlarmDeliveryState.CANCELLED
            }
        matchingRequests.forEach { request ->
            val result = alarmScheduler.scheduleAlarmRequest(request)
            alarmRequestRepository.saveAlarmRequest(request.withScheduleResult(result))
        }
    }

    private fun AlarmRequest.taskIdOrNull(): String? {
        return blockId ?: id.removePrefix("task:").takeIf { it != id }
    }

    private fun AlarmRequest.withScheduleResult(result: AlarmScheduleResult): AlarmRequest {
        val now = Instant.now()
        val (reliability, deliveryState, failureReason) = when (result) {
            is AlarmScheduleResult.Scheduled -> if (result.exact) {
                Triple(AlarmReliability.EXACT, AlarmDeliveryState.SCHEDULED, null)
            } else {
                Triple(
                    AlarmReliability.DEGRADED_WINDOW,
                    AlarmDeliveryState.DEGRADED,
                    "Exact alarm permission unavailable; scheduled with fallback window"
                )
            }
            is AlarmScheduleResult.ExactDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Exact alarm permission denied"
            )
            is AlarmScheduleResult.PermissionDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Notification permission denied"
            )
            is AlarmScheduleResult.Skipped -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                result.reason
            )
        }
        return copy(
            reliability = reliability,
            deliveryState = deliveryState,
            updatedAt = now,
            failureReason = failureReason
        )
    }
}
