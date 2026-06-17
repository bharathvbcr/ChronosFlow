import Foundation

// Habit repair — port of the "missed-habit repair suggestions" feature. For a habit that hasn't been
// done yet today, find the earliest free slot inside its preferred window that fits its expected
// duration, so the user can still keep the streak.

public struct HabitRepairSuggestion: Sendable, Equatable {
    public let startMinute: Int
    public let durationMinutes: Int
}

/// Suggest a slot for a missed habit. Searches its window [windowStart, windowEnd] for the first gap
/// of at least `duration` minutes not covered by `busy` spans and not already past (`nowMinute`).
/// Returns nil if the window has no room left today.
public func suggestHabitRepair(
    windowStart: Int, windowEnd: Int, durationMinutes duration: Int,
    busy: [(start: Int, end: Int)], nowMinute: Int
) -> HabitRepairSuggestion? {
    let dur = max(duration, 1)
    let lowerBound = max(windowStart, nowMinute)
    guard lowerBound + dur <= windowEnd else { return nil }
    let spans = busy
        .map { (start: max($0.start, windowStart), end: min($0.end, windowEnd)) }
        .filter { $0.start < $0.end }
        .sorted { $0.start < $1.start }

    var cursor = lowerBound
    for span in spans {
        if span.start - cursor >= dur { return HabitRepairSuggestion(startMinute: cursor, durationMinutes: dur) }
        cursor = max(cursor, span.end)
    }
    return cursor + dur <= windowEnd ? HabitRepairSuggestion(startMinute: cursor, durationMinutes: dur) : nil
}
