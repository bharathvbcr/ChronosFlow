package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ChronosFlow.VBCR.core.ui.components.ChronosTooltipIconButton
import com.ChronosFlow.VBCR.core.ui.motion.ChronosValueAnimationFactory
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.theme.ChronosGlassTokens
import com.ChronosFlow.VBCR.core.ui.theme.GlassElevation
import com.ChronosFlow.VBCR.feature.daydial.model.SidebarPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateNavHeaderFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

@Composable
internal fun DateNav(
    selectedDate: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isViewingToday: Boolean = true,
    onToday: (() -> Unit)? = null
) {
    val previousDayLabel = dayNavigationActionLabel(selectedDate, dayOffset = -1)
    val nextDayLabel = dayNavigationActionLabel(selectedDate, dayOffset = 1)

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            CompactDateButton(
                onClick = onPrev,
                tooltip = previousDayLabel,
                contentDescription = previousDayLabel
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null)
            }
            Text(
                dateNavHeaderLabel(selectedDate),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (isViewingToday) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.widthIn(min = 98.dp)
            )
            CompactDateButton(
                onClick = onNext,
                tooltip = nextDayLabel,
                contentDescription = nextDayLabel
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
            if (!isViewingToday && onToday != null) {
                CompactDateButton(
                    onClick = onToday,
                    tooltip = DateNavJumpToTodayLabel,
                    contentDescription = DateNavJumpToTodayLabel
                ) {
                    Icon(
                        Icons.Filled.Today,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

internal const val DateNavJumpToTodayLabel = "Jump to today"

internal fun dateNavHeaderLabel(selectedDate: LocalDate): String =
    selectedDate.format(DateNavHeaderFormatter)

internal fun dayNavigationActionLabel(selectedDate: LocalDate, dayOffset: Int): String {
    val targetDate = selectedDate.plusDays(dayOffset.toLong())
    return "Go to ${targetDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CompactDateButton(
    onClick: () -> Unit,
    tooltip: String,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        ChronosTooltipIconButton(
            onClick = onClick,
            tooltip = tooltip,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp)
        ) {
            content()
        }
    }
}

@Composable
internal fun DayDialSidebar(
    activePage: SidebarPage?,
    dark: Boolean,
    glassSurfacesEnabled: Boolean,
    featureFlags: ChronosFeatureFlags,
    versionLabel: String = "0.1.0",
    onPageSelected: (SidebarPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (glassSurfacesEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.88f else 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        modifier = modifier.widthIn(min = 280.dp, max = 320.dp),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        border = if (glassSurfacesEnabled) {
            BorderStroke(
                GlassElevation.MEDIUM.borderWidth,
                ChronosGlassTokens.borderBrush(MaterialTheme.colorScheme.primary)
            )
        } else {
            null
        },
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            val scrollState = rememberScrollState()
            val reduceMotionEnabled = rememberChronosUiSettings().reduceMotionEnabled
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // SECTION 1: YOUR DAY
                Text(
                    text = "Your day",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics { heading() }
                )
                SidebarPage.rootPages(featureFlags).forEach { page ->
                    DrawerPillItem(
                        label = page.label,
                        selected = activePage == page,
                        icon = page.icon,
                        onClick = { onPageSelected(page) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION 2: TOOLS
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics { heading() }
                )
                SidebarPage.morePages
                    // The Journal page follows the journal feature flag, like its quick-add and sheet.
                    .filter { it != SidebarPage.JOURNAL || featureFlags.journalEnabled }
                    .forEach { page ->
                        DrawerPillItem(
                            label = page.label,
                            selected = activePage == page,
                            icon = page.icon,
                            onClick = { onPageSelected(page) }
                        )
                    }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION 3: SETTINGS
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics { heading() }
                )
                SidebarPage.settingsPages.forEach { page ->
                    DrawerPillItem(
                        label = page.label,
                        selected = activePage == page,
                        icon = page.icon,
                        onClick = { onPageSelected(page) }
                    )
                }
            }

            // Bottom fade cue so a height-capped panel reads as scrollable.
            val scrollFadeAlpha by animateFloatAsState(
                targetValue = if (scrollState.canScrollForward) 1f else 0f,
                animationSpec = ChronosValueAnimationFactory.selection(reduceMotionEnabled),
                label = "sidebarScrollFade"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .graphicsLayer { alpha = scrollFadeAlpha }
                    .background(
                        Brush.verticalGradient(
                            listOf(containerColor.copy(alpha = 0f), containerColor)
                        )
                    )
            )
            }

            // Footer Version Tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "v$versionLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DrawerPillItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val reduceMotionEnabled = rememberChronosUiSettings().reduceMotionEnabled
    val selectionSpec = ChronosValueAnimationFactory.selection<Color>(reduceMotionEnabled)

    val animatedBg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = selectionSpec,
        label = "drawerPillBg"
    )
    val animatedIconTint by animateColorAsState(
        targetValue = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = selectionSpec,
        label = "drawerPillIconTint"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (selected) primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = selectionSpec,
        label = "drawerPillTextColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(animatedBg)
            .chronosHapticClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(animationSpec = ChronosValueAnimationFactory.selection(reduceMotionEnabled)) +
                    fadeIn(animationSpec = ChronosValueAnimationFactory.selection(reduceMotionEnabled)),
                exit = shrinkHorizontally(animationSpec = ChronosValueAnimationFactory.selection(reduceMotionEnabled)) +
                    fadeOut(animationSpec = ChronosValueAnimationFactory.selection(reduceMotionEnabled))
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = animatedIconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = animatedTextColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
