package com.ChronosFlow.VBCR.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import com.ChronosFlow.VBCR.core.domain.wear.WearThemeContract
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.TileBuilders
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage

/**
 * Shared building blocks for the ChronosFlow tiles so the Today and Habits tiles stay visually
 * in step with each other (and with the focus tile's palette).
 */
internal object ChronosTileUi {
    const val RESOURCES_VERSION = "1"

    /** Re-render at least every 15 minutes so "Now / Next" lines don't go stale. */
    const val FRESHNESS_INTERVAL_MILLIS = 15 * 60 * 1000L

    // Neutrals stay local: they track the app theme's untouched Material default neutrals.
    val LABEL_COLOR = 0xFFF3F4F6.toInt()
    val MUTED_COLOR = 0xFF9CA3AF.toInt()

    /** Coral warning tint (mirrors the phone error/tertiary) for the stale-data caption. */
    val WARN_COLOR = 0xFFFFB4A8.toInt()

    /** Accent for titles and the focus ring: the phone's mirrored palette, or the brand teal. */
    fun accent(context: Context): Int =
        WearThemeStore.read(context)?.get(WearThemeContract.IDX_PRIMARY) ?: ChronosWearPalette.PRIMARY

    /** [accent] at 10% alpha; the focus ring's unfilled track. */
    fun accentTrack(context: Context): Int = (accent(context) and 0x00FFFFFF) or (0x1A shl 24)

    fun title(context: Context, text: String): LayoutElementBuilders.LayoutElement =
        text(context, text, Typography.TYPOGRAPHY_TITLE3, accent(context))

    fun body(context: Context, text: String): LayoutElementBuilders.LayoutElement =
        text(context, text, Typography.TYPOGRAPHY_BODY2, LABEL_COLOR)

    fun caption(
        context: Context,
        text: String,
        color: Int = MUTED_COLOR
    ): LayoutElementBuilders.LayoutElement =
        text(context, text, Typography.TYPOGRAPHY_CAPTION1, color)

    fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(heightDp))
            .build()

    fun column(
        modifiers: ModifiersBuilders.Modifiers? = null,
        vararg elements: LayoutElementBuilders.LayoutElement
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        elements.forEach { column.addContent(it) }
        val box = LayoutElementBuilders.Box.Builder().addContent(column.build())
        modifiers?.let { box.setModifiers(it) }
        return box.build()
    }

    /**
     * Modifiers that make a tile element tap-to-open the watch app at [startPage] (a
     * [WearStartPage] value). Without this a tile is a glanceable dead end; tapping should always
     * land the wearer on the matching page so they can act.
     */
    fun launchModifiers(context: Context, startPage: String): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(startPage)
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(context.packageName)
                                    .setClassName(MainActivity::class.java.name)
                                    .addKeyToExtraMapping(
                                        WearStartPage.EXTRA,
                                        ActionBuilders.AndroidStringExtra.Builder()
                                            .setValue(startPage)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    fun tile(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                            .build()
                    )
                    .build()
            )
            .build()

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safe = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    private fun text(
        context: Context,
        text: String,
        typography: Int,
        color: Int
    ): LayoutElementBuilders.LayoutElement =
        Text.Builder(context, text)
            .setTypography(typography)
            .setColor(ColorBuilders.ColorProp.Builder(color).build())
            .setMaxLines(1)
            .build()
}
