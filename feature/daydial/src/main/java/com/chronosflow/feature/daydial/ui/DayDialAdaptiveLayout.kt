package com.chronosflow.feature.daydial.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

@Composable
internal fun dayDialAdaptiveWidthClass(): DayDialWidthClass {
    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    val windowSizeClass = adaptiveInfo.windowSizeClass
    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> {
            DayDialWidthClass.EXPANDED
        }
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
            DayDialWidthClass.MEDIUM
        }
        else -> DayDialWidthClass.COMPACT
    }
}

internal fun chronosDialMaxDiameter(widthClass: DayDialWidthClass): Dp = when (widthClass) {
    DayDialWidthClass.EXPANDED -> 380.dp
    DayDialWidthClass.MEDIUM -> 340.dp
    DayDialWidthClass.COMPACT -> 320.dp
}
