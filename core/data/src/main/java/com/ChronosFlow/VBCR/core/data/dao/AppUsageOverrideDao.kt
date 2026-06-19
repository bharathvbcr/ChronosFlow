package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.AppUsageOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageOverrideDao {
    @Query("SELECT * FROM app_usage_overrides")
    fun observeAll(): Flow<List<AppUsageOverrideEntity>>

    @Query("SELECT * FROM app_usage_overrides")
    suspend fun getAll(): List<AppUsageOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: AppUsageOverrideEntity)

    @Query("DELETE FROM app_usage_overrides WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}
