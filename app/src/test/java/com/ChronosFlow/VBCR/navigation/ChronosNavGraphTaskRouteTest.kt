package com.ChronosFlow.VBCR.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosNavGraphTaskRouteTest {
    @Test
    fun `tasks day dial shortcut always targets today`() {
        assertEquals(ChronosRoute.Day.TARGET_TODAY, taskScreenDayDialTarget())
    }
}
