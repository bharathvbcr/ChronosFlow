package com.chronosflow.feature.daydial.ui

import com.chronosflow.core.ui.components.ChronosButton

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.ui.components.ChronosFilterChip
import com.chronosflow.core.ui.components.ChronosSpeechInputButton
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class JournalPrompt(val key: String, val label: String, val question: String)

/**
 * [JournalEntry.promptType] sentinel for an entry composed from the full guided scaffold (all
 * prompts), as opposed to a single-prompt key. Kept distinct from the [JournalPrompts] keys so it
 * never adds a chip to the prompt row; the timeline maps it to its own "Guided" label.
 */
internal const val GuidedPromptType = "guided"

internal val JournalPrompts = listOf(
    JournalPrompt("went_well", "Went well", "What went well today?"),
    JournalPrompt("drained", "Drained me", "What drained you today?"),
    JournalPrompt("tomorrow", "Tomorrow", "One thing to make tomorrow better?")
)

private val journalDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

/** Whitespace-delimited word count for the journal body, used for the live writing meter. */
internal fun journalWordCount(body: String): Int =
    body.trim().split(Regex("\\s+")).count { it.isNotBlank() }

/** Relative "last saved" label for an edited entry, coarsened to just-now / minutes / hours / days. */
internal fun journalLastSavedLabel(updatedAt: Instant, now: Instant): String {
    val minutes = Duration.between(updatedAt, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "Last saved just now"
        minutes < 60 -> "Last saved ${minutes}m ago"
        minutes < 24 * 60 -> "Last saved ${minutes / 60}h ago"
        else -> "Last saved ${minutes / (24 * 60)}d ago"
    }
}

/**
 * Whether the draft differs from what's already saved, so an edit's Update button can stay
 * disabled until there's something new to write. A brand-new entry "has changes" as soon as
 * the body is non-blank.
 */
internal fun journalHasChanges(body: String, promptType: String?, existing: JournalEntry?): Boolean {
    if (existing == null) return body.isNotBlank()
    return body.trim() != existing.body.trim() || promptType != existing.promptType
}

/**
 * Body text after toggling a prompt's [question] in. When the body is empty the question
 * seeds it; otherwise the question is appended on its own line, but only if it isn't already
 * present so repeated taps never duplicate it.
 */
internal fun journalBodyWithPrompt(body: String, question: String): String = when {
    body.isBlank() -> "$question\n"
    body.contains(question) -> body
    else -> "${body.trimEnd()}\n\n$question\n"
}

/**
 * Lays every prompt question into the body as a guided-reflection scaffold, reusing
 * [journalBodyWithPrompt] so already-present questions are never duplicated.
 */
internal fun journalBodyWithGuidedTemplate(body: String, prompts: List<JournalPrompt>): String =
    prompts.fold(body) { acc, prompt -> journalBodyWithPrompt(acc, prompt.question) }

/**
 * Body with a single prompt's [question] scaffolding removed when it has no reflection beneath it,
 * so deselecting a prompt chip undoes the question it seeded. An answered question/reflection pair
 * is left intact (that's real content). The full [prompts] set is needed to know where each
 * section ends; this is the single-prompt counterpart of [journalCleanForSave].
 */
internal fun journalBodyWithoutPrompt(body: String, question: String, prompts: List<JournalPrompt>): String {
    val questions = prompts.map { it.question }.toSet()
    val lines = body.lines()
    val keep = BooleanArray(lines.size) { true }
    var i = 0
    while (i < lines.size) {
        if (lines[i].trim() in questions) {
            var j = i + 1
            var hasReflection = false
            while (j < lines.size && lines[j].trim() !in questions) {
                if (lines[j].isNotBlank()) hasReflection = true
                j++
            }
            if (lines[i].trim() == question && !hasReflection) {
                for (k in i until j) keep[k] = false
            }
            i = j
        } else {
            i++
        }
    }
    return lines.filterIndexed { index, _ -> keep[index] }.joinToString("\n").trim()
}

/**
 * True when the body is a non-empty scaffold of only prompt questions and no actual
 * reflection — used to keep Save disabled until the user adds their own words.
 */
internal fun journalIsOnlyPrompts(body: String, prompts: List<JournalPrompt>): Boolean {
    val questions = prompts.map { it.question }
    val contentLines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return contentLines.isNotEmpty() && contentLines.all { it in questions }
}

/** Keys of the prompts whose question appears in the body with at least one line of reflection beneath it. */
internal fun journalAnsweredPromptKeys(body: String, prompts: List<JournalPrompt>): Set<String> {
    val byQuestion = prompts.associateBy { it.question }
    val lines = body.lines()
    val answered = mutableSetOf<String>()
    var i = 0
    while (i < lines.size) {
        val prompt = byQuestion[lines[i].trim()]
        if (prompt != null) {
            var j = i + 1
            var hasReflection = false
            while (j < lines.size && lines[j].trim() !in byQuestion) {
                if (lines[j].isNotBlank()) hasReflection = true
                j++
            }
            if (hasReflection) answered.add(prompt.key)
            i = j
        } else {
            i++
        }
    }
    return answered
}

/** How many prompt questions present in the body have at least one line of reflection beneath them. */
internal fun journalAnsweredPromptCount(body: String, prompts: List<JournalPrompt>): Int =
    journalAnsweredPromptKeys(body, prompts).size

/** How many of the [prompts] questions appear anywhere in the body. */
internal fun journalPromptsPresentCount(body: String, prompts: List<JournalPrompt>): Int =
    prompts.count { body.contains(it.question) }

/**
 * Body to persist: drops any prompt question that was left unanswered (no reflection beneath it)
 * along with its trailing blank lines, so a guided entry saves only the sections the user filled
 * in. Answered question/reflection pairs and free-form text are untouched (whitespace-trimmed).
 */
internal fun journalCleanForSave(body: String, prompts: List<JournalPrompt>): String {
    val questions = prompts.map { it.question }.toSet()
    val lines = body.lines()
    val keep = BooleanArray(lines.size) { true }
    var i = 0
    while (i < lines.size) {
        if (lines[i].trim() in questions) {
            var j = i + 1
            var hasReflection = false
            while (j < lines.size && lines[j].trim() !in questions) {
                if (lines[j].isNotBlank()) hasReflection = true
                j++
            }
            if (!hasReflection) {
                for (k in i until j) keep[k] = false
            }
            i = j
        } else {
            i++
        }
    }
    return lines.filterIndexed { index, _ -> keep[index] }.joinToString("\n").trim()
}

@Composable
internal fun JournalEntrySheetContent(
    date: LocalDate,
    existing: JournalEntry?,
    moodSummary: String?,
    onSave: (body: String, promptType: String?) -> Unit
) {
    var body by rememberSaveable(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    var promptType by rememberSaveable(existing?.id) { mutableStateOf(existing?.promptType) }

    val wordCount = journalWordCount(body)
    val answeredKeys = journalAnsweredPromptKeys(body, JournalPrompts)
    val promptsPresent = journalPromptsPresentCount(body, JournalPrompts)
    val promptsReflected = answeredKeys.size

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Text(
            text = "Evening journal",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = date.format(journalDateFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        existing?.let { entry ->
            val now = remember(entry.id, entry.updatedAt) { Instant.now() }
            Text(
                text = journalLastSavedLabel(entry.updatedAt, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        moodSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            JournalPrompts.forEach { prompt ->
                ChronosFilterChip(
                    selected = promptType == prompt.key,
                    onClick = {
                        if (promptType == prompt.key) {
                            promptType = null
                            body = journalBodyWithoutPrompt(body, prompt.question, JournalPrompts)
                        } else {
                            promptType = prompt.key
                            body = journalBodyWithPrompt(body, prompt.question)
                        }
                    },
                    label = { Text(prompt.label) },
                    leadingIcon = if (prompt.key in answeredKeys) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "answered",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
            val guided = promptType == GuidedPromptType
            ChronosFilterChip(
                selected = guided,
                onClick = {
                    if (guided) {
                        promptType = null
                        body = journalCleanForSave(body, JournalPrompts)
                    } else {
                        promptType = GuidedPromptType
                        body = journalBodyWithGuidedTemplate(body, JournalPrompts)
                    }
                },
                label = { Text("Guided") }
            )
        }
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("How did today go?") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChronosSpeechInputButton(
                prompt = "Dictate your reflection",
                onTranscript = { transcript ->
                    body = listOf(body.trim(), transcript.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
            )
            Text(
                text = if (promptsPresent > 0) {
                    "$promptsReflected of $promptsPresent reflected · ${if (wordCount == 1) "1 word" else "$wordCount words"}"
                } else if (wordCount == 1) {
                    "1 word"
                } else {
                    "$wordCount words"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val onlyPrompts = journalIsOnlyPrompts(body, JournalPrompts)
        if (onlyPrompts) {
            Text(
                text = "Add your own words below the prompts to save.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ChronosButton(
            onClick = { onSave(journalCleanForSave(body, JournalPrompts), promptType) },
            enabled = body.isNotBlank() && journalHasChanges(body, promptType, existing) && !onlyPrompts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existing == null) "Save entry" else "Update entry", fontWeight = FontWeight.SemiBold)
        }
    }
}
