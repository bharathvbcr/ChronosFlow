package com.ChronosFlow.VBCR.core.ai.di

import com.ChronosFlow.VBCR.core.ai.genai.CloudGeminiConfig
import com.ChronosFlow.VBCR.core.ai.genai.CloudGeminiGateway
import com.ChronosFlow.VBCR.core.ai.genai.CloudGeminiGatewayImpl
import com.ChronosFlow.VBCR.core.ai.genai.FirebaseCloudGeminiConfig
import com.ChronosFlow.VBCR.core.ai.genai.MlKitNanoPromptClient
import com.ChronosFlow.VBCR.core.ai.genai.MlKitGeminiNanoGateway
import com.ChronosFlow.VBCR.core.ai.genai.MlKitNanoModelClientFactory
import com.ChronosFlow.VBCR.core.ai.genai.MlKitTextToolsClient
import com.ChronosFlow.VBCR.core.ai.genai.MlKitTextToolsGateway
import com.ChronosFlow.VBCR.core.ai.genai.NanoModelClientFactory
import com.ChronosFlow.VBCR.core.ai.genai.NanoPromptClient
import com.ChronosFlow.VBCR.core.ai.genai.OnDeviceGeminiGateway
import com.ChronosFlow.VBCR.core.ai.genai.OnDeviceTextToolsGateway
import com.ChronosFlow.VBCR.core.ai.genai.TextToolsClient
import com.ChronosFlow.VBCR.core.ai.PreferencesProactiveAssistStore
import com.ChronosFlow.VBCR.core.ai.ProactiveAssistStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindNanoPromptClient(impl: MlKitNanoPromptClient): NanoPromptClient

    @Binds
    @Singleton
    abstract fun bindNanoModelClientFactory(impl: MlKitNanoModelClientFactory): NanoModelClientFactory

    @Binds
    @Singleton
    abstract fun bindOnDeviceGateway(impl: MlKitGeminiNanoGateway): OnDeviceGeminiGateway

    @Binds
    @Singleton
    abstract fun bindCloudGateway(impl: CloudGeminiGatewayImpl): CloudGeminiGateway

    @Binds
    @Singleton
    abstract fun bindCloudGeminiConfig(impl: FirebaseCloudGeminiConfig): CloudGeminiConfig

    @Binds
    @Singleton
    abstract fun bindTextToolsClient(impl: MlKitTextToolsClient): TextToolsClient

    @Binds
    @Singleton
    abstract fun bindTextToolsGateway(impl: MlKitTextToolsGateway): OnDeviceTextToolsGateway

    @Binds
    @Singleton
    abstract fun bindProactiveAssistStore(impl: PreferencesProactiveAssistStore): ProactiveAssistStore
}
