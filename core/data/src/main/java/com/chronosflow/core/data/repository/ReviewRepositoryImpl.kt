package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.ReviewDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.repository.ReviewRepository
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
        reviewDao.insertDailyReview(summary.toEntity())
        reviewDao.deleteReviewInsights(summary.date)
        reviewDao.insertReviewInsights(summary.insights.map { it.toEntity(summary.date) })
    }
}
