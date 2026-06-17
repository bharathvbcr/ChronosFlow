package com.chronosflow.feature.daydial.ui

import com.chronosflow.core.domain.model.JournalEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalEntrySheetLogicTest {

    private fun entry(body: String, promptType: String? = null) = JournalEntry(
        id = "e1",
        entryDate = LocalDate.of(2026, 6, 12),
        createdAt = Instant.parse("2026-06-12T20:00:00Z"),
        updatedAt = Instant.parse("2026-06-12T20:00:00Z"),
        body = body,
        promptType = promptType
    )

    @Test
    fun `new entry has changes only once the body is non-blank`() {
        assertFalse(journalHasChanges(body = "   ", promptType = null, existing = null))
        assertTrue(journalHasChanges(body = "First note", promptType = null, existing = null))
    }

    @Test
    fun `edited entry is unchanged until body or prompt differs`() {
        val existing = entry(body = "A calm day.", promptType = "went_well")

        // Identical (whitespace aside) -> no changes.
        assertFalse(journalHasChanges("  A calm day.  ", "went_well", existing))
        // Different body.
        assertTrue(journalHasChanges("A calm day. Plus a walk.", "went_well", existing))
        // Different prompt selection.
        assertTrue(journalHasChanges("A calm day.", "drained", existing))
    }

    @Test
    fun `last saved label coarsens by elapsed time`() {
        val base = Instant.parse("2026-06-12T20:00:00Z")

        assertEquals("Last saved just now", journalLastSavedLabel(base, base.plusSeconds(30)))
        assertEquals("Last saved 5m ago", journalLastSavedLabel(base, base.plus(Duration.ofMinutes(5))))
        assertEquals("Last saved 3h ago", journalLastSavedLabel(base, base.plus(Duration.ofHours(3))))
        assertEquals("Last saved 2d ago", journalLastSavedLabel(base, base.plus(Duration.ofDays(2))))
    }

    @Test
    fun `last saved label never goes negative on clock skew`() {
        val base = Instant.parse("2026-06-12T20:00:00Z")
        // "now" earlier than the stamp (clock skew) clamps to just-now rather than a negative span.
        assertEquals("Last saved just now", journalLastSavedLabel(base, base.minusSeconds(120)))
    }

    @Test
    fun `word count ignores surrounding and repeated whitespace`() {
        assertEquals(0, journalWordCount(""))
        assertEquals(0, journalWordCount("   \n  "))
        assertEquals(1, journalWordCount("  hello  "))
        assertEquals(3, journalWordCount("a good\nday"))
    }

    @Test
    fun `prompt seeds an empty body`() {
        assertEquals(
            "What went well today?\n",
            journalBodyWithPrompt("", "What went well today?")
        )
    }

    @Test
    fun `prompt appends to existing text on its own line`() {
        val result = journalBodyWithPrompt("Long run this morning.", "What drained you today?")

        assertTrue(result.startsWith("Long run this morning."))
        assertTrue(result.contains("What drained you today?"))
    }

    @Test
    fun `prompt is not duplicated when already present`() {
        val body = "What went well today?\nFinished the report."

        assertEquals(body, journalBodyWithPrompt(body, "What went well today?"))
    }

    @Test
    fun `guided template lays in every prompt and is idempotent`() {
        val once = journalBodyWithGuidedTemplate("", JournalPrompts)

        JournalPrompts.forEach { prompt ->
            assertTrue(once.contains(prompt.question))
        }
        assertTrue(once.startsWith(JournalPrompts.first().question))

        // Applying again must not duplicate any already-present question.
        val twice = journalBodyWithGuidedTemplate(once, JournalPrompts)
        assertEquals(once, twice)
    }

    @Test
    fun `removing a prompt drops its unanswered scaffolding but keeps an answered section`() {
        val scaffold = journalBodyWithGuidedTemplate("", JournalPrompts)

        // Deselecting an unanswered prompt removes just its question; the others remain.
        val withoutWentWell = journalBodyWithoutPrompt(scaffold, "What went well today?", JournalPrompts)
        assertFalse(withoutWentWell.contains("What went well today?"))
        assertTrue(withoutWentWell.contains("What drained you today?"))
        assertTrue(withoutWentWell.contains("One thing to make tomorrow better?"))

        // An answered prompt is left intact, reflection and all.
        val answered = "What went well today?\nShipped the release.\n\nWhat drained you today?\n"
        val kept = journalBodyWithoutPrompt(answered, "What went well today?", JournalPrompts)
        assertTrue(kept.contains("What went well today?"))
        assertTrue(kept.contains("Shipped the release."))
        // The other unanswered prompt is untouched (only the targeted question is removed).
        assertTrue(kept.contains("What drained you today?"))
    }

    @Test
    fun `removing the only seeded prompt clears the body`() {
        assertEquals("", journalBodyWithoutPrompt("What went well today?\n", "What went well today?", JournalPrompts))
    }

    @Test
    fun `reflected count tracks which prompts have an answer beneath them`() {
        val scaffold = journalBodyWithGuidedTemplate("", JournalPrompts)

        // Bare scaffold: all three prompts present, none answered.
        assertEquals(3, journalPromptsPresentCount(scaffold, JournalPrompts))
        assertEquals(0, journalAnsweredPromptCount(scaffold, JournalPrompts))

        // Answer just the first prompt.
        val answeredFirst =
            "What went well today?\nShipped the release.\n\n" +
                "What drained you today?\n\nOne thing to make tomorrow better?\n"
        assertEquals(3, journalPromptsPresentCount(answeredFirst, JournalPrompts))
        assertEquals(1, journalAnsweredPromptCount(answeredFirst, JournalPrompts))

        // Free-form entry with no prompts present.
        assertEquals(0, journalPromptsPresentCount("Just a normal day.", JournalPrompts))
        assertEquals(0, journalAnsweredPromptCount("Just a normal day.", JournalPrompts))
    }

    @Test
    fun `answered prompt keys identify exactly which prompts have a reflection`() {
        val scaffold = journalBodyWithGuidedTemplate("", JournalPrompts)
        assertTrue(journalAnsweredPromptKeys(scaffold, JournalPrompts).isEmpty())

        val answeredFirst =
            "What went well today?\nShipped the release.\n\n" +
                "What drained you today?\n\nOne thing to make tomorrow better?\n"
        assertEquals(setOf("went_well"), journalAnsweredPromptKeys(answeredFirst, JournalPrompts))
    }

    @Test
    fun `clean-for-save drops unanswered prompt scaffolding but keeps answered sections`() {
        val body =
            "What went well today?\nShipped the release.\n\n" +
                "What drained you today?\n\nOne thing to make tomorrow better?\n"

        // Only the answered prompt and its reflection survive.
        assertEquals(
            "What went well today?\nShipped the release.",
            journalCleanForSave(body, JournalPrompts)
        )
    }

    @Test
    fun `clean-for-save leaves free-form text untouched apart from trimming`() {
        assertEquals(
            "Just a normal day.",
            journalCleanForSave("  Just a normal day.\n", JournalPrompts)
        )
    }

    @Test
    fun `a prompts-only scaffold is detected, and answered or empty bodies are not`() {
        // The bare guided template is just questions -> only-prompts.
        val scaffold = journalBodyWithGuidedTemplate("", JournalPrompts)
        assertTrue(journalIsOnlyPrompts(scaffold, JournalPrompts))

        // Once a real reflection is added, it's no longer only-prompts.
        assertFalse(journalIsOnlyPrompts(scaffold + "We shipped the release.", JournalPrompts))

        // An empty body is not a prompts-only scaffold (nothing to save anyway).
        assertFalse(journalIsOnlyPrompts("   \n  ", JournalPrompts))
    }
}
