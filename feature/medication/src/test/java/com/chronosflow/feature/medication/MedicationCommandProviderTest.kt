package com.chronosflow.feature.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationCommandProviderTest {
    @Test
    fun `medication command provider exposes command metadata and runs callback`() {
        var runs = 0
        val provider = medicationCommandProvider { runs += 1 }

        assertEquals(1, provider.commands().size)

        val command = provider.commands().single()
        assertEquals("medication.open", command.id)
        assertEquals("Open medication", command.title)
        assertEquals("Meds", command.shortcutLabel)
        assertTrue(command.keywords.contains("medicine"))

        command.onRun()
        assertEquals(1, runs)
    }
}

