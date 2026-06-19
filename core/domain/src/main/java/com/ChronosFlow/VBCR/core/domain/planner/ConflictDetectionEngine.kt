package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.ScheduleConflict
import com.ChronosFlow.VBCR.core.domain.model.ScheduleConflictSeverity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.model.occupiesScheduleTime
import javax.inject.Inject

class ConflictDetectionEngine @Inject constructor() {
    fun detect(blocks: List<TimeBlock>): List<ScheduleConflict> {
        val sorted = blocks
            .filter { it.occupiesScheduleTime() }
            .sortedWith(compareBy<TimeBlock> { it.date }.thenBy { it.startMinuteOfDay })
        return buildList {
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    val first = sorted[i]
                    val second = sorted[j]
                    if (first.date != second.date) continue
                    val overlap = overlap(first.startMinuteOfDay, first.durationMinutes, second.startMinuteOfDay, second.durationMinutes)
                    if (overlap != null) {
                        add(
                            ScheduleConflict(
                                primaryBlockId = first.id,
                                conflictingBlockId = second.id,
                                overlapStartMinute = overlap.first,
                                overlapEndMinute = overlap.second,
                                severity = if (first.isLocked || second.isLocked || first.isProtected || second.isProtected) {
                                    ScheduleConflictSeverity.BLOCKING
                                } else {
                                    ScheduleConflictSeverity.WARNING
                                },
                                reason = "Scheduled arcs overlap"
                            )
                        )
                    }
                }
            }
        }
    }

    fun previewMove(blocks: List<TimeBlock>, blockId: String, startMinute: Int, durationMinutes: Int): List<ScheduleConflict> {
        val target = blocks.firstOrNull { it.id == blockId } ?: return emptyList()
        val preview = blocks.map {
            if (it.id == blockId) {
                it.copy(startMinuteOfDay = normalizeMinute(startMinute), durationMinutes = durationMinutes.coerceIn(1, 1440))
            } else {
                it
            }
        }
        return detect(preview).filter { it.primaryBlockId == target.id || it.conflictingBlockId == target.id }
    }

    private fun overlap(startA: Int, durationA: Int, startB: Int, durationB: Int): Pair<Int, Int>? {
        val segmentsA = segments(startA, durationA)
        val segmentsB = segments(startB, durationB)
        for ((aStart, aEnd) in segmentsA) {
            for ((bStart, bEnd) in segmentsB) {
                val start = maxOf(aStart, bStart)
                val end = minOf(aEnd, bEnd)
                if (start < end) return start to end
            }
        }
        return null
    }

    private fun segments(startMinute: Int, duration: Int): List<Pair<Int, Int>> {
        val start = normalizeMinute(startMinute)
        val end = start + duration.coerceIn(1, 1440)
        return if (end <= 1440) listOf(start to end) else listOf(start to 1440, 0 to end - 1440)
    }

    private fun normalizeMinute(minute: Int): Int = ((minute % 1440) + 1440) % 1440
}
