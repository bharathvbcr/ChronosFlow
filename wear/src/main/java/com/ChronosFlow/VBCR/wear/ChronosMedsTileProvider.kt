package com.ChronosFlow.VBCR.wear

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.model.WearMed
import com.ChronosFlow.VBCR.wear.model.sortMedsForGlance
import com.ChronosFlow.VBCR.wear.presentation.WearFormat
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * "Meds" tile: today's medication — the due count and the next few doses (name + time, ✓ once
 * taken) — rendered from the day summary mirrored via [DaySummaryWearListenerService]. Medication is
 * the most time-critical glance in the app, so it earns a swipeable tile alongside Now and Habits,
 * matching the Meds complication and Meds app page that already exist.
 */
class ChronosMedsTileProvider : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val summary = DaySummaryStore.read(this)
        val rows = mutableListOf<LayoutElementBuilders.LayoutElement>(
            ChronosTileUi.title(this, "Meds"),
            ChronosTileUi.spacer(6f)
        )
        // "taken today" is day-relative, so flag a mirror that's gone stale (e.g. across midnight).
        WearFormat.syncAgeLabel(summary.receivedAtMillis, System.currentTimeMillis())?.let { label ->
            rows += ChronosTileUi.caption(this, "⚠ $label", ChronosTileUi.WARN_COLOR)
            rows += ChronosTileUi.spacer(4f)
        }
        when {
            summary.medsDueCount > 0 -> {
                rows += ChronosTileUi.caption(
                    this,
                    if (summary.medsDueCount == 1) "1 dose due" else "${summary.medsDueCount} doses due"
                )
                rows += ChronosTileUi.spacer(4f)
                // Only the phone-sent dose list (absent when privacy-redacted) can name doses; the
                // count line above still carries the glance when the list is hidden.
                sortMedsForGlance(summary.meds).take(MAX_ROWS).forEach { med ->
                    rows += ChronosTileUi.body(this, medLine(med))
                }
            }
            summary.meds.isNotEmpty() -> rows += ChronosTileUi.body(this, ALL_TAKEN_LABEL)
            else -> rows += ChronosTileUi.body(
                this,
                if (summary.receivedAtMillis == 0L) SYNC_LABEL else EMPTY_LABEL
            )
        }

        return Futures.immediateFuture(
            ChronosTileUi.tile(
                ChronosTileUi.column(
                    ChronosTileUi.launchModifiers(this, WearStartPage.MEDS, medsTileDescription(summary)),
                    *rows.toTypedArray()
                )
            )
        )
    }

    private fun medLine(med: WearMed): String = buildString {
        if (med.taken) append("✓ ")
        append(med.name)
        append(" · ${WearFormat.minuteOfDay(med.reminderMinute)}")
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(ChronosTileUi.RESOURCES_VERSION)
                .build()
        )

    companion object {
        const val EMPTY_LABEL = "No meds today"
        const val SYNC_LABEL = "Open phone to sync"
        const val ALL_TAKEN_LABEL = "All doses taken"
        private const val MAX_ROWS = 3
    }
}

/** Spoken TalkBack summary of the Meds tile's glance. Pure, so it is unit-tested directly. */
internal fun medsTileDescription(summary: WearDaySummary): String = when {
    summary.medsDueCount > 0 ->
        "Medication, ${summary.medsDueCount} ${if (summary.medsDueCount == 1) "dose" else "doses"} due"
    summary.meds.isNotEmpty() -> "Medication, all doses taken"
    summary.receivedAtMillis == 0L -> "Medication, open phone to sync"
    else -> "Medication, none today"
}
