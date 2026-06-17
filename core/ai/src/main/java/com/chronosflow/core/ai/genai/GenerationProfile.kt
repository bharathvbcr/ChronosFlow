package com.chronosflow.core.ai.genai

/**
 * Sampling profile for on-device Gemini Nano inference. Every prompt used to share one hardcoded
 * setting (temperature 0.35, topK 16), which is a poor fit for both ends of the workload: structured
 * JSON day-plans want near-zero randomness so the schema parses on the first try, while free-form
 * conversational replies read better with a little more diversity. Callers pick the profile that
 * matches the task instead of accepting one compromise for everything.
 */
enum class GenerationProfile(
    val temperature: Float,
    val topK: Int,
    /** Caps generated length so worst-case on-device latency/compute is bounded. */
    val maxOutputTokens: Int,
    /** Fixed seed pins sampling so identical prompts reproduce identical output; null lets it vary. */
    val seed: Int?
) {
    /**
     * Structured / JSON output — minimal randomness plus a fixed seed so the response parses
     * deterministically and an identical prompt yields an identical (cacheable) plan. The token cap
     * is generous enough for a full 3–6 block plan so it never truncates mid-JSON.
     */
    DETERMINISTIC(temperature = 0.05f, topK = 3, maxOutputTokens = 768, seed = 7),

    /** Default for short assistant copy; matches the previous hardcoded sampling. */
    BALANCED(temperature = 0.35f, topK = 16, maxOutputTokens = 256, seed = null),

    /**
     * Short, factual copy that must not invent numbers (e.g. the daily digest). Low temperature for
     * factual adherence, but no fixed seed so the wording still varies day to day, and a tight token
     * cap since the output is a single sentence.
     */
    FACTUAL(temperature = 0.2f, topK = 8, maxOutputTokens = 96, seed = null),

    /** Free-form conversational replies where a little variety reads more naturally. */
    CREATIVE(temperature = 0.7f, topK = 40, maxOutputTokens = 320, seed = null)
}
