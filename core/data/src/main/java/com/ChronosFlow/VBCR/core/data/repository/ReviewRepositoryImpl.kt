package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.ReviewDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ReviewRepositoryImpl @Inject constructor(
    private val reviewDao: ReviewDao
) : ReviewRepository {
    override fun observeActualTimeSegments(date: LocalDate): Flow<List<ActualTimeSegment>> =
        reviewDao.observeActualTimeSegments(date).map { segments -> segments.map { it.toDomain() } }

    override fun observeActualTimeSegmentsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<ActualTimeSegment>> =
        reviewDao.observeActualTimeSegmentsByDateRange(startDate, endDate).map { segments -> segments.map { it.toDomain() } }

    override fun observeDailyReview(date: LocalDate): Flow<DailyReviewSummary?> =
        combine(
            reviewDao.observeDailyReview(date),
            reviewDao.observeReviewInsights(date)
        ) { review, insights ->
            review?.toDomain()?.copy(
                insights = insights.map { it.toDomain() }.ifEmpty { review.toDomain().insights }
            )
        }

    override suspend fun saveActualTimeSegment(segment: ActualTimeSegment) =
        reviewDao.insertActualTimeSegment(segment.toEntity())

    override suspend fun saveDailyReview(summary: DailyReviewSummary) {
        reviewDao.saveDailyReviewTransactional(
            review = summary.toEntity(),
            date = summary.date,
            insights = summary.insights.map { it.toEntity(summary.date) }
        )
    }
}
