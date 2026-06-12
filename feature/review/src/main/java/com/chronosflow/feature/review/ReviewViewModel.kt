package com.chronosflow.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.ReviewAssistPlanner
import com.chronosflow.core.ai.WeeklyReviewContext
import com.chronosflow.core.data.assist.ProactiveAssistCache
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class WeeklyReviewRollup(
    val weekStart: LocalDate,
    val daysWithPlans: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val missedMinutes: Int,
    val completedBlockCount: Int,
    val missedBlockCount: Int,
    val driftMinutes: Int = 0,
    val insightCount: Int = 0
) {
    val executionPercent: Int
        get() = if (plannedMinutes > 0) {
            (actualMinutes * 100 / plannedMinutes).coerceIn(0, 100)
        } else {
            0
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    reviewRepository: ReviewRepository,
    private val reviewAssistPlanner: ReviewAssistPlanner,
    private val proactiveAssistCache: ProactiveAssistCache
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val review: StateFlow<DailyReviewSummary?> = _selectedDate
        .flatMapLatest { date -> reviewRepository.observeDailyReview(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weeklyRollup: StateFlow<WeeklyReviewRollup?> = _selectedDate
        .flatMapLatest { date ->
            val weekStart = date.with(DayOfWeek.MONDAY)
            val dayFlows = (0L..6L).map { offset ->
                reviewRepository.observeDailyReview(weekStart.plusDays(offset))
            }
            combine(dayFlows) { summaries ->
                val planned = summaries.filterNotNull().filter { it.plannedMinutes > 0 }
                if (planned.isEmpty()) {
                    null
                } else {
                    WeeklyReviewRollup(
                        weekStart = weekStart,
                        daysWithPlans = planned.size,
                        plannedMinutes = planned.sumOf { it.plannedMinutes },
                        actualMinutes = planned.sumOf { it.actualMinutes.coerceAtLeast(0) },
                        missedMinutes = planned.sumOf { it.missedMinutes.coerceAtLeast(0) },
                        completedBlockCount = planned.sumOf { it.completedBlockCount },
                        missedBlockCount = planned.sumOf { it.missedBlockCount },
                        driftMinutes = planned.sumOf { it.driftMinutes },
                        insightCount = planned.sumOf { it.insights.size }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val coachNarrative: StateFlow<AssistNarrative?> = combine(review, weeklyRollup) { summary, rollup ->
        summary to rollup
    }
        .distinctUntilChanged()
        .mapLatest { (summary, rollup) ->
            if (summary == null || summary.plannedMinutes <= 0) {
                null
            } else {
                reviewAssistPlanner.suggestSummary(summary, rollup.toWeeklyContext(summary))
                    .also { narrative ->
                        // Today's coach line feeds passive surfaces (widget).
                        val today = LocalDate.now()
                        if (_selectedDate.value == today) {
                            proactiveAssistCache.putDailyCoachLine(
                                date = today,
                                headline = narrative.headline,
                                nextStep = narrative.nextStep,
                                source = narrative.source.name
                            )
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousDay() {
        _selectedDate.update { it.minusDays(1) }
    }

    fun nextDay() {
        _selectedDate.update { it.plusDays(1) }
    }

    fun jumpToToday() {
        _selectedDate.value = LocalDate.now()
    }
}

private fun WeeklyReviewRollup?.toWeeklyContext(today: DailyReviewSummary): WeeklyReviewContext =
    if (this == null) {
        WeeklyReviewContext(
            daysReviewed = 1,
            plannedMinutes = today.plannedMinutes,
            actualMinutes = today.actualMinutes,
            missedMinutes = today.missedMinutes,
            driftMinutes = today.driftMinutes,
            completedBlocks = today.completedBlockCount,
            missedBlocks = today.missedBlockCount,
            insightCount = today.insights.size
        )
    } else {
        WeeklyReviewContext(
            daysReviewed = daysWithPlans,
            plannedMinutes = plannedMinutes,
            actualMinutes = actualMinutes,
            missedMinutes = missedMinutes,
            driftMinutes = driftMinutes,
            completedBlocks = completedBlockCount,
            missedBlocks = missedBlockCount,
            insightCount = insightCount
        )
    }
