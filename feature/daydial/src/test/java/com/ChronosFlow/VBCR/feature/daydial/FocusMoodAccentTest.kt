package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FocusMoodAccentTest {
    @Test
    fun `latest check-in prefers matching block then most recent`() {
        val checkIns = listOf(
            checkIn(blockId = "other", mood = 2, energy = 2, at = "2026-05-25T08:00:00"),
            checkIn(blockId = "focus-1", mood = 4, energy = 5, at = "2026-05-25T09:00:00"),
            checkIn(blockId = null, mood = 3, energy = 3, at = "2026-05-25T10:00:00")
        )
        val (mood, energy) = latestFocusMoodAccent(checkIns, blockId = "focus-1")
        assertEquals(4, mood)
        assertEquals(5, energy)
    }

    @Test
    fun `no check-ins yields null accent scores`() {
        val (mood, energy) = latestFocusMoodAccent(emptyList(), blockId = "focus-1")
        assertNull(mood)
        assertNull(energy)
    }

    @Test
    fun `cached scores used when no check-ins today`() {
        val (mood, energy) = latestFocusMoodAccent(
            checkIns = emptyList(),
            blockId = "focus-1",
            cachedMoodScore = 4,
            cachedEnergyScore = 5
        )
        assertEquals(4, mood)
        assertEquals(5, energy)
    }

    private fun checkIn(
        blockId: String?,
        mood: Int,
        energy: Int,
        at: String
    ) = MoodEnergyCheckIn(
        id = "c-$mood",
        blockId = blockId,
        moodScore = mood,
        stressScore = 3,
        energyScore = energy,
        focusScore = 3,
        notes = null,
        recordedAt = LocalDateTime.parse(at),
        checkInDate = LocalDate.parse("2026-05-25")
    )
}
