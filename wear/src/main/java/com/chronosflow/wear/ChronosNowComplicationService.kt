package com.chronosflow.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.chronosflow.wear.model.WearDaySummary
import com.chronosflow.wear.presentation.WearFormat
import com.chronosflow.wear.presentation.WearStartPage

/**
 * The watch-face "Now" complication: the most glanceable datum from the mirrored day, rendered in
 * whichever slot type the chosen face exposes (SHORT_TEXT / LONG_TEXT / RANGED_VALUE). It tracks,
 * in priority order, a live focus session, the current schedule block, the next block, or a free
 * day — and taps through to the matching page of the watch app.
 *
 * Refreshes periodically (manifest UPDATE_PERIOD) plus on every Data Layer change (see
 * [requestChronosComplicationUpdates]); the values are point-in-time approximations of a moving
 * countdown, which is acceptable for a glance. The mapping is the pure [chronosNowComplicationContent].
 */
class ChronosNowComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildChronosComplicationData(
            this,
            type,
            ChronosComplicationContent(
                short = "40m",
                title = "Deep work",
                long = "Deep work · 40m left",
                progress = 0.6f,
                tapPage = WearStartPage.NOW
            )
        )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val now = java.time.LocalTime.now()
        val content = chronosNowComplicationContent(
            summary = DaySummaryStore.read(this),
            focus = WearFocusStateStore.read(this),
            nowMinute = now.hour * 60 + now.minute,
            nowMillis = System.currentTimeMillis()
        )
        return buildChronosComplicationData(this, request.complicationType, content) ?: NoDataComplicationData()
    }
}

/**
 * Maps the mirrored day [summary] + [focus] state to glanceable [ChronosComplicationContent] at the
 * given [nowMinute] (minute-of-day) / [nowMillis] (wall clock). Priority: live focus → current block
 * → next block → free. Pure so it is unit-tested without the complications framework.
 */
internal fun chronosNowComplicationContent(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    nowMinute: Int,
    nowMillis: Long
): ChronosComplicationContent {
    if (focus.active) {
        val secondsLeft = if (focus.paused) {
            focus.pausedTimeLeftSeconds
        } else {
            ((focus.plannedEndAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
        }
        val label = if (focus.paused) "Paused" else "Focus"
        val progress = if (focus.totalSeconds > 0) {
            (1f - secondsLeft.toFloat() / focus.totalSeconds).coerceIn(0f, 1f)
        } else null
        return ChronosComplicationContent(
            short = "${(secondsLeft + 59) / 60}m",
            title = label,
            long = "$label · ${WearFormat.mmss(secondsLeft)}",
            progress = progress,
            tapPage = WearStartPage.FOCUS,
            // A running session ticks live to its planned end; a paused one is frozen, so no countdown.
            countDownToMillis = if (focus.paused) null else focus.plannedEndAtMillis
        )
    }
    summary.nowTitle?.let { nowTitle ->
        val progress = summary.blocks
            .firstOrNull { nowMinute >= it.startMinute && nowMinute < it.endMinute }
            ?.let { block ->
                val span = (block.endMinute - block.startMinute).coerceAtLeast(1)
                ((nowMinute - block.startMinute).toFloat() / span).coerceIn(0f, 1f)
            }
        return ChronosComplicationContent(
            short = WearFormat.minutesWords((summary.nowEndMinute - nowMinute).coerceAtLeast(0)),
            title = nowTitle,
            long = "$nowTitle · ${WearFormat.remainingLabel(summary.nowEndMinute, nowMinute)}",
            progress = progress,
            tapPage = WearStartPage.NOW,
            // End instant = now + minutes remaining in the block, so the face ticks the countdown.
            countDownToMillis = nowMillis + (summary.nowEndMinute - nowMinute).coerceAtLeast(0) * 60_000L
        )
    }
    summary.nextTitle?.let { nextTitle ->
        return ChronosComplicationContent(
            short = "→${WearFormat.minutesWords((summary.nextStartMinute - nowMinute).coerceAtLeast(0))}",
            title = "Next",
            long = "Next: $nextTitle · ${WearFormat.startsInLabel(summary.nextStartMinute, nowMinute)}" +
                if (summary.nextBreakStartMinute > 0) {
                    " · Break ${WearFormat.startsInLabel(summary.nextBreakStartMinute, nowMinute)}"
                } else "",
            progress = null,
            tapPage = WearStartPage.NOW
        )
    }
    return ChronosComplicationContent(
        short = "Free",
        title = null,
        long = if (summary.receivedAtMillis == 0L) "Open on phone to sync" else "Nothing scheduled",
        progress = null,
        tapPage = WearStartPage.NOW
    )
}
