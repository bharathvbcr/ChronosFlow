package com.chronosflow.core.ai.genai

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.guava.await

/**
 * Thin coroutine wrapper over the three ML Kit GenAI feature clients. Each call builds a
 * task-scoped client, runs a single inference, and releases the native resource. Kept behind
 * an interface so the gateway can be unit-tested with a fake.
 */
interface TextToolsClient {
    suspend fun summarize(text: String, style: SummaryStyle): String

    suspend fun proofread(text: String): String

    suspend fun rewrite(text: String, style: RewriteStyle): String
}

@Singleton
class MlKitTextToolsClient @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToolsClient {
    override suspend fun summarize(text: String, style: SummaryStyle): String {
        val options = SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(
                when (style) {
                    SummaryStyle.ONE_BULLET -> SummarizerOptions.OutputType.ONE_BULLET
                    SummaryStyle.THREE_BULLETS -> SummarizerOptions.OutputType.THREE_BULLETS
                }
            )
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()
        val summarizer = Summarization.getClient(options)
        return try {
            requireAvailable(summarizer.checkFeatureStatus().await())
            summarizer.runInference(SummarizationRequest.builder(text).build())
                .await()
                .summary
                .trim()
        } finally {
            summarizer.close()
        }
    }

    override suspend fun proofread(text: String): String {
        val options = ProofreaderOptions.builder(context)
            .setInputType(ProofreaderOptions.InputType.KEYBOARD)
            .setLanguage(ProofreaderOptions.Language.ENGLISH)
            .build()
        val proofreader = Proofreading.getClient(options)
        return try {
            requireAvailable(proofreader.checkFeatureStatus().await())
            proofreader.runInference(ProofreadingRequest.builder(text).build())
                .await()
                .results
                .firstOrNull()
                ?.text
                .orEmpty()
                .trim()
        } finally {
            proofreader.close()
        }
    }

    override suspend fun rewrite(text: String, style: RewriteStyle): String {
        val options = RewriterOptions.builder(context)
            .setOutputType(
                when (style) {
                    RewriteStyle.REPHRASE -> RewriterOptions.OutputType.REPHRASE
                    RewriteStyle.SHORTEN -> RewriterOptions.OutputType.SHORTEN
                    RewriteStyle.ELABORATE -> RewriterOptions.OutputType.ELABORATE
                    RewriteStyle.FRIENDLY -> RewriterOptions.OutputType.FRIENDLY
                    RewriteStyle.PROFESSIONAL -> RewriterOptions.OutputType.PROFESSIONAL
                    RewriteStyle.EMOJIFY -> RewriterOptions.OutputType.EMOJIFY
                }
            )
            .setLanguage(RewriterOptions.Language.ENGLISH)
            .build()
        val rewriter = Rewriting.getClient(options)
        return try {
            requireAvailable(rewriter.checkFeatureStatus().await())
            rewriter.runInference(RewritingRequest.builder(text).build())
                .await()
                .results
                .firstOrNull()
                ?.text
                .orEmpty()
                .trim()
        } finally {
            rewriter.close()
        }
    }

    private fun requireAvailable(status: Int) {
        if (status == FeatureStatus.UNAVAILABLE) {
            throw IllegalStateException("This on-device text feature is unavailable on this device.")
        }
    }
}

@Singleton
class MlKitTextToolsGateway @Inject constructor(
    private val foregroundGate: AppForegroundGate,
    private val client: TextToolsClient
) : OnDeviceTextToolsGateway {
    override suspend fun summarize(text: String, style: SummaryStyle): Result<String> =
        guarded { client.summarize(text, style) }

    override suspend fun proofread(text: String): Result<String> =
        guarded { client.proofread(text) }

    override suspend fun rewrite(text: String, style: RewriteStyle): Result<String> =
        guarded { client.rewrite(text, style) }

    private suspend fun guarded(block: suspend () -> String): Result<String> {
        if (!foregroundGate.isAppInForeground()) {
            return Result.failure(
                IllegalStateException("On-device text tools require ChronosFlow to be in the foreground.")
            )
        }
        return runCatching {
            val result = block()
            if (result.isBlank()) {
                throw IllegalStateException("On-device text tool returned an empty response.")
            }
            result
        }
    }
}
