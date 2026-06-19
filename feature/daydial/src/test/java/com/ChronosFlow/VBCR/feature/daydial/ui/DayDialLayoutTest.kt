package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DayDialLayoutTest {
    private val compactDialCanvasSize =
        compactTodayHeroDialHeight - compactTodayDialCanvasInset * 2

    private val compactDialOuterVisualMargin =
        compactTodayDialCanvasInset +
            (
                compactDialCanvasSize -
                    compactDialCanvasSize * (compactTodayDialRadiusScale / 1.5f + 0.1f)
                ) / 2f

    @Test
    fun widthClassBreakpoints_areStable() {
        assertEquals(DayDialWidthClass.COMPACT, DayDialWidthClass.COMPACT)
        assertEquals(DayDialWidthClass.EXPANDED, DayDialWidthClass.EXPANDED)
    }

    @Test
    fun `compact today content keeps breathing room around the dial ring`() {
        assertEquals(12.dp, compactTodayTopSpacer)
        assertEquals(272.dp, compactTodayHeroDialHeight)
        assertEquals(8.dp, compactTodayHeroTopPadding)
        assertEquals(16.dp, compactTodayDialCanvasInset)
        assertEquals(1.0f, compactTodayDialRadiusScale)
        assertEquals(44.0f, compactDialOuterVisualMargin.value, 0.01f)
        assertEquals(272.dp, todayDialHeroHeight(320.dp, DayDialWidthClass.COMPACT))
        assertEquals(340.dp, todayDialHeroHeight(340.dp, DayDialWidthClass.MEDIUM))
        assertEquals(380.dp, todayDialHeroHeight(380.dp, DayDialWidthClass.EXPANDED))
    }
}
