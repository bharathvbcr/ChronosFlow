import Foundation

// Medication form assist — port of the deterministic (LOCAL) heuristics in Android's
// `MedicationAssistPlanner` (core/ai RoutineAssistPlanner.kt): structured details, reminder
// timing, meal-timing, form, supply, and note suggestions parsed from the typed capture.
// Organizational only — never recommends medications, dosage changes, or clinical advice.
// The Android Reminder suggestion carries primary/secondary minutes plus a frequency label that
// the form expands via `applyEvenMedicationSpacing`; the iOS editor stores a flat reminder-minute
// array, so `.reminders` carries the already-evenly-spaced set instead.

public struct MedicationAssistInput: Sendable, Equatable {
    public let name: String
    public let dosage: String
    public let unit: String
    public let notes: String
    /// The form's first reminder time, used to anchor twice/3x/4x-daily spacing.
    public let primaryReminderMinute: Int
    public let reminderCount: Int
    public let takeWithFood: Bool
    public let tracksSupply: Bool

    public init(
        name: String, dosage: String = "", unit: String = "", notes: String = "",
        primaryReminderMinute: Int = 8 * 60, reminderCount: Int = 1,
        takeWithFood: Bool = false, tracksSupply: Bool = false
    ) {
        self.name = name
        self.dosage = dosage
        self.unit = unit
        self.notes = notes
        self.primaryReminderMinute = primaryReminderMinute
        self.reminderCount = reminderCount
        self.takeWithFood = takeWithFood
        self.tracksSupply = tracksSupply
    }
}

public struct MedicationAssistSuggestion: Identifiable, Sendable, Equatable {
    /// The form fields an accepted suggestion fills (Android's sealed suggestion payloads).
    public enum Change: Sendable, Equatable {
        /// Structured name/dosage/unit extracted from the capture; nil slots leave the field as-is.
        case details(name: String?, dosage: String?, unit: String?)
        /// Full replacement reminder-minute set (already evenly spaced for multi-dose cadences).
        case reminders([Int])
        /// "With food" meal timing.
        case takeWithFood
        /// Dose unit implied by form wording (drops/liquid/capsule…), which also drives the form icon.
        case unit(String)
        /// Enable supply tracking with this many doses left.
        case trackSupply(dosesLeft: Int)
        /// Free-text note extracted from the capture (indication / pharmacy refill / "note: …").
        case note(String)
    }

    public let id: String
    public let label: String
    public let reason: String
    public let change: Change

    public init(id: String, label: String, reason: String, change: Change) {
        self.id = id
        self.label = label
        self.reason = reason
        self.change = change
    }
}

/// Deterministic suggestions from the current form fields — the iOS analogue of Android's
/// `MedicationAssistPlanner.localSuggestions`. Pure and offline; capped at 6 like Android.
public func medicationAssistSuggestions(_ input: MedicationAssistInput) -> [MedicationAssistSuggestion] {
    let rawText = [
        input.name,
        input.dosage,
        input.dosage.isEmpty ? "" : input.unit,
        input.notes
    ].filter { !$0.isEmpty }.joined(separator: " ")
    let normalized = rawText.lowercased()
    var out: [MedicationAssistSuggestion] = []

    // 1. Structured details (name / dosage / unit) parsed out of the capture.
    if let details = MedicationAssistParser.inferDetails(rawText, input: input) {
        out.append(details)
    }

    // 2. Reminder timing from explicit times, day-part words, and multi-dose cadence wording.
    out.append(contentsOf: MedicationAssistParser.reminderSuggestions(normalized, input: input))

    // 3. Meal timing.
    if !input.takeWithFood, MedicationAssistParser.mentionsFood(normalized) {
        out.append(MedicationAssistSuggestion(
            id: "local:medication:meal_timing:food",
            label: "With food",
            reason: "Matched food-related wording in the plan.",
            change: .takeWithFood))
    }

    // 4. Form wording → dose unit (the unit drives the plan's form icon on iOS).
    if let unit = MedicationAssistParser.inferUnitForm(normalized, currentUnit: input.unit) {
        out.append(unit)
    }

    // 5. Remaining-supply wording → refill tracking.
    if !input.tracksSupply, let left = MedicationAssistParser.inferSupplyCount(rawText) {
        out.append(MedicationAssistSuggestion(
            id: "local:medication:refill:0",
            label: "Track \(left) doses",
            reason: "The capture mentions remaining supply.",
            change: .trackSupply(dosesLeft: left)))
    }

    // 6. Notes: explicit "note:"/indication wording, else pharmacy refill/pickup intent.
    if input.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        if let note = MedicationAssistParser.extractNote(rawText) {
            out.append(MedicationAssistSuggestion(
                id: "local:medication:notes:0",
                label: "Add note",
                reason: "The capture includes a note or instruction.",
                change: .note(note)))
        } else if let refillNote = MedicationAssistParser.pharmacyNote(normalized) {
            out.append(refillNote)
        }
    }

    var seen = Set<String>()
    return out.filter { seen.insert($0.id).inserted }.prefix(6).map { $0 }
}

// MARK: - Parsing internals

enum MedicationAssistParser {

    // Patterns mirroring the Android planner's regexes.
    static let dosagePattern =
        #"\b(½|1/2|\d+(?:\.\d+)?)\s*(mg|mcg|micrograms?|ug|g|ml|milliliters?|teaspoons?|tsps?|tsp|tablespoons?|tbsps?|tbsp|iu|international\s+units?|units?|tablets?|tabs?|capsules?|caps?|drops?|puffs?|sprays?|doses?|dose)\b"#
    static let explicitTimePattern = #"\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b"#
    static let capturePrefixPattern =
        #"^\s*(?:can you\s+)?(?:take|track|add|log|remind me to take|set a reminder to take|remember to take|add medication:?|add medicine:?|medication:?|medicine:?|med:?|pill:?)\s+"#
    static let contextNoisePattern =
        #"\b(?:every\s+(?:morning|noon|afternoon|evening|night|bedtime)|with food|before bed|after food|after meal|with breakfast|with lunch|with dinner|after breakfast|after lunch|after dinner|at breakfast|at lunch|at dinner|at\s+(?:noon|midnight|\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?))|\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)|breakfast|lunch|dinner|morning|noon|afternoon|evening|night|nightly|bedtime|daily|every day|every\s+(?:6|six|8|eight|12|twelve)\s+hours?|once daily|twice daily|twice|(?:2|two|3|three|4|four)\s*(?:x|times?)\s*/?\s*(?:(?:a|per)\s+)?(?:day|daily)|three times daily|3 times daily|four times daily|4 times daily|as needed|prn|weekly|every other day|weekday|workday)\b"#
    static let indicationPattern =
        #"\bfor\s+((?:headache|migraine|pain|allerg(?:y|ies)|sleep|nausea|heartburn|cough|cold|fever|inflammation|anxiety|asthma|rescue|itching|rash)\b(?:\s+[a-z]{2,24}){0,3})"#
    static let supplyPattern =
        #"\b(\d{1,4})\s*(?:doses?|tablets?|tabs?|capsules?|caps?|puffs?|sprays?)?\s*(?:left|remaining|remain|supply|refill)\b"#
    static let notePattern = #"\b(?:note|notes|instruction|instructions):?\s+(.+)$"#

    static let maxNameLength = 72
    static let maxNoteLength = 120

    // MARK: Regex helpers (case-insensitive)

    static func firstMatch(_ pattern: String, in text: String) -> [String?]? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
        else { return nil }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: range) else { return nil }
        var groups: [String?] = []
        for i in 0..<match.numberOfRanges {
            let r = match.range(at: i)
            if r.location == NSNotFound { groups.append(nil) }
            else if let sr = Range(r, in: text) { groups.append(String(text[sr])) }
            else { groups.append(nil) }
        }
        return groups
    }

    static func contains(_ pattern: String, in text: String) -> Bool {
        firstMatch(pattern, in: text) != nil
    }

    static func removingMatches(_ pattern: String, in text: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
        else { return text }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.stringByReplacingMatches(in: text, options: [], range: range, withTemplate: " ")
    }

    // MARK: Details (name / dosage / unit)

    static func inferDetails(_ rawText: String, input: MedicationAssistInput) -> MedicationAssistSuggestion? {
        let dosageMatch = firstMatch(dosagePattern, in: rawText)
        let dosage = dosageMatch?[1].map { $0.replacingOccurrences(of: "1/2", with: "½") }
        let unit = dosageMatch?[2].map(normalizeUnit)
        let name = nameCandidate(rawText)

        // Only suggest when something materially differs from the current fields (Android parity).
        let trim: (String) -> String = { $0.trimmingCharacters(in: .whitespaces) }
        let differs =
            (name != nil && name!.caseInsensitiveCompare(trim(input.name)) != .orderedSame) ||
            (dosage != nil && dosage!.caseInsensitiveCompare(trim(input.dosage)) != .orderedSame) ||
            (unit != nil && unit!.caseInsensitiveCompare(trim(input.unit)) != .orderedSame)
        guard differs else { return nil }

        let label = [name, dosage.map { d in [d, unit].compactMap { $0 }.joined(separator: " ") }]
            .compactMap { $0 }
            .joined(separator: " · ")
        return MedicationAssistSuggestion(
            id: "local:medication:details:0",
            label: label.isEmpty ? "Fill medication details" : label,
            reason: "Extracted structured medication fields from the capture.",
            change: .details(name: name, dosage: dosage, unit: unit))
    }

    /// Clean medication name: strip capture prefixes, dose amounts, indication and timing noise,
    /// then title-case (all-caps for dose-like tokens). Mirrors `smartMedicationNameCandidate`.
    static func nameCandidate(_ text: String) -> String? {
        var candidate = text.trimmingCharacters(in: .whitespaces)
        candidate = removingMatches(capturePrefixPattern, in: candidate)
        candidate = removingMatches(dosagePattern, in: candidate)
        candidate = removingMatches(indicationPattern, in: candidate)
        candidate = removingMatches(contextNoisePattern, in: candidate)
        candidate = candidate.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        candidate = candidate.trimmingCharacters(in: CharacterSet(charactersIn: " .,;-:"))
        candidate = String(candidate.prefix(maxNameLength)).trimmingCharacters(in: .whitespaces)
        guard candidate.count >= 2 else { return nil }
        return candidate.split(separator: " ").map { part -> String in
            let token = String(part)
            let hasDigit = token.contains { $0.isNumber }
            let hasLetter = token.contains { $0.isLetter }
            if token.count == 1 && hasLetter { return token.uppercased() }
            if hasDigit && hasLetter { return token.uppercased() }
            return token.prefix(1).uppercased() + token.dropFirst()
        }.joined(separator: " ")
    }

    static func normalizeUnit(_ raw: String) -> String {
        switch raw.trimmingCharacters(in: .whitespaces).lowercased() {
        case "tablets", "tablet", "tabs", "tab": return "tablet"
        case "capsules", "capsule", "caps": return "capsule"
        case "micrograms", "microgram", "ug": return "mcg"
        case "international units", "international unit", "iu": return "IU"
        case "milliliters", "milliliter", "ml": return "mL"
        case "teaspoons", "teaspoon", "tsps", "tsp": return "tsp"
        case "tablespoons", "tablespoon", "tbsps", "tbsp": return "tbsp"
        case "drops", "drop": return "drop"
        case "doses", "dose", "puffs", "puff", "sprays", "spray": return "dose"
        case "units", "unit": return "unit"
        case let unit: return unit
        }
    }

    // MARK: Reminders

    static func reminderSuggestions(_ normalized: String, input: MedicationAssistInput) -> [MedicationAssistSuggestion] {
        var out: [MedicationAssistSuggestion] = []
        let primary = min(max(input.primaryReminderMinute, 0), 1439)

        func single(_ idSuffix: String, _ label: String, _ minute: Int, _ reason: String) {
            out.append(MedicationAssistSuggestion(
                id: "local:medication:reminder:\(idSuffix)", label: label, reason: reason,
                change: .reminders([min(max(minute, 0), 1439)])))
        }
        /// `count` evenly spaced daily reminders anchored at the form's primary minute
        /// (Android `applyEvenMedicationSpacing`: 2 → 12h, 3 → 8h, 4 → 6h apart).
        func spaced(_ idSuffix: String, _ label: String, _ count: Int, _ reason: String) {
            let step = (24 * 60) / count
            let minutes = (0..<count).map { (primary + $0 * step) % (24 * 60) }.sorted()
            out.append(MedicationAssistSuggestion(
                id: "local:medication:reminder:\(idSuffix)", label: label, reason: reason,
                change: .reminders(minutes)))
        }

        if let minute = explicitTimeMinute(normalized) {
            single("captured_time", "\(clockLabel(minute)) reminder", minute,
                   "The capture names an exact time.")
        }
        if contains(#"\b(bed|night|sleep)"#, in: normalized) {
            single("bedtime", "Bedtime reminder", 21 * 60, "Matched bedtime wording in the plan.")
        }
        if contains(#"\b(morning|breakfast)\b"#, in: normalized) {
            single("morning", "Morning reminder", 8 * 60, "Matched morning wording in the plan.")
        }
        if contains(#"\b(lunch|noon)\b"#, in: normalized) {
            single("noon", "Noon reminder", 12 * 60, "Matched midday wording in the plan.")
        }
        if contains(#"\b(evening|dinner)\b"#, in: normalized) {
            single("evening", "Evening reminder", 18 * 60, "Matched evening wording in the plan.")
        }
        if contains(#"\btwice\b|\btwo times\b|\b2 times\b|\b2x\b|\bevery\s+(?:12|twelve)\s+hours?\b"#, in: normalized),
           input.reminderCount != 2 {
            spaced("twice", "12h spacing (2 reminders)", 2, "Matched twice-daily wording in the plan.")
        }
        if contains(#"\b3 times\b|\bthree times\b|\b3x\b|\bevery\s+(?:8|eight)\s+hours?\b"#, in: normalized),
           input.reminderCount != 3 {
            spaced("three_times", "3 daily reminders", 3, "Matched three-times-daily wording in the plan.")
        }
        if contains(#"\b4 times\b|\bfour times\b|\b4x\b|\bevery\s+(?:6|six)\s+hours?\b"#, in: normalized),
           input.reminderCount != 4 {
            spaced("four_times", "4 daily reminders", 4, "Matched four-times-daily wording in the plan.")
        }
        return out
    }

    /// "at 9pm" / "8:30 am" → minute-of-day. Mirrors Android's `explicitTimeStartMinute`.
    static func explicitTimeMinute(_ normalized: String) -> Int? {
        guard let groups = firstMatch(explicitTimePattern, in: normalized),
              let hour = groups[1].flatMap({ Int($0) }) else { return nil }
        let minute = groups[2].flatMap { Int($0) } ?? 0
        let period = groups[3] ?? ""
        guard (1...12).contains(hour), (0...59).contains(minute) else { return nil }
        let hour24: Int
        if period.hasPrefix("p") && hour != 12 { hour24 = hour + 12 }
        else if period.hasPrefix("a") && hour == 12 { hour24 = 0 }
        else { hour24 = hour }
        return hour24 * 60 + minute
    }

    static func clockLabel(_ minute: Int) -> String {
        let normalized = min(max(minute, 0), 1439)
        let hour24 = normalized / 60
        let minutePart = normalized % 60
        let hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12
        let period = hour24 < 12 ? "AM" : "PM"
        return String(format: "%d:%02d %@", hour12, minutePart, period)
    }

    // MARK: Meal timing

    static func mentionsFood(_ normalized: String) -> Bool {
        contains(#"\b(food|meal|with breakfast|with lunch|with dinner|after breakfast|after lunch|after dinner)\b"#,
                 in: normalized)
    }

    // MARK: Form wording → unit

    static func inferUnitForm(_ normalized: String, currentUnit: String) -> MedicationAssistSuggestion? {
        let current = currentUnit.lowercased()
        func suggestion(_ idSuffix: String, _ unit: String, _ label: String, _ reason: String) -> MedicationAssistSuggestion? {
            guard unit.lowercased() != current else { return nil }
            return MedicationAssistSuggestion(
                id: "local:medication:form:\(idSuffix)", label: label, reason: reason, change: .unit(unit))
        }
        if contains(#"\b(inhaler|puffs?)\b"#, in: normalized) {
            return suggestion("inhaler", "dose", "Inhaler doses", "Matched inhaler wording in the plan.")
        }
        if contains(#"\bdrops?\b"#, in: normalized) {
            return suggestion("drop", "drop", "Drop form", "Matched drop wording in the plan.")
        }
        if contains(#"\b(liquid|syrup|solution|ml|milliliters?|tsp|teaspoons?|tbsp|tablespoons?)\b"#, in: normalized) {
            return suggestion("liquid", "mL", "Liquid dose (mL)", "Matched liquid or mL wording in the plan.")
        }
        if contains(#"\bcapsules?\b"#, in: normalized) {
            return suggestion("capsule", "capsule", "Capsule form", "Matched capsule wording in the plan.")
        }
        if contains(#"\b(vitamin|supplement|magnesium|calcium|zinc|probiotic|d3|b12|omega)\b"#, in: normalized) {
            return suggestion("supplement", "tablet", "Tablet by mouth", "Matched supplement wording in the plan.")
        }
        return nil
    }

    // MARK: Supply / notes

    static func inferSupplyCount(_ text: String) -> Int? {
        guard let groups = firstMatch(supplyPattern, in: text),
              let count = groups[1].flatMap({ Int($0) }), count > 0 else { return nil }
        return count
    }

    static func extractNote(_ text: String) -> String? {
        let trimSet = CharacterSet(charactersIn: ".,;: ")
        if let explicit = firstMatch(notePattern, in: text)?[1] {
            let note = String(explicit.prefix(maxNoteLength)).trimmingCharacters(in: trimSet)
            if !note.isEmpty { return note }
        }
        if let indication = firstMatch(indicationPattern, in: text)?[1] {
            let note = "Use for \(indication)".trimmingCharacters(in: trimSet)
            if !note.isEmpty { return String(note.prefix(maxNoteLength)) }
        }
        return nil
    }

    static func pharmacyNote(_ normalized: String) -> MedicationAssistSuggestion? {
        let isPickup = normalized.contains("pick up prescription")
            || normalized.contains("pickup prescription")
            || (normalized.contains("pickup") && normalized.contains("pharmacy"))
        let isRefill = isPickup
            || normalized.contains("refill")
            || normalized.contains("prescription")
            || normalized.contains("pharmacy")
            || " \(normalized) ".contains(" rx ")
        guard isRefill else { return nil }
        return MedicationAssistSuggestion(
            id: "local:medication:notes:pharmacy_refill",
            label: isPickup ? "Pharmacy pickup" : "Pharmacy refill",
            reason: "The capture mentions a pharmacy refill or prescription pickup.",
            change: .note(isPickup ? "Pick up prescription at pharmacy" : "Refill at pharmacy"))
    }
}
