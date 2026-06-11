package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.data.privacy.AssistantPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

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
    private val cloudGateway: CloudGeminiGateway,
    private val textToolsGateway: OnDeviceTextToolsGateway
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

    /**
     * Streams an on-device Gemini Nano response as a growing cumulative string. Returns an empty flow
     * when AI is disabled or Nano is unavailable so callers can fall back to [generateAssistText].
     * Cloud streaming is not wired, so this always uses the on-device path.
     */
    fun generateAssistTextStream(
        prompt: String,
        privacyMode: PrivacyMode = privacyMode()
    ): Flow<String> = if (privacyMode == PrivacyMode.DISABLED) {
        emptyFlow()
    } else {
        onDeviceGateway.generateTextStream(prompt)
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

    /**
     * Condense [text] with the on-device ML Kit GenAI Summarization feature. Returns a [LOCAL]
     * generation with a null text when AI is disabled, the input is blank, or the device cannot run
     * the feature — callers should fall back to their own copy in that case.
     */
    suspend fun summarize(
        text: String,
        style: SummaryStyle = SummaryStyle.ONE_BULLET,
        privacyMode: PrivacyMode = privacyMode()
    ): AssistTextGeneration = runTextTool(privacyMode, text) { textToolsGateway.summarize(text, style) }

    /** Grammar/spelling clean-up via the on-device ML Kit GenAI Proofreading feature. */
    suspend fun proofread(
        text: String,
        privacyMode: PrivacyMode = privacyMode()
    ): AssistTextGeneration = runTextTool(privacyMode, text) { textToolsGateway.proofread(text) }

    /** Tone/length rewrite via the on-device ML Kit GenAI Rewriting feature. */
    suspend fun rewrite(
        text: String,
        style: RewriteStyle,
        privacyMode: PrivacyMode = privacyMode()
    ): AssistTextGeneration = runTextTool(privacyMode, text) { textToolsGateway.rewrite(text, style) }

    private suspend fun runTextTool(
        privacyMode: PrivacyMode,
        input: String,
        block: suspend () -> Result<String>
    ): AssistTextGeneration {
        if (privacyMode == PrivacyMode.DISABLED || input.isBlank()) {
            return AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
        }
        return block().getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { AssistTextGeneration(text = it, source = AssistGenAiSource.GEMINI_NANO) }
            ?: AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
    }
}
