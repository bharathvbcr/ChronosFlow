package com.chronosflow.feature.medication

import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSafetyProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationRefillMessageTest {

    @Test
    fun `single plan message includes remaining count`() {
        val plans = listOf(plan("Aspirin", remaining = 3))

        assertEquals("Aspirin (3 left) is running low.", medicationRefillMessage(plans))
    }

    @Test
    fun `two plans are joined with and`() {
        val plans = listOf(plan("Aspirin", remaining = 2), plan("Metformin", remaining = 1))

        assertEquals(
            "Aspirin (2 left) and Metformin (1 left) are running low.",
            medicationRefillMessage(plans)
        )
    }

    @Test
    fun `three or more plans collapse the tail into a count`() {
        val plans = listOf(
            plan("Aspirin", remaining = 2),
            plan("Metformin", remaining = 1),
            plan("Vitamin D", remaining = 0),
            plan("Iron", remaining = 1)
        )

        assertEquals(
            "Aspirin (2 left), Metformin (1 left) and 2 more are running low.",
            medicationRefillMessage(plans)
        )
    }

    @Test
    fun `plan without supply count omits the parenthetical`() {
        val plans = listOf(plan("Aspirin", remaining = null))

        assertEquals("Aspirin is running low.", medicationRefillMessage(plans))
    }

    private fun plan(name: String, remaining: Int?): MedicationPlan = MedicationPlan(
        id = "plan-$name",
        name = name,
        dosage = "1",
        unit = "tablet",
        notes = null,
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = 9 * 60,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = true,
        safetyProfile = MedicationSafetyProfile(
            medicationPlanId = "plan-$name",
            supplyRemaining = remaining,
            refillThreshold = 5
        )
    )
}
