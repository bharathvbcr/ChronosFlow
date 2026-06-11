package com.chronosflow.core.ai.di

import com.chronosflow.core.ai.genai.CloudGeminiGateway
import com.chronosflow.core.ai.genai.CloudGeminiGatewayImpl
import com.chronosflow.core.ai.genai.MlKitNanoPromptClient
import com.chronosflow.core.ai.genai.MlKitGeminiNanoGateway
import com.chronosflow.core.ai.genai.MlKitNanoModelClientFactory
import com.chronosflow.core.ai.genai.MlKitTextToolsClient
import com.chronosflow.core.ai.genai.MlKitTextToolsGateway
import com.chronosflow.core.ai.genai.NanoModelClientFactory
import com.chronosflow.core.ai.genai.NanoPromptClient
import com.chronosflow.core.ai.genai.OnDeviceGeminiGateway
import com.chronosflow.core.ai.genai.OnDeviceTextToolsGateway
import com.chronosflow.core.ai.genai.TextToolsClient
import com.chronosflow.core.ai.PreferencesProactiveAssistStore
import com.chronosflow.core.ai.ProactiveAssistStore
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
    abstract fun bindTextToolsClient(impl: MlKitTextToolsClient): TextToolsClient

    @Binds
    @Singleton
    abstract fun bindTextToolsGateway(impl: MlKitTextToolsGateway): OnDeviceTextToolsGateway

    @Binds
    @Singleton
    abstract fun bindProactiveAssistStore(impl: PreferencesProactiveAssistStore): ProactiveAssistStore
}
