package com.ChronosFlow.VBCR.core.ai.genai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class CloudGeminiGatewayImpl @Inject constructor(
    private val config: CloudGeminiConfig,
    private val foregroundGate: AppForegroundGate
) : CloudGeminiGateway {
    /** Time source, overridable in tests; drives the response-cache TTL. */
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Replay cache for cloud responses, symmetric with the on-device gateways: an identical prompt
     * returns instantly without a second network round-trip. In-memory and bounded.
     */
    private val responseCache = GenAiResponseCache(maxEntries = CACHE_MAX_ENTRIES, ttlMs = CACHE_TTL_MS)

    override suspend fun generateText(prompt: String): Result<String> {
        // A cache hit replays prior output without a network call, so serve it before the
        // configuration/foreground gates.
        responseCache.get(prompt, nowMs())?.let { return Result.success(it) }

        val result = withContext(Dispatchers.IO) {
            if (!config.isCloudConfigured) {
                return@withContext Result.failure<String>(
                    IllegalStateException(
                        "Cloud Gemini is not configured. Connect a Firebase project (add google-services.json) " +
                            "with Firebase AI Logic enabled, then rebuild."
                    )
                )
            }
            if (!foregroundGate.isAppInForeground()) {
                return@withContext Result.failure<String>(
                    IllegalStateException("Cloud Gemini requests require ChronosFlow to be in the foreground.")
                )
            }
            try {
                val text = withTimeout(CLOUD_TIMEOUT_MS) {
                    val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                        modelName = CloudGeminiModels.CLOUD_MODEL,
                        generationConfig = generationConfig {
                            temperature = 0.4f
                            topK = 24
                            maxOutputTokens = 2_048
                        }
                    )
                    val response = model.generateContent(prompt)
                    response.text?.trim().orEmpty().ifBlank {
                        error("Cloud Gemini returned an empty response.")
                    }
                }
                Result.success(text)
            } catch (e: TimeoutCancellationException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                // Caller cancellation must unwind, not surface as a failed generation that then
                // triggers heuristic fallback planning after the user already left.
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        result.getOrNull()?.let { responseCache.put(prompt, it, nowMs()) }
        return result
    }

    private companion object {
        const val CACHE_MAX_ENTRIES = 16
        const val CACHE_TTL_MS = 10L * 60 * 1000
        const val CLOUD_TIMEOUT_MS = 10_000L
    }
}

object CloudGeminiModels {
    const val CLOUD_MODEL = "gemini-2.0-flash"
}
