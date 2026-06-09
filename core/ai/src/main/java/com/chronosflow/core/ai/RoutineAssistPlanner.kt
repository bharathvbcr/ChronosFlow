package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.toRoutineAssistSource
import java.util.Locale
import javax.inject.Inject

data class HabitAssistRequest(
    val title: String,
    val cadence: String,
    val startMinute: Int,
    val endMinute: Int,
    val difficulty: Int,
    val isBundled: Boolean
)

sealed interface HabitAssistSuggestion {
    val id: String
    val label: String
    val reason: String
    val source: RoutineAssistSource

    data class Title(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val title: String
    ) : HabitAssistSuggestion

    data class Recurrence(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val cadence: String
    ) : HabitAssistSuggestion

    data class Window(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val startMinute: Int,
        val endMinute: Int
    ) : HabitAssistSuggestion

    data class Difficulty(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val difficulty: Int
    ) : HabitAssistSuggestion

    data class DayPlan(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val isBundled: Boolean
    ) : HabitAssistSuggestion
}

data class MedicationAssistRequest(
    val name: String,
    val dosage: String,
    val unit: String,
    val frequency: String,
    val primaryReminderMinute: Int,
    val secondaryReminderMinute: Int?,
    val mealTiming: String,
    val hasRefillTracking: Boolean,
    val notes: String,
    val form: String,
    val route: String
)

sealed interface MedicationAssistSuggestion {
    val id: String
    val label: String
    val reason: String
    val source: RoutineAssistSource

    data class Reminder(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val primaryMinute: Int,
        val secondaryMinute: Int? = null,
        val frequency: String? = null
    ) : MedicationAssistSuggestion

    data class MealTiming(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val mealTiming: String
    ) : MedicationAssistSuggestion

    data class RefillTracking(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val dosesLeft: Int
    ) : MedicationAssistSuggestion

    data class Notes(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val notes: String
    ) : MedicationAssistSuggestion

    data class FormRoute(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val form: String,
        val route: String
    ) : MedicationAssistSuggestion

    data class Details(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: RoutineAssistSource,
        val name: String? = null,
        val dosage: String? = null,
        val unit: String? = null,
        val frequency: String? = null
    ) : MedicationAssistSuggestion
}

enum class RoutineAssistSource {
    GEMINI_NANO,
    CLOUD_GEMINI,
    LOCAL
}

class HabitAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggest(request: HabitAssistRequest): List<HabitAssistSuggestion> {
        val generation = genAiAssistCoordinator.generateAssistText(buildPrompt(request))
        generation.text?.let { raw ->
            val parsed = parseAssistSuggestions(raw, generation.source.toRoutineAssistSource())
            if (parsed.isNotEmpty()) return parsed
        }
        return localSuggestions(request)
    }

    private fun buildPrompt(request: HabitAssistRequest): String = buildString {
        appendLine("Parse this typed or dictated capture into manual habit setup suggestions for ChronosFlow.")
        appendLine("Return one suggestion per line as kind|label|value|reason.")
        appendLine("Kinds: title, recurrence, window, difficulty, day_plan.")
        appendLine("Window value is startMinute,endMinute. Day plan value is true or false.")
        appendLine("Extract concise habit names, recurrence, completion windows, and planner visibility from user-provided text only.")
        appendLine("Return complementary suggestions together when supported by the capture, because the user applies them one at a time.")
        appendLine("For dictated fragments, keep the habit name separate from cadence and time words.")
        appendLine("Do not say to apply automatically.")
        appendLine("Examples:")
        appendLine("Input: gym 3x week evening")
        appendLine("title|Gym|Gym|Remove cadence and window words from the title.")
        appendLine("recurrence|3x / week|3x / week|The capture names three sessions per week.")
        appendLine("window|Evening window|1080,1320|The capture says evening.")
        appendLine("difficulty|Hard|4|Gym is a higher effort habit.")
        appendLine("day_plan|Show on day plan|true|Time-windowed workouts should be visible beside scheduled blocks.")
        appendLine("Input: strength training Tue/Thu at 6pm")
        appendLine("title|Strength training|Strength training|Remove cadence and time words from the title.")
        appendLine("recurrence|Tue/Thu|Tue/Thu|The capture names Tuesday and Thursday.")
        appendLine("window|6:00 PM window|1080,1200|The capture names 6pm.")
        appendLine("difficulty|Hard|4|Strength training is a higher effort habit.")
        appendLine("Input: run 3x week morning")
        appendLine("title|Run|Run|Remove cadence and timing words from the habit name.")
        appendLine("recurrence|3x / week|3x / week|Carry the stated running cadence.")
        appendLine("window|Morning window|360,600|Morning wording sets the completion window.")
        appendLine("difficulty|Hard|4|Running is a higher-effort habit.")
        appendLine("day_plan|Show in Day plan|true|Scheduled exercise should be visible in the Day plan.")
        appendLine("Input: run with Strava every morning")
        appendLine("title|Run|Run|Keep the habit name separate from the tracking app.")
        appendLine("recurrence|Daily|Daily|Every morning means a daily habit.")
        appendLine("window|Morning window|360,600|Morning wording sets the completion window.")
        appendLine("difficulty|Hard|4|Running is a higher-effort habit.")
        appendLine("day_plan|Show in Day plan|true|App-assisted workouts should be visible beside scheduled blocks.")
        appendLine("Input: log calories in MyFitnessPal daily")
        appendLine("title|Log calories|Log calories|Keep the habit name separate from the tracking app.")
        appendLine("recurrence|Daily|Daily|Daily wording sets the cadence.")
        appendLine("window|All-day window|480,1320|Food logging usually spans the day.")
        appendLine("difficulty|Easy|2|Nutrition tracking should stay lightweight.")
        appendLine("day_plan|Keep flexible|false|All-day tracking should not force a scheduled block.")
        appendLine("Input: yoga nightly")
        appendLine("title|Yoga|Yoga|Remove cadence words from the habit name.")
        appendLine("recurrence|Daily|Daily|Nightly maps to a daily cadence.")
        appendLine("window|Evening window|1080,1320|Nightly wording sets an evening completion window.")
        appendLine("difficulty|Easy|2|Mobility habits should stay lightweight by default.")
        appendLine("Input: floss nightly")
        appendLine("title|Floss|Floss|Remove cadence words from the habit name.")
        appendLine("recurrence|Daily|Daily|Nightly maps to a daily cadence.")
        appendLine("window|Night window|1260,1320|Dental habits usually fit near bedtime.")
        appendLine("difficulty|Easy|2|Dental-care habits should be lightweight by default.")
        appendLine("day_plan|Show on day plan|true|A bedtime dental routine should be visible before the day closes.")
        appendLine("Input: limit caffeine after noon")
        appendLine("title|Limit caffeine|Limit caffeine|Keep the nutrition guardrail as the habit title.")
        appendLine("recurrence|Daily|Daily|Caffeine limits are daily behavior guardrails.")
        appendLine("window|Midday guardrail|660,840|After-noon wording points to the lunch boundary.")
        appendLine("difficulty|Easy|2|Nutrition guardrails should stay lightweight.")
        appendLine("day_plan|Keep flexible|false|This is a flexible all-day behavior rather than a scheduled block.")
        appendLine("Input: eat vegetables daily with dinner")
        appendLine("title|Eat vegetables|Eat vegetables|Remove cadence and meal-window words from the habit name.")
        appendLine("recurrence|Daily|Daily|Daily wording sets the cadence.")
        appendLine("window|Evening window|1080,1320|Dinner wording sets an evening completion window.")
        appendLine("difficulty|Easy|2|Nutrition habits should be lightweight by default.")
        appendLine("Input: reduce caffeine after 2pm daily")
        appendLine("title|Reduce caffeine|Reduce caffeine|Remove cadence and cutoff-window words from the habit name.")
        appendLine("recurrence|Daily|Daily|Daily wording sets the cadence.")
        appendLine("window|Afternoon cutoff|840,1320|After-2pm wording sets the relevant tracking window.")
        appendLine("difficulty|Easy|2|Reduction habits should stay lightweight by default.")
        appendLine("Input: wind down screen-free nightly")
        appendLine("title|Wind down screen-free|Wind down screen-free|Remove cadence words from the habit name.")
        appendLine("recurrence|Daily|Daily|Nightly maps to a daily cadence.")
        appendLine("window|Night window|1260,1320|Nightly wind-down habits belong near bedtime.")
        appendLine("difficulty|Easy|2|Sleep-routine habits should be lightweight by default.")
        appendLine("Input: study Spanish daily morning")
        appendLine("title|Study Spanish|Study Spanish|Remove cadence and timing words from the habit name.")
        appendLine("recurrence|Daily|Daily|Daily wording sets the cadence.")
        appendLine("window|Morning window|360,600|Morning wording sets a study window.")
        appendLine("difficulty|Moderate effort|3|Learning habits usually need focused effort.")
        appendLine("Input: hydrate all day")
        appendLine("title|Hydrate|Hydrate|Use a concise habit title.")
        appendLine("recurrence|Daily|Daily|Hydration is normally tracked daily when no other cadence is stated.")
        appendLine("window|All-day window|480,1320|The capture says all day.")
        appendLine("day_plan|Keep flexible|false|All-day habits do not need a fixed Day plan block.")
        appendLine("Input: drink 8 cups water daily")
        appendLine("title|Drink 8 cups water|Drink 8 cups water|Keep the measurable hydration target in the title.")
        appendLine("recurrence|Daily|Daily|Daily is stated by the capture.")
        appendLine("window|All-day window|480,1320|Measured hydration usually spans the day.")
        appendLine("difficulty|Easy|2|Hydration is a lightweight habit.")
        appendLine("day_plan|Keep flexible|false|All-day measured hydration should stay flexible.")
        appendLine("Input: drink 8 glasses of water")
        appendLine("title|Drink 8 glasses of water|Drink 8 glasses of water|Keep the measurable hydration target in the title.")
        appendLine("recurrence|Daily|Daily|Measured hydration targets are normally tracked daily.")
        appendLine("window|All-day window|480,1320|Measured hydration usually spans the day.")
        appendLine("difficulty|Easy|2|Hydration is a lightweight habit.")
        appendLine("day_plan|Keep flexible|false|All-day measured hydration should stay flexible.")
        appendLine("Input: meditate 10 minutes every morning")
        appendLine("title|Meditate 10 minutes|Meditate 10 minutes|Keep the measurable mindfulness target and remove cadence words.")
        appendLine("recurrence|Daily|Daily|Every morning means a daily habit.")
        appendLine("window|Morning window|360,600|The capture names morning.")
        appendLine("difficulty|Easy|2|Meditation is usually a lightweight starter habit.")
        appendLine("day_plan|Show on day plan|true|A time-windowed mindfulness habit is easier to start from the day plan.")
            appendLine("Input: meditate twice a day")
            appendLine("title|Meditate|Meditate|Remove daily-frequency wording from the habit name.")
            appendLine("recurrence|Daily|Daily|Twice a day is a daily habit cadence.")
            appendLine("window|Flexible day window|480,1320|No exact time is stated, so keep the completion window broad.")
            appendLine("difficulty|Easy|2|Meditation is usually a lightweight starter habit.")
            appendLine("day_plan|Keep flexible|false|A broad daily-frequency habit does not need a fixed Day plan block.")
            appendLine("Input: breathing exercise nightly")
            appendLine("title|Breathing exercise|Breathing exercise|Remove cadence words from the habit name.")
            appendLine("recurrence|Nightly|Daily|Nightly means a daily habit with a night window.")
            appendLine("window|Night window|1260,1320|Night breathing should be easy to find near bedtime.")
            appendLine("difficulty|Easy|2|Breathing is a lightweight starter habit.")
            appendLine("day_plan|Show on day plan|true|Time-windowed habits are easier to act on from the day plan.")
            appendLine("Input: wind down for sleep every night")
            appendLine("title|Wind down for sleep|Wind down for sleep|Keep the sleep-routine intent in the habit title.")
            appendLine("recurrence|Daily|Daily|Every night means a daily sleep routine.")
            appendLine("window|Night window|1260,1320|Sleep wind-down should live near bedtime.")
            appendLine("difficulty|Easy|2|A sleep routine should be easy enough to repeat.")
            appendLine("day_plan|Show on day plan|true|A sleep routine benefits from visible evening planning.")
            appendLine("Input: read 20 pages every night")
            appendLine("title|Read 20 pages|Read 20 pages|Keep the measurable habit target and remove cadence words.")
            appendLine("recurrence|Daily|Daily|Every night means a daily habit.")
            appendLine("window|Night window|1260,1320|The capture names night.")
            appendLine("difficulty|Easy|2|Reading is usually a lightweight habit.")
            appendLine("Title: ${request.title}")
        appendLine("Cadence: ${request.cadence}")
        appendLine("Window: ${request.startMinute},${request.endMinute}")
        appendLine("Difficulty: ${request.difficulty}")
        appendLine("Day plan: ${request.isBundled}")
    }

    private fun parseAssistSuggestions(text: String, source: RoutineAssistSource): List<HabitAssistSuggestion> {
        return text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() && it.count { char -> char == '|' } >= 3 }
            .mapIndexedNotNull { index, line ->
                val parts = line.split("|").map(String::trim)
                val kind = parts.getOrNull(0)?.lowercase(Locale.getDefault()) ?: return@mapIndexedNotNull null
                val label = parts.getOrNull(1).orEmpty()
                val value = parts.getOrNull(2).orEmpty()
                val reason = parts.drop(3).joinToString("|").ifBlank { defaultRoutineReasonFor(source) }
                habitSuggestionFromParts(index, kind, label, value, reason, source)
            }
            .take(MAX_SUGGESTIONS)
            .toList()
    }

    private fun habitSuggestionFromParts(
        index: Int,
        kind: String,
        label: String,
        value: String,
        reason: String,
        source: RoutineAssistSource
    ): HabitAssistSuggestion? {
        val id = "${source.name.lowercase(Locale.getDefault())}:habit:$kind:$index"
        return when (kind) {
            "title" -> value.takeIf(String::isNotBlank)?.let {
                HabitAssistSuggestion.Title(id, label.ifBlank { it }, reason, source, it)
            }
            "recurrence" -> value.takeIf(String::isNotBlank)?.let {
                HabitAssistSuggestion.Recurrence(id, label.ifBlank { it }, reason, source, it)
            }
            "window" -> parseMinutePair(value)?.let { (start, end) ->
                HabitAssistSuggestion.Window(
                    id = id,
                    label = label.ifBlank { "Use ${formatMinuteLabel(start)} window" },
                    reason = reason,
                    source = source,
                    startMinute = start,
                    endMinute = end
                )
            }
            "difficulty" -> value.toIntOrNull()?.coerceIn(1, 5)?.let {
                HabitAssistSuggestion.Difficulty(id, label.ifBlank { "Set effort" }, reason, source, it)
            }
            "day_plan" -> value.toBooleanStrictOrNull()?.let {
                HabitAssistSuggestion.DayPlan(
                    id = id,
                    label = label.ifBlank { if (it) "Show on day plan" else "Keep flexible" },
                    reason = reason,
                    source = source,
                    isBundled = it
                )
            }
            else -> null
        }
    }

    private fun localSuggestions(request: HabitAssistRequest): List<HabitAssistSuggestion> {
        val normalized = request.title.lowercase(Locale.getDefault())
        val isHydrationCapture = normalized.hasHydrationHabitContext()
        val isFlexibleAllDayCapture = isHydrationCapture || "all day" in normalized
        val capturedTitle = hydrationHabitTitleCandidate(request.title) ?: smartRoutineTitleCandidate(request.title)
        return buildList {
            if (request.title.isBlank()) {
                add(
                    HabitAssistSuggestion.Title(
                        id = "local:habit:title:0",
                        label = "Draft habit name",
                        reason = "Local fallback because Gemini Nano is unavailable.",
                        source = RoutineAssistSource.LOCAL,
                        title = "Daily reset"
                    )
                )
            } else if (
                capturedTitle != null &&
                !capturedTitle.equals(request.title.trim(), ignoreCase = true)
            ) {
                add(
                    HabitAssistSuggestion.Title(
                        id = "local:habit:title:captured",
                        label = capturedTitle,
                        reason = "Cleaned cadence and time-window words out of the capture.",
                        source = RoutineAssistSource.LOCAL,
                        title = capturedTitle
                    )
                )
            }
            val capturedWindow = contextualHabitWindow(normalized)
            if (capturedWindow != null) {
                add(capturedWindow)
            } else {
                contextualRoutineStartMinute(normalized)?.let { minute ->
                    add(localHabitWindow("captured_time", "Captured time window", minute, (minute + 120).coerceAtMost(22 * 60)))
                }
            }
            val inferredCadence = contextualHabitCadence(normalized)
            if (
                request.cadence.isBlank() ||
                request.cadence == "Custom" ||
                !inferredCadence.equals(request.cadence, ignoreCase = true)
            ) {
                add(
                    HabitAssistSuggestion.Recurrence(
                        id = "local:habit:recurrence:0",
                        label = inferredCadence,
                        reason = "Matched the cadence in the habit capture.",
                        source = RoutineAssistSource.LOCAL,
                        cadence = inferredCadence
                    )
                )
            }
            contextualHabitDifficulty(normalized, request.difficulty)?.let { suggestedDifficulty ->
                add(
                    HabitAssistSuggestion.Difficulty(
                        id = "local:habit:difficulty:0",
                        label = habitDifficultyLabel(suggestedDifficulty),
                        reason = "Matched effort wording in the habit capture.",
                        source = RoutineAssistSource.LOCAL,
                        difficulty = suggestedDifficulty
                    )
                )
            }
            if (request.isBundled && isFlexibleAllDayCapture) {
                add(
                    HabitAssistSuggestion.DayPlan(
                        id = "local:habit:day_plan:flexible",
                        label = "Keep flexible",
                        reason = "All-day habits are easier to track without a fixed Day plan block.",
                        source = RoutineAssistSource.LOCAL,
                        isBundled = false
                    )
                )
            }
            if (
                !request.isBundled &&
                !isFlexibleAllDayCapture &&
                listOf(
                    "morning",
                    "workout",
                    "gym",
                    "run",
                    "walk",
                    "stretch",
                    "breathing",
                    "breath",
                    "mindful",
                    "meditate",
                    "meditation",
                    "journal",
                    "read",
                    "sleep"
                ).any { it in normalized }
            ) {
                add(
                    HabitAssistSuggestion.DayPlan(
                        id = "local:habit:day_plan:0",
                        label = "Show on day plan",
                        reason = "Time-windowed habits are easier to see beside scheduled blocks.",
                        source = RoutineAssistSource.LOCAL,
                        isBundled = true
                    )
                )
            }
        }.distinctBy { it.id }.take(MAX_SUGGESTIONS)
    }

    private fun contextualHabitWindow(normalizedText: String): HabitAssistSuggestion.Window? {
        return when {
            "all day" in normalizedText || normalizedText.hasHydrationHabitContext() ->
                localHabitWindow("all_day", "All-day window", 8 * 60, 22 * 60)
            "early morning" in normalizedText ->
                localHabitWindow("early_morning", "Early morning window", 6 * 60, 9 * 60)
            "morning" in normalizedText || "breakfast" in normalizedText ->
                localHabitWindow("morning", "Morning window", 6 * 60, 10 * 60)
            "midday" in normalizedText || "lunch" in normalizedText || "noon" in normalizedText ->
                localHabitWindow("midday", "Midday window", 11 * 60, 14 * 60)
            "afternoon" in normalizedText ->
                localHabitWindow("afternoon", "Afternoon window", 14 * 60, 18 * 60)
            "night" in normalizedText ||
                "evening" in normalizedText ||
                "dinner" in normalizedText ||
                "gym" in normalizedText ||
                "workout" in normalizedText ||
                "run" in normalizedText ||
                "breathing" in normalizedText ||
                "breath" in normalizedText ||
                "journal" in normalizedText ||
                "read" in normalizedText ->
                localHabitWindow("evening", "Evening window", 18 * 60, 22 * 60)
            "meditate" in normalizedText || "meditation" in normalizedText || "mindful" in normalizedText ->
                localHabitWindow("morning", "Morning window", 6 * 60, 10 * 60)
            "walk" in normalizedText || "stretch" in normalizedText ->
                localHabitWindow("morning", "Morning window", 6 * 60, 10 * 60)
            else -> null
        }
    }

    private fun localHabitWindow(idSuffix: String, label: String, start: Int, end: Int): HabitAssistSuggestion.Window {
        return HabitAssistSuggestion.Window(
            id = "local:habit:window:$idSuffix",
            label = label,
            reason = "Matched common wording in the habit name.",
            source = RoutineAssistSource.LOCAL,
            startMinute = start,
            endMinute = end
        )
    }
}

class MedicationAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggest(request: MedicationAssistRequest): List<MedicationAssistSuggestion> {
        val generation = genAiAssistCoordinator.generateAssistText(buildPrompt(request))
        generation.text?.let { raw ->
            val parsed = parseAssistSuggestions(raw, generation.source.toRoutineAssistSource())
            if (parsed.isNotEmpty()) return parsed
        }
        return localSuggestions(request)
    }

    private fun buildPrompt(request: MedicationAssistRequest): String = buildString {
        appendLine("Parse this typed or dictated capture into manual medication tracking fields for ChronosFlow.")
        appendLine("Return one suggestion per line as kind|label|value|reason.")
        appendLine("Kinds: details, reminder, meal_timing, refill, notes, form_route.")
        appendLine("Details value is name,dosage,unit,frequency. Use blank slots for unknown values.")
        appendLine("Reminder value is primaryMinute or primaryMinute,secondaryMinute,frequency. Use frequency for twice/3-times/4-times daily wording.")
        appendLine("Form route value is form,route.")
        appendLine("Only extract and organize user-provided tracking fields. Do not recommend medications, dosage changes, interactions, or clinical advice.")
        appendLine("Return complementary details, reminder, meal_timing, form_route, refill, and notes suggestions together when the capture supports them.")
        appendLine("For dictated fragments, preserve the medication or supplement name separately from dose, frequency, and timing words.")
        appendLine("Examples:")
        appendLine("Input: vitamin d 1000 iu morning")
        appendLine("details|Vitamin D 1000 iu|Vitamin D,1000,iu,Once daily|Extract only the stated name, amount, unit, and simple frequency.")
        appendLine("reminder|Morning reminder|480|The capture says morning.")
        appendLine("form_route|Tablet form|tablet,oral|Use a safe tracking default when form is not stated.")
        appendLine("Input: vitamin b12 1000 mcg after breakfast")
        appendLine("details|Vitamin B12 1000 mcg|Vitamin B12,1000,mcg,Once daily|Extract the supplement name, stated amount, unit, and simple frequency.")
        appendLine("reminder|Breakfast reminder|480|Breakfast maps to a morning reminder.")
        appendLine("meal_timing|After food|After food|After breakfast implies food timing tracking.")
        appendLine("form_route|Tablet form|tablet,oral|Use a safe tracking default when form is not stated.")
        appendLine("Input: magnesium 200 mg at 9pm")
        appendLine("details|Magnesium 200 mg|Magnesium,200,mg,Once daily|Extract only the stated supplement, amount, and unit.")
        appendLine("reminder|9:00 PM reminder|1260|The capture names 9pm.")
        appendLine("Input: metformin 500 mg with dinner")
        appendLine("details|Metformin 500 mg|Metformin,500,mg,Once daily|Extract name, amount, and unit from the capture.")
        appendLine("reminder|Evening reminder|1080|Dinner maps to evening reminder timing.")
        appendLine("meal_timing|With food|With food|Dinner implies the user wants food timing tracked.")
        appendLine("Input: metformin 500 mg twice daily")
        appendLine("details|Metformin 500 mg twice daily|Metformin,500,mg,Twice daily|Extract name, amount, unit, and frequency from the capture.")
        appendLine("reminder|Twice daily|480,1200,Twice daily|Twice daily means two reminder windows.")
        appendLine("Input: insulin 10 units with dinner")
        appendLine("details|Insulin 10 units|Insulin,10,unit,Once daily|Extract name, stated units, and simple frequency from the capture.")
        appendLine("reminder|Evening reminder|1080|Dinner maps to evening reminder timing.")
        appendLine("meal_timing|With food|With food|Dinner implies the user wants food timing tracked.")
        appendLine("form_route|Injection form|injection,injection|Insulin wording sets injection tracking.")
        appendLine("Input: epi pen as needed")
        appendLine("details|EpiPen|EpiPen,,,As needed|As needed is stated by the user.")
        appendLine("form_route|Injection form|injection,injection|EpiPen wording sets injection tracking.")
        appendLine("notes|Emergency injection|Emergency injection|Capture the emergency-use context as a note, without adding clinical advice.")
        appendLine("Input: eye drops 2x/day")
        appendLine("details|Eye drops 2x/day|Eye drops,,,Twice daily|Normalize 2x/day to twice-daily tracking.")
        appendLine("reminder|Twice daily|480,1200,Twice daily|Carry the shorthand cadence as the reminder frequency.")
        appendLine("form_route|Drop form|drop,topical|Drops wording sets the form.")
        appendLine("Input: rescue inhaler as needed 23 left")
        appendLine("details|Rescue inhaler|Rescue inhaler,,,As needed|As needed is stated by the user.")
        appendLine("form_route|Inhaler form|inhaler,inhaled|Inhaler wording sets form and route.")
        appendLine("refill|Track 23 doses|23|The capture states remaining supply.")
        appendLine("Input: refill asthma inhaler at pharmacy")
        appendLine("details|Asthma inhaler|Asthma inhaler,,,As needed|Preserve the refill medication name and avoid inventing a dose.")
        appendLine("form_route|Inhaler form|inhaler,inhaled|Inhaler wording sets form and route.")
        appendLine("notes|Pharmacy refill|Refill at pharmacy|The capture says this is a pharmacy refill.")
        appendLine("Input: rx pickup at pharmacy")
        appendLine("details|Prescription pickup|Prescription,,,As needed|Use a generic prescription label when the actual medication name is not stated.")
        appendLine("notes|Pharmacy pickup|Pick up prescription at pharmacy|Capture the refill/pickup intent without inventing a medication or dose.")
        appendLine("Input: zyrtec for allergies")
        appendLine("details|Zyrtec|Zyrtec,,,As needed|A symptom-only allergy capture should be organized as as-needed tracking unless a cadence is stated.")
        appendLine("notes|Use for allergies|Use for allergies|The capture includes an indication note.")
        appendLine("form_route|Tablet by mouth|tablet,oral|Common allergy pills can use the safe oral-tablet tracking default when form is not stated.")
        appendLine("Input: ibuprofen 200 mg for headache")
        appendLine("details|Ibuprofen 200 mg|Ibuprofen,200,mg,As needed|Pain-relief symptom wording should be organized as as-needed tracking unless a cadence is stated.")
        appendLine("notes|Use for headache|Use for headache|The capture includes an indication note.")
        appendLine("Input: cough syrup 10 ml with food")
        appendLine("details|Cough syrup 10 ml|Cough syrup,10,ml,Once daily|Extract only the stated name, amount, unit, and simple frequency.")
        appendLine("meal_timing|With food|With food|The capture says with food.")
        appendLine("form_route|Liquid by mouth|liquid,oral|Syrup and mL wording set liquid oral tracking.")
        appendLine("Input: cough syrup 2 teaspoons with food")
        appendLine("details|Cough syrup 2 tsp|Cough syrup,2,tsp,Once daily|Normalize teaspoon to tsp and keep the dose separate from the name.")
        appendLine("meal_timing|With food|With food|The capture says with food.")
        appendLine("form_route|Liquid by mouth|liquid,oral|Syrup and teaspoon wording set liquid oral tracking.")
        appendLine("Input: hydrocortisone cream for rash")
        appendLine("details|Hydrocortisone cream|Hydrocortisone cream,,,As needed|Cream used for a symptom is tracked as as-needed when no cadence is stated.")
        appendLine("form_route|Topical form|cream,topical|Cream wording sets topical form and route.")
        appendLine("notes|Use for rash|Use for rash|The capture includes an indication note.")
        appendLine("Input: eye drops every 6 hours")
        appendLine("details|Eye drops|Eye drops,,,4 times daily|Every 6 hours maps to four daily tracking windows.")
        appendLine("reminder|Every 6 hours|480,840,4 times daily|Carry the stated cadence as the reminder frequency.")
        appendLine("form_route|Drop form|drop,topical|Drops wording sets the form.")
        appendLine("Name: ${request.name}")
        appendLine("Dosage: ${request.dosage} ${request.unit}")
        appendLine("Frequency: ${request.frequency}")
        appendLine("Reminder: ${request.primaryReminderMinute},${request.secondaryReminderMinute ?: "none"}")
        appendLine("Meal timing: ${request.mealTiming}")
        appendLine("Refill tracking: ${request.hasRefillTracking}")
        appendLine("Notes: ${request.notes}")
        appendLine("Form/route: ${request.form},${request.route}")
    }

    private fun parseAssistSuggestions(text: String, source: RoutineAssistSource): List<MedicationAssistSuggestion> {
        return text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() && it.count { char -> char == '|' } >= 3 }
            .mapIndexedNotNull { index, line ->
                val parts = line.split("|").map(String::trim)
                val kind = parts.getOrNull(0)?.lowercase(Locale.getDefault()) ?: return@mapIndexedNotNull null
                val label = parts.getOrNull(1).orEmpty()
                val value = parts.getOrNull(2).orEmpty()
                val reason = parts.drop(3).joinToString("|").ifBlank { defaultRoutineReasonFor(source) }
                medicationSuggestionFromParts(index, kind, label, value, reason, source)
            }
            .take(MAX_SUGGESTIONS)
            .toList()
    }

    private fun medicationSuggestionFromParts(
        index: Int,
        kind: String,
        label: String,
        value: String,
        reason: String,
        source: RoutineAssistSource
    ): MedicationAssistSuggestion? {
        val id = "${source.name.lowercase(Locale.getDefault())}:medication:$kind:$index"
        return when (kind) {
            "details" -> parseMedicationDetails(value)?.let { details ->
                MedicationAssistSuggestion.Details(
                    id = id,
                    label = label.ifBlank { details.displayLabel },
                    reason = reason,
                    source = source,
                    name = details.name,
                    dosage = details.dosage,
                    unit = details.unit,
                    frequency = details.frequency
                )
            }
            "reminder" -> parseReminder(value)?.let { reminder ->
                MedicationAssistSuggestion.Reminder(
                    id = id,
                    label = label.ifBlank { "Adjust reminder" },
                    reason = reason,
                    source = source,
                    primaryMinute = reminder.primaryMinute,
                    secondaryMinute = reminder.secondaryMinute,
                    frequency = reminder.frequency
                )
            }
            "meal_timing" -> value.takeIf { it in medicationMealTimingValues }?.let {
                MedicationAssistSuggestion.MealTiming(id, label.ifBlank { it }, reason, source, it)
            }
            "refill" -> value.toIntOrNull()?.takeIf { it > 0 }?.let {
                MedicationAssistSuggestion.RefillTracking(id, label.ifBlank { "Track refills" }, reason, source, it)
            }
            "notes" -> value.takeIf(String::isNotBlank)?.let {
                MedicationAssistSuggestion.Notes(id, label.ifBlank { "Add note" }, reason, source, it)
            }
            "form_route" -> parseFormRoute(value)?.let { (form, route) ->
                MedicationAssistSuggestion.FormRoute(
                    id = id,
                    label = label.ifBlank { "Set form" },
                    reason = reason,
                    source = source,
                    form = form,
                    route = route
                )
            }
            else -> null
        }
    }

    private fun localSuggestions(request: MedicationAssistRequest): List<MedicationAssistSuggestion> {
        val rawText = listOf(
            request.name,
            request.dosage,
            request.unit.takeIf { request.dosage.isNotBlank() }.orEmpty(),
            request.frequency.takeUnless { it == "Once daily" }.orEmpty(),
            request.mealTiming.takeUnless { it == "Anytime" }.orEmpty(),
            request.notes,
            request.form.takeUnless { it == "tablet" }.orEmpty()
        ).joinToString(" ")
        val normalized = rawText
            .lowercase(Locale.getDefault())
        val details = inferMedicationDetails(rawText, request)
        return buildList {
            if (details != null) {
                add(
                    MedicationAssistSuggestion.Details(
                        id = "local:medication:details:0",
                        label = details.displayLabel,
                        reason = "Extracted structured medication fields from the capture.",
                        source = RoutineAssistSource.LOCAL,
                        name = details.name,
                        dosage = details.dosage,
                        unit = details.unit,
                        frequency = details.frequency
                    )
                )
            }
            explicitTimeStartMinute(normalized)?.let { minute ->
                add(localMedicationReminder("captured_time", "${formatMinuteLabel(minute)} reminder", minute, null))
            }
            if ("bed" in normalized || "night" in normalized || "sleep" in normalized) {
                add(localMedicationReminder("bedtime", "Bedtime reminder", 21 * 60, null))
                if (request.mealTiming != "Before bed") {
                    add(
                        MedicationAssistSuggestion.MealTiming(
                            id = "local:medication:meal_timing:bed",
                            label = "Before bed",
                            reason = "Matched bedtime wording in the plan.",
                            source = RoutineAssistSource.LOCAL,
                            mealTiming = "Before bed"
                        )
                    )
                }
            }
            if ("morning" in normalized || "breakfast" in normalized) {
                add(localMedicationReminder("morning", "Morning reminder", 8 * 60, null))
            }
            if (
                "food" in normalized ||
                "meal" in normalized ||
                "with breakfast" in normalized ||
                "with lunch" in normalized ||
                "with dinner" in normalized ||
                "after breakfast" in normalized ||
                "after lunch" in normalized ||
                "after dinner" in normalized
            ) {
                if ("breakfast" in normalized) {
                    add(localMedicationReminder("morning", "Morning reminder", 8 * 60, null))
                }
                if (request.mealTiming != "With food") {
                    add(
                        MedicationAssistSuggestion.MealTiming(
                            id = "local:medication:meal_timing:food",
                            label = "With food",
                            reason = "Matched food-related wording in the plan.",
                            source = RoutineAssistSource.LOCAL,
                            mealTiming = "With food"
                        )
                    )
                }
            }
            if ("lunch" in normalized || "noon" in normalized) {
                add(localMedicationReminder("noon", "Noon reminder", 12 * 60, null))
            }
            if ("evening" in normalized || "dinner" in normalized) {
                add(localMedicationReminder("evening", "Evening reminder", 18 * 60, null))
            }
            if (
                "twice" in normalized ||
                "two times" in normalized ||
                "2 times" in normalized ||
                "2x" in normalized ||
                request.frequency == "Twice daily"
            ) {
                add(localMedicationReminder("twice", "12h spacing", request.primaryReminderMinute, (request.primaryReminderMinute + 12 * 60) % (24 * 60)))
            }
            if (
                Regex("""\bevery\s+(?:12|twelve)\s+hours?\b""").containsMatchIn(normalized) &&
                request.frequency != "Twice daily"
            ) {
                add(localMedicationReminder("every_12_hours", "Every 12 hours", request.primaryReminderMinute, (request.primaryReminderMinute + 12 * 60) % (24 * 60)))
            }
            if (
                "3 times" in normalized ||
                "three times" in normalized ||
                "3x" in normalized ||
                request.frequency == "3 times daily"
            ) {
                add(
                    MedicationAssistSuggestion.Reminder(
                        id = "local:medication:reminder:three_times",
                        label = "3 daily reminders",
                        reason = "Matched three-times-daily wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        primaryMinute = request.primaryReminderMinute.coerceIn(0, 1439),
                        secondaryMinute = (request.primaryReminderMinute + 8 * 60) % (24 * 60),
                        frequency = "3 times daily"
                    )
                )
            }
            if (
                Regex("""\bevery\s+(?:8|eight)\s+hours?\b""").containsMatchIn(normalized) &&
                request.frequency != "3 times daily"
            ) {
                add(
                    MedicationAssistSuggestion.Reminder(
                        id = "local:medication:reminder:every_8_hours",
                        label = "Every 8 hours",
                        reason = "Matched every-eight-hours wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        primaryMinute = request.primaryReminderMinute.coerceIn(0, 1439),
                        secondaryMinute = (request.primaryReminderMinute + 8 * 60) % (24 * 60),
                        frequency = "3 times daily"
                    )
                )
            }
            if (
                "4 times" in normalized ||
                "four times" in normalized ||
                "4x" in normalized ||
                request.frequency == "4 times daily"
            ) {
                add(
                    MedicationAssistSuggestion.Reminder(
                        id = "local:medication:reminder:four_times",
                        label = "4 daily reminders",
                        reason = "Matched four-times-daily wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        primaryMinute = request.primaryReminderMinute.coerceIn(0, 1439),
                        secondaryMinute = (request.primaryReminderMinute + 6 * 60) % (24 * 60),
                        frequency = "4 times daily"
                    )
                )
            }
            if (
                Regex("""\bevery\s+(?:6|six)\s+hours?\b""").containsMatchIn(normalized) &&
                request.frequency != "4 times daily"
            ) {
                add(
                    MedicationAssistSuggestion.Reminder(
                        id = "local:medication:reminder:every_6_hours",
                        label = "Every 6 hours",
                        reason = "Matched every-six-hours wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        primaryMinute = request.primaryReminderMinute.coerceIn(0, 1439),
                        secondaryMinute = (request.primaryReminderMinute + 6 * 60) % (24 * 60),
                        frequency = "4 times daily"
                    )
                )
            }
            if ("inhaler" in normalized && (request.form != "inhaler" || request.route != "inhaled")) {
                add(
                    MedicationAssistSuggestion.FormRoute(
                        id = "local:medication:form_route:inhaler",
                        label = "Inhaler form",
                        reason = "Matched inhaler wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        form = "inhaler",
                        route = "inhaled"
                    )
                )
            }
            if (
                listOf("insulin", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen", "epinephrine").any { it in normalized } &&
                (request.form != "injection" || request.route != "injection")
            ) {
                add(
                    MedicationAssistSuggestion.FormRoute(
                        id = "local:medication:form_route:injection",
                        label = "Injection form",
                        reason = "Matched injection wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        form = "injection",
                        route = "injection"
                    )
                )
            }
            if (
                listOf("vitamin", "supplement", "magnesium", "calcium", "zinc", "probiotic", "d3", "b12", "omega")
                    .any { it in normalized } &&
                (request.form != "tablet" || request.route != "oral")
            ) {
                add(
                    MedicationAssistSuggestion.FormRoute(
                        id = "local:medication:form_route:supplement",
                        label = "Tablet by mouth",
                        reason = "Matched supplement wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        form = "tablet",
                        route = "oral"
                    )
                )
            }
            if (
                listOf("cream", "ointment", "gel", "topical", "patch").any { it in normalized } &&
                (request.form != "cream" || request.route != "topical")
            ) {
                add(
                    MedicationAssistSuggestion.FormRoute(
                        id = "local:medication:form_route:topical",
                        label = "Topical form",
                        reason = "Matched topical wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        form = if ("patch" in normalized) "patch" else "cream",
                        route = if ("patch" in normalized) "transdermal" else "topical"
                    )
                )
            }
            if (
                listOf("liquid", "syrup", "solution", "ml", "milliliter", "milliliters", "tsp", "teaspoon", "tbsp", "tablespoon").any { it in normalized } &&
                (request.form != "liquid" || request.route != "oral")
            ) {
                add(
                    MedicationAssistSuggestion.FormRoute(
                        id = "local:medication:form_route:liquid",
                        label = "Liquid by mouth",
                        reason = "Matched liquid or mL wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        form = "liquid",
                        route = "oral"
                    )
                )
            }
            if (("capsule" in normalized || "cap " in normalized) && request.form != "capsule") {
                add(
                    MedicationAssistSuggestion.FormRoute(
                        id = "local:medication:form_route:capsule",
                        label = "Capsule form",
                        reason = "Matched capsule wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        form = "capsule",
                        route = "oral"
                    )
                )
            }
            if (("drop" in normalized || "drops" in normalized) && request.form != "drop") {
                add(
                    MedicationAssistSuggestion.FormRoute(
                        id = "local:medication:form_route:drop",
                        label = "Drop form",
                        reason = "Matched drop wording in the plan.",
                        source = RoutineAssistSource.LOCAL,
                        form = "drop",
                        route = request.route.takeIf { it != "oral" } ?: "topical"
                    )
                )
            }
            inferMedicationSupplyCount(rawText)?.takeIf { !request.hasRefillTracking }?.let { dosesLeft ->
                add(
                    MedicationAssistSuggestion.RefillTracking(
                        id = "local:medication:refill:0",
                        label = "Track $dosesLeft doses",
                        reason = "The capture mentions remaining supply.",
                        source = RoutineAssistSource.LOCAL,
                        dosesLeft = dosesLeft
                    )
                )
            }
            val explicitMedicationNote = extractMedicationNote(rawText)
            explicitMedicationNote?.takeIf { request.notes.isBlank() }?.let { note ->
                add(
                    MedicationAssistSuggestion.Notes(
                        id = "local:medication:notes:0",
                        label = "Add note",
                        reason = "The capture includes a note or instruction.",
                        source = RoutineAssistSource.LOCAL,
                        notes = note
                    )
                )
            }
            if (request.notes.isBlank() && explicitMedicationNote == null) {
                val isPrescriptionPickup = "pick up prescription" in normalized ||
                    "pickup prescription" in normalized ||
                    ("pickup" in normalized && "pharmacy" in normalized)
                val isPharmacyRefill = isPrescriptionPickup ||
                    "refill" in normalized ||
                    "prescription" in normalized ||
                    "pharmacy" in normalized ||
                    " rx " in " $normalized "
                if (isPharmacyRefill) {
                    add(
                        MedicationAssistSuggestion.Notes(
                            id = "local:medication:notes:pharmacy_refill",
                            label = if (isPrescriptionPickup) "Pharmacy pickup" else "Pharmacy refill",
                            reason = "The capture mentions a pharmacy refill or prescription pickup.",
                            source = RoutineAssistSource.LOCAL,
                            notes = if (isPrescriptionPickup) {
                                "Pick up prescription at pharmacy"
                            } else {
                                "Refill at pharmacy"
                            }
                        )
                    )
                }
            }
        }.distinctBy { it.id }.take(MAX_SUGGESTIONS)
    }

    private fun localMedicationReminder(
        idSuffix: String,
        label: String,
        primary: Int,
        secondary: Int?
    ): MedicationAssistSuggestion.Reminder {
        return MedicationAssistSuggestion.Reminder(
            id = "local:medication:reminder:$idSuffix",
            label = label,
            reason = "Local fallback based on the current plan text.",
            source = RoutineAssistSource.LOCAL,
            primaryMinute = primary.coerceIn(0, 1439),
            secondaryMinute = secondary?.coerceIn(0, 1439),
            frequency = if (secondary != null) "Twice daily" else "Once daily"
        )
    }
}

private data class MedicationDetailsCandidate(
    val name: String? = null,
    val dosage: String? = null,
    val unit: String? = null,
    val frequency: String? = null
) {
    val displayLabel: String
        get() = listOfNotNull(name, dosage?.let { dose -> listOfNotNull(dose, unit).joinToString(" ") }, frequency)
            .joinToString(" · ")
            .ifBlank { "Fill medication details" }
}

private fun parseMedicationDetails(value: String): MedicationDetailsCandidate? {
    val parts = value.split(',', ';').map { it.trim() }
    val details = MedicationDetailsCandidate(
        name = parts.getOrNull(0).blankToNull(),
        dosage = parts.getOrNull(1).blankToNull(),
        unit = parts.getOrNull(2).blankToNull()?.normalizeMedicationUnit(),
        frequency = parts.getOrNull(3).blankToNull()?.normalizeMedicationFrequency()
    )
    return details.takeIf { detail ->
        detail.name != null || detail.dosage != null || detail.unit != null || detail.frequency != null
    }
}

private fun inferMedicationDetails(
    rawText: String,
    request: MedicationAssistRequest
): MedicationDetailsCandidate? {
    val dosageMatch = MEDICATION_DOSAGE_PATTERN.find(rawText)
    val dosage = dosageMatch?.groupValues?.getOrNull(1)?.normalizeMedicationDosage()
    val unit = dosageMatch?.groupValues?.getOrNull(2)?.normalizeMedicationUnit()
    val frequency = inferMedicationFrequency(rawText)
    val name = smartMedicationNameCandidate(rawText)
    val details = MedicationDetailsCandidate(
        name = name,
        dosage = dosage,
        unit = unit,
        frequency = frequency
    )
    return details.takeIf { detail ->
        detail.name?.equals(request.name.trim(), ignoreCase = true) == false ||
            detail.dosage?.equals(request.dosage.trim(), ignoreCase = true) == false ||
            detail.unit?.equals(request.unit.trim(), ignoreCase = true) == false ||
            detail.frequency?.equals(request.frequency.trim(), ignoreCase = true) == false
    }
}

private fun smartMedicationNameCandidate(text: String): String? {
    val candidate = text
        .trim()
        .replace(MEDICATION_CAPTURE_PREFIX_PATTERN, "")
        .replace(MEDICATION_DOSAGE_PATTERN, " ")
        .replace(MEDICATION_INDICATION_PATTERN, " ")
        .replace(MEDICATION_CONTEXT_NOISE_PATTERN, " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ',', ';', '-', ':')
        .take(MAX_ROUTINE_TITLE_LENGTH)
        .trim()
    return candidate
        .takeIf { it.length >= 2 }
        ?.toMedicationNameLabel()
}

private fun smartRoutineTitleCandidate(text: String): String? {
    val candidate = text
        .trim()
        .replace(ROUTINE_CAPTURE_PREFIX_PATTERN, "")
        .replace(ROUTINE_TITLE_NOISE_PATTERN, " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ',', ';', '-', ':')
        .take(MAX_ROUTINE_TITLE_LENGTH)
        .trim()
    return candidate
        .takeIf { it.length >= 3 }
        ?.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
}

private fun hydrationHabitTitleCandidate(text: String): String? {
    val normalized = text.lowercase(Locale.getDefault())
    if (!normalized.hasHydrationHabitContext()) return null
    val amount = HYDRATION_TARGET_PATTERN.find(normalized)
        ?.value
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
    val candidate = if (amount.isNullOrBlank()) {
        "Hydrate"
    } else {
        "Drink $amount water"
    }
    return candidate.take(MAX_ROUTINE_TITLE_LENGTH)
}

private fun contextualHabitCadence(normalizedText: String): String {
    return when {
        "weekday" in normalizedText || "workday" in normalizedText -> "Weekdays"
        "weekend" in normalizedText -> "Weekends"
        Regex("""\b(?:mon|monday)\s*[/,\s]\s*(?:wed|wednesday)\s*[/,\s]\s*(?:fri|friday)\b""").containsMatchIn(normalizedText) ||
            "monday wednesday friday" in normalizedText -> "Mon/Wed/Fri"
        Regex("""\b(?:tue|tues|tuesday)\s*[/,\s]\s*(?:thu|thur|thurs|thursday)\b""").containsMatchIn(normalizedText) ||
            "tuesday thursday" in normalizedText -> "Tue/Thu"
        "every other day" in normalizedText -> "Every 2 days"
        Regex("""\b(?:(?:once|twice)|(?:(?:two|three|four|five|six|\d+)\s+times?))\s+(?:(?:a|per)\s+day|daily)\b|\b(?:\d+|two|three|four|five|six)x\s*/?\s*(?:day|daily)\b""")
            .containsMatchIn(normalizedText) -> "Daily"
        Regex("""\b(?:2|two)\s*(?:x|times?)\s*(?:a|per)?\s*week\b""").containsMatchIn(normalizedText) ||
            "twice a week" in normalizedText ||
            "twice per week" in normalizedText -> "2x / week"
        Regex("""\b(?:3|three)\s*(?:x|times?)\s*(?:a|per)?\s*week\b""").containsMatchIn(normalizedText) -> "3x / week"
        Regex("""\b(?:4|four)\s*(?:x|times?)\s*(?:a|per)?\s*week\b""").containsMatchIn(normalizedText) -> "4x / week"
        Regex("""\b(?:5|five)\s*(?:x|times?)\s*(?:a|per)?\s*week\b""").containsMatchIn(normalizedText) -> "5x / week"
        Regex("""\b(?:6|six)\s*(?:x|times?)\s*(?:a|per)?\s*week\b""").containsMatchIn(normalizedText) -> "6x / week"
        "weekly" in normalizedText ||
            "every week" in normalizedText ||
            Regex("""\bonce\s*(?:a|per)?\s*week\b""").containsMatchIn(normalizedText) -> "Weekly"
        "monthly" in normalizedText ||
            "every month" in normalizedText ||
            Regex("""\bonce\s*(?:a|per)?\s*month\b""").containsMatchIn(normalizedText) -> "1x / month"
        else -> "Daily"
    }
}

private fun contextualRoutineStartMinute(normalizedText: String): Int? {
    explicitTimeStartMinute(normalizedText)?.let { return it }
    return when {
        "early morning" in normalizedText -> 6 * 60
        "morning" in normalizedText || "breakfast" in normalizedText -> 7 * 60
        "midday" in normalizedText || "lunch" in normalizedText || "noon" in normalizedText -> 12 * 60
        "afternoon" in normalizedText -> 15 * 60
        "evening" in normalizedText || "dinner" in normalizedText -> 18 * 60
        "night" in normalizedText || "bedtime" in normalizedText -> 21 * 60
        else -> null
    }
}

private fun explicitTimeStartMinute(normalizedText: String): Int? {
    val match = EXPLICIT_TIME_PATTERN.find(normalizedText) ?: return null
    val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val minute = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
    val period = match.groupValues.getOrNull(3).orEmpty()
    if (hour !in 1..12 || minute !in 0..59) return null
    val hour24 = when {
        period.startsWith("p") && hour != 12 -> hour + 12
        period.startsWith("a") && hour == 12 -> 0
        else -> hour
    }
    return hour24 * 60 + minute
}

private fun contextualHabitDifficulty(normalizedText: String, currentDifficulty: Int): Int? {
    return when {
        "very easy" in normalizedText || "tiny" in normalizedText || "simple" in normalizedText -> 1
        "easy" in normalizedText ||
            "light" in normalizedText ||
            normalizedText.hasHydrationHabitContext() ||
            "meditate" in normalizedText ||
            "journal" in normalizedText ||
            "read" in normalizedText ||
            "walk" in normalizedText ||
            "yoga" in normalizedText ||
            "pilates" in normalizedText ||
            "mobility" in normalizedText ||
            "stretch" in normalizedText ||
            "floss" in normalizedText ||
            "flossing" in normalizedText ||
            "brush teeth" in normalizedText ||
            "brush my teeth" in normalizedText ||
            "brushing teeth" in normalizedText ||
            "brushing my teeth" in normalizedText ||
            "dental" in normalizedText ||
            "meal prep" in normalizedText ||
            "meal planning" in normalizedText ||
            "prep meals" in normalizedText ||
            "nutrition" in normalizedText ||
            "healthy eating" in normalizedText ||
            "eat breakfast" in normalizedText ||
            "eat lunch" in normalizedText ||
            "eat dinner" in normalizedText ||
            "eat vegetables" in normalizedText ||
            "eat fruit" in normalizedText ||
            "protein" in normalizedText ||
            "reduce caffeine" in normalizedText ||
            "cut caffeine" in normalizedText ||
            "limit caffeine" in normalizedText ||
            "cut sugar" in normalizedText ||
            "reduce sugar" in normalizedText ||
            "limit sugar" in normalizedText ||
            "sleep routine" in normalizedText ||
            "bedtime" in normalizedText ||
            "wake up" in normalizedText ||
            "wind down" in normalizedText ||
            "screen free" in normalizedText ||
            "screen-free" in normalizedText ||
            "sleep" in normalizedText -> 2
        "moderate" in normalizedText -> 3
        "study" in normalizedText ||
            "learn" in normalizedText ||
            "learning" in normalizedText ||
            "language practice" in normalizedText ||
            "spanish" in normalizedText ||
            "guitar" in normalizedText ||
            "piano" in normalizedText ||
            "coding practice" in normalizedText -> 3
        "hard" in normalizedText ||
            "workout" in normalizedText ||
            "gym" in normalizedText ||
            "cardio" in normalizedText ||
            "cycling" in normalizedText ||
            "run" in normalizedText ||
            "running" in normalizedText ||
            "swim" in normalizedText ||
            "swimming" in normalizedText ||
            "strength" in normalizedText ||
            "weights" in normalizedText ||
            "lift" in normalizedText ||
            "lifting" in normalizedText -> 4
        "very hard" in normalizedText || "intense" in normalizedText -> 5
        currentDifficulty !in 1..5 -> 3
        else -> null
    }
}

private fun String.hasHydrationHabitContext(): Boolean {
    return Regex("""\b(?:water|hydrate|hydration|drink|fluids?)\b""").containsMatchIn(this) ||
        HYDRATION_TARGET_PATTERN.containsMatchIn(this)
}

private fun habitDifficultyLabel(level: Int): String = when (level.coerceIn(1, 5)) {
    1 -> "Very easy"
    2 -> "Easy"
    3 -> "Moderate effort"
    4 -> "Hard"
    else -> "Very hard"
}

private fun inferMedicationFrequency(text: String): String? {
    val normalized = text.lowercase(Locale.getDefault())
    return when {
        "as needed" in normalized || "prn" in normalized || "rescue" in normalized -> "As needed"
        "every other day" in normalized -> "Every other day"
        "weekday" in normalized || "workday" in normalized -> "Weekdays"
        "mon/wed/fri" in normalized || "monday wednesday friday" in normalized -> "Mon/Wed/Fri"
        "tue/thu" in normalized || "tuesday thursday" in normalized -> "Tue/Thu"
        "weekly" in normalized || "once a week" in normalized -> "Weekly"
        Regex("""\bevery\s+(?:12|twelve)\s+hours?\b""").containsMatchIn(normalized) -> "Twice daily"
        Regex("""\bevery\s+(?:8|eight)\s+hours?\b""").containsMatchIn(normalized) -> "3 times daily"
        Regex("""\bevery\s+(?:6|six)\s+hours?\b""").containsMatchIn(normalized) -> "4 times daily"
        Regex("""\b(?:4|four)\s*(?:x|times?)\s*(?:a|per)?\s*day\b""").containsMatchIn(normalized) ||
            "4 times" in normalized ||
            "four times" in normalized ||
            "4x" in normalized -> "4 times daily"
        Regex("""\b(?:3|three)\s*(?:x|times?)\s*(?:a|per)?\s*day\b""").containsMatchIn(normalized) ||
            "3 times" in normalized ||
            "three times" in normalized ||
            "3x" in normalized -> "3 times daily"
        Regex("""\b(?:2|two)\s*(?:x|times?)\s*(?:a|per)?\s*day\b""").containsMatchIn(normalized) ||
            "twice" in normalized ||
            "two times" in normalized ||
            "2x" in normalized -> "Twice daily"
        Regex("""\bonce\s*(?:a|per)?\s*day\b""").containsMatchIn(normalized) ||
            Regex("""\bevery\s+(?:morning|afternoon|evening|night|bedtime)\b""").containsMatchIn(normalized) ||
            "nightly" in normalized ||
            "daily" in normalized ||
            "every day" in normalized ||
            "once" in normalized -> "Once daily"
        AS_NEEDED_SYMPTOM_HINTS.any { it in normalized } -> "As needed"
        else -> null
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

private fun String.normalizeMedicationDosage(): String {
    return trim().replace("1/2", "½")
}

private fun String.normalizeMedicationUnit(): String {
    return trim().lowercase(Locale.getDefault()).let { unit ->
        when (unit) {
            "tablets", "tablet", "tabs", "tab" -> "tablet"
            "capsules", "capsule", "caps" -> "capsule"
            "micrograms", "microgram", "ug" -> "mcg"
            "international units", "international unit" -> "iu"
            "milliliters", "milliliter" -> "ml"
            "teaspoons", "teaspoon", "tsps", "tsp" -> "tsp"
            "tablespoons", "tablespoon", "tbsps", "tbsp" -> "tbsp"
            "drops", "drop" -> "drop"
            "doses", "dose", "puffs", "puff", "sprays", "spray" -> "dose"
            "units" -> "unit"
            else -> unit
        }
    }
}

private fun String.normalizeMedicationFrequency(): String? {
    return inferMedicationFrequency(this) ?: trim().takeIf { it.isNotBlank() }
}

private fun String.toMedicationNameLabel(): String {
    return trim().split(Regex("\\s+"))
        .joinToString(" ") { part ->
            val normalized = part.trim()
            when {
                normalized.length == 1 && normalized.any(Char::isLetter) -> normalized.uppercase(Locale.getDefault())
                normalized.any(Char::isDigit) && normalized.any(Char::isLetter) -> normalized.uppercase(Locale.getDefault())
                else -> normalized.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                }
            }
        }
}

private fun inferMedicationSupplyCount(text: String): Int? {
    return MEDICATION_SUPPLY_PATTERN.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
}

private fun extractMedicationNote(text: String): String? {
    return MEDICATION_NOTE_PATTERN.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.trim('.', ',', ';', ':')
        ?.take(MAX_MEDICATION_NOTE_LENGTH)
        ?.takeIf { it.isNotBlank() }
        ?: MEDICATION_INDICATION_PATTERN.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.let { "Use for $it" }
        ?.trim('.', ',', ';', ':')
        ?.take(MAX_MEDICATION_NOTE_LENGTH)
        ?.takeIf { it.isNotBlank() }
}

private val medicationMealTimingValues = setOf("Anytime", "With food", "Before bed")

private fun parseMinutePair(value: String): Pair<Int, Int>? {
    val parts = value.split(',', ';').map(String::trim)
    val start = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 1425) ?: return null
    val end = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(start + 15, 1440) ?: return null
    return start to end
}

private data class ParsedMedicationReminder(
    val primaryMinute: Int,
    val secondaryMinute: Int?,
    val frequency: String
)

private fun parseReminder(value: String): ParsedMedicationReminder? {
    val parts = value.split(',', ';').map(String::trim)
    val primary = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 1439) ?: return null
    val secondary = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 1439)
    val frequency = parts.getOrNull(2)
        ?.normalizeMedicationFrequency()
        ?: if (secondary != null) "Twice daily" else "Once daily"
    return ParsedMedicationReminder(primary, secondary, frequency)
}

private fun parseFormRoute(value: String): Pair<String, String>? {
    val parts = value.split(',', ';').map { it.trim().lowercase(Locale.getDefault()) }
    val form = parts.getOrNull(0)
        ?.takeIf(String::isNotBlank)
        ?.normalizeMedicationFormRouteForm()
        ?: return null
    val route = parts.getOrNull(1)
        ?.takeIf(String::isNotBlank)
        ?.normalizeMedicationFormRouteRoute(form)
        ?: return null
    return form to route
}

private fun String.normalizeMedicationFormRouteForm(): String {
    return when (this) {
        "ointment", "gel", "topical" -> "cream"
        "syrup", "solution" -> "liquid"
        else -> this
    }
}

private fun String.normalizeMedicationFormRouteRoute(form: String): String {
    return when {
        form == "cream" -> "topical"
        form == "patch" -> "transdermal"
        form == "inhaler" -> "inhaled"
        form == "injection" -> "injection"
        this == "mouth" || this == "by mouth" || this == "po" -> "oral"
        else -> this
    }
}

private fun formatMinuteLabel(minute: Int): String {
    val normalized = minute.coerceIn(0, 1439)
    val hour24 = normalized / 60
    val minutePart = normalized % 60
    val hour12 = when (val hour = hour24 % 12) {
        0 -> 12
        else -> hour
    }
    val period = if (hour24 < 12) "AM" else "PM"
    return "$hour12:${minutePart.toString().padStart(2, '0')} $period"
}

private fun defaultRoutineReasonFor(source: RoutineAssistSource): String = when (source) {
    RoutineAssistSource.GEMINI_NANO -> "Suggested with Gemini Nano on-device."
    RoutineAssistSource.CLOUD_GEMINI -> "Suggested with cloud Gemini."
    RoutineAssistSource.LOCAL -> "Suggested locally."
}

private const val MAX_ROUTINE_TITLE_LENGTH = 72
private const val MAX_MEDICATION_NOTE_LENGTH = 120
private const val MAX_SUGGESTIONS = 6

private val MEDICATION_DOSAGE_PATTERN = Regex(
    """\b(½|1/2|\d+(?:\.\d+)?)\s*(mg|mcg|micrograms?|ug|g|ml|milliliters?|teaspoons?|tsps?|tsp|tablespoons?|tbsps?|tbsp|iu|international\s+units?|units?|tablets?|tabs?|capsules?|caps?|drops?|puffs?|sprays?|doses?|dose)\b""",
    RegexOption.IGNORE_CASE
)
private val HYDRATION_TARGET_PATTERN = Regex(
    """\b\d+(?:\.\d+)?\s*(?:cups?|oz|ounces?|liters?|litres?|l|ml|milliliters?)\b""",
    RegexOption.IGNORE_CASE
)
private val EXPLICIT_TIME_PATTERN = Regex(
    """\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""",
    RegexOption.IGNORE_CASE
)
private val MEDICATION_CAPTURE_PREFIX_PATTERN = Regex(
    """(?i)^\s*(?:can you\s+)?(?:take|track|add|log|remind me to take|set a reminder to take|remember to take|add medication:?|add medicine:?|medication:?|medicine:?|med:?|pill:?)\s+"""
)
private val MEDICATION_CONTEXT_NOISE_PATTERN = Regex(
    """(?i)\b(?:with food|before bed|after food|after meal|with breakfast|with lunch|with dinner|after breakfast|after lunch|after dinner|at breakfast|at lunch|at dinner|at\s+(?:noon|midnight|\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?))|\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)|breakfast|lunch|dinner|morning|noon|afternoon|evening|night|nightly|bedtime|daily|every day|every\s+(?:6|six|8|eight|12|twelve)\s+hours?|once daily|twice daily|twice|(?:2|two|3|three|4|four)\s*(?:x|times?)\s*/?\s*(?:(?:a|per)\s+)?(?:day|daily)|three times daily|3 times daily|four times daily|4 times daily|as needed|prn|weekly|every other day|weekday|workday)\b"""
)
private val MEDICATION_INDICATION_PATTERN = Regex(
    """(?i)\bfor\s+((?:headache|migraine|pain|allerg(?:y|ies)|sleep|nausea|heartburn|cough|cold|fever|inflammation|anxiety|asthma|rescue|itching|rash)\b(?:\s+[a-z]{2,24}){0,3})"""
)
private val AS_NEEDED_SYMPTOM_HINTS = listOf(
    "headache",
    "migraine",
    "pain",
    "allergy",
    "allergies",
    "fever",
    "rash",
    "itching",
    "cough",
    "cold",
    "heartburn",
    "nausea"
)
private val MEDICATION_SUPPLY_PATTERN = Regex(
    """(?i)\b(\d{1,4})\s*(?:doses?|tablets?|tabs?|capsules?|caps?|puffs?|sprays?)?\s*(?:left|remaining|remain|supply|refill)\b"""
)
private val MEDICATION_NOTE_PATTERN = Regex(
    """(?i)\b(?:note|notes|instruction|instructions):?\s+(.+)$"""
)
private val ROUTINE_CAPTURE_PREFIX_PATTERN = Regex(
    """(?i)^\s*(?:can you\s+)?(?:habit:?|add habit:?|create habit:?|track|do|build|start|practice|remind me to|set a reminder to|remember to)\s+"""
)
private val ROUTINE_TITLE_NOISE_PATTERN = Regex(
    """(?i)\b(?:daily|every day|(?:(?:once|twice)|(?:(?:two|three|four|five|six|\d+)\s+times?))\s+(?:(?:a|per)\s+day|daily)|(?:\d+|two|three|four|five|six)x\s*/?\s*(?:day|daily)|weekdays?|workdays?|weekends?|weekly|monthly|every other day|(?:mon|monday)\s*[/,\s]\s*(?:wed|wednesday)\s*[/,\s]\s*(?:fri|friday)|(?:tue|tues|tuesday)\s*[/,\s]\s*(?:thu|thur|thurs|thursday)|monday\s+wednesday\s+friday|tuesday\s+thursday|(?:2|two|3|three|4|four|5|five|6|six)\s*(?:x|times?)\s*(?:a|per)?\s*week|once\s*(?:a|per)?\s*week|twice\s*(?:a|per)?\s*week|once\s*(?:a|per)?\s*month|morning|midday|lunch|afternoon|evening|night|bedtime|all day|at\s+(?:noon|midnight|\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)?)|\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?))\b"""
)
