package com.chronosflow.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class WeeklyReviewRollup(
    val weekStart: LocalDate,
    val daysWithPlans: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val missedMinutes: Int,
    val completedBlockCount: Int,
    val missedBlockCount: Int
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
    reviewRepository: ReviewRepository
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
                        missedBlockCount = planned.sumOf { it.missedBlockCount }
                    )
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
