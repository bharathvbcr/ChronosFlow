package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.data.privacy.AssistantPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

enum class AssistGenAiSource {
    GEMINI_NANO,
    CLOUD_GEMINI,
    LOCAL
}

data class AssistTextGeneration(
    val text: String?,
    val source: AssistGenAiSource
)

@Singleton
class GenAiAssistCoordinator @Inject constructor(
    private val assistantPreferences: AssistantPreferences,
    private val onDeviceGateway: OnDeviceGeminiGateway,
    private val cloudGateway: CloudGeminiGateway
) {
    val runtimeStatus: StateFlow<GenAiRuntimeStatus> = onDeviceGateway.runtimeStatus

    fun privacyMode(): PrivacyMode = runCatching {
        PrivacyMode.valueOf(assistantPreferences.assistantPrivacyModeValue())
    }.getOrDefault(PrivacyMode.ON_DEVICE_ONLY)

    suspend fun refreshRuntimeStatus(): GenAiRuntimeStatus {
        onDeviceGateway.refreshStatus()
        return onDeviceGateway.runtimeStatus.value
    }

    suspend fun generateAssistText(
        prompt: String,
        privacyMode: PrivacyMode = privacyMode()
    ): AssistTextGeneration {
        return when (privacyMode) {
            PrivacyMode.DISABLED -> AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
            PrivacyMode.ON_DEVICE_ONLY -> generateWithNano(prompt)
            PrivacyMode.CLOUD_ALLOWED -> generateWithCloudThenNano(prompt)
        }
    }

    private suspend fun generateWithNano(prompt: String): AssistTextGeneration {
        val status = onDeviceGateway.refreshStatus()
        if (status == NanoModelStatus.AVAILABLE || status == NanoModelStatus.DOWNLOADABLE) {
            onDeviceGateway.generateText(prompt).getOrNull()?.let { text ->
                return AssistTextGeneration(text = text, source = AssistGenAiSource.GEMINI_NANO)
            }
        }
        return AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
    }

    private suspend fun generateWithCloudThenNano(prompt: String): AssistTextGeneration {
        cloudGateway.generateText(prompt).getOrNull()?.let { text ->
            return AssistTextGeneration(text = text, source = AssistGenAiSource.CLOUD_GEMINI)
        }
        return generateWithNano(prompt)
    }
}
