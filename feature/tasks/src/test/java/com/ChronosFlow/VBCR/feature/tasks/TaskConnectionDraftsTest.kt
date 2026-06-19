package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskConnectionDraftsTest {
    @Test
    fun `normalizeTaskActionDraft rejects phone values with non dial characters`() {
        val draft = TaskActionDraft(
            id = "a1",
            type = TaskActionType.PHONE,
            label = "Call Alex",
            value = "555-ALEX-0100",
            isPrimary = false
        )

        assertNull(normalizeTaskActionDraft(draft))
    }

    @Test
    fun `normalizeTaskActionDraft strips whitespace from phone values`() {
        val draft = TaskActionDraft(
            id = "a1",
            type = TaskActionType.PHONE,
            label = "Call Alex",
            value = " +1 (555) 010-0100 ",
            isPrimary = false
        )

        assertEquals("+1(555)010-0100", normalizeTaskActionDraft(draft)?.value)
    }

    @Test
    fun `normalizeTaskActionDrafts promotes first primary action to index one`() {
        val drafts = listOf(
            TaskActionDraft(id = "a1", type = TaskActionType.WEBSITE, label = "Website", value = "example.com", isPrimary = false),
            TaskActionDraft(id = "a2", type = TaskActionType.EMAIL, label = "Email", value = "alex@example.com", isPrimary = true),
            TaskActionDraft(id = "a3", type = TaskActionType.PHONE, label = "Office", value = "+14045550100", isPrimary = true)
        )

        val normalized = normalizeTaskActionDrafts(drafts)

        assertEquals("a2", normalized[1].id)
        assertTrue(normalized[1].isPrimary)
        assertEquals(false, normalized[0].isPrimary)
        assertEquals(false, normalized[2].isPrimary)
    }

    @Test
    fun `hasInvalidTaskActionDraft ignores blank action draft`() {
        val drafts = listOf(
            TaskActionDraft(id = "a1", type = TaskActionType.WEBSITE, label = " ", value = " ", isPrimary = false)
        )

        assertEquals(false, hasInvalidTaskActionDraft(drafts))
    }

    @Test
    fun `hasInvalidTaskActionDraft flags partially complete action draft`() {
        val drafts = listOf(
            TaskActionDraft(id = "a1", type = TaskActionType.EMAIL, label = "Work", value = "", isPrimary = false)
        )

        assertEquals(true, hasInvalidTaskActionDraft(drafts))
    }
}
