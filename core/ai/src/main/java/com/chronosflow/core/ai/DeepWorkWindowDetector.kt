package com.chronosflow.core.ai

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.TimeBlock

data class DeepWorkWindow(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val durationMinutes: Int,
    val score: Int,
    val reason: String
)

class DeepWorkWindowDetector {
    fun detect(
        blocks: List<TimeBlock>,
        dayStartMinute: Int = 7 * 60,
        dayEndMinute: Int = 21 * 60,
        minimumDurationMinutes: Int = 90
    ): List<DeepWorkWindow> {
        val boundedStart = dayStartMinute.coerceIn(0, 1439)
        val boundedEnd = dayEndMinute.coerceIn(boundedStart + 1, 1440)
        val occupied = blocks
            .mapNotNull { block ->
                val start = block.startMinuteOfDay.coerceIn(boundedStart, boundedEnd)
                val end = (block.startMinuteOfDay + block.durationMinutes).coerceIn(boundedStart, boundedEnd)
                if (end > start) start to end else null
            }
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .fold(emptyList<Pair<Int, Int>>()) { acc, range ->
                val last = acc.lastOrNull()
                if (last == null || range.first > last.second) {
                    acc + range
                } else {
                    acc.dropLast(1) + (last.first to maxOf(last.second, range.second))
                }
            }

        val freeSegments = buildList {
            var cursor = boundedStart
            occupied.forEach { (start, end) ->
                if (start - cursor >= minimumDurationMinutes) add(cursor to start)
                cursor = maxOf(cursor, end)
            }
            if (boundedEnd - cursor >= minimumDurationMinutes) add(cursor to boundedEnd)
        }

        return freeSegments
            .map { (start, end) ->
                val duration = end - start
                val previous = blocks.lastOrNull { it.startMinuteOfDay + it.durationMinutes <= start }
                val next = blocks.firstOrNull { it.startMinuteOfDay >= end }
                val score = scoreWindow(start, end, duration, previous, next)
                DeepWorkWindow(
                    startMinuteOfDay = start,
                    endMinuteOfDay = end,
                    durationMinutes = duration,
                    score = score,
                    reason = buildReason(start, duration, previous, next)
                )
            }
            .sortedWith(compareByDescending<DeepWorkWindow> { it.score }.thenBy { it.startMinuteOfDay })
    }

    private fun scoreWindow(
        start: Int,
        end: Int,
        duration: Int,
        previous: TimeBlock?,
        next: TimeBlock?
    ): Int {
        var score = duration
        score += when {
            overlaps(start, end, 8 * 60, 11 * 60) -> 180
            overlaps(start, end, 11 * 60, 14 * 60) -> 80
            overlaps(start, end, 14 * 60, 17 * 60) -> 10
            else -> 0
        }
        if (duration >= 120) score += 20
        if (isFragmenting(previous)) score -= 15
        if (isFragmenting(next)) score -= 15
        if (end > 18 * 60) score -= 20
        return score.coerceAtLeast(0)
    }

    private fun isFragmenting(block: TimeBlock?): Boolean {
        if (block == null) return false
        val lowPriorityCategory = block.category.lowercase() in setOf("admin", "email", "recovery", "break")
        return lowPriorityCategory && block.flexibility != BlockFlexibility.FIXED
    }

    private fun buildReason(
        start: Int,
        duration: Int,
        previous: TimeBlock?,
        next: TimeBlock?
    ): String {
        val end = start + duration
        val dayPart = when {
            overlaps(start, end, 8 * 60, 11 * 60) -> "morning energy"
            overlaps(start, end, 11 * 60, 14 * 60) -> "midday continuity"
            overlaps(start, end, 14 * 60, 17 * 60) -> "afternoon availability"
            else -> "open schedule space"
        }
        val protection = if (isFragmenting(previous) || isFragmenting(next)) {
            " Move adjacent flexible low-priority work to protect the window."
        } else {
            ""
        }
        return "$duration minutes of $dayPart.$protection"
    }

    private fun overlaps(start: Int, end: Int, windowStart: Int, windowEnd: Int): Boolean {
        return start < windowEnd && end > windowStart
    }
}
