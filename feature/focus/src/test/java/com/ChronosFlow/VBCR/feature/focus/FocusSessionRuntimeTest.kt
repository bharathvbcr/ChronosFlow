package com.ChronosFlow.VBCR.feature.focus

import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.planner.FocusSessionReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FocusSessionRuntimeTest {
    @Test
    fun `running snapshot counts down without external updates`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-1",
            blockId = "block-1",
            timeLeftSeconds = 1_500,
            totalSeconds = 1_500
        )
        clock.advanceBySeconds(90)

        val snapshot = runtime.snapshot()

        assertEquals(1_410, snapshot.timeLeftSeconds)
        assertEquals(1_500, snapshot.totalSeconds)
        assertTrue(snapshot.isRunning)
    }

    @Test
    fun `pause freezes remaining time and resume keeps paused remainder`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-2",
            blockId = "block-2",
            timeLeftSeconds = 1_500,
            totalSeconds = 1_500
        )
        clock.advanceBySeconds(300)

        val paused = runtime.pause()
        clock.advanceBySeconds(120)
        val resumed = runtime.resume()

        assertEquals(1_200, paused.timeLeftSeconds)
        assertEquals(1_200, runtime.snapshot().timeLeftSeconds)
        assertEquals(1_200, resumed.timeLeftSeconds)
        assertTrue(resumed.isRunning)
    }

    @Test
    fun `restored running session survives service recreation`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )
        runtime.restore(
            state = FocusSessionState.Running(
                sessionId = "session-restored",
                blockId = "block-1",
                startedAt = clock.now(),
                plannedEndAt = clock.now().plusSeconds(1_500)
            ),
            totalSeconds = 1_500
        )

        clock.advanceBySeconds(300)

        val snapshot = runtime.snapshot()
        assertEquals("session-restored", snapshot.sessionId)
        assertEquals("block-1", snapshot.blockId)
        assertEquals(1_200, snapshot.timeLeftSeconds)
        assertTrue(snapshot.isRunning)
    }

    @Test
    fun `adjustSeconds can shorten remaining time`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-shorten",
            blockId = null,
            timeLeftSeconds = 900,
            totalSeconds = 900
        )
        clock.advanceBySeconds(60)

        val shortened = runtime.adjustSeconds(-300)

        assertTrue(shortened.timeLeftSeconds < 840)
        assertTrue(shortened.totalSeconds < 900)
        assertTrue(shortened.isRunning)
    }

    @Test
    fun `extend increases remaining and total seconds`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-3",
            blockId = null,
            timeLeftSeconds = 600,
            totalSeconds = 600
        )
        clock.advanceBySeconds(60)

        val extended = runtime.extend(300)

        assertEquals(900, extended.totalSeconds)
        assertEquals(840, extended.timeLeftSeconds)
        assertTrue(extended.isRunning)
    }

    @Test
    fun `adjustSeconds with zero delta is a no-op`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-zero",
            blockId = "block-zero",
            timeLeftSeconds = 600,
            totalSeconds = 600
        )

        val adjusted = runtime.adjustSeconds(0)

        assertEquals(600, adjusted.totalSeconds)
        assertEquals(600, adjusted.timeLeftSeconds)
    }

    @Test
    fun `adjustSeconds clamps to one minute minimum`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-clamp",
            blockId = null,
            timeLeftSeconds = 120,
            totalSeconds = 120
        )
        val clamped = runtime.adjustSeconds(-90)

        assertEquals(60, clamped.totalSeconds)
        assertEquals(60, clamped.timeLeftSeconds)
    }

    @Test
    fun `snapshot on idle falls back to total seconds`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )
        runtime.restore(FocusSessionState.Idle, 420)

        assertEquals(420, runtime.snapshot().timeLeftSeconds)
        assertEquals(420, runtime.snapshot().totalSeconds)
    }

    @Test
    fun `restore archived state does not expose session runtime identifiers`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )
        runtime.restore(
            state = FocusSessionState.Archived("archived-session"),
            totalSeconds = 600
        )

        val snapshot = runtime.snapshot()

        assertFalse(snapshot.isRunning)
        assertFalse(snapshot.isPaused)
        assertEquals("archived-session", snapshot.sessionId)
        assertEquals(null, snapshot.blockId)
    }

    @Test
    fun `finished session moves into completing state`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-4",
            blockId = "block-4",
            timeLeftSeconds = 60,
            totalSeconds = 60
        )
        clock.advanceBySeconds(60)

        val completed = runtime.completeIfFinished()

        assertTrue(completed)
        assertEquals("session-4", runtime.snapshot().sessionId)
        assertFalse(runtime.snapshot().isRunning)
    }

    @Test
    fun `completeIfFinished is idempotent after completion`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-once",
            blockId = "block-once",
            timeLeftSeconds = 60,
            totalSeconds = 60
        )
        clock.advanceBySeconds(60)

        assertTrue(runtime.completeIfFinished())
        assertFalse(runtime.completeIfFinished())
        assertEquals("session-once", runtime.snapshot().sessionId)
        assertFalse(runtime.snapshot().isRunning)
    }

    @Test
    fun `archive stops the active session`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.start(
            sessionId = "session-5",
            blockId = null,
            timeLeftSeconds = 600,
            totalSeconds = 600
        )

        val archived = runtime.archive()

        assertEquals("session-5", archived.sessionId)
        assertFalse(archived.isRunning)
        assertFalse(archived.isPaused)
    }

    @Test
    fun `pause is no-op when session is idle`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.restore(
            state = FocusSessionState.Idle,
            totalSeconds = 300
        )

        val paused = runtime.pause()

        assertEquals(300, paused.timeLeftSeconds)
        assertEquals(300, paused.totalSeconds)
        assertEquals(null, paused.sessionId)
        assertEquals(false, paused.isPaused)
        assertEquals(false, paused.isRunning)
    }

    @Test
    fun `resume is no-op when session is idle`() {
        val clock = FakeClock(Instant.parse("2026-05-25T02:00:00Z"))
        val runtime = FocusSessionRuntime(
            reducer = FocusSessionReducer(),
            now = clock::now
        )

        runtime.restore(
            state = FocusSessionState.Idle,
            totalSeconds = 300
        )

        val resumed = runtime.resume()

        assertEquals(300, resumed.timeLeftSeconds)
        assertEquals(300, resumed.totalSeconds)
        assertEquals(null, resumed.sessionId)
        assertEquals(false, resumed.isPaused)
        assertEquals(false, resumed.isRunning)
    }
}

private class FakeClock(private var instant: Instant) {
    fun now(): Instant = instant

    fun advanceBySeconds(seconds: Long) {
        instant = instant.plusSeconds(seconds)
    }
}
