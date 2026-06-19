package com.ChronosFlow.VBCR.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyRedactionTest {
    @Test
    fun `medication widget label shows the plan name when not redacted`() {
        assertEquals(
            "Metformin",
            PrivacyRedaction.medicationWidgetLabel(planName = "Metformin", redactMedicationNames = false)
        )
    }

    @Test
    fun `medication widget label redacts to the generic title when requested`() {
        assertEquals(
            PrivacyRedaction.GENERIC_MEDICATION_TITLE,
            PrivacyRedaction.medicationWidgetLabel(planName = "Metformin", redactMedicationNames = true)
        )
    }

    @Test
    fun `medication widget label falls back to the generic title for blank or missing names`() {
        assertEquals(
            PrivacyRedaction.GENERIC_MEDICATION_TITLE,
            PrivacyRedaction.medicationWidgetLabel(planName = null, redactMedicationNames = false)
        )
        assertEquals(
            PrivacyRedaction.GENERIC_MEDICATION_TITLE,
            PrivacyRedaction.medicationWidgetLabel(planName = "   ", redactMedicationNames = false)
        )
    }
}
