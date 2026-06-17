package com.chronosflow.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationAccessTest {
    @Test
    fun `medication routes are recognised by section`() {
        assertTrue(isMedicationRoute(ChronosRoute.Medication()))
        assertTrue(isMedicationRoute(ChronosRoute.Medication(target = ChronosRoute.TARGET_ADD)))
        assertFalse(isMedicationRoute(ChronosRoute.Day()))
        assertFalse(isMedicationRoute(ChronosRoute.Tasks()))
    }

    @Test
    fun `medication add capture is carried on the type-safe route`() {
        val route = ChronosRoute.Medication(
            target = ChronosRoute.TARGET_ADD,
            capture = "vitamin d 1000 iu morning"
        )

        assertEquals(ChronosRoute.TARGET_ADD, route.target)
        assertEquals("vitamin d 1000 iu morning", route.capture)
    }
}
