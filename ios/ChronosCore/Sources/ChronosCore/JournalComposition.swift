import Foundation

// JournalComposition — portable, Foundation-only helpers for multi-entry journal composition,
// the calendar mood overview, the deterministic daily prompt, flexible time-of-day parsing, and the
// AI insight prompt builder.
//
// Platform-agnostic port of the Android journal logic:
//   - `feature/daydial/.../JournalMoodAndStreak.kt` (journalMoodByDate / journalMoodFor,
//     journalSerializeBody / journalParseBody, journalPromptOfTheDay + JournalDailyPrompts,
//     buildJournalInsightPrompt, the `hc-workout-` workout-point convention).
//   - `core/ui/.../ChronosTimeUtils.kt` (parseFlexibleMinute / formatDisplayMinute).
//
// Everything is pure and deterministic: dates and the `Calendar` are injected (never `Date()` /
// `Calendar.current` read internally) so these unit-test off-device on the Windows/Linux CI that
// builds ChronosCore. No SwiftUI / UIKit / HealthKit.

// MARK: - Entry record

/// A minimal, value-typed view of a journal entry — exactly the fields the portable composition and
/// overview helpers need. Mirrors `core/domain/.../JournalEntry.kt` (id, entryDate, body, isPrimary,
/// dayRating, entryMinuteOfDay) without dragging the persistence layer into ChronosCore.
public struct JournalEntryRecord: Sendable, Equatable, Identifiable {
    public let id: String
    /// The calendar day this entry belongs to (use the start-of-day instant for stable keying).
    public let entryDate: Date
    public let body: String
    /// One primary entry per day acts as the day's main reflection; secondary points are `false`.
    public let isPrimary: Bool
    /// 1–5 mood rating, or nil when the day was left unrated.
    public let dayRating: Int?
    /// Optional minute-of-day (0–1439) timestamp for the point, used to order points within a day.
    public let entryMinuteOfDay: Int?
    /// Creation order tiebreaker, used after isPrimary and minute-of-day when sorting points.
    public let createdAt: Date

    public init(
        id: String,
        entryDate: Date,
        body: String = "",
        isPrimary: Bool = true,
        dayRating: Int? = nil,
        entryMinuteOfDay: Int? = nil,
        createdAt: Date = Date(timeIntervalSince1970: 0)
    ) {
        self.id = id
        self.entryDate = entryDate
        self.body = body
        self.isPrimary = isPrimary
        self.dayRating = dayRating
        self.entryMinuteOfDay = entryMinuteOfDay
        self.createdAt = createdAt
    }
}

// MARK: - Mood

/// A single "how did today feel?" option: a 1–5 `rating` with its `emoji` face and short `label`.
/// Mirrors Android's `JournalMood`.
public struct JournalMood: Sendable, Equatable, Identifiable {
    public let rating: Int
    public let emoji: String
    public let label: String

    public init(rating: Int, emoji: String, label: String) {
        self.rating = rating
        self.emoji = emoji
        self.label = label
    }

    public var id: Int { rating }
}

/// The five day-feeling options, rough → great. The `rating` doubles as the persisted value.
/// Labels match Android (`JournalMoods` in JournalMoodAndStreak.kt): Rough / Low / Okay / Good / Great.
public let journalMoods: [JournalMood] = [
    JournalMood(rating: 1, emoji: "😞", label: "Rough"),
    JournalMood(rating: 2, emoji: "😕", label: "Low"),
    JournalMood(rating: 3, emoji: "😐", label: "Okay"),
    JournalMood(rating: 4, emoji: "🙂", label: "Good"),
    JournalMood(rating: 5, emoji: "😄", label: "Great"),
]

/// The `JournalMood` for a stored `rating`, or nil when the day was left unrated / out of range.
/// Mirrors `journalMoodFor`.
public func journalMoodFor(_ rating: Int?) -> JournalMood? {
    guard let rating else { return nil }
    return journalMoods.first { $0.rating == rating }
}

// MARK: - Workout points

/// Id prefix the Health Connect / HealthKit importer stamps on workout points. Mirrors
/// `JournalWorkoutIdPrefix`.
public let journalWorkoutIdPrefix = "hc-workout-"

/// Whether `entry` is an auto-imported workout point rather than a written reflection. Mirrors
/// `isJournalWorkoutEntry`.
public func isJournalWorkoutEntry(_ entry: JournalEntryRecord) -> Bool {
    entry.id.hasPrefix(journalWorkoutIdPrefix)
}

// MARK: - Calendar mood overview

/// The representative mood per day for the calendar overview: the primary entry's rating when present,
/// otherwise the first rated entry that day (in the given input order). Days with no rating are absent
/// from the map. Keys are the day's start-of-day in the injected `calendar`. Mirrors `journalMoodByDate`.
public func journalMoodByDate(
    _ entries: [JournalEntryRecord],
    calendar: Calendar
) -> [Date: JournalMood] {
    var result: [Date: JournalMood] = [:]
    let grouped = groupByDay(entries, calendar: calendar)
    for (day, dayEntries) in grouped {
        let rating = dayEntries.first(where: { $0.isPrimary && $0.dayRating != nil })?.dayRating
            ?? dayEntries.first(where: { $0.dayRating != nil })?.dayRating
        if let mood = journalMoodFor(rating) {
            result[day] = mood
        }
    }
    return result
}

/// Days that carry at least one imported workout point (keyed by start-of-day). Mirrors
/// `journalWorkoutDates`.
public func journalWorkoutDates(
    _ entries: [JournalEntryRecord],
    calendar: Calendar
) -> Set<Date> {
    Set(entries.filter { isJournalWorkoutEntry($0) }.map { calendar.startOfDay(for: $0.entryDate) })
}

/// Days the user actually journaled (any non-workout entry), for the calendar's "journaled" marker.
/// Mirrors `journalWrittenDates`.
public func journalWrittenDates(
    _ entries: [JournalEntryRecord],
    calendar: Calendar
) -> Set<Date> {
    Set(entries.filter { !isJournalWorkoutEntry($0) }.map { calendar.startOfDay(for: $0.entryDate) })
}

/// The day's entries sorted the way the multi-entry journal list shows them: the primary reflection
/// first, then secondary points ordered by `entryMinuteOfDay` (unset last), then by `createdAt`.
/// Mirrors `JournalViewModel`'s "sorted by time then creation, primary first" ordering.
public func journalSortedPoints(
    _ entries: [JournalEntryRecord]
) -> [JournalEntryRecord] {
    entries.sorted { lhs, rhs in
        if lhs.isPrimary != rhs.isPrimary { return lhs.isPrimary }
        let lm = lhs.entryMinuteOfDay ?? Int.max
        let rm = rhs.entryMinuteOfDay ?? Int.max
        if lm != rm { return lm < rm }
        return lhs.createdAt < rhs.createdAt
    }
}

/// Group entries by their start-of-day key in the injected `calendar`. Preserves first-seen order of
/// entries within each day so primary/first-rated resolution matches Android's input-order semantics.
private func groupByDay(
    _ entries: [JournalEntryRecord],
    calendar: Calendar
) -> [(day: Date, entries: [JournalEntryRecord])] {
    var order: [Date] = []
    var buckets: [Date: [JournalEntryRecord]] = [:]
    for entry in entries {
        let day = calendar.startOfDay(for: entry.entryDate)
        if buckets[day] == nil {
            buckets[day] = []
            order.append(day)
        }
        buckets[day]?.append(entry)
    }
    return order.map { (day: $0, entries: buckets[$0] ?? []) }
}

// MARK: - Body serialization (main note + sub-note bullets)

/// Prefix marking a line as a sub-note ("bullet") within an entry's body. Mirrors `JournalSubNotePrefix`.
public let journalSubNotePrefix = "• "

/// An entry body split into its free-form `mainNote` and any subtask-style `subNotes`. Mirrors
/// `JournalBodyParts`.
public struct JournalBodyParts: Sendable, Equatable {
    public let mainNote: String
    public let subNotes: [String]

    public init(mainNote: String, subNotes: [String]) {
        self.mainNote = mainNote
        self.subNotes = subNotes
    }
}

/// Combine a `mainNote` and subtask-style `subNotes` into a single entry body: the main note, then one
/// bullet line per sub-note. Round-trips with `journalParseBody`. Blank sub-notes are dropped. Mirrors
/// `journalSerializeBody`.
public func journalSerializeBody(mainNote: String, subNotes: [String]) -> String {
    let main = mainNote.trimmingCharacters(in: .whitespacesAndNewlines)
    let notes = subNotes
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
    var out = main
    if !notes.isEmpty {
        if !main.isEmpty { out += "\n" }
        out += notes.map { "\(journalSubNotePrefix)\($0)" }.joined(separator: "\n")
    }
    return out.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// Split an entry `body` back into its main note and sub-notes: lines starting with the bullet prefix
/// (after leading whitespace) become sub-notes, everything else is the main note. A plain body with no
/// bullets parses to itself with no sub-notes. Mirrors `journalParseBody`.
public func journalParseBody(_ body: String) -> JournalBodyParts {
    // Match Kotlin's `String.lines()`. Normalize CRLF/CR to LF first: in Swift "\r\n" is a single
    // grapheme cluster, so splitting on "\n" and stripping a trailing "\r" is unreliable. Replacing
    // line endings up front handles \r\n, \r, and \n uniformly across platforms.
    let normalized = body
        .replacingOccurrences(of: "\r\n", with: "\n")
        .replacingOccurrences(of: "\r", with: "\n")
    let lines = normalized.components(separatedBy: "\n")
    let subNotes: [String] = lines.compactMap { line in
        let leading = String(line.drop(while: { $0 == " " || $0 == "\t" }))
        guard leading.hasPrefix(journalSubNotePrefix) else { return nil }
        let stripped = String(leading.dropFirst(journalSubNotePrefix.count))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return stripped.isEmpty ? nil : stripped
    }
    let mainNote = lines
        .filter { line in
            let leading = String(line.drop(while: { $0 == " " || $0 == "\t" }))
            return !leading.hasPrefix(journalSubNotePrefix)
        }
        .joined(separator: "\n")
        .trimmingCharacters(in: .whitespacesAndNewlines)
    return JournalBodyParts(mainNote: mainNote, subNotes: subNotes)
}

// MARK: - Deterministic daily prompt

/// Rotating reflection prompts, surfaced one-per-day so the journal feels fresh. Mirrors Android's
/// `JournalDailyPrompts` (12 prompts, in order).
public let journalDailyPrompts: [String] = [
    "What's one small win from today?",
    "What are you grateful for right now?",
    "What's been on your mind lately?",
    "When did you feel most like yourself today?",
    "What would make tomorrow feel lighter?",
    "What's something you learned today?",
    "Who or what gave you energy today?",
    "What's one thing you can let go of tonight?",
    "What are you looking forward to?",
    "How did you take care of yourself today?",
    "What challenged you, and how did you handle it?",
    "What's a moment from today worth remembering?",
]

/// Days since the Unix epoch (1970-01-01) for `date`'s calendar day in the injected `calendar`. The
/// portable equivalent of `LocalDate.toEpochDay()`. Use a fixed-offset (e.g. UTC) calendar for a
/// timezone-stable result. Negative for dates before the epoch.
public func journalEpochDay(_ date: Date, calendar: Calendar) -> Int {
    let epoch = Date(timeIntervalSince1970: 0)
    let startEpoch = calendar.startOfDay(for: epoch)
    let startDate = calendar.startOfDay(for: date)
    return calendar.dateComponents([.day], from: startEpoch, to: startDate).day ?? 0
}

/// The reflection prompt for a given `date`, chosen deterministically so the same day always shows the
/// same prompt while consecutive days rotate through the list. Uses the epoch day (via the injected
/// `calendar`) so it's timezone- and locale-stable. Mirrors `journalPromptOfTheDay`.
public func journalPromptOfTheDay(
    date: Date,
    calendar: Calendar,
    prompts: [String] = journalDailyPrompts
) -> String {
    guard !prompts.isEmpty else { return "" }
    let size = prompts.count
    let index = journalEpochDay(date, calendar: calendar) % size
    let safeIndex = index < 0 ? index + size : index
    return prompts[safeIndex]
}

/// The prompt offset `steps` ahead of the day's deterministic base, wrapping around the list — the
/// manual "shuffle" button cycles through prompts without repeating until the list is exhausted.
/// Returns the day's prompt when `steps == 0`.
public func journalPrompt(
    date: Date,
    calendar: Calendar,
    offset steps: Int,
    prompts: [String] = journalDailyPrompts
) -> String {
    guard !prompts.isEmpty else { return "" }
    let size = prompts.count
    let base = journalEpochDay(date, calendar: calendar) % size
    let safeBase = base < 0 ? base + size : base
    var index = (safeBase + steps) % size
    if index < 0 { index += size }
    return prompts[index]
}

// MARK: - Flexible time-of-day parsing

/// Parse "HH:mm", "H:mm", "9:00 AM", "21:00", or a bare hour into minutes from midnight (0–1439), or
/// nil when the value isn't a valid time. Mirrors `parseFlexibleMinute`. Case-insensitive AM/PM suffix.
public func parseFlexibleMinute(_ value: String) -> Int? {
    let normalized = value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    let amPm: String?
    if normalized.hasSuffix("AM") {
        amPm = "AM"
    } else if normalized.hasSuffix("PM") {
        amPm = "PM"
    } else {
        amPm = nil
    }
    var time = normalized
    if let amPm {
        time = String(time.dropLast(amPm.count))
    }
    time = time.trimmingCharacters(in: .whitespacesAndNewlines)

    let parts = time.components(separatedBy: ":")
    if parts.isEmpty || parts.count > 2 { return nil }
    guard let rawHour = Int(parts[0]) else { return nil }
    // Kotlin: parts.getOrNull(1)?.toIntOrNull() ?: 0 — a missing or unparseable minute segment
    // (e.g. "9" or "9:") falls back to 0, matching Android's parseFlexibleMinute exactly.
    let minute = (parts.count == 2 ? Int(parts[1]) : nil) ?? 0
    if !(0...59).contains(minute) { return nil }
    let hour: Int
    switch amPm {
    case "AM": hour = (rawHour == 12) ? 0 : rawHour
    case "PM": hour = (rawHour == 12) ? 12 : rawHour + 12
    default: hour = rawHour
    }
    guard (0...23).contains(hour) else { return nil }
    return hour * 60 + minute
}

/// 12-hour clock label, e.g. "8:00 AM". `minute` is wrapped into 0–1439. Mirrors `formatDisplayMinute`.
public func formatDisplayMinute(_ minute: Int) -> String {
    let normalized = ((minute % (24 * 60)) + (24 * 60)) % (24 * 60)
    let hour = normalized / 60
    let suffix = hour >= 12 ? "PM" : "AM"
    let displayHour = (hour % 12 == 0) ? 12 : hour % 12
    let mm = String(format: "%02d", normalized % 60)
    return "\(displayHour):\(mm) \(suffix)"
}

// MARK: - AI insight prompt

/// Prompt fed to the GenAI assist gateway to reflect on recent entries. Deliberately short and bounded
/// (last 14 entries, snippets trimmed to 140 chars) so it stays within on-device limits. Entries are
/// taken newest-first in the order supplied (caller sorts). Mirrors `buildJournalInsightPrompt`.
public func buildJournalInsightPrompt(
    entries: [JournalEntryRecord],
    today: Date,
    calendar: Calendar
) -> String {
    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.timeZone = calendar.timeZone
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"

    let lines = entries.prefix(14).map { entry -> String in
        let mood = entry.dayRating.map { "mood \($0)/5" } ?? "mood —"
        let collapsed = entry.body
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\n", with: " ")
        let snippet = String(collapsed.prefix(140))
        let shown = snippet.isEmpty ? "(mood only)" : snippet
        let day = formatter.string(from: entry.entryDate)
        return "- \(day) (\(mood)): \(shown)"
    }.joined(separator: "\n")

    let todayString = formatter.string(from: today)
    var out = ""
    out += "You are a warm, concise journaling companion. "
    out += "Based on these recent daily reflections, write 2-3 short sentences noticing any patterns "
    out += "in mood or recurring themes, then offer one gentle, encouraging suggestion. "
    out += "Be supportive and specific; avoid clinical or therapy language. "
    out += "Today is \(todayString). Reflections (newest first):\n"
    out += lines
    return out
}
