package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronosflow.core.data.model.ActualTimeSegmentEntity
import com.chronosflow.core.data.model.DailyReviewEntity
import com.chronosflow.core.data.model.ReviewInsightEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ReviewDao {
    @Query("SELECT * FROM actual_time_segments WHERE date = :date ORDER BY startInstant ASC")
    fun observeActualTimeSegments(date: LocalDate): Flow<List<ActualTimeSegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActualTimeSegment(segment: ActualTimeSegmentEntity)

    @Query("SELECT * FROM daily_reviews WHERE date = :date")
    fun observeDailyReview(date: LocalDate): Flow<DailyReviewEntity?>

    @Query("SELECT * FROM review_insights WHERE date = :date ORDER BY severity DESC, type ASC, title ASC")
    fun observeReviewInsights(date: LocalDate): Flow<List<ReviewInsightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyReview(review: DailyReviewEntity)

    @Query("DELETE FROM review_insights WHERE date = :date")
    suspend fun deleteReviewInsights(date: LocalDate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewInsights(insights: List<ReviewInsightEntity>)
}
