package com.chronosflow.feature.focus

import com.chronosflow.core.ui.components.CommandPaletteGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusCommandProviderTest {
    @Test
    fun `focus command provider exposes command metadata`() {
        val command = focusCommandProvider { }.commands().single()

        assertEquals("focus.open", command.id)
        assertEquals("Open focus", command.title)
        assertEquals(CommandPaletteGroups.FOCUS, command.group)
        assertEquals("Focus", command.shortcutLabel)
        assertTrue(command.priority > 0)
        assertTrue(command.keywords.contains("session"))
    }

    @Test
    fun `focus command provider executes callback`() {
        var invoked = false
        val command = focusCommandProvider { invoked = true }.commands().single()

        command.onRun()
        assertEquals(true, invoked)
    }
}
