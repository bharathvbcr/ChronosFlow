import Foundation

// Habit streak computation — port of the streak logic in the iOS Habit model / Android habit
// analytics. A streak is the run of consecutive completed days ending today (or yesterday, so an
// as-yet-incomplete today doesn't break it).

/// Current streak length given the set of completed days and a reference "today".
public func currentStreak(completionDates: [Date], today: Date = Date(), calendar: Calendar = .current) -> Int {
    let cal = calendar
    let days = Set(completionDates.map { cal.startOfDay(for: $0) })
    var streak = 0
    var cursor = cal.startOfDay(for: today)
    if !days.contains(cursor) {
        guard let yesterday = cal.date(byAdding: .day, value: -1, to: cursor) else { return 0 }
        cursor = yesterday
    }
    while days.contains(cursor) {
        streak += 1
        guard let prev = cal.date(byAdding: .day, value: -1, to: cursor) else { break }
        cursor = prev
    }
    return streak
}

/// Completion rate over the trailing `window` days (e.g. 7 or 30), in 0...1.
public func completionRate(
    completionDates: [Date], window: Int, today: Date = Date(), calendar: Calendar = .current
) -> Double {
    guard window > 0 else { return 0 }
    let cal = calendar
    let days = Set(completionDates.map { cal.startOfDay(for: $0) })
    let start = cal.startOfDay(for: today)
    var hits = 0
    for offset in 0..<window {
        if let d = cal.date(byAdding: .day, value: -offset, to: start), days.contains(d) { hits += 1 }
    }
    return Double(hits) / Double(window)
}
