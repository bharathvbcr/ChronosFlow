package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.model.DailyReviewSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ReviewRepository {
    fun observeActualTimeSegments(date: LocalDate): Flow<List<ActualTimeSegment>>
    fun observeDailyReview(date: LocalDate): Flow<DailyReviewSummary?>
    suspend fun saveActualTimeSegment(segment: ActualTimeSegment)
    suspend fun saveDailyReview(summary: DailyReviewSummary)
}
