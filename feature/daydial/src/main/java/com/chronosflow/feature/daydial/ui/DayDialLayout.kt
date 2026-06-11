package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.theme.ChronosSpacing

internal enum class DayDialWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

internal fun BoxWithConstraintsScope.dayDialWidthClass(): DayDialWidthClass = when {
    maxWidth >= 840.dp -> DayDialWidthClass.EXPANDED
    maxWidth >= 600.dp -> DayDialWidthClass.MEDIUM
    else -> DayDialWidthClass.COMPACT
}

/** Prefer [dayDialAdaptiveWidthClass] in Compose; this remains for unit tests. */
internal fun widthClassFromMaxWidth(maxWidth: Dp): DayDialWidthClass = when {
    maxWidth >= 840.dp -> DayDialWidthClass.EXPANDED
    maxWidth >= 600.dp -> DayDialWidthClass.MEDIUM
    else -> DayDialWidthClass.COMPACT
}

/** Breathing room between the dial and neighboring cards (hour labels, handles, center overlay). */
internal val chronosDialSectionVerticalPadding = ChronosSpacing.Medium
internal val chronosDialSectionHorizontalPadding = ChronosSpacing.Compact
internal val chronosDialCanvasInset = ChronosSpacing.Medium
internal val compactTodayTopSpacer = ChronosSpacing.Compact
internal val compactTodayHeroDialHeight = 272.dp
internal val compactTodayHeroTopPadding = ChronosSpacing.Small
internal val compactTodayDialCanvasInset = ChronosSpacing.Standard
// 1.0f = no upscaling, so the ring + hour labels keep their designed margin inside the
// hero box. Values >1 crowd the edges and push the 6 o'clock label to the box bottom,
// where the zoom-toggle AnimatedContent clips it mid-transition.
internal const val compactTodayDialRadiusScale = 1.0f

internal fun BoxWithConstraintsScope.dayDialHeightClass(): DayDialWidthClass = when {
    maxHeight >= 700.dp -> DayDialWidthClass.EXPANDED
    maxHeight >= 480.dp -> DayDialWidthClass.MEDIUM
    else -> DayDialWidthClass.COMPACT
}

internal fun BoxWithConstraintsScope.chronosDialHeight(
    widthClass: DayDialWidthClass = dayDialWidthClass()
): Dp {
    val landscape = maxWidth > maxHeight
    val capped = chronosDialMaxDiameter(widthClass)
    return when (widthClass) {
        DayDialWidthClass.EXPANDED -> capped
        DayDialWidthClass.MEDIUM -> if (landscape) 220.dp else capped.coerceAtMost(320.dp)
        DayDialWidthClass.COMPACT -> if (landscape) 200.dp else capped.coerceAtMost(maxWidth - 48.dp)
    }
}

internal fun todayDialHeroHeight(baseHeight: Dp, widthClass: DayDialWidthClass): Dp =
    if (widthClass == DayDialWidthClass.COMPACT) compactTodayHeroDialHeight else baseHeight

internal fun BoxWithConstraintsScope.useNavigationRail(): Boolean =
    dayDialWidthClass() != DayDialWidthClass.COMPACT

internal fun BoxWithConstraintsScope.useTodaySupportingPane(): Boolean =
    dayDialWidthClass() == DayDialWidthClass.EXPANDED
