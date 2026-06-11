package com.chronosflow.wear.model

import com.chronosflow.core.domain.wear.WearDaySummaryContract

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
    /** Today's current + upcoming blocks for the day dial; times only. */
    val blocks: List<WearBlock> = emptyList(),
    val nextTitle: String? = null,
    val nextStartMinute: Int = 0,
    val openTaskCount: Int = 0,
    val tasks: List<WearTask> = emptyList(),
    val habitsDone: Int = 0,
    val habitsTotal: Int = 0,
    val habits: List<WearHabit> = emptyList(),
    val medsDueCount: Int = 0,
    val meds: List<WearMed> = emptyList(),
    /** One-line AI day digest mirrored from the phone; absent when redacted or not yet generated. */
    val digest: String? = null
)

private val SEP = WearDaySummaryContract.FIELD_SEP

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
