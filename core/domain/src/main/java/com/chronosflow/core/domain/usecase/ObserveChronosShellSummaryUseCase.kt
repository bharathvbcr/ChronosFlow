package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.ChronosShellSummary
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.repository.FocusSessionRepository
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveChronosShellSummaryUseCase @Inject constructor(
    private val timeBlockRepository: TimeBlockRepository,
    private val reviewRepository: ReviewRepository,
    private val focusSessionRepository: FocusSessionRepository
) {
    operator fun invoke(
        date: LocalDate,
        currentMinuteOfDay: Flow<Int>
    ): Flow<ChronosShellSummary> = combine(
        timeBlockRepository.getTimeBlocksByDate(date),
        reviewRepository.observeDailyReview(date),
        focusSessionRepository.observeRecoverableSession(),
        currentMinuteOfDay
    ) { blocks, review, focusSession, currentMinute ->
        ChronosShellSummary(
            missedBlocksCount = blocks.count { it.isMissedBy(currentMinute) },
            focusActive = focusSession is FocusSessionState.Running || focusSession is FocusSessionState.Paused,
            unreadInsightsCount = review?.insights.orEmpty().count {
                it.severity != ReviewInsightSeverity.INFO
            }
        )
    }
}

private fun TimeBlock.isMissedBy(currentMinute: Int): Boolean {
    val blockEnd = startMinuteOfDay + durationMinutes
    return blockEnd <= currentMinute &&
        actualStartMinuteOfDay == null &&
        actualEndMinuteOfDay == null
}
