package com.ChronosFlow.VBCR.core.data.repository

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.dao.JournalAttachmentDao
import com.ChronosFlow.VBCR.core.data.dao.JournalEntryDao
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class JournalRepositoryImplTest {
    private val dao: JournalEntryDao = mockk()
    private val attachmentDao: JournalAttachmentDao = mockk(relaxed = true)
    private val repository = JournalRepositoryImpl(dao, attachmentDao)

    @Test
    fun `observe for date range maps entities to domain`() = runTest {
        val start = LocalDate.parse("2026-06-09")
        val end = LocalDate.parse("2026-06-11")
        every { dao.observeForDateRange(start, end) } returns flowOf(listOf(entry("j-1").toEntity()))

        repository.observeForDateRange(start, end).test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            assertEquals("j-1", entries[0].id)
            assertEquals("reflection", entries[0].body)
            awaitComplete()
        }
    }

    @Test
    fun `save primary entry demotes other entries for the day`() = runTest {
        val entry = entry("j-1", primary = true)
        coEvery { dao.insert(any()) } returns Unit
        coEvery { dao.clearPrimaryForDate(entry.entryDate, entry.id) } returns Unit

        repository.save(entry)

        coVerify { dao.insert(any()) }
        coVerify { dao.clearPrimaryForDate(entry.entryDate, "j-1") }
    }

    private fun entry(id: String, primary: Boolean = true) = JournalEntry(
        id = id,
        entryDate = LocalDate.parse("2026-06-11"),
        createdAt = Instant.parse("2026-06-11T19:00:00Z"),
        updatedAt = Instant.parse("2026-06-11T19:00:00Z"),
        body = "reflection",
        isPrimary = primary
    )
}
