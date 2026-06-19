package com.ChronosFlow.VBCR.core.ai.genai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MlKitGeminiNanoGatewayTest {

    @Test
    fun refreshStatus_exposesAvailableStatusMessage() = runTest {
        val gateway = gatewayWith(
            client = FakeNanoPromptClient(status = NanoModelStatus.AVAILABLE)
        )

        val status = gateway.refreshStatus()

        assertEquals(NanoModelStatus.AVAILABLE, status)
        assertEquals(NanoModelStatus.AVAILABLE, gateway.runtimeStatus.value.nanoStatus)
        assertEquals("Gemini Nano is ready for on-device planning.", gateway.runtimeStatus.value.statusMessage)
    }

    @Test
    fun refreshStatus_exposesDownloadingStatusMessage() = runTest {
        val gateway = gatewayWith(
            client = FakeNanoPromptClient(status = NanoModelStatus.DOWNLOADING)
        )

        val status = gateway.refreshStatus()

        assertEquals(NanoModelStatus.DOWNLOADING, status)
        assertEquals(NanoModelStatus.DOWNLOADING, gateway.runtimeStatus.value.nanoStatus)
        assertEquals("Gemini Nano is downloading through AICore.", gateway.runtimeStatus.value.statusMessage)
    }

    @Test
    fun ensureReadyForInference_downloadsWhenModelIsDownloadable() = runTest {
        val client = FakeNanoPromptClient(
            statuses = ArrayDeque(
                listOf(
                    NanoModelStatus.DOWNLOADABLE,
                    NanoModelStatus.AVAILABLE
                )
            ),
            downloadEvents = listOf(
                NanoDownloadEvent.Started,
                NanoDownloadEvent.Progress(256L),
                NanoDownloadEvent.Completed
            )
        )
        val gateway = gatewayWith(client = client)

        val status = gateway.ensureReadyForInference()

        assertEquals(NanoModelStatus.AVAILABLE, status)
        assertEquals(1, client.downloadCalls)
        assertEquals("Gemini Nano is ready for on-device planning.", gateway.runtimeStatus.value.statusMessage)
    }

    @Test
    fun ensureReadyForInference_returnsUnavailableWhenDownloadFails() = runTest {
        val client = FakeNanoPromptClient(
            statuses = ArrayDeque(
                listOf(
                    NanoModelStatus.DOWNLOADABLE,
                    NanoModelStatus.UNAVAILABLE
                )
            ),
            downloadEvents = listOf(
                NanoDownloadEvent.Started,
                NanoDownloadEvent.Failed(IllegalStateException("download failed"))
            )
        )
        val gateway = gatewayWith(client = client)

        val status = gateway.ensureReadyForInference()

        assertEquals(NanoModelStatus.UNAVAILABLE, status)
        assertEquals(1, client.downloadCalls)
        assertEquals(
            "Gemini Nano is unavailable on this device. ChronosFlow will use local heuristics.",
            gateway.runtimeStatus.value.statusMessage
        )
    }

    @Test
    fun generateText_rejectsBackgroundUse() = runTest {
        val gateway = gatewayWith(
            client = FakeNanoPromptClient(status = NanoModelStatus.AVAILABLE),
            foregroundGate = StaticForegroundGate(false)
        )

        val result = gateway.generateText("plan my day")

        assertTrue(result.isFailure)
        assertEquals(
            "Gemini Nano inference requires ChronosFlow to be in the foreground.",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun generateText_retriesBusyFailuresAndEventuallyReturnsText() = runTest {
        val client = FakeNanoPromptClient(
            status = NanoModelStatus.AVAILABLE,
            generatedResponses = ArrayDeque(
                listOf(
                    Result.failure(IllegalStateException("AICore busy, retry later")),
                    Result.failure(IllegalStateException("quota exceeded")),
                    Result.success("structured response")
                )
            )
        )
        val gateway = gatewayWith(client = client)

        val result = gateway.generateText("plan my day")

        assertTrue(result.isSuccess)
        assertEquals("structured response", result.getOrNull())
        assertEquals(3, client.generateCalls)
    }

    @Test
    fun generateText_reportsUnavailableStatusWithoutInference() = runTest {
        val client = FakeNanoPromptClient(status = NanoModelStatus.UNAVAILABLE)
        val gateway = gatewayWith(client = client)

        val result = gateway.generateText("plan my day")

        assertTrue(result.isFailure)
        assertEquals(
            "Gemini Nano is unavailable on this device. ChronosFlow will use local heuristics.",
            result.exceptionOrNull()?.message
        )
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun generateText_forwardsProfileToClient() = runTest {
        val client = FakeNanoPromptClient(status = NanoModelStatus.AVAILABLE)
        val gateway = gatewayWith(client = client)

        gateway.generateText("plan my day", GenerationProfile.DETERMINISTIC)

        assertEquals(GenerationProfile.DETERMINISTIC, client.lastProfile)
    }

    @Test
    fun generateText_replaysCachedResultWithoutRerunningInference() = runTest {
        val client = FakeNanoPromptClient(
            status = NanoModelStatus.AVAILABLE,
            generatedResponses = ArrayDeque(listOf(Result.success("cached plan")))
        )
        val gateway = gatewayWith(client = client).apply { nowMs = { 1_000L } }

        val first = gateway.generateText("plan my day")
        val second = gateway.generateText("plan my day")

        assertEquals("cached plan", first.getOrNull())
        assertEquals("cached plan", second.getOrNull())
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun generateText_coalescesConcurrentIdenticalRequests() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeNanoPromptClient(
            status = NanoModelStatus.AVAILABLE,
            generatedResponses = ArrayDeque(listOf(Result.success("shared plan")))
        ).apply { generateGate = gate }
        val gateway = gatewayWith(client = client).apply { nowMs = { 1_000L } }

        val first = async { gateway.generateText("plan my day") }
        val second = async { gateway.generateText("plan my day") }
        runCurrent() // leader parks at the inference gate; follower parks awaiting the shared result
        gate.complete(Unit)

        assertEquals("shared plan", first.await().getOrNull())
        assertEquals("shared plan", second.await().getOrNull())
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun generateText_servesCacheEvenWhenBackgrounded() = runTest {
        val client = FakeNanoPromptClient(status = NanoModelStatus.AVAILABLE)
        val foreground = StaticForegroundGate(true)
        val gateway = gatewayWith(client = client, foregroundGate = foreground).apply { nowMs = { 1_000L } }

        val warm = gateway.generateText("plan my day")
        foreground.inForeground = false
        val replay = gateway.generateText("plan my day")

        assertEquals("generated text", warm.getOrNull())
        assertTrue(replay.isSuccess)
        assertEquals("generated text", replay.getOrNull())
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun ensureReadyForInference_coolsDownAfterFailedDownloadThenRetriesPastCooldown() = runTest {
        val client = FakeNanoPromptClient(
            statuses = ArrayDeque(listOf(NanoModelStatus.DOWNLOADABLE)),
            downloadEvents = listOf(
                NanoDownloadEvent.Started,
                NanoDownloadEvent.Failed(IllegalStateException("offline"))
            )
        )
        var clock = 0L
        val gateway = gatewayWith(client = client).apply { nowMs = { clock } }

        gateway.ensureReadyForInference() // download attempt fails -> arms cooldown
        clock = 1_000L
        gateway.ensureReadyForInference() // inside cooldown -> no second doomed attempt

        assertEquals(1, client.downloadCalls)

        clock = 70_000L
        gateway.ensureReadyForInference() // past cooldown -> attempts again

        assertEquals(2, client.downloadCalls)
    }

    @Test
    fun generateTextWithPrefix_usesClientPrefixMethodAndAppliesCache() = runTest {
        val client = FakeNanoPromptClient(
            status = NanoModelStatus.AVAILABLE,
            generatedResponses = ArrayDeque(listOf(Result.success("prefix cached plan")))
        )
        val gateway = gatewayWith(client = client).apply { nowMs = { 1_000L } }

        val first = gateway.generateTextWithPrefix("system prefix", "user suffix")
        val second = gateway.generateTextWithPrefix("system prefix", "user suffix")

        assertEquals("prefix cached plan", first.getOrNull())
        assertEquals("prefix cached plan", second.getOrNull())
        assertEquals(1, client.generateWithPrefixCalls)
    }

    @Test
    fun generateTextWithPrefix_rejectsBackgroundUse() = runTest {
        val gateway = gatewayWith(
            client = FakeNanoPromptClient(status = NanoModelStatus.AVAILABLE),
            foregroundGate = StaticForegroundGate(false)
        )

        val result = gateway.generateTextWithPrefix("system prefix", "user suffix")

        assertTrue(result.isFailure)
        assertEquals(
            "Gemini Nano inference requires ChronosFlow to be in the foreground.",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun refreshStatus_cachesAvailableStatusWithinTtl() = runTest {
        val client = FakeNanoPromptClient(status = NanoModelStatus.AVAILABLE)
        var clock = 0L
        val gateway = gatewayWith(client = client).apply { nowMs = { clock } }

        gateway.refreshStatus()
        clock = 1_000L
        gateway.refreshStatus()

        assertEquals(1, client.statusChecks)

        clock = 10_000L
        gateway.refreshStatus()

        assertEquals(2, client.statusChecks)
    }

    private fun gatewayWith(
        client: FakeNanoPromptClient,
        foregroundGate: AppForegroundGate = StaticForegroundGate(true),
        cloudConfig: CloudGeminiConfig = StaticCloudGeminiConfig(isCloudConfigured = false)
    ): MlKitGeminiNanoGateway {
        return MlKitGeminiNanoGateway(
            foregroundGate = foregroundGate,
            cloudGeminiConfig = cloudConfig,
            client = client
        )
    }

    private class StaticForegroundGate(
        var inForeground: Boolean
    ) : AppForegroundGate {
        override fun isAppInForeground(): Boolean = inForeground
    }

    private class StaticCloudGeminiConfig(
        override val isCloudConfigured: Boolean
    ) : CloudGeminiConfig

    private class FakeNanoPromptClient(
        status: NanoModelStatus = NanoModelStatus.UNAVAILABLE,
        val statuses: ArrayDeque<NanoModelStatus> = ArrayDeque(listOf(status)),
        private val downloadEvents: List<NanoDownloadEvent> = emptyList(),
        val generatedResponses: ArrayDeque<Result<String>> = ArrayDeque(listOf(Result.success("generated text")))
    ) : NanoPromptClient {
        var downloadCalls: Int = 0
        var generateCalls: Int = 0
        var generateWithPrefixCalls: Int = 0
        var statusChecks: Int = 0
        var lastProfile: GenerationProfile? = null

        /** When set, [generateText] suspends on this gate so a test can overlap concurrent callers. */
        var generateGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun checkStatus(): NanoModelStatus {
            statusChecks++
            return if (statuses.size > 1) statuses.removeFirst() else statuses.first()
        }

        override fun download(): Flow<NanoDownloadEvent> = flow {
            downloadCalls++
            downloadEvents.forEach { emit(it) }
        }

        override fun selectionState(): NanoModelSelectionState = NanoModelSelectionState()

        override suspend fun generateText(prompt: String, profile: GenerationProfile): String {
            generateCalls++
            lastProfile = profile
            generateGate?.await()
            val result = if (generatedResponses.size > 1) generatedResponses.removeFirst() else generatedResponses.first()
            return result.getOrThrow()
        }

        override fun generateTextStream(prompt: String, profile: GenerationProfile): Flow<String> = flow {
            generateCalls++
            val result = if (generatedResponses.size > 1) generatedResponses.removeFirst() else generatedResponses.first()
            emit(result.getOrThrow())
        }

        override suspend fun generateTextWithPrefix(prefix: String, suffix: String, profile: GenerationProfile): String {
            generateWithPrefixCalls++
            lastProfile = profile
            val result = if (generatedResponses.size > 1) generatedResponses.removeFirst() else generatedResponses.first()
            return result.getOrThrow()
        }
    }
}
