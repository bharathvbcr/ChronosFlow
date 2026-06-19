package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.usecase.RecordSleepUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
