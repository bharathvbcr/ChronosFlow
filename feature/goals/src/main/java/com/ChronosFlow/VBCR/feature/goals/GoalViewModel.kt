package com.ChronosFlow.VBCR.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.domain.model.Goal
import com.ChronosFlow.VBCR.core.domain.model.GoalLinkedWork
import com.ChronosFlow.VBCR.core.domain.model.GoalWithProgress
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveGoalLinkedWorkUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveGoalsWithProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    observeGoalsWithProgressUseCase: ObserveGoalsWithProgressUseCase,
    private val observeGoalLinkedWorkUseCase: ObserveGoalLinkedWorkUseCase
) : ViewModel() {

    val goals: StateFlow<List<GoalWithProgress>> = observeGoalsWithProgressUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The goal whose detail sheet is open; null collapses the linked-work stream to empty.
    private val detailGoalId = MutableStateFlow<String?>(null)

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()
    fun clearError() { _errorState.value = null }

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailLinkedWork: StateFlow<GoalLinkedWork> = detailGoalId
        .flatMapLatest { id ->
            if (id == null) flowOf(GoalLinkedWork()) else observeGoalLinkedWorkUseCase(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalLinkedWork())

    fun openGoalDetail(goalId: String) {
        detailGoalId.value = goalId
    }

    fun closeGoalDetail() {
        detailGoalId.value = null
    }

    fun addGoal(
        title: String,
        description: String?,
        category: String,
        targetValue: Int,
        targetDate: LocalDate?
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                goalRepository.saveGoal(
                    Goal(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        description = description?.trim()?.ifBlank { null },
                        category = category.trim().ifBlank { DEFAULT_CATEGORY },
                        targetValue = targetValue.coerceAtLeast(1),
                        startDate = LocalDate.now(),
                        targetDate = targetDate,
                        progressValue = 0,
                        isCompleted = false
                    )
                )
            }.onFailure { e -> _errorState.value = e.message }
        }
    }

    fun updateGoal(
        original: Goal,
        title: String,
        description: String?,
        category: String,
        targetValue: Int,
        targetDate: LocalDate?
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                goalRepository.saveGoal(
                    original.copy(
                        title = title.trim(),
                        description = description?.trim()?.ifBlank { null },
                        category = category.trim().ifBlank { DEFAULT_CATEGORY },
                        targetValue = targetValue.coerceAtLeast(1),
                        targetDate = targetDate
                    )
                )
            }.onFailure { e -> _errorState.value = e.message }
        }
    }

    /** Bumps the manual progress contribution by [delta], clamped to 0..target. */
    fun adjustProgress(goal: Goal, delta: Int) {
        viewModelScope.launch {
            runCatching {
                val next = (goal.progressValue + delta).coerceIn(0, goal.targetValue)
                goalRepository.saveGoal(goal.copy(progressValue = next))
            }.onFailure { e -> _errorState.value = e.message }
        }
    }

    fun toggleComplete(goal: Goal) {
        viewModelScope.launch {
            runCatching {
                goalRepository.saveGoal(goal.copy(isCompleted = !goal.isCompleted))
            }.onFailure { e -> _errorState.value = e.message }
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            runCatching {
                goalRepository.deleteGoal(goal)
            }.onFailure { e -> _errorState.value = e.message }
        }
    }

    companion object {
        const val DEFAULT_CATEGORY = "Personal"
        val CATEGORY_OPTIONS = listOf("Personal", "Health", "Work", "Learning", "Finance", "Habits")
    }
}
