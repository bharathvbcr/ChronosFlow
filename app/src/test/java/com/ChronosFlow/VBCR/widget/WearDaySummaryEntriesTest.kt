package com.ChronosFlow.VBCR.widget

import com.ChronosFlow.VBCR.core.domain.model.ChronosDayOverview
import com.ChronosFlow.VBCR.core.domain.model.DayOverviewBlock
import com.ChronosFlow.VBCR.core.domain.model.DayOverviewHabit
import com.ChronosFlow.VBCR.core.domain.model.DayOverviewMedication
import com.ChronosFlow.VBCR.core.domain.model.DayOverviewTask
import com.ChronosFlow.VBCR.core.domain.wear.WearDaySummaryContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WearDaySummaryEntriesTest {

    private val sep = WearDaySummaryContract.FIELD_SEP

    @Test
    fun `digest publishes only when titles are not redacted`() {
        val overview = ChronosDayOverview()

        val open = overview.toWearDaySummaryEntries(redactTitles = false, digest = "2 blocks done; 3 tasks open.")
        val redacted = overview.toWearDaySummaryEntries(redactTitles = true, digest = "2 blocks done; 3 tasks open.")
        val absent = overview.toWearDaySummaryEntries(redactTitles = false, digest = "  ")

        assertEquals("2 blocks done; 3 tasks open.", open[WearDaySummaryContract.KEY_DIGEST])
        assertFalse(redacted.containsKey(WearDaySummaryContract.KEY_DIGEST))
        assertFalse(absent.containsKey(WearDaySummaryContract.KEY_DIGEST))
    }

    @Test
    fun `full overview maps every contract key with packed ids`() {
        val overview = ChronosDayOverview(
            blocks = listOf(
                DayOverviewBlock(id = "b1", title = "Deep work", startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60, isCurrent = true),
                DayOverviewBlock(id = "b2", title = "Gym", startMinuteOfDay = 16 * 60, endMinuteOfDay = 17 * 60, isCurrent = false)
            ),
            openTasks = listOf(
                DayOverviewTask(id = "t1", title = "File taxes", priority = 2),
                DayOverviewTask(id = "t2", title = "Email Sam", priority = 0)
            ),
            habits = listOf(
                DayOverviewHabit(id = "h1", title = "Morning walk", streakCount = 7, isDoneToday = false),
                DayOverviewHabit(id = "h2", title = "Stretch", streakCount = 0, isDoneToday = true)
            ),
            medications = listOf(
                DayOverviewMedication(id = "m1", name = "Vitamin D", doseLabel = "1 tablet", reminderMinuteOfDay = 8 * 60, isTakenToday = false),
                DayOverviewMedication(id = "m2", name = "Omega 3", doseLabel = "2 caps", reminderMinuteOfDay = 20 * 60, isTakenToday = true)
            )
        )

        val entries = overview.toWearDaySummaryEntries()

        assertEquals("Deep work", entries[WearDaySummaryContract.KEY_NOW_TITLE])
        assertEquals(10 * 60, entries[WearDaySummaryContract.KEY_NOW_END_MINUTE])
        assertEquals("b1", entries[WearDaySummaryContract.KEY_NOW_BLOCK_ID])
        assertEquals("Gym", entries[WearDaySummaryContract.KEY_NEXT_TITLE])
        assertEquals(16 * 60, entries[WearDaySummaryContract.KEY_NEXT_START_MINUTE])
        assertArrayEquals(
            arrayOf("${9 * 60}$sep${10 * 60}", "${16 * 60}$sep${17 * 60}"),
            entries[WearDaySummaryContract.KEY_BLOCK_ENTRIES] as Array<*>
        )
        assertEquals(2, entries[WearDaySummaryContract.KEY_OPEN_TASK_COUNT])
        assertArrayEquals(
            arrayOf("t1${sep}File taxes", "t2${sep}Email Sam"),
            entries[WearDaySummaryContract.KEY_TASK_ENTRIES] as Array<*>
        )
        assertEquals(1, entries[WearDaySummaryContract.KEY_HABITS_DONE])
        assertEquals(2, entries[WearDaySummaryContract.KEY_HABITS_TOTAL])
        assertArrayEquals(
            arrayOf("h1${sep}0${sep}7${sep}Morning walk", "h2${sep}1${sep}0${sep}Stretch"),
            entries[WearDaySummaryContract.KEY_HABIT_ENTRIES] as Array<*>
        )
        assertEquals(1, entries[WearDaySummaryContract.KEY_MEDS_DUE_COUNT])
        assertArrayEquals(
            arrayOf(
                "m1${sep}0${sep}${8 * 60}${sep}1 tablet${sep}Vitamin D",
                "m2${sep}1${sep}${20 * 60}${sep}2 caps${sep}Omega 3"
            ),
            entries[WearDaySummaryContract.KEY_MED_ENTRIES] as Array<*>
        )
    }

    @Test
    fun `next break and next event are split, and now-category flows`() {
        val overview = ChronosDayOverview(
            blocks = listOf(
                DayOverviewBlock(id = "b1", title = "Deep work", startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60, isCurrent = true, category = "WORK"),
                DayOverviewBlock(id = "b2", title = "Coffee", startMinuteOfDay = 15 * 60, endMinuteOfDay = 15 * 60 + 15, isCurrent = false, category = "BREAK"),
                DayOverviewBlock(id = "b3", title = "Gym", startMinuteOfDay = 16 * 60, endMinuteOfDay = 17 * 60, isCurrent = false, category = "WORK")
            )
        )

        val entries = overview.toWearDaySummaryEntries()

        // Current block's category flows for the watch's focus gating.
        assertEquals("WORK", entries[WearDaySummaryContract.KEY_NOW_CATEGORY])
        // "Next" is the next non-break EVENT (Gym), skipping the sooner break.
        assertEquals("Gym", entries[WearDaySummaryContract.KEY_NEXT_TITLE])
        assertEquals(16 * 60, entries[WearDaySummaryContract.KEY_NEXT_START_MINUTE])
        // The break is carried separately.
        assertEquals(15 * 60, entries[WearDaySummaryContract.KEY_NEXT_BREAK_START_MINUTE])
        assertEquals("Coffee", entries[WearDaySummaryContract.KEY_NEXT_BREAK_TITLE])
    }

    @Test
    fun `redaction drops the next-break title but keeps its start time`() {
        val overview = ChronosDayOverview(
            blocks = listOf(
                DayOverviewBlock(id = "b1", title = "Therapy", startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60, isCurrent = true, category = "WORK"),
                DayOverviewBlock(id = "b2", title = "Lunch", startMinuteOfDay = 12 * 60, endMinuteOfDay = 12 * 60 + 45, isCurrent = false, category = "BREAK")
            )
        )

        val entries = overview.toWearDaySummaryEntries(redactTitles = true)

        assertEquals(12 * 60, entries[WearDaySummaryContract.KEY_NEXT_BREAK_START_MINUTE])
        assertFalse(entries.containsKey(WearDaySummaryContract.KEY_NEXT_BREAK_TITLE))
    }

    @Test
    fun `redaction keeps times and counts but strips every per-item payload`() {
        val overview = ChronosDayOverview(
            blocks = listOf(
                DayOverviewBlock(id = "b1", title = "Therapy", startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60, isCurrent = true)
            ),
            openTasks = listOf(DayOverviewTask(id = "t1", title = "Call doctor", priority = 2)),
            habits = listOf(DayOverviewHabit(id = "h1", title = "Take meds", streakCount = 3, isDoneToday = false)),
            medications = listOf(DayOverviewMedication(id = "m1", name = "Sertraline", doseLabel = "1 tablet", reminderMinuteOfDay = 9 * 60, isTakenToday = false))
        )

        val entries = overview.toWearDaySummaryEntries(redactTitles = true)

        assertEquals(REDACTED_BLOCK_TITLE, entries[WearDaySummaryContract.KEY_NOW_TITLE])
        assertEquals(10 * 60, entries[WearDaySummaryContract.KEY_NOW_END_MINUTE])
        // The bare id carries no title, so it still flows under redaction (the watch can act on it).
        assertEquals("b1", entries[WearDaySummaryContract.KEY_NOW_BLOCK_ID])
        // Block times carry no titles, so the dial survives redaction.
        assertArrayEquals(
            arrayOf("${9 * 60}$sep${10 * 60}"),
            entries[WearDaySummaryContract.KEY_BLOCK_ENTRIES] as Array<*>
        )
        assertEquals(1, entries[WearDaySummaryContract.KEY_OPEN_TASK_COUNT])
        assertEquals(1, entries[WearDaySummaryContract.KEY_HABITS_TOTAL])
        assertEquals(1, entries[WearDaySummaryContract.KEY_MEDS_DUE_COUNT])
        assertArrayEquals(emptyArray<String>(), entries[WearDaySummaryContract.KEY_TASK_ENTRIES] as Array<*>)
        assertArrayEquals(emptyArray<String>(), entries[WearDaySummaryContract.KEY_HABIT_ENTRIES] as Array<*>)
        assertArrayEquals(emptyArray<String>(), entries[WearDaySummaryContract.KEY_MED_ENTRIES] as Array<*>)
    }

    @Test
    fun `empty overview omits block keys but keeps counts`() {
        val entries = ChronosDayOverview().toWearDaySummaryEntries()

        assertFalse(entries.containsKey(WearDaySummaryContract.KEY_NOW_TITLE))
        assertFalse(entries.containsKey(WearDaySummaryContract.KEY_NOW_BLOCK_ID))
        assertFalse(entries.containsKey(WearDaySummaryContract.KEY_NEXT_TITLE))
        assertEquals(0, entries[WearDaySummaryContract.KEY_OPEN_TASK_COUNT])
        assertEquals(0, entries[WearDaySummaryContract.KEY_HABITS_TOTAL])
        assertEquals(0, entries[WearDaySummaryContract.KEY_MEDS_DUE_COUNT])
        assertArrayEquals(emptyArray<String>(), entries[WearDaySummaryContract.KEY_TASK_ENTRIES] as Array<*>)
    }
}
