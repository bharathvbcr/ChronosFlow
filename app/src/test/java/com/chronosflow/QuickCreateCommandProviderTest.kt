package com.chronosflow

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.navigation.quickCreateCommandProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            onNewGoal = { invoked += "goal" },
            onNewMedication = { invoked += "medication" },
            onNewJournal = { invoked += "journal" },
            onLogSleep = { invoked += "sleep" },
            onOpenRoutines = { invoked += "routines" }
        ).commands()

        assertEquals(
            listOf(
                "quick.create-block",
                "quick.create-task",
                "quick.create-focus",
                "quick.create-habit",
                "quick.create-goal",
                "quick.create-medication",
                "quick.create-journal",
                "quick.create-sleep",
                "quick.open-routines"
            ),
            commands.map { it.id }
        )
        assertTrue(commands.all { it.group == CommandPaletteGroups.QUICK_CREATE })
        assertTrue(commands.all { it.priority > 0 })
        assertTrue(commands.all { !it.shortcutLabel.isNullOrBlank() })

        commands.forEach { it.onRun() }

        assertEquals(
            listOf("block", "task", "focus", "habit", "goal", "medication", "journal", "sleep", "routines"),
            invoked
        )
    }

    @Test
    fun journalAndSleepCommandsRespectTheirOwnFlagsNotReview() {
        val ids = quickCreateCommandProvider(
            onNewBlock = {},
            onNewTask = {},
            onNewFocus = {},
            onNewHabit = {},
            onNewGoal = {},
            onNewMedication = {},
            onNewJournal = {},
            onLogSleep = {},
            onOpenRoutines = {},
            // reviewEnabled stays true (default); journal/sleep are independently off.
            featureFlags = ChronosFeatureFlags(journalEnabled = false, sleepEnabled = false)
        ).commands().map { it.id }

        assertFalse(ids.contains("quick.create-journal"))
        assertFalse(ids.contains("quick.create-sleep"))
    }
}
