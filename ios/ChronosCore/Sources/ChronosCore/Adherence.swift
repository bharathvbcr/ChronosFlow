import Foundation

// Medication adherence — port of the medication analytics: adherence rate over a window and a
// refill projection from the dosing cadence.

public struct AdherenceStats: Sendable, Equatable {
    public let takenCount: Int
    public let expectedCount: Int
    public let rate: Double          // 0...1
    public let missedCount: Int
}

/// Adherence over the trailing `days`, given how many doses are expected per day and the set of
/// dates a dose was acknowledged.
public func adherenceStats(
    takenDates: [Date], dosesPerDay: Int, overDays days: Int,
    today: Date = Date(), calendar: Calendar = .current
) -> AdherenceStats {
    let perDay = max(dosesPerDay, 1)
    let window = max(days, 1)
    let cal = calendar
    let start = cal.startOfDay(for: today)
    let validDays: Set<Date> = Set((0..<window).compactMap { cal.date(byAdding: .day, value: -$0, to: start) })

    // Count acknowledged doses that fall on a day within the window, capped at perDay per day.
    var perDayTaken: [Date: Int] = [:]
    for d in takenDates {
        let day = cal.startOfDay(for: d)
        guard validDays.contains(day) else { continue }
        perDayTaken[day, default: 0] += 1
    }
    let taken = perDayTaken.values.reduce(0) { $0 + min($1, perDay) }
    let expected = perDay * window
    let rate = expected > 0 ? Double(taken) / Double(expected) : 0
    return AdherenceStats(takenCount: taken, expectedCount: expected,
                          rate: min(max(rate, 0), 1), missedCount: max(expected - taken, 0))
}

/// Days until refill is needed given remaining doses and doses taken per day. Nil when cadence is 0.
public func daysUntilRefill(remainingDoses: Int, dosesPerDay: Int) -> Int? {
    guard dosesPerDay > 0 else { return nil }
    return max(remainingDoses, 0) / dosesPerDay
}
