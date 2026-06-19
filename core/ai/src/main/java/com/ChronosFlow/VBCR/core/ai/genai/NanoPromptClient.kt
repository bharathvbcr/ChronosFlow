package com.ChronosFlow.VBCR.core.ai.genai

import com.ChronosFlow.VBCR.core.data.privacy.AssistantPreferences
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.createCachedContextRequest
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface NanoDownloadEvent {
    data object Started : NanoDownloadEvent

    data class Progress(
        val downloadedBytes: Long
    ) : NanoDownloadEvent

    data object Completed : NanoDownloadEvent

    data class Failed(
        val error: Throwable
    ) : NanoDownloadEvent
}

enum class NanoModelVariant {
    STABLE_FULL,
    PREVIEW_FAST
}

data class NanoModelSelectionState(
    val activeVariant: NanoModelVariant = NanoModelVariant.STABLE_FULL,
    val previewRequested: Boolean = false,
    val usedStableFallback: Boolean = false
) {
    val previewActive: Boolean
        get() = previewRequested && activeVariant == NanoModelVariant.PREVIEW_FAST

    val modelLabel: String
        get() = when (activeVariant) {
            NanoModelVariant.STABLE_FULL -> "stable/full"
            NanoModelVariant.PREVIEW_FAST -> "preview/fast"
        }
}

interface NanoModelHandle {
    suspend fun checkStatus(): NanoModelStatus

    fun download(): Flow<NanoDownloadEvent>

    suspend fun generateText(prompt: String, profile: GenerationProfile = GenerationProfile.BALANCED): String

    /** Streams the response as a growing cumulative string, one emission per model chunk. */
    fun generateTextStream(prompt: String, profile: GenerationProfile = GenerationProfile.BALANCED): Flow<String>

    /**
     * Generates text with ML Kit implicit prefix caching. [prefix] is the static portion (role +
     * schema) that the SDK caches across calls; [suffix] is the per-request dynamic data.
     * Defaults to combining prefix+suffix for implementations without native PromptPrefix support.
     */
    suspend fun generateTextWithPrefix(
        prefix: String,
        suffix: String,
        profile: GenerationProfile = GenerationProfile.BALANCED
    ): String = generateText("$prefix\n$suffix", profile)
}

interface NanoModelClientFactory {
    fun create(variant: NanoModelVariant): NanoModelHandle
}

interface NanoPromptClient {
    suspend fun checkStatus(): NanoModelStatus

    fun download(): Flow<NanoDownloadEvent>

    suspend fun generateText(prompt: String, profile: GenerationProfile = GenerationProfile.BALANCED): String

    fun generateTextStream(prompt: String, profile: GenerationProfile = GenerationProfile.BALANCED): Flow<String>

    fun selectionState(): NanoModelSelectionState

    suspend fun generateTextWithPrefix(
        prefix: String,
        suffix: String,
        profile: GenerationProfile = GenerationProfile.BALANCED
    ): String = generateText("$prefix\n$suffix", profile)
}

@Singleton
class MlKitNanoPromptClient @Inject constructor(
    private val assistantPreferences: AssistantPreferences,
    private val factory: NanoModelClientFactory
) : NanoPromptClient {
    private var currentSelectionState = NanoModelSelectionState()
    private var activeHandle: NanoModelHandle? = null

    override suspend fun checkStatus(): NanoModelStatus {
        val resolved = resolveHandle()
        activeHandle = resolved.handle
        currentSelectionState = resolved.selectionState
        return resolved.status
    }

    override fun download(): Flow<NanoDownloadEvent> = flow {
        val resolved = resolveHandle()
        activeHandle = resolved.handle
        currentSelectionState = resolved.selectionState
        emitAll(resolved.handle.download())
    }

    override suspend fun generateText(prompt: String, profile: GenerationProfile): String {
        val resolved = ensureActiveHandle()
        return resolved.handle.generateText(prompt, profile)
    }

    override fun generateTextStream(prompt: String, profile: GenerationProfile): Flow<String> = flow {
        val resolved = ensureActiveHandle()
        emitAll(resolved.handle.generateTextStream(prompt, profile))
    }

    override suspend fun generateTextWithPrefix(prefix: String, suffix: String, profile: GenerationProfile): String {
        val resolved = ensureActiveHandle()
        return resolved.handle.generateTextWithPrefix(prefix, suffix, profile)
    }

    override fun selectionState(): NanoModelSelectionState = currentSelectionState

    private suspend fun ensureActiveHandle(): ResolvedNanoModel {
        val existing = activeHandle
        return if (existing != null) {
            ResolvedNanoModel(existing, currentSelectionState, NanoModelStatus.AVAILABLE)
        } else {
            val resolved = resolveHandle()
            activeHandle = resolved.handle
            currentSelectionState = resolved.selectionState
            resolved
        }
    }

    private suspend fun resolveHandle(): ResolvedNanoModel {
        val previewRequested = assistantPreferences.preferPreviewNanoModel()
        if (!previewRequested) {
            val stableHandle = factory.create(NanoModelVariant.STABLE_FULL)
            val stableStatus = stableHandle.checkStatus()
            return ResolvedNanoModel(
                handle = stableHandle,
                selectionState = NanoModelSelectionState(
                    activeVariant = NanoModelVariant.STABLE_FULL,
                    previewRequested = false,
                    usedStableFallback = false
                ),
                status = stableStatus
            )
        }

        val previewHandle = factory.create(NanoModelVariant.PREVIEW_FAST)
        val previewStatus = previewHandle.checkStatus()
        if (previewStatus != NanoModelStatus.UNAVAILABLE) {
            return ResolvedNanoModel(
                handle = previewHandle,
                selectionState = NanoModelSelectionState(
                    activeVariant = NanoModelVariant.PREVIEW_FAST,
                    previewRequested = true,
                    usedStableFallback = false
                ),
                status = previewStatus
            )
        }

        val stableHandle = factory.create(NanoModelVariant.STABLE_FULL)
        val stableStatus = stableHandle.checkStatus()
        return ResolvedNanoModel(
            handle = stableHandle,
            selectionState = NanoModelSelectionState(
                activeVariant = NanoModelVariant.STABLE_FULL,
                previewRequested = true,
                usedStableFallback = true
            ),
            status = stableStatus
        )
    }

    private data class ResolvedNanoModel(
        val handle: NanoModelHandle,
        val selectionState: NanoModelSelectionState,
        val status: NanoModelStatus
    )
}

@Singleton
class MlKitNanoModelClientFactory @Inject constructor() : NanoModelClientFactory {
    override fun create(variant: NanoModelVariant): NanoModelHandle {
        val model = when (variant) {
            NanoModelVariant.STABLE_FULL -> Generation.getClient()
            NanoModelVariant.PREVIEW_FAST -> {
                val previewFastConfig = generationConfig {
                    modelConfig = modelConfig {
                        releaseStage = ModelReleaseStage.PREVIEW
                        preference = ModelPreference.FAST
                    }
                }
                Generation.getClient(previewFastConfig)
            }
        }

        return object : NanoModelHandle {
            override suspend fun checkStatus(): NanoModelStatus = when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> NanoModelStatus.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> NanoModelStatus.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> NanoModelStatus.DOWNLOADING
                else -> NanoModelStatus.UNAVAILABLE
            }

            override fun download(): Flow<NanoDownloadEvent> = model.download().map { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> NanoDownloadEvent.Started
                    is DownloadStatus.DownloadProgress -> NanoDownloadEvent.Progress(status.totalBytesDownloaded)
                    DownloadStatus.DownloadCompleted -> NanoDownloadEvent.Completed
                    is DownloadStatus.DownloadFailed -> NanoDownloadEvent.Failed(status.e)
                }
            }

            override suspend fun generateText(prompt: String, profile: GenerationProfile): String {
                val response = model.generateContent(
                    generateContentRequest(TextPart(prompt)) {
                        temperature = profile.temperature
                        topK = profile.topK
                        candidateCount = 1
                        maxOutputTokens = profile.maxOutputTokens
                        seed = profile.seed
                    }
                )
                return response.candidates.firstOrNull()?.text?.trim().orEmpty()
            }

            override fun generateTextStream(prompt: String, profile: GenerationProfile): Flow<String> {
                val request = generateContentRequest(TextPart(prompt)) {
                    temperature = profile.temperature
                    topK = profile.topK
                    candidateCount = 1
                    maxOutputTokens = profile.maxOutputTokens
                    seed = profile.seed
                }
                return model.generateContentStream(request)
                    .map { response -> response.candidates.firstOrNull()?.text.orEmpty() }
                    .runningReduce { accumulated, delta -> accumulated + delta }
                    .map { it.trim() }
            }

            override suspend fun generateTextWithPrefix(prefix: String, suffix: String, profile: GenerationProfile): String {
                // Prefer an explicit CachedContext for the static prefix (the model reuses its
                // persisted KV state — a larger first-token win than implicit caching on long
                // prefixes); fall back to implicit PromptPrefix caching when unavailable.
                val resolvedCacheName = resolveCachedContextName(prefix)
                val response = model.generateContent(
                    generateContentRequest(TextPart(suffix)) {
                        if (resolvedCacheName != null) {
                            cachedContextName = resolvedCacheName
                        } else {
                            promptPrefix = PromptPrefix(prefix)
                        }
                        temperature = profile.temperature
                        topK = profile.topK
                        candidateCount = 1
                        maxOutputTokens = profile.maxOutputTokens
                        seed = profile.seed
                    }
                )
                return response.candidates.firstOrNull()?.text?.trim().orEmpty()
            }

            // --- Explicit ML Kit prefix caching --------------------------------------------------
            /** Maps a static prefix to the name of its persisted [CachedContext], created on demand. */
            private val cachedContextNames = HashMap<String, String>()
            private val cacheMutex = Mutex()

            /** Tri-state cache of [GenerativeModel.isCachingFeatureAvailable]; null until first checked. */
            private var cachingAvailable: Boolean? = null

            /**
             * Returns the name of a [CachedContext] for [prefix], creating (or reusing) one the first
             * time a long-enough prefix is seen. Returns null — so the caller uses implicit caching —
             * when the device lacks the caching feature, the prefix is short, or any cache op fails.
             */
            private suspend fun resolveCachedContextName(prefix: String): String? {
                if (prefix.length < MIN_PREFIX_CHARS_FOR_EXPLICIT_CACHE) return null
                return cacheMutex.withLock {
                    cachedContextNames[prefix]?.let { return@withLock it }
                    val available = cachingAvailable
                        ?: runCatching { model.isCachingFeatureAvailable() }.getOrDefault(false)
                            .also { cachingAvailable = it }
                    if (!available) return@withLock null
                    val name = "chronosflow-prefix-${prefix.hashCode().toUInt().toString(16)}"
                    val context = runCatching { model.caches.get(name) }.getOrNull()
                        ?: runCatching {
                            model.caches.create(createCachedContextRequest(name, PromptPrefix(prefix)))
                        }.getOrNull()
                    context?.name?.also { cachedContextNames[prefix] = it }
                }
            }
        }
    }

    private companion object {
        /** Below this prefix size implicit caching suffices, so a dedicated cache file isn't worth it. */
        const val MIN_PREFIX_CHARS_FOR_EXPLICIT_CACHE = 200
    }
}
