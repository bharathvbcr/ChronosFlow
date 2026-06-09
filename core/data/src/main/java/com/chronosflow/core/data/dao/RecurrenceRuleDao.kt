package com.chronosflow.core.data.dao

import androidx.room.*
import com.chronosflow.core.data.model.RecurrenceRuleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurrenceRuleDao {
    @Query("SELECT * FROM recurrence_rules WHERE blockId = :blockId")
    fun getRuleForBlock(blockId: String): Flow<List<RecurrenceRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RecurrenceRuleEntity)

    @Update
    suspend fun updateRule(rule: RecurrenceRuleEntity)

    @Query("DELETE FROM recurrence_rules WHERE blockId = :blockId")
    suspend fun deleteRulesForBlock(blockId: String)

    @Query("DELETE FROM recurrence_rules WHERE endsOn < :beforeDate")
    suspend fun deleteExpiredRules(beforeDate: LocalDate)
}
