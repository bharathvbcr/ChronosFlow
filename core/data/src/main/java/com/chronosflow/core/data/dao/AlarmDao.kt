package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronosflow.core.data.model.AlarmRequestEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm_requests WHERE scheduledFor >= :now AND deliveryState IN ('PENDING', 'SCHEDULED', 'DEGRADED') ORDER BY scheduledFor ASC")
    fun observePendingRequests(now: Instant): Flow<List<AlarmRequestEntity>>

    @Query("SELECT * FROM alarm_requests WHERE type = :type ORDER BY scheduledFor ASC")
    fun observeRequestsByType(type: String): Flow<List<AlarmRequestEntity>>

    @Query("SELECT * FROM alarm_requests WHERE id = :id")
    suspend fun getAlarmRequest(id: String): AlarmRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmRequest(request: AlarmRequestEntity)

    @Query("DELETE FROM alarm_requests WHERE scheduledFor < :threshold AND deliveryState IN ('DELIVERED', 'FAILED')")
    suspend fun deleteExpiredAlarmRequests(threshold: Instant)
}
