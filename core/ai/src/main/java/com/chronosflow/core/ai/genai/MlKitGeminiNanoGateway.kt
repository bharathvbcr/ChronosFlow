package com.chronosflow.core.ai.genai

import android.util.Log
import com.google.mlkit.genai.common.GenAiException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout

@Singleton
class MlKitGeminiNanoGateway @Inject constructor(
    private val foregroundGate: AppForegroundGate,
    private val cloudGeminiConfig: CloudGeminiConfig,
    private val client: NanoPromptClient
) : OnDeviceGeminiGateway {
    private val _runtimeStatus = MutableStateFlow(
        GenAiRuntimeStatus(cloudConfigured = !cloudGeminiConfig.apiKey.isNullOrBlank())
    )
    override val runtimeStatus: StateFlow<GenAiRuntimeStatus> = _runtimeStatus.asStateFlow()

    override suspend fun refreshStatus(): NanoModelStatus {
        val mapped = client.checkStatus()
        val selection = client.selectionState()
        _runtimeStatus.update {
            it.copy(
                nanoStatus = mapped,
                statusMessage = statusMessageFor(mapped, selection),
                selectedModelLabel = selection.modelLabel,
                previewModelRequested = selection.previewRequested,
                previewModelActive = selection.previewActive,
                previewModelFallback = selection.usedStableFallback
            )
        }
        return mapped
    }

    override suspend fun ensureReadyForInference(): NanoModelStatus {
        var status = refreshStatus()
        if (status == NanoModelStatus.DOWNLOADABLE) {
            runCatching {
                withTimeout(DOWNLOAD_TIMEOUT_MS) {
                    client.download().collect { download ->
                        when (download) {
                            NanoDownloadEvent.Started -> {
                                _runtimeStatus.update {
                                    it.copy(
                                        nanoStatus = NanoModelStatus.DOWNLOADING,
                                        statusMessage = "Downloading ${downloadModelLabel(client.selectionState())} on device…"
                                    )
                                }
                            }
                            is NanoDownloadEvent.Progress -> {
                                _runtimeStatus.update {
                                    it.copy(
                                        nanoStatus = NanoModelStatus.DOWNLOADING,
                                        statusMessage = "Downloading ${downloadModelLabel(client.selectionState())}…"
                                    )
                                }
                            }
                            NanoDownloadEvent.Completed -> Unit
                            is NanoDownloadEvent.Failed -> throw download.error
                        }
                    }
                }
            }.onFailure { error ->
                runCatching {
                    Log.w(TAG, "Gemini Nano download failed: ${error.message}")
                }
            }
            status = refreshStatus()
        }
        return status
    }

    override suspend fun generateText(prompt: String): Result<String> {
        if (!foregroundGate.isAppInForeground()) {
            return Result.failure(
                IllegalStateException("Gemini Nano inference requires ChronosFlow to be in the foreground.")
            )
        }
        val readiness = ensureReadyForInference()
        if (readiness != NanoModelStatus.AVAILABLE) {
            return Result.failure(
                IllegalStateException(statusMessageFor(readiness) ?: "Gemini Nano is not ready on this device.")
            )
        }

        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        var lastError: Throwable? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val inference = runCatching { client.generateText(prompt) }
            if (inference.isSuccess) {
                val text = inference.getOrThrow()
                if (text.isNotBlank()) return Result.success(text)
                lastError = IllegalStateException("Gemini Nano returned an empty response.")
            } else {
                lastError = inference.exceptionOrNull()
                if (!shouldRetry(lastError)) break
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Gemini Nano inference failed."))
    }

    override fun generateTextStream(prompt: String): Flow<String> = flow {
        if (!foregroundGate.isAppInForeground()) return@flow
        if (ensureReadyForInference() != NanoModelStatus.AVAILABLE) return@flow
        emitAll(client.generateTextStream(prompt))
    }

    private fun statusMessageFor(
        status: NanoModelStatus,
        selection: NanoModelSelectionState = client.selectionState()
    ): String? {
        return when {
            selection.previewActive -> when (status) {
                NanoModelStatus.AVAILABLE -> "Gemini Nano preview/fast is ready for on-device planning."
                NanoModelStatus.DOWNLOADABLE -> "Gemini Nano preview/fast can be downloaded for this device."
                NanoModelStatus.DOWNLOADING -> "Gemini Nano preview/fast is downloading through AICore."
                NanoModelStatus.UNAVAILABLE ->
                    "Gemini Nano preview/fast is unavailable on this device. ChronosFlow will use local heuristics."
            }

            selection.usedStableFallback -> when (status) {
                NanoModelStatus.AVAILABLE ->
                    "Gemini Nano preview/fast is unavailable on this device, so ChronosFlow will use the stable/full Gemini Nano model."
                NanoModelStatus.DOWNLOADABLE ->
                    "Gemini Nano preview/fast is unavailable on this device. The stable/full Gemini Nano model can be downloaded instead."
                NanoModelStatus.DOWNLOADING ->
                    "Gemini Nano preview/fast is unavailable on this device. Downloading the stable/full Gemini Nano model through AICore."
                NanoModelStatus.UNAVAILABLE ->
                    "Gemini Nano preview/fast is unavailable on this device, and the stable/full Gemini Nano model is unavailable. ChronosFlow will use local heuristics."
            }

            else -> when (status) {
                NanoModelStatus.AVAILABLE -> "Gemini Nano is ready for on-device planning."
                NanoModelStatus.DOWNLOADABLE -> "Gemini Nano can be downloaded for this device."
                NanoModelStatus.DOWNLOADING -> "Gemini Nano is downloading through AICore."
                NanoModelStatus.UNAVAILABLE ->
                    "Gemini Nano is unavailable on this device. ChronosFlow will use local heuristics."
            }
        }
    }

    private fun downloadModelLabel(selection: NanoModelSelectionState): String = when {
        selection.previewActive -> "Gemini Nano preview/fast"
        selection.usedStableFallback -> "stable/full Gemini Nano"
        else -> "Gemini Nano"
    }

    private fun shouldRetry(error: Throwable?): Boolean {
        val message = error?.message.orEmpty().lowercase()
        return message.contains("busy") ||
            message.contains("quota") ||
            (error is GenAiException && message.contains("quota"))
    }

    private companion object {
        const val TAG = "MlKitGeminiNano"
        const val MAX_ATTEMPTS = 3
        const val INITIAL_BACKOFF_MS = 400L
        const val MAX_BACKOFF_MS = 2_000L
        const val DOWNLOAD_TIMEOUT_MS = 120_000L
    }
}
