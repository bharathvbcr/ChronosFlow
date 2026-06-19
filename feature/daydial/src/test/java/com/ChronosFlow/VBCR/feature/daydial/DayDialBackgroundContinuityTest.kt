package com.ChronosFlow.VBCR.feature.daydial

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class DayDialBackgroundContinuityTest {
    @Test
    fun `day content lets the shell background continue behind the header and body`() {
        val source = File("src/main/java/com/ChronosFlow/VBCR/feature/daydial/DayDialMainContent.kt")
            .readText()

        assertFalse(
            "DayDialMainContent must not repaint the body with MaterialTheme.colorScheme.background; " +
                "the outer shell background should stay continuous from top to bottom.",
            source.contains(".background(MaterialTheme.colorScheme.background)")
        )
    }
}
