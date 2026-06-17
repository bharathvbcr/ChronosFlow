import Foundation
import SwiftData

/// A repeating routine with a completion window, streaks, and flexible cadence.
/// Ported from `Habit.kt` (+ a flattened slice of its schedule/analytics companions).
@Model
final class Habit {
    @Attribute(.unique) var id: String
    var title: String
    /// e.g. "DAILY", "WEEKLY", or "3x/week" — kept as a string like the Android `cadence`.
    var cadence: String
    var windowStartMinute: Int
    var windowEndMinute: Int
    /// 1..5
    var difficulty: Int
    var isBundled: Bool
    var streakCount: Int
    var lastCompletedDate: Date?
    var isActive: Bool
    var goalID: String?

    /// Logged completion dates (start-of-day), the iOS analogue of `recentEvents`.
    var completionDates: [Date]

    init(
        id: String = UUID().uuidString,
        title: String,
        cadence: String = "DAILY",
        windowStartMinute: Int = 6 * 60,
        windowEndMinute: Int = 22 * 60,
        difficulty: Int = 2,
        isBundled: Bool = false,
        streakCount: Int = 0,
        lastCompletedDate: Date? = nil,
        isActive: Bool = true,
        goalID: String? = nil,
        completionDates: [Date] = []
    ) {
        self.id = id
        self.title = title
        self.cadence = cadence
        self.windowStartMinute = windowStartMinute
        self.windowEndMinute = windowEndMinute
        self.difficulty = difficulty
        self.isBundled = isBundled
        self.streakCount = streakCount
        self.lastCompletedDate = lastCompletedDate
        self.isActive = isActive
        self.goalID = goalID
        self.completionDates = completionDates
    }

    func isCompleted(on day: Date) -> Bool {
        let start = Calendar.current.startOfDay(for: day)
        return completionDates.contains { Calendar.current.isDate($0, inSameDayAs: start) }
    }

    /// Toggle completion for a day and recompute the streak.
    func toggleCompletion(on day: Date) {
        let cal = Calendar.current
        let start = cal.startOfDay(for: day)
        if let idx = completionDates.firstIndex(where: { cal.isDate($0, inSameDayAs: start) }) {
            completionDates.remove(at: idx)
        } else {
            completionDates.append(start)
            lastCompletedDate = start
        }
        recomputeStreak()
    }

    private func recomputeStreak() {
        let cal = Calendar.current
        let days = Set(completionDates.map { cal.startOfDay(for: $0) })
        var streak = 0
        var cursor = cal.startOfDay(for: .now)
        // Allow today to be incomplete without breaking yesterday's streak.
        if !days.contains(cursor) { cursor = cal.date(byAdding: .day, value: -1, to: cursor)! }
        while days.contains(cursor) {
            streak += 1
            cursor = cal.date(byAdding: .day, value: -1, to: cursor)!
        }
        streakCount = streak
    }
}
