package com.ChronosFlow.VBCR.feature.tasks

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the dependency-free template codec survives delimiter-bearing text and bad input. */
class TaskTemplateTest {

    @Test
    fun `round-trips templates including delimiter-bearing text`() {
        val templates = listOf(
            TaskTemplate(
                id = "t1",
                name = "Weekly report | v2",
                description = "Compile\nmetrics",
                priority = 1,
                durationMinutes = 60,
                preferredStartMinute = 540,
                checklistLabels = listOf("Gather data", "Write summary")
            ),
            TaskTemplate(id = "t2", name = "Pay bills")
        )

        assertEquals(templates, decodeTaskTemplates(encodeTaskTemplates(templates)))
    }

    @Test
    fun `decodes empty input to an empty list and drops malformed records`() {
        assertEquals(emptyList<TaskTemplate>(), decodeTaskTemplates(""))

        val valid = encodeTaskTemplates(listOf(TaskTemplate(id = "t1", name = "Solid")))
        val decoded = decodeTaskTemplates(valid + "\nthis-is-garbage")
        assertEquals(1, decoded.size)
        assertEquals("Solid", decoded.first().name)
    }

    @Test
    fun `round-trips recurrence fields`() {
        val template = TaskTemplate(
            id = "r1",
            name = "Standup",
            recurrenceCadence = TaskRecurringCadence.WEEKLY,
            recurrenceInterval = 2,
            recurrenceWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )
        assertEquals(listOf(template), decodeTaskTemplates(encodeTaskTemplates(listOf(template))))
    }

    @Test
    fun `decodes legacy records that predate the recurrence fields`() {
        val full = encodeTaskTemplates(listOf(TaskTemplate(id = "x", name = "Legacy", durationMinutes = 30)))
        val legacy = full.split("|").take(7).joinToString("|")

        val decoded = decodeTaskTemplates(legacy)
        assertEquals(1, decoded.size)
        assertEquals("Legacy", decoded.first().name)
        assertEquals(30, decoded.first().durationMinutes)
        assertNull(decoded.first().recurrenceCadence)
    }

    @Test
    fun `upsert dedupes by case-insensitive name and caps the list`() {
        val result = upsertTaskTemplate(
            existing = listOf(TaskTemplate(id = "a", name = "Report", priority = 0)),
            template = TaskTemplate(id = "b", name = "report", priority = 2)
        )
        assertEquals(1, result.size)
        assertEquals(2, result.first().priority)

        val capped = (1..15).fold(emptyList<TaskTemplate>()) { acc, index ->
            upsertTaskTemplate(acc, TaskTemplate(id = "$index", name = "T$index"))
        }
        assertEquals(12, capped.size)
        assertEquals("T15", capped.last().name)
    }
}
