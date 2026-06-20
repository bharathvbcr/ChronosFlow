package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.motion.ChronosValueAnimationFactory
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing

/**
 * Standard progressive-disclosure section for form sheets: a tappable header showing
 * the section title and a live one-line summary of its current value, with the full
 * controls revealed beneath on expand. One pattern for every form — replaces the
 * ad-hoc collapsed-card / boolean-state variants.
 */
@Composable
fun ChronosCollapsibleSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val reduceMotionEnabled = rememberChronosUiSettings().reduceMotionEnabled
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ChronosValueAnimationFactory.selection(reduceMotionEnabled),
        label = "collapsibleSectionChevron"
    )

    ChronosCardSurface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotionEnabled)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = if (expanded) "Collapse $title" else "Expand $title" }
                    .chronosHapticClick(
                        onClick = { onExpandedChange(!expanded) },
                        onClickLabel = if (expanded) "Collapse $title" else "Expand $title"
                    )
                    .padding(ChronosSpacing.Standard),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = ChronosSpacing.Standard,
                        end = ChronosSpacing.Standard,
                        bottom = ChronosSpacing.Standard
                    ),
                    verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
                ) {
                    content()
                }
            }
        }
    }
}
