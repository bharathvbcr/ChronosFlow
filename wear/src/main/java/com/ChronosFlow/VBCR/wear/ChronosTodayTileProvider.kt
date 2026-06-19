package com.ChronosFlow.VBCR.wear

import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.ChronosFlow.VBCR.wear.presentation.WearFormat
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * "Now" tile — the single most useful glance, merging what used to be two tiles:
 *
 * - When a focus session is running it becomes the live focus ring (the same
 *   [WearFocusStateStore] state that backs the ongoing activity), with the time remaining.
 * - Otherwise it shows the current and next schedule blocks plus the open-task count, mirrored
 *   from the phone via [DaySummaryWearListenerService].
 *
 * Both halves render the last-known cache, so the tile degrades gracefully when the phone is
 * unreachable. A short freshness interval keeps the system re-requesting it while focus runs.
 *
 * Platform direction (Google I/O, May 2026): Wear OS 7 replaces full-screen Tiles with
 * "Wear Widgets" (Remote Compose). Existing ProtoLayout tiles keep working; a future pass
 * should target the Wear Widget format.
 */
class ChronosTodayTileProvider : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val focus = WearFocusStateStore.read(this)
        val root = if (focus.active) focusLayout(focus) else scheduleLayout()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(ChronosTileUi.RESOURCES_VERSION)
            .setFreshnessIntervalMillis(
                if (focus.active) ACTIVE_FRESHNESS_MILLIS else ChronosTileUi.FRESHNESS_INTERVAL_MILLIS
            )
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder().setRoot(root).build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    private fun scheduleLayout(): LayoutElementBuilders.LayoutElement {
        val summary = DaySummaryStore.read(this)
        val now = java.time.LocalTime.now()
        val nowMinute = now.hour * 60 + now.minute
        val rows = mutableListOf<LayoutElementBuilders.LayoutElement>(
            ChronosTileUi.title(this, "Now"),
            ChronosTileUi.spacer(6f)
        )
        // The schedule is time-relative; warn on the glance itself when the mirror is too old.
        WearFormat.syncAgeLabel(summary.receivedAtMillis, System.currentTimeMillis())?.let { label ->
            rows += ChronosTileUi.caption(this, "⚠ $label", ChronosTileUi.WARN_COLOR)
            rows += ChronosTileUi.spacer(4f)
        }
        val nowTitle = summary.nowTitle
        when {
            nowTitle != null -> {
                rows += ChronosTileUi.body(this, nowTitle)
                // Full start–end window when the dial knows this block, else the bare end time.
                val windowOrEnd = com.ChronosFlow.VBCR.wear.model.currentBlock(summary.blocks, nowMinute)
                    ?.let { WearFormat.windowLabel(it.startMinute, it.endMinute) }
                    ?: "until ${ChronosTileUi.formatMinuteOfDay(summary.nowEndMinute)}"
                rows += ChronosTileUi.caption(
                    this,
                    "${WearFormat.remainingLabel(summary.nowEndMinute, nowMinute)} · $windowOrEnd"
                )
            }
            summary.nextTitle == null -> rows += ChronosTileUi.body(
                this,
                if (summary.receivedAtMillis == 0L) SYNC_LABEL else EMPTY_LABEL
            )
        }
        summary.nextTitle?.let { next ->
            rows += ChronosTileUi.spacer(4f)
            rows += ChronosTileUi.body(
                this,
                "Next: $next · ${WearFormat.startsInLabel(summary.nextStartMinute, nowMinute)}"
            )
        }
        if (summary.nextBreakStartMinute > 0) {
            val breakName = summary.nextBreakTitle?.takeIf { it.isNotBlank() && !it.equals("Break", ignoreCase = true) }
            rows += ChronosTileUi.caption(
                this,
                "Break ${WearFormat.startsInLabel(summary.nextBreakStartMinute, nowMinute)}" +
                    (breakName?.let { " · $it" } ?: "")
            )
        }
        rows += ChronosTileUi.spacer(6f)
        rows += ChronosTileUi.caption(
            this,
            tileDayLine(
                com.ChronosFlow.VBCR.wear.model.upcomingBlockCount(summary.blocks, nowMinute),
                summary.openTaskCount
            )
        )
        // Present only when the phone syncs task entries (privacy redaction strips them).
        summary.tasks.firstOrNull()?.let { topTask ->
            rows += ChronosTileUi.caption(this, "Next: ${topTask.title}")
        }

        return ChronosTileUi.column(
            ChronosTileUi.launchModifiers(this, WearStartPage.NOW),
            *rows.toTypedArray()
        )
    }

    private fun focusLayout(state: WearFocusStateStore.FocusState): LayoutElementBuilders.LayoutElement {
        val remainingSeconds = if (state.paused) {
            state.pausedTimeLeftSeconds
        } else {
            ((state.plannedEndAtMillis - System.currentTimeMillis()) / 1000L).toInt()
        }.coerceAtLeast(0)
        val progress = if (state.totalSeconds > 0) {
            1f - remainingSeconds.toFloat() / state.totalSeconds.toFloat()
        } else {
            0f
        }.coerceIn(0f, 1f)

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, state.title ?: DEFAULT_ACTIVE_TITLE)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(ColorBuilders.ColorProp.Builder(ChronosTileUi.LABEL_COLOR).build())
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                Text.Builder(this, formatMmSs(remainingSeconds))
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY3)
                    .setColor(ColorBuilders.ColorProp.Builder(ChronosTileUi.accent(this)).build())
                    .build()
            )
            .addContent(
                Text.Builder(this, if (state.paused) PAUSED_LABEL else RUNNING_LABEL)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(ColorBuilders.ColorProp.Builder(ChronosTileUi.MUTED_COLOR).build())
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(ChronosTileUi.launchModifiers(this, WearStartPage.FOCUS))
            .addContent(
                CircularProgressIndicator.Builder()
                    .setProgress(progress)
                    .setCircularProgressIndicatorColors(
                        ProgressIndicatorColors(ChronosTileUi.accent(this), ChronosTileUi.accentTrack(this))
                    )
                    .build()
            )
            .addContent(column)
            .build()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(ChronosTileUi.RESOURCES_VERSION)
                .build()
        )

    private fun formatMmSs(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    companion object {
        const val EMPTY_LABEL = "Nothing scheduled"
        const val SYNC_LABEL = "Open phone to sync"
        const val RUNNING_LABEL = "Focusing"
        const val PAUSED_LABEL = "Paused"
        const val DEFAULT_ACTIVE_TITLE = "Focus session"
        private const val ACTIVE_FRESHNESS_MILLIS = 60 * 1000L
    }
}

/**
 * The schedule tile's bottom "day line": the remaining-block count folded in with the open-task
 * count ("2 blocks left · 3 tasks open"), so the tile carries the same schedule-load glance the
 * Now home page does without spending an extra row. Pure, so it is unit-tested directly.
 */
internal fun tileDayLine(blocksLeft: Int, openTaskCount: Int): String {
    val tasks = if (openTaskCount == 0) "No open tasks" else "$openTaskCount tasks open"
    return if (blocksLeft > 0) {
        "$blocksLeft block${if (blocksLeft == 1) "" else "s"} left · $tasks"
    } else {
        tasks
    }
}
