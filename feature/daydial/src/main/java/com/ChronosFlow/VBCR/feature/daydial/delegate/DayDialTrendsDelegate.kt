package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.data.usage.ScreenTimeSyncManager
import com.ChronosFlow.VBCR.core.domain.model.AppUsageDay
import com.ChronosFlow.VBCR.core.domain.model.AppUsageSample
import com.ChronosFlow.VBCR.core.domain.model.HabitDailyCompletion
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.model.MedicationDailyAdherence
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyTrends
import com.ChronosFlow.VBCR.core.domain.model.SleepTrends
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveAppUsageTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveHabitCompletionTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMedicationAdherenceTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMoodEnergyTrendsUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveRecentJournalEntriesUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveSleepTrendUseCase
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

data class CompanionTrendSections(
    val moodTrends: MoodEnergyTrends = MoodEnergyTrends(),
    val habitTrend: List<HabitDailyCompletion> = emptyList(),
    val medicationTrend: List<MedicationDailyAdherence> = emptyList(),
    val sleepTrend: SleepTrends = SleepTrends(),
    val journalHistory: List<JournalEntry> = emptyList(),
    val screenTimeTrend: List<AppUsageDay> = emptyList(),
    val screenTimeFocusGoalMinutes: Int = 0,
    val topDistractingApps: List<AppUsageSample> = emptyList()
) {
    val isEmpty: Boolean
        get() = moodTrends.isEmpty &&
            habitTrend.isEmpty() &&
            medicationTrend.isEmpty() &&
            sleepTrend.isEmpty &&
            journalHistory.isEmpty() &&
            screenTimeTrend.none { it.totalMinutes > 0 }
}

/** Streams the companion trend sections shown by the Insights tab's trend cards. */
class DayDialTrendsDelegate @Inject constructor(
    private val observeMoodEnergyTrendsUseCase: ObserveMoodEnergyTrendsUseCase,
    private val observeHabitCompletionTrendUseCase: ObserveHabitCompletionTrendUseCase,
    private val observeMedicationAdherenceTrendUseCase: ObserveMedicationAdherenceTrendUseCase,
    private val observeSleepTrendUseCase: ObserveSleepTrendUseCase,
    private val observeRecentJournalEntriesUseCase: ObserveRecentJournalEntriesUseCase,
    private val observeAppUsageTrendUseCase: ObserveAppUsageTrendUseCase,
    private val screenTimeSyncManager: ScreenTimeSyncManager
) {
    /**
     * Read-only check of whether today's distracting screen time runs above the user's usual level,
     * used to make the AI day-plan focus-aware. Delegates to the screen-time store (false when screen
     * time isn't tracked), so the planner can ask without taking a direct dependency on it.
     */
    suspend fun isDistractionAboveUsualToday(today: LocalDate = LocalDate.now()): Boolean =
        screenTimeSyncManager.isDistractionAboveUsualToday(today)

    /**
     * Emits a fresh snapshot whenever any underlying source for the window changes — a background
     * Health Connect import, a just-saved journal entry, a completed habit, or a logged dose all
     * surface live without a manual reload. [today] anchors the trailing window; pass the live
     * current-date flow so the window rolls forward when the day changes mid-session.
     */
    fun observeTrends(windowDays: Int, today: LocalDate = LocalDate.now()): Flow<CompanionTrendSections> {
        // combine() only types up to five flows; nest the screen-time stream onto the rest so each
        // section keeps its own type rather than collapsing to an untyped Array.
        val companionSections = combine(
            observeMoodEnergyTrendsUseCase(windowDays, today),
            observeHabitCompletionTrendUseCase(windowDays, today),
            observeMedicationAdherenceTrendUseCase(windowDays, today),
            observeSleepTrendUseCase(windowDays, today),
            observeRecentJournalEntriesUseCase(windowDays, today)
        ) { moodTrends, habitTrend, medicationTrend, sleepTrend, journalHistory ->
            CompanionTrendSections(
                moodTrends = moodTrends,
                habitTrend = habitTrend,
                medicationTrend = medicationTrend,
                sleepTrend = sleepTrend,
                journalHistory = journalHistory
            )
        }
        return combine(
            companionSections,
            observeAppUsageTrendUseCase(windowDays, today),
            screenTimeSyncManager.observeFocusGoalMinutes(),
            screenTimeSyncManager.observeTopDistractingApps(windowDays, today)
        ) { sections, screenTimeTrend, focusGoal, topDistracting ->
            sections.copy(
                screenTimeTrend = screenTimeTrend,
                screenTimeFocusGoalMinutes = focusGoal,
                topDistractingApps = topDistracting
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }
}
