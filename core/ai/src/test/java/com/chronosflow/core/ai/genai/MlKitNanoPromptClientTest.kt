package com.chronosflow.core.ai.genai

import com.chronosflow.core.data.privacy.AssistantPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MlKitNanoPromptClientTest {
    private val assistantPreferences: AssistantPreferences = mockk()

    @Test
    fun `uses stable full model when preview is disabled`() = runTest {
        every { assistantPreferences.preferPreviewNanoModel() } returns false
        val stableHandle = FakeNanoModelHandle(status = NanoModelStatus.AVAILABLE, generatedText = "stable")
        val previewHandle = FakeNanoModelHandle(status = NanoModelStatus.AVAILABLE, generatedText = "preview")
        val client = MlKitNanoPromptClient(
            assistantPreferences = assistantPreferences,
            factory = FakeNanoModelClientFactory(
                stableHandle = stableHandle,
                previewHandle = previewHandle
            )
        )

        val status = client.checkStatus()
        val generated = client.generateText("plan my day")

        assertEquals(NanoModelStatus.AVAILABLE, status)
        assertEquals("stable", generated)
        assertEquals(NanoModelVariant.STABLE_FULL, client.selectionState().activeVariant)
        assertFalse(client.selectionState().previewRequested)
        assertFalse(client.selectionState().usedStableFallback)
        assertEquals(1, stableHandle.statusChecks)
        assertEquals(0, previewHandle.statusChecks)
    }

    @Test
    fun `uses preview fast model when preview is enabled and available`() = runTest {
        every { assistantPreferences.preferPreviewNanoModel() } returns true
        val stableHandle = FakeNanoModelHandle(status = NanoModelStatus.AVAILABLE, generatedText = "stable")
        val previewHandle = FakeNanoModelHandle(status = NanoModelStatus.AVAILABLE, generatedText = "preview")
        val client = MlKitNanoPromptClient(
            assistantPreferences = assistantPreferences,
            factory = FakeNanoModelClientFactory(
                stableHandle = stableHandle,
                previewHandle = previewHandle
            )
        )

        val status = client.checkStatus()
        val generated = client.generateText("plan my day")

        assertEquals(NanoModelStatus.AVAILABLE, status)
        assertEquals("preview", generated)
        assertEquals(NanoModelVariant.PREVIEW_FAST, client.selectionState().activeVariant)
        assertTrue(client.selectionState().previewRequested)
        assertFalse(client.selectionState().usedStableFallback)
        assertTrue(previewHandle.statusChecks > 0)
        assertEquals(0, stableHandle.generateCalls)
    }

    @Test
    fun `falls back to stable full model when preview is unavailable`() = runTest {
        every { assistantPreferences.preferPreviewNanoModel() } returns true
        val stableHandle = FakeNanoModelHandle(status = NanoModelStatus.DOWNLOADABLE, generatedText = "stable")
        val previewHandle = FakeNanoModelHandle(status = NanoModelStatus.UNAVAILABLE, generatedText = "preview")
        val client = MlKitNanoPromptClient(
            assistantPreferences = assistantPreferences,
            factory = FakeNanoModelClientFactory(
                stableHandle = stableHandle,
                previewHandle = previewHandle
            )
        )

        val status = client.checkStatus()

        assertEquals(NanoModelStatus.DOWNLOADABLE, status)
        assertEquals(NanoModelVariant.STABLE_FULL, client.selectionState().activeVariant)
        assertTrue(client.selectionState().previewRequested)
        assertTrue(client.selectionState().usedStableFallback)
        assertTrue(previewHandle.statusChecks > 0)
        assertTrue(stableHandle.statusChecks > 0)
    }

    private class FakeNanoModelClientFactory(
        private val stableHandle: NanoModelHandle,
        private val previewHandle: NanoModelHandle
    ) : NanoModelClientFactory {
        override fun create(variant: NanoModelVariant): NanoModelHandle {
            return when (variant) {
                NanoModelVariant.STABLE_FULL -> stableHandle
                NanoModelVariant.PREVIEW_FAST -> previewHandle
            }
        }
    }

    private class FakeNanoModelHandle(
        private val status: NanoModelStatus,
        private val generatedText: String
    ) : NanoModelHandle {
        var statusChecks: Int = 0
        var generateCalls: Int = 0

        override suspend fun checkStatus(): NanoModelStatus {
            statusChecks++
            return status
        }

        override fun download(): Flow<NanoDownloadEvent> = emptyFlow()

        override suspend fun generateText(prompt: String): String {
            generateCalls++
            return generatedText
        }
    }
}
