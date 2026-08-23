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

    private fun running(id: String = "session-1") = FocusSessionState.Running(
        sessionId = id,
        blockId = "block-1",
        startedAt = Instant.parse("2026-05-08T14:00:00Z"),
        plannedEndAt = Instant.parse("2026-05-08T14:25:00Z")
    )

    @Test
    fun `duplicate start with the live session id is ignored`() {
        val state = running()

        val reduced = reducer.reduce(state, FocusSessionEvent.Start("session-1", "block-9", Instant.parse("2026-05-08T14:10:00Z"), Instant.parse("2026-05-08T14:40:00Z")))

        assertEquals(state, reduced)
    }

    @Test
    fun `start with a different session id supersedes a loaded phase`() {
        val state = running("session-old")

        val reduced = reducer.reduce(state, FocusSessionEvent.Start("session-new", null, Instant.parse("2026-05-08T14:30:00Z"), Instant.parse("2026-05-08T15:00:00Z")))

        assertEquals(FocusSessionState.Running::class, reduced::class)
        assertEquals("session-new", (reduced as FocusSessionState.Running).sessionId)
    }

    @Test
    fun `prepare never clobbers a live session`() {
        val state = running()

        val reduced = reducer.reduce(state, FocusSessionEvent.Prepare("other-block"))

        assertEquals(state, reduced)
    }

    @Test
    fun `prepare from idle enters preparing`() {
        val reduced = reducer.reduce(FocusSessionState.Idle, FocusSessionEvent.Prepare("block-1"))

        assertEquals(FocusSessionState.Preparing("block-1"), reduced)
    }

    @Test
    fun `archive with mismatched session id is ignored`() {
        val completing = FocusSessionState.Completing("session-1")

        val reduced = reducer.reduce(completing, FocusSessionEvent.Archive("session-stale"))

        assertEquals(completing, reduced)
    }

    @Test
    fun `archive of the completing session archives it`() {
        val reduced = reducer.reduce(FocusSessionState.Completing("session-1"), FocusSessionEvent.Archive("session-1"))

        assertEquals(FocusSessionState.Archived("session-1"), reduced)
    }

    @Test
    fun `permission blocked cannot replace a live session`() {
        val state = running()

        val reduced = reducer.reduce(state, FocusSessionEvent.PermissionBlocked("android.permission.POST_NOTIFICATIONS"))

        assertEquals(state, reduced)
    }

    @Test
    fun `resume with a rolled-back clock keeps the planned end instead of shortening it`() {
        val paused = FocusSessionState.Paused(
            sessionId = "session-1",
            blockId = "block-1",
            startedAt = Instant.parse("2026-05-08T14:00:00Z"),
            pausedAt = Instant.parse("2026-05-08T14:10:00Z"),
            plannedEndAt = Instant.parse("2026-05-08T14:25:00Z")
        )

        // now (14:05) is before pausedAt (14:10): the deadline must not move backwards.
        val reduced = reducer.reduce(paused, FocusSessionEvent.Resume(Instant.parse("2026-05-08T14:05:00Z")))

        reduced as FocusSessionState.Running
        assertEquals(Instant.parse("2026-05-08T14:25:00Z"), reduced.plannedEndAt)
    }

    @Test
    fun `resume after a normal pause extends the deadline by the paused duration`() {
        val paused = FocusSessionState.Paused(
            sessionId = "session-1",
            blockId = "block-1",
            startedAt = Instant.parse("2026-05-08T14:00:00Z"),
            pausedAt = Instant.parse("2026-05-08T14:10:00Z"),
            plannedEndAt = Instant.parse("2026-05-08T14:25:00Z")
        )

        val reduced = reducer.reduce(paused, FocusSessionEvent.Resume(Instant.parse("2026-05-08T14:12:00Z")))

        reduced as FocusSessionState.Running
        assertEquals(Instant.parse("2026-05-08T14:27:00Z"), reduced.plannedEndAt)
    }
}
