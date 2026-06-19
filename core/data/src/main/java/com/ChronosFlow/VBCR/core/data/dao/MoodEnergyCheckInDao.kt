package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.MoodEnergyCheckInEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MoodEnergyCheckInDao {
    @Query("SELECT * FROM mood_energy_check_ins WHERE checkInDate = :date ORDER BY recordedAt DESC")
    fun observeForDate(date: LocalDate): Flow<List<MoodEnergyCheckInEntity>>

    @Query("SELECT * FROM mood_energy_check_ins WHERE checkInDate BETWEEN :start AND :end ORDER BY recordedAt DESC")
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<MoodEnergyCheckInEntity>

    @Query("SELECT * FROM mood_energy_check_ins WHERE checkInDate BETWEEN :start AND :end ORDER BY recordedAt DESC")
    fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<MoodEnergyCheckInEntity>>

    @Query("SELECT * FROM mood_energy_check_ins ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatest(): MoodEnergyCheckInEntity?

    @Query("SELECT * FROM mood_energy_check_ins WHERE blockId = :blockId ORDER BY recordedAt DESC")
    suspend fun getForBlock(blockId: String): List<MoodEnergyCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: MoodEnergyCheckInEntity)

    @Query("DELETE FROM mood_energy_check_ins WHERE id = :id")
    suspend fun deleteById(id: String)
}
