package com.chronosflow.feature.daydial

import com.chronosflow.feature.daydial.model.FocusPhase
import com.chronosflow.feature.daydial.model.FocusPhaseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusPhaseBoundaryTextTest {

    @Test
    fun `break boundary prompt names the break length`() {
        assertEquals(
            "Time for a 5m break — tap to continue",
            focusPhaseBoundaryText(FocusPhase(FocusPhaseKind.BREAK, 5))
        )
    }

    @Test
    fun `focus boundary prompt names the focus length`() {
        assertEquals(
            "Back to focus for 25m — tap to continue",
            focusPhaseBoundaryText(FocusPhase(FocusPhaseKind.FOCUS, 25))
        )
    }
}
