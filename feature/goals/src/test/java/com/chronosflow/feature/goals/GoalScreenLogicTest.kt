package com.chronosflow.feature.goals

import com.chronosflow.core.domain.model.Goal
import com.chronosflow.core.domain.model.GoalDerivedProgress
import com.chronosflow.core.domain.model.GoalWithProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalScreenLogicTest {

    private fun goal(
        id: String = "g",
        category: String = "Personal",
        targetDate: LocalDate? = null,
        isCompleted: Boolean = false
    ) = Goal(
        id = id,
        title = "Goal $id",
        description = null,
        category = category,
        targetValue = 10,
        startDate = LocalDate.of(2026, 1, 1),
        targetDate = targetDate,
        progressValue = 0,
        isCompleted = isCompleted
    )

    @Test
    fun `categories in use are distinct and sorted`() {
        val goals = listOf(
            GoalWithProgress(goal(id = "1", category = "Work")),
            GoalWithProgress(goal(id = "2", category = "Health")),
            GoalWithProgress(goal(id = "3", category = "Work"))
        )

        assertEquals(listOf("Health", "Work"), goalCategoriesInUse(goals))
    }

    @Test
    fun `due label is null without a target date or when completed`() {
        val today = LocalDate.of(2026, 6, 12)
        assertNull(goalDueLabel(targetDate = null, isCompleted = false, today = today))
        assertNull(goalDueLabel(targetDate = today, isCompleted = true, today = today))
    }

    @Test
    fun `due label emphasizes overdue, today, and near deadlines`() {
        val today = LocalDate.of(2026, 6, 12)

        assertEquals(
            GoalDueLabel("Overdue", emphasized = true),
            goalDueLabel(targetDate = today.minusDays(1), isCompleted = false, today = today)
        )
        assertEquals(
            GoalDueLabel("Due today", emphasized = true),
            goalDueLabel(targetDate = today, isCompleted = false, today = today)
        )
        assertEquals(
            GoalDueLabel("Due in 3d", emphasized = true),
            goalDueLabel(targetDate = today.plusDays(3), isCompleted = false, today = today)
        )
    }

    @Test
    fun `far-off deadline is shown without emphasis`() {
        val today = LocalDate.of(2026, 6, 12)
        val far = today.plusDays(30)

        assertEquals(
            GoalDueLabel("Due $far", emphasized = false),
            goalDueLabel(targetDate = far, isCompleted = false, today = today)
        )
    }

    @Test
    fun `linked work label sums tasks and habits, or is null when none`() {
        assertNull(goalLinkedWorkLabel(GoalDerivedProgress()))
        assertEquals(
            "+5 from linked work",
            goalLinkedWorkLabel(GoalDerivedProgress(completedTaskCount = 2, habitCompletionCount = 3))
        )
    }

    @Test
    fun `target-date warning fires only for past dates`() {
        val today = LocalDate.of(2026, 6, 12)

        assertNull(goalTargetDateWarning(targetDate = null, today = today))
        assertNull(goalTargetDateWarning(targetDate = today, today = today))
        assertNull(goalTargetDateWarning(targetDate = today.plusDays(1), today = today))

        assertEquals(
            "This date is in the past — the goal will show as overdue.",
            goalTargetDateWarning(targetDate = today.minusDays(1), today = today)
        )
    }

    @Test
    fun `target quick picks are ascending and unique`() {
        assertEquals(GoalTargetQuickPicks.sorted(), GoalTargetQuickPicks)
        assertEquals(GoalTargetQuickPicks.distinct(), GoalTargetQuickPicks)
    }

    @Test
    fun `progress summary caps at target and reports percent`() {
        assertEquals("3 of 10 · 30%", goalProgressSummary(progress = 3, target = 10))
        // Over-achieved progress is capped to the target for display.
        assertEquals("10 of 10 · 100%", goalProgressSummary(progress = 14, target = 10))
        // No measurable target falls back to a raw count.
        assertEquals("4 logged", goalProgressSummary(progress = 4, target = 0))
    }

    @Test
    fun `progress fraction is clamped to unit range`() {
        assertEquals(0.3f, goalProgressFraction(progress = 3, target = 10), 0.0001f)
        assertEquals(1f, goalProgressFraction(progress = 14, target = 10), 0.0001f)
        assertEquals(0f, goalProgressFraction(progress = 5, target = 0), 0.0001f)
    }

    @Test
    fun `category is suggested from title keywords, or null when unrecognised`() {
        assertEquals("Health", suggestGoalCategory("Run a 10k"))
        assertEquals("Learning", suggestGoalCategory("Read 12 books"))
        assertEquals("Finance", suggestGoalCategory("Save 5000 for a trip"))
        assertEquals("Work", suggestGoalCategory("Launch the new project"))
        assertNull(suggestGoalCategory("Plan the team offsite"))
    }

    @Test
    fun `target-below-progress warning fires only when the new target is under logged progress`() {
        assertNull(goalTargetBelowProgressWarning(target = 10, progress = 3))
        assertNull(goalTargetBelowProgressWarning(target = 10, progress = 10))
        assertNull(goalTargetBelowProgressWarning(target = 10, progress = 0))
        assertEquals(
            "Target is below your logged progress (7).",
            goalTargetBelowProgressWarning(target = 5, progress = 7)
        )
    }

    @Test
    fun `target is extracted from the first usable number in a title`() {
        assertEquals(12, goalTargetFromTitle("Read 12 books this year"))
        assertEquals(100, goalTargetFromTitle("Run 100 miles"))
        // No number, or a leading zero/out-of-range value, yields nothing.
        assertNull(goalTargetFromTitle("Meditate daily"))
        assertNull(goalTargetFromTitle("Goal 0"))
    }

    @Test
    fun `deadline presets are relative to today and end-of-year is Dec 31`() {
        val today = LocalDate.of(2026, 6, 12)
        val presets = goalDeadlinePresets(today)

        assertEquals(
            listOf("1 month", "3 months", "6 months", "End of year"),
            presets.map { it.first }
        )
        assertEquals(today.plusMonths(1), presets[0].second)
        assertEquals(today.plusMonths(3), presets[1].second)
        assertEquals(today.plusMonths(6), presets[2].second)
        assertEquals(LocalDate.of(2026, 12, 31), presets[3].second)
    }

    @Test
    fun `category options merge presets, existing, and a typed custom value`() {
        val presets = listOf("Personal", "Health")

        // Nothing custom, no existing -> just the presets.
        assertEquals(presets, goalCategoryOptions(presets, existingCategory = null, customCategory = ""))

        // A typed custom value is trimmed and appended once.
        assertEquals(
            listOf("Personal", "Health", "Travel"),
            goalCategoryOptions(presets, existingCategory = null, customCategory = "  Travel  ")
        )

        // A custom value that duplicates a preset does not double up.
        assertEquals(
            presets,
            goalCategoryOptions(presets, existingCategory = null, customCategory = "Health")
        )

        // The edited goal's own off-list category is preserved.
        assertEquals(
            listOf("Personal", "Health", "Finance"),
            goalCategoryOptions(presets, existingCategory = "Finance", customCategory = "")
        )
    }
}
