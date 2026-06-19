package com.ChronosFlow.VBCR.core.notifications

import android.content.Intent
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskActionIntentsTest {

    @Test
    fun `website action builds browse intent`() {
        val action = TaskAction(
            id = "a1",
            type = TaskActionType.WEBSITE,
            label = "Docs",
            value = "https://example.com",
            isPrimary = true
        )

        assertEquals(Intent.ACTION_VIEW, taskActionIntentAction(action.type))
        assertEquals("https://example.com", taskActionIntentData(action))
    }

    @Test
    fun `email action builds sendto intent`() {
        val action = TaskAction(
            id = "a1",
            type = TaskActionType.EMAIL,
            label = "Email Alex",
            value = "alex@example.com",
            isPrimary = false
        )

        assertEquals(Intent.ACTION_SENDTO, taskActionIntentAction(action.type))
        assertEquals("mailto:alex%40example.com", taskActionIntentData(action))
    }

    @Test
    fun `app action builds package launcher intent`() {
        val action = TaskAction(
            id = "a1",
            type = TaskActionType.APP,
            label = "Open Journal",
            value = "com.example.journal",
            isPrimary = true
        )

        val intent = buildTaskActionIntent(action)

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals("com.example.journal", intent.`package`)
    }

    @Test
    fun `app action builds component launcher intent`() {
        val action = TaskAction(
            id = "a1",
            type = TaskActionType.APP,
            label = "Open Journal",
            value = "component:com.example.journal/.MainActivity",
            isPrimary = true
        )

        val intent = buildTaskActionIntent(action)

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals("com.example.journal", intent.component?.packageName)
        assertEquals("com.example.journal.MainActivity", intent.component?.className)
    }

    @Test
    fun `primary task actions sort primary and communication actions first`() {
        val actions = listOf(
            TaskAction("1", TaskActionType.DOCUMENT, "Doc", "https://example.com/doc", false),
            TaskAction("2", TaskActionType.PHONE, "Call", "+15551234567", false),
            TaskAction("3", TaskActionType.WEBSITE, "Site", "https://example.com", true),
            TaskAction("4", TaskActionType.EMAIL, "Mail", "alex@example.com", false)
        )

        val primary = primaryTaskActions(actions, limit = 3)

        assertEquals(listOf("Site", "Call", "Mail"), primary.map { it.label })
    }
}
