package com.chronosflow.feature.daydial.delegate

import com.chronosflow.feature.daydial.model.FocusPhase
import com.chronosflow.feature.daydial.model.FocusPhaseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusSplitSessionStoreTest {

    @Test
    fun `encode then decode round-trips a phase plan`() {
        val phases = listOf(
            FocusPhase(FocusPhaseKind.FOCUS, 25),
            FocusPhase(FocusPhaseKind.BREAK, 5),
            FocusPhase(FocusPhaseKind.FOCUS, 25),
            FocusPhase(FocusPhaseKind.BREAK, 5)
        )

        assertEquals("F25,B5,F25,B5", encodePhases(phases))
        assertEquals(phases, decodePhases(encodePhases(phases)))
    }

    @Test
    fun `decode skips malformed tokens`() {
        assertEquals(
            listOf(FocusPhase(FocusPhaseKind.FOCUS, 25), FocusPhase(FocusPhaseKind.BREAK, 5)),
            decodePhases("F25,,X9,B5,F")
        )
    }
}
