package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.FocusSessionEvent
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class FocusSessionReducerTest {
    private val reducer = FocusSessionReducer()

    @Test
    fun `service killed while running becomes recoverable`() {
        val state = FocusSessionState.Running(
            sessionId = "session-1",
            blockId = "block-1",
            startedAt = Instant.parse("2026-05-08T14:00:00Z"),
            plannedEndAt = Instant.parse("2026-05-08T14:25:00Z")
        )

        val reduced = reducer.reduce(state, FocusSessionEvent.ServiceKilled)

        assertEquals(FocusSessionState.ServiceKilledRecoverable("session-1"), reduced)
    }

    @Test
    fun `service killed while paused becomes recoverable`() {
        val state = FocusSessionState.Paused(
            sessionId = "session-2",
            blockId = "block-2",
            startedAt = Instant.parse("2026-05-08T15:00:00Z"),
            pausedAt = Instant.parse("2026-05-08T15:10:00Z"),
            plannedEndAt = Instant.parse("2026-05-08T15:30:00Z")
        )

        val reduced = reducer.reduce(state, FocusSessionEvent.ServiceKilled)

        assertEquals(FocusSessionState.ServiceKilledRecoverable("session-2"), reduced)
    }

    @Test
    fun `service killed while idle leaves state unchanged`() {
        val reduced = reducer.reduce(FocusSessionState.Idle, FocusSessionEvent.ServiceKilled)

        assertSame(FocusSessionState.Idle, reduced)
    }
}
