package com.chronosflow.feature.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCommandProviderTest {
    @Test
    fun `task command provider exposes command metadata and runs callback`() {
        var runs = 0
        val provider = taskCommandProvider { runs += 1 }

        assertEquals(1, provider.commands().size)

        val command = provider.commands().single()
        assertEquals("tasks.open", command.id)
        assertEquals("Open tasks", command.title)
        assertEquals("Tasks", command.shortcutLabel)
        assertTrue(command.keywords.contains("todo"))

        command.onRun()
        assertEquals(1, runs)
    }
}

