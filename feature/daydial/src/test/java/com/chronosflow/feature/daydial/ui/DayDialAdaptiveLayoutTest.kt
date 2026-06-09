package com.chronosflow.feature.daydial.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DayDialAdaptiveLayoutTest {
    @Test
    fun `chronos dial diameter stays bounded across width classes`() {
        assertEquals(320.dp, chronosDialMaxDiameter(DayDialWidthClass.COMPACT))
        assertEquals(340.dp, chronosDialMaxDiameter(DayDialWidthClass.MEDIUM))
        assertEquals(380.dp, chronosDialMaxDiameter(DayDialWidthClass.EXPANDED))
    }
}
