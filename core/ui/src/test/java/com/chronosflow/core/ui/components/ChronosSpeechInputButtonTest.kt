package com.chronosflow.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosSpeechInputButtonTest {
    @Test
    fun `speech transcript uses first nonblank normalized result when scores tie`() {
        val transcript = chronosSpeechTranscriptFromResults(
            listOf(
                "   ",
                "  vitamin   d   1000   iu   morning  ",
                "vitamin d morning"
            )
        )

        assertEquals("vitamin d 1000 iu morning", transcript)
    }

    @Test
    fun `speech transcript prefers stronger capture-like alternative`() {
        val transcript = chronosSpeechTranscriptFromResults(
            listOf(
                "vitamin the morning",
                "vitamin d 1000 iu morning",
                "vitamin d"
            )
        )

        assertEquals("vitamin d 1000 iu morning", transcript)
    }

    @Test
    fun `speech transcript prefers cadence rich habit alternative`() {
        val transcript = chronosSpeechTranscriptFromResults(
            listOf(
                "strength train",
                "strength training three times per week at 6pm"
            )
        )

        assertEquals("strength training three times per week at 6pm", transcript)
    }

    @Test
    fun `speech transcript is blank when no usable result exists`() {
        val transcript = chronosSpeechTranscriptFromResults(listOf(" ", "\t"))

        assertEquals("", transcript)
    }
}
