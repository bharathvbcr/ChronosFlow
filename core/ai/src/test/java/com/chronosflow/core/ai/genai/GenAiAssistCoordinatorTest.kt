package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.data.privacy.AssistantPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenAiAssistCoordinatorTest {
    @Test
    fun `disabled privacy skips model calls`() = runTest {
        val preferences = mockk<AssistantPreferences>()
        every { preferences.assistantPrivacyModeValue() } returns PrivacyMode.DISABLED.name
        val onDevice = FakeAssistGateway(NanoModelStatus.AVAILABLE, "nano")
        val cloud = FakeCloudGateway("cloud")
        val coordinator = GenAiAssistCoordinator(preferences, onDevice, cloud, FakeTextToolsGateway())

        val generation = coordinator.generateAssistText("prompt")

        assertNull(generation.text)
        assertEquals(AssistGenAiSource.LOCAL, generation.source)
        assertEquals(0, onDevice.generateCount)
        assertEquals(0, cloud.generateCount)
    }

    @Test
    fun `cloud allowed uses cloud before nano`() = runTest {
        val preferences = mockk<AssistantPreferences>()
        every { preferences.assistantPrivacyModeValue() } returns PrivacyMode.CLOUD_ALLOWED.name
        val coordinator = GenAiAssistCoordinator(
            preferences,
            FakeAssistGateway(NanoModelStatus.AVAILABLE, "nano-response"),
            FakeCloudGateway("cloud-response"),
            FakeTextToolsGateway()
        )

        val generation = coordinator.generateAssistText("prompt")

        assertEquals("cloud-response", generation.text)
        assertEquals(AssistGenAiSource.CLOUD_GEMINI, generation.source)
    }

    @Test
    fun `on device only uses nano`() = runTest {
        val preferences = mockk<AssistantPreferences>()
        every { preferences.assistantPrivacyModeValue() } returns PrivacyMode.ON_DEVICE_ONLY.name
        val onDevice = FakeAssistGateway(NanoModelStatus.AVAILABLE, "nano-response")
        val coordinator = GenAiAssistCoordinator(
            preferences,
            onDevice,
            FakeCloudGateway("cloud-response"),
            FakeTextToolsGateway()
        )

        val generation = coordinator.generateAssistText("prompt")

        assertEquals("nano-response", generation.text)
        assertEquals(AssistGenAiSource.GEMINI_NANO, generation.source)
        assertEquals(1, onDevice.generateCount)
    }

    @Test
    fun `text tools route to gemini nano when on device`() = runTest {
        val preferences = mockk<AssistantPreferences>()
        every { preferences.assistantPrivacyModeValue() } returns PrivacyMode.ON_DEVICE_ONLY.name
        val textTools = FakeTextToolsGateway(response = "cleaned up")
        val coordinator = GenAiAssistCoordinator(
            preferences,
            FakeAssistGateway(NanoModelStatus.AVAILABLE, "nano"),
            FakeCloudGateway("cloud"),
            textTools
        )

        val proofread = coordinator.proofread("draft text")
        val rewrite = coordinator.rewrite("draft text", RewriteStyle.SHORTEN)
        val summary = coordinator.summarize("a long article body")

        assertEquals("cleaned up", proofread.text)
        assertEquals(AssistGenAiSource.GEMINI_NANO, proofread.source)
        assertEquals("cleaned up", rewrite.text)
        assertEquals("cleaned up", summary.text)
        assertEquals(3, textTools.callCount)
    }

    @Test
    fun `streaming routes to on-device gateway and is empty when disabled`() = runTest {
        val preferences = mockk<AssistantPreferences>()
        every { preferences.assistantPrivacyModeValue() } returns PrivacyMode.ON_DEVICE_ONLY.name
        val coordinator = GenAiAssistCoordinator(
            preferences,
            FakeAssistGateway(NanoModelStatus.AVAILABLE, "streamed answer"),
            FakeCloudGateway("cloud"),
            FakeTextToolsGateway()
        )

        val streamed = coordinator.generateAssistTextStream("prompt").toList()
        val disabled = coordinator.generateAssistTextStream("prompt", PrivacyMode.DISABLED).toList()

        assertEquals(listOf("streamed answer"), streamed)
        assertEquals(emptyList<String>(), disabled)
    }

    @Test
    fun `text tools skip model calls when disabled`() = runTest {
        val preferences = mockk<AssistantPreferences>()
        every { preferences.assistantPrivacyModeValue() } returns PrivacyMode.DISABLED.name
        val textTools = FakeTextToolsGateway(response = "cleaned up")
        val coordinator = GenAiAssistCoordinator(
            preferences,
            FakeAssistGateway(NanoModelStatus.AVAILABLE, "nano"),
            FakeCloudGateway("cloud"),
            textTools
        )

        val proofread = coordinator.proofread("draft text")

        assertNull(proofread.text)
        assertEquals(AssistGenAiSource.LOCAL, proofread.source)
        assertEquals(0, textTools.callCount)
    }
}

private class FakeAssistGateway(
    private val status: NanoModelStatus,
    private val response: String
) : OnDeviceGeminiGateway {
    var generateCount = 0

    override val runtimeStatus = MutableStateFlow(GenAiRuntimeStatus(nanoStatus = status))

    override suspend fun refreshStatus(): NanoModelStatus = status

    override suspend fun ensureReadyForInference(): NanoModelStatus = status

    override suspend fun generateText(prompt: String): Result<String> {
        generateCount++
        return Result.success(response)
    }

    override fun generateTextStream(prompt: String): kotlinx.coroutines.flow.Flow<String> =
        kotlinx.coroutines.flow.flowOf(response)
}

private class FakeCloudGateway(
    private val response: String
) : CloudGeminiGateway {
    var generateCount = 0

    override suspend fun generateText(prompt: String): Result<String> {
        generateCount++
        return Result.success(response)
    }
}

private class FakeTextToolsGateway(
    private val response: String = "ignored"
) : OnDeviceTextToolsGateway {
    var callCount = 0

    override suspend fun summarize(text: String, style: SummaryStyle): Result<String> {
        callCount++
        return Result.success(response)
    }

    override suspend fun proofread(text: String): Result<String> {
        callCount++
        return Result.success(response)
    }

    override suspend fun rewrite(text: String, style: RewriteStyle): Result<String> {
        callCount++
        return Result.success(response)
    }
}
