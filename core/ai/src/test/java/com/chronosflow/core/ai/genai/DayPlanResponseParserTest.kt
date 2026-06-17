package com.chronosflow.core.ai.genai

import com.chronosflow.core.domain.model.BlockFlexibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DayPlanResponseParserTest {
    @Test
    fun `parses valid json with blocks`() {
        val raw = """
            {
                "blocks": [
                    {
                        "title": "Gym",
                        "startMinuteOfDay": 420,
                        "durationMinutes": 60,
                        "flexibility": "MOVABLE"
                    },
                    {
                        "title": "Work",
                        "startMinuteOfDay": 540,
                        "durationMinutes": 480,
                        "flexibility": "FIXED"
                    }
                ],
                "reason": "Optimize morning energy",
                "explanation": "Moving gym to early morning.",
                "conflictsResolved": ["overlap-1"]
            }
        """.trimIndent()

        val result = DayPlanResponseParser.parse(raw, "UTC", "Fallback")

        assertNotNull(result)
        assertEquals(2, result?.proposedBlocks?.size)
        assertEquals("Gym", result?.proposedBlocks?.get(0)?.title)
        assertEquals(BlockFlexibility.FIXED, result?.proposedBlocks?.get(1)?.flexibility)
        assertEquals("Optimize morning energy", result?.reason)
        assertEquals(listOf("overlap-1"), result?.conflictsResolved)
    }

    @Test
    fun `parses json wrapped in markdown code blocks`() {
        val raw = """
            Here is your plan:
            ```json
            {
                "blocks": [
                    { "title": "Test", "startMinuteOfDay": 600, "durationMinutes": 30 }
                ]
            }
            ```
            Hope this helps!
        """.trimIndent()

        val result = DayPlanResponseParser.parse(raw, "UTC", "Fallback")

        assertNotNull(result)
        assertEquals(1, result?.proposedBlocks?.size)
        assertEquals("Test", result?.proposedBlocks?.first()?.title)
    }

    @Test
    fun `tolerates trailing commas in objects and arrays`() {
        val raw = """
            {
                "blocks": [
                    { "title": "Deep work", "startMinuteOfDay": 540, "durationMinutes": 90, },
                ],
                "reason": "r",
            }
        """.trimIndent()

        val result = DayPlanResponseParser.parse(raw, "UTC", "Fallback")

        assertNotNull(result)
        assertEquals(1, result?.proposedBlocks?.size)
        assertEquals("Deep work", result?.proposedBlocks?.first()?.title)
    }

    @Test
    fun `does not strip commas inside string values`() {
        val raw = """
            {
                "blocks": [
                    { "title": "Email Sam, then call", "startMinuteOfDay": 600, "durationMinutes": 30 }
                ]
            }
        """.trimIndent()

        val result = DayPlanResponseParser.parse(raw, "UTC", "Fallback")

        assertNotNull(result)
        assertEquals("Email Sam, then call", result?.proposedBlocks?.first()?.title)
    }

    @Test
    fun `returns null for invalid json`() {
        val raw = "Not a json string"
        val result = DayPlanResponseParser.parse(raw, "UTC", "Fallback")
        assertNull(result)
    }

    @Test
    fun `returns null for json without blocks`() {
        val raw = """{ "reason": "No blocks here" }"""
        val result = DayPlanResponseParser.parse(raw, "UTC", "Fallback")
        assertNull(result)
    }

    @Test
    fun `uses default values for missing block fields`() {
        val raw = """
            {
                "blocks": [
                    { }
                ]
            }
        """.trimIndent()

        val result = DayPlanResponseParser.parse(raw, "UTC", "Fallback")

        assertNotNull(result)
        val block = result?.proposedBlocks?.first()
        assertEquals("Suggested block", block?.title)
        assertEquals(9 * 60, block?.startMinuteOfDay)
        assertEquals(45, block?.durationMinutes)
        assertEquals(BlockFlexibility.MOVABLE, block?.flexibility)
    }
}
