package com.chronosflow.core.data.mapper

import com.chronosflow.core.domain.model.FocusSessionState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FocusSessionMapperTest {
    @Test
    fun `running sessions persist explicit total seconds`() {
        val state = FocusSessionState.Running(
            sessionId = "session-1",
            blockId = "block-1",
            startedAt = Instant.parse("2026-05-25T07:00:00Z"),
            plannedEndAt = Instant.parse("2026-05-25T07:40:00Z")
        )

        val entity = state.toEntity(
            now = Instant.parse("2026-05-25T07:05:00Z"),
            totalSeconds = 2_400
        )

        assertEquals(2_400, entity.totalSeconds)
    }

    @Test
    fun `paused sessions persist explicit total seconds`() {
        val state = FocusSessionState.Paused(
            sessionId = "session-2",
            blockId = "block-2",
            startedAt = Instant.parse("2026-05-25T07:00:00Z"),
            pausedAt = Instant.parse("2026-05-25T07:10:00Z"),
            plannedEndAt = Instant.parse("2026-05-25T07:35:00Z")
        )

        val entity = state.toEntity(
            now = Instant.parse("2026-05-25T07:10:00Z"),
            totalSeconds = 2_100
        )

        assertEquals(2_100, entity.totalSeconds)
    }
}
