package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.ui.unit.Dp
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing

internal fun dayDialScrollableBottomPadding(
    contentBottomPadding: Dp,
    pageBottomPadding: Dp
): Dp = contentBottomPadding + pageBottomPadding

internal fun dayDialScrollableViewportBottomPadding(contentBottomPadding: Dp): Dp =
    contentBottomPadding.coerceAtMost(ChronosSpacing.Hero)
