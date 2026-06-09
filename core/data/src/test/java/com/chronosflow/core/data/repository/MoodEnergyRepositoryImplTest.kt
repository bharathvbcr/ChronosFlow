package com.chronosflow.core.data.repository

import app.cash.turbine.test
import com.chronosflow.core.data.dao.MoodEnergyCheckInDao
import com.chronosflow.core.data.model.MoodEnergyCheckInEntity
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
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
import java.time.ZoneId

class MoodEnergyRepositoryImplTest {
    private val dao: MoodEnergyCheckInDao = mockk()
    private val repository = MoodEnergyRepositoryImpl(dao)

    @Test
    fun `observe for date maps entities to domain models`() = runTest {
        val date = LocalDate.parse("2026-01-02")
        val recorded = Instant.parse("2026-01-02T08:00:00Z")
        val entity = MoodEnergyCheckInEntity(
            id = "entry-1",
            checkInDate = date,
            recordedAt = recorded,
            blockId = "block-1",
            moodScore = 4,
            stressScore = 2,
            energyScore = 3,
            focusScore = 5,
            notes = "good"
        )
        every { dao.observeForDate(date) } returns flowOf(listOf(entity))

        val expectedRecordTime = recorded.atZone(ZoneId.systemDefault()).toLocalDateTime()
        repository.observeForDate(date).test {
            val values = awaitItem()
            assertEquals(1, values.size)
            assertEquals("entry-1", values[0].id)
            assertEquals(expectedRecordTime, values[0].recordedAt)
            awaitComplete()
        }
    }

    @Test
    fun `get latest delegates to dao and maps domain model`() = runTest {
        val recorded = Instant.parse("2026-01-02T08:00:00Z")
        coEvery { dao.getLatest() } returns MoodEnergyCheckInEntity(
            id = "latest-1",
            checkInDate = LocalDate.parse("2026-01-02"),
            recordedAt = recorded,
            blockId = null,
            moodScore = 5,
            stressScore = 1,
            energyScore = 4,
            focusScore = 3,
            notes = null
        )

        val latest = repository.getLatest()
        assertEquals("latest-1", latest?.id)
        assertEquals(
            recorded.atZone(ZoneId.systemDefault()).toLocalDateTime(),
            latest?.recordedAt
        )
    }

    @Test
    fun `get by block id returns mapped values`() = runTest {
        val recorded = Instant.parse("2026-01-03T09:00:00Z")
        coEvery { dao.getForBlock("block-1") } returns listOf(
            MoodEnergyCheckInEntity(
                id = "block-entry",
                checkInDate = LocalDate.parse("2026-01-03"),
                recordedAt = recorded,
                blockId = "block-1",
                moodScore = 2,
                stressScore = 3,
                energyScore = 2,
                focusScore = 4,
                notes = "focus"
            )
        )

        val items = repository.getForBlock("block-1")
        assertEquals(1, items.size)
        assertEquals("block-entry", items[0].id)
    }

    @Test
    fun `save and delete forward to dao`() = runTest {
        coEvery { dao.insert(any()) } returns Unit
        coEvery { dao.deleteById(any()) } returns Unit
        coEvery { dao.getForDateRange(any(), any()) } returns listOf(
            MoodEnergyCheckInEntity(
                id = "range-1",
                checkInDate = LocalDate.parse("2026-01-03"),
                recordedAt = Instant.parse("2026-01-03T10:00:00Z"),
                blockId = "block-1",
                moodScore = 4,
                stressScore = 2,
                energyScore = 3,
                focusScore = 5,
                notes = null
            )
        )

        repository.save(
            MoodEnergyCheckIn(
                id = "save-1",
                blockId = "block-1",
                moodScore = 3,
                stressScore = 2,
                energyScore = 4,
                focusScore = 5,
                notes = "after workout",
                recordedAt = java.time.LocalDateTime.parse("2026-01-03T09:00:00"),
                checkInDate = LocalDate.parse("2026-01-03")
            )
        )
        repository.delete("save-1")

        val range = repository.getForDateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-03"))
        assertEquals(1, range.size)

        coVerify {
            dao.insert(any())
            dao.deleteById("save-1")
            dao.getForDateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-03"))
        }
    }
}
