package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.model.occupiesScheduleTime
import javax.inject.Inject

data class FreeTimeSegment(
    val startMinute: Int,
    val endMinute: Int
)

class FreeTimeCalculator @Inject constructor() {
    fun calculate(blocks: List<TimeBlock>): List<FreeTimeSegment> {
        val taken = BooleanArray(1440)
        blocks.filter { it.occupiesScheduleTime() }.forEach { block ->
            val start = block.startMinuteOfDay
            val end = start + block.durationMinutes
            if (end <= 1440) {
                for (minute in start until end) taken[minute % 1440] = true
            } else {
                for (minute in start until 1440) taken[minute] = true
                for (minute in 0 until end % 1440) taken[minute] = true
            }
        }

        val freeRanges = mutableListOf<FreeTimeSegment>()
        var cursor: Int? = null
        for (minute in 0 until 1440) {
            if (!taken[minute] && cursor == null) {
                cursor = minute
            }
            if ((taken[minute] || minute == 1439) && cursor != null) {
                val end = if (taken[minute]) minute else minute + 1
                if (end > cursor) freeRanges.add(FreeTimeSegment(cursor, end))
                cursor = null
            }
        }
        return freeRanges
    }
}
