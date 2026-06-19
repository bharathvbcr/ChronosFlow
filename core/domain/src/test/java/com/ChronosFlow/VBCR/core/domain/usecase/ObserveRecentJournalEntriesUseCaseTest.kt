package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ObserveRecentJournalEntriesUseCaseTest {
    private val repository: JournalRepository = mockk()
    private val useCase = ObserveRecentJournalEntriesUseCase(repository)
    private val today = LocalDate.of(2026, 6, 11)

    @Test
    fun `orders by date desc then primary then created desc`() = runTest {
        val start = today.minusDays(13)
        val older = entry("old", today.minusDays(1), primary = true, created = "2026-06-10T20:00:00Z")
        val todaySecondary = entry("t2", today, primary = false, created = "2026-06-11T21:00:00Z")
        val todayPrimary = entry("t1", today, primary = true, created = "2026-06-11T19:00:00Z")
        every { repository.observeForDateRange(start, today) } returns flowOf(
            listOf(older, todaySecondary, todayPrimary)
        )

        val result = useCase(windowDays = 14, today = today).first()

        // Newest day first; within a day the primary reflection leads.
        assertEquals(listOf("t1", "t2", "old"), result.map { it.id })
    }

    @Test
    fun `non-positive window short-circuits to empty`() = runTest {
        val result = useCase(windowDays = 0, today = today).first()
        assertTrue(result.isEmpty())
    }

    private fun entry(id: String, date: LocalDate, primary: Boolean, created: String) = JournalEntry(
        id = id,
        entryDate = date,
        createdAt = Instant.parse(created),
        updatedAt = Instant.parse(created),
        body = "body $id",
        isPrimary = primary
    )
}
