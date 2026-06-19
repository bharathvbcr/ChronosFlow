package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getFocusSession(id: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE state IN ('RUNNING', 'PAUSED', 'SERVICE_KILLED_RECOVERABLE') ORDER BY updatedAt DESC LIMIT 1")
    fun observeRecoverableSession(): Flow<FocusSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSessionEntity)
}
