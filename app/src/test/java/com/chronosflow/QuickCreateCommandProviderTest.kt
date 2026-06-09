package com.chronosflow

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.navigation.quickCreateCommandProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCreateCommandProviderTest {

    @Test
    fun quickCreateCommandsExposePaletteMetadataAndCallbacks() {
        val invoked = mutableListOf<String>()
        val commands = quickCreateCommandProvider(
            onNewBlock = { invoked += "block" },
            onNewTask = { invoked += "task" },
            onNewFocus = { invoked += "focus" },
            onNewHabit = { invoked += "habit" },
            onNewMedication = { invoked += "medication" }
        ).commands()

        assertEquals(
            listOf(
                "quick.create-block",
                "quick.create-task",
                "quick.create-focus",
                "quick.create-habit",
                "quick.create-medication"
            ),
            commands.map { it.id }
        )
        assertTrue(commands.all { it.group == CommandPaletteGroups.QUICK_CREATE })
        assertTrue(commands.all { it.priority > 0 })
        assertTrue(commands.all { !it.shortcutLabel.isNullOrBlank() })

        commands.forEach { it.onRun() }

        assertEquals(listOf("block", "task", "focus", "habit", "medication"), invoked)
    }
}
