package com.ChronosFlow.VBCR.feature.daydial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the block-editor Save contract: well-formed start/duration commit with the parsed
 * values, and any malformed input is rejected with an actionable message instead of being
 * silently discarded behind a "Changes saved" toast (the bug this validation closed).
 */
class BlockEditValidationTest {

    @Test
    fun validInputCommitsParsedValues() {
        val result = validateBlockEdit(startText = "09:00", durationText = "90")
        assertEquals(BlockEditValidation.Commit(startMinute = 9 * 60, durationMinutes = 90), result)
    }

    @Test
    fun midnightAndOneMinuteAreValidEdges() {
        assertEquals(
            BlockEditValidation.Commit(startMinute = 0, durationMinutes = 1),
            validateBlockEdit(startText = "00:00", durationText = "1")
        )
        assertEquals(
            BlockEditValidation.Commit(startMinute = 23 * 60 + 59, durationMinutes = 480),
            validateBlockEdit(startText = "23:59", durationText = "480")
        )
    }

    @Test
    fun outOfRangeTimeIsRejected() {
        val result = validateBlockEdit(startText = "99:99", durationText = "90")
        assertTrue(result is BlockEditValidation.Invalid)
        assertEquals("Enter a valid start time as HH:MM", (result as BlockEditValidation.Invalid).message)
    }

    @Test
    fun nonNumericTimeIsRejected() {
        assertTrue(validateBlockEdit(startText = "abc", durationText = "90") is BlockEditValidation.Invalid)
    }

    @Test
    fun nonNumericDurationIsRejected() {
        val result = validateBlockEdit(startText = "09:00", durationText = "abc")
        assertTrue(result is BlockEditValidation.Invalid)
        assertEquals("Enter a duration of at least 1 minute", (result as BlockEditValidation.Invalid).message)
    }

    @Test
    fun zeroOrNegativeDurationIsRejected() {
        assertTrue(validateBlockEdit(startText = "09:00", durationText = "0") is BlockEditValidation.Invalid)
        assertTrue(validateBlockEdit(startText = "09:00", durationText = "-5") is BlockEditValidation.Invalid)
    }

    @Test
    fun timeIsValidatedBeforeDuration() {
        // Both fields bad → the start-time message wins, matching the editor's check order.
        val result = validateBlockEdit(startText = "nope", durationText = "0")
        assertEquals(
            "Enter a valid start time as HH:MM",
            (result as BlockEditValidation.Invalid).message
        )
    }
}
