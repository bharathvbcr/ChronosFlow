package com.chronosflow.feature.daydial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosTimePickerField
import com.chronosflow.core.ui.theme.categoryColor
import java.util.Locale
import kotlin.math.roundToInt

private const val BlockEditorMinDurationMinutes = 5
private const val BlockEditorMaxDurationMinutes = 240
private const val BlockEditorDurationStepMinutes = 5

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
    val parsedStartMinute = parseEditorMinute(startText)
    val startMinute = parsedStartMinute ?: 9 * 60
    val durationMinutes = durationText.toIntOrNull()
        ?.coerceIn(BlockEditorMinDurationMinutes, BlockEditorMaxDurationMinutes)
        ?: 25
    val endMinute = blockEditorEndMinute(startMinute, durationMinutes)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )

        BlockTimeRangeSummary(
            start = startText,
            endTimeStr = formatEditorMinute(endMinute),
            durMin = durationMinutes
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChronosTimePickerField(
                label = "Start time",
                value = parsedStartMinute?.let(::formatEditorMinute) ?: startText.ifBlank { "Pick a time" },
                selectedMinute = startMinute,
                onTimeSelected = { onStartTextChange(formatEditorMinute(it)) },
                modifier = Modifier.weight(1f)
            )
            ChronosTimePickerField(
                label = "End time",
                value = formatEditorMinute(endMinute),
                selectedMinute = endMinute,
                onTimeSelected = { selectedEnd ->
                    onDurationTextChange(blockEditorDurationFromEnd(startMinute, selectedEnd).toString())
                },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Adjust Duration",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = durationMinutes.toFloat(),
            onValueChange = { onDurationTextChange(snapBlockEditorDuration(it).toString()) },
            valueRange = BlockEditorMinDurationMinutes.toFloat()..BlockEditorMaxDurationMinutes.toFloat(),
            steps = blockEditorDurationSliderSteps()
        )

        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CategoryChipSelector(
            selectedCategory = category,
            onCategorySelected = onCategorySelected
        )
    }
}

internal fun formatEditorMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    val h = (normalized / 60) % 24
    val m = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", h, m)
}

internal fun parseEditorMinute(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun blockEditorDurationSliderSteps(): Int =
    ((BlockEditorMaxDurationMinutes - BlockEditorMinDurationMinutes) / BlockEditorDurationStepMinutes) - 1

internal fun snapBlockEditorDuration(value: Float): Int {
    val snapped = BlockEditorMinDurationMinutes +
        (((value - BlockEditorMinDurationMinutes) / BlockEditorDurationStepMinutes).roundToInt() *
            BlockEditorDurationStepMinutes)
    return snapped.coerceIn(BlockEditorMinDurationMinutes, BlockEditorMaxDurationMinutes)
}

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
                    text = "${durMin}m",
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
private fun CategoryChipSelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
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
                    .clickable { onCategorySelected(cat) }
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
