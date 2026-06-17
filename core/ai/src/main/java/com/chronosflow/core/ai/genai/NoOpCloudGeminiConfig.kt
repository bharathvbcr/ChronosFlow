package com.chronosflow.core.ai.genai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpCloudGeminiConfig @Inject constructor() : CloudGeminiConfig {
    override val isCloudConfigured: Boolean = false
}
