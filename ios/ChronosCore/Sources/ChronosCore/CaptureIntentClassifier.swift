import Foundation

// CaptureIntentClassifier — scores a typed quick-capture as a task / medication / habit / focus
// entity so the shell can route it to the right editor.
//
// Platform-agnostic (Foundation-only) port of the Android classifier
// (`core/ai/.../CaptureIntentClassifier.kt`): the hint lists, regex patterns, and scoring weights
// are byte-mirrored so both apps rank the same capture the same way. Pure and deterministic —
// no wall-clock reads, no SwiftUI / UIKit — so it unit-tests off-device.

/// The entity a typed capture most likely represents. Mirrors Android's `CaptureIntentType`;
/// raw values match the Kotlin enum names, and the score tie-break sorts by this name.
public enum CaptureIntentType: String, CaseIterable, Sendable {
    case task = "TASK"
    case medication = "MEDICATION"
    case habit = "HABIT"
    case focus = "FOCUS"
}

/// One ranked classification with the score and a human-readable reason for the suggestion chip.
public struct CaptureIntentSuggestion: Sendable, Equatable {
    public let type: CaptureIntentType
    public let score: Int
    public let reason: String

    public init(type: CaptureIntentType, score: Int, reason: String) {
        self.type = type
        self.score = score
        self.reason = reason
    }
}

public enum CaptureIntentClassifier {

    /// Ranked suggestions for `text`, highest score first (score ties break on the type name,
    /// matching Kotlin's `thenBy { it.type.name }`). Empty for captures shorter than 3 characters
    /// unless the capture is itself a known short medication term ("d3" / "rx").
    public static func classify(_ text: String) -> [CaptureIntentSuggestion] {
        let normalized = text.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if normalized.count < minCaptureLength && !shortMedicationCaptureTerms.contains(normalized) {
            return []
        }

        let hasMedicationCadence = matches(medicationCadencePattern, normalized)
        let hasDailyFrequencyCadence = matches(dailyFrequencyCadencePattern, normalized)
        let hasHabitAppAssist = matches(habitAppAssistPattern, normalized)
        let hasHabitCadence = (matches(habitCadencePattern, normalized) || hasDailyFrequencyCadence)
            && !hasMedicationCadence
        let hasOneOffChoreTask = !hasHabitCadence && matches(oneOffChoreTaskPattern, normalized)
        let hasPurchaseTask = matches(purchaseTaskPattern, normalized)
        let hasPriorityTask = matches(taskPriorityPattern, normalized)
        let hasTaskAppAction = matches(taskAppActionPattern, normalized)
        let hasFocusDuration = matches(focusDurationPattern, normalized)
        let hasHydrationContext = matches(hydrationContextPattern, normalized)
        let hasHydrationCapture = !hasOneOffChoreTask && (
            hasHydrationContext
                || matches(hydrationGlassTargetPattern, normalized)
                || matches(hydrationNonMLTargetPattern, normalized)
                || (hasHydrationContext && matches(hydrationMLTargetPattern, normalized))
        )
        let oneOffChoreScore = hasOneOffChoreTask ? 8 : 0

        let medicationCoreScore = score(normalized, hints: medicationStrongHints, weight: 4)
            + ((matches(medicationDosePattern, normalized) && !hasHydrationCapture) ? 6 : 0)
        let medicationScore = medicationCoreScore
            + (medicationCoreScore > 0 ? score(normalized, hints: medicationWeakHints, weight: 2) : 0)
            + ((medicationCoreScore > 0 && (hasHabitCadence || hasMedicationCadence || hasDailyFrequencyCadence)) ? 6 : 0)
        let habitScore = score(normalized, hints: habitStrongHints, weight: 4)
            + score(normalized, hints: habitWeakHints, weight: 2)
            + (hasHydrationCapture ? 6 : 0)
            + (hasHabitAppAssist ? 6 : 0)
            + (hasHabitCadence ? 5 : 0)
        let taskScore = score(normalized, hints: taskStrongHints, weight: 4)
            + score(normalized, hints: taskWeakHints, weight: 2)
            + (matches(taskTimePattern, normalized) ? 2 : 0)
            + oneOffChoreScore
            + (hasPurchaseTask ? 7 : 0)
            + (hasPriorityTask ? 6 : 0)
            + (hasTaskAppAction ? 6 : 0)
        let focusScore = score(normalized, hints: focusStrongHints, weight: 5)
            + score(normalized, hints: focusWeakHints, weight: 2)
            + (hasFocusDuration ? 5 : 0)

        // A capture nothing recognises still routes somewhere: task is the fallback entity.
        let adjustedTaskScore = (medicationScore == 0 && habitScore == 0 && taskScore == 0 && focusScore == 0)
            ? 1
            : taskScore

        return [
            CaptureIntentSuggestion(
                type: .medication,
                score: medicationScore,
                reason: "Medication wording, dose, or reminder details detected."),
            CaptureIntentSuggestion(
                type: .habit,
                score: habitScore,
                reason: "Repeatable routine, cadence, or wellness habit wording detected."),
            CaptureIntentSuggestion(
                type: .focus,
                score: focusScore,
                reason: "Protected focus window, duration, or deep-work wording detected."),
            CaptureIntentSuggestion(
                type: .task,
                score: adjustedTaskScore,
                reason: "One-off action, follow-up, or general capture wording detected."),
        ]
        .filter { $0.score > 0 }
        .sorted { lhs, rhs in
            if lhs.score != rhs.score { return lhs.score > rhs.score }
            return lhs.type.rawValue < rhs.type.rawValue
        }
        .prefix(maxCaptureSuggestions)
        .map { $0 }
    }

    // MARK: Scoring

    /// Count of matching hints × weight. Multi-word hints match as substrings; single-word hints
    /// require word boundaries so e.g. "run" never fires inside "brunch" (mirrors Kotlin `scoreFor`).
    private static func score(_ text: String, hints: [String], weight: Int) -> Int {
        hints.filter { hint in
            if hint.contains(where: \.isWhitespace) {
                return text.contains(hint)
            }
            let pattern = "\\b\(NSRegularExpression.escapedPattern(for: hint))\\b"
            return text.range(of: pattern, options: .regularExpression) != nil
        }.count * weight
    }

    private static func matches(_ regex: NSRegularExpression, _ text: String) -> Bool {
        regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    private static func regex(_ pattern: String) -> NSRegularExpression {
        // Patterns are compile-time constants mirrored from Android; a failure is programmer error.
        try! NSRegularExpression(pattern: pattern)
    }

    // MARK: Constants (byte-mirrored from the Kotlin classifier)

    private static let minCaptureLength = 3
    private static let maxCaptureSuggestions = 3
    private static let shortMedicationCaptureTerms: Set<String> = ["d3", "rx"]

    private static let medicationStrongHints = [
        "med", "meds", "medication", "medicine", "pill", "tablet", "capsule", "cream", "ointment",
        "gel", "topical", "patch", "liquid", "syrup", "solution", "drop", "drops", "teaspoon",
        "tsp", "tablespoon", "tbsp", "dose", "dosage", "vitamin", "supplement", "magnesium",
        "iron", "calcium", "zinc", "d3", "vitamin d", "vit d", "vitamin c", "vit c", "probiotic",
        "cbd", "inhaler", "insulin", "metformin", "aspirin", "ibuprofen", "advil", "acetaminophen",
        "tylenol", "naproxen", "aleve", "lisinopril", "levothyroxine", "melatonin", "b12", "omega",
        "antibiotic", "allergy", "allergies", "antihistamine", "zyrtec", "claritin", "cetirizine",
        "loratadine", "benadryl", "thyroid", "antacid", "rescue", "rx", "refill", "prescription",
        "pharmacy", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen",
        "epinephrine", "pharmacy pickup", "pick up prescription", "pickup prescription",
        "renew prescription", "refill prescription", "with food", "with breakfast", "with lunch",
        "with dinner", "after food", "after breakfast", "after lunch", "after dinner",
        "empty stomach", "before bed", "bedtime", "puff", "spray", "as needed", "prn",
    ]
    private static let medicationWeakHints = [
        "take", "morning", "night", "nightly", "breakfast", "dinner", "headache", "migraine",
        "pain", "sleep", "fever",
    ]

    private static let habitStrongHints = [
        "habit", "routine", "streak", "gym", "workout", "workouts", "work out", "working out",
        "run", "running", "jog", "jogging", "walk", "strength", "training", "yoga", "swim",
        "swimming", "bike", "cycling", "cycle", "cardio", "stretch", "mobility", "breathing",
        "breath", "floss", "flossing", "brush teeth", "brush my teeth", "brushing teeth",
        "brushing my teeth", "dental", "meal prep", "meal planning", "prep meals", "nutrition",
        "healthy eating", "eat breakfast", "eat lunch", "eat dinner", "eat vegetables",
        "eat fruit", "protein", "reduce caffeine", "cut caffeine", "limit caffeine", "cut sugar",
        "reduce sugar", "limit sugar", "meditate", "journal", "hydrate", "water", "cups",
        "ounces", "oz", "liters", "litres", "read", "sleep", "sleep routine", "bedtime",
        "wake up", "wind down", "screen free", "screen-free", "steps", "pilates", "weights",
        "lift", "lifting", "practice", "study", "learn", "learning", "language practice",
        "spanish", "guitar", "piano", "coding practice",
    ]
    private static let habitWeakHints = [
        "daily", "nightly", "weekly", "weekdays", "weekends", "morning", "evening",
    ]

    private static let focusStrongHints = [
        "focus", "focus session", "deep work", "deep-work", "protected work", "protected focus",
        "pomodoro", "flow block",
    ]
    private static let focusWeakHints = [
        "timer", "concentrate", "concentration", "distraction free", "heads down", "work block",
    ]

    private static let taskStrongHints = [
        "task", "todo", "call", "email", "text", "message", "remind", "reminder", "ask", "tell",
        "follow up", "reply", "send", "pay", "bill", "invoice", "rent", "utility bill",
        "utilities", "subscription", "credit card", "buy", "shop", "order", "grocery",
        "groceries", "package", "ship", "shipping", "mail", "post office", "ups", "fedex",
        "usps", "return label", "appointment", "appt", "doctor", "dentist", "clinic", "checkup",
        "physical", "exam", "therapy", "therapist", "pediatrician", "optometrist", "lab",
        "blood test", "vaccination", "vaccine", "invite", "meeting invite", "calendar invite",
        "save the date", "send invite", "send calendar invite", "send meeting invite",
        "event invite", "rsvp", "accept invite", "decline invite", "birthday", "anniversary",
        "gift", "present", "birthday gift", "birthday card", "party", "celebration", "errand",
        "drive to", "go to", "directions to", "navigate to", "meet at", "book", "renew", "fix",
        "repair", "clean", "laundry", "wash clothes", "dishes", "dishwasher", "trash", "plants",
        "water plants", "return", "bring", "bring to", "deliver", "deliver to", "visit",
        "take to", "review", "submit", "document", "doc", "pdf", "file", "attachment", "brief",
        "proposal", "link", "url", "website", "webpage", "http", "https", "www", "pick up",
        "pick up from", "pickup", "drop off", "drop off at", "schedule", "reschedule", "cancel",
        "meeting",
    ]
    private static let taskWeakHints = [
        "today", "tomorrow", "tonight", "deadline", "due", "urgent",
    ]

    private static let medicationDosePattern = regex(
        #"\b(?:\d+(?:\.\d+)?|1/2|½)\s*(?:mg|mcg|g|ml|teaspoons?|tsps?|tsp|tablespoons?|tbsps?|tbsp|iu|units?|tablets?|tabs?|capsules?|caps?|drops?|puffs?|sprays?|doses?)\b"#
    )
    private static let hydrationContextPattern = regex(
        #"\b(?:water|hydrate|hydration|drink|drinking|fluids?)\b"#
    )
    private static let hydrationGlassTargetPattern = regex(
        #"\b(?:\d+(?:\.\d+)?\s*glasses?\s+(?:of\s+)?water|drink(?:ing)?\s+\d+(?:\.\d+)?\s*glasses?)\b"#
    )
    private static let hydrationNonMLTargetPattern = regex(
        #"\b\d+(?:\.\d+)?\s*(?:cups?|oz|ounces?|liters?|litres?|l)\b"#
    )
    private static let hydrationMLTargetPattern = regex(
        #"\b\d+(?:\.\d+)?\s*(?:ml|milliliters?)\b"#
    )
    private static let medicationCadencePattern = regex(
        #"\bevery\s+(?:6|six|8|eight|12|twelve)\s+hours?\b"#
    )
    private static let dailyFrequencyCadencePattern = regex(
        #"\b(?:(?:once|twice)|(?:(?:two|three|four|five|six|\d+)\s+times?))\s+(?:(?:a|per)\s+day|daily)\b|\b(?:\d+|two|three|four|five|six)x\s*/?\s*(?:day|daily)\b"#
    )
    private static let habitCadencePattern = regex(
        #"\b(?:\d+\s*x\s*/?\s*(?:week|wk)|daily|nightly|weekly|monthly|weekdays?|weekends?|every\s+(?:day|weekday|weekend|morning|night|week|month|other\s+day|\w+)|twice\s+(?:a\s+|per\s+)?week|(?:three|four|five|six)\s+times\s+(?:a\s+|per\s+)?week|mon(?:day)?\s+wed(?:nesday)?\s+fri(?:day)?|tue(?:sday)?\s+thu(?:rsday)?)\b"#
    )
    private static let taskTimePattern = regex(
        #"\b(?:today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next\s+\w+|this\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)|by\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)|on\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)|at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b"#
    )
    private static let focusDurationPattern = regex(
        #"\b(?:\d+(?:\.\d+)?\s*(?:m|min|mins|minutes?|h|hr|hrs|hours?)|(?:half|one|two|three)\s+hours?)\b"#
    )
    private static let oneOffChoreTaskPattern = regex(
        #"\b(?:water\s+(?:the\s+)?(?:plants|garden|lawn)|walk\s+(?:the\s+)?dog|feed\s+(?:the\s+)?(?:dog|cat|pet)|take\s+out\s+(?:the\s+)?trash|trash\s+out)\b"#
    )
    private static let purchaseTaskPattern = regex(
        #"\b(?:buy|purchase|shop\s+for|order|pick\s+up|pickup|return|ship)\b"#
    )
    private static let taskPriorityPattern = regex(
        #"\b(?:urgent|high\s+priority|important|asap|critical)\b"#
    )
    private static let taskAppActionPattern = regex(
        #"\b(?:open|launch|start|join|check|review)\s+(?:spotify|youtube|you\s+tube|zoom|teams|microsoft\s+teams|slack|notion|gmail|google\s+calendar|google\s+drive|google\s+docs|google\s+sheets|google\s+slides|google\s+maps|maps)\b"#
    )
    private static let habitAppAssistPattern = regex(
        #"\b(?:with|using|in|on)\s+(?:duolingo|strava|headspace|calm|spotify|youtube|you\s+tube|google\s+fit|fitbit|myfitnesspal|my\s+fitness\s+pal)\b"#
    )
}
