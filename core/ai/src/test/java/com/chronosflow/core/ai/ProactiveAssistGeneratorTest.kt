package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProactiveAssistGeneratorTest {
    private val input = ProactiveAssistInput(
        dateIso = "2026-06-10",
        plannedMinutes = 300,
        completedBlocks = 3,
        openTaskCount = 2,
        dueMedicationCount = 0,
        nextBlockTitle = "Deep work"
    )

    @Test
    fun `refresh caches the AI digest when Nano is available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Strong day — 3 blocks done, 2 tasks left to close out.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val store = InMemoryProactiveAssistStore()
        val generator = ProactiveAssistGenerator(coordinator, store)

        val content = generator.refresh(input, nowEpochMs = 1_000L)

        assertEquals(AssistGenAiSource.GEMINI_NANO, content.source)
        assertEquals(content, store.latest())
        assertEquals(content, generator.cachedCopy("2026-06-10", nowEpochMs = 2_000L))
    }

    @Test
    fun `refresh falls back to a local digest when AI returns nothing`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val store = InMemoryProactiveAssistStore()
        val generator = ProactiveAssistGenerator(coordinator, store)

        val content = generator.refresh(input.copy(dueMedicationCount = 1), nowEpochMs = 1_000L)

        assertEquals(AssistGenAiSource.LOCAL, content.source)
        assertEquals(true, content.text.contains("medication", ignoreCase = true))
    }

    @Test
    fun `cachedCopy rejects stale or wrong-day content`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Cached line.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val store = InMemoryProactiveAssistStore()
        val generator = ProactiveAssistGenerator(coordinator, store)
        generator.refresh(input, nowEpochMs = 0L)

        val freshnessWindow = 12L * 60 * 60 * 1000
        assertNull(generator.cachedCopy("2026-06-11", nowEpochMs = 1_000L))
        assertNull(generator.cachedCopy("2026-06-10", nowEpochMs = freshnessWindow + 1))
    }
}

private class InMemoryProactiveAssistStore : ProactiveAssistStore {
    private var content: ProactiveAssistContent? = null

    override fun latest(): ProactiveAssistContent? = content

    override fun save(content: ProactiveAssistContent) {
        this.content = content
    }

    override fun clear() {
        content = null
    }
}
