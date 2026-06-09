package com.chronosflow.core.ai.genai

import com.chronosflow.core.data.privacy.AssistantPreferences
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

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

    suspend fun generateText(prompt: String): String
}

interface NanoModelClientFactory {
    fun create(variant: NanoModelVariant): NanoModelHandle
}

interface NanoPromptClient {
    suspend fun checkStatus(): NanoModelStatus

    fun download(): Flow<NanoDownloadEvent>

    suspend fun generateText(prompt: String): String

    fun selectionState(): NanoModelSelectionState
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

    override suspend fun generateText(prompt: String): String {
        val resolved = ensureActiveHandle()
        return resolved.handle.generateText(prompt)
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

            override suspend fun generateText(prompt: String): String {
                val response = model.generateContent(
                    generateContentRequest(TextPart(prompt)) {
                        temperature = 0.35f
                        topK = 16
                        candidateCount = 1
                    }
                )
                return response.candidates.firstOrNull()?.text?.trim().orEmpty()
            }
        }
    }
}
