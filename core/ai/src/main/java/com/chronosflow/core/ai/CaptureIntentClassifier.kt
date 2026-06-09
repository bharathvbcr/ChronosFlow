package com.chronosflow.core.ai

import java.util.Locale

enum class CaptureIntentType {
    TASK,
    MEDICATION,
    HABIT,
    FOCUS
}

data class CaptureIntentSuggestion(
    val type: CaptureIntentType,
    val score: Int,
    val reason: String
)

object CaptureIntentClassifier {
    fun classify(text: String): List<CaptureIntentSuggestion> {
        val normalized = text.lowercase(Locale.getDefault()).trim()
        if (normalized.length < MIN_CAPTURE_LENGTH && normalized !in SHORT_MEDICATION_CAPTURE_TERMS) return emptyList()

        val hasMedicationCadence = MEDICATION_CADENCE_PATTERN.containsMatchIn(normalized)
        val hasDailyFrequencyCadence = DAILY_FREQUENCY_CADENCE_PATTERN.containsMatchIn(normalized)
        val hasHabitAppAssist = HABIT_APP_ASSIST_PATTERN.containsMatchIn(normalized)
        val hasHabitCadence = (
            HABIT_CADENCE_PATTERN.containsMatchIn(normalized) ||
                hasDailyFrequencyCadence
            ) && !hasMedicationCadence
        val hasOneOffChoreTask = !hasHabitCadence && ONE_OFF_CHORE_TASK_PATTERN.containsMatchIn(normalized)
        val hasPurchaseTask = PURCHASE_TASK_PATTERN.containsMatchIn(normalized)
        val hasPriorityTask = TASK_PRIORITY_PATTERN.containsMatchIn(normalized)
        val hasTaskAppAction = TASK_APP_ACTION_PATTERN.containsMatchIn(normalized)
        val hasFocusDuration = FOCUS_DURATION_PATTERN.containsMatchIn(normalized)
        val hasHydrationContext = HYDRATION_CONTEXT_PATTERN.containsMatchIn(normalized)
        val hasHydrationCapture = !hasOneOffChoreTask && (
            hasHydrationContext ||
                HYDRATION_GLASS_TARGET_PATTERN.containsMatchIn(normalized) ||
                HYDRATION_NON_ML_TARGET_PATTERN.containsMatchIn(normalized) ||
                (hasHydrationContext && HYDRATION_ML_TARGET_PATTERN.containsMatchIn(normalized))
            )
        val oneOffChoreScore = if (hasOneOffChoreTask) 8 else 0

        val medicationCoreScore = scoreFor(normalized, MEDICATION_STRONG_HINTS, 4) +
            if (MEDICATION_DOSE_PATTERN.containsMatchIn(normalized) && !hasHydrationCapture) 6 else 0
        val medicationScore = medicationCoreScore +
            (if (medicationCoreScore > 0) scoreFor(normalized, MEDICATION_WEAK_HINTS, 2) else 0) +
            (if (medicationCoreScore > 0 && (hasHabitCadence || hasMedicationCadence || hasDailyFrequencyCadence)) 6 else 0)
        val habitScore = scoreFor(normalized, HABIT_STRONG_HINTS, 4) +
            scoreFor(normalized, HABIT_WEAK_HINTS, 2) +
            (if (hasHydrationCapture) 6 else 0) +
            (if (hasHabitAppAssist) 6 else 0) +
            (if (hasHabitCadence) 5 else 0)
        val taskScore = scoreFor(normalized, TASK_STRONG_HINTS, 4) +
            scoreFor(normalized, TASK_WEAK_HINTS, 2) +
            (if (TASK_TIME_PATTERN.containsMatchIn(normalized)) 2 else 0) +
            oneOffChoreScore +
            (if (hasPurchaseTask) 7 else 0) +
            (if (hasPriorityTask) 6 else 0) +
            (if (hasTaskAppAction) 6 else 0)
        val focusScore = scoreFor(normalized, FOCUS_STRONG_HINTS, 5) +
            scoreFor(normalized, FOCUS_WEAK_HINTS, 2) +
            (if (hasFocusDuration) 5 else 0)

        val adjustedTaskScore = if (medicationScore == 0 && habitScore == 0 && taskScore == 0 && focusScore == 0) {
            1
        } else {
            taskScore
        }

        return listOf(
            CaptureIntentSuggestion(
                type = CaptureIntentType.MEDICATION,
                score = medicationScore,
                reason = "Medication wording, dose, or reminder details detected."
            ),
            CaptureIntentSuggestion(
                type = CaptureIntentType.HABIT,
                score = habitScore,
                reason = "Repeatable routine, cadence, or wellness habit wording detected."
            ),
            CaptureIntentSuggestion(
                type = CaptureIntentType.FOCUS,
                score = focusScore,
                reason = "Protected focus window, duration, or deep-work wording detected."
            ),
            CaptureIntentSuggestion(
                type = CaptureIntentType.TASK,
                score = adjustedTaskScore,
                reason = "One-off action, follow-up, or general capture wording detected."
            )
        )
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<CaptureIntentSuggestion> { it.score }.thenBy { it.type.name })
            .take(MAX_CAPTURE_SUGGESTIONS)
    }

    private fun scoreFor(text: String, hints: List<String>, weight: Int): Int {
        return hints.count { hint ->
            if (hint.any { it.isWhitespace() }) {
                hint in text
            } else {
                Regex("""\b${Regex.escape(hint)}\b""").containsMatchIn(text)
            }
        } * weight
    }

    private const val MIN_CAPTURE_LENGTH = 3
    private const val MAX_CAPTURE_SUGGESTIONS = 3
    private val SHORT_MEDICATION_CAPTURE_TERMS = setOf("d3", "rx")

    private val MEDICATION_STRONG_HINTS = listOf(
        "med",
        "meds",
        "medication",
        "medicine",
        "pill",
        "tablet",
        "capsule",
        "cream",
        "ointment",
        "gel",
        "topical",
        "patch",
        "liquid",
        "syrup",
        "solution",
        "drop",
        "drops",
        "teaspoon",
        "tsp",
        "tablespoon",
        "tbsp",
        "dose",
        "dosage",
        "vitamin",
        "supplement",
        "magnesium",
        "iron",
        "calcium",
        "zinc",
        "d3",
        "vitamin d",
        "vit d",
        "vitamin c",
        "vit c",
        "probiotic",
        "cbd",
        "inhaler",
        "insulin",
        "metformin",
        "aspirin",
        "ibuprofen",
        "advil",
        "acetaminophen",
        "tylenol",
        "naproxen",
        "aleve",
        "lisinopril",
        "levothyroxine",
        "melatonin",
        "b12",
        "omega",
        "antibiotic",
        "allergy",
        "allergies",
        "antihistamine",
        "zyrtec",
        "claritin",
        "cetirizine",
        "loratadine",
        "benadryl",
        "thyroid",
        "antacid",
        "rescue",
        "rx",
        "refill",
        "prescription",
        "pharmacy",
        "injection",
        "inject",
        "shot",
        "syringe",
        "pen needle",
        "epipen",
        "epi pen",
        "epinephrine",
        "pharmacy pickup",
        "pick up prescription",
        "pickup prescription",
        "renew prescription",
        "refill prescription",
        "with food",
        "with breakfast",
        "with lunch",
        "with dinner",
        "after food",
        "after breakfast",
        "after lunch",
        "after dinner",
        "empty stomach",
        "before bed",
        "bedtime",
        "puff",
        "spray",
        "as needed",
        "prn"
    )
    private val MEDICATION_WEAK_HINTS = listOf(
        "take",
        "morning",
        "night",
        "nightly",
        "breakfast",
        "dinner",
        "headache",
        "migraine",
        "pain",
        "sleep",
        "fever"
    )

    private val HABIT_STRONG_HINTS = listOf(
        "habit",
        "routine",
        "streak",
        "gym",
        "workout",
        "workouts",
        "work out",
        "working out",
        "run",
        "running",
        "jog",
        "jogging",
        "walk",
        "strength",
        "training",
        "yoga",
        "swim",
        "swimming",
        "bike",
        "cycling",
        "cycle",
        "cardio",
        "stretch",
        "mobility",
        "breathing",
        "breath",
        "floss",
        "flossing",
        "brush teeth",
        "brush my teeth",
        "brushing teeth",
        "brushing my teeth",
        "dental",
        "meal prep",
        "meal planning",
        "prep meals",
        "nutrition",
        "healthy eating",
        "eat breakfast",
        "eat lunch",
        "eat dinner",
        "eat vegetables",
        "eat fruit",
        "protein",
        "reduce caffeine",
        "cut caffeine",
        "limit caffeine",
        "cut sugar",
        "reduce sugar",
        "limit sugar",
        "meditate",
        "journal",
        "hydrate",
        "water",
        "cups",
        "ounces",
        "oz",
        "liters",
        "litres",
        "read",
        "sleep",
        "sleep routine",
        "bedtime",
        "wake up",
        "wind down",
        "screen free",
        "screen-free",
        "steps",
        "pilates",
        "weights",
        "lift",
        "lifting",
        "practice",
        "study",
        "learn",
        "learning",
        "language practice",
        "spanish",
        "guitar",
        "piano",
        "coding practice"
    )
    private val HABIT_WEAK_HINTS = listOf("daily", "nightly", "weekly", "weekdays", "weekends", "morning", "evening")

    private val FOCUS_STRONG_HINTS = listOf(
        "focus",
        "focus session",
        "deep work",
        "deep-work",
        "protected work",
        "protected focus",
        "pomodoro",
        "flow block"
    )
    private val FOCUS_WEAK_HINTS = listOf(
        "timer",
        "concentrate",
        "concentration",
        "distraction free",
        "heads down",
        "work block"
    )

    private val TASK_STRONG_HINTS = listOf(
        "task",
        "todo",
        "call",
        "email",
        "text",
        "message",
        "remind",
        "reminder",
        "ask",
        "tell",
        "follow up",
        "reply",
        "send",
        "pay",
        "bill",
        "invoice",
        "rent",
        "utility bill",
        "utilities",
        "subscription",
        "credit card",
        "buy",
        "shop",
        "order",
        "grocery",
        "groceries",
        "package",
        "ship",
        "shipping",
        "mail",
        "post office",
        "ups",
        "fedex",
        "usps",
        "return label",
        "appointment",
        "appt",
        "doctor",
        "dentist",
        "clinic",
        "checkup",
        "physical",
        "exam",
        "therapy",
        "therapist",
        "pediatrician",
        "optometrist",
        "lab",
        "blood test",
        "vaccination",
        "vaccine",
        "invite",
        "meeting invite",
        "calendar invite",
        "save the date",
        "send invite",
        "send calendar invite",
        "send meeting invite",
        "event invite",
        "rsvp",
        "accept invite",
        "decline invite",
        "birthday",
        "anniversary",
        "gift",
        "present",
        "birthday gift",
        "birthday card",
        "party",
        "celebration",
        "errand",
        "drive to",
        "go to",
        "directions to",
        "navigate to",
        "meet at",
        "book",
        "renew",
        "fix",
        "repair",
        "clean",
        "laundry",
        "wash clothes",
        "dishes",
        "dishwasher",
        "trash",
        "plants",
        "water plants",
        "return",
        "bring",
        "bring to",
        "deliver",
        "deliver to",
        "visit",
        "take to",
        "review",
        "submit",
        "document",
        "doc",
        "pdf",
        "file",
        "attachment",
        "brief",
        "proposal",
        "link",
        "url",
        "website",
        "webpage",
        "http",
        "https",
        "www",
        "pick up",
        "pick up from",
        "pickup",
        "drop off",
        "drop off at",
        "schedule",
        "reschedule",
        "cancel",
        "meeting"
    )
    private val TASK_WEAK_HINTS = listOf("today", "tomorrow", "tonight", "deadline", "due", "urgent")

    private val MEDICATION_DOSE_PATTERN = Regex(
        """\b(?:\d+(?:\.\d+)?|1/2|½)\s*(?:mg|mcg|g|ml|teaspoons?|tsps?|tsp|tablespoons?|tbsps?|tbsp|iu|units?|tablets?|tabs?|capsules?|caps?|drops?|puffs?|sprays?|doses?)\b"""
    )
    private val HYDRATION_CONTEXT_PATTERN = Regex(
        """\b(?:water|hydrate|hydration|drink|drinking|fluids?)\b"""
    )
    private val HYDRATION_GLASS_TARGET_PATTERN = Regex(
        """\b(?:\d+(?:\.\d+)?\s*glasses?\s+(?:of\s+)?water|drink(?:ing)?\s+\d+(?:\.\d+)?\s*glasses?)\b"""
    )
    private val HYDRATION_NON_ML_TARGET_PATTERN = Regex(
        """\b\d+(?:\.\d+)?\s*(?:cups?|oz|ounces?|liters?|litres?|l)\b"""
    )
    private val HYDRATION_ML_TARGET_PATTERN = Regex(
        """\b\d+(?:\.\d+)?\s*(?:ml|milliliters?)\b"""
    )
    private val MEDICATION_CADENCE_PATTERN = Regex(
        """\bevery\s+(?:6|six|8|eight|12|twelve)\s+hours?\b"""
    )
    private val DAILY_FREQUENCY_CADENCE_PATTERN = Regex(
        """\b(?:(?:once|twice)|(?:(?:two|three|four|five|six|\d+)\s+times?))\s+(?:(?:a|per)\s+day|daily)\b|\b(?:\d+|two|three|four|five|six)x\s*/?\s*(?:day|daily)\b"""
    )
    private val HABIT_CADENCE_PATTERN = Regex(
        """\b(?:\d+\s*x\s*/?\s*(?:week|wk)|daily|nightly|weekly|monthly|weekdays?|weekends?|every\s+(?:day|weekday|weekend|morning|night|week|month|other\s+day|\w+)|twice\s+(?:a\s+|per\s+)?week|(?:three|four|five|six)\s+times\s+(?:a\s+|per\s+)?week|mon(?:day)?\s+wed(?:nesday)?\s+fri(?:day)?|tue(?:sday)?\s+thu(?:rsday)?)\b"""
    )
    private val TASK_TIME_PATTERN = Regex(
        """\b(?:today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next\s+\w+|this\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)|by\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)|on\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)|at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b"""
    )
    private val FOCUS_DURATION_PATTERN = Regex(
        """\b(?:\d+(?:\.\d+)?\s*(?:m|min|mins|minutes?|h|hr|hrs|hours?)|(?:half|one|two|three)\s+hours?)\b"""
    )
    private val ONE_OFF_CHORE_TASK_PATTERN = Regex(
        """\b(?:water\s+(?:the\s+)?(?:plants|garden|lawn)|walk\s+(?:the\s+)?dog|feed\s+(?:the\s+)?(?:dog|cat|pet)|take\s+out\s+(?:the\s+)?trash|trash\s+out)\b"""
    )
    private val PURCHASE_TASK_PATTERN = Regex(
        """\b(?:buy|purchase|shop\s+for|order|pick\s+up|pickup|return|ship)\b"""
    )
    private val TASK_PRIORITY_PATTERN = Regex(
        """\b(?:urgent|high\s+priority|important|asap|critical)\b"""
    )
    private val TASK_APP_ACTION_PATTERN = Regex(
        """\b(?:open|launch|start|join|check|review)\s+(?:spotify|youtube|you\s+tube|zoom|teams|microsoft\s+teams|slack|notion|gmail|google\s+calendar|google\s+drive|google\s+docs|google\s+sheets|google\s+slides|google\s+maps|maps)\b"""
    )
    private val HABIT_APP_ASSIST_PATTERN = Regex(
        """\b(?:with|using|in|on)\s+(?:duolingo|strava|headspace|calm|spotify|youtube|you\s+tube|google\s+fit|fitbit|myfitnesspal|my\s+fitness\s+pal)\b"""
    )
}
