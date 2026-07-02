import Foundation

// SmartFill — offline natural-language parser for the add-task headline field.
//
// This is the platform-agnostic (Foundation-only) port of the Android add-task modal's title
// smart-fill engine (`feature/tasks/.../TaskFormSheet.kt`: taskTitleSmartFill,
// taskTranscriptTargetDate, taskTitleRecurrence, taskTranscriptPriority, duration/time parsing).
// It reads a free-text task title and extracts a due date, time-of-day, duration, priority,
// recurrence, and a contact/action hint (email / phone / URL), then strips the recognised tokens
// to produce a cleaned title plus human-readable "detected" chips for a preview card.
//
// The function is pure and deterministic — `now` is injected (never `Date()` internally) so it
// unit-tests off-device (Windows/Linux CI). No SwiftUI / UIKit; NSDataDetector + Calendar only.

// MARK: - Public result types

/// Priority strength detected in the title. Maps to the app's integer priority at the call site
/// (`.high` → 3, `.medium` → 2, `.low` → 1; the app has no separate "urgent" level above high).
public enum TaskPriorityHint: String, Sendable, Equatable {
    case low, medium, high
}

/// A one-tap action detected in the title (an address, contact, or link to act on).
public struct ActionHint: Sendable, Equatable {
    public enum Kind: String, Sendable, Equatable { case email, phone, url }
    public var kind: Kind
    public var value: String
    public init(kind: Kind, value: String) {
        self.kind = kind
        self.value = value
    }
    /// Short human label for the chip, e.g. "Email john@x.com".
    public var label: String {
        switch kind {
        case .email: return "Email \(value)"
        case .phone: return "Call \(value)"
        case .url: return "Open \(value)"
        }
    }
}

/// One typed detection for the preview card: which field it fills, the chip label, and a short
/// human reason (mirroring the Android per-suggestion `reason` strings) so a UI can apply chips
/// individually instead of only all-at-once.
public struct SmartFillDetection: Sendable, Equatable, Hashable, Identifiable {
    public enum Kind: String, Sendable, Equatable { case action, priority, duration, time, date, recurrence }
    public var kind: Kind
    public var label: String
    public var reason: String
    public var id: String { kind.rawValue + "|" + label }

    public init(kind: Kind, label: String, reason: String) {
        self.kind = kind
        self.label = label
        self.reason = reason
    }
}

/// Everything the parser pulled out of a typed task title.
public struct SmartFillResult: Sendable, Equatable {
    /// The title with recognised scheduling/priority/action tokens removed and tidied.
    public var cleanedTitle: String
    /// Resolved due day (start-of-day) when a date phrase was found; `nil` otherwise.
    public var dueDate: Date?
    /// Time-of-day as minutes after midnight (0…1439) when a time phrase was found.
    public var timeMinuteOfDay: Int?
    /// Estimated duration in minutes when a duration phrase was found.
    public var durationMinutes: Int?
    /// Detected priority strength.
    public var priority: TaskPriorityHint?
    /// Detected recurrence (reuses ChronosCore's `RecurrenceRule`).
    public var recurrence: RecurrenceRule?
    /// Detected contact/action.
    public var actionHint: ActionHint?
    /// Human-readable chips for the "Detected in your text" preview card.
    public var detections: [String]
    /// The same detections, typed (kind + reason) so each chip can be applied individually.
    /// Parallel to `detections`; defaults empty so hand-built results stay source-compatible.
    public var typedDetections: [SmartFillDetection]

    public init(
        cleanedTitle: String,
        dueDate: Date? = nil,
        timeMinuteOfDay: Int? = nil,
        durationMinutes: Int? = nil,
        priority: TaskPriorityHint? = nil,
        recurrence: RecurrenceRule? = nil,
        actionHint: ActionHint? = nil,
        detections: [String] = [],
        typedDetections: [SmartFillDetection] = []
    ) {
        self.cleanedTitle = cleanedTitle
        self.dueDate = dueDate
        self.timeMinuteOfDay = timeMinuteOfDay
        self.durationMinutes = durationMinutes
        self.priority = priority
        self.recurrence = recurrence
        self.actionHint = actionHint
        self.detections = detections
        self.typedDetections = typedDetections
    }

    /// True when at least one schedulable/actionable field was detected (drives whether the UI
    /// surfaces the preview card at all).
    public var hasDetection: Bool {
        dueDate != nil || timeMinuteOfDay != nil || durationMinutes != nil ||
            priority != nil || recurrence != nil || actionHint != nil
    }
}

// MARK: - Entry point

/// Parse a typed task title for inline scheduling / priority / recurrence / action hints.
///
/// - Parameters:
///   - text: the raw title text.
///   - now: the reference "now" (injected for deterministic tests — the parser never reads the clock).
///   - calendar: the calendar used for date math (defaults to `.current`).
/// - Returns: a `SmartFillResult` with a cleaned title plus any detected fields and chips. When
///   nothing is detected the cleaned title is just the trimmed input and `detections` is empty.
public func parseSmartFill(
    _ text: String,
    now: Date,
    calendar: Calendar = .current
) -> SmartFillResult {
    let capture = text.trimmingCharacters(in: .whitespacesAndNewlines)
    guard capture.count >= 3 else {
        return SmartFillResult(cleanedTitle: capture)
    }
    let normalized = capture.lowercased()
    // Typed detections carry a per-chip reason (mirroring the Android suggestion `reason` copy);
    // the plain `detections` chip strings are derived from them so the two stay aligned.
    var typed: [SmartFillDetection] = []

    // --- Action (email / phone / url) -------------------------------------------------------
    let action = SmartFillParser.detectAction(capture)
    if let action {
        typed.append(SmartFillDetection(
            kind: .action, label: action.label, reason: SmartFillParser.actionReason(action.kind)))
    }

    // --- Priority ---------------------------------------------------------------------------
    let priority = SmartFillParser.detectPriority(normalized)
    if let priority {
        typed.append(SmartFillDetection(
            kind: .priority, label: SmartFillParser.priorityChip(priority),
            reason: priority == .high
                ? "The task text looks time-sensitive."
                : "The task text names a priority."))
    }

    // --- Duration ---------------------------------------------------------------------------
    let duration = SmartFillParser.detectDurationMinutes(normalized)
    if let duration {
        typed.append(SmartFillDetection(
            kind: .duration, label: SmartFillParser.durationChip(duration),
            reason: "The task text names a duration."))
    }

    // --- Time-of-day ------------------------------------------------------------------------
    let minuteOfDay = SmartFillParser.detectMinuteOfDay(capture, normalized: normalized)
    if let minuteOfDay {
        typed.append(SmartFillDetection(
            kind: .time, label: SmartFillParser.timeChip(minuteOfDay),
            reason: "The task text names a start time."))
    }

    // --- Date -------------------------------------------------------------------------------
    let (dueDate, dateChip) = SmartFillParser.detectDate(normalized, now: now, calendar: calendar)
    if let dateChip {
        typed.append(SmartFillDetection(
            kind: .date, label: dateChip, reason: "The task text names a target day."))
    }

    // --- Recurrence -------------------------------------------------------------------------
    let recurrence = SmartFillParser.detectRecurrence(normalized)
    if let recurrence {
        typed.append(SmartFillDetection(
            kind: .recurrence, label: SmartFillParser.recurrenceChip(recurrence),
            reason: "The task text names a repeating cadence."))
    }
    let chips = typed.map(\.label)

    // --- Cleaned title ----------------------------------------------------------------------
    let cleaned = SmartFillParser.cleanedTitle(from: capture, action: action)

    // If a date phrase resolved, fold the detected time-of-day into the due Date (TaskItem also has
    // a separate preferredStartMinuteOfDay the caller can set from `timeMinuteOfDay`).
    var resolvedDue = dueDate
    if let due = dueDate, let minute = minuteOfDay {
        resolvedDue = calendar.date(byAdding: .minute, value: minute, to: due) ?? due
    }

    return SmartFillResult(
        cleanedTitle: cleaned,
        dueDate: resolvedDue,
        timeMinuteOfDay: minuteOfDay,
        durationMinutes: duration,
        priority: priority,
        recurrence: recurrence,
        actionHint: action,
        detections: chips,
        typedDetections: typed
    )
}

// MARK: - Parsing internals

enum SmartFillParser {

    // Bounds mirroring the Android constants (TaskDurationMinMinutes / TaskDurationMaxMinutes).
    static let durationMinMinutes = 5
    static let durationMaxMinutes = 240

    // MARK: Regex helper (whole-word, case-insensitive)

    /// Returns the first capture-group values of the first match of `pattern` in `text`, or nil.
    /// Group 0 is the whole match. Case-insensitive.
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

    /// True if `pattern` matches anywhere in `text` (case-insensitive).
    static func contains(_ pattern: String, in text: String) -> Bool {
        firstMatch(pattern, in: text) != nil
    }

    // MARK: Priority

    /// Mirrors taskTranscriptPriority: urgent/asap/p1 → high, "high priority"/p2 → medium-ish,
    /// "low priority"/someday → low. The Android engine returns 0/1/2 (its scale tops at "urgent"=2);
    /// here we map to the app's 1/2/3 scale so "urgent/asap/p1" surfaces as High.
    static func detectPriority(_ normalized: String) -> TaskPriorityHint? {
        if contains(#"\b(urgent|asap|critical|important|deadline|due today|due tomorrow|p1)\b"#, in: normalized) {
            return .high
        }
        if contains(#"\b(high priority|high-priority|p2)\b"#, in: normalized) {
            return .high
        }
        if contains(#"\b(low priority|someday|whenever|optional|p3|p4)\b"#, in: normalized) {
            return .low
        }
        return nil
    }

    static func priorityChip(_ p: TaskPriorityHint) -> String {
        switch p {
        case .high: return "High priority"
        case .medium: return "Medium priority"
        case .low: return "Low priority"
        }
    }

    // MARK: Duration

    static func detectDurationMinutes(_ normalized: String) -> Int? {
        if let m = durationMention(normalized) { return m }
        if quickDurationWords.contains(where: { normalized.contains($0) }) { return 15 }
        if longDurationWords.contains(where: { normalized.contains($0) }) { return 60 }
        return nil
    }

    private static let quickDurationWords = ["call", "text", "reply", "quick", "pay", "pick up", "drop off"]
    private static let longDurationWords = ["deep work", "write", "draft", "review", "prepare", "plan", "research", "study"]

    private static func durationMention(_ normalized: String) -> Int? {
        // "half an hour" → 30
        if contains(#"\bhalf\s+an?\s+hour\b"#, in: normalized) { return 30 }
        // "an hour", "2 hours", "1 hour and 30 minutes"
        if let g = firstMatch(
            #"\b(an?|one|two|\d{1,2})\s*-?\s*(?:h|hr|hrs|hour|hours)(?:\s*(?:and\s*)?(\d{1,2})\s*-?\s*(?:m|min|mins|minute|minutes))?\b"#,
            in: normalized
        ), let hoursToken = g[safe: 1] ?? nil, let hours = hourValue(hoursToken) {
            let minutes = (g[safe: 2] ?? nil).flatMap { Int($0) } ?? 0
            let total = hours * 60 + minutes
            if (durationMinMinutes...durationMaxMinutes).contains(total) { return total }
        }
        // "30 min", "45 minutes", "20m"
        if let g = firstMatch(#"\b(\d{1,3})\s*-?\s*(?:m|min|mins|minute|minutes)\b"#, in: normalized),
           let token = g[safe: 1] ?? nil, let value = Int(token),
           (durationMinMinutes...durationMaxMinutes).contains(value) {
            return value
        }
        return nil
    }

    private static func hourValue(_ value: String) -> Int? {
        switch value.lowercased() {
        case "a", "an", "one": return 1
        case "two": return 2
        default: return Int(value)
        }
    }

    static func durationChip(_ minutes: Int) -> String {
        if minutes % 60 == 0 {
            let h = minutes / 60
            return h == 1 ? "1 hour" : "\(h) hours"
        }
        if minutes > 60 {
            let h = minutes / 60, m = minutes % 60
            return "\(h)h \(m)m"
        }
        return "\(minutes) min"
    }

    // MARK: Time-of-day

    /// Mirrors taskTranscriptPreferredStartMinute: explicit clock times first ("at 9:30", "noon",
    /// "midnight"), then named parts of day ("first thing", "early morning", "evening", …).
    static func detectMinuteOfDay(_ capture: String, normalized: String) -> Int? {
        // Explicit clock time. Capture group 1 is the time token.
        if let g = firstMatch(
            #"\b(?:at\s+)?(\d{1,2}:\d{2}\s*(?:am|pm)?|\d{1,2}\s*(?:am|pm)|noon|midnight)\b"#,
            in: capture
        ), let token = g[safe: 1] ?? nil, let minute = parseFlexibleMinute(token) {
            return minute
        }
        // Named phrases — most specific first so "late morning"/"after lunch" win over bare words.
        if normalized.contains("first thing") { return 8 * 60 }
        if normalized.contains("before work") { return 8 * 60 }
        if normalized.contains("early morning") { return 7 * 60 }
        if normalized.contains("late morning") { return 11 * 60 }
        if normalized.contains("morning") || normalized.contains("breakfast") { return 9 * 60 }
        if normalized.contains("before noon") { return 11 * 60 }
        if normalized.contains("noon") || normalized.contains("midday") { return 12 * 60 }
        if normalized.contains("before lunch") { return 11 * 60 + 30 }
        if normalized.contains("after lunch") { return 14 * 60 }
        if normalized.contains("lunch") { return 13 * 60 }
        if normalized.contains("early afternoon") { return 13 * 60 }
        if normalized.contains("late afternoon") { return 16 * 60 }
        if normalized.contains("afternoon") { return 13 * 60 }
        if normalized.contains("after work") { return 17 * 60 + 30 }
        if normalized.contains("end of day") || normalized.contains("eod") { return 17 * 60 }
        if normalized.contains("after dinner") { return 20 * 60 }
        if normalized.contains("late evening") { return 20 * 60 }
        if normalized.contains("evening") || normalized.contains("dinner") || normalized.contains("tonight") { return 18 * 60 }
        if normalized.contains("midnight") { return 0 }
        return nil
    }

    /// Port of `parseFlexibleMinute` (core/ui ChronosTimeUtils): "9:30", "9:30 am", "9 pm", "noon",
    /// "midnight" → minutes after midnight, or nil.
    static func parseFlexibleMinute(_ value: String) -> Int? {
        let upper = value.trimmingCharacters(in: .whitespaces).uppercased()
        if upper == "NOON" { return 12 * 60 }
        if upper == "MIDNIGHT" { return 0 }
        var amPm: String? = nil
        if upper.hasSuffix("AM") { amPm = "AM" }
        else if upper.hasSuffix("PM") { amPm = "PM" }
        var time = upper
        if time.hasSuffix("AM") { time = String(time.dropLast(2)) }
        else if time.hasSuffix("PM") { time = String(time.dropLast(2)) }
        time = time.trimmingCharacters(in: .whitespaces)
        let parts = time.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard !parts.isEmpty, parts.count <= 2, let rawHour = Int(parts[0]) else { return nil }
        let minute = parts.count == 2 ? (Int(parts[1]) ?? -1) : 0
        guard (0...59).contains(minute) else { return nil }
        let hour: Int
        switch amPm {
        case "AM": hour = rawHour == 12 ? 0 : rawHour
        case "PM": hour = rawHour == 12 ? 12 : rawHour + 12
        default: hour = rawHour
        }
        guard (0...23).contains(hour) else { return nil }
        return hour * 60 + minute
    }

    static func timeChip(_ minute: Int) -> String {
        let m = ((minute % 1440) + 1440) % 1440
        let hour24 = m / 60
        let min = m % 60
        let suffix = hour24 >= 12 ? "PM" : "AM"
        var hour12 = hour24 % 12
        if hour12 == 0 { hour12 = 12 }
        return String(format: "%d:%02d %@", hour12, min, suffix)
    }

    // MARK: Date

    /// Resolve a date phrase to a start-of-day `Date`, plus the chip text. Mirrors
    /// taskTranscriptTargetDate (relative/weekday/weekend/end-of-month) and the today/tomorrow path.
    static func detectDate(
        _ normalized: String, now: Date, calendar: Calendar
    ) -> (Date?, String?) {
        let today = calendar.startOfDay(for: now)

        func plusDays(_ n: Int) -> Date? { calendar.date(byAdding: .day, value: n, to: today) }
        func plusMonths(_ n: Int) -> Date? { calendar.date(byAdding: .month, value: n, to: today) }

        // Day-after-tomorrow / tomorrow / today (check the longer phrase first).
        if contains(#"\bday after tomorrow\b"#, in: normalized) {
            return (plusDays(2), "Due in 2 days")
        }
        if normalized.contains("tomorrow") {
            return (plusDays(1), "Due tomorrow")
        }

        // "in 3 days" / "in two weeks" / "in a month"
        if let g = firstMatch(
            #"\bin\s+(a|an|one|two|three|four|five|six|seven|eight|nine|ten|\d{1,3})\s+(day|days|week|weeks|month|months)\b"#,
            in: normalized
        ), let amountToken = g[safe: 1] ?? nil, let amount = relativeAmount(amountToken),
           (1...365).contains(amount), let unit = g[safe: 2] ?? nil {
            let date: Date?
            if unit.hasPrefix("month") { date = plusMonths(amount) }
            else if unit.hasPrefix("week") { date = plusDays(amount * 7) }
            else { date = plusDays(amount) }
            return (date, chipFor(date, calendar: calendar))
        }

        // Weekends / end-of-month / next week / next month
        if contains(#"\bnext weekend\b"#, in: normalized) {
            let sat = nextOrSame(weekday: 7, from: today, calendar: calendar)
            let date = calendar.date(byAdding: .day, value: 7, to: sat)
            return (date, "Due next weekend")
        }
        if contains(#"\bthis weekend\b"#, in: normalized) {
            return (nextOrSame(weekday: 7, from: today, calendar: calendar), "Due this weekend")
        }
        if contains(#"\bend of (the )?month\b"#, in: normalized) {
            return (endOfMonth(today, calendar: calendar), "Due end of month")
        }
        if contains(#"\bnext week\b"#, in: normalized) {
            // Android: next Monday.
            return (next(weekday: 2, from: today, calendar: calendar), "Due next week")
        }
        if contains(#"\bnext month\b"#, in: normalized) {
            return (plusMonths(1), "Due next month")
        }

        // Today / tonight (after the richer phrases so "due tomorrow" isn't shadowed).
        if contains(#"\b(today|tonight)\b"#, in: normalized) {
            return (today, "Due today")
        }

        // Qualified weekday: "next tuesday", "this fri", "on monday", "by wed".
        if let g = firstMatch(
            #"\b(this|next|on|by|coming|come)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|weds|thu|thur|thurs|fri|sat|sun)\b"#,
            in: normalized
        ), let qualifier = g[safe: 1] ?? nil, let dayToken = g[safe: 2] ?? nil,
           let weekday = weekdayNumber(dayToken) {
            let date: Date?
            if qualifier.lowercased() == "next" {
                date = next(weekday: weekday, from: today, calendar: calendar)
            } else {
                date = nextOrSame(weekday: weekday, from: today, calendar: calendar)
            }
            return (date, chipFor(date, calendar: calendar, weekdayPreferred: true))
        }

        return (nil, nil)
    }

    private static func relativeAmount(_ token: String) -> Int? {
        switch token.lowercased() {
        case "a", "an", "one": return 1
        case "two": return 2
        case "three": return 3
        case "four": return 4
        case "five": return 5
        case "six": return 6
        case "seven": return 7
        case "eight": return 8
        case "nine": return 9
        case "ten": return 10
        default: return Int(token)
        }
    }

    /// Calendar weekday number: 1=Sun … 7=Sat (matches Calendar.component(.weekday)).
    static func weekdayNumber(_ token: String) -> Int? {
        switch token.lowercased() {
        case "sunday", "sun": return 1
        case "monday", "mon": return 2
        case "tuesday", "tue", "tues": return 3
        case "wednesday", "wed", "weds": return 4
        case "thursday", "thu", "thur", "thurs": return 5
        case "friday", "fri": return 6
        case "saturday", "sat": return 7
        default: return nil
        }
    }

    /// Next date with `weekday` strictly after `from` (never `from` itself).
    static func next(weekday: Int, from: Date, calendar: Calendar) -> Date? {
        var date = calendar.date(byAdding: .day, value: 1, to: from) ?? from
        for _ in 0..<7 {
            if calendar.component(.weekday, from: date) == weekday { return calendar.startOfDay(for: date) }
            date = calendar.date(byAdding: .day, value: 1, to: date) ?? date
        }
        return calendar.startOfDay(for: date)
    }

    /// `from` if it already matches `weekday`, otherwise the next occurrence.
    static func nextOrSame(weekday: Int, from: Date, calendar: Calendar) -> Date {
        var date = from
        for _ in 0..<8 {
            if calendar.component(.weekday, from: date) == weekday { return calendar.startOfDay(for: date) }
            date = calendar.date(byAdding: .day, value: 1, to: date) ?? date
        }
        return calendar.startOfDay(for: date)
    }

    static func endOfMonth(_ date: Date, calendar: Calendar) -> Date? {
        guard let range = calendar.range(of: .day, in: .month, for: date) else { return nil }
        var comps = calendar.dateComponents([.year, .month], from: date)
        comps.day = range.count
        return calendar.date(from: comps).map { calendar.startOfDay(for: $0) }
    }

    private static func chipFor(_ date: Date?, calendar: Calendar, weekdayPreferred: Bool = false) -> String? {
        guard let date else { return nil }
        var cal = calendar
        let fmt = DateFormatter()
        fmt.calendar = cal
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.timeZone = cal.timeZone
        if weekdayPreferred {
            fmt.dateFormat = "EEE"   // "Tue"
            return "Due \(fmt.string(from: date))"
        }
        fmt.dateFormat = "MMM d"     // "Jun 16"
        cal.timeZone = calendar.timeZone
        return "Due \(fmt.string(from: date))"
    }

    // MARK: Recurrence

    /// Mirrors taskTitleRecurrence → contextualTaskRecurringCadenceContextOptions / interval / weekdays.
    /// Returns a ChronosCore `RecurrenceRule` (the app maps it to its own RecurrenceSpec at the call site).
    static func detectRecurrence(_ normalized: String) -> RecurrenceRule? {
        // Weekly takes priority when an explicit weekday cadence is present, then daily, then monthly —
        // matching the Android order where the first detected cadence wins.
        let daily = contains(
            #"\b(daily|every day|every morning|every night|every\s+(?:other|\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+days?)\b"#,
            in: normalized)
        let weekly = contains(
            #"\b(weekly|bi-?weekly|fortnightly|every week|once a week|weekdays?|weekends?|every\s+(?:\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+weeks?|every\s+other\s+(week|mon|monday|tue|tuesday|wed|wednesday|thu|thursday|fri|friday|sat|saturday|sun|sunday)|every\s+(mon|monday|tue|tuesday|wed|wednesday|thu|thursday|fri|friday|sat|saturday|sun|sunday))\b"#,
            in: normalized)
        let monthly = contains(
            #"\b(monthly|quarterly|every month|every\s+(?:other|\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+months?|day of month|on the \d{1,2}(?:st|nd|rd|th)?)\b"#,
            in: normalized)

        // Preserve Android's add-order: DAILY, then WEEKLY, then MONTHLY — firstOrNull wins.
        if daily {
            let interval = intervalForUnit(normalized, unit: "day") ?? 1
            return RecurrenceRule(frequency: .daily, interval: interval)
        }
        if weekly {
            let interval: Int
            if contains(#"\b(bi-?weekly|fortnightly|every\s+other\s+(week|mon|monday|tue|tuesday|wed|wednesday|thu|thursday|fri|friday|sat|saturday|sun|sunday))\b"#, in: normalized) {
                interval = 2
            } else {
                interval = intervalForUnit(normalized, unit: "week") ?? 1
            }
            return RecurrenceRule(frequency: .weekly, interval: interval, weekdays: weeklyDays(normalized))
        }
        if monthly {
            let interval: Int
            if contains(#"\bquarterly\b"#, in: normalized) { interval = 3 }
            else { interval = intervalForUnit(normalized, unit: "month") ?? 1 }
            return RecurrenceRule(frequency: .monthly, interval: interval)
        }
        return nil
    }

    private static func intervalForUnit(_ normalized: String, unit: String) -> Int? {
        if contains(#"\bevery\s+other\s+\#(unit)s?\b"#, in: normalized) { return 2 }
        if let g = firstMatch(
            #"\bevery\s+(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+\#(unit)s?\b"#,
            in: normalized
        ), let token = g[safe: 1] ?? nil {
            return intervalValue(token)
        }
        return nil
    }

    private static func intervalValue(_ value: String) -> Int? {
        switch value.lowercased() {
        case "one": return 1
        case "two": return 2
        case "three": return 3
        case "four": return 4
        case "five": return 5
        case "six": return 6
        case "seven": return 7
        case "eight": return 8
        case "nine": return 9
        case "ten": return 10
        case "eleven": return 11
        case "twelve": return 12
        default: return Int(value).map { max($0, 1) }
        }
    }

    /// Weekday set for a weekly rule (1=Sun … 7=Sat). Mirrors contextualTaskWeeklyDays.
    static func weeklyDays(_ normalized: String) -> Set<Int> {
        var days: Set<Int> = []
        if contains(#"\b(weekday|weekdays|workday|workdays)\b"#, in: normalized) {
            days.formUnion([2, 3, 4, 5, 6])   // Mon–Fri
        }
        if contains(#"\b(weekend|weekends)\b"#, in: normalized) {
            days.formUnion([7, 1])            // Sat, Sun
        }
        if contains(#"\b(mon|monday)\b"#, in: normalized) { days.insert(2) }
        if contains(#"\b(tue|tuesday)\b"#, in: normalized) { days.insert(3) }
        if contains(#"\b(wed|wednesday)\b"#, in: normalized) { days.insert(4) }
        if contains(#"\b(thu|thursday)\b"#, in: normalized) { days.insert(5) }
        if contains(#"\b(fri|friday)\b"#, in: normalized) { days.insert(6) }
        if contains(#"\b(sat|saturday)\b"#, in: normalized) { days.insert(7) }
        if contains(#"\b(sun|sunday)\b"#, in: normalized) { days.insert(1) }
        return days
    }

    static func recurrenceChip(_ rule: RecurrenceRule) -> String {
        let weekdayNames = ["", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
        switch rule.frequency {
        case .daily:
            return rule.interval == 1 ? "Every day" : "Every \(rule.interval) days"
        case .weekly:
            if rule.weekdays == [2, 3, 4, 5, 6] { return "Weekdays" }
            if rule.weekdays == [7, 1] { return "Weekends" }
            if rule.weekdays.count == 1, let d = rule.weekdays.first {
                return "Every \(weekdayNames[d])"
            }
            return rule.interval == 1 ? "Every week" : "Every \(rule.interval) weeks"
        case .monthly:
            return rule.interval == 1 ? "Every month" : "Every \(rule.interval) months"
        }
    }

    // MARK: Action (email / phone / url)

    /// Detect an email / phone / URL in the title using the same regex patterns as the Android
    /// engine. Pure `NSRegularExpression` (cross-platform — `NSDataDetector` is Apple-only and is
    /// not available in swift-corelibs-Foundation, so it can't be used in the portable core).
    /// Email is preferred, then phone, then URL — matching Android's ordering (taskTranscriptActionDraft).
    static func detectAction(_ capture: String) -> ActionHint? {
        // Email.
        if let g = firstMatch(#"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"#, in: capture),
           let email = g.first.flatMap({ $0 }) {
            return ActionHint(kind: .email, value: email)
        }

        // Phone — a run of digits / separators with at least 7 digits (mirrors the Android pattern).
        if let g = firstMatch(#"(?:\+?\d[\d\s().-]{6,}\d)"#, in: capture),
           let raw = g.first.flatMap({ $0 }), digitCount(raw) >= 7 {
            return ActionHint(kind: .phone, value: raw.trimmingCharacters(in: .whitespaces))
        }

        // URL — explicit scheme, www., or a bare host with a path/known TLD.
        if let g = firstMatch(
            #"(?:https?://[^\s]+|www\.[^\s]+|[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?:/[^\s]*)?)"#,
            in: capture
        ), let raw = g.first.flatMap({ $0 }) {
            var value = raw
            while let last = value.last, ".,)".contains(last) { value.removeLast() }
            // Don't mistake an email's domain (already returned above) — but emails were handled
            // first, so any URL here is genuinely a link.
            if value.contains("."), !value.contains("@") {
                return ActionHint(kind: .url, value: value)
            }
        }

        return nil
    }

    private static func digitCount(_ s: String) -> Int {
        s.reduce(0) { $0 + ($1.isNumber ? 1 : 0) }
    }

    /// Per-kind reason line for an action chip (mirrors the Android assist `reason` copy).
    static func actionReason(_ kind: ActionHint.Kind) -> String {
        switch kind {
        case .email: return "The task text includes an email address."
        case .phone: return "The task text includes a phone number."
        case .url: return "The task text includes a link."
        }
    }

    // MARK: Cleaned title

    /// Strip the recognised scheduling / priority / duration / action tokens to produce a tidy title,
    /// mirroring TASK_TRANSCRIPT_TITLE_NOISE_PATTERNS. Falls back to the trimmed capture if stripping
    /// would empty the title or leave fewer than 3 chars.
    static func cleanedTitle(from capture: String, action: ActionHint?) -> String {
        var value = capture

        // Action values (email / phone / url) come out first so they don't survive as noise.
        if let action {
            value = value.replacingOccurrences(of: action.value, with: " ")
        }

        let noisePatterns: [String] = [
            // Leading capture filler ("remind me to…", "todo:").
            #"^\s*(?:please\s+)?(?:remind me to|reminder to|don'?t forget to|i need to|i have to|i should|need to|have to|gotta|to-?do:|task:)\s+"#,
            // Priority words.
            #"\b(urgent|asap|critical|important|high priority|high-priority|low priority|optional|p[1-4])\b"#,
            // Time-of-day named phrases.
            #"\b(?:today|tomorrow|tonight|first thing|end of day|eod|(?:early |late )?(?:morning|afternoon|evening|midday)|at night)\b"#,
            // Relative date phrases.
            #"\b(this weekend|next weekend|end of (the )?month|next week|next month|day after)\b"#,
            #"\bin\s+(a|an|one|two|three|four|five|six|seven|eight|nine|ten|\d{1,3})\s+(day|days|week|weeks|month|months)\b"#,
            #"\b(this|next|on|by|coming|come)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|weds|thu|thur|thurs|fri|sat|sun)\b"#,
            // Times.
            #"\b(?:at\s+)?(?:\d{1,2}:\d{2}\s*(?:am|pm)?|\d{1,2}\s*(?:am|pm)|noon|midnight)\b"#,
            // Durations.
            #"\b(\d{1,3})\s*-?\s*(?:m|min|mins|minute|minutes)\b"#,
            #"\b(an?|one|two|\d{1,2})\s*-?\s*(?:h|hr|hrs|hour|hours)(?:\s*(?:and\s*)?(\d{1,2})\s*-?\s*(?:m|min|mins|minute|minutes))?\b"#,
            #"\bhalf\s+an?\s+hour\b"#,
            // Recurrence phrases (so "every monday" etc. don't linger in the title).
            #"\b(daily|weekly|bi-?weekly|fortnightly|monthly|quarterly)\b"#,
            #"\bevery\s+(?:other\s+)?(?:\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)?\s*(?:day|days|week|weeks|month|months|morning|night|monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)s?\b"#,
            #"\b(weekdays?|weekends?|workdays?)\b"#,
        ]

        for pattern in noisePatterns {
            value = replacingMatches(pattern, in: value, with: " ")
        }

        // Collapse whitespace and trim stray separators.
        value = value.replacingOccurrences(
            of: #"\s+"#, with: " ", options: .regularExpression
        )
        value = value.trimmingCharacters(in: CharacterSet(charactersIn: " ,.-:"))

        // If stripping emptied the title (or left fewer than 3 chars), keep the original capture so
        // the user never loses what they typed.
        guard value.count >= 3 else {
            return capture.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return capitalizeFirst(value)
    }

    private static func capitalizeFirst(_ s: String) -> String {
        guard let first = s.first else { return s }
        return String(first).uppercased() + s.dropFirst()
    }

    private static func replacingMatches(_ pattern: String, in text: String, with replacement: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
        else { return text }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.stringByReplacingMatches(in: text, options: [], range: range, withTemplate: replacement)
    }
}

// MARK: - Small helpers

private extension Array {
    /// Safe subscript that returns nil out of bounds (used to read optional regex capture groups).
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
