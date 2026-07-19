package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ChronosFlow.VBCR.core.ui.motion.ChronosMotionDefaults
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import com.ChronosFlow.VBCR.core.ui.theme.LocalFocusAwareColorState

/**
 * Single muted placeholder bar for first-paint loading. Animates a shimmer brush when motion is
 * allowed; under reduced motion or high contrast it stays a solid `surfaceContainerHighest` fill.
 */
@Composable
fun ChronosSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    reduceMotionEnabled: Boolean = rememberChronosUiSettings().reduceMotionEnabled,
    highContrastEnabled: Boolean = LocalFocusAwareColorState.current.isHighContrast
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val brush = chronosSkeletonBrush(
        base = base,
        reduceMotionEnabled = reduceMotionEnabled,
        highContrastEnabled = highContrastEnabled
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * Multi-row list placeholder built from [ChronosSkeleton] cards. Prefer this over an indefinite
 * [androidx.compose.material3.CircularProgressIndicator] for list first paint.
 */
@Composable
fun ChronosShimmerPlaceholder(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    reduceMotionEnabled: Boolean = rememberChronosUiSettings().reduceMotionEnabled,
    highContrastEnabled: Boolean = LocalFocusAwareColorState.current.isHighContrast
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Loading"
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
    ) {
        repeat(rows.coerceAtLeast(1)) { index ->
            ChronosSkeletonListRow(
                modifier = Modifier.fillMaxWidth(),
                reduceMotionEnabled = reduceMotionEnabled,
                highContrastEnabled = highContrastEnabled,
                emphasizePrimary = index == 0
            )
        }
    }
}

@Composable
private fun ChronosSkeletonListRow(
    modifier: Modifier,
    reduceMotionEnabled: Boolean,
    highContrastEnabled: Boolean,
    emphasizePrimary: Boolean
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(ChronosSpacing.Compact)
            .height(ChronosSpacing.Hero + ChronosSpacing.Standard),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        ChronosSkeleton(
            modifier = Modifier
                .fillMaxWidth(if (emphasizePrimary) 0.72f else 0.55f)
                .height(ChronosSpacing.Standard),
            shape = RoundedCornerShape(ChronosSpacing.Micro),
            reduceMotionEnabled = reduceMotionEnabled,
            highContrastEnabled = highContrastEnabled
        )
        ChronosSkeleton(
            modifier = Modifier
                .fillMaxWidth(if (emphasizePrimary) 0.92f else 0.78f)
                .height(ChronosSpacing.Compact),
            shape = RoundedCornerShape(ChronosSpacing.Micro),
            reduceMotionEnabled = reduceMotionEnabled,
            highContrastEnabled = highContrastEnabled
        )
    }
}

@Composable
private fun chronosSkeletonBrush(
    base: Color,
    reduceMotionEnabled: Boolean,
    highContrastEnabled: Boolean
): Brush {
    if (reduceMotionEnabled || highContrastEnabled) {
        return Brush.linearGradient(listOf(base, base))
    }
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val transition = rememberInfiniteTransition(label = "chronosSkeletonShimmer")
    // Derived from existing motion tokens — avoid inventing a free-standing duration literal.
    val durationMillis = ChronosMotionDefaults.FocusProgressDurationMillis * 2
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chronosSkeletonShimmerShift"
    )
    val startX = -200f + (shift * 600f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(startX, 0f),
        end = Offset(startX + 200f, 200f)
    )
}
