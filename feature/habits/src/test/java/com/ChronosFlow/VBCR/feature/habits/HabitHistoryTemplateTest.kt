package com.ChronosFlow.VBCR.feature.habits

import com.ChronosFlow.VBCR.core.domain.model.Habit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitHistoryTemplateTest {
    @Test
    fun buildHabitHistoryTemplatesPrioritizesArchivedEntriesAndExcludesCurrentHabit() {
        val templates = buildHabitHistoryTemplates(
            habits = listOf(
                habit(id = "current", title = "Stretch", isActive = true),
                habit(id = "archived", title = "Morning walk", isActive = false, startMinute = 6 * 60),
                habit(id = "active", title = "Read", isActive = true, startMinute = 19 * 60)
            ),
            currentHabitId = "current"
        )

        assertEquals(listOf("archived", "active"), templates.map(HabitHistoryTemplate::id))
        assertEquals("Archived", templates.first().sourceLabel)
        assertEquals("Saved", templates.last().sourceLabel)
    }

    @Test
    fun filterHabitHistoryTemplatesMatchesTitleCadenceAndSource() {
        val templates = listOf(
            HabitHistoryTemplate(
                id = "archived",
                title = "Morning walk",
                cadence = "Daily",
                startMinute = 6 * 60,
                endMinute = 7 * 60,
                difficulty = 2,
                isBundled = true,
                isArchived = true
            ),
            HabitHistoryTemplate(
                id = "saved",
                title = "Read",
                cadence = "Weekdays",
                startMinute = 19 * 60,
                endMinute = 20 * 60,
                difficulty = 2,
                isBundled = false,
                isArchived = false
            )
        )

        assertEquals(listOf("archived"), filterHabitHistoryTemplates(templates, "archive").map(HabitHistoryTemplate::id))
        assertEquals(listOf("saved"), filterHabitHistoryTemplates(templates, "weekday").map(HabitHistoryTemplate::id))
        assertEquals(listOf("archived"), filterHabitHistoryTemplates(templates, "walk").map(HabitHistoryTemplate::id))
        assertEquals(listOf("archived"), filterHabitHistoryTemplates(templates, "MORNING archived").map(HabitHistoryTemplate::id))
        assertEquals(templates, filterHabitHistoryTemplates(templates, "   "))
    }

    @Test
    fun prioritizeRecentHabitHistoryTemplatesMovesRecentSelectionsToTopWithinCurrentResults() {
        val templates = listOf(
            HabitHistoryTemplate("archived", "Morning walk", "Daily", 6 * 60, 7 * 60, 2, true, true),
            HabitHistoryTemplate("saved", "Read", "Weekdays", 19 * 60, 20 * 60, 2, false, false),
            HabitHistoryTemplate("later", "Workout", "Weekdays", 7 * 60, 8 * 60, 4, true, false)
        )

        val prioritized = prioritizeRecentHabitHistoryTemplates(
            templates = templates,
            recentIds = listOf("later", "archived", "missing")
        )

        assertEquals(listOf("later", "archived", "saved"), prioritized.map(HabitHistoryTemplate::id))
    }

    @Test
    fun encodeAndParseRecentHabitTemplateIdsRoundTripWithDeduping() {
        val encoded = encodeRecentHabitTemplateIds(
            listOf("later", "archived", "later", "", "saved", "extra", "overflow")
        )

        assertEquals(listOf("later", "archived", "saved", "extra", "overflow"), parseRecentHabitTemplateIds(encoded))
    }

    private fun habit(
        id: String,
        title: String,
        isActive: Boolean,
        startMinute: Int = 8 * 60
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = startMinute,
        windowEndMinute = startMinute + 60,
        difficulty = 2,
        isBundled = false,
        streakCount = 0,
        lastCompletedDate = LocalDate.of(2026, 5, 24),
        isActive = isActive
    )
}
