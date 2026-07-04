package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.feature.daydial.blockPlannerChipValue
import com.ChronosFlow.VBCR.feature.daydial.blockTimeSectionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockEditorFormTest {

    @Test
    fun blockPlannerChipValue_isNullWhenUnset() {
        assertNull(blockPlannerChipValue(locked = false, isProtected = false))
    }

    @Test
    fun blockPlannerChipValue_joinsActiveFlags() {
        assertEquals("Locked", blockPlannerChipValue(locked = true, isProtected = false))
        assertEquals("Protected", blockPlannerChipValue(locked = false, isProtected = true))
        assertEquals("Locked · Protected", blockPlannerChipValue(locked = true, isProtected = true))
    }

    @Test
    fun blockTimeSectionSummary_formatsRangeAndDuration() {
        assertEquals("09:00 – 10:30 · 1h 30m", blockTimeSectionSummary("09:00", "90"))
    }

    @Test
    fun blockTimeSectionSummary_handlesInvalidStart() {
        assertEquals("Set start and duration", blockTimeSectionSummary("", "60"))
    }
}
