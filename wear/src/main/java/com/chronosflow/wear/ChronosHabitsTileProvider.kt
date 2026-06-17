package com.chronosflow.wear

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.chronosflow.wear.presentation.WearFormat
import com.chronosflow.wear.presentation.WearStartPage
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * "Habits" tile: today's habit progress and the top habits (done-check + streak), rendered on
 * the watch from the structured habit entries mirrored via [DaySummaryWearListenerService].
 */
class ChronosHabitsTileProvider : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val summary = DaySummaryStore.read(this)
        val rows = mutableListOf<LayoutElementBuilders.LayoutElement>(
            ChronosTileUi.title(this, "Habits"),
            ChronosTileUi.spacer(6f)
        )
        // "done today" is day-relative, so flag a mirror that's gone stale (e.g. across midnight).
        WearFormat.syncAgeLabel(summary.receivedAtMillis, System.currentTimeMillis())?.let { label ->
            rows += ChronosTileUi.caption(this, "⚠ $label", ChronosTileUi.WARN_COLOR)
            rows += ChronosTileUi.spacer(4f)
        }
        if (summary.habitsTotal == 0) {
            rows += ChronosTileUi.body(
                this,
                if (summary.receivedAtMillis == 0L) SYNC_LABEL else EMPTY_LABEL
            )
        } else {
            rows += ChronosTileUi.caption(this, "${summary.habitsDone}/${summary.habitsTotal} done today")
            rows += ChronosTileUi.spacer(4f)
            summary.habits.forEach { habit ->
                rows += ChronosTileUi.body(this, habitLine(habit))
            }
        }

        return Futures.immediateFuture(
            ChronosTileUi.tile(
                ChronosTileUi.column(
                    ChronosTileUi.launchModifiers(this, WearStartPage.HABITS),
                    *rows.toTypedArray()
                )
            )
        )
    }

    private fun habitLine(habit: com.chronosflow.wear.model.WearHabit): String = buildString {
        if (habit.done) append("✓ ")
        append(habit.title)
        if (habit.streak > 0) append(" · ${habit.streak}🔥")
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(ChronosTileUi.RESOURCES_VERSION)
                .build()
        )

    companion object {
        const val EMPTY_LABEL = "No active habits"
        const val SYNC_LABEL = "Open phone to sync"
    }
}
