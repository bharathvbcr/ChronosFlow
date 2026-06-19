package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalMoodAndStreakTest {

    private fun entry(
        date: LocalDate,
        rating: Int? = null,
        isPrimary: Boolean = true,
        id: String = "e-$date"
    ) = JournalEntry(
        id = id,
        entryDate = date,
        createdAt = Instant.parse("2026-06-12T20:00:00Z"),
        updatedAt = Instant.parse("2026-06-12T20:00:00Z"),
        body = "Reflection for $date",
        isPrimary = isPrimary,
        dayRating = rating
    )

    private fun workout(date: LocalDate) = entry(
        date = date,
        isPrimary = false,
        id = "hc-workout-${date}-1"
    )

    @Test
    fun `mood lookup resolves valid ratings and rejects out-of-range or null`() {
        assertEquals("Great", journalMoodFor(5)?.label)
        assertEquals("😞", journalMoodFor(1)?.emoji)
        assertNull(journalMoodFor(null))
        assertNull(journalMoodFor(0))
        assertNull(journalMoodFor(6))
    }

    @Test
    fun `every mood option has a unique rating one through five`() {
        assertEquals(listOf(1, 2, 3, 4, 5), JournalMoods.map { it.rating })
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val today = LocalDate.of(2026, 6, 17)
        val entries = listOf(
            entry(today),
            entry(today.minusDays(1)),
            entry(today.minusDays(2))
        )
        assertEquals(3, journalStreak(entries, today))
    }

    @Test
    fun `streak stays alive when today is not yet written but yesterday is`() {
        val today = LocalDate.of(2026, 6, 17)
        val entries = listOf(
            entry(today.minusDays(1)),
            entry(today.minusDays(2))
        )
        assertEquals(2, journalStreak(entries, today))
    }

    @Test
    fun `streak lapses when the most recent entry is older than yesterday`() {
        val today = LocalDate.of(2026, 6, 17)
        val entries = listOf(entry(today.minusDays(3)), entry(today.minusDays(4)))
        assertEquals(0, journalStreak(entries, today))
    }

    @Test
    fun `streak stops at the first gap`() {
        val today = LocalDate.of(2026, 6, 17)
        // today, yesterday present; then a gap at -2; older days don't extend the streak.
        val entries = listOf(
            entry(today),
            entry(today.minusDays(1)),
            entry(today.minusDays(3)),
            entry(today.minusDays(4))
        )
        assertEquals(2, journalStreak(entries, today))
    }

    @Test
    fun `streak ignores duplicate entries on the same day`() {
        val today = LocalDate.of(2026, 6, 17)
        val entries = listOf(entry(today), entry(today), entry(today.minusDays(1)))
        assertEquals(2, journalStreak(entries, today))
    }

    @Test
    fun `streak is zero with no entries`() {
        assertEquals(0, journalStreak(emptyList(), LocalDate.of(2026, 6, 17)))
    }

    @Test
    fun `streak label celebrates a run and stays quiet at zero`() {
        assertNull(journalStreakLabel(0))
        assertEquals("Day 1 — nice start", journalStreakLabel(1))
        assertEquals("🔥 5-day streak", journalStreakLabel(5))
    }

    @Test
    fun `prompt of the day is stable per date and rotates across days`() {
        val date = LocalDate.of(2026, 6, 17)
        // Deterministic: same date always yields the same prompt.
        assertEquals(journalPromptOfTheDay(date), journalPromptOfTheDay(date))
        // It is always one of the curated prompts.
        assertTrue(journalPromptOfTheDay(date) in JournalDailyPrompts)
        // Consecutive days move to the next prompt in the rotation.
        assertEquals(
            JournalDailyPrompts[((date.toEpochDay() + 1) % JournalDailyPrompts.size).toInt()],
            journalPromptOfTheDay(date.plusDays(1))
        )
    }

    @Test
    fun `prompt of the day handles pre-epoch dates without crashing`() {
        // Negative epoch day must not throw or index out of bounds.
        assertNotNull(journalPromptOfTheDay(LocalDate.of(1900, 1, 1)))
    }

    @Test
    fun `average mood ignores unrated and out-of-range entries`() {
        val today = LocalDate.of(2026, 6, 17)
        val entries = listOf(
            entry(today, rating = 4),
            entry(today.minusDays(1), rating = 2),
            entry(today.minusDays(2), rating = null),
            entry(today.minusDays(3), rating = 9)
        )
        assertEquals(3.0, journalAverageMood(entries)!!, 0.0001)
        assertNull(journalAverageMood(listOf(entry(today, rating = null))))
    }

    @Test
    fun `days in window counts distinct dates inside the trailing window`() {
        val today = LocalDate.of(2026, 6, 17)
        val entries = listOf(
            entry(today),
            entry(today.minusDays(13)),
            entry(today.minusDays(14)) // just outside a 14-day window
        )
        assertEquals(2, journalDaysInWindow(entries, today, windowDays = 14))
    }

    @Test
    fun `insight prompt includes entries and stays non-empty`() {
        val today = LocalDate.of(2026, 6, 17)
        val prompt = buildJournalInsightPrompt(listOf(entry(today, rating = 4)), today)
        assertTrue(prompt.contains("mood 4/5"))
        assertTrue(prompt.contains(today.toString()))
    }

    @Test
    fun `body serialize and parse round-trips main note and sub-notes`() {
        val parts = JournalBodyParts("Great day overall", listOf("Walked 5k", "Called mom"))
        val body = journalSerializeBody(parts.mainNote, parts.subNotes)
        assertTrue(body.contains("Great day overall"))
        assertTrue(body.contains("• Walked 5k"))
        val parsed = journalParseBody(body)
        assertEquals("Great day overall", parsed.mainNote)
        assertEquals(listOf("Walked 5k", "Called mom"), parsed.subNotes)
    }

    @Test
    fun `serialize drops blank sub-notes and parse of plain body yields no sub-notes`() {
        assertEquals("Just text", journalSerializeBody("Just text", listOf("  ", "")))
        val parsed = journalParseBody("A normal reflection\nwith two lines")
        assertEquals("A normal reflection\nwith two lines", parsed.mainNote)
        assertTrue(parsed.subNotes.isEmpty())
    }

    @Test
    fun `expand prompt embeds the note and asks for a rewrite without inventing`() {
        val prompt = journalExpandPrompt("  ran 5k  ")
        assertTrue(prompt.contains("ran 5k"))
        assertTrue(prompt.contains("do not invent"))
    }

    @Test
    fun `serialize with only sub-notes omits the main note`() {
        val body = journalSerializeBody("", listOf("First", "Second"))
        assertEquals("• First\n• Second", body)
        val parsed = journalParseBody(body)
        assertEquals("", parsed.mainNote)
        assertEquals(listOf("First", "Second"), parsed.subNotes)
    }

    @Test
    fun `calendar weeks are aligned 7-day rows covering the whole month`() {
        val month = YearMonth.of(2026, 6)
        val weeks = journalCalendarWeeks(month, DayOfWeek.SUNDAY)

        // Every row is a full week.
        assertTrue(weeks.all { it.size == 7 })
        // The non-null cells are exactly the month's days, in order.
        val days = weeks.flatten().filterNotNull()
        assertEquals((1..30).map { month.atDay(it) }, days)
        // Leading blanks sit before the 1st, never after it.
        val firstRow = weeks.first()
        val firstDayIndex = firstRow.indexOfFirst { it != null }
        assertTrue(firstRow.take(firstDayIndex).all { it == null })
    }

    @Test
    fun `mood by date prefers the primary rated entry then any rated entry`() {
        val d1 = LocalDate.of(2026, 6, 10)
        val d2 = LocalDate.of(2026, 6, 11)
        val d3 = LocalDate.of(2026, 6, 12)
        val byDate = journalMoodByDate(
            listOf(
                entry(d1, rating = 4, isPrimary = true, id = "p1"),
                entry(d1, rating = 2, isPrimary = false, id = "s1"),
                entry(d2, rating = null, isPrimary = true, id = "p2"),
                entry(d2, rating = 5, isPrimary = false, id = "s2"),
                entry(d3, rating = null, isPrimary = true, id = "p3")
            )
        )
        assertEquals("Good", byDate[d1]?.label)   // primary's 4 wins over secondary's 2
        assertEquals("Great", byDate[d2]?.label)  // falls back to the only rated entry
        assertNull(byDate[d3])                     // unrated day is absent
    }

    @Test
    fun `workout detection keys on the imported id prefix`() {
        val date = LocalDate.of(2026, 6, 15)
        assertTrue(isJournalWorkoutEntry(workout(date)))
        assertFalse(isJournalWorkoutEntry(entry(date, rating = 4)))

        val entries = listOf(entry(date, rating = 4), workout(date), entry(date.minusDays(1)))
        assertEquals(setOf(date), journalWorkoutDates(entries))
        // A day with only a workout import is not counted as "journaled".
        assertEquals(setOf(date, date.minusDays(1)), journalWrittenDates(entries))
    }

    @Test
    fun `month summary recaps mood, journaled days and workouts within the month`() {
        val month = YearMonth.of(2026, 6)
        val entries = listOf(
            entry(LocalDate.of(2026, 6, 4), rating = 4),
            entry(LocalDate.of(2026, 6, 5), rating = 2),
            workout(LocalDate.of(2026, 6, 5)),
            entry(LocalDate.of(2026, 5, 30), rating = 5) // outside the month, ignored
        )
        val summary = journalMonthSummary(entries, month)
        assertTrue(summary.contains("avg"))
        assertTrue(summary.contains("2 days journaled"))
        assertTrue(summary.contains("1 workout"))

        assertEquals("", journalMonthSummary(emptyList(), month))
    }

    @Test
    fun `local insight summarizes cadence and never throws on empty`() {
        val today = LocalDate.of(2026, 6, 17)
        assertTrue(localJournalInsight(emptyList(), today).isNotBlank())
        val withEntries = localJournalInsight(
            listOf(entry(today, rating = 4), entry(today.minusDays(1), rating = 5)),
            today
        )
        assertTrue(withEntries.contains("2 days"))
        assertTrue(withEntries.contains("streak"))
    }
}
