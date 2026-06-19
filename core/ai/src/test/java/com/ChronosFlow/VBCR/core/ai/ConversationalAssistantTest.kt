package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationalAssistantTest {
    private val commands = listOf(
        CommandAssistCandidate("focus.start", "Start focus session", setOf("focus", "timer")),
        CommandAssistCandidate("task.add", "Add task", setOf("task", "todo", "add"))
    )

    @Test
    fun `respond maps an action line to a confirmable proposal`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "Sure, I can start a focus session.\nACTION: focus.start",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val response = assistant.respond(emptyList(), "help me focus", commands)

        assertEquals("focus.start", response.proposal?.commandId)
        assertTrue(response.proposal?.requiresConfirmation == true)
        assertFalse(response.reply.contains("ACTION:"))
        assertTrue(response.reply.contains("focus", ignoreCase = true))
    }

    @Test
    fun `respond ignores action ids that are not in the catalog`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "Here you go.\nACTION: delete.everything",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val response = assistant.respond(emptyList(), "do something", commands)

        assertNull(response.proposal)
    }

    @Test
    fun `respond falls back to local routing when AI unavailable`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val response = assistant.respond(emptyList(), "add task buy milk", commands)

        assertEquals("task.add", response.proposal?.commandId)
        assertEquals(AssistGenAiSource.LOCAL, response.source)
    }

    @Test
    fun `respondStream emits partial text then a final response`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf(
            "Opening",
            "Opening the add task screen.\nACTION: task.add"
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val events = assistant.respondStream(emptyList(), "add a task", commands).toList()

        val partials = events.filterIsInstance<AssistantStreamEvent.Partial>()
        val final = events.filterIsInstance<AssistantStreamEvent.Final>().single()
        assertEquals("Opening", partials.first().text)
        assertFalse(partials.last().text.contains("ACTION:"))
        assertEquals("task.add", final.response.proposal?.commandId)
    }

    @Test
    fun `respond extracts a capture payload for quick-capture proposals`() = runTest {
        val captureCommands = commands + CommandAssistCandidate(
            "capture.create.task",
            "Create task from \"call mom\"",
            setOf("add", "create", "capture")
        )
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "I'll draft that task.\nACTION: capture.create.task | call mom tomorrow at 2pm",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val response = assistant.respond(emptyList(), "make it tomorrow at 2pm", captureCommands)

        assertEquals("capture.create.task", response.proposal?.commandId)
        assertEquals("call mom tomorrow at 2pm", response.proposal?.capturePayload)
        assertFalse(response.reply.contains("ACTION:"))
    }

    @Test
    fun `respond returns multiple proposals in order and ignores unknown ids`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "Both would help.\nACTION: focus.start\nACTION: delete.everything\nACTION: task.add",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val response = assistant.respond(emptyList(), "set me up", commands)

        assertEquals(listOf("focus.start", "task.add"), response.proposals.map { it.commandId })
        assertEquals("focus.start", response.proposal?.commandId)
        assertFalse(response.reply.contains("ACTION:"))
    }

    @Test
    fun `respond ignores payloads on non-capture commands`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "Starting focus.\nACTION: focus.start | 45 minutes",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val response = assistant.respond(emptyList(), "focus please", commands)

        assertEquals("focus.start", response.proposal?.commandId)
        assertNull(response.proposal?.capturePayload)
    }

    @Test
    fun `respondStream retries the non-streaming path when the stream is empty`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf()
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "Opening the add task screen.\nACTION: task.add",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val assistant = ConversationalAssistant(coordinator, commandPlanner())

        val events = assistant.respondStream(emptyList(), "add a task", commands).toList()

        val final = events.filterIsInstance<AssistantStreamEvent.Final>().single()
        assertEquals("task.add", final.response.proposal?.commandId)
        assertEquals(AssistGenAiSource.CLOUD_GEMINI, final.response.source)
    }

    private fun commandPlanner() = CommandAssistPlanner(mockk(), mockk())
}
