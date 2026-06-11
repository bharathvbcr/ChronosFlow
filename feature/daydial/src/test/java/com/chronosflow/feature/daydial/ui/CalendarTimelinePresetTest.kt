package com.chronosflow.feature.daydial.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarTimelinePresetTest {
    @Test
    fun `presets map to their source sets and back`() {
        CalendarTimelinePreset.segments.forEach { preset ->
            val sources = timelineSourcesForPreset(preset)
            assertEquals(preset, presetForTimelineSources(requireNotNull(sources)))
        }
        assertNull(timelineSourcesForPreset(CalendarTimelinePreset.CUSTOM))
    }

    @Test
    fun `manual source mixes read as custom`() {
        assertEquals(
            CalendarTimelinePreset.CUSTOM,
            presetForTimelineSources(setOf("Task", "Calendar"))
        )
        assertEquals(
            CalendarTimelinePreset.CUSTOM,
            presetForTimelineSources(emptySet())
        )
    }

    @Test
    fun `persisted source string round-trips and drops unknown labels`() {
        val sources = setOf("Task", "Habit", "Plan")
        val stored = CalendarTimelineSourceLabels.filter { it in sources }.joinToString(",")

        assertEquals(sources, parseTimelineSources(stored))
        assertEquals(emptySet<String>(), parseTimelineSources(""))
        assertEquals(setOf("Task"), parseTimelineSources("Task,Bogus"))
    }

    @Test
    fun `day tools labels surface missed count and clear confirmation`() {
        assertEquals("Review missed (3)", dayToolsReviewMissedLabel(3))
        assertEquals("Review missed", dayToolsReviewMissedLabel(0))
        assertEquals(
            "Delete 1 block for 2026-06-10? This can't be undone.",
            clearDayConfirmationMessage(1, java.time.LocalDate.of(2026, 6, 10))
        )
    }
}
