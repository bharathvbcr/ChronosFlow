package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.feature.daydial.model.FocusExecutionStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FocusSessionTabRestoreTest {
    @Test
    fun `buildFocusExecutionStateFromPersisted maps running session`() = runTest {
        val repository = mockk<TimeBlockRepository>()
        val startedAt = Instant.parse("2026-05-25T10:00:00Z")
        val plannedEndAt = Instant.parse("2026-05-25T10:25:00Z")
        val session = FocusSessionState.Running(
            sessionId = "running-1",
            blockId = "block-1",
            startedAt = startedAt,
            plannedEndAt = plannedEndAt
        )
        val block = sampleBlock()
        coEvery { repository.getTimeBlockById("block-1") } returns block

        val result = buildFocusExecutionStateFromPersisted(session, repository)

        assertNotNull(result)
        assertEquals("block-1", result!!.blockId)
        assertEquals("Focus Block", result.blockTitle)
        assertEquals(8 * 60 + 15, result.blockStartMinute)
        assertEquals(25, result.plannedDurationMinutes)
        assertEquals(startedAt.toEpochMilli(), result.startedEpochMs)
        assertEquals(FocusExecutionStatus.RUNNING, result.status)
        assertNull(result.pausedAtEpochMs)
    }

    @Test
    fun `buildFocusExecutionStateFromPersisted maps paused session with caps for long sessions`() = runTest {
        val repository = mockk<TimeBlockRepository>()
        val startedAt = Instant.parse("2026-05-25T10:00:00Z")
        val plannedEndAt = Instant.parse("2026-05-25T20:00:00Z")
        val session = FocusSessionState.Paused(
            sessionId = "paused-1",
            blockId = "block-1",
            startedAt = startedAt,
            pausedAt = Instant.parse("2026-05-25T10:45:00Z"),
            plannedEndAt = plannedEndAt
        )
        coEvery { repository.getTimeBlockById(any()) } returns null

        val result = buildFocusExecutionStateFromPersisted(session, repository)

        assertNotNull(result)
        assertEquals("Focus session", result!!.blockTitle)
        assertEquals(FocusExecutionStatus.PAUSED, result.status)
        assertEquals(480, result.plannedDurationMinutes)
        assertEquals(Instant.parse("2026-05-25T10:45:00Z").toEpochMilli(), result.pausedAtEpochMs)
    }

    @Test
    fun `buildFocusExecutionStateFromPersisted returns null for non recoverable session`() = runTest {
        assertNull(buildFocusExecutionStateFromPersisted(FocusSessionState.Idle, mockk(relaxed = true)))
    }

    @Test
    fun `recoverable session id only for running or paused`() {
        val running = FocusSessionState.Running(
            sessionId = "s-1",
            blockId = "b-1",
            startedAt = Instant.parse("2026-05-25T10:00:00Z"),
            plannedEndAt = Instant.parse("2026-05-25T10:25:00Z")
        )
        assertEquals("s-1", recoverableServiceSessionId(running))
        assertNull(recoverableServiceSessionId(FocusSessionState.Idle))
    }

    private fun sampleBlock(
        id: String = "block-1",
        title: String = "Focus Block",
        startMinuteOfDay: Int = 8 * 60 + 15
    ): TimeBlock = TimeBlock(
        id = id,
        date = LocalDate.parse("2026-05-25"),
        title = title,
        category = "WORK",
        startMinuteOfDay = startMinuteOfDay,
        durationMinutes = 60,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.MOVABLE,
        energyLevel = EnergyIntensity.MODERATE,
        source = "USER_CREATED",
        taskId = null,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.parse("2026-05-25T08:00:00Z"),
        updatedAt = Instant.parse("2026-05-25T08:00:00Z")
    )
}
