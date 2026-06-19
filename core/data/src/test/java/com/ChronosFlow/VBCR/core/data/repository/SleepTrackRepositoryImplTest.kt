package com.ChronosFlow.VBCR.core.data.repository

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.dao.SleepTrackDao
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.SleepSource
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SleepTrackRepositoryImplTest {
    private val dao: SleepTrackDao = mockk()
    private val repository = SleepTrackRepositoryImpl(dao)

    @Test
    fun `observe for date range maps entities to domain`() = runTest {
        val start = LocalDate.parse("2026-06-09")
        val end = LocalDate.parse("2026-06-11")
        val track = SleepTrack(
            id = "s-1",
            date = LocalDate.parse("2026-06-11"),
            plannedStartMinute = null,
            plannedEndMinute = null,
            actualStartMinute = 1380,
            actualEndMinute = 420,
            sleepQuality = 4,
            windDownNotes = "read a book",
            interruptedCount = 1,
            source = SleepSource.MANUAL
        )
        every { dao.observeForDateRange(start, end) } returns flowOf(listOf(track.toEntity()))

        repository.observeForDateRange(start, end).test {
            val tracks = awaitItem()
            assertEquals(1, tracks.size)
            assertEquals("s-1", tracks[0].id)
            assertEquals(4, tracks[0].sleepQuality)
            assertEquals(SleepSource.MANUAL, tracks[0].source)
            awaitComplete()
        }
    }
}
