package com.chronosflow.feature.daydial

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.components.ChronosTooltipIconButton
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.ChronosTransitionFactory
import com.chronosflow.core.ui.motion.ChronosTransitionSet
import com.chronosflow.core.ui.theme.ChronosGlassTokens
import com.chronosflow.core.ui.theme.liquidGlass
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SidebarPage
import com.chronosflow.feature.daydial.ui.DateNav
import java.time.LocalDate

private val TopBarActionRailWidth = 88.dp
internal val dayDialTopBarTopPadding = 12.dp
internal val dayDialTopBarBottomPadding = 0.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDialTopBar(
    activeSidebarPage: SidebarPage?,
    currentTab: DayDialTab,
    selectedDate: LocalDate,
    canUndo: Boolean,
    canRedo: Boolean,
    darkTheme: Boolean,
    glassSurfacesEnabled: Boolean,
    highContrastEnabled: Boolean = false,
    onSelectDate: (LocalDate) -> Unit,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    reduceMotionEnabled: Boolean,
    onOpenCommandPalette: (() -> Unit)?
) {
    val barSurface = when {
        highContrastEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        glassSurfacesEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) 0.62f else 0.76f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val topBlurFade = remember(barSurface, darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                barSurface.copy(alpha = if (darkTheme) 0.36f else 0.28f),
                barSurface.copy(alpha = 0.10f),
                Color.Transparent,
                Color.Transparent
            )
        )
    }

    Box(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .fillMaxWidth()
            .padding(top = dayDialTopBarTopPadding, bottom = dayDialTopBarBottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(58.dp)
                .then(
                    if (glassSurfacesEnabled && !highContrastEnabled) {
                        Modifier.liquidGlass(cornerRadius = 24.dp, blur = ChronosGlassTokens.AmbientBlur)
                    } else {
                        Modifier
                    }
                ),
            color = barSurface,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = if (glassSurfacesEnabled && !highContrastEnabled) 0.dp else 8.dp,
            tonalElevation = if (glassSurfacesEnabled && !highContrastEnabled) 0.dp else 5.dp,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (glassSurfacesEnabled && !highContrastEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(topBlurFade)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(TopBarActionRailWidth),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        TopBarPill(
                            onClick = if (activeSidebarPage != null) onBackClick else onMenuClick,
                            tooltip = if (activeSidebarPage != null) "Back" else "Menu",
                            contentDescription = if (activeSidebarPage != null) "Back" else "Menu"
                        ) {
                            Icon(
                                imageVector = if (activeSidebarPage != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                                contentDescription = null
                            )
                        }
                    }

                    // Keep the title/date centered against equal action rails.
                    val topBarCenterKey = topBarCenterState(activeSidebarPage, currentTab)
                    AnimatedContent(
                        targetState = topBarCenterKey,
                        transitionSpec = {
                            topBarCenterTransition(
                                initialState = this.initialState,
                                targetState = this.targetState,
                                reducedMotion = reduceMotionEnabled
                            ).asContentTransform()
                        },
                        label = "topBarCenter",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = TopBarActionRailWidth)
                    ) {
                        DateNav(
                            selectedDate = selectedDate,
                            onPrev = { onSelectDate(selectedDate.minusDays(1)) },
                            onNext = { onSelectDate(selectedDate.plusDays(1)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(TopBarActionRailWidth),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canUndo || canRedo) {
                            var showHistoryMenu by remember { mutableStateOf(false) }
                            Box {
                                TopBarPill(
                                    onClick = { showHistoryMenu = true },
                                    tooltip = "History actions",
                                    contentDescription = "Open undo and redo actions"
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = showHistoryMenu,
                                    onDismissRequest = { showHistoryMenu = false }
                                ) {
                                    if (canUndo) {
                                        DropdownMenuItem(
                                            text = { Text("Undo") },
                                            onClick = {
                                                showHistoryMenu = false
                                                onUndo()
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                                            }
                                        )
                                    }
                                    if (canRedo) {
                                        DropdownMenuItem(
                                            text = { Text("Redo") },
                                            onClick = {
                                                showHistoryMenu = false
                                                onRedo()
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (onOpenCommandPalette != null) {
                            TopBarPill(
                                onClick = onOpenCommandPalette,
                                tooltip = "Command palette",
                                contentDescription = "Open command palette"
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun topBarCenterTransition(
    initialState: Pair<SidebarPage?, DayDialTab>,
    targetState: Pair<SidebarPage?, DayDialTab>,
    reducedMotion: Boolean
): ChronosTransitionSet {
    if (!shouldAnimateTopBarCenterTransition(initialState, targetState)) {
        return ChronosTransitionFactory.none()
    }

    if (reducedMotion) {
        return ChronosTransitionFactory.fadeScale(
            durationMillis = ChronosMotionDefaults.ReducedDurationMillis,
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Neutral,
            enterScale = 1f,
            exitScale = 1f
        )
    }

    val delta = topBarTargetIndex(targetState) - topBarTargetIndex(initialState)
    return ChronosTransitionFactory.materialSharedAxis(
        durationMillis = ChronosMotionDefaults.PrimaryTabDurationMillis,
        easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction = if (delta >= 0) {
            ChronosTransitionDirection.Forward
        } else {
            ChronosTransitionDirection.Backward
        },
        slideFraction = ChronosMotionDefaults.PrimaryTabSlideFraction,
        enterScale = ChronosMotionDefaults.PrimaryTabScale,
        exitScale = ChronosMotionDefaults.PrimaryTabScale
    )
}

internal fun shouldAnimateTopBarCenterTransition(
    initialState: Pair<SidebarPage?, DayDialTab>,
    targetState: Pair<SidebarPage?, DayDialTab>
): Boolean = initialState.first != null || targetState.first != null

@Suppress("UNUSED_PARAMETER")
internal fun topBarCenterState(
    activeSidebarPage: SidebarPage?,
    currentTab: DayDialTab
): Pair<SidebarPage?, DayDialTab> = null to DayDialTab.TODAY

private fun topBarTargetIndex(state: Pair<SidebarPage?, DayDialTab>): Int =
    if (state.first != null) 4 else when (state.second) {
        DayDialTab.PLAN -> 0
        DayDialTab.TODAY -> 1
        DayDialTab.FOCUS -> 2
        DayDialTab.INSIGHTS -> 3
    }

@Composable
private fun TopBarPill(
    onClick: () -> Unit,
    tooltip: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        ChronosTooltipIconButton(
            onClick = onClick,
            tooltip = tooltip,
            contentDescription = contentDescription,
            modifier = Modifier.size(40.dp)
        ) {
            content()
        }
    }
}
