package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.GenerationProfile
import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import com.chronosflow.core.domain.model.ProactiveDigestKeys
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signals about the current day used to generate a proactive digest. Deliberately small and
 * primitive so it can be assembled cheaply from a day-overview snapshot without dragging heavy
 * domain types into [ProactiveAssistGenerator].
 */
data class ProactiveAssistInput(
    val dateIso: String,
    val plannedMinutes: Int,
    val completedBlocks: Int,
    val openTaskCount: Int,
    val dueMedicationCount: Int,
    val nextBlockTitle: String? = null,
    /** Today's focused (productive-app) screen-time minutes; 0 when screen time isn't tracked. */
    val focusedMinutes: Int = 0,
    /** The user's daily focus-minutes goal; 0 when unset or screen time isn't tracked. */
    val focusGoalMinutes: Int = 0,
    /** True when today's distracting-app time is notably above the user's usual level. */
    val distractionAboveUsual: Boolean = false
)

/** A pre-generated, cached piece of assistant copy plus when and what it was generated for. */
data class ProactiveAssistContent(
    val text: String,
    val source: AssistGenAiSource,
    val generatedAtEpochMs: Long,
    val forDateIso: String
)

/** Persists the most recently generated proactive content so background code can read it. */
interface ProactiveAssistStore {
    fun latest(): ProactiveAssistContent?

    fun save(content: ProactiveAssistContent)

    fun clear()
}

@Singleton
class PreferencesProactiveAssistStore @Inject constructor(
    private val preferences: ChronosPreferencesDataSource
) : ProactiveAssistStore {
    override fun latest(): ProactiveAssistContent? {
        val text = preferences.getString(ProactiveDigestKeys.KEY_TEXT, "").takeIf { it.isNotBlank() } ?: return null
        val generatedAt = preferences.getString(ProactiveDigestKeys.KEY_GENERATED_AT, "").toLongOrNull() ?: return null
        val source = runCatching {
            AssistGenAiSource.valueOf(preferences.getString(ProactiveDigestKeys.KEY_SOURCE, AssistGenAiSource.LOCAL.name))
        }.getOrDefault(AssistGenAiSource.LOCAL)
        val forDate = preferences.getString(ProactiveDigestKeys.KEY_FOR_DATE, "")
        return ProactiveAssistContent(text, source, generatedAt, forDate)
    }

    override fun save(content: ProactiveAssistContent) {
        preferences.putString(ProactiveDigestKeys.KEY_TEXT, content.text)
        preferences.putString(ProactiveDigestKeys.KEY_SOURCE, content.source.name)
        preferences.putString(ProactiveDigestKeys.KEY_GENERATED_AT, content.generatedAtEpochMs.toString())
        preferences.putString(ProactiveDigestKeys.KEY_FOR_DATE, content.forDateIso)
    }

    override fun clear() {
        preferences.remove(ProactiveDigestKeys.KEY_TEXT)
        preferences.remove(ProactiveDigestKeys.KEY_SOURCE)
        preferences.remove(ProactiveDigestKeys.KEY_GENERATED_AT)
        preferences.remove(ProactiveDigestKeys.KEY_FOR_DATE)
    }
}

/**
 * Pre-generates a short daily digest with Gemini Nano **while the app is foregrounded** (Nano
 * inference is foreground-only) and caches it through [ProactiveAssistStore]. Background surfaces —
 * notifications, the end-of-day reminder — then read [cachedCopy] without any live inference. When
 * AI is unavailable the cached copy is a deterministic local digest, so callers always have copy.
 */
@Singleton
class ProactiveAssistGenerator @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val store: ProactiveAssistStore
) {
    suspend fun refresh(input: ProactiveAssistInput, nowEpochMs: Long): ProactiveAssistContent {
        val local = localDigest(input)
        // The digest is a factual status line ("3 blocks done, 2 tasks open") — low temperature keeps
        // the model from inventing numbers, which a chattier profile is more prone to.
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(input),
            profile = GenerationProfile.FACTUAL
        )
        val cleaned = generation.text?.trim()?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        val content = if (cleaned.isNotBlank()) {
            ProactiveAssistContent(cleaned.take(MAX_DIGEST_LENGTH), generation.source, nowEpochMs, input.dateIso)
        } else {
            ProactiveAssistContent(local, AssistGenAiSource.LOCAL, nowEpochMs, input.dateIso)
        }
        store.save(content)
        return content
    }

    /** Cached content if it is for [dateIso] and still within [freshnessWindowMs]; null otherwise. */
    fun cachedCopy(
        dateIso: String,
        nowEpochMs: Long,
        freshnessWindowMs: Long = DEFAULT_FRESHNESS_WINDOW_MS
    ): ProactiveAssistContent? {
        val latest = store.latest() ?: return null
        if (latest.forDateIso != dateIso) return null
        if (nowEpochMs - latest.generatedAtEpochMs > freshnessWindowMs) return null
        return latest
    }

    private fun buildPrompt(input: ProactiveAssistInput): String = buildString {
        appendLine("Write one short, encouraging end-of-day status line for a focus-planning app.")
        appendLine("Use only these facts. No new tasks, no invented numbers. One sentence, under 140 characters.")
        appendLine("Date: ${input.dateIso}")
        appendLine("Planned minutes: ${input.plannedMinutes}")
        appendLine("Completed blocks: ${input.completedBlocks}")
        appendLine("Open tasks: ${input.openTaskCount}")
        appendLine("Medications due: ${input.dueMedicationCount}")
        appendLine("Next block: ${input.nextBlockTitle ?: "none"}")
        // Screen-time signals are only included when tracked, so the model never references focus
        // data for users who haven't enabled it. When present, the model may acknowledge focus
        // progress or, if distracted, gently suggest a focus block.
        if (input.focusGoalMinutes > 0 || input.focusedMinutes > 0 || input.distractionAboveUsual) {
            appendLine("Focused app minutes today: ${input.focusedMinutes}")
            if (input.focusGoalMinutes > 0) appendLine("Daily focus goal minutes: ${input.focusGoalMinutes}")
            if (input.distractionAboveUsual) {
                appendLine("Note: more time on distracting apps than usual today — a focus block may help.")
            }
        }
    }

    private fun localDigest(input: ProactiveAssistInput): String = when {
        input.dueMedicationCount > 0 ->
            "${input.dueMedicationCount} medication(s) still due today — a quick check keeps the streak."
        input.distractionAboveUsual ->
            "More time on distracting apps than usual today — a focus block could rebalance the day."
        input.openTaskCount > 0 ->
            "${input.completedBlocks} block(s) done; ${input.openTaskCount} task(s) still open for today."
        input.focusGoalMinutes > 0 && input.focusedMinutes >= input.focusGoalMinutes ->
            "You hit your focus goal today — nicely done."
        input.completedBlocks > 0 ->
            "Nice work — ${input.completedBlocks} block(s) completed today."
        input.nextBlockTitle != null ->
            "Next up: ${input.nextBlockTitle}."
        else ->
            "Plan is clear — a good moment to set up tomorrow."
    }

    private companion object {
        const val MAX_DIGEST_LENGTH = 160
        const val DEFAULT_FRESHNESS_WINDOW_MS = 12L * 60 * 60 * 1000
    }
}
