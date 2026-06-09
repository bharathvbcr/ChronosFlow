package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.DayPlan
import com.chronosflow.core.domain.planner.DayPlanAssembler
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
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
