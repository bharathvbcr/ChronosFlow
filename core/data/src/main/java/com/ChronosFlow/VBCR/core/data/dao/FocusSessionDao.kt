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

    /**
     * Removes every focus session row except [keepId]. Each phase of a split session is persisted
     * under its own session id, so without this the finished phases linger as RUNNING rows and the
     * newest one is wrongly restored as a recoverable session after the split completes (a false
     * "Resumed your active focus session"). Pruning on each new phase keeps exactly one live row.
     */
    @Query("DELETE FROM focus_sessions WHERE id != :keepId")
    suspend fun deleteSessionsExcept(keepId: String)
}
