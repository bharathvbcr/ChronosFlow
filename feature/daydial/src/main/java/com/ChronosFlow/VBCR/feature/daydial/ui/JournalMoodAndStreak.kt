package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** A single "how did today feel?" option: a 1–5 [rating] with its [emoji] face and short [label]. */
internal data class JournalMood(val rating: Int, val emoji: String, val label: String)

/** The five day-feeling options, rough → great. The [rating] doubles as the persisted value. */
internal val JournalMoods: List<JournalMood> = listOf(
    JournalMood(1, "😞", "Rough"),
    JournalMood(2, "😕", "Low"),
    JournalMood(3, "😐", "Okay"),
    JournalMood(4, "🙂", "Good"),
    JournalMood(5, "😄", "Great")
)

/** The [JournalMood] for a stored [rating], or null when the day was left unrated / out of range. */
internal fun journalMoodFor(rating: Int?): JournalMood? =
    rating?.let { value -> JournalMoods.firstOrNull { it.rating == value } }

/** Id prefix the Health Connect importer stamps on workout points (see JournalViewModel.importWorkouts). */
internal const val JournalWorkoutIdPrefix = "hc-workout-"

/** Whether [entry] is an auto-imported Health Connect workout point rather than a written reflection. */
internal fun isJournalWorkoutEntry(entry: JournalEntry): Boolean =
    entry.id.startsWith(JournalWorkoutIdPrefix)

/**
 * The representative mood per day for the calendar overview: the primary entry's rating when present,
 * otherwise the first rated entry that day. Days with no rating are absent from the map.
 */
internal fun journalMoodByDate(entries: List<JournalEntry>): Map<LocalDate, JournalMood> {
    val result = LinkedHashMap<LocalDate, JournalMood>()
    entries.groupBy { it.entryDate }.forEach { (date, dayEntries) ->
        val rating = dayEntries.firstOrNull { it.isPrimary && it.dayRating != null }?.dayRating
            ?: dayEntries.firstNotNullOfOrNull { it.dayRating }
        journalMoodFor(rating)?.let { result[date] = it }
    }
    return result
}

/** Days that carry at least one imported workout point. */
internal fun journalWorkoutDates(entries: List<JournalEntry>): Set<LocalDate> =
    entries.filter { isJournalWorkoutEntry(it) }.mapTo(HashSet()) { it.entryDate }

/** Days the user actually journaled (any non-workout entry), for the calendar's "journaled" marker. */
internal fun journalWrittenDates(entries: List<JournalEntry>): Set<LocalDate> =
    entries.filterNot { isJournalWorkoutEntry(it) }.mapTo(HashSet()) { it.entryDate }

/**
 * The day cells of [month] laid out in calendar weeks, each a 7-slot row starting on [firstDayOfWeek].
 * Leading/trailing slots outside the month are null so the grid stays aligned.
 */
internal fun journalCalendarWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
    val firstOfMonth = month.atDay(1)
    val leadingBlanks = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    val cells = ArrayList<LocalDate?>()
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

/**
 * A one-line "how the month went" recap for the calendar: average mood, days journaled, and workouts
 * within [month]. Empty when nothing happened that month.
 */
internal fun journalMonthSummary(entries: List<JournalEntry>, month: YearMonth): String {
    val inMonth = entries.filter { YearMonth.from(it.entryDate) == month }
    if (inMonth.isEmpty()) return ""
    val journaledDays = journalWrittenDates(inMonth).size
    val workouts = inMonth.count { isJournalWorkoutEntry(it) }
    val avg = journalAverageMood(inMonth.filterNot { isJournalWorkoutEntry(it) })
    val parts = buildList {
        avg?.let { value ->
            journalMoodFor(Math.round(value.coerceIn(1.0, 5.0)).toInt())?.let { mood ->
                add("avg ${mood.emoji} ${mood.label}")
            }
        }
        if (journaledDays > 0) add(if (journaledDays == 1) "1 day journaled" else "$journaledDays days journaled")
        if (workouts > 0) add(if (workouts == 1) "1 workout" else "$workouts workouts")
    }
    return parts.joinToString(" · ")
}

/**
 * Current consecutive-day journaling streak. Counts back day-by-day from [today] — or from yesterday,
 * so a not-yet-written today never resets a live streak — over days that each carry at least one
 * entry. Returns 0 when the most recent entry is older than yesterday (the streak has lapsed).
 */
internal fun journalStreak(entries: List<JournalEntry>, today: LocalDate): Int {
    val days = entries.mapTo(HashSet()) { it.entryDate }
    val anchor = when {
        today in days -> today
        today.minusDays(1) in days -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    var cursor = anchor
    while (cursor in days) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** A motivating streak caption, or null when there's no live streak to celebrate. */
internal fun journalStreakLabel(streak: Int): String? = when {
    streak <= 0 -> null
    streak == 1 -> "Day 1 — nice start"
    else -> "🔥 $streak-day streak"
}

/**
 * Rotating reflection prompts. Surfaced one-per-day so the journal feels fresh rather than asking the
 * same three questions forever. These are gentle, open-ended invitations — distinct from the
 * structured [JournalPrompts] chips that scaffold a guided entry.
 */
internal val JournalDailyPrompts: List<String> = listOf(
    "What's one small win from today?",
    "What are you grateful for right now?",
    "What's been on your mind lately?",
    "When did you feel most like yourself today?",
    "What would make tomorrow feel lighter?",
    "What's something you learned today?",
    "Who or what gave you energy today?",
    "What's one thing you can let go of tonight?",
    "What are you looking forward to?",
    "How did you take care of yourself today?",
    "What challenged you, and how did you handle it?",
    "What's a moment from today worth remembering?"
)

/**
 * The reflection prompt for a given [date], chosen deterministically so the same day always shows the
 * same prompt while consecutive days rotate through the list. Uses the epoch day so it's timezone- and
 * locale-stable.
 */
internal fun journalPromptOfTheDay(date: LocalDate): String {
    val size = JournalDailyPrompts.size
    val index = (date.toEpochDay() % size).toInt()
    val safeIndex = if (index < 0) index + size else index
    return JournalDailyPrompts[safeIndex]
}

/** Mean of the rated days in [entries], 1.0–5.0, or null when nothing is rated. */
internal fun journalAverageMood(entries: List<JournalEntry>): Double? {
    val ratings = entries.mapNotNull { it.dayRating?.takeIf { r -> r in 1..5 } }
    return if (ratings.isEmpty()) null else ratings.sum().toDouble() / ratings.size
}

/** How many distinct days in [entries] fall within the trailing [windowDays] ending [today]. */
internal fun journalDaysInWindow(entries: List<JournalEntry>, today: LocalDate, windowDays: Int): Int {
    val start = today.minusDays((windowDays - 1).toLong())
    return entries.map { it.entryDate }.filter { it in start..today }.toSet().size
}

/**
 * Prompt fed to the GenAI assist gateway to reflect on recent entries. Deliberately short and bounded
 * (snippets trimmed) so it stays within on-device Gemini Nano limits. Newest entry first.
 */
internal fun buildJournalInsightPrompt(entries: List<JournalEntry>, today: LocalDate): String {
    val lines = entries.take(14).joinToString("\n") { entry ->
        val mood = entry.dayRating?.let { "mood $it/5" } ?: "mood —"
        val snippet = entry.body.trim().replace("\n", " ").take(140)
        "- ${entry.entryDate} ($mood): ${snippet.ifBlank { "(mood only)" }}"
    }
    return buildString {
        append("You are a warm, concise journaling companion. ")
        append("Based on these recent daily reflections, write 2-3 short sentences noticing any patterns ")
        append("in mood or recurring themes, then offer one gentle, encouraging suggestion. ")
        append("Be supportive and specific; avoid clinical or therapy language. ")
        append("Today is $today. Reflections (newest first):\n")
        append(lines)
    }
}

/**
 * Deterministic, offline reflection used when the AI gateway is unavailable (disabled, no model, or
 * an empty result). Still genuinely useful: summarizes cadence, mood, and streak with a nudge.
 */
internal fun localJournalInsight(entries: List<JournalEntry>, today: LocalDate): String {
    if (entries.isEmpty()) {
        return "No reflections yet. Capturing even a one-tap mood each evening is how the streak — and the insights — begin."
    }
    val journaledDays = journalDaysInWindow(entries.filterNot { isJournalWorkoutEntry(it) }, today, windowDays = 14)
    val avg = journalAverageMood(entries.filter { it.entryDate in today.minusDays(13)..today })
    val streak = journalStreak(entries, today)

    return buildString {
        append("Over the last 14 days you reflected on $journaledDays ")
        append(if (journaledDays == 1) "day" else "days")
        avg?.let { value ->
            journalMoodFor(value.roundToNearestRating())?.let { mood ->
                append(", with your mood averaging ${mood.label} ${mood.emoji} (%.1f/5)".format(value))
            }
        }
        append(". ")
        append(
            when {
                streak >= 3 -> "You're on a $streak-day streak — keep the momentum with a quick note tonight."
                streak >= 1 -> "You've started a streak; one more entry tomorrow keeps it alive."
                else -> "A short reflection tonight is an easy way to start a new streak."
            }
        )
    }
}

/** Nearest 1–5 rating for an average mood value, for picking a representative [JournalMood]. */
private fun Double.roundToNearestRating(): Int = coerceIn(1.0, 5.0).let { Math.round(it).toInt() }

/**
 * Prompt for the on-device "expand / rewrite" text action: turn a terse note into a fuller first-person
 * reflection without inventing events. Kept short so it fits on-device Gemini Nano.
 */
internal fun journalExpandPrompt(text: String): String =
    "Expand this short personal journal note into a fuller, natural first-person reflection of one or " +
        "two sentences. Keep the original meaning, tone, and any facts; do not invent events. Return " +
        "only the rewritten note.\nNote: ${text.trim()}"

/**
 * A row of five tappable mood faces, rough → great. Each is a circular emoji tile with its label
 * beneath; the selected face fills with the primary container and gains a ring so the current rating
 * reads at a glance. Tapping the selected face again deselects it (clears the rating). Larger and more
 * inviting than the old chip row, which truncated its labels.
 */
@Composable
internal fun JournalMoodPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        JournalMoods.forEach { mood ->
            val isSelected = selected == mood.rating
            val faceColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                label = "moodFace"
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(faceColor)
                        .border(
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                }
                            ),
                            shape = CircleShape
                        )
                        .chronosHapticClick(
                            onClick = { onSelect(if (isSelected) null else mood.rating) },
                            onClickLabel = if (isSelected) "Clear ${mood.label} mood" else "Set mood to ${mood.label}",
                            role = Role.RadioButton
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = mood.emoji, style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = mood.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/** Prefix marking a line as a sub-note ("bullet") within an entry's body. */
internal const val JournalSubNotePrefix = "• "

/** An entry body split into its free-form [mainNote] and any subtask-style [subNotes]. */
internal data class JournalBodyParts(val mainNote: String, val subNotes: List<String>)

/**
 * Combine a [mainNote] and subtask-style [subNotes] into a single entry body: the main note, then one
 * bullet line per sub-note. Round-trips with [journalParseBody]. Blank sub-notes are dropped.
 */
internal fun journalSerializeBody(mainNote: String, subNotes: List<String>): String {
    val main = mainNote.trim()
    val notes = subNotes.map { it.trim() }.filter { it.isNotEmpty() }
    return buildString {
        append(main)
        if (notes.isNotEmpty()) {
            if (main.isNotEmpty()) append("\n")
            append(notes.joinToString("\n") { "$JournalSubNotePrefix$it" })
        }
    }.trim()
}

/**
 * Split an entry [body] back into its main note and sub-notes: lines starting with the bullet prefix
 * become sub-notes, everything else is the main note. A plain body with no bullets parses to itself
 * with no sub-notes.
 */
internal fun journalParseBody(body: String): JournalBodyParts {
    val lines = body.lines()
    val subNotes = lines
        .filter { it.trimStart().startsWith(JournalSubNotePrefix) }
        .map { it.trimStart().removePrefix(JournalSubNotePrefix).trim() }
        .filter { it.isNotEmpty() }
    val mainNote = lines
        .filterNot { it.trimStart().startsWith(JournalSubNotePrefix) }
        .joinToString("\n")
        .trim()
    return JournalBodyParts(mainNote, subNotes)
}
