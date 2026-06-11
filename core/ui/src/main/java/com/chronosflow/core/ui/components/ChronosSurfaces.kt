package com.chronosflow.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.theme.ChronosGlassTokens
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.core.ui.theme.GlassElevation
import com.chronosflow.core.ui.theme.GlassTone
import com.chronosflow.core.ui.theme.chronosGlass

@Composable
fun ChronosBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val colors = if (highContrast) {
        listOf(
            if (darkTheme) Color.Black else Color.White,
            scheme.background
        )
    } else if (darkTheme) {
        listOf(
            scheme.primaryContainer.copy(alpha = 0.45f),
            scheme.background,
            scheme.surface
        )
    } else {
        listOf(
            scheme.primaryContainer.copy(alpha = 0.35f),
            scheme.surfaceVariant,
            scheme.background
        )
    }
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        Box(
            modifier = modifier.background(
                Brush.radialGradient(colors = colors, radius = 1400f)
            )
        ) {
            content()
        }
    }
}

@Composable
fun ChronosGlassPanel(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ChronosGlassTokens.StandardRadius,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val panelModifier = if (enabled) {
        modifier.chronosGlass(
            shape = shape,
            tone = GlassTone.PROMINENT,
            elevation = GlassElevation.LOW
        )
    } else {
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier = panelModifier.padding(contentPadding)) {
            content()
        }
    }
}

/**
 * The one page-level header: icon chip + title + subtitle. Used by DayDial
 * sidebar pages and the standalone feature screens so every page opens the
 * same way under the shared glass top bar.
 */
@Composable
fun ChronosPageHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Standard)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(10.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}

@Composable
fun ChronosSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun ChronosMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    ChronosCardSurface(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(ChronosSpacing.Standard),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

@Composable
fun ChronosActionTile(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ChronosCardSurface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ChronosEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    ChronosCardSurface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(2.dp))
                action()
            }
        }
    }
}

@Composable
fun ChronosWarningBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val highContrast = rememberChronosUiSettings().highContrastEnabled
    val accent = MaterialTheme.colorScheme.tertiary
    val containerColor = if (highContrast) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        accent.copy(alpha = 0.13f)
    }
    val borderColor = if (highContrast) {
        MaterialTheme.colorScheme.outline
    } else {
        accent.copy(alpha = 0.35f)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ChronosGlassTokens.CompactRadius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(ChronosSpacing.Standard), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (highContrast) MaterialTheme.colorScheme.onSurface else accent
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
