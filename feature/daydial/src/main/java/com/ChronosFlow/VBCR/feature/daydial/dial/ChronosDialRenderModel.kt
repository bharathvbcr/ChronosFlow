package com.ChronosFlow.VBCR.feature.daydial.dial

import androidx.compose.ui.graphics.Color
import com.ChronosFlow.VBCR.core.domain.planner.DialRing
import com.ChronosFlow.VBCR.feature.daydial.DialUtils.durationToSweep
import com.ChronosFlow.VBCR.feature.daydial.DialUtils.durationToSweepInWindow
import com.ChronosFlow.VBCR.feature.daydial.DialUtils.minuteToAngle
import com.ChronosFlow.VBCR.feature.daydial.DialUtils.minuteToAngleInWindow
import com.ChronosFlow.VBCR.feature.daydial.isInnerRingActionBlock
import com.ChronosFlow.VBCR.feature.daydial.model.TimeBlockUiModel
import com.ChronosFlow.VBCR.feature.daydial.model.TimeRangeUi
import com.ChronosFlow.VBCR.feature.daydial.ringForBlock

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

data class DialNightArc(
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
    val nightArcs: List<DialNightArc> = emptyList(),
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
        blockingConflictIds: Set<String> = emptySet(),
        nightStartMinute: Int = 21 * 60,
        nightEndMinute: Int = 7 * 60,
        nightBandVisible: Boolean = true
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

        // Sleep hours aren't schedulable (the planner rejects blocks there), so "open time"
        // shouldn't paint over the night band — otherwise the dashed free-time arc reads as a
        // second overlapping bar inside the sleep window. Clip free time to daytime first.
        val nightBandActive = nightBandVisible && (nightStartMinute % 1440) != (nightEndMinute % 1440)
        val daytimeFreeSegments = if (nightBandActive) {
            subtractNightFromFreeSegments(freeTimeSegments, nightStartMinute, nightEndMinute)
        } else {
            freeTimeSegments
        }

        val freeTimeArcs = daytimeFreeSegments.flatMap { segment ->
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

        // Night band: the sleep window drawn as a darker arc on the ring track. Built with the
        // same window-slice machinery as blocks so it clips/wraps correctly in the 12h window
        // and stays anchored to real clock time in both zoom levels.
        val nightArcs = if (!nightBandVisible || (nightStartMinute % 1440) == (nightEndMinute % 1440)) {
            emptyList()
        } else {
            val duration = circularDuration(nightStartMinute, nightEndMinute)
            val visibleSlices = if (compactMode) {
                visibleWindowSlices(
                    startMinute = nightStartMinute,
                    durationMinutes = duration,
                    windowStart = compactWindowStart,
                    windowMinutes = windowMinutes
                )
            } else {
                listOf(VisibleWindowSlice(relativeStart = nightStartMinute, visibleDuration = duration))
            }
            visibleSlices.map { visibleSlice ->
                val start = if (compactMode) visibleSlice.relativeStart else visibleStart(nightStartMinute)
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
                DialNightArc(startAngle = startAngle, sweepAngle = sweep)
            }
        }

        val hourTicks = (0 until 24 step if (compactMode) 2 else 1).mapNotNull { hour ->
            val minute = hour * 60
            val relativeMinute = visibleStart(minute)
            // In the 12h window only keep hours that actually fall inside it; otherwise
            // minuteToAngleInWindow wraps the off-window hours back onto the face and the
            // zoomed dial shows a full 24h worth of labels.
            if (compactMode && relativeMinute !in 0 until windowMinutes) return@mapNotNull null
            val angle = if (compactMode) {
                minuteToAngleInWindow(relativeMinute, 0, windowMinutes)
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
                    // The zoomed face is a 12-hour clock, so it uses clock numbering
                    // (16:00 reads "4"); the window pill below carries the AM/PM range.
                    compactMode -> clockHourLabel(hour)
                    hour == 0 -> "24"
                    isCardinalHour -> hour.toString()
                    hour % 3 == 0 -> hour.toString()
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
            nightArcs = nightArcs,
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

    /**
     * Removes the night window from each free-time segment, returning the remaining daytime
     * ranges. Works on a 1440-minute circle so overnight windows and wrapping segments are
     * handled uniformly. A segment fully inside the night window drops out entirely.
     */
    internal fun subtractNightFromFreeSegments(
        segments: List<TimeRangeUi>,
        nightStartMinute: Int,
        nightEndMinute: Int
    ): List<TimeRangeUi> {
        if (segments.isEmpty()) return segments
        val covered = BooleanArray(MINUTES_PER_DAY)
        segments.forEach { segment ->
            val duration = circularDuration(segment.startMinute, segment.endMinute)
            repeat(duration) { offset -> covered[wrapMinute(segment.startMinute + offset)] = true }
        }
        repeat(circularDuration(nightStartMinute, nightEndMinute)) { offset ->
            covered[wrapMinute(nightStartMinute + offset)] = false
        }
        if (covered.none { it }) return emptyList()
        if (covered.all { it }) return listOf(TimeRangeUi(0, 0))

        // Start scanning from a daytime (uncovered) minute so a run that straddles midnight
        // is emitted as one range instead of being split at index 0.
        val anchor = covered.indexOfFirst { !it }
        val result = mutableListOf<TimeRangeUi>()
        var runStart = -1
        for (step in 0 until MINUTES_PER_DAY) {
            val index = wrapMinute(anchor + step)
            if (covered[index]) {
                if (runStart == -1) runStart = index
            } else if (runStart != -1) {
                result += TimeRangeUi(runStart, index)
                runStart = -1
            }
        }
        if (runStart != -1) result += TimeRangeUi(runStart, anchor)
        return result
    }

    private fun wrapMinute(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    private const val MINUTES_PER_DAY = 1440
}

/** 12-hour clock numbering for the zoomed face: 0 and 12 read "12", 16 reads "4". */
internal fun clockHourLabel(hour: Int): String {
    val clockHour = hour % 12
    return if (clockHour == 0) "12" else clockHour.toString()
}
