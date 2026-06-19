package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.DayPlan
import com.ChronosFlow.VBCR.core.domain.planner.DayPlanAssembler
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ObserveDayPlanUseCase @Inject constructor(
    private val timeBlockRepository: TimeBlockRepository,
    private val reviewRepository: ReviewRepository,
    private val dayPlanAssembler: DayPlanAssembler
) {
    operator fun invoke(
        date: LocalDate,
        timezone: ZoneId = ZoneId.systemDefault()
    ): Flow<DayPlan> {
        return combine(
            timeBlockRepository.getTimeBlocksByDate(date),
            reviewRepository.observeDailyReview(date)
        ) { blocks, review ->
            dayPlanAssembler.assemble(
                date = date,
                timezone = timezone,
                blocks = blocks,
                review = review
            )
        }
    }
}
