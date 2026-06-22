import Foundation

// Insights analytics — portable, Foundation-only rollups for the Insights/Review surface. Ports the
// analytics shapes from the Android feature/daydial Insights tab: InsightsPeriod, the per-category
// breakdown rows (share / progress / concentration label), the rollup PeriodSummary, and the
// severity-ranked ReviewFinding list. All inputs are plain value types so nothing here touches
// SwiftData; the iOS target maps its @Model rows onto these before calling in.

// MARK: - Period

/// Rollup window for the Insights surface. Each non-day period is a trailing window of `days`
/// dates ending at the anchored (selected) date, so future days never dilute completion.
/// Mirrors Android's `InsightsPeriod`.
public enum InsightsPeriod: String, CaseIterable, Sendable {
    case day, week, month

    public var label: String {
        switch self {
        case .day: return "Day"
        case .week: return "Week"
        case .month: return "Month"
        }
    }

    /// Number of trailing days the window spans.
    public var days: Int {
        switch self {
        case .day: return 1
        case .week: return 7
        case .month: return 30
        }
    }

    /// Inclusive start-of-day…end-of-day range ending at `anchorDate`, oldest bound first.
    public func dateInterval(endingAt anchorDate: Date, calendar: Calendar) -> (start: Date, end: Date) {
        let endDay = calendar.startOfDay(for: anchorDate)
        let startDay = calendar.date(byAdding: .day, value: -(days - 1), to: endDay) ?? endDay
        // End bound is the very end of the anchor day so same-day blocks are included.
        let endBound = calendar.date(byAdding: .day, value: 1, to: endDay) ?? endDay
        return (startDay, endBound)
    }
}

// MARK: - Inputs

/// A block reduced to the fields the analytics need. The iOS target builds these from `TimeBlock`.
public struct InsightsBlock: Sendable {
    public let date: Date            // start-of-day (or any instant within the day)
    public let category: String
    public let plannedDuration: Int
    public let actualStart: Int?
    public let actualEnd: Int?

    public init(date: Date, category: String, plannedDuration: Int,
                actualStart: Int? = nil, actualEnd: Int? = nil) {
        self.date = date
        self.category = category
        self.plannedDuration = plannedDuration
        self.actualStart = actualStart
        self.actualEnd = actualEnd
    }

    /// Logged length in minutes, wrapping past midnight; nil when not executed.
    var actualDuration: Int? {
        guard let s = actualStart, let e = actualEnd else { return nil }
        let span = e - s
        return span >= 0 ? span : span + 1440
    }

    var isLogged: Bool { actualStart != nil }
}

// MARK: - Category breakdown

/// One category row of the breakdown. `share` is this category's fraction of total minutes (0…1);
/// `progress` is its fraction of the largest category (0…1, for relative bar fill). Mirrors
/// Android's `InsightCategoryBreakdownRow`.
public struct CategoryBreakdownRow: Sendable, Equatable {
    public let category: String
    public let minutes: Int
    public let share: Double
    public let progress: Double

    public init(category: String, minutes: Int, share: Double, progress: Double) {
        self.category = category
        self.minutes = minutes
        self.share = share
        self.progress = progress
    }
}

/// Coarse "how concentrated is the day on one category" label, keyed off the top category's share.
/// Mirrors Android's `focusConcentrationLabel`.
public enum ConcentrationLabel: String, Sendable {
    case high = "High"
    case moderate = "Moderate"
    case even = "Even"
    case balanced = "Balanced"

    public init(topShare: Double) {
        switch topShare {
        case let s where s >= 0.7: self = .high
        case let s where s >= 0.5: self = .moderate
        case let s where s >= 0.3: self = .even
        default: self = .balanced
        }
    }
}

/// Build category rows from (category, minutes) pairs. Rows are sorted by minutes desc, then name
/// asc (stable, deterministic). Empty input yields no rows. Mirrors `categoryBreakdownRows`.
public func categoryBreakdownRows(_ categoryMinutes: [(category: String, minutes: Int)]) -> [CategoryBreakdownRow] {
    var totals: [String: Int] = [:]
    for pair in categoryMinutes where pair.minutes > 0 {
        totals[pair.category, default: 0] += pair.minutes
    }
    guard let maxMinutes = totals.values.max(), maxMinutes > 0 else { return [] }
    let totalMinutes = max(totals.values.reduce(0, +), 1)

    return totals
        .sorted { a, b in a.value != b.value ? a.value > b.value : a.key < b.key }
        .map { category, minutes in
            CategoryBreakdownRow(
                category: category,
                minutes: minutes,
                share: Double(minutes) / Double(totalMinutes),
                progress: Double(minutes) / Double(maxMinutes)
            )
        }
}

// MARK: - Period summary

/// Aggregated execution metrics for a period, plus the category breakdown and concentration.
public struct PeriodSummary: Sendable, Equatable {
    public let period: InsightsPeriod
    public let plannedMinutes: Int
    public let actualMinutes: Int
    public let missedMinutes: Int
    public let completionPercent: Int       // 0…100, actual / planned
    public let driftMinutes: Int            // actual − planned (can be negative)
    public let loggedCount: Int
    public let missedCount: Int             // blocks in window with no logged actual
    public let totalCount: Int
    public let categoryRows: [CategoryBreakdownRow]

    /// The leading category by minutes, or nil when nothing was tracked.
    public var topCategory: CategoryBreakdownRow? { categoryRows.first }

    /// Concentration of the day on the top category.
    public var concentration: ConcentrationLabel {
        ConcentrationLabel(topShare: categoryRows.first?.share ?? 0)
    }

    public var hasPlan: Bool { plannedMinutes > 0 }
}

/// Summarize `blocks` over `period` ending at `anchorDate`. Blocks outside the trailing window are
/// ignored. Completion = actual / planned (capped 0…100). Mirrors Android's InsightsPeriodSummary
/// aggregation: planned/actual/missed minutes, missed count, and the category breakdown.
public func summarize(
    blocks: [InsightsBlock],
    period: InsightsPeriod,
    anchorDate: Date,
    calendar: Calendar
) -> PeriodSummary {
    let (start, end) = period.dateInterval(endingAt: anchorDate, calendar: calendar)
    let inWindow = blocks.filter { $0.date >= start && $0.date < end }

    let plannedMinutes = inWindow.reduce(0) { $0 + $1.plannedDuration }
    let logged = inWindow.filter { $0.isLogged }
    let actualMinutes = logged.reduce(0) { $0 + ($1.actualDuration ?? 0) }
    // Missed minutes: planned time on blocks that were never logged.
    let missedMinutes = inWindow.filter { !$0.isLogged }.reduce(0) { $0 + $1.plannedDuration }
    let missedCount = inWindow.filter { !$0.isLogged }.count

    let completion: Int
    if plannedMinutes > 0 {
        let raw = Double(max(actualMinutes, 0)) / Double(plannedMinutes) * 100.0
        completion = min(max(Int(raw.rounded()), 0), 100)
    } else {
        completion = 0
    }

    let rows = categoryBreakdownRows(inWindow.map { ($0.category, $0.plannedDuration) })

    return PeriodSummary(
        period: period,
        plannedMinutes: plannedMinutes,
        actualMinutes: actualMinutes,
        missedMinutes: missedMinutes,
        completionPercent: completion,
        driftMinutes: actualMinutes - plannedMinutes,
        loggedCount: logged.count,
        missedCount: missedCount,
        totalCount: inWindow.count,
        categoryRows: rows
    )
}

// MARK: - Findings

public enum FindingSeverity: Int, Sendable, Comparable {
    case critical = 0, warning = 1, info = 2
    public static func < (lhs: FindingSeverity, rhs: FindingSeverity) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

/// A single derived finding for the Insights findings list. `source` tags where it came from
/// (e.g. "Execution", "Sleep", "Balance") so the UI can show a provenance chip.
public struct ReviewFinding: Sendable, Equatable, Identifiable {
    public let id: String
    public let severity: FindingSeverity
    public let title: String
    public let detail: String
    public let source: String

    public init(id: String, severity: FindingSeverity, title: String, detail: String, source: String) {
        self.id = id
        self.severity = severity
        self.title = title
        self.detail = detail
        self.source = source
    }
}

/// Extra signals the findings derivation reasons about alongside the period summary.
public struct FindingsContext: Sendable {
    public let demandingMinutes: Int       // minutes at energy >= 4 in the window
    public let totalScheduledMinutes: Int
    public let categoryVariety: Int
    public let breakRatio: Double
    public let sleepReadiness: SleepReadiness
    public let medicationAdherence: Double?    // 0…1, nil when not tracked
    public let bestHabitStreak: Int            // longest current habit streak

    public init(
        demandingMinutes: Int = 0,
        totalScheduledMinutes: Int = 0,
        categoryVariety: Int = 0,
        breakRatio: Double = 0,
        sleepReadiness: SleepReadiness = .unknown,
        medicationAdherence: Double? = nil,
        bestHabitStreak: Int = 0
    ) {
        self.demandingMinutes = demandingMinutes
        self.totalScheduledMinutes = totalScheduledMinutes
        self.categoryVariety = categoryVariety
        self.breakRatio = breakRatio
        self.sleepReadiness = sleepReadiness
        self.medicationAdherence = medicationAdherence
        self.bestHabitStreak = bestHabitStreak
    }
}

/// Derive a severity-ranked list of findings from a period summary plus context. Result is sorted
/// critical → warning → info, with a stable secondary order by source then title so output is
/// deterministic. Mirrors the spirit of Android's ReviewInsight ordering and the overload / sleep /
/// adherence / concentration heuristics.
public func deriveFindings(
    summary: PeriodSummary,
    context: FindingsContext,
    maxDemandingMinutes: Int = 5 * 60,
    maxScheduledMinutes: Int = 14 * 60
) -> [ReviewFinding] {
    var findings: [ReviewFinding] = []

    // --- Overload (critical/warning) ---
    if context.demandingMinutes > maxDemandingMinutes {
        let overHours = Double(context.demandingMinutes - maxDemandingMinutes) / 60.0
        findings.append(ReviewFinding(
            id: "overload.demanding",
            severity: overHours >= 1 ? .critical : .warning,
            title: "Overloaded with demanding work",
            detail: "\(context.demandingMinutes / 60)h of high-energy work is scheduled. Consider moving some to another day.",
            source: "Balance"))
    }
    if context.totalScheduledMinutes > maxScheduledMinutes {
        findings.append(ReviewFinding(
            id: "overload.scheduled",
            severity: .warning,
            title: "Over-scheduled day",
            detail: "\(context.totalScheduledMinutes / 60)h is booked. Protect some recovery time.",
            source: "Balance"))
    }

    // --- Sleep readiness ---
    switch context.sleepReadiness {
    case .depleted:
        findings.append(ReviewFinding(
            id: "sleep.depleted",
            severity: .critical,
            title: "Poor night's sleep",
            detail: "Demanding work is deferred past the morning and breaks run longer to help you recover.",
            source: "Sleep"))
    case .rested:
        findings.append(ReviewFinding(
            id: "sleep.rested",
            severity: .info,
            title: "Well rested",
            detail: "You slept well — a good day to take on your most demanding work early.",
            source: "Sleep"))
    case .normal, .unknown:
        break
    }

    // --- Execution adherence (only meaningful when a plan exists) ---
    if summary.hasPlan {
        if summary.completionPercent < 50 {
            findings.append(ReviewFinding(
                id: "execution.low",
                severity: .warning,
                title: "Execution is behind",
                detail: "Only \(summary.completionPercent)% of planned time was logged. Prioritize your top tasks and defer the rest.",
                source: "Execution"))
        } else if summary.completionPercent >= 95 {
            findings.append(ReviewFinding(
                id: "execution.high",
                severity: .info,
                title: "On top of your plan",
                detail: "You logged \(summary.completionPercent)% of planned time. Keep the cadence.",
                source: "Execution"))
        }
        if summary.missedCount > 0 {
            findings.append(ReviewFinding(
                id: "execution.missed",
                severity: summary.missedCount >= 3 ? .warning : .info,
                title: "\(summary.missedCount) missed block\(summary.missedCount == 1 ? "" : "s")",
                detail: "\(formatMinutes(summary.missedMinutes)) of planned time went unlogged.",
                source: "Execution"))
        }
    }

    // --- Concentration / idle ---
    if let top = summary.topCategory {
        switch summary.concentration {
        case .high:
            findings.append(ReviewFinding(
                id: "concentration.high",
                severity: .warning,
                title: "Heavily concentrated on \(top.category)",
                detail: "\(Int((top.share * 100).rounded()))% of your time is one category. Add variety to reduce context-switching cost.",
                source: "Balance"))
        case .moderate, .even, .balanced:
            break
        }
    } else if summary.hasPlan {
        // Plan exists but nothing categorized/tracked.
        findings.append(ReviewFinding(
            id: "idle.untracked",
            severity: .info,
            title: "Nothing tracked yet",
            detail: "Log your blocks to unlock the category breakdown.",
            source: "Execution"))
    }

    // --- Medication adherence ---
    if let adherence = context.medicationAdherence, adherence < 0.6 {
        findings.append(ReviewFinding(
            id: "medication.low",
            severity: adherence < 0.4 ? .critical : .warning,
            title: "Medication adherence is low",
            detail: "\(Int((adherence * 100).rounded()))% of doses logged recently. Set reminders to stay on track.",
            source: "Medication"))
    }

    // --- Positive streak ---
    if context.bestHabitStreak >= 7 {
        findings.append(ReviewFinding(
            id: "habit.streak",
            severity: .info,
            title: "\(context.bestHabitStreak)-day streak going",
            detail: "Your strongest habit is on a \(context.bestHabitStreak)-day run. Keep it alive.",
            source: "Habits"))
    }

    return findings.sorted { a, b in
        if a.severity != b.severity { return a.severity < b.severity }
        if a.source != b.source { return a.source < b.source }
        return a.title < b.title
    }
}

func formatMinutes(_ minutes: Int) -> String {
    let m = max(minutes, 0)
    if m < 60 { return "\(m)m" }
    let h = m / 60, rem = m % 60
    return rem == 0 ? "\(h)h" : "\(h)h \(rem)m"
}

// MARK: - Companion trend sections

/// One day's habit completion tally. Mirrors Android's `HabitDailyCompletion`.
public struct HabitDailyCompletion: Sendable, Equatable {
    public let completedCount: Int
    public let missedCount: Int

    public init(completedCount: Int, missedCount: Int = 0) {
        self.completedCount = completedCount
        self.missedCount = missedCount
    }
}

/// One day's medication adherence tally. Mirrors Android's `MedicationDailyAdherence`.
public struct MedicationDailyAdherence: Sendable, Equatable {
    public let takenCount: Int
    public let missedCount: Int

    public init(takenCount: Int, missedCount: Int) {
        self.takenCount = takenCount
        self.missedCount = missedCount
    }
}

/// Recent companion-data trends fed into the recommendation generator so suggestions reflect
/// multi-day patterns instead of only today's numbers. Foundation-only port of Android's
/// `CompanionTrendContext`. All fields default to empty: callers that cannot supply trends lose
/// nothing. The day lists are expected oldest-first (so `suffix(7)` is the most recent week),
/// matching the Android ordering used by the week-over-week math.
public struct CompanionTrendSections: Sendable, Equatable {
    /// Hour of day (0…23) with the highest average energy across recent check-ins, when known.
    public let peakEnergyHour: Int?
    /// Per-day habit completion tallies, oldest-first.
    public let habitCompletion: [HabitDailyCompletion]
    /// Per-day medication adherence tallies, oldest-first.
    public let medicationAdherence: [MedicationDailyAdherence]

    public init(
        peakEnergyHour: Int? = nil,
        habitCompletion: [HabitDailyCompletion] = [],
        medicationAdherence: [MedicationDailyAdherence] = []
    ) {
        self.peakEnergyHour = peakEnergyHour
        self.habitCompletion = habitCompletion
        self.medicationAdherence = medicationAdherence
    }

    /// True when no trend signal is available. Mirrors Android's `isEmpty`.
    public var isEmpty: Bool {
        peakEnergyHour == nil && habitCompletion.isEmpty && medicationAdherence.isEmpty
    }

    /// Completions over the most recent 7 days. Mirrors `habitCompletedLastWeek`.
    public var habitCompletedLastWeek: Int {
        habitCompletion.suffix(7).reduce(0) { $0 + $1.completedCount }
    }

    /// Completions over the 7 days before the most recent week. Mirrors `habitCompletedPriorWeek`.
    public var habitCompletedPriorWeek: Int {
        habitCompletion.dropLast(7).suffix(7).reduce(0) { $0 + $1.completedCount }
    }

    /// Last-week minus prior-week completions; nil without a full two-week window or a non-zero
    /// prior-week baseline to compare against. Mirrors `habitWeekOverWeekDelta`.
    public var habitWeekOverWeekDelta: Int? {
        if habitCompletion.count < 14 || habitCompletedPriorWeek == 0 { return nil }
        return habitCompletedLastWeek - habitCompletedPriorWeek
    }

    /// Total doses taken across the window. Mirrors `medicationTakenTotal`.
    public var medicationTakenTotal: Int {
        medicationAdherence.reduce(0) { $0 + $1.takenCount }
    }

    /// Total doses missed across the window. Mirrors `medicationMissedTotal`.
    public var medicationMissedTotal: Int {
        medicationAdherence.reduce(0) { $0 + $1.missedCount }
    }
}
