package com.chronosflow.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.theme.ChronosSpacing
import kotlin.math.roundToInt

@Composable
fun ChronosMoodEnergyCheckInCard(
    onSave: (mood: Int, stress: Int, energy: Int, focus: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var mood by rememberSaveable { mutableIntStateOf(3) }
    var stress by rememberSaveable { mutableIntStateOf(2) }
    var energy by rememberSaveable { mutableIntStateOf(3) }
    var focus by rememberSaveable { mutableIntStateOf(3) }

    ChronosListCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(ChronosSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Mood & energy check-in", style = MaterialTheme.typography.titleSmall)
            MoodEnergyScoreRow(label = "Mood", value = mood, onValueChange = { mood = it })
            MoodEnergyScoreRow(label = "Stress", value = stress, onValueChange = { stress = it })
            MoodEnergyScoreRow(label = "Energy", value = energy, onValueChange = { energy = it })
            MoodEnergyScoreRow(label = "Focus", value = focus, onValueChange = { focus = it })
            ChronosButton(
                onClick = { onSave(mood, stress, energy, focus) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save check-in")
            }
        }
    }
}

@Composable
private fun MoodEnergyScoreRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
