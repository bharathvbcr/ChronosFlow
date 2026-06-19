package com.ChronosFlow.VBCR.appfunctions

import com.ChronosFlow.VBCR.core.data.focus.ManualMissedBlockRegistry
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.usecase.AdvanceRecurringTaskOccurrenceUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteTimeBlockUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.SyncRecurringTaskAlarmsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TimeBlockCompletionHandlerTest {
    private val completeTimeBlockUseCase: CompleteTimeBlockUseCase = mockk(relaxed = true)
    private val advanceRecurringTaskOccurrenceUseCase: AdvanceRecurringTaskOccurrenceUseCase = mockk(relaxed = true)
    private val syncRecurringTaskAlarmsUseCase: SyncRecurringTaskAlarmsUseCase = mockk(relaxed = true)
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry = mockk(relaxed = true)
    private val handler = TimeBlockCompletionHandler(
        completeTimeBlockUseCase,
        advanceRecurringTaskOccurrenceUseCase,
        syncRecurringTaskAlarmsUseCase,
        manualMissedBlockRegistry
    )

    @Test
    fun `fresh completion advances recurring task, syncs alarms and clears missed`() = runTest {
        val block = block()
        val task = mockk<Task>()
        val schedule = mockk<TaskSchedule>()
        coEvery { completeTimeBlockUseCase(block) } returns true
        coEvery { advanceRecurringTaskOccurrenceUseCase(block) } returns (task to schedule)

        handler.complete(block)

        coVerify(exactly = 1) { syncRecurringTaskAlarmsUseCase(task, schedule, any()) }
        verify(exactly = 1) { manualMissedBlockRegistry.clearMissed("block-1", any()) }
    }

    @Test
    fun `already-complete block clears missed but does not advance or sync`() = runTest {
        val block = block()
        coEvery { completeTimeBlockUseCase(block) } returns false

        handler.complete(block)

        coVerify(exactly = 0) { advanceRecurringTaskOccurrenceUseCase(any()) }
        coVerify(exactly = 0) { syncRecurringTaskAlarmsUseCase(any(), any(), any()) }
        verify(exactly = 1) { manualMissedBlockRegistry.clearMissed("block-1", any()) }
    }

    private fun block(): TimeBlock = TimeBlock(
        id = "block-1",
        date = LocalDate.of(2026, 5, 26),
        title = "Standup",
        category = "work",
        startMinuteOfDay = 540,
        durationMinutes = 15,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.MOVABLE,
        energyLevel = EnergyIntensity.fromLevel(2),
        source = "test",
        taskId = "task-1",
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        taskOccurrenceDate = LocalDate.of(2026, 5, 26),
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
