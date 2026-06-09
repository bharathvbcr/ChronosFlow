package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.RoutineAssistSource
import com.chronosflow.core.ai.TaskAssistSource

fun AssistGenAiSource.toTaskAssistSource(): TaskAssistSource = when (this) {
    AssistGenAiSource.GEMINI_NANO -> TaskAssistSource.GEMINI_NANO
    AssistGenAiSource.CLOUD_GEMINI -> TaskAssistSource.CLOUD_GEMINI
    AssistGenAiSource.LOCAL -> TaskAssistSource.LOCAL
}

fun AssistGenAiSource.toRoutineAssistSource(): RoutineAssistSource = when (this) {
    AssistGenAiSource.GEMINI_NANO -> RoutineAssistSource.GEMINI_NANO
    AssistGenAiSource.CLOUD_GEMINI -> RoutineAssistSource.CLOUD_GEMINI
    AssistGenAiSource.LOCAL -> RoutineAssistSource.LOCAL
}
