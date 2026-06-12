package com.chronosflow.core.ai.genai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    private fun gatewayWith(
        client: FakeNanoPromptClient,
        foregroundGate: AppForegroundGate = StaticForegroundGate(true),
        cloudConfig: CloudGeminiConfig = StaticCloudGeminiConfig(apiKey = null)
    ): MlKitGeminiNanoGateway {
        return MlKitGeminiNanoGateway(
            foregroundGate = foregroundGate,
            cloudGeminiConfig = cloudConfig,
            client = client
        )
    }

    private class StaticForegroundGate(
        private val inForeground: Boolean
    ) : AppForegroundGate {
        override fun isAppInForeground(): Boolean = inForeground
    }

    private class StaticCloudGeminiConfig(
        override val apiKey: String?
    ) : CloudGeminiConfig

    private class FakeNanoPromptClient(
        status: NanoModelStatus = NanoModelStatus.UNAVAILABLE,
        val statuses: ArrayDeque<NanoModelStatus> = ArrayDeque(listOf(status)),
        private val downloadEvents: List<NanoDownloadEvent> = emptyList(),
        val generatedResponses: ArrayDeque<Result<String>> = ArrayDeque(listOf(Result.success("generated text")))
    ) : NanoPromptClient {
        var downloadCalls: Int = 0
        var generateCalls: Int = 0

        override suspend fun checkStatus(): NanoModelStatus {
            return if (statuses.size > 1) statuses.removeFirst() else statuses.first()
        }

        override fun download(): Flow<NanoDownloadEvent> = flow {
            downloadCalls++
            downloadEvents.forEach { emit(it) }
        }

        override fun selectionState(): NanoModelSelectionState = NanoModelSelectionState()

        override suspend fun generateText(prompt: String): String {
            generateCalls++
            val result = if (generatedResponses.size > 1) generatedResponses.removeFirst() else generatedResponses.first()
            return result.getOrThrow()
        }

        override fun generateTextStream(prompt: String): Flow<String> = flow {
            generateCalls++
            val result = if (generatedResponses.size > 1) generatedResponses.removeFirst() else generatedResponses.first()
            emit(result.getOrThrow())
        }
    }
}
