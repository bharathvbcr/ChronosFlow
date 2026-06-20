package com.ChronosFlow.VBCR.feature.daydial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.ai.AssistNarrative
import com.ChronosFlow.VBCR.core.ai.FocusGuidancePlanner
import com.ChronosFlow.VBCR.core.ai.FocusNextBlockPlanner
import com.ChronosFlow.VBCR.core.ai.FocusNextBlockSuggestion
import com.ChronosFlow.VBCR.core.ai.RecommendationQuickAction
import com.ChronosFlow.VBCR.core.ai.MoodEnergyCheckInAssistPlanner
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.RoutineAssistPlanner
import com.ChronosFlow.VBCR.core.ai.RoutineAssistRequest
import com.ChronosFlow.VBCR.core.ai.RoutineAssistSuggestion
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.genai.refreshAssistUiSnapshot
import com.ChronosFlow.VBCR.core.domain.diagnostics.AppEventCategory
import com.ChronosFlow.VBCR.core.domain.diagnostics.AppEventLog
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.ConflictResolutionResult
import com.ChronosFlow.VBCR.core.domain.planner.FreeTimeCalculator
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.data.assist.ProactiveAssistCache
import com.ChronosFlow.VBCR.core.data.dao.FocusSessionDao
import com.ChronosFlow.VBCR.core.data.backup.ChronosDataExportFile
import com.ChronosFlow.VBCR.core.data.backup.ChronosDataExportRepository
import com.ChronosFlow.VBCR.core.data.focus.FocusMoodAccentCache
import com.ChronosFlow.VBCR.core.data.focus.ManualMissedBlockRegistry
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.repository.CalendarEventRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.model.Routine
import com.ChronosFlow.VBCR.core.domain.usecase.ApplyRoutineToDateUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteHabitUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteRoutineForDateUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ToggleTaskCompletionUseCase
import com.ChronosFlow.VBCR.core.notifications.AlarmCapabilityRefresher
import com.ChronosFlow.VBCR.core.notifications.CurrentBlockNotificationCoordinator
import com.ChronosFlow.VBCR.core.notifications.HabitReminderScheduler
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialAiDelegate
import androidx.fragment.app.FragmentActivity
import com.ChronosFlow.VBCR.core.data.security.AppLockAuthResult
import com.ChronosFlow.VBCR.core.data.security.SensitiveArea
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialAppLockDelegate
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialBlockDelegate
import com.ChronosFlow.VBCR.feature.daydial.delegate.CompanionTrendSections
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialFocusDelegate
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialJournalDelegate
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialMoodEnergyDelegate
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialTrendsDelegate
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialReminderDelegate
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialReviewDelegate
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemsUiState
import com.ChronosFlow.VBCR.feature.daydial.model.InsightsPeriod
import com.ChronosFlow.VBCR.feature.daydial.model.InsightsTabUiState
import com.ChronosFlow.VBCR.feature.daydial.model.buildDayQuickItemsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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

typealias PlannerHapticCue = com.ChronosFlow.VBCR.feature.daydial.model.PlannerHapticCue
typealias FocusExecutionStatus = com.ChronosFlow.VBCR.feature.daydial.model.FocusExecutionStatus
typealias FocusExecutionState = com.ChronosFlow.VBCR.feature.daydial.model.FocusExecutionState
typealias FocusPhase = com.ChronosFlow.VBCR.feature.daydial.model.FocusPhase
typealias FocusPhaseKind = com.ChronosFlow.VBCR.feature.daydial.model.FocusPhaseKind
typealias DailyReview = com.ChronosFlow.VBCR.feature.daydial.model.DailyReview

/** Insights trend window bounds and default (days); user selections are coerced into this range. */
private const val MIN_TREND_RANGE_DAYS = 7
private const val MAX_TREND_RANGE_DAYS = 60
private const val DEFAULT_TREND_RANGE_DAYS = 14

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

data class RoutineAssistUiState(
    val isLoading: Boolean = false,
    val suggestions: List<RoutineAssistSuggestion> = emptyList(),
    val message: String? = null,
    val assistSnapshot: GenAiAssistUiSnapshot? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
    private val completeRoutineForDateUseCase: CompleteRoutineForDateUseCase,
    private val routineAssistPlanner: RoutineAssistPlanner,
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator,
    private val appEventLog: AppEventLog
) : ViewModel() {

    /** Recent in-memory app events surfaced by the developer "View Logs" sheet. */
    val appEventLogEntries = appEventLog.entries

    val appLockSettings = appLockDelegate.settings
    val sensitiveSession = appLockDelegate.sensitiveSession
    internal var dataExportDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var calendarSyncDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val calendarAutoSyncGate = CalendarAutoSyncGate()
    // Overridable like the dispatchers above so tests can drive the throttle clock.
    internal var foregroundSyncClock: () -> Long = { System.currentTimeMillis() }
    private var lastForegroundSyncAtMillis: Long? = null
    private val foregroundSyncMinIntervalMillis = 60_000L

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
    private val _routineAssistState = MutableStateFlow(RoutineAssistUiState())
    val routineAssistState = _routineAssistState.asStateFlow()
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
            }.distinctUntilChanged()
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
    }
        .debounce(100L)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayQuickItemsUiState())
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
    // Last successful device-calendar sync time, for the plan page's "Synced X ago" hint.
    // Updates live across all sync paths (manual, foreground, periodic worker) via the store flow.
    val lastCalendarSyncAtMillis: StateFlow<Long?> =
        calendarEventRepository.observeLastDeviceSyncAtMillis()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

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
    private val _insightsPeriod = MutableStateFlow(InsightsPeriod.DAY)
    private val _trendRangeDays = MutableStateFlow(DEFAULT_TREND_RANGE_DAYS)
    val trendRangeDays: kotlinx.coroutines.flow.StateFlow<Int> = _trendRangeDays.asStateFlow()

    /**
     * Reactive companion trend sections for the Insights tab. Kept out of [insightsTabState] so the
     * five Room-backed sources are only subscribed while the UI is observing (WhileSubscribed), and so
     * an imperative recommendations refresh can't wipe the loaded trends. Re-subscribes when the range
     * changes or the day rolls over.
     */
    val insightsTrends: kotlinx.coroutines.flow.StateFlow<CompanionTrendSections> =
        combine(_trendRangeDays, coordinatorState.currentDate) { days, today -> days to today }
            .flatMapLatest { (days, today) ->
                trendsDelegate.observeTrends(days, today)
                    .catch { emit(CompanionTrendSections()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanionTrendSections())
    private val periodInsights = reviewDelegate.observePeriodInsights(viewModelScope, selectedDate, _insightsPeriod)
    val moodEnergyCheckIns = moodEnergyDelegate.observeCheckIns(viewModelScope, selectedDate)
    val journalEntryForDay = journalDelegate.journalEntry(viewModelScope, selectedDate)
    val sleepTrackForDay = journalDelegate.sleepTrack(viewModelScope, selectedDate)

    val currentMinute = coordinatorState.currentMinute
    private val minuteTickerJob: Job

    init {
        appEventLog.record(AppEventCategory.SESSION, "Day planner session started")
        viewModelScope.launch {
            selectedDate.collect { date ->
                if (calendarAutoSyncGate.shouldSync(date)) {
                    quietSyncDeviceCalendar(date)
                }
            }
        }
        viewModelScope.launch {
            focusSessionDao.observeRecoverableSession()
                .catch { e -> appEventLog.record(AppEventCategory.SESSION, "collector error: $e") }
                .collect { entity ->
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
            alarmCapabilityRefresher.refreshes
                .catch { e -> appEventLog.record(AppEventCategory.SESSION, "collector error: $e") }
                .collect {
                    refreshReminderScheduleFromStoredPreferences()
                }
        }
        viewModelScope.launch {
            manualMissedBlockRegistry.externalSkipEvents
                .distinctUntilChanged()
                .catch { e -> appEventLog.record(AppEventCategory.SESSION, "collector error: $e") }
                .collect { blockId ->
                    focusDelegate.skipIfActiveBlock(blockId)
                }
        }
        viewModelScope.launch {
            focusDelegate.focusExecutionState
                .map { it.status to it.blockId }
                .distinctUntilChanged()
                .catch { e -> appEventLog.record(AppEventCategory.SESSION, "collector error: $e") }
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
        viewModelScope.launch {
            combine(_insightsPeriod, periodInsights) { period, summary -> period to summary }
                .catch { e -> appEventLog.record(AppEventCategory.SESSION, "collector error: $e") }
                .collect { (period, summary) ->
                    _insightsTabState.update { it.copy(period = period, periodSummary = summary) }
                }
        }
        viewModelScope.launch {
            combine(reviewInsights, selectedDate, timeBlocksDomain) { insights, date, blocks ->
                Triple(insights, date, blocks)
            }
                // Block edits arrive in bursts (the write plus its reactive re-emit). The review
                // summary below runs 14-day mood/habit/medication reads + correlation analysis, so
                // coalesce bursts instead of recomputing for every intermediate emission — this
                // keeps the shared DB connection free for the timeline repaint after a delete.
                .debounce(250L)
                .catch { e -> appEventLog.record(AppEventCategory.SESSION, "collector error: $e") }
                .collect { (insights, date, blocks) ->
                // Heavy compute (DB fan-out + correlation analysis) runs off the main thread so it
                // never competes with rendering the just-edited timeline.
                val recommendations = withContext(Dispatchers.Default) {
                    val summary = runCatching {
                        reviewDelegate.currentReviewSummary(date, blocks)
                    }.getOrNull()
                    reviewDelegate.localInsightRecommendations(summary, insights)
                }
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

    fun setInsightsTrendRange(days: Int) {
        // [trendRangeDays] is exposed directly from this source, so the chip reflects the new range
        // immediately and [insightsTrends] re-subscribes for the new window.
        _trendRangeDays.value = days.coerceIn(MIN_TREND_RANGE_DAYS, MAX_TREND_RANGE_DAYS)
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

    /**
     * Quietly pulls the latest device-calendar events for the day in view when the
     * app returns to the foreground. Unlike [syncCalendar] this stays silent — no
     * working banner or status message — so reopening the app picks up calendar
     * edits made elsewhere without surfacing UI on every resume. The per-date
     * [calendarAutoSyncGate] only covers the first visit to each date for the
     * lifetime of the ViewModel, so a surviving instance needs this on re-open.
     *
     * Throttled to at most once per [foregroundSyncMinIntervalMillis]: each sync is
     * a wipe-and-replace of the day's imported blocks, so rapid app-switching would
     * otherwise churn the database and flicker the timeline on every return.
     */
    fun refreshCalendarForAppForeground() {
        val now = foregroundSyncClock()
        val last = lastForegroundSyncAtMillis
        if (last != null && now - last < foregroundSyncMinIntervalMillis) return
        lastForegroundSyncAtMillis = now
        viewModelScope.launch {
            quietSyncDeviceCalendar(selectedDate.value)
        }
    }

    /**
     * Quiet refresh: the repository no-ops without READ_CALENDAR permission and
     * failures must not raise calendar banners — the manual sync action reports
     * status when the user asks.
     */
    private suspend fun quietSyncDeviceCalendar(date: LocalDate) {
        runCatching {
            val zone = java.time.ZoneId.systemDefault()
            withContext(calendarSyncDispatcher) {
                calendarEventRepository.syncFromDeviceCalendar(
                    date.atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant()
                )
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
        appEventLog.record(AppEventCategory.SYNC, message)
    }

    private fun setCalendarFailure(message: String) {
        _calendarConnectionState.value = _calendarConnectionState.value.copy(
            isWorking = false,
            statusMessage = message
        )
        appEventLog.record(AppEventCategory.ERROR, message)
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
            sleepScheduleEndMinute = settings.sleepScheduleEndMinute,
            journalRemindersEnabled = settings.journalEnabled,
            sleepJournalLogReminder = settings.sleepJournalLogReminder,
            sleepJournalRemindersEnabled = settings.sleepEnabled || settings.journalEnabled
        )
    }

    fun refreshReminderSchedule(
        blockStartReminders: Boolean,
        breakReminders: Boolean,
        missedAlerts: Boolean,
        endDayReviewReminder: Boolean,
        sleepScheduleEnabled: Boolean,
        sleepScheduleStartMinute: Int,
        sleepScheduleEndMinute: Int,
        journalRemindersEnabled: Boolean = true,
        sleepJournalLogReminder: Boolean = false,
        sleepJournalRemindersEnabled: Boolean = true
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
            sleepScheduleEndMinute = sleepScheduleEndMinute,
            journalRemindersEnabled = journalRemindersEnabled,
            sleepJournalLogReminder = sleepJournalLogReminder,
            sleepJournalRemindersEnabled = sleepJournalRemindersEnabled
        )
    }

    fun openExactAlarmSettings() = reminderDelegate.openExactAlarmSettings()

    fun onBlockSelected(blockId: String?) = coordinatorState.selectBlock(blockId)

    fun moveToPreviousDate() = coordinatorState.moveToPreviousDate()

    fun moveToNextDate() = coordinatorState.moveToNextDate()

    fun onCompactModeToggled() = coordinatorState.onCompactModeToggled(selectedBlock.value?.startMinuteOfDay)

    fun moveWindowBack() = coordinatorState.moveWindowBack()

    fun moveWindowForward() = coordinatorState.moveWindowForward()

    fun centerWindowOnNow() = coordinatorState.centerWindowOnNow()

    fun setCurrentBlockNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            currentBlockNotificationCoordinator.setEnabled(enabled)
        }
    }

    /** Re-renders the current-block live notification after schedule edits. */
    fun refreshCurrentBlockNotification() {
        viewModelScope.launch {
            currentBlockNotificationCoordinator.refresh()
        }
    }

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
            runCatching { routineRepository.saveRoutine(routine) }
                .onFailure { e -> appEventLog.record(AppEventCategory.ERROR, "write failed: $e") }
        }
    }

    /** Deletes the routine with [routineId] if it exists. */
    fun deleteRoutineById(routineId: String) {
        viewModelScope.launch {
            runCatching { routineRepository.getRoutineById(routineId)?.let { routineRepository.deleteRoutine(it) } }
                .onFailure { e -> appEventLog.record(AppEventCategory.ERROR, "write failed: $e") }
        }
    }

    /**
     * Drafts editable routine suggestions (name + step outline) from the current template-editor
     * capture via [RoutineAssistPlanner]. Mirrors the habit/medication assist flow: best-effort
     * on-device proofread of the name is merged in, and nothing is applied until the user taps a chip.
     */
    fun requestRoutineAssist(request: RoutineAssistRequest) {
        viewModelScope.launch {
            val snapshot = runCatching { genAiAssistCoordinator.refreshAssistUiSnapshot() }.getOrNull()
            _routineAssistState.value = RoutineAssistUiState(isLoading = true, assistSnapshot = snapshot)
            val suggestions = runCatching { routineAssistPlanner.suggest(request) }
                .getOrElse { throwable ->
                    _routineAssistState.value = RoutineAssistUiState(
                        message = throwable.message ?: "No suggestions available",
                        assistSnapshot = snapshot
                    )
                    return@launch
                }
            val refinedTitle = runCatching {
                request.title.takeIf { it.isNotBlank() }?.let { routineAssistPlanner.refineTitle(it) }
            }.getOrNull()
            val merged = (listOfNotNull(refinedTitle) + suggestions).distinctBy { it.id }
            _routineAssistState.value = if (merged.isNotEmpty()) {
                RoutineAssistUiState(
                    suggestions = merged,
                    assistSnapshot = snapshot,
                    message = snapshot?.takeIf { it.aiDisabled }?.let { GenAiAssistCopy.disabledAssistMessage() }
                )
            } else {
                RoutineAssistUiState(message = "No suggestions available", assistSnapshot = snapshot)
            }
        }
    }

    fun clearRoutineAssist() {
        _routineAssistState.value = RoutineAssistUiState()
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
            runCatching { applyRoutineToDateUseCase(routineId, date, startMinuteOfDay) }
                .onSuccess { created -> onApplied(created) }
                .onFailure { e -> appEventLog.record(AppEventCategory.ERROR, "write failed: $e") }
        }
    }

    /** Marks the routine completed for [date]. */
    fun completeRoutineForDate(routineId: String, date: LocalDate) {
        viewModelScope.launch {
            runCatching { completeRoutineForDateUseCase(routineId, date) }
                .onFailure { e -> appEventLog.record(AppEventCategory.ERROR, "write failed: $e") }
        }
    }

    fun duplicateBlock(blockId: String, onOutcome: (PlannerOperationResult) -> Unit = {}) =
        blockDelegate.duplicateBlock(viewModelScope, blockId) { result, isFinalMove ->
            handlePlannerResult(result, isFinalMove)
            onOutcome(result)
        }

    fun deleteBlock(blockId: String) {
        blockDelegate.deleteBlock(viewModelScope, blockId, ::handlePlannerResult) { deletedBlockId ->
            coordinatorState.clearSelectedBlockIf(blockId)
            reviewDelegate.removeMissedBlock(deletedBlockId, coordinatorState.selectedDateValue)
        }
    }

    fun clearCurrentDay() = blockDelegate.clearCurrentDay(viewModelScope, coordinatorState.selectedDateValue)

    fun copyPlanFromPreviousDay() =
        blockDelegate.copyPlanFromPreviousDay(viewModelScope, coordinatorState.selectedDateValue, ::handlePlannerResult)

    /**
     * Proposes blocks for every qualifying gap (pending tasks and due habits,
     * breaks last) and stages them as AI suggestions for preview; callers open
     * the AI sheet so the user can review before anything is applied.
     */
    fun fillEmptyTime(addBreaksAutomatically: Boolean = true) =
        aiDelegate.proposeGapFill(
            scope = viewModelScope,
            date = coordinatorState.selectedDateValue,
            addBreaksAutomatically = addBreaksAutomatically,
            onResult = ::handlePlannerResult
        )

    fun markCurrentBlockMissed(blockId: String, missed: Boolean) =
        reviewDelegate.markCurrentBlockMissed(viewModelScope, blockId, missed)

    fun markBlockComplete(blockId: String) = reviewDelegate.markBlockComplete(viewModelScope, blockId)

    fun completeDayQuickTask(taskId: String) {
        viewModelScope.launch {
            runCatching { toggleTaskCompletionUseCase(taskId) }
                .onFailure { e -> appEventLog.record(AppEventCategory.ERROR, "write failed: $e") }
        }
    }

    fun completeDayQuickHabit(habitId: String) {
        viewModelScope.launch {
            runCatching {
                val habit = habitRepository.getHabitById(habitId) ?: return@launch
                completeHabitUseCase(habit, coordinatorState.selectedDate.value)
                val updated = habitRepository.getHabitById(habitId)
                    ?: habit.copy(lastCompletedDate = coordinatorState.selectedDate.value)
                habitReminderScheduler.syncUpcomingHabitReminder(updated)
            }.onFailure { e -> appEventLog.record(AppEventCategory.ERROR, "write failed: $e") }
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
            runCatching {
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
            }.onFailure { e -> appEventLog.record(AppEventCategory.ERROR, "write failed: $e") }
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

    fun onAiPlanRequested(goals: List<String>) {
        viewModelScope.launch {
            val date = coordinatorState.selectedDateValue
            // Same-day-gated focus signal: a high-distraction day nudges the planner to protect focus.
            val distractionAboveUsual = date == LocalDate.now() &&
                runCatching { trendsDelegate.isDistractionAboveUsualToday(date) }.getOrDefault(false)
            aiDelegate.requestPlan(
                viewModelScope,
                date,
                focusAwareGoals(goals, distractionAboveUsual),
                ::currentReviewSummary
            )
        }
    }

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
                    onOpenAiPlan()
                    onApplied("Proposed gap fills from recommendation")
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

    fun setInsightsPeriod(period: InsightsPeriod) {
        _insightsPeriod.value = period
    }

    fun refreshInsightsRecommendations() {
        viewModelScope.launch {
            _insightsTabState.update { it.copy(isRefreshing = true) }
            val result = runCatching {
                reviewDelegate.refreshInsightsTab(coordinatorState.selectedDateValue)
            }.getOrNull()
            _insightsTabState.update { state ->
                if (result == null) {
                    state.copy(isRefreshing = false)
                } else {
                    // copy() rather than a fresh instance so the reactively-maintained period/summary
                    // fields aren't reset by an imperative recommendations refresh.
                    state.copy(
                        reviewInsights = result.reviewInsights,
                        recommendations = result.recommendations,
                        assistSnapshot = result.assistSnapshot,
                        digest = result.digest,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun startFocusSession(blockId: String, workMinutes: Int = 0, breakMinutes: Int = 0) {
        coordinatorState.selectBlock(blockId)
        focusDelegate.startFocusSession(viewModelScope, plannerService, blockId, workMinutes, breakMinutes)
        appEventLog.record(AppEventCategory.FOCUS, "Started focus on ${focusBlockTitle(blockId)}")
    }

    /** Logs a developer-visible app event when [label] sheet/surface is opened. */
    fun recordSheetOpened(label: String) {
        appEventLog.record(AppEventCategory.SESSION, "Opened $label")
    }

    /** Clears the in-memory developer event log shown by the "View Logs" sheet. */
    fun clearAppEventLog() = appEventLog.clear()

    private fun focusBlockTitle(blockId: String): String =
        timeBlocks.value.firstOrNull { it.id == blockId }?.title?.ifBlank { "block" } ?: "block"

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
        appEventLog.record(AppEventCategory.FOCUS, "Skipped focus session")
    }

    fun finishFocusSession(note: String = "") {
        focusDelegate.finishFocusSession(viewModelScope, plannerService, note)
        coordinatorState.selectBlock(null)
        appEventLog.record(AppEventCategory.FOCUS, "Finished focus session")
    }

    /**
     * Injects an immediate break of [breakMinutes] into the running session.
     * For a flat session this promotes it to a split session automatically.
     */
    fun injectBreakNow(breakMinutes: Int) {
        focusDelegate.injectBreakNow(breakMinutes)
        appEventLog.record(AppEventCategory.FOCUS, "Injected ${breakMinutes}m break mid-session")
    }

    /**
     * Ends the current break early. Normally drops to the phase boundary so the
     * user taps "Back to focus" to resume the next interval; if the break is the
     * final phase there's nothing to resume, so the session finishes instead.
     */
    fun endBreakEarly() {
        if (focusDelegate.endBreakNow()) {
            finishFocusSession("Complete")
        } else {
            appEventLog.record(AppEventCategory.FOCUS, "Ended break early")
        }
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
                appEventLog.record(AppEventCategory.EXPORT, "Created data export ${export.file.name}")
                onReady(export.file)
            }.onFailure { error ->
                val reason = error.message ?: error::class.java.simpleName
                _dataExportState.value = DataExportState(
                    summary = "Export failed",
                    errorMessage = reason
                )
                appEventLog.record(AppEventCategory.ERROR, "Data export failed: $reason")
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

    /**
     * Deterministically repairs overlapping blocks (undoable). [onResolution] receives the outcome so
     * the caller can decide whether to escalate any unresolved (immovable) overlaps to AI guidance.
     */
    fun resolveScheduleConflicts(onResolution: (ConflictResolutionResult) -> Unit = {}) =
        blockDelegate.resolveConflicts(
            viewModelScope,
            coordinatorState.selectedDateValue,
            fromMinute = if (coordinatorState.isViewingToday) coordinatorState.currentMinuteValue else null,
            onResult = ::handlePlannerResult,
            onResolution = onResolution
        )

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

    /**
     * Asks the focus planner which of today's remaining blocks to start next, weighing the latest
     * mood/energy check-in. Invoked by the Focus tab whenever no session is running; null when the
     * day has no remaining focus-suitable blocks.
     */
    fun refreshNextFocusSuggestion() {
        viewModelScope.launch {
            val latestCheckIn = moodEnergyCheckIns.value.lastOrNull()
            _focusNextBlockSuggestion.value = runCatching {
                focusNextBlockPlanner.suggestNextBlock(
                    blocks = timeBlocksDomain.value,
                    currentMinute = currentMinute.value,
                    moodScore = latestCheckIn?.moodScore,
                    energyScore = latestCheckIn?.energyScore
                )
            }.getOrNull()
        }
    }

    /**
     * Refreshes the on-device coaching line for the running or paused focus session. Cleared
     * whenever the session is inactive so stale advice never lingers under the timer.
     */
    fun refreshFocusGuidance(remainingSeconds: Long, nextBlockTitle: String? = null) {
        val session = focusExecutionState.value
        if (session.status != FocusExecutionStatus.RUNNING && session.status != FocusExecutionStatus.PAUSED) {
            _focusGuidance.value = null
            return
        }
        viewModelScope.launch {
            val latestCheckIn = moodEnergyCheckIns.value.lastOrNull()
            _focusGuidance.value = runCatching {
                focusGuidancePlanner.suggestGuidance(
                    focusTitle = session.blockTitle.ifBlank { "Focus session" },
                    linkedBlockId = session.blockId,
                    isRunning = session.status == FocusExecutionStatus.RUNNING,
                    isPaused = session.status == FocusExecutionStatus.PAUSED,
                    timeLeft = remainingSeconds.toInt().coerceAtLeast(0),
                    totalSeconds = (session.plannedDurationMinutes * 60).coerceAtLeast(60),
                    nextBlockTitle = nextBlockTitle,
                    moodScore = latestCheckIn?.moodScore,
                    energyScore = latestCheckIn?.energyScore
                )
            }.getOrNull()
        }
    }

    fun clearFocusGuidance() {
        _focusGuidance.value = null
    }
}

/** Hint appended to AI-plan goals on a high-distraction day so the generated plan protects focus. */
internal const val FOCUS_PROTECT_GOAL_HINT =
    "Screen time shows more distraction than usual today, so protect focus: include at least one " +
        "protected focus or deep-work block and avoid many short, fragmented gaps."

/**
 * Appends [FOCUS_PROTECT_GOAL_HINT] to the AI-plan [goals] when today's distraction runs above the
 * user's usual; otherwise returns [goals] unchanged. Pure so the focus-aware planning behaviour is
 * unit-testable without constructing the view model.
 */
internal fun focusAwareGoals(goals: List<String>, distractionAboveUsual: Boolean): List<String> =
    if (distractionAboveUsual) goals + FOCUS_PROTECT_GOAL_HINT else goals
