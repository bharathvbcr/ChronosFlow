import Foundation
import SwiftData
import ChronosCore

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

    /// Days (start-of-day) the user explicitly skipped this habit, the iOS analogue of the
    /// SKIPPED `HabitEvent`s and the schedule's `skipDate`. Skipped days don't break a streak
    /// but also don't count as completions, and exclude the habit from missed-for-repair.
    /// Optional-by-default ([]) for CloudKit. Mirrors Android's skip handling.
    var skipDates: [Date]

    /// Day (start-of-day) the habit is paused until, inclusive — while today < pausedUntil the
    /// habit is hidden from the planner / reminders / repair. nil = not paused. Mirrors
    /// Android's `HabitSchedule.pausedUntil`.
    var pausedUntil: Date?

    /// One-off deferral: the minute-of-day (0..1439) to surface the habit at today instead of its
    /// usual window start, e.g. "remind me in 30 min". nil = no deferral. Mirrors Android's
    /// `HabitSchedule.deferUntilMinuteOfDay`.
    var deferUntilMinuteOfDay: Int?

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
        completionDates: [Date] = [],
        skipDates: [Date] = [],
        pausedUntil: Date? = nil,
        deferUntilMinuteOfDay: Int? = nil
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
        self.skipDates = skipDates
        self.pausedUntil = pausedUntil
        self.deferUntilMinuteOfDay = deferUntilMinuteOfDay
    }

    func isCompleted(on day: Date) -> Bool {
        let start = Calendar.current.startOfDay(for: day)
        return completionDates.contains { Calendar.current.isDate($0, inSameDayAs: start) }
    }

    func isSkipped(on day: Date) -> Bool {
        skipDates.contains { Calendar.current.isDate($0, inSameDayAs: day) }
    }

    /// Whether this habit's cadence schedules it to occur on `day` (every-N-days / weekly-interval /
    /// quota aware, via ChronosCore `habitIsDue`). Paused/skipped state is separate — this answers
    /// only "does the schedule land here today". A quota habit stays due until its per-period target
    /// is met, so a same-day completion flips it to not-due.
    func isDue(on day: Date = .now) -> Bool {
        habitIsDue(cadence: cadence, on: day, completions: completionDates)
    }

    /// True while the habit is paused for the given day (today < pausedUntil, inclusive of the
    /// pause day). Mirrors Android's `pausedUntil?.let { !it.isBefore(date) } == true`.
    func isPaused(on day: Date = .now) -> Bool {
        guard let pausedUntil else { return false }
        let cal = Calendar.current
        return cal.startOfDay(for: pausedUntil) >= cal.startOfDay(for: day)
    }

    /// Toggle an explicit skip for a day. Skipping removes any logged completion for that day
    /// (a day is either completed or skipped, not both) and recomputes the streak.
    func toggleSkip(on day: Date) {
        let cal = Calendar.current
        let start = cal.startOfDay(for: day)
        if let idx = skipDates.firstIndex(where: { cal.isDate($0, inSameDayAs: start) }) {
            skipDates.remove(at: idx)
        } else {
            skipDates.append(start)
            if let completedIdx = completionDates.firstIndex(where: { cal.isDate($0, inSameDayAs: start) }) {
                completionDates.remove(at: completedIdx)
            }
            recomputeStreak()
        }
    }

    /// Pause the habit for `days` (clamped to at least 1) starting today, mirroring Android's
    /// `pausePlan`/`pauseHabit` clamp. Clears any active deferral.
    func pause(forDays days: Int, from today: Date = .now) {
        let cal = Calendar.current
        let clamped = max(days, 1)
        pausedUntil = cal.date(byAdding: .day, value: clamped, to: cal.startOfDay(for: today))
        deferUntilMinuteOfDay = nil
    }

    /// Resume a paused habit, clearing the pause and any deferral. Mirrors Android's resume.
    func resume() {
        pausedUntil = nil
        deferUntilMinuteOfDay = nil
    }

    /// Defer today's reminder by `minutes` from `nowMinute`, capped at 23:59 like Android's
    /// `deferHabit` (`(nowMinute + minutes).coerceAtMost(23*60+59)`).
    func deferReminder(byMinutes minutes: Int, nowMinute: Int) {
        deferUntilMinuteOfDay = min(nowMinute + minutes, 23 * 60 + 59)
    }

    /// The minute-of-day this habit should surface at today: the one-off deferral if set,
    /// otherwise the usual window start. Mirrors Android's
    /// `deferUntilMinuteOfDay ?: targetStartMinute`.
    var effectiveStartMinute: Int { deferUntilMinuteOfDay ?? windowStartMinute }

    // MARK: - ChronosCore bridges

    /// The most recent skip day (start-of-day), the single-value analogue of Android's
    /// `HabitSchedule.skipDate` used by `missedForRepair`.
    var latestSkipDate: Date? {
        skipDates.map { Calendar.current.startOfDay(for: $0) }.max()
    }

    /// Project this habit onto the ChronosCore `HabitRepairCandidate` so repair selection reuses
    /// the shared `missedForRepair` logic instead of duplicating it here.
    var repairCandidate: HabitRepairCandidate {
        HabitRepairCandidate(
            id: id,
            isActive: isActive,
            windowEndMinute: windowEndMinute,
            lastCompletedDate: lastCompletedDate,
            pausedUntil: pausedUntil,
            skipDate: latestSkipDate
        )
    }

    /// Build the ChronosCore `HabitEvent` log for this habit (completions + skips) so callers can
    /// reuse `deriveHabitAnalytics` / `habitConsistencyHeadline` rather than re-implementing them.
    func coreEvents() -> [HabitEvent] {
        completionDates.map { HabitEvent(type: .completed, eventDate: $0) }
            + skipDates.map { HabitEvent(type: .skipped, eventDate: $0) }
    }

    /// Rolled-up analytics via the shared ChronosCore derivation.
    func analytics(today: Date = .now, calendar: Calendar = .current) -> HabitAnalytics {
        deriveHabitAnalytics(events: coreEvents(), today: today, calendar: calendar)
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
