package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.RecurrenceRuleDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.RecurrenceRule
import com.ChronosFlow.VBCR.core.domain.repository.RecurrenceRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class RecurrenceRuleRepositoryImpl @Inject constructor(
    private val recurrenceRuleDao: RecurrenceRuleDao
) : RecurrenceRuleRepository {
    override fun getRulesForBlock(blockId: String): Flow<List<RecurrenceRule>> =
        recurrenceRuleDao.getRuleForBlock(blockId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveRecurrenceRule(rule: RecurrenceRule) {
        recurrenceRuleDao.insertRule(rule.toEntity())
    }

    override suspend fun deleteRecurrenceRuleForBlock(blockId: String) {
        recurrenceRuleDao.deleteRulesForBlock(blockId)
    }

    override suspend fun deleteExpiredRules(beforeDate: LocalDate) {
        recurrenceRuleDao.deleteExpiredRules(beforeDate)
    }
}
