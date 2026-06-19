package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilterChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosIconButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Emoji badge painted on days that had an imported workout, when the Workout pill is on. */
private const val JournalWorkoutEmoji = "🏋️"

private val journalCalendarMonthFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

/**
 * A month calendar overview for the Journal page. Each day cell shows, at a glance, how that day went:
 * its mood emoji (Mood pill) and/or a workout badge (Workout pill) — the two pills are independent
 * layers, so a day can show both at once. Days the user journaled but didn't rate get a small dot.
 * Navigate months with the arrows; the recap line under the grid sums up the whole month. Tapping a
 * day opens that day's reflection via [onSelectDate].
 */
@Composable
internal fun JournalCalendarCard(
    entries: List<JournalEntry>,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // YearMonth isn't Saveable, so the visible month is held as (year * 12 + monthIndex).
    var monthCode by rememberSaveable { mutableStateOf(today.year * 12 + (today.monthValue - 1)) }
    val month = remember(monthCode) { YearMonth.of(monthCode / 12, monthCode % 12 + 1) }
    // Independent overlay layers — both on by default so the calendar is richest out of the box.
    var showMood by rememberSaveable { mutableStateOf(true) }
    var showWorkout by rememberSaveable { mutableStateOf(true) }

    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekdayLabels = remember(firstDayOfWeek) {
        (0L until 7L).map {
            firstDayOfWeek.plus(it).getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
    }
    val moodByDate = remember(entries) { journalMoodByDate(entries) }
    val workoutDates = remember(entries) { journalWorkoutDates(entries) }
    val writtenDates = remember(entries) { journalWrittenDates(entries) }
    val weeks = remember(month, firstDayOfWeek) { journalCalendarWeeks(month, firstDayOfWeek) }
    val summary = remember(entries, month) { journalMonthSummary(entries, month) }

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            // Month navigation.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChronosIconButton(onClick = { monthCode -= 1 }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }
                Text(
                    text = month.format(journalCalendarMonthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChronosIconButton(onClick = { monthCode += 1 }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month"
                    )
                }
            }

            // Overlay pills — each toggles a layer the day cells paint. Independent, not exclusive.
            Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                ChronosFilterChip(
                    selected = showMood,
                    onClick = { showMood = !showMood },
                    label = { Text("🙂 Mood") }
                )
                ChronosFilterChip(
                    selected = showWorkout,
                    onClick = { showWorkout = !showWorkout },
                    label = { Text("$JournalWorkoutEmoji Workout") }
                )
            }

            // Weekday header.
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Day grid.
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    week.forEach { date ->
                        if (date == null) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            JournalCalendarDayCell(
                                date = date,
                                isToday = date == today,
                                isFuture = date.isAfter(today),
                                mood = moodByDate[date],
                                hasWorkout = date in workoutDates,
                                journaled = date in writtenDates,
                                showMood = showMood,
                                showWorkout = showWorkout,
                                onClick = { onSelectDate(date) }
                            )
                        }
                    }
                }
            }

            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One square day cell: the date number (top-start), the mood emoji or a journaled dot (center), and a
 * workout badge (top-end) — the latter two gated by their pills, so the layers compose independently.
 */
@Composable
private fun RowScope.JournalCalendarDayCell(
    date: LocalDate,
    isToday: Boolean,
    isFuture: Boolean,
    mood: JournalMood?,
    hasWorkout: Boolean,
    journaled: Boolean,
    showMood: Boolean,
    showWorkout: Boolean,
    onClick: () -> Unit
) {
    val cellShape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(cellShape)
            .background(
                if (isToday) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            )
            .then(
                if (isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, cellShape)
                } else {
                    Modifier
                }
            )
            .chronosHapticClick(
                onClick = onClick,
                onClickLabel = "Open ${date.dayOfMonth}",
                role = Role.Button
            )
            .padding(2.dp)
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            modifier = Modifier.align(Alignment.TopStart),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        // Center: mood emoji, or a small dot when journaled-but-unrated.
        if (showMood) {
            when {
                mood != null -> Text(
                    text = mood.emoji,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )
                journaled -> Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // Top-end: workout badge.
        if (showWorkout && hasWorkout) {
            Text(
                text = JournalWorkoutEmoji,
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
