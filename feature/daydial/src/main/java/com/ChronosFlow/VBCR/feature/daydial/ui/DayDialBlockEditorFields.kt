package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.components.ChronosDurationSlider
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosTimePickerField
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import com.ChronosFlow.VBCR.core.ui.components.snapDurationMinutes
import com.ChronosFlow.VBCR.core.ui.theme.categoryColor
import java.util.Locale

internal const val BlockEditorMinDurationMinutes = 5
internal const val BlockEditorMaxDurationMinutes = 480

@Composable
internal fun DayDialBlockEditorFields(
    title: String,
    onTitleChange: (String) -> Unit,
    startText: String,
    onStartTextChange: (String) -> Unit,
    durationText: String,
    onDurationTextChange: (String) -> Unit,
    category: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        BlockEditorTimeFields(
            startText = startText,
            onStartTextChange = onStartTextChange,
            durationText = durationText,
            onDurationTextChange = onDurationTextChange,
        )
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BlockEditorCategoryChipSelector(
            selectedCategory = category,
            onCategorySelected = onCategorySelected,
        )
    }
}

@Composable
internal fun BlockEditorTimeFields(
    startText: String,
    onStartTextChange: (String) -> Unit,
    durationText: String,
    onDurationTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val parsedStartMinute = parseMinuteOfDay(startText)
    val startMinute = parsedStartMinute ?: 9 * 60
    val durationMinutes = durationText.toIntOrNull()
        ?.coerceIn(BlockEditorMinDurationMinutes, BlockEditorMaxDurationMinutes)
        ?: 25
    val endMinute = blockEditorEndMinute(startMinute, durationMinutes)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BlockTimeRangeSummary(
            start = startText,
            endTimeStr = formatMinuteOfDay(endMinute),
            durMin = durationMinutes
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChronosTimePickerField(
                label = "Start time",
                value = parsedStartMinute?.let(::formatMinuteOfDay) ?: startText.ifBlank { "Pick a time" },
                selectedMinute = startMinute,
                onTimeSelected = { onStartTextChange(formatMinuteOfDay(it)) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            ChronosTimePickerField(
                label = "End time",
                value = formatMinuteOfDay(endMinute),
                selectedMinute = endMinute,
                onTimeSelected = { selectedEnd ->
                    onDurationTextChange(blockEditorDurationFromEnd(startMinute, selectedEnd).toString())
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }

        ChronosDurationSlider(
            durationMinutes = durationMinutes,
            onDurationChange = { if (enabled) onDurationTextChange(it.toString()) },
            range = BlockEditorMinDurationMinutes..BlockEditorMaxDurationMinutes,
            label = "Adjust Duration"
        )
    }
}

internal fun snapBlockEditorDuration(value: Float): Int =
    snapDurationMinutes(value, BlockEditorMinDurationMinutes..BlockEditorMaxDurationMinutes)

internal fun blockEditorDurationFromEnd(startMinute: Int, endMinute: Int): Int {
    val rawDuration = ((endMinute - startMinute) % 1440 + 1440) % 1440
    return snapBlockEditorDuration(rawDuration.toFloat())
}

internal fun blockEditorEndMinute(startMinute: Int, durationMinutes: Int): Int =
    (startMinute + durationMinutes.coerceIn(BlockEditorMinDurationMinutes, BlockEditorMaxDurationMinutes)) % 1440

@Composable
private fun BlockTimeRangeSummary(
    start: String,
    endTimeStr: String,
    durMin: Int
) {
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Time range",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$start - $endTimeStr",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = formatDurationLabel(durMin),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun BlockEditorCategoryChipSelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    enabled: Boolean = true,
) {
    val categories = listOf("WORK", "BREAK", "MEETING", "ROUTINE", "MEDICATION", "RECOVERY", "PERSONAL")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val color = categoryColor(cat)
            val isSelected = selectedCategory.uppercase(Locale.getDefault()) == cat
            val baseColor = if (isSelected) color.copy(alpha = 0.18f) else Color.Transparent
            val borderCol = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textCol = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(baseColor)
                    .border(
                        width = 1.dp,
                        color = borderCol,
                        shape = MaterialTheme.shapes.small
                    )
                    .chronosHapticClick(onClick = { if (enabled) onCategorySelected(cat) }, enabled = enabled)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, RoundedCornerShape(100))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = cat.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textCol
                    )
                }
            }
        }
    }
}
