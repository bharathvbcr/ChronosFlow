package com.chronosflow.feature.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.domain.model.MedicationDailyAdherence
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosTrendChart
import com.chronosflow.core.ui.components.ChronosTrendChartMode
import com.chronosflow.core.ui.components.ChronosTrendSeries
import com.chronosflow.core.ui.components.formatDisplayMinute
import java.time.LocalDate

/**
 * 14-day taken/missed dose trend for the Medication page. Complements [MedicationAdherenceChart]
 * (a per-plan weekly estimate) with the actual day-by-day logging history, reusing the shared
 * [ChronosTrendChart] for visual parity with the Insights tab.
 */
@Composable
fun MedicationAdherenceTrendCard(
    trend: List<MedicationDailyAdherence>,
    modifier: Modifier = Modifier
) {
    val totalTaken = trend.sumOf { it.takenCount }
    if (trend.isEmpty() || totalTaken == 0) return

    val totalMissed = trend.sumOf { it.missedCount }
    val windowDays = trend.size
    val onTrackDays = trend.count { it.takenCount > 0 && it.missedCount == 0 }

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Dose history",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        medicationTrendHeadline(onTrackDays, windowDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "$onTrackDays/$windowDays days",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ChronosTrendChart(
                series = listOf(
                    ChronosTrendSeries(
                        label = "Taken",
                        color = MaterialTheme.colorScheme.primary,
                        points = trend.map { it.takenCount.toFloat() }
                    )
                ),
                mode = ChronosTrendChartMode.BAR
            )
            Text(
                "$totalTaken taken · $totalMissed missed in the last $windowDays days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Surfaces plans whose supply has dropped to (or below) their refill threshold, so a low stock is a
 * gentle prompt rather than a missed dose later. Renders nothing when nothing needs a refill.
 */
@Composable
fun MedicationRefillCard(
    plans: List<MedicationPlan>,
    modifier: Modifier = Modifier
) {
    val needRefill = remember(plans) {
        plans.filter { it.isActive && it.safetyProfile?.refillSoon == true }
    }
    if (needRefill.isEmpty()) return

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Refill soon",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    medicationRefillMessage(needRefill),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun medicationRefillMessage(plans: List<MedicationPlan>): String {
    val names = plans.map { plan ->
        val remaining = plan.safetyProfile?.supplyRemaining
        if (remaining != null) "${plan.name} ($remaining left)" else plan.name
    }
    return when (names.size) {
        1 -> "${names.first()} is running low."
        2 -> "${names[0]} and ${names[1]} are running low."
        else -> "${names[0]}, ${names[1]} and ${names.size - 2} more are running low."
    }
}

/** The next scheduled dose across active, non-PRN, non-paused plans. */
internal data class NextMedicationDose(
    val planName: String,
    val minuteOfDay: Int,
    val isToday: Boolean
)

/**
 * A compact "what's next" prompt at the top of the Medication page so the upcoming dose is always
 * one glance away. Renders nothing when no plan has an upcoming scheduled dose.
 */
@Composable
fun MedicationNextDoseCard(
    plans: List<MedicationPlan>,
    nowMinuteOfDay: Int,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val next = remember(plans, nowMinuteOfDay, today) {
        nextMedicationDose(plans, nowMinuteOfDay, today)
    }
    if (next == null) return

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Next dose",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    medicationNextDoseLabel(next),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

internal fun medicationNextDoseLabel(next: NextMedicationDose): String {
    val time = formatDisplayMinute(next.minuteOfDay)
    return if (next.isToday) {
        "${next.planName} at $time"
    } else {
        "${next.planName} tomorrow at $time"
    }
}

/**
 * The soonest upcoming dose: the earliest reminder still ahead today across eligible plans, else the
 * earliest reminder overall (rolling to tomorrow). Skips archived, PRN, and currently-paused plans.
 * Pure for testability.
 */
internal fun nextMedicationDose(
    plans: List<MedicationPlan>,
    nowMinuteOfDay: Int,
    today: LocalDate
): NextMedicationDose? {
    val eligible = plans.filter { plan ->
        plan.isActive &&
            plan.schedule?.isPrn != true &&
            plan.schedule?.pausedUntil?.isBefore(today) != false
    }
    if (eligible.isEmpty()) return null

    val upcomingToday = eligible
        .filter { it.reminderMinuteOfDay >= nowMinuteOfDay }
        .minByOrNull { it.reminderMinuteOfDay }
    if (upcomingToday != null) {
        return NextMedicationDose(upcomingToday.name, upcomingToday.reminderMinuteOfDay, isToday = true)
    }
    val earliest = eligible.minByOrNull { it.reminderMinuteOfDay } ?: return null
    return NextMedicationDose(earliest.name, earliest.reminderMinuteOfDay, isToday = false)
}

internal fun medicationTrendHeadline(onTrackDays: Int, windowDays: Int): String {
    if (windowDays == 0) return "Log a dose to start tracking."
    val ratio = onTrackDays.toFloat() / windowDays
    return when {
        ratio >= 0.85f -> "Excellent adherence — keep it up."
        ratio >= 0.5f -> "Solid routine. Stay consistent."
        ratio >= 0.25f -> "Getting on track, dose by dose."
        else -> "Every logged dose builds the habit."
    }
}
