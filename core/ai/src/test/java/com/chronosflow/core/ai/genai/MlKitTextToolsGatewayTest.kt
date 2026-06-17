package com.chronosflow.core.ai.genai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitTextToolsGatewayTest {

    @Test
    fun proofread_replaysCachedResultWithoutRerunningInference() = runTest {
        val client = FakeTextToolsClient(response = "cleaned up")
        val gateway = gatewayWith(client)

        val first = gateway.proofread("draft text")
        val second = gateway.proofread("draft text")

        assertEquals("cleaned up", first.getOrNull())
        assertEquals("cleaned up", second.getOrNull())
        assertEquals(1, client.proofreadCalls)
    }

    @Test
    fun rewrite_distinctStylesDoNotShareACacheEntry() = runTest {
        val client = FakeTextToolsClient(response = "rewritten")
        val gateway = gatewayWith(client)

        gateway.rewrite("draft", RewriteStyle.SHORTEN)
        gateway.rewrite("draft", RewriteStyle.ELABORATE)
        gateway.rewrite("draft", RewriteStyle.SHORTEN) // same as first -> cache hit

        assertEquals(2, client.rewriteCalls)
    }

    @Test
    fun summarize_servesCacheEvenWhenBackgrounded() = runTest {
        val client = FakeTextToolsClient(response = "summary")
        val foreground = StaticForegroundGate(true)
        val gateway = MlKitTextToolsGateway(foreground, client).apply { nowMs = { 1_000L } }

        val warm = gateway.summarize("a long article body", SummaryStyle.ONE_BULLET)
        foreground.inForeground = false
        val replay = gateway.summarize("a long article body", SummaryStyle.ONE_BULLET)

        assertEquals("summary", warm.getOrNull())
        assertTrue(replay.isSuccess)
        assertEquals("summary", replay.getOrNull())
        assertEquals(1, client.summarizeCalls)
    }

    private fun gatewayWith(client: FakeTextToolsClient): MlKitTextToolsGateway =
        MlKitTextToolsGateway(StaticForegroundGate(true), client).apply { nowMs = { 1_000L } }

    private class StaticForegroundGate(var inForeground: Boolean) : AppForegroundGate {
        override fun isAppInForeground(): Boolean = inForeground
    }

    private class FakeTextToolsClient(private val response: String) : TextToolsClient {
        var summarizeCalls = 0
        var proofreadCalls = 0
        var rewriteCalls = 0

        override suspend fun summarize(text: String, style: SummaryStyle): String {
            summarizeCalls++
            return response
        }

        override suspend fun proofread(text: String): String {
            proofreadCalls++
            return response
        }

        override suspend fun rewrite(text: String, style: RewriteStyle): String {
            rewriteCalls++
            return response
        }
    }
}
