package com.chronosflow.wear

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TileBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Focus tile scaffold. This module is not yet wired into a Wear application and has no
 * access to focus-session data (no Wearable Data Layer, no shared repository), so the
 * tile intentionally renders a neutral resting state rather than fabricated progress.
 *
 * Shipping real progress requires: a wear application module that packages this service,
 * play-services-wearable Data Layer sync from the phone (or a shared on-watch store),
 * and a tile refresh strategy tied to focus-session state changes.
 */
class ChronosWearTileProvider : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val progressIndicator = CircularProgressIndicator.Builder()
            .setProgress(0f)
            .setCircularProgressIndicatorColors(ProgressIndicatorColors(ACCENT_COLOR, TRACK_COLOR))
            .build()

        val textElement = Text.Builder(this, RESTING_LABEL)
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(ColorBuilders.ColorProp.Builder(LABEL_COLOR).build())
            .build()

        val layout = LayoutElementBuilders.Box.Builder()
            .addContent(progressIndicator)
            .addContent(textElement)
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(layout)
                            .build()
                    )
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }

    companion object {
        const val RESTING_LABEL = "No active focus"
        const val RESOURCES_VERSION = "1"
        private val ACCENT_COLOR = 0xFF8B5CF6.toInt()
        private val TRACK_COLOR = 0x1A8B5CF6.toInt()
        private val LABEL_COLOR = 0xFFF3F4F6.toInt()
    }
}
