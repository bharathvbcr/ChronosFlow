package com.chronosflow.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.ChronosTransitionFactory
import com.chronosflow.core.ui.motion.ChronosTransitionSet
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.settings.resolveChronosDarkTheme
import com.chronosflow.core.ui.theme.ChronosGlassTokens
import com.chronosflow.core.ui.theme.liquidGlass

/**
 * Shared chrome for every top header in the app: the floating glass pill bar.
 * DayDial and the standalone screen scaffold both compose their headers from
 * [ChronosGlassTopBarShell] so the surface, glass treatment, insets, and action
 * pills stay identical everywhere.
 */
object ChronosGlassTopBarDefaults {
    val ActionRailWidth = 88.dp
    val BarHeight = 58.dp
    val TopPadding = 12.dp
    val BottomPadding = 0.dp
    val GlassCornerRadius = 24.dp
    val BarCornerRadius = 30.dp
    const val BarWidthFraction = 0.94f
}

internal fun chronosGlassTopBarTitleTransition(reducedMotion: Boolean): ChronosTransitionSet =
    if (reducedMotion) {
        ChronosTransitionFactory.fadeScale(
            durationMillis = ChronosMotionDefaults.ReducedDurationMillis,
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Neutral,
            enterScale = 1f,
            exitScale = 1f
        )
    } else {
        ChronosTransitionFactory.fadeScale(
            durationMillis = ChronosMotionDefaults.DefaultDurationMillis,
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Neutral,
            enterScale = ChronosMotionDefaults.SharedAxisEnterScale,
            exitScale = ChronosMotionDefaults.SharedAxisExitScale
        )
    }

@Composable
fun ChronosGlassTopBarShell(
    darkTheme: Boolean,
    glassSurfacesEnabled: Boolean,
    highContrastEnabled: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable BoxScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val glassBar = glassSurfacesEnabled && !highContrastEnabled
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
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .fillMaxWidth()
            .padding(
                top = ChronosGlassTopBarDefaults.TopPadding,
                bottom = ChronosGlassTopBarDefaults.BottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(ChronosGlassTopBarDefaults.BarWidthFraction)
                .height(ChronosGlassTopBarDefaults.BarHeight)
                .then(
                    if (glassBar) {
                        // Intentionally the faux (translucency) glass, NOT chronosFrostedGlass: this top
                        // bar is per-screen chrome reused by every ChronosScreenScaffold, and those
                        // feature screens don't guarantee a `hazeSource` in their content. Real backdrop
                        // blur would sample an empty source and render wrong. Shell-level chrome that DOES
                        // sit over the shell's hazeSource (the floating nav bar / menu) uses frosted glass.
                        Modifier.liquidGlass(
                            cornerRadius = ChronosGlassTopBarDefaults.GlassCornerRadius,
                            blur = ChronosGlassTokens.AmbientBlur
                        )
                    } else {
                        Modifier
                    }
                ),
            color = barSurface,
            shape = RoundedCornerShape(ChronosGlassTopBarDefaults.BarCornerRadius),
            shadowElevation = if (glassBar) 0.dp else 8.dp,
            tonalElevation = if (glassBar) 0.dp else 5.dp,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (glassBar) {
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
                            .width(ChronosGlassTopBarDefaults.ActionRailWidth),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        leading()
                    }

                    // Keep the center content balanced against equal action rails.
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = ChronosGlassTopBarDefaults.ActionRailWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        center()
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(ChronosGlassTopBarDefaults.ActionRailWidth),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        trailing()
                    }
                }
            }
        }
    }
}

@Composable
fun ChronosTopBarPill(
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

/**
 * Standard trailing action that opens the command palette, kept identical
 * across DayDial and every standalone screen's glass bar.
 */
@Composable
fun ChronosCommandPaletteAction(onOpenCommandPalette: (() -> Unit)?) {
    if (onOpenCommandPalette != null) {
        ChronosTopBarPill(
            onClick = onOpenCommandPalette,
            tooltip = "Command palette",
            contentDescription = "Open command palette"
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
        }
    }
}

@Composable
fun ChronosGlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val uiSettings = rememberChronosUiSettings()
    val darkTheme = resolveChronosDarkTheme(uiSettings.appearanceMode)
    ChronosGlassTopBarShell(
        darkTheme = darkTheme,
        glassSurfacesEnabled = uiSettings.glassSurfacesEnabled,
        highContrastEnabled = uiSettings.highContrastEnabled,
        modifier = modifier,
        leading = {
            if (onBack != null) {
                ChronosTopBarPill(
                    onClick = onBack,
                    tooltip = "Back",
                    contentDescription = "Back"
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        },
        center = {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    chronosGlassTopBarTitleTransition(uiSettings.reduceMotionEnabled).asContentTransform()
                },
                label = "glassTopBarTitle"
            ) { currentTitle ->
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )
            }
        },
        trailing = actions
    )
}
