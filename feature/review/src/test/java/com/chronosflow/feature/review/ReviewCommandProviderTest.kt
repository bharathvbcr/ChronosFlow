package com.chronosflow.feature.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCommandProviderTest {
    @Test
    fun `review command provider exposes palette metadata and runs callback`() {
        var callbackRuns = 0
        val command = reviewCommandProvider { callbackRuns += 1 }.commands().single()

        assertEquals("review.open", command.id)
        assertTrue(command.title.contains("review", ignoreCase = true))
        assertTrue(command.keywords.contains("review"))
        assertEquals("Review", command.shortcutLabel)
        command.onRun()

        assertEquals(1, callbackRuns)
    }
}
