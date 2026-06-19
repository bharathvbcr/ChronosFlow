package com.ChronosFlow.VBCR.core.data.repository

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.dao.RecurrenceRuleDao
import com.ChronosFlow.VBCR.core.data.model.RecurrenceRuleEntity
import com.ChronosFlow.VBCR.core.domain.model.RecurrenceRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceRuleRepositoryImplTest {
    private val dao: RecurrenceRuleDao = mockk()
    private val repository = RecurrenceRuleRepositoryImpl(dao)

    @Test
    fun `observe maps recurrence entities to domain`() = runTest {
        val expectedStart = LocalDate.parse("2026-01-01")
        val expectedEnd = LocalDate.parse("2026-02-01")
        val entity = RecurrenceRuleEntity(
            id = "rule-1",
            blockId = "block-1",
            pattern = "WEEKLY",
            intervalWeeks = 2,
            startsOn = expectedStart.toString(),
            endsOn = expectedEnd.toString(),
            maxOccurrences = 3,
            weekdays = "MON,WED,FRI",
            createdAt = 1L,
            updatedAt = 2L
        )
        every { dao.getRuleForBlock("block-1") } returns flowOf(listOf(entity))

        repository.getRulesForBlock("block-1").test {
            val rules = awaitItem()
            assertEquals(1, rules.size)
            assertEquals("rule-1", rules[0].id)
            assertEquals(expectedStart, rules[0].startsOn)
            assertEquals(expectedEnd, rules[0].endsOn)
            awaitComplete()
        }
    }

    @Test
    fun `save converts domain rule into dao entity`() = runTest {
        coEvery { dao.insertRule(any()) } returns Unit

        repository.saveRecurrenceRule(
            RecurrenceRule(
                id = "rule-1",
                blockId = "block-1",
                pattern = "WEEKLY",
                intervalWeeks = 2,
                startsOn = LocalDate.parse("2026-01-01"),
                endsOn = null,
                maxOccurrences = null,
                weekdays = null,
                createdAt = null,
                updatedAt = null
            )
        )

        coVerify {
            dao.insertRule(
                match {
                    it.id == "rule-1" &&
                        it.blockId == "block-1" &&
                        it.pattern == "WEEKLY"
                }
            )
        }
    }

    @Test
    fun `delete methods forward to dao`() = runTest {
        val beforeDate = LocalDate.parse("2026-01-01")
        coEvery { dao.deleteRulesForBlock("block-1") } returns Unit
        coEvery { dao.deleteExpiredRules(beforeDate) } returns Unit

        repository.deleteRecurrenceRuleForBlock("block-1")
        repository.deleteExpiredRules(beforeDate)

        coVerify {
            dao.deleteRulesForBlock("block-1")
            dao.deleteExpiredRules(beforeDate)
        }
    }
}

