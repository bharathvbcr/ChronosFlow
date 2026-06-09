package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.data.privacy.AssistantPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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
        val coordinator = GenAiAssistCoordinator(preferences, onDevice, cloud)

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
            FakeCloudGateway("cloud-response")
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
            FakeCloudGateway("cloud-response")
        )

        val generation = coordinator.generateAssistText("prompt")

        assertEquals("nano-response", generation.text)
        assertEquals(AssistGenAiSource.GEMINI_NANO, generation.source)
        assertEquals(1, onDevice.generateCount)
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
