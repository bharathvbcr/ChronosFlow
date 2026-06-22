import Foundation

// Habit analytics helpers — Foundation-only port of the Android habit analytics derivation
// (core/domain HabitPlannerModels.deriveHabitAnalytics) plus the consistency headline, streak
// milestone label, and missed-for-repair selection (feature/habits + core/ai
// HabitRepairAssistPlanner.missedForRepair). All functions are pure and date-injected: the
// reference "today" and the Calendar are parameters so nothing reads the wall clock. Dates that
// represent calendar days are normalized to start-of-day with the supplied calendar, mirroring the
// Android code's use of LocalDate (which has no time component) for event/streak math.
//
// Reuses HabitStreak.swift / HabitRepair.swift where applicable; the streak walk-back here matches
// currentStreak's grace-period behaviour but is computed inline so the analytics rollup stays a
// single pass over the (already-filtered) completion set.

// MARK: - Events

/// The kind of a logged habit event. Mirrors Android's `HabitEventType`.
public enum HabitEventType: String, Codable, CaseIterable, Sendable {
    case completed, skipped, paused, resumed, missed, deferred
}

/// A single logged habit event. `eventDate` is the calendar day the event belongs to (any instant
/// within the day is fine — it is normalized to start-of-day before use). `startMinuteOfDay` is the
/// minute-of-day the habit was actually performed, used to derive the best completion time.
/// Mirrors the load-bearing fields of Android's `HabitEvent`.
public struct HabitEvent: Sendable, Equatable {
    public let type: HabitEventType
    public let eventDate: Date
    public let startMinuteOfDay: Int?

    public init(type: HabitEventType, eventDate: Date, startMinuteOfDay: Int? = nil) {
        self.type = type
        self.eventDate = eventDate
        self.startMinuteOfDay = startMinuteOfDay
    }
}

/// Rolled-up analytics for one habit over the trailing window. Mirrors Android's `HabitAnalytics`.
public struct HabitAnalytics: Sendable, Equatable {
    public let adherenceRate: Double
    public let completedCountLast7Days: Int
    public let missedCountLast14Days: Int
    public let skippedCountLast14Days: Int
    public let currentStreak: Int
    public let bestCompletionMinuteOfDay: Int?

    public init(
        adherenceRate: Double = 0,
        completedCountLast7Days: Int = 0,
        missedCountLast14Days: Int = 0,
        skippedCountLast14Days: Int = 0,
        currentStreak: Int = 0,
        bestCompletionMinuteOfDay: Int? = nil
    ) {
        self.adherenceRate = adherenceRate
        self.completedCountLast7Days = completedCountLast7Days
        self.missedCountLast14Days = missedCountLast14Days
        self.skippedCountLast14Days = skippedCountLast14Days
        self.currentStreak = currentStreak
        self.bestCompletionMinuteOfDay = bestCompletionMinuteOfDay
    }
}

/// Derive habit analytics from an event log. Ports Android `deriveHabitAnalytics`:
/// - The window is the last 14 calendar days (today and the 13 prior days).
/// - `adherenceRate` = completed / (completed + missed + skipped) over 14 days, 0 when the
///   denominator is 0.
/// - `completedCountLast7Days` counts COMPLETED events on today..today-6.
/// - `bestCompletionMinuteOfDay` is the most frequent completion start minute among the 14-day
///   COMPLETED events; ties break toward the higher count, then toward the *later* minute (matching
///   Android's `compareBy { value }.thenByDescending { key }`). Nil when no completion has a minute.
/// - `currentStreak` walks back over COMPLETED days with a grace period: if today has no completion
///   yet, the walk starts from yesterday so a late-night completion isn't dropped at 00:01.
public func deriveHabitAnalytics(
    events: [HabitEvent],
    today: Date = Date(),
    calendar: Calendar = .current
) -> HabitAnalytics {
    let cal = calendar
    let todayDay = cal.startOfDay(for: today)
    // Start-of-day for the 14-day and 7-day window lower bounds (inclusive).
    let window14Start = cal.date(byAdding: .day, value: -13, to: todayDay) ?? todayDay
    let window7Start = cal.date(byAdding: .day, value: -6, to: todayDay) ?? todayDay

    // Normalize each event's date to its start-of-day once.
    let recent14 = events
        .map { (type: $0.type, day: cal.startOfDay(for: $0.eventDate), minute: $0.startMinuteOfDay) }
        .filter { $0.day >= window14Start }

    let completed14 = recent14.filter { $0.type == .completed }
    let missed14 = recent14.filter { $0.type == .missed }.count
    let skipped14 = recent14.filter { $0.type == .skipped }.count

    let denominator = completed14.count + missed14 + skipped14
    let adherence = denominator == 0 ? 0.0 : Double(completed14.count) / Double(denominator)

    let completed7 = completed14.filter { $0.day >= window7Start }.count

    // Best completion minute: mode of the completion start minutes. Tie-break: higher count first,
    // then the later minute (descending key), matching Android's comparator exactly.
    var minuteCounts: [Int: Int] = [:]
    for entry in completed14 {
        if let minute = entry.minute { minuteCounts[minute, default: 0] += 1 }
    }
    let bestMinute: Int? = minuteCounts
        .max { a, b in
            if a.value != b.value { return a.value < b.value }
            return a.key < b.key
        }?
        .key

    // Streak walk-back over the COMPLETED calendar days.
    let completedDays = Set(completed14.map { $0.day })
    var streak = 0
    var cursor = completedDays.contains(todayDay)
        ? todayDay
        : (cal.date(byAdding: .day, value: -1, to: todayDay) ?? todayDay)
    // Guard against the degenerate case where the grace-period step failed.
    if completedDays.contains(cursor) {
        while completedDays.contains(cursor) {
            streak += 1
            guard let prev = cal.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = prev
        }
    }

    return HabitAnalytics(
        adherenceRate: adherence,
        completedCountLast7Days: completed7,
        missedCountLast14Days: missed14,
        skippedCountLast14Days: skipped14,
        currentStreak: streak,
        bestCompletionMinuteOfDay: bestMinute
    )
}

// MARK: - Consistency headline

/// Encouraging headline for the 14-day consistency card, keyed off how many of the `windowDays` had
/// at least one completion. Ports Android `habitConsistencyHeadline` (HabitConsistencyCard.kt),
/// including the empty-window copy and the 0.85 / 0.5 / 0.25 ratio tiers.
public func habitConsistencyHeadline(activeDays: Int, windowDays: Int) -> String {
    if windowDays == 0 { return "Complete a habit to start your trend." }
    let ratio = Double(activeDays) / Double(windowDays)
    switch ratio {
    case let r where r >= 0.85: return "On fire — almost every day counts."
    case let r where r >= 0.5: return "Strong rhythm. Keep it rolling."
    case let r where r >= 0.25: return "Building momentum, one day at a time."
    default: return "Every completed day moves you forward."
    }
}

// MARK: - Streak milestone

/// Highest milestone tiers a streak can reach, checked from largest to smallest.
private let habitStreakMilestoneTiers = [365, 100, 50, 30, 14, 7]

/// Celebratory label for the highest streak milestone `streak` has reached, or nil below the first
/// tier (7 days). Ports Android `habitStreakMilestoneLabel` (HabitScreen.kt).
public func habitStreakMilestoneLabel(_ streak: Int) -> String? {
    guard let tier = habitStreakMilestoneTiers.first(where: { streak >= $0 }) else { return nil }
    return "🔥 \(tier)-day streak"
}

// MARK: - Missed-for-repair selection

/// The subset of a habit needed to decide whether it is a candidate for repair. The iOS target maps
/// its `Habit` @Model rows onto these. Day-typed fields (`lastCompletedDate`, `pausedUntil`,
/// `skipDate`) are compared as calendar days.
public struct HabitRepairCandidate: Sendable, Equatable {
    public let id: String
    public let isActive: Bool
    public let windowEndMinute: Int
    public let lastCompletedDate: Date?
    public let pausedUntil: Date?
    public let skipDate: Date?

    public init(
        id: String,
        isActive: Bool,
        windowEndMinute: Int,
        lastCompletedDate: Date? = nil,
        pausedUntil: Date? = nil,
        skipDate: Date? = nil
    ) {
        self.id = id
        self.isActive = isActive
        self.windowEndMinute = windowEndMinute
        self.lastCompletedDate = lastCompletedDate
        self.pausedUntil = pausedUntil
        self.skipDate = skipDate
    }
}

/// Select the active habits whose window has already closed today without a completion, excluding
/// paused (pausedUntil today-or-later) and skipped-today habits. Sorted by window end (earliest
/// closed first). Ports Android `List<Habit>.missedForRepair` (HabitRepairAssistPlanner.kt):
///
/// `isActive && (pausedUntil == null || pausedUntil < today) && skipDate != today &&
///  windowEndMinute < currentMinute && lastCompletedDate != today`.
public func missedForRepair(
    habits: [HabitRepairCandidate],
    today: Date = Date(),
    currentMinute: Int,
    calendar: Calendar = .current
) -> [HabitRepairCandidate] {
    let cal = calendar
    let todayDay = cal.startOfDay(for: today)
    func sameDay(_ date: Date?) -> Bool {
        guard let date else { return false }
        return cal.startOfDay(for: date) == todayDay
    }
    return habits
        .filter { habit in
            guard habit.isActive else { return false }
            // Not paused: no pause set, or the pause ended before today.
            let notPaused = habit.pausedUntil.map { cal.startOfDay(for: $0) < todayDay } ?? true
            guard notPaused else { return false }
            guard !sameDay(habit.skipDate) else { return false }
            guard habit.windowEndMinute < currentMinute else { return false }
            guard !sameDay(habit.lastCompletedDate) else { return false }
            return true
        }
        .sorted { $0.windowEndMinute < $1.windowEndMinute }
}
