package com.ChronosFlow.VBCR.core.ai.genai

import com.ChronosFlow.VBCR.core.ai.RoutineAssistSource
import com.ChronosFlow.VBCR.core.ai.TaskAssistSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistGenAiSourceMappingTest {

    @Test
    fun `maps to task assist source`() {
        assertEquals(TaskAssistSource.GEMINI_NANO, AssistGenAiSource.GEMINI_NANO.toTaskAssistSource())
        assertEquals(TaskAssistSource.CLOUD_GEMINI, AssistGenAiSource.CLOUD_GEMINI.toTaskAssistSource())
        assertEquals(TaskAssistSource.LOCAL, AssistGenAiSource.LOCAL.toTaskAssistSource())
    }

    @Test
    fun `maps to routine assist source`() {
        assertEquals(RoutineAssistSource.GEMINI_NANO, AssistGenAiSource.GEMINI_NANO.toRoutineAssistSource())
        assertEquals(RoutineAssistSource.CLOUD_GEMINI, AssistGenAiSource.CLOUD_GEMINI.toRoutineAssistSource())
        assertEquals(RoutineAssistSource.LOCAL, AssistGenAiSource.LOCAL.toRoutineAssistSource())
    }
}
