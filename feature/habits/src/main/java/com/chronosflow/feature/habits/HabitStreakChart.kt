package com.chronosflow.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chronosflow.core.domain.usecase.HabitStreak
import com.chronosflow.core.ui.components.ChronosListCard
import kotlin.math.max

@Composable
fun HabitStreakChart(
    streaks: List<HabitStreak>,
    modifier: Modifier = Modifier
) {
    val visibleStreaks = streaks.take(5)
    val maxStreak = max(1, visibleStreaks.maxOfOrNull { it.streakCount } ?: 1)

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Streaks", style = MaterialTheme.typography.titleSmall)
            if (visibleStreaks.isEmpty()) {
                Text(
                    "Complete a habit to start a streak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                visibleStreaks.forEach { streak ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            streak.title,
                            modifier = Modifier.width(96.dp),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(streak.streakCount / maxStreak.toFloat())
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text(
                            "${streak.streakCount}d",
                            modifier = Modifier.width(36.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
