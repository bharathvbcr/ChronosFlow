package com.chronosflow.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitEventType
import java.time.LocalDate

/**
 * Compact "this week" completion strip — one dot per day for the trailing [days] days, filled when
 * the habit was completed that day. Gives each habit row an at-a-glance momentum cue, the kind of
 * weekly grid that keeps streak-style apps engaging.
 */
@Composable
fun HabitWeekStrip(
    habit: Habit,
    modifier: Modifier = Modifier,
    days: Int = 7,
    today: LocalDate = LocalDate.now()
) {
    val completedDays = remember(habit.recentEvents, habit.lastCompletedDate, today, days) {
        habitCompletedDaysInWindow(habit, today, days)
    }
    val start = today.minusDays((days - 1).toLong())
    val filled = MaterialTheme.colorScheme.primary
    val todayEmpty = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val empty = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until days).forEach { offset ->
            val date = start.plusDays(offset.toLong())
            val isCompleted = date in completedDays
            val isToday = date == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                isCompleted -> filled
                                isToday -> todayEmpty
                                else -> empty
                            }
                        )
                )
                Text(
                    text = dayInitial(date),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * The set of dates in the trailing [days]-day window (inclusive of [today]) on which [habit] was
 * completed, derived from its recorded events plus its last-completed date. Pure for testability.
 */
internal fun habitCompletedDaysInWindow(
    habit: Habit,
    today: LocalDate,
    days: Int = 7
): Set<LocalDate> {
    if (days <= 0) return emptySet()
    val start = today.minusDays((days - 1).toLong())
    val window = start..today
    val fromEvents = habit.recentEvents
        .filter { it.type == HabitEventType.COMPLETED && it.eventDate in window }
        .map { it.eventDate }
    val fromLastCompleted = habit.lastCompletedDate?.takeIf { it in window }
    return (fromEvents + listOfNotNull(fromLastCompleted)).toSet()
}

private fun dayInitial(date: LocalDate): String =
    date.dayOfWeek.name.take(1)
