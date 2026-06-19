package com.ChronosFlow.VBCR.feature.medication

import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MedicationSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NextMedicationDoseTest {

    private val today: LocalDate = LocalDate.parse("2026-06-13")

    @Test
    fun `picks the earliest reminder still ahead today`() {
        val plans = listOf(
            plan("Morning", reminderMinute = 8 * 60),
            plan("Evening", reminderMinute = 20 * 60),
            plan("Noon", reminderMinute = 12 * 60)
        )

        val next = nextMedicationDose(plans, nowMinuteOfDay = 9 * 60, today = today)

        assertEquals("Noon", next?.planName)
        assertEquals(12 * 60, next?.minuteOfDay)
        assertTrue(next!!.isToday)
    }

    @Test
    fun `rolls to tomorrow when all reminders are past`() {
        val plans = listOf(
            plan("Morning", reminderMinute = 8 * 60),
            plan("Noon", reminderMinute = 12 * 60)
        )

        val next = nextMedicationDose(plans, nowMinuteOfDay = 22 * 60, today = today)

        assertEquals("Morning", next?.planName)
        assertFalse(next!!.isToday)
    }

    @Test
    fun `skips archived, paused, and prn plans`() {
        val plans = listOf(
            plan("Archived", reminderMinute = 9 * 60, isActive = false),
            plan("Paused", reminderMinute = 9 * 60, pausedUntil = today.plusDays(2)),
            plan("Prn", reminderMinute = 9 * 60, isPrn = true),
            plan("Active", reminderMinute = 10 * 60)
        )

        val next = nextMedicationDose(plans, nowMinuteOfDay = 8 * 60, today = today)

        assertEquals("Active", next?.planName)
    }

    @Test
    fun `expired pause is eligible again`() {
        val plans = listOf(plan("Resumed", reminderMinute = 9 * 60, pausedUntil = today.minusDays(1)))

        val next = nextMedicationDose(plans, nowMinuteOfDay = 8 * 60, today = today)

        assertEquals("Resumed", next?.planName)
    }

    @Test
    fun `no eligible plans yields null`() {
        val plans = listOf(plan("Archived", reminderMinute = 9 * 60, isActive = false))

        assertNull(nextMedicationDose(plans, nowMinuteOfDay = 8 * 60, today = today))
    }

    @Test
    fun `label distinguishes today from tomorrow`() {
        assertEquals(
            "Aspirin at 9:00 AM",
            medicationNextDoseLabel(NextMedicationDose("Aspirin", 9 * 60, isToday = true))
        )
        assertEquals(
            "Aspirin tomorrow at 9:00 AM",
            medicationNextDoseLabel(NextMedicationDose("Aspirin", 9 * 60, isToday = false))
        )
    }

    private fun plan(
        name: String,
        reminderMinute: Int,
        isActive: Boolean = true,
        isPrn: Boolean = false,
        pausedUntil: LocalDate? = null
    ): MedicationPlan = MedicationPlan(
        id = "plan-$name",
        name = name,
        dosage = "1",
        unit = "tablet",
        notes = null,
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = reminderMinute,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = isActive,
        schedule = MedicationSchedule(
            id = "schedule-$name",
            medicationPlanId = "plan-$name",
            isPrn = isPrn,
            pausedUntil = pausedUntil
        )
    )
}
