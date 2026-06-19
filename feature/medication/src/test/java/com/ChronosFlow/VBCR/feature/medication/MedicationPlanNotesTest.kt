package com.ChronosFlow.VBCR.feature.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationPlanNotesTest {
    @Test
    fun parseMedicationPlanNotesStripsTimingMetadataFromDisplayNotes() {
        val parsed = parseMedicationPlanNotes(
            "[[reminder2:1260]] [[timing:before_bed]] Take with water"
        )

        assertEquals(21 * 60, parsed.secondaryReminderMinute)
        assertEquals("Take with water", parsed.displayNotes)
    }

    @Test
    fun parseMedicationPlanNotesReturnsTrimmedDisplayWhenNoMetadata() {
        val parsed = parseMedicationPlanNotes("   Take with food   ")

        assertNull(parsed.secondaryReminderMinute)
        assertNull(parsed.mealTiming)
        assertEquals("Take with food", parsed.displayNotes)
    }

    @Test
    fun parseMedicationPlanNotesHandlesOutOfRangeReminderMinuteByClamping() {
        val parsed = parseMedicationPlanNotes("[[reminder2:5555]] note")

        assertEquals(1439, parsed.secondaryReminderMinute)
        assertEquals("note", parsed.displayNotes)
    }

    @Test
    fun encodeMedicationPlanNotesRoundTripsDisplayAndMetadata() {
        val encoded = encodeMedicationPlanNotes(
            displayNotes = "Take with food",
            secondaryReminderMinute = 9 * 60,
            mealTiming = "Before bed"
        )

        val parsed = parseMedicationPlanNotes(encoded)
        assertEquals("Before bed", parsed.mealTiming)
        assertEquals(9 * 60, parsed.secondaryReminderMinute)
        assertEquals("Take with food", parsed.displayNotes)
    }

    @Test
    fun encodeMedicationPlanNotesReturnsNullForEmptyInputs() {
        assertNull(encodeMedicationPlanNotes(displayNotes = null, secondaryReminderMinute = null, mealTiming = null))
        assertNull(encodeMedicationPlanNotes(displayNotes = "   ", secondaryReminderMinute = null, mealTiming = null))
    }
}
