package com.chronosflow.di

import com.chronosflow.core.ai.genai.AppForegroundGate
import com.chronosflow.core.ai.genai.CloudGeminiConfig
import com.chronosflow.core.ai.genai.ProcessLifecycleAppForegroundGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppAiModule {
    @Binds
    @Singleton
    abstract fun bindCloudGeminiConfig(impl: BuildTimeCloudGeminiConfig): CloudGeminiConfig

    @Binds
    @Singleton
    abstract fun bindAppForegroundGate(impl: ProcessLifecycleAppForegroundGate): AppForegroundGate
}

@Singleton
class BuildTimeCloudGeminiConfig @Inject constructor() : CloudGeminiConfig {
    override val apiKey: String? =
        buildConfigGeminiApiKey().takeIf { it.isNotBlank() }

    private fun buildConfigGeminiApiKey(): String =
        runCatching {
            Class.forName("com.chronosflow.BuildConfig")
                .getField("GEMINI_API_KEY")
                .get(null) as? String
        }.getOrNull().orEmpty()
}
