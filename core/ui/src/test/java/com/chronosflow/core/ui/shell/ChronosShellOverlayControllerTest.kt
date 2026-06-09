package com.chronosflow.core.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosShellOverlayControllerTest {

    @Test
    fun suppressBottomChrome_isFalseUntilTagged() {
        val controller = ChronosShellOverlayController()
        assertFalse(controller.suppressBottomChrome)
    }

    @Test
    fun setSuppressed_addsAndRemovesTags() {
        val controller = ChronosShellOverlayController()

        controller.setSuppressed("drawer", true)
        assertTrue(controller.suppressBottomChrome)

        controller.setSuppressed("drawer", false)
        assertFalse(controller.suppressBottomChrome)
    }

    @Test
    fun multipleTags_requireAllCleared() {
        val controller = ChronosShellOverlayController()

        controller.setSuppressed("drawer", true)
        controller.setSuppressed("sheet", true)
        assertTrue(controller.suppressBottomChrome)

        controller.setSuppressed("drawer", false)
        assertTrue(controller.suppressBottomChrome)

        controller.setSuppressed("sheet", false)
        assertFalse(controller.suppressBottomChrome)
    }
}
