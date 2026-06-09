package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.RecurrenceRule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RecurrenceRuleRepository {
    fun getRulesForBlock(blockId: String): Flow<List<RecurrenceRule>>
    suspend fun saveRecurrenceRule(rule: RecurrenceRule)
    suspend fun deleteRecurrenceRuleForBlock(blockId: String)
    suspend fun deleteExpiredRules(beforeDate: LocalDate)
}
