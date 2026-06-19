package com.ChronosFlow.VBCR.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SleepReadinessTest {

    private val date = LocalDate.of(2026, 6, 12)

    private fun night(
        quality: Int = 0,
        actualStartMinute: Int? = null,
        actualEndMinute: Int? = null,
        interruptedCount: Int = 0
    ): SleepTrack = SleepTrack(
        id = "night",
        date = date,
        plannedStartMinute = null,
        plannedEndMinute = null,
        actualStartMinute = actualStartMinute,
        actualEndMinute = actualEndMinute,
        sleepQuality = quality,
        windDownNotes = null,
        interruptedCount = interruptedCount
    )

    @Test
    fun `missing night is unknown`() {
        assertEquals(SleepReadiness.UNKNOWN, deriveSleepReadiness(null))
    }

    @Test
    fun `unrated night with no measurable window is unknown`() {
        assertEquals(SleepReadiness.UNKNOWN, deriveSleepReadiness(night(quality = 0)))
    }

    @Test
    fun `low quality ratings are depleted`() {
        assertEquals(SleepReadiness.DEPLETED, deriveSleepReadiness(night(quality = 1)))
        assertEquals(SleepReadiness.DEPLETED, deriveSleepReadiness(night(quality = 2)))
    }

    @Test
    fun `middle quality rating is normal`() {
        assertEquals(SleepReadiness.NORMAL, deriveSleepReadiness(night(quality = 3)))
    }

    @Test
    fun `high quality ratings are rested`() {
        assertEquals(SleepReadiness.RESTED, deriveSleepReadiness(night(quality = 4)))
        assertEquals(SleepReadiness.RESTED, deriveSleepReadiness(night(quality = 5)))
    }

    @Test
    fun `heavy interruptions drag an otherwise good rating to depleted`() {
        assertEquals(
            SleepReadiness.DEPLETED,
            deriveSleepReadiness(night(quality = 5, interruptedCount = 3))
        )
    }

    @Test
    fun `duration stands in when the night is unrated`() {
        // 23:00 -> 04:30 = 5h30m, under the 6h depleted floor.
        assertEquals(
            SleepReadiness.DEPLETED,
            deriveSleepReadiness(night(actualStartMinute = 23 * 60, actualEndMinute = 4 * 60 + 30))
        )
        // 23:00 -> 06:00 = 7h, between the floors.
        assertEquals(
            SleepReadiness.NORMAL,
            deriveSleepReadiness(night(actualStartMinute = 23 * 60, actualEndMinute = 6 * 60))
        )
        // 22:30 -> 06:30 = 8h, at or above the rested floor.
        assertEquals(
            SleepReadiness.RESTED,
            deriveSleepReadiness(night(actualStartMinute = 22 * 60 + 30, actualEndMinute = 6 * 60 + 30))
        )
    }

    @Test
    fun `a user rating outweighs a long measured window`() {
        assertEquals(
            SleepReadiness.DEPLETED,
            deriveSleepReadiness(
                night(quality = 1, actualStartMinute = 22 * 60, actualEndMinute = 7 * 60)
            )
        )
    }
}
