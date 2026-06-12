package com.chronosflow.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosOptionChipsTest {

    @Test
    fun `withSelectedOption keeps the current selection visible in truncated option lists`() {
        assertEquals(
            listOf("15 min", "30 min", "1 hr"),
            listOf("15 min", "30 min").withSelectedOption("1 hr")
        )
    }

    @Test
    fun `withSelectedOption leaves the list untouched when selection is blank or already present`() {
        val options = listOf("Today", "Tomorrow")
        assertEquals(options, options.withSelectedOption(null))
        assertEquals(options, options.withSelectedOption(""))
        assertEquals(options, options.withSelectedOption("Today"))
    }
}
