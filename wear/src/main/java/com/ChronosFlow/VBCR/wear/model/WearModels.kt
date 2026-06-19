package com.ChronosFlow.VBCR.wear.model

import com.ChronosFlow.VBCR.core.domain.wear.WearDaySummaryContract

/** A single open task mirrored from the phone. */
data class WearTask(val id: String, val title: String)

/** A schedule block as times only (titles never travel for the dial); minutes-of-day. */
data class WearBlock(val startMinute: Int, val endMinute: Int)

/** A single habit mirrored from the phone, with today's completion + streak. */
data class WearHabit(
    val id: String,
    val title: String,
    val streak: Int,
    val done: Boolean
)

/** A single medication dose mirrored from the phone. */
data class WearMed(
    val id: String,
    val name: String,
    val doseLabel: String,
    val reminderMinute: Int,
    val taken: Boolean
)

/**
 * The watch-side view of the phone's day summary. Built from the packed [WearDaySummaryContract]
 * entries by [parseTasks]/[parseHabits]/[parseMeds] so the rest of the watch (tiles + app) works
 * with real objects, never raw strings.
 */
data class WearDaySummary(
    val nowTitle: String? = null,
    val nowEndMinute: Int = 0,
    /** Id of the block happening now, so the watch can mark it complete; absent when none. */
    val nowBlockId: String? = null,
    /** Raw category of the current block (e.g. "WORK", "BREAK"); gates the "Start focus" action. */
    val nowCategory: String = "",
    /** Today's current + upcoming blocks for the day dial; times only. */
    val blocks: List<WearBlock> = emptyList(),
    /** Next upcoming *event* (non-break) — title + start; absent when no further event today. */
    val nextTitle: String? = null,
    val nextStartMinute: Int = 0,
    /** Next upcoming *break* — start always (0 = none), title only when not redacted. */
    val nextBreakStartMinute: Int = 0,
    val nextBreakTitle: String? = null,
    val openTaskCount: Int = 0,
    val tasks: List<WearTask> = emptyList(),
    val habitsDone: Int = 0,
    val habitsTotal: Int = 0,
    val habits: List<WearHabit> = emptyList(),
    val medsDueCount: Int = 0,
    val meds: List<WearMed> = emptyList(),
    /** One-line AI day digest mirrored from the phone; absent when redacted or not yet generated. */
    val digest: String? = null,
    /**
     * Wall-clock epoch millis when the phone last pushed this summary (0 = never synced on this
     * device). Lets the watch flag a schedule that may be out of date when the phone has been out
     * of reach — the "now"/"until"/dial claims are time-relative and silently rot otherwise.
     */
    val receivedAtMillis: Long = 0L
)

private val SEP = WearDaySummaryContract.FIELD_SEP

/** The block spanning now (start ≤ now < end), or null in a gap — the watch's "current block". */
fun currentBlock(blocks: List<WearBlock>, nowMinute: Int): WearBlock? =
    blocks.firstOrNull { nowMinute >= it.startMinute && nowMinute < it.endMinute }

/** How many blocks start strictly after now — the "N to go" tally for the glance. */
fun upcomingBlockCount(blocks: List<WearBlock>, nowMinute: Int): Int =
    blocks.count { it.startMinute > nowMinute }

fun parseBlocks(entries: List<String>): List<WearBlock> =
    entries.mapNotNull { entry ->
        val parts = entry.split(SEP, limit = 2)
        val start = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val end = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        WearBlock(startMinute = start, endMinute = end)
    }

fun parseTasks(entries: List<String>): List<WearTask> =
    entries.mapNotNull { entry ->
        val parts = entry.split(SEP, limit = 2)
        if (parts.size < 2) null else WearTask(id = parts[0], title = parts[1])
    }

fun parseHabits(entries: List<String>): List<WearHabit> =
    entries.mapNotNull { entry ->
        val parts = entry.split(SEP, limit = 4)
        if (parts.size < 4) return@mapNotNull null
        WearHabit(
            id = parts[0],
            done = parts[1] == "1",
            streak = parts[2].toIntOrNull() ?: 0,
            title = parts[3]
        )
    }

/**
 * Orders doses for an at-a-glance list: untaken first (earliest reminder first, so anything
 * already overdue floats to the top), taken doses last.
 */
fun sortMedsForGlance(meds: List<WearMed>): List<WearMed> =
    meds.sortedWith(compareBy({ it.taken }, { it.reminderMinute }))

fun parseMeds(entries: List<String>): List<WearMed> =
    entries.mapNotNull { entry ->
        val parts = entry.split(SEP, limit = 5)
        if (parts.size < 5) return@mapNotNull null
        WearMed(
            id = parts[0],
            taken = parts[1] == "1",
            reminderMinute = parts[2].toIntOrNull() ?: 0,
            doseLabel = parts[3],
            name = parts[4]
        )
    }
