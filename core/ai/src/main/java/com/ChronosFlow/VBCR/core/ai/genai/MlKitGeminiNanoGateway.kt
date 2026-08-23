package com.ChronosFlow.VBCR.core.ai.genai

import android.util.Log
import com.google.mlkit.genai.common.GenAiException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class MlKitGeminiNanoGateway @Inject constructor(
    private val foregroundGate: AppForegroundGate,
    private val cloudGeminiConfig: CloudGeminiConfig,
    private val client: NanoPromptClient
) : OnDeviceGeminiGateway {
    private val _runtimeStatus = MutableStateFlow(
        GenAiRuntimeStatus(cloudConfigured = cloudGeminiConfig.isCloudConfigured)
    )
    override val runtimeStatus: StateFlow<GenAiRuntimeStatus> = _runtimeStatus.asStateFlow()

    /**
     * Time source, overridable in tests. Drives the short status cache and response cache TTLs.
     * Kept as a property rather than a constructor parameter so Hilt can construct the gateway.
     */
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    /** Epoch ms of the last check that returned [NanoModelStatus.AVAILABLE], or null when not cached. */
    @Volatile private var availableCheckedAtMs: Long? = null

    /**
     * Epoch ms until which a fresh download attempt is suppressed after one failed. A doomed download
     * (offline, no space) can take up to [DOWNLOAD_TIMEOUT_MS]; without this, every request would
     * re-pay that wait instead of failing fast and letting the caller fall back. Null = no cooldown.
     */
    @Volatile private var downloadCooldownUntilMs: Long? = null

    /**
     * Replay cache for completed on-device generations. Repeating the same request — re-opening a
     * plan, re-proofreading unchanged text — returns instantly without a cold inference, and still
     * serves while backgrounded since it never touches ML Kit. In-memory and bounded, so nothing is
     * persisted off-device.
     *
     * The plain [generateText] path uses exact replay only. The prefix path ([generateTextWithPrefix])
     * additionally matches near-duplicates by the **dynamic suffix**, so a re-plan whose data barely
     * changed reuses the prior output. The two paths use separate namespaces ([plainNamespace] vs
     * [suffixNamespace]) so a suffix-similarity scan never matches a full-prompt entry (whose vector
     * would be dominated by the shared static prefix).
     */
    private val responseCache = SemanticResponseCache(maxEntries = RESPONSE_CACHE_MAX_ENTRIES, ttlMs = RESPONSE_CACHE_TTL_MS)

    /**
     * Coalesces concurrent identical requests: on-device inference is a scarce, effectively serial
     * resource, so two callers asking for the same (profile, prompt) at once — a double tap, a
     * recomposition firing twice — share one inference instead of queueing a redundant second.
     */
    private val inFlight = HashMap<String, CompletableDeferred<Result<String>>>()
    private val inFlightMutex = Mutex()

    /**
     * Guards the check-then-act sequences on [availableCheckedAtMs] and [downloadCooldownUntilMs].
     * @Volatile alone ensures visibility but not atomicity of compound operations (TS-009).
     */
    private val statusMutex = Mutex()

    override suspend fun refreshStatus(): NanoModelStatus {
        // Steady-state AVAILABLE rarely flips mid-session, so a short TTL collapses the redundant
        // round-trips a single generation otherwise makes (coordinator pre-check + readiness check).
        // statusMutex serialises the check-then-act so two coroutines cannot both pass the null
        // check and each fire a redundant checkStatus() call (TS-009).
        statusMutex.withLock {
            availableCheckedAtMs?.let { checkedAt ->
                if (nowMs() - checkedAt <= STATUS_CACHE_TTL_MS) return NanoModelStatus.AVAILABLE
            }
        }
        val mapped = client.checkStatus()
        statusMutex.withLock {
            availableCheckedAtMs = if (mapped == NanoModelStatus.AVAILABLE) nowMs() else null
        }
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
            // After a failed download, suppress re-attempts for a cooldown so a flaky/offline device
            // fails fast and falls back instead of blocking every request on a doomed 120s retry.
            // statusMutex makes the read-compare-return atomic so two coroutines cannot race past
            // the cooldown guard simultaneously (TS-009).
            statusMutex.withLock {
                downloadCooldownUntilMs?.let { until ->
                    if (nowMs() < until) return status
                }
            }
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
                statusMutex.withLock { downloadCooldownUntilMs = nowMs() + DOWNLOAD_COOLDOWN_MS }
                runCatching {
                    Log.w(TAG, "Gemini Nano download failed: ${error.message}")
                }
            }.onSuccess {
                statusMutex.withLock { downloadCooldownUntilMs = null }
            }
            status = refreshStatus()
        }
        return status
    }

    override suspend fun generateText(prompt: String, profile: GenerationProfile): Result<String> {
        // A cache hit is a replay of the user's own prior on-device output, so it can be served
        // before the foreground/readiness gates — it never runs inference. Exact-only here: a plain
        // prompt has no static/dynamic split, so a near-duplicate could differ in what matters.
        responseCache.getExact(inFlightKey(profile, prompt), nowMs())?.let { return Result.success(it) }

        // Single-flight: if an identical request is already running, await its result instead of
        // launching a second inference. The leader (no existing entry) runs and shares its outcome.
        val key = inFlightKey(profile, prompt)
        var leader: CompletableDeferred<Result<String>>? = null
        val joined = inFlightMutex.withLock {
            inFlight[key] ?: CompletableDeferred<Result<String>>().also {
                inFlight[key] = it
                leader = it
            }
        }
        val pending = leader
        if (pending == null) {
            return joined.await()
        }

        val result = try {
            runGeneration(prompt, profile)
        } catch (error: Throwable) {
            inFlightMutex.withLock { inFlight.remove(key) }
            pending.complete(Result.failure(error))
            throw error
        }
        inFlightMutex.withLock { inFlight.remove(key) }
        pending.complete(result)
        return result
    }

    private suspend fun runGeneration(prompt: String, profile: GenerationProfile): Result<String> {
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
            val inference = runCatching {
                withTimeoutOrNull(INFERENCE_TIMEOUT_MS) { client.generateText(prompt, profile) }
            }
            inference.exceptionOrNull()
                ?.takeIf { it is kotlinx.coroutines.CancellationException && it !is TimeoutCancellationException }
                ?.let { throw it }
            if (inference.isSuccess) {
                val text = inference.getOrThrow()
                if (text != null && text.isNotBlank()) {
                    responseCache.put(inFlightKey(profile, prompt), plainNamespace(profile), prompt, text, nowMs())
                    return Result.success(text)
                }
                lastError = IllegalStateException("Gemini Nano returned an empty response.")
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            } else {
                lastError = inference.exceptionOrNull()
                if (!shouldRetry(lastError)) break
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Gemini Nano inference failed."))
    }

    private fun inFlightKey(profile: GenerationProfile, prompt: String): String = "${profile.name} $prompt"

    /** Semantic-cache namespace for plain-prompt entries (exact-only; never near-duplicate matched). */
    private fun plainNamespace(profile: GenerationProfile): String = "${profile.name}plain"

    /** Semantic-cache namespace for suffix entries — kept separate so suffix scans never see full prompts. */
    private fun suffixNamespace(profile: GenerationProfile): String = "${profile.name}suffix"

    override suspend fun generateTextWithPrefix(
        prefix: String,
        suffix: String,
        profile: GenerationProfile
    ): Result<String> {
        val combined = prefix + suffix
        // Exact replay on the full prompt, then a near-duplicate match on the dynamic suffix only —
        // the static prefix (role + schema) is shared across every call and must not drive the match.
        responseCache.lookup(inFlightKey(profile, combined), suffixNamespace(profile), suffix, nowMs())
            ?.let { return Result.success(it) }

        val key = inFlightKey(profile, combined)
        var leader: CompletableDeferred<Result<String>>? = null
        val joined = inFlightMutex.withLock {
            inFlight[key] ?: CompletableDeferred<Result<String>>().also {
                inFlight[key] = it
                leader = it
            }
        }
        val pending = leader
        if (pending == null) return joined.await()

        val result = try {
            runGenerationWithPrefix(prefix, suffix, profile)
        } catch (error: Throwable) {
            inFlightMutex.withLock { inFlight.remove(key) }
            pending.complete(Result.failure(error))
            throw error
        }
        inFlightMutex.withLock { inFlight.remove(key) }
        pending.complete(result)
        return result
    }

    private suspend fun runGenerationWithPrefix(
        prefix: String,
        suffix: String,
        profile: GenerationProfile
    ): Result<String> {
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
            val inference = runCatching {
                withTimeoutOrNull(INFERENCE_TIMEOUT_MS) { client.generateTextWithPrefix(prefix, suffix, profile) }
            }
            inference.exceptionOrNull()
                ?.takeIf { it is kotlinx.coroutines.CancellationException && it !is TimeoutCancellationException }
                ?.let { throw it }
            if (inference.isSuccess) {
                val text = inference.getOrThrow()
                if (text != null && text.isNotBlank()) {
                    responseCache.put(inFlightKey(profile, prefix + suffix), suffixNamespace(profile), suffix, text, nowMs())
                    return Result.success(text)
                }
                lastError = IllegalStateException("Gemini Nano returned an empty response.")
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            } else {
                lastError = inference.exceptionOrNull()
                if (!shouldRetry(lastError)) break
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Gemini Nano inference failed."))
    }

    override fun generateTextStream(prompt: String, profile: GenerationProfile): Flow<String> = flow {
        if (!foregroundGate.isAppInForeground()) return@flow
        if (ensureReadyForInference() != NanoModelStatus.AVAILABLE) return@flow
        emitAll(client.generateTextStream(prompt, profile))
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

        /**
         * Upper bound on a single Gemini Nano inference attempt. On-device generation normally
         * finishes in seconds; without this bound a hung AICore call would block the planning
         * coroutine indefinitely (and, before cancellation rethrow was added, even past caller
         * cancellation). Generous enough that slow-but-healthy devices never hit it.
         */
        const val INFERENCE_TIMEOUT_MS = 30_000L

        /** How long to suppress download re-attempts after one fails, so requests fail fast meanwhile. */
        const val DOWNLOAD_COOLDOWN_MS = 60_000L

        /** How long a steady-state AVAILABLE check is trusted before re-querying AICore. */
        const val STATUS_CACHE_TTL_MS = 5_000L

        /** How long a completed generation is replayable from the in-memory cache. */
        const val RESPONSE_CACHE_TTL_MS = 10L * 60 * 1000
        const val RESPONSE_CACHE_MAX_ENTRIES = 32
    }
}
