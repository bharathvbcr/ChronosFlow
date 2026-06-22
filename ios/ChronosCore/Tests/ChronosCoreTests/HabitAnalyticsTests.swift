import XCTest
@testable import ChronosCore

private let utcCal: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()

private func day(_ y: Int, _ m: Int, _ d: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: m, day: d))!
}

/// A date at a specific time-of-day, to prove the analytics normalize to start-of-day.
private func instant(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
}

// MARK: - deriveHabitAnalytics

final class HabitAnalyticsDeriveTests: XCTestCase {

    /// Mirrors Android `deriveHabitAnalytics uses completion history as source of truth`.
    func testCompletionHistoryIsSourceOfTruth() {
        let today = day(2026, 5, 25)
        let events = [
            HabitEvent(type: .completed, eventDate: today, startMinuteOfDay: 8 * 60),
            HabitEvent(type: .completed, eventDate: day(2026, 5, 24), startMinuteOfDay: 8 * 60),
            HabitEvent(type: .missed, eventDate: day(2026, 5, 23)),
            HabitEvent(type: .skipped, eventDate: day(2026, 5, 22)),
        ]
        let a = deriveHabitAnalytics(events: events, today: today, calendar: utcCal)
        // 2 completed / (2 completed + 1 missed + 1 skipped) = 0.5
        XCTAssertEqual(a.adherenceRate, 0.5, accuracy: 0.0001)
        XCTAssertEqual(a.completedCountLast7Days, 2)
        XCTAssertEqual(a.missedCountLast14Days, 1)
        XCTAssertEqual(a.skippedCountLast14Days, 1)
        XCTAssertEqual(a.currentStreak, 2)
        XCTAssertEqual(a.bestCompletionMinuteOfDay, 8 * 60)
    }

    func testEmptyEventsYieldZeroedAnalytics() {
        let a = deriveHabitAnalytics(events: [], today: day(2026, 6, 20), calendar: utcCal)
        XCTAssertEqual(a.adherenceRate, 0)
        XCTAssertEqual(a.completedCountLast7Days, 0)
        XCTAssertEqual(a.missedCountLast14Days, 0)
        XCTAssertEqual(a.skippedCountLast14Days, 0)
        XCTAssertEqual(a.currentStreak, 0)
        XCTAssertNil(a.bestCompletionMinuteOfDay)
    }

    /// Adherence is 0 when only non-counting event types exist (paused/resumed/deferred do not
    /// enter the denominator), and there are no completions/misses/skips.
    func testAdherenceZeroDenominator() {
        let today = day(2026, 6, 20)
        let events = [
            HabitEvent(type: .paused, eventDate: today),
            HabitEvent(type: .resumed, eventDate: today),
            HabitEvent(type: .deferred, eventDate: today),
        ]
        let a = deriveHabitAnalytics(events: events, today: today, calendar: utcCal)
        XCTAssertEqual(a.adherenceRate, 0)
        XCTAssertEqual(a.missedCountLast14Days, 0)
        XCTAssertEqual(a.skippedCountLast14Days, 0)
    }

    /// Events older than 14 days are excluded from every rollup, and completions older than 7 days
    /// drop out of the 7-day count but stay in the 14-day adherence.
    func testWindowBoundaries() {
        let today = day(2026, 6, 20)
        let events = [
            // Day 14 ago = today-13: inside the 14-day window (boundary inclusive).
            HabitEvent(type: .completed, eventDate: day(2026, 6, 7), startMinuteOfDay: 9 * 60),
            // Day 15 ago = today-14: outside the 14-day window.
            HabitEvent(type: .completed, eventDate: day(2026, 6, 6), startMinuteOfDay: 9 * 60),
            // 8 days ago: in 14-day, out of 7-day.
            HabitEvent(type: .completed, eventDate: day(2026, 6, 12), startMinuteOfDay: 9 * 60),
            // today-6: boundary of 7-day window (inclusive).
            HabitEvent(type: .completed, eventDate: day(2026, 6, 14), startMinuteOfDay: 9 * 60),
        ]
        let a = deriveHabitAnalytics(events: events, today: today, calendar: utcCal)
        // 3 of the 4 completions land in the 14-day window.
        XCTAssertEqual(a.completedCountLast7Days, 1) // only today-6
        // adherence = 3 completed / 3 (no missed/skipped) = 1.0
        XCTAssertEqual(a.adherenceRate, 1.0, accuracy: 0.0001)
        XCTAssertEqual(a.currentStreak, 0) // none of these are today/yesterday
    }

    /// Best completion minute is the mode; ties break toward the higher count, then the later minute.
    func testBestCompletionMinuteTieBreak() {
        let today = day(2026, 6, 20)
        let events = [
            // 7:00 appears twice, 9:00 appears twice -> tie on count -> later minute (9:00) wins.
            HabitEvent(type: .completed, eventDate: day(2026, 6, 20), startMinuteOfDay: 7 * 60),
            HabitEvent(type: .completed, eventDate: day(2026, 6, 19), startMinuteOfDay: 7 * 60),
            HabitEvent(type: .completed, eventDate: day(2026, 6, 18), startMinuteOfDay: 9 * 60),
            HabitEvent(type: .completed, eventDate: day(2026, 6, 17), startMinuteOfDay: 9 * 60),
        ]
        let a = deriveHabitAnalytics(events: events, today: today, calendar: utcCal)
        XCTAssertEqual(a.bestCompletionMinuteOfDay, 9 * 60)
    }

    /// A clear mode wins regardless of minute ordering (higher count beats later minute).
    func testBestCompletionMinuteByCount() {
        let today = day(2026, 6, 20)
        let events = [
            HabitEvent(type: .completed, eventDate: day(2026, 6, 20), startMinuteOfDay: 6 * 60),
            HabitEvent(type: .completed, eventDate: day(2026, 6, 19), startMinuteOfDay: 6 * 60),
            HabitEvent(type: .completed, eventDate: day(2026, 6, 18), startMinuteOfDay: 6 * 60),
            HabitEvent(type: .completed, eventDate: day(2026, 6, 17), startMinuteOfDay: 23 * 60),
        ]
        let a = deriveHabitAnalytics(events: events, today: today, calendar: utcCal)
        XCTAssertEqual(a.bestCompletionMinuteOfDay, 6 * 60)
    }

    /// Completions without a minute contribute to counts/streak but never to bestCompletionMinute.
    func testBestCompletionMinuteNilWhenNoMinutes() {
        let today = day(2026, 6, 20)
        let a = deriveHabitAnalytics(
            events: [HabitEvent(type: .completed, eventDate: today, startMinuteOfDay: nil)],
            today: today, calendar: utcCal
        )
        XCTAssertNil(a.bestCompletionMinuteOfDay)
        XCTAssertEqual(a.currentStreak, 1)
    }

    /// Event dates carrying a time-of-day are normalized to the calendar day.
    func testInstantNormalizedToDay() {
        let today = day(2026, 6, 20)
        let events = [
            HabitEvent(type: .completed, eventDate: instant(2026, 6, 20, 23, 59), startMinuteOfDay: 23 * 60 + 59),
            HabitEvent(type: .completed, eventDate: instant(2026, 6, 19, 0, 1), startMinuteOfDay: 0),
        ]
        let a = deriveHabitAnalytics(events: events, today: today, calendar: utcCal)
        XCTAssertEqual(a.currentStreak, 2)
        XCTAssertEqual(a.completedCountLast7Days, 2)
    }
}

// MARK: - Streak grace period (mirrors HabitStreakWindowTest)

final class HabitAnalyticsStreakTests: XCTestCase {

    func testLateNightCompletionCountsNextDay() {
        let today = day(2026, 6, 20)
        let tomorrow = day(2026, 6, 21)
        let event = HabitEvent(type: .completed, eventDate: today, startMinuteOfDay: 23 * 60 + 59)
        let a = deriveHabitAnalytics(events: [event], today: tomorrow, calendar: utcCal)
        XCTAssertEqual(a.currentStreak, 1, "A 23:59 completion must count as yesterday at 00:01")
    }

    func testCompletionOnSameDayStreakIsOne() {
        let today = day(2026, 6, 20)
        let event = HabitEvent(type: .completed, eventDate: today, startMinuteOfDay: 8 * 60)
        XCTAssertEqual(deriveHabitAnalytics(events: [event], today: today, calendar: utcCal).currentStreak, 1)
    }

    func testTwoConsecutiveDaysStreakIsTwo() {
        let today = day(2026, 6, 20)
        let events = [
            HabitEvent(type: .completed, eventDate: today, startMinuteOfDay: 23 * 60 + 50),
            HabitEvent(type: .completed, eventDate: day(2026, 6, 19), startMinuteOfDay: 7 * 60),
        ]
        XCTAssertEqual(deriveHabitAnalytics(events: events, today: today, calendar: utcCal).currentStreak, 2)
    }

    func testGapBreaksStreak() {
        let today = day(2026, 6, 20)
        let events = [
            HabitEvent(type: .completed, eventDate: today, startMinuteOfDay: 9 * 60),
            // yesterday missing
            HabitEvent(type: .completed, eventDate: day(2026, 6, 18), startMinuteOfDay: 9 * 60),
        ]
        XCTAssertEqual(deriveHabitAnalytics(events: events, today: today, calendar: utcCal).currentStreak, 1)
    }

    func testNoCompletionsStreakIsZero() {
        XCTAssertEqual(
            deriveHabitAnalytics(events: [], today: day(2026, 6, 20), calendar: utcCal).currentStreak, 0
        )
    }

    /// A completion two days ago (with neither today nor yesterday completed) yields streak 0:
    /// the grace-period walk starts at yesterday, which is empty.
    func testStaleCompletionStreakIsZero() {
        let today = day(2026, 6, 20)
        let event = HabitEvent(type: .completed, eventDate: day(2026, 6, 18), startMinuteOfDay: 9 * 60)
        XCTAssertEqual(deriveHabitAnalytics(events: [event], today: today, calendar: utcCal).currentStreak, 0)
    }
}

// MARK: - Consistency headline

final class HabitConsistencyHeadlineTests: XCTestCase {
    func testEmptyWindow() {
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 0, windowDays: 0),
                       "Complete a habit to start your trend.")
    }

    func testTiers() {
        // ratio >= 0.85
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 13, windowDays: 14),
                       "On fire — almost every day counts.")
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 14, windowDays: 14),
                       "On fire — almost every day counts.")
        // 0.5 <= ratio < 0.85
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 7, windowDays: 14),
                       "Strong rhythm. Keep it rolling.")
        // 0.25 <= ratio < 0.5
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 4, windowDays: 14),
                       "Building momentum, one day at a time.")
        // ratio < 0.25
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 1, windowDays: 14),
                       "Every completed day moves you forward.")
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 0, windowDays: 14),
                       "Every completed day moves you forward.")
    }

    func testExactBoundaryRatios() {
        // exactly 0.85
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 17, windowDays: 20),
                       "On fire — almost every day counts.")
        // exactly 0.5
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 5, windowDays: 10),
                       "Strong rhythm. Keep it rolling.")
        // exactly 0.25
        XCTAssertEqual(habitConsistencyHeadline(activeDays: 1, windowDays: 4),
                       "Building momentum, one day at a time.")
    }
}

// MARK: - Streak milestone label

final class HabitStreakMilestoneLabelTests: XCTestCase {
    func testBelowFirstTierReturnsNil() {
        XCTAssertNil(habitStreakMilestoneLabel(0))
        XCTAssertNil(habitStreakMilestoneLabel(6))
    }

    func testExactTiers() {
        XCTAssertEqual(habitStreakMilestoneLabel(7), "🔥 7-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(14), "🔥 14-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(30), "🔥 30-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(50), "🔥 50-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(100), "🔥 100-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(365), "🔥 365-day streak")
    }

    func testBetweenTiersReportsHighestReached() {
        XCTAssertEqual(habitStreakMilestoneLabel(13), "🔥 7-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(49), "🔥 30-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(200), "🔥 100-day streak")
        XCTAssertEqual(habitStreakMilestoneLabel(1000), "🔥 365-day streak")
    }
}

// MARK: - missedForRepair

final class HabitMissedForRepairTests: XCTestCase {
    private let today = day(2026, 5, 8)

    private func candidate(
        id: String,
        windowEndMinute: Int,
        lastCompletedDate: Date? = nil,
        isActive: Bool = true,
        pausedUntil: Date? = nil,
        skipDate: Date? = nil
    ) -> HabitRepairCandidate {
        HabitRepairCandidate(
            id: id, isActive: isActive, windowEndMinute: windowEndMinute,
            lastCompletedDate: lastCompletedDate, pausedUntil: pausedUntil, skipDate: skipDate
        )
    }

    /// Mirrors Android `missedForRepairReturnsPastDueUncompletedActiveHabits`.
    func testReturnsPastDueUncompletedActive() {
        let habits = [
            candidate(id: "past", windowEndMinute: 9 * 60),
            candidate(id: "future", windowEndMinute: 20 * 60),
            candidate(id: "done", windowEndMinute: 8 * 60, lastCompletedDate: today),
            candidate(id: "archived", windowEndMinute: 7 * 60, isActive: false),
        ]
        let missed = missedForRepair(habits: habits, today: today, currentMinute: 10 * 60, calendar: utcCal)
        XCTAssertEqual(missed.map(\.id), ["past"])
    }

    /// Mirrors Android `missedForRepairReturnsEmptyWhenNothingIsMissed`.
    func testEmptyWhenNothingMissed() {
        let habits = [
            candidate(id: "current", windowEndMinute: 12 * 60),
            candidate(id: "done", windowEndMinute: 8 * 60, lastCompletedDate: today),
        ]
        let missed = missedForRepair(habits: habits, today: today, currentMinute: 9 * 60, calendar: utcCal)
        XCTAssertTrue(missed.isEmpty)
    }

    func testSortedByWindowEnd() {
        let habits = [
            candidate(id: "late", windowEndMinute: 11 * 60),
            candidate(id: "early", windowEndMinute: 7 * 60),
            candidate(id: "mid", windowEndMinute: 9 * 60),
        ]
        let missed = missedForRepair(habits: habits, today: today, currentMinute: 12 * 60, calendar: utcCal)
        XCTAssertEqual(missed.map(\.id), ["early", "mid", "late"])
    }

    func testSkippedTodayExcluded() {
        let habits = [
            candidate(id: "skipped", windowEndMinute: 8 * 60, skipDate: today),
            candidate(id: "skipped-earlier", windowEndMinute: 8 * 60, skipDate: day(2026, 5, 7)),
        ]
        let missed = missedForRepair(habits: habits, today: today, currentMinute: 10 * 60, calendar: utcCal)
        // Only the habit skipped on a prior day remains eligible.
        XCTAssertEqual(missed.map(\.id), ["skipped-earlier"])
    }

    func testPausedExclusionRules() {
        let habits = [
            // Paused through today -> excluded (pausedUntil not before today).
            candidate(id: "paused-today", windowEndMinute: 8 * 60, pausedUntil: today),
            // Paused into the future -> excluded.
            candidate(id: "paused-future", windowEndMinute: 8 * 60, pausedUntil: day(2026, 5, 10)),
            // Pause ended yesterday -> eligible again.
            candidate(id: "pause-ended", windowEndMinute: 8 * 60, pausedUntil: day(2026, 5, 7)),
            // Never paused -> eligible.
            candidate(id: "never-paused", windowEndMinute: 8 * 60, pausedUntil: nil),
        ]
        let missed = missedForRepair(habits: habits, today: today, currentMinute: 10 * 60, calendar: utcCal)
        XCTAssertEqual(Set(missed.map(\.id)), ["pause-ended", "never-paused"])
    }

    /// A completion logged earlier today blocks repair; completion on a prior day does not.
    func testCompletedTodayExcludedButPriorCompletionEligible() {
        let habits = [
            candidate(id: "done-today", windowEndMinute: 8 * 60, lastCompletedDate: instant(2026, 5, 8, 7, 30)),
            candidate(id: "done-yesterday", windowEndMinute: 8 * 60, lastCompletedDate: day(2026, 5, 7)),
        ]
        let missed = missedForRepair(habits: habits, today: today, currentMinute: 10 * 60, calendar: utcCal)
        XCTAssertEqual(missed.map(\.id), ["done-yesterday"])
    }

    /// Window-end exactly equal to currentMinute is not yet "closed" (Android uses strict <).
    func testWindowEndEqualToCurrentMinuteNotMissed() {
        let habits = [candidate(id: "edge", windowEndMinute: 10 * 60)]
        let missed = missedForRepair(habits: habits, today: today, currentMinute: 10 * 60, calendar: utcCal)
        XCTAssertTrue(missed.isEmpty)
    }
}
