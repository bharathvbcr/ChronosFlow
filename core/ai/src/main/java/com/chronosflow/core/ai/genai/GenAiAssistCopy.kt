package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.PrivacyMode

object GenAiAssistCopy {
    fun bannerTitle(mode: PrivacyMode, status: GenAiRuntimeStatus): String = when (mode) {
        PrivacyMode.DISABLED -> "AI assist disabled"
        PrivacyMode.CLOUD_ALLOWED ->
            if (status.cloudConfigured) "Cloud Gemini enabled" else "Cloud Gemini not configured"
        PrivacyMode.ON_DEVICE_ONLY -> when (status.nanoStatus) {
            NanoModelStatus.AVAILABLE ->
                if (status.previewModelActive) "Gemini Nano preview ready" else "Gemini Nano ready"
            NanoModelStatus.DOWNLOADING -> "Downloading Gemini Nano"
            NanoModelStatus.DOWNLOADABLE ->
                if (status.previewModelActive) {
                    "Gemini Nano preview available to download"
                } else {
                    "Gemini Nano available to download"
                }
            NanoModelStatus.UNAVAILABLE ->
                if (status.previewModelRequested) {
                    "Gemini Nano preview unavailable"
                } else {
                    "Gemini Nano unsupported on this device"
                }
        }
    }

    fun bannerMessage(mode: PrivacyMode, status: GenAiRuntimeStatus): String {
        val selection = when {
            status.previewModelActive ->
                "Selected on-device model: Gemini Nano ${status.selectedModelLabel}."
            status.previewModelFallback ->
                "Preview Gemini Nano was requested, but this device is using the stable Gemini Nano model (${status.selectedModelLabel})."
            status.previewModelRequested ->
                "Preview Gemini Nano was requested for this device."
            else ->
                "Selected on-device model: Gemini Nano ${status.selectedModelLabel}."
        }
        val base = listOf(selection, status.statusMessage.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return when (mode) {
            PrivacyMode.DISABLED ->
                "Assist uses local heuristics only. Enable on-device or cloud planning in Day Dial to use Gemini."
            PrivacyMode.CLOUD_ALLOWED ->
                if (status.cloudConfigured) {
                    "Suggestions use cloud Gemini first, then Gemini Nano through AICore when supported, then local heuristics. $base"
                } else {
                    "Add GEMINI_API_KEY to local.properties for cloud assist. Until then, ChronosFlow uses Gemini Nano when supported, otherwise local heuristics."
                }
            PrivacyMode.ON_DEVICE_ONLY ->
                "Suggestions use Gemini Nano through AICore when supported. Otherwise ChronosFlow uses local heuristics. $base"
        }.trim()
    }

    fun privacyModeLabel(mode: PrivacyMode): String = when (mode) {
        PrivacyMode.ON_DEVICE_ONLY -> "Gemini Nano (on-device)"
        PrivacyMode.CLOUD_ALLOWED -> "Cloud Gemini"
        PrivacyMode.DISABLED -> "Disabled"
    }

    fun assistSourceLabel(source: AssistGenAiSource): String = when (source) {
        AssistGenAiSource.GEMINI_NANO -> "Gemini Nano"
        AssistGenAiSource.CLOUD_GEMINI -> "Cloud Gemini"
        AssistGenAiSource.LOCAL -> "Local"
    }

    fun taskAssistSourceLabel(source: com.chronosflow.core.ai.TaskAssistSource): String = when (source) {
        com.chronosflow.core.ai.TaskAssistSource.GEMINI_NANO -> "Gemini Nano"
        com.chronosflow.core.ai.TaskAssistSource.CLOUD_GEMINI -> "Cloud Gemini"
        com.chronosflow.core.ai.TaskAssistSource.LOCAL -> "Local"
    }

    fun routineAssistSourceLabel(source: com.chronosflow.core.ai.RoutineAssistSource): String = when (source) {
        com.chronosflow.core.ai.RoutineAssistSource.GEMINI_NANO -> "Gemini Nano"
        com.chronosflow.core.ai.RoutineAssistSource.CLOUD_GEMINI -> "Cloud Gemini"
        com.chronosflow.core.ai.RoutineAssistSource.LOCAL -> "Local"
    }

    fun focusAssistSourceLabel(source: com.chronosflow.core.ai.FocusAssistSource): String = when (source) {
        com.chronosflow.core.ai.FocusAssistSource.GEMINI_NANO -> "Gemini Nano"
        com.chronosflow.core.ai.FocusAssistSource.CLOUD_GEMINI -> "Cloud Gemini"
        com.chronosflow.core.ai.FocusAssistSource.LOCAL -> "Local"
    }

    fun disabledAssistMessage(): String =
        "AI assist is off. Suggestions below use local heuristics only."
}
