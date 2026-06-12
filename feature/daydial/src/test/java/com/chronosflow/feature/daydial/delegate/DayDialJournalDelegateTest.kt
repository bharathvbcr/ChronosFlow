package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.domain.repository.JournalRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.SleepTrackRepository
import com.chronosflow.core.domain.usecase.RecordSleepUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDialJournalDelegateTest {
    private val journalRepository: JournalRepository = mockk(relaxed = true)
    private val sleepTrackRepository: SleepTrackRepository = mockk(relaxed = true)
    private val sleepScheduleRepository: SleepScheduleRepository = mockk()
    private val delegate = DayDialJournalDelegate(
        journalRepository,
        sleepTrackRepository,
        RecordSleepUseCase(sleepTrackRepository, sleepScheduleRepository)
    )
    private val date: LocalDate = LocalDate.of(2026, 6, 11)

    @Test
    fun `saveJournalEntry creates a trimmed primary entry`() = runTest {
        val saved = slot<JournalEntry>()
        coEvery { journalRepository.save(capture(saved)) } returns Unit

        delegate.saveJournalEntry(date, "  Good day  ", "went_well", existing = null)

        assertEquals("Good day", saved.captured.body)
        assertEquals("went_well", saved.captured.promptType)
        assertEquals(date, saved.captured.entryDate)
        assertTrue(saved.captured.isPrimary)
    }

    @Test
    fun `saveJournalEntry updates the existing entry in place`() = runTest {
        val existing = JournalEntry(
            id = "entry-1",
            entryDate = date,
            createdAt = Instant.parse("2026-06-11T18:00:00Z"),
            updatedAt = Instant.parse("2026-06-11T18:00:00Z"),
            body = "Old text"
        )
        val saved = slot<JournalEntry>()
        coEvery { journalRepository.save(capture(saved)) } returns Unit

        delegate.saveJournalEntry(date, "New text", null, existing)

        assertEquals("entry-1", saved.captured.id)
        assertEquals("New text", saved.captured.body)
        assertEquals(existing.createdAt, saved.captured.createdAt)
    }

    @Test
    fun `saveJournalEntry ignores blank bodies`() = runTest {
        delegate.saveJournalEntry(date, "   ", null, existing = null)

        coVerify(exactly = 0) { journalRepository.save(any()) }
    }

    @Test
    fun `saveSleepLog coerces quality and prefills the planned window`() = runTest {
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule(
            enabled = true,
            startMinute = 22 * 60,
            endMinute = 6 * 60
        )
        val saved = slot<SleepTrack>()
        coEvery { sleepTrackRepository.upsert(capture(saved)) } returns Unit

        delegate.saveSleepLog(
            date = date,
            quality = 6,
            actualStartMinute = 23 * 60,
            actualEndMinute = 7 * 60,
            interruptions = 1,
            windDownNotes = "  read a book  ",
            existing = null
        )

        assertEquals(5, saved.captured.sleepQuality)
        assertEquals(22 * 60, saved.captured.plannedStartMinute)
        assertEquals(6 * 60, saved.captured.plannedEndMinute)
        assertEquals(23 * 60, saved.captured.actualStartMinute)
        assertEquals("read a book", saved.captured.windDownNotes)
        assertEquals(date, saved.captured.date)
    }
}
