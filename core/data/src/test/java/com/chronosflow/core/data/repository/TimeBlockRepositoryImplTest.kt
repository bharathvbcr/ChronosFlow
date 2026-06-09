package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.model.TimeBlockEntity
import com.chronosflow.core.data.sync.SyncMutationNotifier
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TimeBlockRepositoryImplTest {
    private val timeBlockDao: TimeBlockDao = mockk()
    private val syncMutationNotifier = RecordingSyncMutationNotifier()
    private lateinit var repository: TimeBlockRepositoryImpl

    @Before
    fun setup() {
        syncMutationNotifier.reset()
        repository = TimeBlockRepositoryImpl(timeBlockDao, syncMutationNotifier)
    }

    @Test
    fun `saveTimeBlock persists DAO row and queues sync`() = runTest {
        coEvery { timeBlockDao.insertTimeBlock(any()) } returns Unit

        repository.saveTimeBlock(sampleTimeBlock())

        coVerify {
            timeBlockDao.insertTimeBlock(
                match { it.id == "block-1" && it.taskId == "task-1" }
            )
        }
        assertEquals(1, syncMutationNotifier.callCount)
    }

    @Test
    fun `saveTimeBlock persists linked planner source identifiers`() = runTest {
        coEvery { timeBlockDao.insertTimeBlock(any()) } returns Unit

        repository.saveTimeBlock(
            sampleTimeBlock().copy(
                calendarEventId = 42L,
                medicationPlanId = "med-1",
                habitId = "habit-1",
                recurrenceRuleId = "recurrence-1"
            )
        )

        coVerify {
            timeBlockDao.insertTimeBlock(
                match {
                    it.taskId == "task-1" &&
                        it.calendarEventId == 42L &&
                        it.medicationPlanId == "med-1" &&
                        it.habitId == "habit-1" &&
                        it.recurrenceRuleId == "recurrence-1" &&
                        it.taskOccurrenceDate == LocalDate.parse("2026-01-12")
                }
            )
        }
    }

    @Test
    fun `deleteTimeBlock deletes DAO row and queues sync`() = runTest {
        coEvery { timeBlockDao.deleteTimeBlock(any()) } returns Unit

        repository.deleteTimeBlock(sampleTimeBlock())

        coVerify {
            timeBlockDao.deleteTimeBlock(
                match<TimeBlockEntity> { it.id == "block-1" }
            )
        }
        assertEquals(1, syncMutationNotifier.callCount)
    }

    @Test
    fun `clearDay clears DAO rows and queues sync`() = runTest {
        val date = LocalDate.parse("2026-01-12")
        coEvery { timeBlockDao.clearDay(date) } returns Unit

        repository.clearDay(date)

        coVerify { timeBlockDao.clearDay(date) }
        assertEquals(1, syncMutationNotifier.callCount)
    }

    private fun sampleTimeBlock(): TimeBlock = TimeBlock(
        id = "block-1",
        date = LocalDate.parse("2026-01-12"),
        title = "Draft sync plan",
        category = "Deep Work",
        startMinuteOfDay = 540,
        durationMinutes = 45,
        timezone = "America/Chicago",
        provenance = BlockProvenance.TASK_CONVERTED,
        flexibility = BlockFlexibility.MOVABLE,
        energyLevel = EnergyIntensity.HIGH,
        source = "TASK",
        taskId = "task-1",
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.parse("2026-01-12T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-12T01:00:00Z"),
        taskOccurrenceDate = LocalDate.parse("2026-01-12")
    )

    private class RecordingSyncMutationNotifier : SyncMutationNotifier {
        var callCount = 0
            private set

        override fun notifyLocalMutation() {
            callCount += 1
        }

        fun reset() {
            callCount = 0
        }
    }
}
