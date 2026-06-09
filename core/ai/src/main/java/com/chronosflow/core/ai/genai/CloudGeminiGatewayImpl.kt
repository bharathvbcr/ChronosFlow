package com.chronosflow.core.ai.genai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CloudGeminiGatewayImpl @Inject constructor(
    private val config: CloudGeminiConfig,
    private val foregroundGate: AppForegroundGate
) : CloudGeminiGateway {
    override suspend fun generateText(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = config.apiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Cloud Gemini is not configured. Add GEMINI_API_KEY to local.properties and rebuild."
                )
            )
        }
        if (!foregroundGate.isAppInForeground()) {
            return@withContext Result.failure(
                IllegalStateException("Cloud Gemini requests require ChronosFlow to be in the foreground.")
            )
        }
        runCatching {
            val model = GenerativeModel(
                modelName = CloudGeminiModels.CLOUD_MODEL,
                apiKey = apiKey,
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
    }

}

object CloudGeminiModels {
    const val CLOUD_MODEL = "gemini-2.0-flash"
}
