package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import java.time.LocalDate
import javax.inject.Inject

class CompleteRoutineForDateUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    suspend operator fun invoke(routineId: String, date: LocalDate) {
        routineRepository.markCompleted(routineId, date)
    }
}
