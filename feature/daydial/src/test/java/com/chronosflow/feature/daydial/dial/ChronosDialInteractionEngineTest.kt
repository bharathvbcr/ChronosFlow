package com.chronosflow.feature.daydial.dial

import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.feature.daydial.model.PlannerHapticCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosDialInteractionEngineTest {
    private val engine = ChronosDialInteractionEngine()

    @Test
    fun `snaps minute to grid`() {
        assertEquals(60, engine.snapMinute(58))
    }

    @Test
    fun `locked validation maps to locked haptic`() {
        val (cue, _) = engine.hapticForValidation(
            PlannerOperationResult.Locked("locked", "block-1"),
            positionChanged = true
        )
        assertEquals(PlannerHapticCue.LOCKED_COLLISION, cue)
    }

    @Test
    fun `circular minute delta wraps backward`() {
        assertEquals(-30, engine.circularMinuteDelta(30, 0))
    }

    @Test
    fun `repeated move previews across dial wrap stay normalized`() {
        val validation = PlannerOperationResult.Applied(
            message = "Placement preview valid",
            blockId = "block-1",
            snappedToMinute = null
        )

        repeat(96) { index ->
            val pointerMinute = (23 * 60 + index * 15) % 1440

            val preview = engine.previewMove(
                blockId = "block-1",
                blockStartMinute = 23 * 60,
                blockDurationMinutes = 45,
                dragStartMinute = 23 * 60,
                currentMinute = pointerMinute,
                isLocked = false,
                validation = validation
            )

            assertEquals("block-1", preview.blockId)
            assertEquals(45, preview.durationMinutes)
            assertTrue(preview.startMinute in 0 until 1440)
        }
    }
}
