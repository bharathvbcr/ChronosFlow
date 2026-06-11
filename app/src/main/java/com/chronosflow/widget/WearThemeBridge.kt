package com.chronosflow.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.toArgb
import com.chronosflow.core.domain.wear.WearThemeContract
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Data Layer payload for a theme palette: the accent roles in [WearThemeContract] `IDX_*`
 * order. Kept as a plain function (mirroring [toWearDaySummaryEntries]) so the publish contract
 * is unit-testable without Google Play services on the classpath.
 */
internal fun ColorScheme.toWearThemePalette(): LongArray = longArrayOf(
    primary.toArgb().toLong(),
    onPrimary.toArgb().toLong(),
    primaryContainer.toArgb().toLong(),
    onPrimaryContainer.toArgb().toLong(),
    secondary.toArgb().toLong(),
    onSecondary.toArgb().toLong(),
    secondaryContainer.toArgb().toLong(),
    onSecondaryContainer.toArgb().toLong(),
    tertiary.toArgb().toLong(),
    onTertiary.toArgb().toLong(),
    tertiaryContainer.toArgb().toLong(),
    onTertiaryContainer.toArgb().toLong()
)

/**
 * Mirrors the phone's Material You palette to the paired watch so the watch app and tiles can
 * render the wearer's wallpaper-derived colors (see the :wear module's ThemeWearListenerService).
 *
 * Always publishes the *dark* dynamic scheme — the watch renders on AMOLED black regardless of
 * the phone's light/dark setting. On devices without dynamic color (pre-Android 12) the mirror
 * is deleted instead, so the watch falls back to its own watch-face scheme or the shared brand
 * palette (which already matches the phone's static theme at compile time).
 *
 * All calls are best-effort and never throw — Wearable APIs are absent on devices without
 * Google Play services.
 */
@Singleton
class WearThemeBridge @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    fun publish() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val request = PutDataMapRequest.create(WearThemeContract.THEME_PATH).apply {
                    dataMap.putLongArray(
                        WearThemeContract.KEY_PALETTE,
                        dynamicDarkColorScheme(context).toWearThemePalette()
                    )
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request)
            } else {
                dataClient.deleteDataItems(
                    PutDataMapRequest.create(WearThemeContract.THEME_PATH).uri
                )
            }
        }
    }
}
