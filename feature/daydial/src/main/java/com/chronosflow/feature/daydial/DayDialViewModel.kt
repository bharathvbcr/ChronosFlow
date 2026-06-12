package com.chronosflow.feature.daydial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.FocusGuidancePlanner
import com.chronosflow.core.ai.FocusNextBlockPlanner
import com.chronosflow.core.ai.FocusNextBlockSuggestion
import com.chronosflow.core.ai.RecommendationQuickAction
import com.chronosflow.core.ai.MoodEnergyCheckInAssistPlanner
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.FreeTimeCalculator
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.data.assist.ProactiveAssistCache
import com.chronosflow.core.data.dao.FocusSessionDao
import com.chronosflow.core.data.backup.ChronosDataExportFile
import com.chronosflow.core.data.backup.ChronosDataExportRepository
import com.chronosflow.core.data.focus.FocusMoodAccentCache
import com.chronosflow.core.data.focus.ManualMissedBlockRegistry
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.repository.CalendarEventRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import com.chronosflow.core.domain.repository.RoutineRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.model.Routine
import com.chronosflow.core.domain.usecase.ApplyRoutineToDateUseCase
import com.chronosflow.core.domain.usecase.CompleteHabitUseCase
import com.chronosflow.core.domain.usecase.CompleteRoutineForDateUseCase
import com.chronosflow.core.domain.usecase.ToggleTaskCompletionUseCase
import com.chronosflow.core.notifications.AlarmCapabilityRefresher
import com.chronosflow.core.notifications.HabitReminderScheduler
import com.chronosflow.feature.daydial.delegate.DayDialAiDelegate
import androidx.fragment.app.FragmentActivity
import com.chronosflow.core.data.security.AppLockAuthResult
import com.chronosflow.core.data.security.SensitiveArea
import com.chronosflow.feature.daydial.delegate.DayDialAppLockDelegate
import com.chronosflow.feature.daydial.delegate.DayDialBlockDelegate
import com.chronosflow.feature.daydial.delegate.CompanionTrendSections
import com.chronosflow.feature.daydial.delegate.DayDialFocusDelegate
import com.chronosflow.feature.daydial.delegate.DayDialJournalDelegate
import com.chronosflow.feature.daydial.delegate.DayDialMoodEnergyDelegate
import com.chronosflow.feature.daydial.delegate.DayDialTrendsDelegate
import com.chronosflow.feature.daydial.delegate.DayDialReminderDelegate
import com.chronosflow.feature.daydial.delegate.DayDialReviewDelegate
import com.chronosflow.feature.daydial.model.DayQuickItemsUiState
import com.chronosflow.feature.daydial.model.InsightsTabUiState
import com.chronosflow.feature.daydial.model.buildDayQuickItemsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

typealias PlannerHapticCue = com.chronosflow.feature.daydial.model.PlannerHapticCue
typealias FocusExecutionStatus = com.chronosflow.feature.daydial.model.FocusExecutionStatus
typealias FocusExecutionState = com.chronosflow.feature.daydial.model.FocusExecutionState
typealias FocusPhase = com.chronosflow.feature.daydial.model.FocusPhase
typealias FocusPhaseKind = com.chronosflow.feature.daydial.model.FocusPhaseKind
typealias DailyReview = com.chronosflow.feature.daydial.model.DailyReview

data class CalendarConnectionState(
    val isWorking: Boolean = false,
    val statusMessage: String = "Calendar not connected yet",
    val lastSuccessMessage: String? = null
)

data class DataExportState(
    val isExporting: Boolean = false,
    val lastFileName: String? = null,
    val summary: String = "No export created yet",
    val errorMessage: String? = null
)

private fun ChronosDataExportFile.toState(): DataExportState =
    DataExportState(
        lastFileName = file.name,
        summary = buildString {
            append("Exported $rowCount database rows from $tableCount tables")
            if (localStateFileCount > 0) {
                append(" plus $localStateFileCount local settings ")
                append(if (localStateFileCount == 1) "file" else "files")
            }
            append(" (${byteCount.formatBytes()})")
        }
    )

private fun Long.formatBytes(): String = when {
    this >= 1024L * 1024L -> "${this / (1024L * 1024L)} MB"
    this >= 1024L -> "${this / 1024L} KB"
    else -> "$this B"
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DayDialViewModel @Inject constructor(
    private val repository: TimeBlockRepository,
    private val taskRepository: TaskRepository,
    private val taskScheduleRepository: TaskScheduleRepository,
    private val habitRepository: HabitRepository,
    private val medicationRepository: MedicationRepository,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val completeHabitUseCase: CompleteHabitUseCase,
    private val calendarEventRepository: CalendarEventRepository,
    private val focusSessionDao: FocusSessionDao,
    private val focusDelegate: DayDialFocusDelegate,
    private val blockDelegate: DayDialBlockDelegate,
    private val aiDelegate: DayDialAiDelegate,
    private val reminderDelegate: DayDialReminderDelegate,
    private val reviewDelegate: DayDialReviewDelegate,
    private val moodEnergyDelegate: DayDialMoodEnergyDelegate,
    private val journalDelegate: DayDialJournalDelegate,
    private val trendsDelegate: DayDialTrendsDelegate,
    private val moodEnergyCheckInAssistPlanner: MoodEnergyCheckInAssistPlanner,
    private val focusNextBlockPlanner: FocusNextBlockPlanner,
    private val focusGuidancePlanner: FocusGuidancePlanner,
    private val proactiveAssistCache: ProactiveAssistCache,
    private val appLockDelegate: DayDialAppLockDelegate,
    private val alarmCapabilityRefresher: AlarmCapabilityRefresher,
    private val habitReminderScheduler: HabitReminderScheduler,
    private val reminderPreferencesReader: DayDialReminderPreferencesReader,
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry,
    private val focusMoodAccentCache: FocusMoodAccentCache,
    private val dataExportRepository: ChronosDataExportRepository,
    private val routineRepository: RoutineRepository,
    private val applyRoutineToDateUseCase: ApplyRoutineToDateUseCase,
    private val completeRoutineForDateUseCase: CompleteRoutineForDateUseCase
) : ViewModel() {

    val appLockSettings = appLockDelegate.settings
    val sensitiveSession = appLockDelegate.sensitiveSession
    internal var dataExportDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val plannerService = PlannerService(repository)
    private val freeTimeCalculator = FreeTimeCalculator()
    private val coordinatorState = DayDialCoordinatorState()
    private val stateFlows = buildDayDialStateFlows(
        scope = viewModelScope,
        repository = repository,
        selectedDate = coordinatorState.selectedDate,
        selectedBlockId = coordinatorState.selectedBlockId,
        focusExecutionState = focusDelegate.focusExecutionState,
        dragPreview = blockDelegate.dragPreview,
        reviewDelegate = reviewDelegate,
        freeTimeCalculator = freeTimeCalculator
    )

    val selectedDate = coordinatorState.selectedDate
    val selectedBlockId = coordinatorState.selectedBlockId
    private val _dataExportState = MutableStateFlow(DataExportState())
    val dataExportState = _dataExportState.asStateFlow()
    val routines: kotlinx.coroutines.flow.StateFlow<List<Routine>> = routineRepository.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val manualMissedBlockIds = combine(
        coordinatorState.selectedDate,
        manualMissedBlockRegistry.ids
    ) { date, entries ->
        manualMissedBlockRegistry.missedIdsForDate(date, entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    private val quickItemTasks = taskRepository.getAllTasks()
    private val quickItemTaskSchedules = quickItemTasks.flatMapLatest { tasks ->
        if (tasks.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(tasks.map { task -> taskScheduleRepository.observeTaskSchedule(task.id) }) { schedules ->
                tasks.mapIndexed { index, task -> task.id to schedules[index] }.toMap()
            }
        }
    }
    internal val dayQuickItems = combine(
        coordinatorState.selectedDate,
        quickItemTasks,
        quickItemTaskSchedules,
        habitRepository.observeHabits(),
        medicationRepository.observeMedicationPlans()
    ) { date, tasks, taskSchedules, habits, medications ->
        buildDayQuickItemsState(
            selectedDate = date,
            tasks = tasks,
            taskSchedules = taskSchedules,
            habits = habits,
            medications = medications
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayQuickItemsUiState())
    val missedFromFocusMessage = manualMissedBlockRegistry.missedFromFocusMessage
    val focusExecutionState = focusDelegate.focusExecutionState
    private val _recoverableServiceSessionId = MutableStateFlow<String?>(null)
    val recoverableServiceSessionId = _recoverableServiceSessionId.asStateFlow()
    private val _focusRestoredMessage = MutableStateFlow<String?>(null)
    val focusRestoredMessage = _focusRestoredMessage.asStateFlow()
    val reminderScheduleStatus = reminderDelegate.reminderScheduleStatus
    val medicationReliabilityStatus = reminderDelegate.medicationReliabilityStatus(viewModelScope)
    val hapticCue = coordinatorState.hapticCue
    val compactMode = coordinatorState.compactMode
    val compactWindowStart = coordinatorState.compactWindowStart

    val aiPlanResult = aiDelegate.aiPlanResult
    val privacyMode = aiDelegate.privacyMode
    val previewOnDeviceModel = aiDelegate.previewOnDeviceModel
    val suggestedBlocks = aiDelegate.suggestedBlocks
    val isGenerating = aiDelegate.isGenerating
    val explainPlan = aiDelegate.explainPlan
    val explainPlanSource = aiDelegate.explainPlanSource
    val repairPlanResult = aiDelegate.repairPlanResult
    val aiPlanGoalPrefill = aiDelegate.aiPlanGoalPrefill
    val aiPlanSuggestedGoals = aiDelegate.aiPlanSuggestedGoals
    val genAiRuntimeStatus = aiDelegate.genAiRuntimeStatus

    private val _moodCheckInCoaching = MutableStateFlow<AssistNarrative?>(null)
    val moodCheckInCoaching = _moodCheckInCoaching.asStateFlow()

    private val _focusGuidance = MutableStateFlow<AssistNarrative?>(null)
    val focusGuidance = _focusGuidance.asStateFlow()
    private val _focusNextBlockSuggestion = MutableStateFlow<FocusNextBlockSuggestion?>(null)
    val focusNextBlockSuggestion = _focusNextBlockSuggestion.asStateFlow()
    private var focusAssistJob: Job? = null

    val canUndo = blockDelegate.canUndo
    val canRedo = blockDelegate.canRedo

    val lastResult = coordinatorState.lastResult
    private val _calendarConnectionState = MutableStateFlow(CalendarConnectionState())
    val calendarConnectionState = _calendarConnectionState.asStateFlow()

    val missedBlocks = reviewDelegate.missedBlocks(viewModelScope, selectedDate)
    val selectedFocusBlock = stateFlows.selectedFocusBlock
    val timeBlocks = stateFlows.timeBlocks
    val timeBlocksDomain = stateFlows.timeBlocksDomain
    val freeTime = stateFlows.freeTime
    val selectedBlock = stateFlows.selectedBlock
    val dailyReview = stateFlows.dailyReview
    val reviewInsights = reviewDelegate.reviewInsights(viewModelScope, selectedDate)
    private val _insightsTabState = MutableStateFlow(InsightsTabUiState())
    val insightsTabState = _insightsTabState.asStateFlow()
    val moodEnergyCheckIns = moodEnergyDelegate.observeCheckIns(viewModelScope, selectedDate)
    val journalEntryForDay = journalDelegate.journalEntry(viewModelScope, selectedDate)
    val sleepTrackForDay = journalDelegate.sleepTrack(viewModelScope, selectedDate)

    val currentMinute = coordinatorState.currentMinute
    private val minuteTickerJob: Job

    init {
        viewModelScope.launch {
            focusSessionDao.observeRecoverableSession().collect { entity ->
                val persisted = entity?.toDomain()
                _recoverableServiceSessionId.value = recoverableServiceSessionId(persisted)
                when {
                    persisted is FocusSessionState.Running || persisted is FocusSessionState.Paused -> {
                        if (focusDelegate.isIdle()) {
                            focusDelegate.restoreFromPersistedSession(persisted)
                            _focusRestoredMessage.value = "Resumed your active focus session"
                        }
                    }

                    persisted == null ||
                        persisted is FocusSessionState.Idle ||
                        persisted is FocusSessionState.Archived -> {
                        if (!focusDelegate.isIdle()) {
                            focusDelegate.clearToIdle()
                        }
                    }
                }
            }
        }
        aiDelegate.refreshGenAiStatus(viewModelScope)
        minuteTickerJob = viewModelScope.launch {
            coordinatorState.refreshCurrentMinute()
            while (true) {
                delay(nextMinuteBoundaryDelayMillis())
                coordinatorState.refreshCurrentMinute()
            }
        }
        viewModelScope.launch {
            alarmCapabilityRefresher.refreshes.collect {
                refreshReminderScheduleFromStoredPreferences()
            }
        }
        viewModelScope.launch {
            manualMissedBlockRegistry.externalSkipEvents
                .distinctUntilChanged()
                .collect { blockId ->
                    focusDelegate.skipIfActiveBlock(blockId)
                }
        }
        viewModelScope.launch {
            focusDelegate.focusExecutionState
                .map { it.status to it.blockId }
                .distinctUntilChanged()
                .collect { (status, blockId) ->
                    when (status) {
                        FocusExecutionStatus.RUNNING -> refreshFocusSessionAssist(blockId)
                        FocusExecutionStatus.FINISHED -> refreshFocusNextBlockSuggestion(blockId)
                        FocusExecutionStatus.IDLE,
                        FocusExecutionStatus.SKIPPED -> {
                            focusAssistJob?.cancel()
                            _focusGuidance.value = null
                            _focusNextBlockSuggestion.value = null
                        }
                        else -> Unit
                    }
                }
        }
        refreshTrendSections()
        viewModelScope.launch {
            combine(reviewInsights, selectedDate, timeBlocksDomain) { insights, date, blocks ->
                Triple(insights, date, blocks)
            }.collect { (insights, date, blocks) ->
                val summary = runCatching {
                    reviewDelegate.currentReviewSummary(date, blocks)
                }.getOrNull()
                val recommendations = reviewDelegate.localInsightRecommendations(summary, insights)
                // Fallback daily coach line for passive surfaces (widget): the
                // Review screen writes the richer narrative, but only when opened.
                val today = LocalDate.now()
                if (date == today && recommendations.isNotEmpty() &&
                    proactiveAssistCache.dailyCoachLine(today) == null
                ) {
                    val top = recommendations.first()
                    proactiveAssistCache.putDailyCoachLine(
                        date = today,
                        headline = top.text,
                        nextStep = "",
                        source = top.source.name
                    )
                }
                _insightsTabState.update { state ->
                    state.copy(
                        reviewInsights = insights,
                        recommendations = if (state.isRefreshing) state.recommendations else recommendations
                    )
                }
            }
        }
    }

    fun clearMissedFromFocusMessage() = manualMissedBlockRegistry.clearMissedFromFocusMessage()

    fun setInsightsTrendRange(days: Int) = refreshTrendSections(days.coerceIn(7, 60))

    private fun refreshTrendSections(
        windowDays: Int = _insightsTabState.value.trendRangeDays
    ) {
        viewModelScope.launch {
            val sections = runCatching { trendsDelegate.loadTrends(windowDays) }
                .getOrDefault(CompanionTrendSections())
            _insightsTabState.update { state ->
                state.copy(trendRangeDays = windowDays, trends = sections)
            }
        }
    }

    fun saveJournalEntry(date: LocalDate, body: String, promptType: String?) {
        viewModelScope.launch {
            journalDelegate.saveJournalEntry(date, body, promptType, journalEntryForDay.value)
        }
    }

    fun saveSleepLog(
        date: LocalDate,
        quality: Int,
        actualStartMinute: Int?,
        actualEndMinute: Int?,
        interruptions: Int,
        windDownNotes: String?
    ) {
        viewModelScope.launch {
            journalDelegate.saveSleepLog(
                date = date,
                quality = quality,
                actualStartMinute = actualStartMinute,
                actualEndMinute = actualEndMinute,
                interruptions = interruptions,
                windDownNotes = windDownNotes,
                existing = sleepTrackForDay.value
            )
        }
    }

    /**
     * Session started or resumed: generate in-session guidance plus the AI pick
     * for the block after this one. The pick is also cached so the completion
     * notification (posted by the background FocusService) can reuse it.
     */
    private fun refreshFocusSessionAssist(activeBlockId: String?) {
        focusAssistJob?.cancel()
        focusAssistJob = viewModelScope.launch {
            val state = focusDelegate.focusExecutionState.value
            val (moodScore, energyScore) = focusMoodAccentCache.moodEnergyForBlock(activeBlockId)
            val suggestion = focusNextBlockPlanner.suggestNextBlock(
                blocks = timeBlocksDomain.value.filter { it.id != activeBlockId },
                currentMinute = currentMinute.value,
                moodScore = moodScore,
                energyScore = energyScore
            )
            _focusNextBlockSuggestion.value = suggestion
            if (suggestion != null) {
                proactiveAssistCache.putFocusNextBlockLine(
                    date = LocalDate.now(),
                    blockId = suggestion.id,
                    line = "Next up: ${suggestion.title} — ${suggestion.reason}"
                )
            }
            _focusGuidance.value = focusGuidancePlanner.suggestGuidance(
                focusTitle = state.blockTitle ?: "Focus session",
                linkedBlockId = state.blockId,
                isRunning = state.status == FocusExecutionStatus.RUNNING,
                isPaused = state.status == FocusExecutionStatus.PAUSED,
                timeLeft = focusDelegate.focusRemainingSeconds(state).toInt(),
                totalSeconds = (state.plannedDurationMinutes * 60).coerceAtLeast(1),
                nextBlockTitle = suggestion?.title,
                moodScore = moodScore,
                energyScore = energyScore
            )
        }
    }

    /** Session finished: clear guidance and re-pick the next block fresh. */
    private fun refreshFocusNextBlockSuggestion(finishedBlockId: String?) {
        focusAssistJob?.cancel()
        focusAssistJob = viewModelScope.launch {
            _focusGuidance.value = null
            val (moodScore, energyScore) = focusMoodAccentCache.moodEnergyForBlock(finishedBlockId)
            _focusNextBlockSuggestion.value = focusNextBlockPlanner.suggestNextBlock(
                blocks = timeBlocksDomain.value.filter { it.id != finishedBlockId },
                currentMinute = currentMinute.value,
                moodScore = moodScore,
                energyScore = energyScore
            )
        }
    }

    fun focusMoodAccentFor(blockId: String?): Pair<Int?, Int?> =
        focusMoodAccentCache.moodEnergyForBlock(blockId)

    internal fun cancelMinuteTickerForTest() {
        minuteTickerJob.cancel()
    }

    fun selectDate(date: LocalDate) = coordinatorState.selectDate(date)

    fun syncCalendar() {
        viewModelScope.launch {
            val start = selectedDate.value.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            val end = selectedDate.value.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            setCalendarWorking("Refreshing device calendar for ${selectedDate.value}")
            runCatching {
                calendarEventRepository.syncFromDeviceCalendar(start, end)
            }.onSuccess {
                setCalendarSuccess("Device calendar refreshed for ${selectedDate.value}")
            }.onFailure { throwable ->
                setCalendarFailure("Calendar refresh failed: ${calendarErrorText(throwable)}")
            }
        }
    }

    fun exportBlockToCalendar(blockId: String) {
        viewModelScope.launch {
            val block = repository.getTimeBlockById(blockId)
            if (block == null) {
                setCalendarFailure("Calendar export failed: block no longer exists")
                return@launch
            }
            setCalendarWorking("Connecting ${calendarBlockTitle(block)} to device calendar")
            runCatching {
                calendarEventRepository.exportTimeBlock(block)
            }.onSuccess { exportedEventId ->
                if (exportedEventId == null) {
                    setCalendarFailure("Calendar export failed for ${calendarBlockTitle(block)}")
                    return@onSuccess
                }
                repository.saveTimeBlock(block.copy(calendarEventId = exportedEventId))
                setCalendarSuccess("Connected ${calendarBlockTitle(block)} to device calendar")
            }.onFailure { throwable ->
                setCalendarFailure("Calendar export failed: ${calendarErrorText(throwable)}")
            }
        }
    }

    fun refreshCalendarExport(blockId: String) {
        viewModelScope.launch {
            val block = repository.getTimeBlockById(blockId)
            if (block == null) {
                setCalendarFailure("Calendar export refresh failed: block no longer exists")
                return@launch
            }
            if (block.calendarEventId == null) {
                setCalendarFailure("${calendarBlockTitle(block)} is not connected to device calendar")
                return@launch
            }
            setCalendarWorking("Refreshing calendar export for ${calendarBlockTitle(block)}")
            runCatching {
                calendarEventRepository.updateExportedTimeBlock(block)
            }.onSuccess { updated ->
                if (updated) {
                    setCalendarSuccess("Calendar export refreshed for ${calendarBlockTitle(block)}")
                } else {
                    setCalendarFailure("Calendar export could not be refreshed for ${calendarBlockTitle(block)}")
                }
            }.onFailure { throwable ->
                setCalendarFailure("Calendar export refresh failed: ${calendarErrorText(throwable)}")
            }
        }
    }

    fun removeCalendarExport(blockId: String) {
        viewModelScope.launch {
            val block = repository.getTimeBlockById(blockId)
            if (block == null) {
                setCalendarFailure("Calendar export removal failed: block no longer exists")
                return@launch
            }
            val calendarEventId = block.calendarEventId
            if (calendarEventId == null) {
                setCalendarFailure("${calendarBlockTitle(block)} is not connected to device calendar")
                return@launch
            }
            setCalendarWorking("Removing calendar export for ${calendarBlockTitle(block)}")
            runCatching {
                calendarEventRepository.deleteExportedTimeBlock(calendarEventId)
            }.onSuccess { deleted ->
                if (deleted) {
                    repository.saveTimeBlock(block.copy(calendarEventId = null))
                    setCalendarSuccess("Calendar export removed for ${calendarBlockTitle(block)}")
                } else {
                    setCalendarFailure("Calendar export could not be removed for ${calendarBlockTitle(block)}")
                }
            }.onFailure { throwable ->
                setCalendarFailure("Calendar export removal failed: ${calendarErrorText(throwable)}")
            }
        }
    }

    private fun setCalendarWorking(message: String) {
        _calendarConnectionState.value = _calendarConnectionState.value.copy(
            isWorking = true,
            statusMessage = message
        )
    }

    private fun setCalendarSuccess(message: String) {
        _calendarConnectionState.value = CalendarConnectionState(
            isWorking = false,
            statusMessage = message,
            lastSuccessMessage = message
        )
    }

    private fun setCalendarFailure(message: String) {
        _calendarConnectionState.value = _calendarConnectionState.value.copy(
            isWorking = false,
            statusMessage = message
        )
    }

    private fun calendarBlockTitle(block: TimeBlock): String = block.title.ifBlank { "this block" }

    private fun calendarErrorText(throwable: Throwable): String =
        throwable.message ?: throwable::class.simpleName ?: "unknown error"

    fun refreshReminderScheduleFromStoredPreferences() {
        val settings = reminderPreferencesReader.read()
        refreshReminderSchedule(
            blockStartReminders = settings.blockStartReminders,
            breakReminders = settings.breakReminders,
            missedAlerts = settings.missedAlerts,
            endDayReviewReminder = settings.endDayReviewReminder,
            sleepScheduleEnabled = settings.sleepScheduleEnabled,
            sleepScheduleStartMinute = settings.sleepScheduleStartMinute,
            sleepScheduleEndMinute = settings.sleepScheduleEndMinute
        )
    }

    fun refreshReminderSchedule(
        blockStartReminders: Boolean,
        breakReminders: Boolean,
        missedAlerts: Boolean,
        endDayReviewReminder: Boolean,
        sleepScheduleEnabled: Boolean,
        sleepScheduleStartMinute: Int,
        sleepScheduleEndMinute: Int
    ) {
        reminderDelegate.refreshReminderSchedule(
            scope = viewModelScope,
            date = coordinatorState.selectedDateValue,
            blockStartReminders = blockStartReminders,
            breakReminders = breakReminders,
            missedAlerts = missedAlerts,
            endDayReviewReminder = endDayReviewReminder,
            sleepScheduleEnabled = sleepScheduleEnabled,
            sleepScheduleStartMinute = sleepScheduleStartMinute,
            sleepScheduleEndMinute = sleepScheduleEndMinute
        )
    }

    fun openExactAlarmSettings() = reminderDelegate.openExactAlarmSettings()

    fun onBlockSelected(blockId: String?) = coordinatorState.selectBlock(blockId)

    fun moveToPreviousDate() = coordinatorState.moveToPreviousDate()

    fun moveToNextDate() = coordinatorState.moveToNextDate()

    fun onCompactModeToggled() = coordinatorState.onCompactModeToggled(selectedBlock.value?.startMinuteOfDay)

    fun moveWindowBack() = coordinatorState.moveWindowBack()

    fun moveWindowForward() = coordinatorState.moveWindowForward()

    fun onBlockMoved(blockId: String, newStartMinute: Int) =
        blockDelegate.onBlockMoved(viewModelScope, blockId, newStartMinute, ::handlePlannerResult)

    fun onBlockDragStarted(blockId: String, startMinute: Int) =
        blockDelegate.onBlockDragStarted(blockId, startMinute)

    fun onBlockDragMoved(blockId: String, newStartMinute: Int) =
        blockDelegate.onBlockDragMoved(viewModelScope, blockId, newStartMinute, ::handlePlannerResult)

    fun onBlockMoveCommitted(blockId: String, finalStartMinute: Int) =
        blockDelegate.onBlockMoveCommitted(viewModelScope, blockId, finalStartMinute, ::handlePlannerResult)

    fun onBlockResize(blockId: String, startMinute: Int, durationMinutes: Int) =
        blockDelegate.onBlockResize(viewModelScope, blockId, startMinute, durationMinutes, ::handlePlannerResult)

    fun onBlockResizeCommitted(blockId: String, finalStartMinute: Int, finalDurationMinutes: Int) =
        blockDelegate.onBlockResizeCommitted(viewModelScope, blockId, finalStartMinute, finalDurationMinutes, ::handlePlannerResult)

    fun onBlockDragCancelled() {
        blockDelegate.onBlockDragCancelled()
        clearHapticCue()
    }

    fun createQuickBlock(title: String = "Focus Block", startMinute: Int = DialUtils.snapToIncrement(coordinatorState.currentMinuteValue), durationMinutes: Int = 25, category: String = "WORK") {
        blockDelegate.createQuickBlock(
            scope = viewModelScope,
            date = coordinatorState.selectedDateValue,
            title = title,
            startMinute = startMinute,
            durationMinutes = durationMinutes,
            category = category,
            onResult = ::handlePlannerResult
        )
    }

    fun createQuickBlockAtMinute(startMinute: Int, title: String = "Focus Block", durationMinutes: Int = 25, category: String = "WORK") =
        createQuickBlock(title, DialUtils.snapToIncrement(startMinute), durationMinutes, category)

    /** Persists a routine (create or update) derived from a [Routine] domain model. */
    fun persistRoutine(routine: Routine) {
        viewModelScope.launch {
            routineRepository.saveRoutine(routine)
        }
    }

    /** Deletes the routine with [routineId] if it exists. */
    fun deleteRoutineById(routineId: String) {
        viewModelScope.launch {
            routineRepository.getRoutineById(routineId)?.let { routineRepository.deleteRoutine(it) }
        }
    }

    /**
     * One-time idempotent import of any legacy prefs-era templates into the routines table.
     * Runs [onComplete] once the routines table is guaranteed seeded, so the caller can stop
     * re-importing on later loads.
     */
    fun importLegacyRoutinesIfEmpty(legacyRoutines: List<Routine>, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (routineRepository.observeRoutines().first().isEmpty()) {
                legacyRoutines.forEach { routineRepository.saveRoutine(it) }
            }
            onComplete()
        }
    }

    /**
     * Instantiates a routine's steps as blocks on [date], anchored at [startMinuteOfDay]
     * (template-backed routines pass 0 — their offsets are absolute minutes-of-day), then
     * reports how many blocks were created via [onApplied].
     */
    fun applyRoutineToDate(
        routineId: String,
        date: LocalDate,
        startMinuteOfDay: Int,
        onApplied: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val created = applyRoutineToDateUseCase(routineId, date, startMinuteOfDay)
            onApplied(created)
        }
    }

    /** Marks the routine completed for [date]. */
    fun completeRoutineForDate(routineId: String, date: LocalDate) {
        viewModelScope.launch {
            completeRoutineForDateUseCase(routineId, date)
        }
    }

    fun duplicateBlock(blockId: String) = blockDelegate.duplicateBlock(viewModelScope, blockId, ::handlePlannerResult)

    fun deleteBlock(blockId: String) {
        blockDelegate.deleteBlock(viewModelScope, blockId, ::handlePlannerResult) { deletedBlockId ->
            coordinatorState.clearSelectedBlockIf(blockId)
            reviewDelegate.removeMissedBlock(deletedBlockId, coordinatorState.selectedDateValue)
        }
    }

    fun clearCurrentDay() = blockDelegate.clearCurrentDay(viewModelScope, coordinatorState.selectedDateValue)

    fun copyPlanFromPreviousDay() =
        blockDelegate.copyPlanFromPreviousDay(viewModelScope, coordinatorState.selectedDateValue, ::handlePlannerResult)

    fun fillEmptyTime() =
        blockDelegate.fillEmptyTime(viewModelScope, coordinatorState.selectedDateValue, ::handlePlannerResult)

    fun markCurrentBlockMissed(blockId: String, missed: Boolean) =
        reviewDelegate.markCurrentBlockMissed(viewModelScope, blockId, missed)

    fun markBlockComplete(blockId: String) = reviewDelegate.markBlockComplete(viewModelScope, blockId)

    fun completeDayQuickTask(taskId: String) {
        viewModelScope.launch {
            toggleTaskCompletionUseCase(taskId)
        }
    }

    fun completeDayQuickHabit(habitId: String) {
        viewModelScope.launch {
            val habit = habitRepository.getHabitById(habitId) ?: return@launch
            completeHabitUseCase(habit, coordinatorState.selectedDate.value)
            val updated = habitRepository.getHabitById(habitId)
                ?: habit.copy(lastCompletedDate = coordinatorState.selectedDate.value)
            habitReminderScheduler.syncUpcomingHabitReminder(updated)
        }
    }

    fun markDayQuickMedicationTaken(planId: String, scheduledMinuteOfDay: Int?) {
        recordDayQuickMedicationDose(
            planId = planId,
            scheduledMinuteOfDay = scheduledMinuteOfDay,
            type = MedicationDoseEventType.TAKEN,
            reason = null
        )
    }

    fun markDayQuickMedicationMissed(planId: String, scheduledMinuteOfDay: Int?) {
        recordDayQuickMedicationDose(
            planId = planId,
            scheduledMinuteOfDay = scheduledMinuteOfDay,
            type = MedicationDoseEventType.MISSED,
            reason = "Marked missed"
        )
    }

    private fun recordDayQuickMedicationDose(
        planId: String,
        scheduledMinuteOfDay: Int?,
        type: MedicationDoseEventType,
        reason: String?
    ) {
        viewModelScope.launch {
            val plan = medicationRepository.getMedicationPlanById(planId) ?: return@launch
            val scheduledMinute = scheduledMinuteOfDay ?: plan.reminderMinuteOfDay
            medicationRepository.addMedicationDoseEvent(
                MedicationDoseEvent(
                    id = UUID.randomUUID().toString(),
                    medicationPlanId = plan.id,
                    type = type,
                    eventDate = coordinatorState.selectedDate.value,
                    recordedAt = Instant.now(),
                    scheduledMinuteOfDay = scheduledMinute,
                    reason = reason,
                    doseAmount = if (type == MedicationDoseEventType.TAKEN) plan.dosage else null
                )
            )
            if (type == MedicationDoseEventType.TAKEN) {
                val remaining = plan.safetyProfile?.supplyRemaining?.let { (it - 1).coerceAtLeast(0) }
                medicationRepository.saveMedicationPlan(
                    plan.copy(safetyProfile = plan.safetyProfile?.copy(supplyRemaining = remaining))
                )
            } else if (type == MedicationDoseEventType.MISSED) {
                medicationRepository.saveMedicationPlan(plan.copy(missedCount = plan.missedCount + 1))
            }
        }
    }

    fun updateBlockTitle(blockId: String, title: String) =
        blockDelegate.updateBlockTitle(viewModelScope, blockId, title)

    fun updateBlockDetails(blockId: String, title: String, startMinute: Int, durationMinutes: Int, category: String, isLocked: Boolean, isProtected: Boolean) {
        blockDelegate.updateBlockDetails(
            scope = viewModelScope,
            blockId = blockId,
            title = title,
            startMinute = startMinute,
            durationMinutes = durationMinutes,
            category = category,
            isLocked = isLocked,
            isProtected = isProtected,
            onResult = ::handlePlannerResult
        )
    }

    fun logActualRange(blockId: String, actualStartMinute: Int, actualEndMinute: Int) =
        reviewDelegate.logActualRange(viewModelScope, blockId, actualStartMinute, actualEndMinute)

    fun deleteSelectedBlock() {
        val blockId = coordinatorState.selectedBlockIdValue ?: return
        deleteBlock(blockId)
    }

    fun onAiPlanRequested(goals: List<String>) =
        aiDelegate.requestPlan(viewModelScope, coordinatorState.selectedDateValue, goals, ::currentReviewSummary)

    fun applyInsightRecommendation(
        recommendation: String,
        onOpenAiPlan: () -> Unit,
        onApplied: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val intent = aiDelegate.interpretRecommendation(
                recommendation = recommendation,
                date = coordinatorState.selectedDateValue,
                blocks = timeBlocksDomain.value,
                reviewProvider = ::currentReviewSummary
            )
            when (intent.quickAction) {
                RecommendationQuickAction.FILL_GAPS -> {
                    fillEmptyTime()
                    onApplied("Filled open time from recommendation")
                }
                RecommendationQuickAction.ADD_BREAK -> {
                    createQuickBlock(title = "Break", durationMinutes = 15, category = "BREAK")
                    onApplied("Added a 15-minute break block")
                }
                RecommendationQuickAction.OPEN_AI_PLAN -> {
                    aiDelegate.setAiPlanPrefillFromIntent(intent)
                    onOpenAiPlan()
                }
            }
        }
    }

    fun clearAiPlanPrefill() = aiDelegate.clearAiPlanPrefill()

    fun refreshInsightsRecommendations() {
        viewModelScope.launch {
            _insightsTabState.update { it.copy(isRefreshing = true) }
            val result = runCatching {
                reviewDelegate.refreshInsightsTab(coordinatorState.selectedDateValue)
            }.getOrNull()
            _insightsTabState.value = if (result == null) {
                _insightsTabState.value.copy(isRefreshing = false)
            } else {
                InsightsTabUiState(
                    reviewInsights = result.reviewInsights,
                    recommendations = result.recommendations,
                    assistSnapshot = result.assistSnapshot,
                    isRefreshing = false
                )
            }
        }
    }

    fun startFocusSession(blockId: String, workMinutes: Int = 0, breakMinutes: Int = 0) {
        coordinatorState.selectBlock(blockId)
        focusDelegate.startFocusSession(viewModelScope, plannerService, blockId, workMinutes, breakMinutes)
    }

    fun pauseFocusSession() = focusDelegate.pauseFocusSession()

    fun resumeFocusSession() = focusDelegate.resumeFocusSession()

    /** Tap-to-continue at a split-session phase boundary. */
    fun advanceFocusPhase() = focusDelegate.advancePhase()

    /** Drives split-session phase boundaries; invoked once per timer tick. */
    fun checkFocusPhaseBoundary() = focusDelegate.checkPhaseBoundary(viewModelScope, plannerService)

    fun extendFocusSession(additionalMinutes: Int = 15) = focusDelegate.extendFocusSession(additionalMinutes)

    fun shortenFocusSession(minutes: Int = 15) = focusDelegate.shortenFocusSession(minutes)

    fun skipFocusSession() {
        focusDelegate.skipFocusSession { blockId ->
            markCurrentBlockMissed(blockId, true)
        }
        coordinatorState.selectBlock(null)
    }

    fun finishFocusSession(note: String = "") {
        focusDelegate.finishFocusSession(viewModelScope, plannerService, note)
        coordinatorState.selectBlock(null)
    }

    fun endDayReview(markCompleted: List<String> = emptyList(), markMissed: List<String> = emptyList()) =
        reviewDelegate.endDayReview(viewModelScope, coordinatorState.selectedDateValue, markCompleted, markMissed)

    fun focusElapsedSeconds(state: FocusExecutionState = focusExecutionState.value): Long =
        focusDelegate.focusElapsedSeconds(state)

    fun focusRemainingSeconds(state: FocusExecutionState = focusExecutionState.value): Long =
        focusDelegate.focusRemainingSeconds(state)

    fun clearFocusRestoredMessage() {
        _focusRestoredMessage.value = null
    }

    fun applyAiSuggestions() =
        aiDelegate.applyAiSuggestions(viewModelScope, coordinatorState.selectedDateValue, ::handlePlannerResult)

    fun acceptAiSuggestion(suggestionId: String) =
        aiDelegate.acceptSuggestion(viewModelScope, coordinatorState.selectedDateValue, suggestionId, ::handlePlannerResult)

    fun rejectAiSuggestion(suggestionId: String) = aiDelegate.rejectSuggestion(suggestionId)

    fun rejectAllAiSuggestions() = aiDelegate.rejectAllSuggestions()

    fun modifyAiSuggestion(suggestionId: String, title: String, startMinuteOfDay: Int, durationMinutes: Int) {
        aiDelegate.modifySuggestion(suggestionId, title, startMinuteOfDay, durationMinutes)
    }

    fun rebalanceDay() =
        blockDelegate.rebalanceDay(
            viewModelScope,
            coordinatorState.selectedDateValue,
            fromMinute = if (coordinatorState.isViewingToday) coordinatorState.currentMinuteValue else null,
            ::handlePlannerResult
        )

    fun undo() = blockDelegate.undo(viewModelScope, ::handlePlannerResult)

    fun redo() = blockDelegate.redo(viewModelScope, ::handlePlannerResult)

    fun setPrivacyMode(mode: PrivacyMode) = aiDelegate.setPrivacyMode(mode)

    fun setAppLockEnabled(enabled: Boolean) = appLockDelegate.setAppLockEnabled(enabled)

    fun setLockOnResume(enabled: Boolean) = appLockDelegate.setLockOnResume(enabled)

    fun setRequireAuthMedication(enabled: Boolean) = appLockDelegate.setRequireAuthMedication(enabled)

    fun setRequireAuthReview(enabled: Boolean) = appLockDelegate.setRequireAuthReview(enabled)

    fun setRequireAuthDataExport(enabled: Boolean) = appLockDelegate.setRequireAuthDataExport(enabled)

    fun createDataExport(onReady: (File) -> Unit) {
        if (_dataExportState.value.isExporting) return

        _dataExportState.value = DataExportState(isExporting = true, summary = "Preparing full local data export")
        viewModelScope.launch {
            runCatching {
                withContext(dataExportDispatcher) {
                    dataExportRepository.exportSnapshot()
                }
            }.onSuccess { export ->
                _dataExportState.value = export.toState()
                onReady(export.file)
            }.onFailure { error ->
                _dataExportState.value = DataExportState(
                    summary = "Export failed",
                    errorMessage = error.message ?: error::class.java.simpleName
                )
            }
        }
    }

    fun requiresSensitiveAuth(area: SensitiveArea): Boolean =
        appLockDelegate.requiresSensitiveAuth(area)

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        appLockDelegate.canAuthenticate(activity)

    fun unlockSensitiveArea(
        activity: FragmentActivity,
        area: SensitiveArea,
        onResult: (AppLockAuthResult) -> Unit
    ) = appLockDelegate.unlockSensitiveArea(activity, area, onResult)

    fun setPreviewOnDeviceModel(enabled: Boolean) {
        aiDelegate.setPreviewOnDeviceModel(enabled)
        aiDelegate.refreshGenAiStatus(viewModelScope)
    }

    fun explainCurrentPlan() =
        aiDelegate.explainCurrentPlan(viewModelScope, coordinatorState.selectedDateValue, ::currentReviewSummary)

    fun repairConflictingPlan(conflictDescription: String) =
        aiDelegate.repairConflictingPlan(viewModelScope, coordinatorState.selectedDateValue, conflictDescription)

    fun clearHapticCue() = coordinatorState.clearHapticCue()

    private fun handlePlannerResult(result: PlannerOperationResult, forceSuccessfulDrop: Boolean) =
        coordinatorState.handlePlannerResult(result, forceSuccessfulDrop)

    private suspend fun currentReviewSummary(blocks: List<TimeBlock>): DailyReviewSummary =
        reviewDelegate.currentReviewSummary(coordinatorState.selectedDateValue, blocks)

    fun saveMoodEnergyCheckIn(
        moodScore: Int,
        stressScore: Int,
        energyScore: Int,
        focusScore: Int
    ) {
        val blockTitle = selectedBlock.value?.title
        moodEnergyDelegate.saveCheckIn(
            scope = viewModelScope,
            date = coordinatorState.selectedDateValue,
            blockId = selectedBlockId.value,
            moodScore = moodScore,
            stressScore = stressScore,
            energyScore = energyScore,
            focusScore = focusScore
        )
        viewModelScope.launch {
            _moodCheckInCoaching.value = moodEnergyCheckInAssistPlanner.suggestAfterCheckIn(
                moodScore = moodScore,
                stressScore = stressScore,
                energyScore = energyScore,
                focusScore = focusScore,
                linkedBlockTitle = blockTitle
            )
        }
    }

    fun clearMoodCheckInCoaching() {
        _moodCheckInCoaching.value = null
    }
}
