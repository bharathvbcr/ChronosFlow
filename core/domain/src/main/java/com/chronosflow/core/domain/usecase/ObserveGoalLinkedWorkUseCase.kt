package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.GoalLinkedWork
import com.chronosflow.core.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the tasks and habits linked to a goal so the detail view can list them. */
class ObserveGoalLinkedWorkUseCase @Inject constructor(
    private val goalRepository: GoalRepository
) {
    operator fun invoke(goalId: String): Flow<GoalLinkedWork> =
        goalRepository.observeLinkedWork(goalId)
}
