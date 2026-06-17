import Foundation

// Recurrence expansion — port of the planner recurrence rules (PlannerRecurrence / RecurrenceRule).
// Expands a rule into concrete occurrence dates within a range. Pure Foundation (Calendar) so it is
// fully testable off-device.

public enum RecurrenceFrequency: String, Sendable, Equatable {
    case daily, weekly, monthly
}

public struct RecurrenceRule: Sendable, Equatable {
    public var frequency: RecurrenceFrequency
    public var interval: Int                 // every N days/weeks/months
    public var weekdays: Set<Int>            // for .weekly: 1=Sun…7=Sat (Calendar.component(.weekday))
    public var count: Int?                   // optional max occurrences
    public init(frequency: RecurrenceFrequency, interval: Int = 1,
                weekdays: Set<Int> = [], count: Int? = nil) {
        self.frequency = frequency
        self.interval = max(interval, 1)
        self.weekdays = weekdays
        self.count = count
    }
}

/// Expand `rule` from `start` through `end` (inclusive), returning start-of-day dates.
/// Uses the supplied `calendar` (defaults to current) so tests are deterministic.
public func expandRecurrence(
    _ rule: RecurrenceRule, from start: Date, to end: Date, calendar: Calendar = .current
) -> [Date] {
    guard start <= end else { return [] }
    var cal = calendar
    cal.timeZone = calendar.timeZone
    let startDay = cal.startOfDay(for: start)
    let endDay = cal.startOfDay(for: end)
    var result: [Date] = []

    switch rule.frequency {
    case .daily:
        var day = startDay
        var step = 0
        while day <= endDay {
            if step % rule.interval == 0 { result.append(day) }
            step += 1
            guard let next = cal.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
            if let c = rule.count, result.count >= c { break }
        }
    case .weekly:
        // Walk day by day; include matching weekdays, honoring the week interval from the anchor week.
        let anchorWeek = cal.component(.weekOfYear, from: startDay)
        let anchorYear = cal.component(.yearForWeekOfYear, from: startDay)
        var day = startDay
        while day <= endDay {
            let wd = cal.component(.weekday, from: day)
            let weeksApart = weekIndex(day, cal: cal) - (anchorYear * 53 + anchorWeek)
            let matchesWeekday = rule.weekdays.isEmpty
                ? cal.component(.weekday, from: day) == cal.component(.weekday, from: startDay)
                : rule.weekdays.contains(wd)
            if matchesWeekday && weeksApart % rule.interval == 0 && weeksApart >= 0 {
                result.append(day)
            }
            guard let next = cal.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
            if let c = rule.count, result.count >= c { break }
        }
    case .monthly:
        let targetDay = cal.component(.day, from: startDay)
        var month = startDay
        var step = 0
        while month <= endDay {
            if step % rule.interval == 0,
               let occ = sameDayOfMonth(month, day: targetDay, cal: cal), occ <= endDay, occ >= startDay {
                result.append(occ)
            }
            step += 1
            guard let next = cal.date(byAdding: .month, value: 1, to: month) else { break }
            month = next
            if let c = rule.count, result.count >= c { break }
        }
    }

    if let c = rule.count, result.count > c { result = Array(result.prefix(c)) }
    return result
}

private func weekIndex(_ date: Date, cal: Calendar) -> Int {
    cal.component(.yearForWeekOfYear, from: date) * 53 + cal.component(.weekOfYear, from: date)
}

private func sameDayOfMonth(_ monthDate: Date, day: Int, cal: Calendar) -> Date? {
    var comps = cal.dateComponents([.year, .month], from: monthDate)
    let range = cal.range(of: .day, in: .month, for: monthDate)
    comps.day = min(day, range?.count ?? day)
    return comps.isValidDate(in: cal) ? cal.date(from: comps).map { cal.startOfDay(for: $0) } : cal.date(from: comps)
}
