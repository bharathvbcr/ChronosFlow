package com.ChronosFlow.VBCR.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.ai.HabitAssistPlanner
import com.ChronosFlow.VBCR.core.ai.HabitRepairAssistPlanner
import com.ChronosFlow.VBCR.core.ai.RoutineAssistSource
import com.ChronosFlow.VBCR.core.ai.HabitAssistRequest
import com.ChronosFlow.VBCR.core.ai.HabitAssistSuggestion
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.genai.refreshAssistUiSnapshot
import com.ChronosFlow.VBCR.core.domain.model.AppLaunchTarget
import com.ChronosFlow.VBCR.core.domain.model.Goal
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.model.HabitSchedule
import com.ChronosFlow.VBCR.core.domain.model.buildLegacyHabitSchedule
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.PlannerPreferencesRepository
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteHabitUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.GetActiveHabitsUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveHabitCompletionTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveHabitStreaksUseCase
import com.ChronosFlow.VBCR.core.notifications.HabitReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitRepairSuggestion(
    val habit: Habit,
    val suggestedStartMinute: Int,
    val suggestedEndMinute: Int,
    val reason: String,
    val source: RoutineAssistSource = RoutineAssistSource.LOCAL
)

data class HabitAssistUiState(
    val isLoading: Boolean = false,
    val suggestions: List<HabitAssistSuggestion> = emptyList(),
    val message: String? = null,
    val assistSnapshot: GenAiAssistUiSnapshot? = null
)

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val goalRepository: GoalRepository,
    private val plannerPreferencesRepository: PlannerPreferencesRepository,
    getActiveHabitsUseCase: GetActiveHabitsUseCase,
    observeHabitStreaksUseCase: ObserveHabitStreaksUseCase,
    observeHabitCompletionTrendUseCase: ObserveHabitCompletionTrendUseCase,
    private val completeHabitUseCase: CompleteHabitUseCase,
    private val habitAssistPlanner: HabitAssistPlanner,
    private val habitRepairAssistPlanner: HabitRepairAssistPlanner,
    private val habitReminderScheduler: HabitReminderScheduler,
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) : ViewModel() {
    val allHabits = habitRepository.observeHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<Goal>> = goalRepository.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeHabits = getActiveHabitsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _repairSuggestions = MutableStateFlow<List<HabitRepairSuggestion>>(emptyList())
    val repairSuggestions = _repairSuggestions.asStateFlow()

    private val _repairAssistSnapshot = MutableStateFlow<GenAiAssistUiSnapshot?>(null)
    val repairAssistSnapshot = _repairAssistSnapshot.asStateFlow()

    init {
        viewModelScope.launch {
            activeHabits.collect { habits ->
                refreshRepairSuggestions(habits)
            }
        }
    }

    /** Emits the current date and refreshes every minute so midnight rollovers are picked up. */
    val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now())

    val streaks = observeHabitStreaksUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Per-day completed/missed counts over the trailing 14 days, oldest first. */
    val completionTrend = observeHabitCompletionTrendUseCase(windowDays = 14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recentHistoryTemplateIds = MutableStateFlow(plannerPreferencesRepository.getRecentHabitTemplateIds())
    val recentHistoryTemplateIds = _recentHistoryTemplateIds.asStateFlow()

    private val _assistState = MutableStateFlow(HabitAssistUiState())
    val assistState = _assistState.asStateFlow()

    fun addHabit(
        title: String,
        cadence: String = "Daily",
        windowStartMinute: Int = 8 * 60,
        windowEndMinute: Int = 20 * 60,
        difficulty: Int = 2,
        isBundled: Boolean = false,
        schedule: HabitSchedule? = null,
        launchTarget: AppLaunchTarget? = null,
        goalId: String? = null
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val (safeStart, safeEnd) = normalizedHabitWindow(windowStartMinute, windowEndMinute)
            val habitId = UUID.randomUUID().toString()
            val habit = Habit(
                id = habitId,
                title = title.trim(),
                cadence = cadence,
                windowStartMinute = safeStart,
                windowEndMinute = safeEnd,
                difficulty = difficulty.coerceIn(1, 5),
                isBundled = isBundled,
                streakCount = 0,
                lastCompletedDate = null,
                isActive = true,
                launchTarget = launchTarget,
                goalId = goalId,
                schedule = schedule?.copy(
                    id = schedule.id.ifBlank { "schedule-$habitId" },
                    habitId = habitId,
                    targetStartMinute = safeStart,
                    targetEndMinute = safeEnd,
                    plannerVisible = isBundled
                ) ?: buildLegacyHabitSchedule(
                    habitId = habitId,
                    cadence = cadence,
                    windowStartMinute = safeStart,
                    windowEndMinute = safeEnd,
                    plannerVisible = isBundled
                )
            )
            habitRepository.saveHabit(habit)
            habitReminderScheduler.syncUpcomingHabitReminder(habit)
        }
    }

    fun updateHabit(
        habit: Habit,
        title: String,
        cadence: String,
        windowStartMinute: Int,
        windowEndMinute: Int,
        difficulty: Int,
        isBundled: Boolean,
        schedule: HabitSchedule? = null,
        launchTarget: AppLaunchTarget? = null,
        goalId: String? = null
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val (safeStart, safeEnd) = normalizedHabitWindow(windowStartMinute, windowEndMinute)
            val updated = habit.copy(
                title = title.trim(),
                cadence = cadence.trim().ifBlank { habit.cadence },
                windowStartMinute = safeStart,
                windowEndMinute = safeEnd,
                difficulty = difficulty.coerceIn(1, 5),
                isBundled = isBundled,
                launchTarget = launchTarget,
                goalId = goalId,
                schedule = schedule?.copy(
                    id = schedule.id.ifBlank { habit.schedule?.id ?: "schedule-${habit.id}" },
                    habitId = habit.id,
                    targetStartMinute = safeStart,
                    targetEndMinute = safeEnd,
                    plannerVisible = isBundled
                ) ?: buildLegacyHabitSchedule(
                    habitId = habit.id,
                    cadence = cadence,
                    windowStartMinute = safeStart,
                    windowEndMinute = safeEnd,
                    plannerVisible = isBundled
                )
            )
            habitRepository.saveHabit(updated)
            habitReminderScheduler.syncUpcomingHabitReminder(updated)
        }
    }

    fun completeHabit(habit: Habit, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            completeHabitUseCase(habit, date)
            val updated = habitRepository.getHabitById(habit.id) ?: habit.copy(lastCompletedDate = date)
            habitReminderScheduler.syncUpcomingHabitReminder(updated)
        }
    }

    fun pauseHabit(habit: Habit, days: Int, reason: String? = null) {
        viewModelScope.launch {
            val until = LocalDate.now().plusDays(days.toLong().coerceAtLeast(1))
            val schedule = (habit.schedule ?: buildLegacyHabitSchedule(
                habitId = habit.id,
                cadence = habit.cadence,
                windowStartMinute = habit.windowStartMinute,
                windowEndMinute = habit.windowEndMinute,
                plannerVisible = habit.isBundled
            )).copy(pausedUntil = until, skipDate = null, deferUntilMinuteOfDay = null)
            val updated = habit.copy(schedule = schedule)
            habitRepository.saveHabit(updated)
            habitRepository.addHabitEvent(
                HabitEvent(
                    id = UUID.randomUUID().toString(),
                    habitId = habit.id,
                    type = HabitEventType.PAUSED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    reason = reason ?: "Paused for $days day(s)",
                    startMinuteOfDay = null,
                    endMinuteOfDay = null
                )
            )
            habitReminderScheduler.syncUpcomingHabitReminder(updated)
        }
    }

    fun resumeHabit(habit: Habit, reason: String? = null) {
        viewModelScope.launch {
            val schedule = (habit.schedule ?: buildLegacyHabitSchedule(
                habitId = habit.id,
                cadence = habit.cadence,
                windowStartMinute = habit.windowStartMinute,
                windowEndMinute = habit.windowEndMinute,
                plannerVisible = habit.isBundled
            )).copy(pausedUntil = null, skipDate = null, deferUntilMinuteOfDay = null)
            val updated = habit.copy(schedule = schedule)
            habitRepository.saveHabit(updated)
            habitRepository.addHabitEvent(
                HabitEvent(
                    id = UUID.randomUUID().toString(),
                    habitId = habit.id,
                    type = HabitEventType.RESUMED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    reason = reason ?: "Resumed",
                    startMinuteOfDay = null,
                    endMinuteOfDay = null
                )
            )
            habitReminderScheduler.syncUpcomingHabitReminder(updated)
        }
    }

    fun skipHabitToday(habit: Habit, reason: String? = null) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val schedule = (habit.schedule ?: buildLegacyHabitSchedule(
                habitId = habit.id,
                cadence = habit.cadence,
                windowStartMinute = habit.windowStartMinute,
                windowEndMinute = habit.windowEndMinute,
                plannerVisible = habit.isBundled
            )).copy(skipDate = today)
            val updated = habit.copy(schedule = schedule)
            habitRepository.saveHabit(updated)
            habitRepository.addHabitEvent(
                HabitEvent(
                    id = UUID.randomUUID().toString(),
                    habitId = habit.id,
                    type = HabitEventType.SKIPPED,
                    eventDate = today,
                    recordedAt = Instant.now(),
                    reason = reason ?: "Skipped today",
                    startMinuteOfDay = null,
                    endMinuteOfDay = null
                )
            )
            habitReminderScheduler.syncUpcomingHabitReminder(updated)
        }
    }

    fun deferHabit(habit: Habit, minutes: Int, reason: String? = null) {
        viewModelScope.launch {
            val nowMinute = currentMinuteOfDay()
            val schedule = (habit.schedule ?: buildLegacyHabitSchedule(
                habitId = habit.id,
                cadence = habit.cadence,
                windowStartMinute = habit.windowStartMinute,
                windowEndMinute = habit.windowEndMinute,
                plannerVisible = habit.isBundled
            )).copy(deferUntilMinuteOfDay = (nowMinute + minutes).coerceAtMost(23 * 60 + 59))
            val updated = habit.copy(schedule = schedule)
            habitRepository.saveHabit(updated)
            habitRepository.addHabitEvent(
                HabitEvent(
                    id = UUID.randomUUID().toString(),
                    habitId = habit.id,
                    type = HabitEventType.DEFERRED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    reason = reason ?: "Deferred by $minutes minutes",
                    startMinuteOfDay = schedule.deferUntilMinuteOfDay,
                    endMinuteOfDay = null
                )
            )
            habitReminderScheduler.syncUpcomingHabitReminder(updated)
        }
    }

    fun archiveHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.saveHabit(habit.copy(isActive = false))
            habitReminderScheduler.cancelUpcomingHabitReminders(habit.id)
        }
    }

    fun duplicateHabit(habit: Habit) {
        viewModelScope.launch {
            val (safeStart, safeEnd) = normalizedHabitWindow(habit.windowStartMinute, habit.windowEndMinute)
            val duplicatedId = UUID.randomUUID().toString()
            val duplicated = habit.copy(
                id = duplicatedId,
                title = "${habit.title} (copy)",
                windowStartMinute = safeStart,
                windowEndMinute = safeEnd,
                streakCount = 0,
                lastCompletedDate = null,
                isActive = true,
                schedule = habit.schedule?.copy(
                    id = "schedule-$duplicatedId",
                    habitId = duplicatedId,
                    targetStartMinute = safeStart,
                    targetEndMinute = safeEnd,
                    pausedUntil = null,
                    skipDate = null,
                    deferUntilMinuteOfDay = null
                )
            )
            habitRepository.saveHabit(duplicated)
            habitReminderScheduler.syncUpcomingHabitReminder(duplicated)
        }
    }

    fun rememberHistoryTemplateSelection(selectedId: String) {
        val updated = buildList {
            add(selectedId)
            _recentHistoryTemplateIds.value.filterNot { it == selectedId }.take(4).forEach(::add)
        }
        _recentHistoryTemplateIds.value = updated
        plannerPreferencesRepository.saveRecentHabitTemplateIds(updated)
    }

    fun requestHabitAssist(request: HabitAssistRequest) {
        viewModelScope.launch {
            val snapshot = runCatching { genAiAssistCoordinator.refreshAssistUiSnapshot() }.getOrNull()
            _assistState.value = HabitAssistUiState(isLoading = true, assistSnapshot = snapshot)
            val suggestions = runCatching { habitAssistPlanner.suggest(request) }
                .getOrElse { throwable ->
                    _assistState.value = HabitAssistUiState(
                        message = throwable.message ?: "No suggestions available",
                        assistSnapshot = snapshot
                    )
                    return@launch
                }
            // On-device proofread of the captured habit name (ML Kit GenAI Proofreading), surfaced as
            // an extra Title suggestion alongside the generated ones. Best-effort: failures are ignored.
            val refinedTitle = runCatching {
                request.title.takeIf { it.isNotBlank() }?.let { habitAssistPlanner.refineTitle(it) }
            }.getOrNull()
            val merged = (listOfNotNull(refinedTitle) + suggestions).distinctBy { it.id }
            _assistState.value = if (merged.isNotEmpty()) {
                HabitAssistUiState(
                    suggestions = merged,
                    assistSnapshot = snapshot,
                    message = snapshot?.takeIf { it.aiDisabled }?.let { GenAiAssistCopy.disabledAssistMessage() }
                )
            } else {
                HabitAssistUiState(
                    message = "No suggestions available",
                    assistSnapshot = snapshot
                )
            }
        }
    }

    fun clearHabitAssist() {
        _assistState.value = HabitAssistUiState()
    }

    internal suspend fun refreshRepairSuggestions(habits: List<Habit>) {
        _repairAssistSnapshot.value = runCatching { genAiAssistCoordinator.refreshAssistUiSnapshot() }.getOrNull()
        val today = LocalDate.now()
        val results = habitRepairAssistPlanner.suggestRepairs(habits, today, currentMinuteOfDay())
        _repairSuggestions.value = results.mapNotNull { result ->
            habits.firstOrNull { it.id == result.habitId }?.let { habit ->
                HabitRepairSuggestion(
                    habit = habit,
                    suggestedStartMinute = result.suggestedStartMinute,
                    suggestedEndMinute = result.suggestedEndMinute,
                    reason = result.reason,
                    source = result.source
                )
            }
        }
    }
}

private fun normalizedHabitWindow(startMinute: Int, endMinute: Int): Pair<Int, Int> {
    val safeStart = startMinute.coerceIn(0, 1425)
    return safeStart to endMinute.coerceIn(safeStart + 15, 1440)
}

private fun currentMinuteOfDay(): Int {
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute
}
