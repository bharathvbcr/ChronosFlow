import Foundation

// Habit cadence → "is it due on this day" derivation.
//
// The habit's `cadence` string is the source of truth (same as Android, which parses the stored
// cadence into a `HabitSchedule` / `HabitRecurrenceRule` — see `parseLegacyHabitCadence` in
// core/domain HabitPlannerModels.kt). Parsing the string means no schema change: every existing
// habit keeps working and new every-N-days / quota cadences are just new string forms the editor
// already writes. This mirrors the Android recurrence types (DAILY / WEEKDAYS / WEEKENDS /
// SELECTED_WEEKDAYS / EVERY_N_DAYS / WEEKLY_INTERVAL) and the `HabitRecurrenceRule.Quota` rule.

/// The period a quota cadence counts completions over.
public enum HabitQuotaPeriod: String, Sendable, Equatable {
    case day, week, month
}

/// A parsed habit cadence. Weekday sets use ISO numbering (1 = Monday … 7 = Sunday).
public enum HabitScheduleKind: Sendable, Equatable {
    case daily
    case weekdays                                   // Mon–Fri
    case weekends                                   // Sat–Sun
    case selectedWeekdays(Set<Int>)                 // ISO weekdays
    case everyNDays(Int)
    case weeklyInterval(interval: Int, weekdays: Set<Int>)
    case quota(target: Int, period: HabitQuotaPeriod, interval: Int)
}

/// ISO weekday short names (Mon-first), matching the iOS editor's `habitWeekdayShortNames` and the
/// "Mon/Wed/Fri"-style cadence serialization.
private let isoWeekdayNames: [String: Int] = [
    "mon": 1, "monday": 1, "tue": 2, "tues": 2, "tuesday": 2, "wed": 3, "wednesday": 3,
    "thu": 4, "thur": 4, "thurs": 4, "thursday": 4, "fri": 5, "friday": 5,
    "sat": 6, "saturday": 6, "sun": 7, "sunday": 7
]

/// Map a captured period word ("day"/"week"/"month", already singular from the regex) to the enum.
private func period(_ word: String) -> HabitQuotaPeriod {
    switch word {
    case "day": return .day
    case "month": return .month
    default: return .week
    }
}

private func weekdaySet(from token: String) -> Set<Int> {
    Set(token.split(whereSeparator: { $0 == "/" || $0 == "+" || $0 == "," })
        .compactMap { isoWeekdayNames[$0.trimmingCharacters(in: .whitespaces).lowercased()] })
}

/// Parse a stored cadence string into a `HabitScheduleKind`. Accepts the iOS editor's display forms
/// ("Daily", "Weekly", "Weekdays", "Weekends", "Every N days", "Nx / week", "1x / month",
/// "Mon/Wed/Fri") plus the Android legacy forms ("every N days", "N times per week",
/// "N times every M weeks", "weekly on …"). Unknown input falls back to `.daily`, mirroring Android.
public func parseHabitCadence(_ cadence: String) -> HabitScheduleKind {
    let trimmed = cadence.trimmingCharacters(in: .whitespacesAndNewlines)
    let lower = trimmed.lowercased()

    switch lower {
    case "daily", "": return .daily
    case "weekdays": return .weekdays
    case "weekends": return .weekends
    case "weekly": return .weeklyInterval(interval: 1, weekdays: [])
    default: break
    }

    // "Every N days" (N defaults to 1).
    if let m = firstMatch(#"^every\s+(\d+)\s+days?$"#, lower) {
        return .everyNDays(max(Int(m[1]) ?? 1, 1))
    }
    // "Nx / week", "Nx/week", "N times per week|month|day", legacy "3X/WEEK".
    if let m = firstMatch(#"^(\d+)\s*(?:x|times?)\s*(?:/|per)\s*(day|week|month)s?$"#, lower) {
        return .quota(target: max(Int(m[1]) ?? 1, 1), period: period(m[2]), interval: 1)
    }
    // "N times every M days|weeks|months".
    if let m = firstMatch(#"^(\d+)\s*(?:x|times?)\s*every\s+(\d+)\s*(day|week|month)s?$"#, lower) {
        return .quota(target: max(Int(m[1]) ?? 1, 1), period: period(m[3]), interval: max(Int(m[2]) ?? 1, 1))
    }
    // "Every N weeks (on …)" and "weekly on …".
    if let m = firstMatch(#"^every\s+(\d+)\s*weeks?(?:\s+on\s+(.+))?$"#, lower) {
        return .weeklyInterval(interval: max(Int(m[1]) ?? 1, 1), weekdays: weekdaySet(from: m[safe: 2] ?? ""))
    }
    if let m = firstMatch(#"^weekly\s+on\s+(.+)$"#, lower) {
        return .weeklyInterval(interval: 1, weekdays: weekdaySet(from: m[1]))
    }

    // A "/"-joined weekday list ("Mon/Wed/Fri").
    let weekdays = weekdaySet(from: trimmed)
    if !weekdays.isEmpty { return .selectedWeekdays(weekdays) }

    return .daily
}

/// Whether a habit with `cadence` is scheduled to occur on `day`, given its completion history
/// (needed to anchor every-N-days / weekly-interval cadences and to count quota progress). Paused /
/// skipped state is handled by the caller — this answers only "does the schedule land here".
public func habitIsDue(
    cadence: String,
    on day: Date,
    completions: [Date],
    calendar: Calendar = .current
) -> Bool {
    let kind = parseHabitCadence(cadence)
    let dayStart = calendar.startOfDay(for: day)
    let iso = isoWeekday(dayStart, calendar)
    let completionDays = completions.map { calendar.startOfDay(for: $0) }

    switch kind {
    case .daily:
        return true
    case .weekdays:
        return (1...5).contains(iso)
    case .weekends:
        return iso == 6 || iso == 7
    case .selectedWeekdays(let set):
        return set.isEmpty ? true : set.contains(iso)
    case .everyNDays(let n):
        let interval = max(n, 1)
        guard let anchor = completionDays.min() else { return true }  // new habit: due to start today
        let gap = dayCount(from: anchor, to: dayStart, calendar)
        return gap >= 0 && gap % interval == 0
    case .weeklyInterval(let interval, let weekdays):
        let step = max(interval, 1)
        guard let anchor = completionDays.min() else {
            // New habit: due to start. With explicit weekdays, only on one of them.
            return weekdays.isEmpty ? true : weekdays.contains(iso)
        }
        let anchorWeek = startOfWeek(anchor, calendar)
        let dayWeek = startOfWeek(dayStart, calendar)
        let weeks = dayCount(from: anchorWeek, to: dayWeek, calendar) / 7
        guard weeks >= 0, weeks % step == 0 else { return false }
        return weekdays.isEmpty ? (iso == isoWeekday(anchor, calendar)) : weekdays.contains(iso)
    case .quota(let target, let period, let interval):
        let window = quotaWindow(containing: dayStart, period: period, interval: max(interval, 1),
                                 completions: completionDays, calendar: calendar)
        let done = completionDays.filter { $0 >= window.start && $0 < window.end }.count
        return done < target
    }
}

// MARK: - Date helpers

/// ISO weekday (1 = Monday … 7 = Sunday) from Foundation's 1 = Sunday … 7 = Saturday.
private func isoWeekday(_ date: Date, _ calendar: Calendar) -> Int {
    let w = calendar.component(.weekday, from: date)
    return w == 1 ? 7 : w - 1
}

/// Whole days from `a` to `b` (both floored to start-of-day by the caller's use).
private func dayCount(from a: Date, to b: Date, _ calendar: Calendar) -> Int {
    calendar.dateComponents([.day], from: a, to: b).day ?? 0
}

/// The Monday on or before `date` (ISO week start), independent of the calendar's `firstWeekday`.
private func startOfWeek(_ date: Date, _ calendar: Calendar) -> Date {
    let start = calendar.startOfDay(for: date)
    return calendar.date(byAdding: .day, value: -(isoWeekday(start, calendar) - 1), to: start) ?? start
}

/// The [start, end) window of the quota period that contains `day`. For `interval == 1` this is the
/// calendar day / ISO week / month containing `day`; for larger intervals the window is `interval`
/// periods long, anchored on the earliest completion's period (so "N times every 2 weeks" counts
/// over rolling 2-week blocks aligned to when the habit began).
private func quotaWindow(
    containing day: Date, period: HabitQuotaPeriod, interval: Int,
    completions: [Date], calendar: Calendar
) -> (start: Date, end: Date) {
    func periodStart(_ date: Date) -> Date {
        switch period {
        case .day: return calendar.startOfDay(for: date)
        case .week: return startOfWeek(date, calendar)
        case .month:
            return calendar.date(from: calendar.dateComponents([.year, .month], from: date)) ?? calendar.startOfDay(for: date)
        }
    }
    func advance(_ date: Date, by units: Int) -> Date {
        let component: Calendar.Component = period == .day ? .day : (period == .week ? .weekOfYear : .month)
        let step = period == .week ? units * 1 : units  // weekOfYear step is 1 week per unit
        return calendar.date(byAdding: component, value: step, to: date) ?? date
    }
    let base = periodStart(day)
    guard interval > 1, let anchor = completions.min() else {
        return (base, advance(base, by: interval))
    }
    // Align the window to interval-sized blocks counted from the anchor's period.
    let anchorStart = periodStart(anchor)
    let unitsBetween: Int
    switch period {
    case .day: unitsBetween = dayCount(from: anchorStart, to: base, calendar)
    case .week: unitsBetween = dayCount(from: anchorStart, to: base, calendar) / 7
    case .month: unitsBetween = (calendar.dateComponents([.month], from: anchorStart, to: base).month ?? 0)
    }
    let blockIndex = unitsBetween >= 0 ? unitsBetween / interval : (unitsBetween - interval + 1) / interval
    let windowStart = advance(anchorStart, by: blockIndex * interval)
    return (windowStart, advance(windowStart, by: interval))
}

// MARK: - Small regex + subscript helpers

/// First regex match's capture groups (index 0 = whole match), or nil. Case-sensitive; callers
/// lowercase first. Keeps the parser readable without pulling in a heavier matcher.
private func firstMatch(_ pattern: String, _ input: String) -> [String]? {
    guard let re = try? NSRegularExpression(pattern: pattern) else { return nil }
    let range = NSRange(input.startIndex..., in: input)
    guard let m = re.firstMatch(in: input, range: range) else { return nil }
    return (0..<m.numberOfRanges).map { i in
        guard let r = Range(m.range(at: i), in: input) else { return "" }
        return String(input[r])
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
