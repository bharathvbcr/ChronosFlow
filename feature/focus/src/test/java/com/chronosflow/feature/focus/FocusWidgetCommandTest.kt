package com.chronosflow.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusWidgetCommandTest {
    @Test
    fun `maps widget actions to focus service actions`() {
        assertEquals(FocusService.ACTION_START, FocusWidgetCommand.serviceActionFor("start"))
        assertEquals(FocusService.ACTION_PAUSE, FocusWidgetCommand.serviceActionFor("pause"))
        assertEquals(FocusService.ACTION_RESUME, FocusWidgetCommand.serviceActionFor("resume"))
        assertEquals(FocusService.ACTION_STOP, FocusWidgetCommand.serviceActionFor("stop"))
        assertNull(FocusWidgetCommand.serviceActionFor("unknown"))
    }
}
