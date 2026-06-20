package com.ChronosFlow.VBCR.core.ai.genai

import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.data.privacy.AssistantPreferences
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

    fun privacyMode(): PrivacyMode {
        val stored = runCatching {
            PrivacyMode.valueOf(assistantPreferences.assistantPrivacyModeValue())
        }.getOrDefault(PrivacyMode.ON_DEVICE_ONLY)
        // Cloud calls require explicit user consent. If the stored mode is CLOUD_ALLOWED but the
        // user has not opted in (or has since revoked consent), silently fall back to on-device.
        return if (stored == PrivacyMode.CLOUD_ALLOWED && !assistantPreferences.isCloudAiEnabled()) {
            PrivacyMode.ON_DEVICE_ONLY
        } else {
            stored
        }
    }

    suspend fun refreshRuntimeStatus(): GenAiRuntimeStatus {
        onDeviceGateway.refreshStatus()
        return onDeviceGateway.runtimeStatus.value
    }

    /**
     * Proactively readies the on-device model (status + any pending download) so the first
     * user-triggered generation of a session isn't cold. No-op when AI is disabled. Safe to call on
     * every foreground: the gateway's status cache and download-failure cooldown keep repeats cheap.
     */
    suspend fun prewarm(privacyMode: PrivacyMode = privacyMode()) {
        if (privacyMode == PrivacyMode.DISABLED) return
        runCatching { onDeviceGateway.ensureReadyForInference() }
    }

    suspend fun generateAssistText(
        prompt: String,
        privacyMode: PrivacyMode = privacyMode(),
        profile: GenerationProfile = GenerationProfile.BALANCED
    ): AssistTextGeneration {
        return when (privacyMode) {
            PrivacyMode.DISABLED -> AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
            PrivacyMode.ON_DEVICE_ONLY -> generateWithNano(prompt, profile)
            PrivacyMode.CLOUD_ALLOWED -> generateWithCloudThenNano(prompt, profile)
        }
    }

    /**
     * Streams an on-device Gemini Nano response as a growing cumulative string. Returns an empty flow
     * when AI is disabled or Nano is unavailable so callers can fall back to [generateAssistText].
     * Cloud streaming is not wired, so this always uses the on-device path.
     */
    fun generateAssistTextStream(
        prompt: String,
        privacyMode: PrivacyMode = privacyMode(),
        profile: GenerationProfile = GenerationProfile.BALANCED
    ): Flow<String> = if (privacyMode == PrivacyMode.DISABLED) {
        emptyFlow()
    } else {
        onDeviceGateway.generateTextStream(prompt, profile)
    }

    /**
     * Day-plan variant that routes the static [prefix] (role + schema) and per-request [suffix]
     * (constraints + user data) separately so on-device Gemini Nano can apply implicit prefix
     * caching. Cloud path concatenates them since Firebase AI has no equivalent API.
     */
    suspend fun generateAssistTextWithPrefix(
        prefix: String,
        suffix: String,
        privacyMode: PrivacyMode = privacyMode(),
        profile: GenerationProfile = GenerationProfile.BALANCED
    ): AssistTextGeneration {
        return when (privacyMode) {
            PrivacyMode.DISABLED -> AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
            PrivacyMode.ON_DEVICE_ONLY -> generateWithNanoPrefix(prefix, suffix, profile)
            PrivacyMode.CLOUD_ALLOWED -> generateWithCloudThenNanoPrefix(prefix, suffix, profile)
        }
    }

    private suspend fun generateWithNanoPrefix(prefix: String, suffix: String, profile: GenerationProfile): AssistTextGeneration {
        val status = onDeviceGateway.refreshStatus()
        if (status == NanoModelStatus.AVAILABLE || status == NanoModelStatus.DOWNLOADABLE) {
            onDeviceGateway.generateTextWithPrefix(prefix, suffix, profile).getOrNull()?.let { text ->
                return AssistTextGeneration(text = text, source = AssistGenAiSource.GEMINI_NANO)
            }
        }
        return AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
    }

    private suspend fun generateWithCloudThenNanoPrefix(prefix: String, suffix: String, profile: GenerationProfile): AssistTextGeneration {
        cloudGateway.generateText("$prefix\n$suffix").getOrNull()?.let { text ->
            return AssistTextGeneration(text = text, source = AssistGenAiSource.CLOUD_GEMINI)
        }
        return generateWithNanoPrefix(prefix, suffix, profile)
    }

    private suspend fun generateWithNano(prompt: String, profile: GenerationProfile): AssistTextGeneration {
        val status = onDeviceGateway.refreshStatus()
        if (status == NanoModelStatus.AVAILABLE || status == NanoModelStatus.DOWNLOADABLE) {
            onDeviceGateway.generateText(prompt, profile).getOrNull()?.let { text ->
                return AssistTextGeneration(text = text, source = AssistGenAiSource.GEMINI_NANO)
            }
        }
        return AssistTextGeneration(text = null, source = AssistGenAiSource.LOCAL)
    }

    private suspend fun generateWithCloudThenNano(prompt: String, profile: GenerationProfile): AssistTextGeneration {
        cloudGateway.generateText(prompt).getOrNull()?.let { text ->
            return AssistTextGeneration(text = text, source = AssistGenAiSource.CLOUD_GEMINI)
        }
        return generateWithNano(prompt, profile)
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
