package com.chronosflow.feature.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitCommandProviderTest {
    @Test
    fun `habit command provider exposes command metadata and runs callback`() {
        var runs = 0
        val provider = habitCommandProvider { runs += 1 }

        assertEquals(1, provider.commands().size)

        val command = provider.commands().single()
        assertEquals("habits.open", command.id)
        assertEquals("Open habits", command.title)
        assertEquals("Habits", command.shortcutLabel)
        assertTrue(command.keywords.contains("habit"))

        command.onRun()
        assertEquals(1, runs)
    }
}

