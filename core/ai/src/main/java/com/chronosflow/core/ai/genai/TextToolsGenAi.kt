package com.chronosflow.core.ai.genai

/** Output shapes for the ML Kit GenAI Summarization feature. */
enum class SummaryStyle {
    ONE_BULLET,
    THREE_BULLETS
}

/** Tones supported by the ML Kit GenAI Rewriting feature. */
enum class RewriteStyle {
    REPHRASE,
    SHORTEN,
    ELABORATE,
    FRIENDLY,
    PROFESSIONAL,
    EMOJIFY
}

/**
 * UI state for one in-flight or completed on-device rewrite of a free-text form field,
 * shared by the Task description and Medication notes assist surfaces. The rewritten text
 * is a preview the form must explicitly apply — fields are never replaced automatically.
 */
data class RewriteAssistUiState(
    val isLoading: Boolean = false,
    val styleLabel: String? = null,
    val original: String? = null,
    val rewritten: String? = null,
    val message: String? = null
)

/**
 * On-device text utilities backed by the purpose-built ML Kit GenAI feature APIs
 * (Summarization, Proofreading, Rewriting) running on top of Gemini Nano / AICore.
 *
 * These are on-device only — there is no cloud equivalent wired here — so callers should
 * treat a failed [Result] as "fall back to the original text" rather than an error to surface.
 */
interface OnDeviceTextToolsGateway {
    suspend fun summarize(text: String, style: SummaryStyle): Result<String>

    suspend fun proofread(text: String): Result<String>

    suspend fun rewrite(text: String, style: RewriteStyle): Result<String>
}
