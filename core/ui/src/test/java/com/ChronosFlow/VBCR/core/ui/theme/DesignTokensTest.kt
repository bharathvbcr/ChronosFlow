package com.ChronosFlow.VBCR.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignTokensTest {

    @Test
    fun `categoryColor maps known categories`() {
        assertEquals(ChronosColors.CategoryWork, categoryColor("work"))
        assertEquals(ChronosColors.CategoryStudy, categoryColor("STUDY"))
        assertEquals(ChronosColors.CategoryExercise, categoryColor("exercise"))
        assertEquals(ChronosColors.CategoryExercise, categoryColor("WORKOUT"))
        assertEquals(ChronosColors.CategoryMeal, categoryColor("food"))
        assertEquals(ChronosColors.CategorySleep, categoryColor("sleep"))
        assertEquals(ChronosColors.CategoryBreak, categoryColor("break"))
        assertEquals(ChronosColors.CategoryRoutine, categoryColor("routine"))
        assertEquals(ChronosColors.CategoryCalendar, categoryColor("calendar"))
    }

    @Test
    fun `categoryColor falls back to soft violet for unknown categories`() {
        assertEquals(ChronosColors.SoftViolet, categoryColor("unknown"))
        assertEquals(ChronosColors.SoftViolet, categoryColor(""))
        assertEquals(ChronosColors.SoftViolet, categoryColor("   "))
    }
}

