package com.chronosflow.feature.daydial

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.chronosflow.core.ui.components.ChronosCommandPaletteAction
import com.chronosflow.core.ui.components.ChronosDropdownMenuItem
import com.chronosflow.core.ui.components.ChronosGlassTopBarDefaults
import com.chronosflow.core.ui.components.ChronosGlassTopBarShell
import com.chronosflow.core.ui.components.ChronosTopBarPill
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.ChronosTransitionFactory
import com.chronosflow.core.ui.motion.ChronosTransitionSet
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SidebarPage
import com.chronosflow.feature.daydial.ui.DateNav
import java.time.LocalDate

internal val dayDialTopBarTopPadding = ChronosGlassTopBarDefaults.TopPadding
internal val dayDialTopBarBottomPadding = ChronosGlassTopBarDefaults.BottomPadding

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
    menuExpanded: Boolean = false,
    onSelectDate: (LocalDate) -> Unit,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    reduceMotionEnabled: Boolean,
    onOpenCommandPalette: (() -> Unit)?
) {
    ChronosGlassTopBarShell(
        darkTheme = darkTheme,
        glassSurfacesEnabled = glassSurfacesEnabled,
        highContrastEnabled = highContrastEnabled,
        leading = {
            val menuLabel = dayDialMenuButtonLabel(activeSidebarPage, menuExpanded)
            ChronosTopBarPill(
                onClick = if (activeSidebarPage != null) onBackClick else onMenuClick,
                tooltip = menuLabel,
                contentDescription = menuLabel
            ) {
                Icon(
                    imageVector = when {
                        activeSidebarPage != null -> Icons.AutoMirrored.Filled.ArrowBack
                        menuExpanded -> Icons.Default.Close
                        else -> Icons.Default.Menu
                    },
                    contentDescription = null
                )
            }
        },
        center = {
            // Keep the title/date centered against equal action rails. Sidebar
            // pages show their own title so the bar actions read as one header.
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
                modifier = Modifier.fillMaxWidth()
            ) { centerState ->
                val centerPage = centerState.first
                if (centerPage != null) {
                    Text(
                        text = centerPage.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { heading() }
                    )
                } else {
                    DateNav(
                        selectedDate = selectedDate,
                        onPrev = { onSelectDate(selectedDate.minusDays(1)) },
                        onNext = { onSelectDate(selectedDate.plusDays(1)) },
                        isViewingToday = selectedDate == LocalDate.now(),
                        onToday = { onSelectDate(LocalDate.now()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        trailing = {
            if (canUndo || canRedo) {
                var showHistoryMenu by remember { mutableStateOf(false) }
                Box {
                    ChronosTopBarPill(
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
                            ChronosDropdownMenuItem(
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
                            ChronosDropdownMenuItem(
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
            ChronosCommandPaletteAction(onOpenCommandPalette)
        }
    )
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

internal fun dayDialMenuButtonLabel(
    activeSidebarPage: SidebarPage?,
    menuExpanded: Boolean
): String = when {
    activeSidebarPage != null -> "Back"
    menuExpanded -> "Close menu"
    else -> "Menu"
}

internal fun shouldAnimateTopBarCenterTransition(
    initialState: Pair<SidebarPage?, DayDialTab>,
    targetState: Pair<SidebarPage?, DayDialTab>
): Boolean = initialState.first != null || targetState.first != null

// Primary tabs all share one date state so tab switches never re-animate the
// center; sidebar pages surface their own title in the bar.
@Suppress("UNUSED_PARAMETER")
internal fun topBarCenterState(
    activeSidebarPage: SidebarPage?,
    currentTab: DayDialTab
): Pair<SidebarPage?, DayDialTab> = activeSidebarPage to DayDialTab.TODAY

private fun topBarTargetIndex(state: Pair<SidebarPage?, DayDialTab>): Int =
    if (state.first != null) 4 else when (state.second) {
        DayDialTab.PLAN -> 0
        DayDialTab.TODAY -> 1
        DayDialTab.FOCUS -> 2
        DayDialTab.INSIGHTS -> 3
    }
