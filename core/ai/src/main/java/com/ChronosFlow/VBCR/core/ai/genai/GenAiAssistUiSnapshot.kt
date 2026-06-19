package com.ChronosFlow.VBCR.core.ai.genai

import com.ChronosFlow.VBCR.core.ai.PrivacyMode

data class GenAiAssistUiSnapshot(
    val bannerTitle: String,
    val bannerMessage: String,
    val privacyModeLabel: String,
    val aiDisabled: Boolean,
    val isReady: Boolean = false
)

fun GenAiAssistCoordinator.assistUiSnapshot(): GenAiAssistUiSnapshot {
    val mode = privacyMode()
    val status = runtimeStatus.value
    return GenAiAssistUiSnapshot(
        bannerTitle = GenAiAssistCopy.bannerTitle(mode, status),
        bannerMessage = GenAiAssistCopy.bannerMessage(mode, status),
        privacyModeLabel = GenAiAssistCopy.privacyModeLabel(mode),
        aiDisabled = mode == PrivacyMode.DISABLED,
        isReady = GenAiAssistCopy.isReady(mode, status)
    )
}

suspend fun GenAiAssistCoordinator.refreshAssistUiSnapshot(): GenAiAssistUiSnapshot {
    refreshRuntimeStatus()
    return assistUiSnapshot()
}
