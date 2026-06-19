package com.ChronosFlow.VBCR.core.ai.genai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Mirrors ML Kit [com.google.mlkit.genai.common.FeatureStatus] without leaking SDK types. */
enum class NanoModelStatus {
    UNAVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    AVAILABLE
}

data class GenAiRuntimeStatus(
    val nanoStatus: NanoModelStatus = NanoModelStatus.UNAVAILABLE,
    val cloudConfigured: Boolean = false,
    val statusMessage: String? = null,
    val selectedModelLabel: String = "stable/full",
    val previewModelRequested: Boolean = false,
    val previewModelActive: Boolean = false,
    val previewModelFallback: Boolean = false
)

interface AppForegroundGate {
    fun isAppInForeground(): Boolean
}

interface CloudGeminiConfig {
    /** True when a default FirebaseApp is initialized — i.e. cloud AI (Firebase AI Logic) is available. */
    val isCloudConfigured: Boolean
}

interface OnDeviceGeminiGateway {
    val runtimeStatus: StateFlow<GenAiRuntimeStatus>

    suspend fun refreshStatus(): NanoModelStatus

    suspend fun ensureReadyForInference(): NanoModelStatus

    suspend fun generateText(prompt: String, profile: GenerationProfile = GenerationProfile.BALANCED): Result<String>

    /**
     * Generates text using ML Kit prefix caching. The [prefix] is the static system instruction
     * shared across calls (e.g. role + schema); the [suffix] is the dynamic per-request data.
     * The SDK reuses the cached KV state for the prefix, reducing first-token latency by ~45% on
     * supported Pixel devices. Falls back to a combined prompt on implementations that do not
     * override this (cloud paths, test fakes).
     */
    suspend fun generateTextWithPrefix(
        prefix: String,
        suffix: String,
        profile: GenerationProfile = GenerationProfile.BALANCED
    ): Result<String> = generateText("$prefix\n$suffix", profile)

    /**
     * Streams a cumulative response. Emits nothing (an empty flow) when the app is backgrounded or
     * Gemini Nano is not ready, so callers can fall back to [generateText] or local copy.
     */
    fun generateTextStream(prompt: String, profile: GenerationProfile = GenerationProfile.BALANCED): Flow<String>
}

interface CloudGeminiGateway {
    suspend fun generateText(prompt: String): Result<String>
}
