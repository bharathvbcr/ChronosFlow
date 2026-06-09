package com.chronosflow.feature.medication

import com.chronosflow.core.domain.model.PlannerRecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class MedicationAutoAssistTest {

    @Test
    fun `shouldAutoRequestMedicationAssistForCapture returns false for short non-contextual strings`() {
        assertFalse(shouldAutoRequestMedicationAssistForCapture("abc"))
        assertFalse(shouldAutoRequestMedicationAssistForCapture("hello"))
    }

    @Test
    fun `shouldAutoRequestMedicationAssistForCapture returns true for strings containing dose words`() {
        assertTrue(shouldAutoRequestMedicationAssistForCapture("take 50mg"))
        assertTrue(shouldAutoRequestMedicationAssistForCapture("2 tablets"))
    }

    @Test
    fun `shouldAutoRequestMedicationAssistForCapture returns true for non-supplement context`() {
        // "as needed" returns MedicationContextKind.AS_NEEDED, which is not SUPPLEMENT
        assertTrue(shouldAutoRequestMedicationAssistForCapture("as needed"))
    }

    @Test
    fun `shouldAutoRequestMedicationAssistForCapture returns true for reminder words`() {
        assertTrue(shouldAutoRequestMedicationAssistForCapture("morning meds"))
        assertTrue(shouldAutoRequestMedicationAssistForCapture("take at lunch"))
    }

    @Test
    fun `shouldAutoRequestMedicationAssistForCapture returns true for safety or refill words`() {
        assertTrue(shouldAutoRequestMedicationAssistForCapture("inhaler needed"))
        assertTrue(shouldAutoRequestMedicationAssistForCapture("need refill"))
    }

    @Test
    fun `buildMedicationRecurrenceForFrequency - As needed`() {
        val recurrence = buildMedicationRecurrenceForFrequency("As needed", emptyList(), DayOfWeek.MONDAY)
        assertEquals(PlannerRecurrenceType.PRN, recurrence.type)
        assertTrue(recurrence.timesOfDayMinutes.isEmpty())
    }

    @Test
    fun `buildMedicationRecurrenceForFrequency - Weekdays`() {
        val times = listOf(480)
        val recurrence = buildMedicationRecurrenceForFrequency("Weekdays", times, DayOfWeek.MONDAY)
        assertEquals(PlannerRecurrenceType.WEEKDAYS, recurrence.type)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), recurrence.weekdays)
        assertEquals(times, recurrence.timesOfDayMinutes)
    }

    @Test
    fun `buildMedicationRecurrenceForFrequency - Every other day`() {
        val times = listOf(600)
        val recurrence = buildMedicationRecurrenceForFrequency("Every other day", times, DayOfWeek.MONDAY)
        assertEquals(PlannerRecurrenceType.EVERY_N_DAYS, recurrence.type)
        assertEquals(2, recurrence.interval)
        assertEquals(times, recurrence.timesOfDayMinutes)
    }

    @Test
    fun `buildMedicationRecurrenceForFrequency - Daily`() {
        val times = listOf(720)
        val recurrence = buildMedicationRecurrenceForFrequency("Daily", times, DayOfWeek.MONDAY)
        assertEquals(PlannerRecurrenceType.DAILY, recurrence.type)
        assertEquals(times, recurrence.timesOfDayMinutes)
    }

    @Test
    fun `buildMedicationRecurrenceForFrequency - Multiple times daily`() {
        val times = listOf(480, 1080)
        val recurrence = buildMedicationRecurrenceForFrequency("Daily", times, DayOfWeek.MONDAY)
        assertEquals(PlannerRecurrenceType.MULTIPLE_TIMES_DAILY, recurrence.type)
        assertEquals(times, recurrence.timesOfDayMinutes)
    }
}
