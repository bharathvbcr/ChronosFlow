package com.ChronosFlow.VBCR.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosDurationSliderTest {
    private val range = 5..480

    @Test
    fun `snap uses five minute steps below two hours`() {
        assertEquals(65, snapDurationMinutes(63f, range))
        assertEquals(5, snapDurationMinutes(1f, range))
        assertEquals(115, snapDurationMinutes(116f, range))
    }

    @Test
    fun `snap uses fifteen minute steps from two hours up`() {
        assertEquals(120, snapDurationMinutes(123f, range))
        assertEquals(195, snapDurationMinutes(190f, range))
        assertEquals(480, snapDurationMinutes(900f, range))
    }

    @Test
    fun `stepper changes by the step size of the side it moves toward`() {
        assertEquals(115, stepDurationMinutes(120, -1, range))
        assertEquals(135, stepDurationMinutes(120, 1, range))
        assertEquals(60, stepDurationMinutes(55, 1, range))
        assertEquals(5, stepDurationMinutes(5, -1, range))
        assertEquals(480, stepDurationMinutes(480, 1, range))
    }

    @Test
    fun `step size threshold sits at two hours`() {
        assertEquals(5, durationStepMinutes(119))
        assertEquals(15, durationStepMinutes(120))
    }
}
