package com.chronosflow.feature.daydial.dial

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.planner.DialRing
import com.chronosflow.feature.daydial.DialUtils.durationToSweep
import com.chronosflow.feature.daydial.DialUtils.durationToSweepInWindow
import com.chronosflow.feature.daydial.DialUtils.minuteToAngle
import com.chronosflow.feature.daydial.DialUtils.minuteToAngleInWindow
import com.chronosflow.feature.daydial.isInnerRingActionBlock
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import com.chronosflow.feature.daydial.model.TimeRangeUi
import com.chronosflow.feature.daydial.ringForBlock

data class DialBlockArc(
    val blockId: String,
    val startAngle: Float,
    val sweepAngle: Float,
    val color: Color,
    val ring: DialRing,
    val isSelected: Boolean,
    val isActive: Boolean,
    val isUpcoming: Boolean,
    val isMissed: Boolean,
    val isInnerAction: Boolean,
    val isLocked: Boolean,
    val isProtected: Boolean,
    val conflictOverlay: Boolean,
    val contentDescription: String
)

data class DialFreeTimeArc(
    val startAngle: Float,
    val sweepAngle: Float
)

data class DialHourTick(
    val angle: Float,
    val isMajor: Boolean,
    val label: String?
)

data class DialConflictOverlay(
    val blockId: String,
    val startAngle: Float,
    val sweepAngle: Float,
    val isBlocking: Boolean,
    val ring: DialRing
)

data class ChronosDialRenderModel(
    val blockArcs: List<DialBlockArc>,
    val freeTimeArcs: List<DialFreeTimeArc>,
    val hourTicks: List<DialHourTick>,
    val conflictOverlays: List<DialConflictOverlay>,
    val currentTimeAngle: Float,
    val selectedBlockId: String?,
    val windowMinutes: Int
)

object ChronosDialRenderModelBuilder {
    fun build(
        blocks: List<TimeBlockUiModel>,
        freeTimeSegments: List<TimeRangeUi>,
        selectedBlockId: String?,
        activeBlockId: String? = null,
        upcomingBlockId: String? = null,
        missedBlockIds: Set<String> = emptySet(),
        compactMode: Boolean,
        compactWindowStart: Int,
        conflictBlockIds: Set<String> = emptySet(),
        blockingConflictIds: Set<String> = emptySet()
    ): ChronosDialRenderModel {
        val windowMinutes = if (compactMode) 720 else 1440
        fun visibleStart(start: Int): Int {
            if (!compactMode) return start
            return ((start - compactWindowStart + 1440) % 1440)
        }

        val blockArcs = blocks.flatMap { block ->
            val visibleSlices = if (compactMode) {
                visibleWindowSlices(
                    startMinute = block.startMinuteOfDay,
                    durationMinutes = block.durationMinutes,
                    windowStart = compactWindowStart,
                    windowMinutes = windowMinutes
                )
            } else {
                listOf(VisibleWindowSlice(relativeStart = block.startMinuteOfDay, visibleDuration = block.durationMinutes))
            }
            visibleSlices.map { visibleSlice ->
                val start = if (compactMode) visibleSlice.relativeStart else visibleStart(block.startMinuteOfDay)
                val sweep = if (compactMode) {
                    durationToSweepInWindow(visibleSlice.visibleDuration, windowMinutes)
                } else {
                    durationToSweep(block.durationMinutes)
                }
                val startAngle = if (compactMode) {
                    minuteToAngleInWindow(start, 0, windowMinutes)
                } else {
                    minuteToAngle(start)
                }
                DialBlockArc(
                    blockId = block.id,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    color = block.color,
                    ring = ringForBlock(block),
                    isSelected = block.id == selectedBlockId,
                    isActive = block.id == activeBlockId,
                    isUpcoming = block.id == upcomingBlockId,
                    isMissed = block.id in missedBlockIds,
                    isInnerAction = block.isInnerRingActionBlock,
                    isLocked = block.isLocked,
                    isProtected = block.isProtected,
                    conflictOverlay = block.id in conflictBlockIds,
                    contentDescription = "${block.title}, ${visibleSlice.visibleDuration} minutes"
                )
            }
        }

        val freeTimeArcs = freeTimeSegments.flatMap { segment ->
            val duration = circularDuration(segment.startMinute, segment.endMinute)
            val visibleSlices = if (compactMode) {
                visibleWindowSlices(
                    startMinute = segment.startMinute,
                    durationMinutes = duration,
                    windowStart = compactWindowStart,
                    windowMinutes = windowMinutes
                )
            } else {
                listOf(VisibleWindowSlice(relativeStart = segment.startMinute, visibleDuration = duration))
            }
            visibleSlices.map { visibleSlice ->
                val start = if (compactMode) visibleSlice.relativeStart else visibleStart(segment.startMinute)
                val sweep = if (compactMode) {
                    durationToSweepInWindow(visibleSlice.visibleDuration, windowMinutes)
                } else {
                    durationToSweep(duration)
                }
                val startAngle = if (compactMode) {
                    minuteToAngleInWindow(start, 0, windowMinutes)
                } else {
                    minuteToAngle(start)
                }
                DialFreeTimeArc(startAngle = startAngle, sweepAngle = sweep)
            }
        }

        val hourTicks = (0 until 24 step if (compactMode) 2 else 1).map { hour ->
            val minute = hour * 60
            val angle = if (compactMode) {
                minuteToAngleInWindow(visibleStart(minute), 0, windowMinutes)
            } else {
                minuteToAngle(minute)
            }
            val isCardinalHour = hour == 0 || hour == 6 || hour == 12 || hour == 18
            // Cardinal hours read large; intermediate 3-hour marks get small labels so
            // times can be read at a glance without interpolating across 6-hour gaps.
            DialHourTick(
                angle = angle,
                isMajor = isCardinalHour,
                label = when {
                    hour == 0 -> "24"
                    isCardinalHour -> hour.toString()
                    !compactMode && hour % 3 == 0 -> hour.toString()
                    else -> null
                }
            )
        }

        val conflictOverlays = blocks
            .filter { it.id in conflictBlockIds }
            .flatMap { block ->
                val visibleSlices = if (compactMode) {
                    visibleWindowSlices(
                        startMinute = block.startMinuteOfDay,
                        durationMinutes = block.durationMinutes,
                        windowStart = compactWindowStart,
                        windowMinutes = windowMinutes
                    )
                } else {
                    listOf(VisibleWindowSlice(relativeStart = block.startMinuteOfDay, visibleDuration = block.durationMinutes))
                }
                visibleSlices.map { visibleSlice ->
                    val start = if (compactMode) visibleSlice.relativeStart else visibleStart(block.startMinuteOfDay)
                    val sweep = if (compactMode) {
                        durationToSweepInWindow(visibleSlice.visibleDuration, windowMinutes)
                    } else {
                        durationToSweep(block.durationMinutes)
                    }
                    val startAngle = if (compactMode) {
                        minuteToAngleInWindow(start, 0, windowMinutes)
                    } else {
                        minuteToAngle(start)
                    }
                    DialConflictOverlay(
                        blockId = block.id,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        isBlocking = block.id in blockingConflictIds,
                        ring = ringForBlock(block)
                    )
                }
            }

        return ChronosDialRenderModel(
            blockArcs = blockArcs,
            freeTimeArcs = freeTimeArcs,
            hourTicks = hourTicks,
            conflictOverlays = conflictOverlays,
            currentTimeAngle = 0f,
            selectedBlockId = selectedBlockId,
            windowMinutes = windowMinutes
        )
    }

    private fun circularDuration(startMinute: Int, endMinute: Int): Int {
        val duration = (endMinute - startMinute + 1440) % 1440
        return if (duration == 0) 1440 else duration
    }
}
