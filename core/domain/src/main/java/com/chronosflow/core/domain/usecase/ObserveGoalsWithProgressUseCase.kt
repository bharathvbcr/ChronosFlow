package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.GoalWithProgress
import com.chronosflow.core.domain.repository.GoalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveGoalsWithProgressUseCase @Inject constructor(
    private val goalRepository: GoalRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<GoalWithProgress>> =
        goalRepository.observeGoals().flatMapLatest { goals ->
            if (goals.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    goals.map { goal ->
                        goalRepository.observeDerivedProgress(goal.id)
                            .map { derived -> GoalWithProgress(goal, derived) }
                    }
                ) { it.toList() }
            }
        }
}
