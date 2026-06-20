package com.ChronosFlow.VBCR.feature.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.theme.ChronosColors

@Composable
fun MedicationAdherenceChart(
    plans: List<MedicationPlan>,
    modifier: Modifier = Modifier
) {
    val activePlans = remember(plans) { plans.filter { it.isActive } }
    val totalMissed = activePlans.sumOf { it.missedCount }
    val totalExpected = activePlans.sumOf { (it.missedCount + 7).coerceAtLeast(7) }
    val adherence = if (totalExpected == 0) 1f else ((totalExpected - totalMissed).toFloat() / totalExpected).coerceIn(0f, 1f)

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Adherence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${(adherence * 100).toInt()}% estimated this week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "$totalMissed missed",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (totalMissed == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(adherence)
                            .height(14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(adherenceColor(adherence))
                    )
                }
                Text(
                    text = adherenceStatusLabel(adherence),
                    style = MaterialTheme.typography.labelSmall,
                    color = adherenceColor(adherence),
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (activePlans.isEmpty()) {
                Text(
                    "No active medication plans to score.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activePlans.take(4).forEach { plan ->
                        MedicationAdherenceRow(plan)
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationAdherenceRow(plan: MedicationPlan) {
    val missed = plan.missedCount.coerceAtLeast(0)
    val expected = (missed + 7).coerceAtLeast(7)
    val score = ((expected - missed).toFloat() / expected).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            plan.name,
            modifier = Modifier.widthIn(min = 96.dp).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1.2f)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(adherenceColor(score))
            )
        }
        Text(
            "${(score * 100).toInt()}% · ${adherenceStatusLabel(score)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun adherenceColor(score: Float): Color {
    return when {
        score >= 0.9f -> MaterialTheme.colorScheme.primary
        score >= 0.7f -> ChronosColors.AdherenceMid
        else -> MaterialTheme.colorScheme.error
    }
}

private fun adherenceStatusLabel(score: Float): String = when {
    score >= 0.9f -> "Good"
    score >= 0.7f -> "Fair"
    else -> "Low"
}
