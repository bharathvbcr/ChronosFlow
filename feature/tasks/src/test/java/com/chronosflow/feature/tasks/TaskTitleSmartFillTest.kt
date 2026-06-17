package com.chronosflow.feature.tasks

import com.chronosflow.core.ui.components.ChronosLinkOption
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for the offline inline title parser that powers the add-task "Smart fill" banner.
 * It must extract scheduling/priority tokens deterministically and tidy the title, without ever
 * depending on on-device AI.
 */
class TaskTitleSmartFillTest {

    private val reference = LocalDate.of(2026, 6, 15)

    @Test
    fun `extracts date time duration and priority and strips them from the title`() {
        val fill = taskTitleSmartFill("Call dentist tomorrow at 3pm urgent 30 min")

        requireNotNull(fill)
        assertEquals("Tomorrow", fill.scheduleDateOption)
        assertEquals(15 * 60, fill.preferredStartMinute)
        assertEquals(30, fill.durationMinutes)
        assertEquals(2, fill.priority)
        // The action verb stays; only the scheduling/priority noise is removed.
        assertTrue(fill.cleanedTitle.contains("Call dentist", ignoreCase = true))
        assertTrue(fill.cleanedTitle.none { it.isDigit() })
    }

    @Test
    fun `surfaces a chip preview for each detected field`() {
        val fill = taskTitleSmartFill("Prep slides today 9am 60 min")

        requireNotNull(fill)
        assertEquals(listOf("Today", "9:00 AM", "1h"), fill.detectedChips)
    }

    @Test
    fun `returns null for a plain title with no schedulable tokens`() {
        assertNull(taskTitleSmartFill("Email follow-up"))
    }

    @Test
    fun `returns null for too-short input`() {
        assertNull(taskTitleSmartFill("hi"))
    }

    @Test
    fun `resolves relative in-N-days and in-N-weeks phrases`() {
        assertEquals(reference.plusDays(3), taskTranscriptTargetDate("submit report in 3 days", reference))
        assertEquals(reference.plusDays(14), taskTranscriptTargetDate("plan trip in 2 weeks", reference))
    }

    @Test
    fun `resolves day-after-tomorrow and this-weekend`() {
        assertEquals(reference.plusDays(2), taskTranscriptTargetDate("ship it day after tomorrow", reference))
        val weekend = taskTranscriptTargetDate("relax this weekend", reference)
        requireNotNull(weekend)
        assertEquals(DayOfWeek.SATURDAY, weekend.dayOfWeek)
    }

    @Test
    fun `resolves a qualified weekday to the next future occurrence`() {
        val nextTue = taskTranscriptTargetDate("meet sam next tuesday", reference)
        requireNotNull(nextTue)
        assertEquals(DayOfWeek.TUESDAY, nextTue.dayOfWeek)
        assertTrue(nextTue.isAfter(reference))
    }

    @Test
    fun `does not treat a bare weekday with no qualifier as a date`() {
        assertNull(taskTranscriptTargetDate("monday report draft", reference))
    }

    @Test
    fun `smart fill resolves a weekday into a concrete target date and strips it from the title`() {
        val fill = taskTitleSmartFill("Submit taxes next friday", reference)

        requireNotNull(fill)
        assertEquals(DayOfWeek.FRIDAY, fill.targetDate?.dayOfWeek)
        assertTrue(fill.cleanedTitle.equals("Submit taxes", ignoreCase = true))
        assertTrue(fill.detectedChips.isNotEmpty())
    }

    @Test
    fun `smart fill surfaces an email in the title as an attachable action`() {
        val fill = taskTitleSmartFill("Email john@example.com about the report")

        requireNotNull(fill)
        assertTrue(fill.actionPreview!!.contains("john@example.com"))
        assertTrue(fill.detectedChips.any { it.contains("john@example.com") })
    }

    @Test
    fun `recent task options dedupe drop blanks and cap the list`() {
        val recent = recentTaskTitleOptions(
            listOf("Pay rent", "Pay rent", "  ", "Call mom", "A", "B", "C", "D", "E", "F")
        )
        assertEquals(listOf("Pay rent", "Call mom", "A", "B", "C", "D"), recent)
    }

    @Test
    fun `duplicate warning fires only on a case-insensitive match of sufficient length`() {
        assertNull(taskTitleDuplicateWarning("Buy milk", listOf("Pay rent")))
        assertNull(taskTitleDuplicateWarning("hi", listOf("hi")))
        val warning = taskTitleDuplicateWarning("buy MILK", listOf("Buy milk"))
        requireNotNull(warning)
        assertTrue(warning.contains("Buy milk"))
    }

    @Test
    fun `detects a weekly cadence with the named weekday`() {
        val recurrence = taskTitleRecurrence("Standup every monday", reference)

        requireNotNull(recurrence)
        assertTrue(recurrence.enabled)
        assertEquals(TaskRecurringCadence.WEEKLY, recurrence.cadence)
        assertEquals(1, recurrence.interval)
        assertTrue(recurrence.weekdays.contains(DayOfWeek.MONDAY))
    }

    @Test
    fun `detects an interval cadence like every 2 weeks`() {
        val recurrence = taskTitleRecurrence("Pay rent every 2 weeks", reference)

        requireNotNull(recurrence)
        assertEquals(TaskRecurringCadence.WEEKLY, recurrence.cadence)
        assertEquals(2, recurrence.interval)
    }

    @Test
    fun `detects a daily cadence and returns null for non-recurring titles`() {
        val daily = taskTitleRecurrence("Water plants every day", reference)
        requireNotNull(daily)
        assertEquals(TaskRecurringCadence.DAILY, daily.cadence)
        assertEquals(1, daily.interval)

        assertNull(taskTitleRecurrence("Buy milk", reference))
    }

    @Test
    fun `detects high priority as level 1 and strips the phrase from the title`() {
        val fill = taskTitleSmartFill("Fix login bug high priority")

        requireNotNull(fill)
        assertEquals(1, fill.priority)
        assertTrue(fill.detectedChips.contains("High"))
        assertTrue(fill.cleanedTitle.equals("Fix login bug", ignoreCase = true))
    }

    @Test
    fun `smart fill surfaces a recurrence chip`() {
        val fill = taskTitleSmartFill("Team sync every week", reference)

        requireNotNull(fill)
        assertEquals(TaskRecurringCadence.WEEKLY, fill.recurrence?.cadence)
        assertTrue(fill.detectedChips.any { it.contains("weekly", ignoreCase = true) })
    }

    @Test
    fun `detects p1 and p2 priority shorthand and strips the token`() {
        val urgent = taskTitleSmartFill("Ship release p1")
        requireNotNull(urgent)
        assertEquals(2, urgent.priority)
        assertTrue(urgent.cleanedTitle.equals("Ship release", ignoreCase = true))

        val high = taskTitleSmartFill("Review PR p2")
        requireNotNull(high)
        assertEquals(1, high.priority)
    }

    @Test
    fun `resolves next weekend and end of month`() {
        // next weekend = the Saturday after this coming one.
        val nextWeekend = taskTranscriptTargetDate("camping next weekend", reference)
        requireNotNull(nextWeekend)
        assertEquals(DayOfWeek.SATURDAY, nextWeekend.dayOfWeek)
        assertTrue(nextWeekend.isAfter(reference.plusDays(6)))

        val endOfMonth = taskTranscriptTargetDate("file taxes end of month", reference)
        assertEquals(reference.withDayOfMonth(reference.lengthOfMonth()), endOfMonth)
    }

    @Test
    fun `resolves word-number and month relative offsets`() {
        assertEquals(reference.plusDays(7), taskTranscriptTargetDate("renew pass in a week", reference))
        assertEquals(reference.plusDays(14), taskTranscriptTargetDate("review in two weeks", reference))
        assertEquals(reference.plusMonths(3), taskTranscriptTargetDate("dentist in 3 months", reference))
        assertEquals(reference.plusDays(2), taskTranscriptTargetDate("ping in two days", reference))
    }

    @Test
    fun `parses finer time-of-day phrases`() {
        assertEquals(14 * 60, taskTitleSmartFill("Call Sam after lunch")?.preferredStartMinute)
        assertEquals(11 * 60 + 30, taskTitleSmartFill("Prep deck before lunch")?.preferredStartMinute)
        assertEquals(17 * 60 + 30, taskTitleSmartFill("Email recap after work")?.preferredStartMinute)
        assertEquals(11 * 60, taskTitleSmartFill("Finish report before noon")?.preferredStartMinute)
    }

    @Test
    fun `parses clock ranges into start and duration, ignoring quantity ranges`() {
        assertEquals(14 * 60 to 120, taskTranscriptTimeRange("meeting from 2 to 4pm"))
        assertEquals(9 * 60 to 90, taskTranscriptTimeRange("standup 9 to 10:30am"))
        assertEquals(11 * 60 to 120, taskTranscriptTimeRange("call 11 to 1pm"))
        assertNull(taskTranscriptTimeRange("buy 5-10 apples"))
    }

    @Test
    fun `smart fill applies a time range as start plus duration`() {
        val fill = taskTitleSmartFill("Design block from 2 to 4pm tomorrow")
        requireNotNull(fill)
        assertEquals(14 * 60, fill.preferredStartMinute)
        assertEquals(120, fill.durationMinutes)
        assertEquals("Tomorrow", fill.scheduleDateOption)
    }

    @Test
    fun `parses arbitrary (non-preset) durations`() {
        assertEquals(75, taskTitleSmartFill("Deep work block 75 min today")?.durationMinutes)
        assertEquals(180, taskTitleSmartFill("Workshop 3 hours tomorrow")?.durationMinutes)
        // Out-of-range durations are ignored.
        assertEquals(null, taskTitleSmartFill("Marathon 600 min tomorrow")?.durationMinutes)
    }

    @Test
    fun `strips leading capture filler from the title`() {
        val fill = taskTitleSmartFill("remind me to water the plants tomorrow")
        requireNotNull(fill)
        assertEquals("Tomorrow", fill.scheduleDateOption)
        assertTrue(fill.cleanedTitle.equals("Water the plants", ignoreCase = true))
    }

    @Test
    fun `pluralizes counts with singular for one`() {
        assertEquals("1 step", pluralizeCount(1, "step"))
        assertEquals("3 steps", pluralizeCount(3, "step"))
        assertEquals("0 steps", pluralizeCount(0, "step"))
    }

    @Test
    fun `detects an existing goal name in the task text`() {
        val goals = listOf(
            ChronosLinkOption("g1", "Run a marathon"),
            ChronosLinkOption("g2", "Learn Spanish")
        )
        assertEquals("g2", detectGoalIdFromText("Practice learn spanish flashcards", goals))
        assertNull(detectGoalIdFromText("Buy groceries", goals))
    }

    @Test
    fun `resolves a coming weekday qualifier`() {
        val date = taskTranscriptTargetDate("ship it coming friday", reference)
        requireNotNull(date)
        assertEquals(DayOfWeek.FRIDAY, date.dayOfWeek)
        assertTrue(!date.isBefore(reference))
    }

    @Test
    fun `splits checklist input on semicolons and newlines`() {
        assertEquals(listOf("Pack bag", "Charge phone", "Lock door"), splitChecklistInput("Pack bag; Charge phone\nLock door"))
        assertEquals(listOf("Single step"), splitChecklistInput("  Single step  "))
        assertTrue(splitChecklistInput("  ;  \n ").isEmpty())
    }

    @Test
    fun `extracts bulleted description lines as checklist candidates, skipping existing`() {
        val description = "- Book venue\n2. Send invites\n• Order cake\nBook venue"
        val candidates = descriptionChecklistCandidates(
            description = description,
            existingLabels = listOf("Order cake")
        )
        assertEquals(listOf("Book venue", "Send invites"), candidates)
    }
}
