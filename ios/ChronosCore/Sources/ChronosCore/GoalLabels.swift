import Foundation

// GoalLabels — portable, Foundation-only label/derivation helpers for the Goals feature.
//
// Platform-agnostic port of the Android goal-screen / goal-form logic
// (`feature/goals/.../GoalScreen.kt`, `GoalFormSheet.kt`, and `cadenceLabel` from
// `GoalDetailSheet.kt`): due-date chips, linked-work hints, overdue counts/messages,
// category suggestion + options, target extraction, deadline presets, and target/date
// warnings. Pure and deterministic — no wall-clock reads, no SwiftUI / UIKit — so it
// unit-tests off-device on the Windows/Linux CI that builds ChronosCore.
//
// Android represents goal/target dates as `LocalDate` (a calendar day, no time). Here a
// day is a `Date` interpreted through an injected `Calendar`: all comparisons are made on
// the start-of-day so the result never depends on the time-of-day component, and the
// caller controls the time zone (inject a UTC calendar in tests).

/// Due-date chip for a goal. `emphasized` marks an approaching or passed deadline so the
/// UI can highlight it. Mirrors Android's `GoalDueLabel`.
public struct GoalDueLabel: Sendable, Equatable {
    public let text: String
    public let emphasized: Bool
    public init(text: String, emphasized: Bool) {
        self.text = text
        self.emphasized = emphasized
    }
}

public enum GoalLabels {

    // MARK: Categories

    /// Default preset categories offered as chips, mirroring Android's `CATEGORY_OPTIONS`
    /// ordering. The first entry is the default category for a new goal.
    public static let categoryOptions: [String] = [
        "Personal", "Health", "Work", "Learning", "Finance", "Habits",
    ]

    /// The category a new goal starts in — mirrors Android's `DEFAULT_CATEGORY`.
    public static let defaultCategory = "Personal"

    /// Keyword → category rules backing `suggestGoalCategory`, most specific first.
    /// Byte-mirrored from Android's `GoalCategoryKeywordRules`.
    private static let categoryKeywordRules: [(keywords: [String], category: String)] = [
        (["run", "gym", "workout", "exercise", "weight", "sleep", "steps", "diet", "yoga", "hydrate"], "Health"),
        (["read", "book", "learn", "study", "course", "language", "skill"], "Learning"),
        (["save", "money", "budget", "invest", "debt", "spend"], "Finance"),
        (["work", "project", "career", "client", "launch", "ship"], "Work"),
        (["habit", "daily", "meditate", "journal", "streak"], "Habits"),
    ]

    /// Suggests a category from keywords in `title`, or nil when nothing recognisable
    /// matches. Mirrors `suggestGoalCategory`.
    public static func suggestGoalCategory(_ title: String) -> String? {
        let lower = title.lowercased()
        return categoryKeywordRules
            .first { rule in rule.keywords.contains { lower.contains($0) } }?
            .category
    }

    /// Distinct goal categories currently in use, sorted for a stable filter row.
    /// Mirrors `goalCategoriesInUse`.
    public static func categoriesInUse(_ categories: [String]) -> [String] {
        var seen = Set<String>()
        var distinct: [String] = []
        for category in categories where seen.insert(category).inserted {
            distinct.append(category)
        }
        return distinct.sorted()
    }

    /// Selectable category chips: the presets, the edited goal's own category, and any
    /// custom value the user is typing — trimmed, de-blanked, and de-duplicated so a typed
    /// preset never doubles up. Mirrors `goalCategoryOptions`.
    public static func categoryOptions(
        presets: [String], existingCategory: String?, customCategory: String
    ) -> [String] {
        let trimmedCustom = customCategory.trimmingCharacters(in: .whitespacesAndNewlines)
        var candidates = presets
        if let existingCategory { candidates.append(existingCategory) }
        if !trimmedCustom.isEmpty { candidates.append(trimmedCustom) }

        var seen = Set<String>()
        var result: [String] = []
        for raw in candidates {
            let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { continue }
            if seen.insert(value).inserted { result.append(value) }
        }
        return result
    }

    // MARK: Target count

    /// Common target counts offered as one-tap chips, mirroring `GoalTargetQuickPicks`.
    public static let targetQuickPicks = [5, 10, 12, 30, 50, 100]

    /// First sensible whole number embedded in a goal title (e.g. "Read 12 books" → 12),
    /// so the target count can be offered without retyping. Nil when there's no number in
    /// the usable 1...99999 range. Mirrors `goalTargetFromTitle`.
    public static func goalTargetFromTitle(_ title: String) -> Int? {
        var current = ""
        for char in title {
            if char.isNumber {
                current.append(char)
            } else if !current.isEmpty {
                if let value = matchedTarget(current) { return value }
                current = ""
            }
        }
        return current.isEmpty ? nil : matchedTarget(current)
    }

    private static func matchedTarget(_ digits: String) -> Int? {
        guard let value = Int(digits), (1...99999).contains(value) else { return nil }
        return value
    }

    // MARK: Cadence

    /// Turns a raw cadence token like "DAILY" into a readable "Daily". Mirrors
    /// `cadenceLabel`: trimmed, lower-cased, first character upper-cased, "Habit" when blank.
    public static func cadenceLabel(_ cadence: String) -> String {
        let trimmed = cadence.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard let first = trimmed.first else { return "Habit" }
        return first.uppercased() + trimmed.dropFirst()
    }

    // MARK: Linked work

    /// Hint surfacing progress contributed by linked tasks and habits, or nil when none.
    /// Mirrors `goalLinkedWorkLabel`.
    public static func goalLinkedWorkLabel(_ derived: GoalDerivedProgress) -> String? {
        let total = derived.completedTaskCount + derived.habitCompletionCount
        return total <= 0 ? nil : "+\(total) from linked work"
    }

    // MARK: Due-date label

    /// Due-date chip for a goal. Completed goals and goals without a target date show
    /// nothing; an approaching or passed deadline is emphasized. Mirrors `goalDueLabel`.
    /// Day differences are computed on the calendar's start-of-day, so the time component
    /// of either date is ignored.
    public static func goalDueLabel(
        targetDate: Date?, isCompleted: Bool, today: Date, calendar: Calendar = .current
    ) -> GoalDueLabel? {
        guard let targetDate, !isCompleted else { return nil }
        let days = dayDifference(from: today, to: targetDate, calendar: calendar)
        switch days {
        case ..<0: return GoalDueLabel(text: "Overdue", emphasized: true)
        case 0: return GoalDueLabel(text: "Due today", emphasized: true)
        case 1...7: return GoalDueLabel(text: "Due in \(days)d", emphasized: true)
        default: return GoalDueLabel(text: "Due \(isoDay(targetDate, calendar: calendar))", emphasized: false)
        }
    }

    // MARK: Overdue roll-up

    /// Active (incomplete) goals whose target date has already passed. Mirrors
    /// `goalsOverdueCount`. A goal counts only when its start-of-day target is strictly
    /// before today's start-of-day.
    public static func goalsOverdueCount(
        targetDates: [(targetDate: Date?, isCompleted: Bool)], today: Date, calendar: Calendar = .current
    ) -> Int {
        targetDates.reduce(0) { running, entry in
            guard !entry.isCompleted, let target = entry.targetDate else { return running }
            return dayDifference(from: today, to: target, calendar: calendar) < 0 ? running + 1 : running
        }
    }

    /// Overdue banner copy, singular for one and plural otherwise. Mirrors
    /// `goalsOverdueMessage`.
    public static func goalsOverdueMessage(_ count: Int) -> String {
        count == 1
            ? "1 active goal is past its target date."
            : "\(count) active goals are past their target date."
    }

    // MARK: Deadline presets

    /// A named deadline preset relative to today.
    public struct DeadlinePreset: Sendable, Equatable {
        public let label: String
        public let date: Date
        public init(label: String, date: Date) {
            self.label = label
            self.date = date
        }
    }

    /// One-tap target-date options relative to `today`, mirroring `goalDeadlinePresets`:
    /// +1 month, +3 months, +6 months, and December 31 of the current year.
    public static func goalDeadlinePresets(today: Date, calendar: Calendar = .current) -> [DeadlinePreset] {
        let base = calendar.startOfDay(for: today)
        let year = calendar.component(.year, from: base)
        let endOfYear = calendar.date(from: DateComponents(year: year, month: 12, day: 31)) ?? base
        return [
            DeadlinePreset(label: "1 month", date: addMonths(1, to: base, calendar: calendar)),
            DeadlinePreset(label: "3 months", date: addMonths(3, to: base, calendar: calendar)),
            DeadlinePreset(label: "6 months", date: addMonths(6, to: base, calendar: calendar)),
            DeadlinePreset(label: "End of year", date: calendar.startOfDay(for: endOfYear)),
        ]
    }

    // MARK: Warnings

    /// Warns when an edited target count is below the progress already logged, or nil
    /// otherwise. Mirrors `goalTargetBelowProgressWarning`: fires only for a target in
    /// 1..<progress.
    public static func goalTargetBelowProgressWarning(target: Int, progress: Int) -> String? {
        // Fires for a target in 1..<progress. Bounds-checked directly to avoid building an invalid
        // Range when progress <= 1 (e.g. 1..<0 would trap).
        (target >= 1 && target < progress) ? "Target is below your logged progress (\(progress))." : nil
    }

    /// Warns when a chosen target date has already passed, or nil when the date is fine.
    /// Mirrors `goalTargetDateWarning`: fires only when the target's start-of-day is
    /// strictly before today's start-of-day.
    public static func goalTargetDateWarning(
        targetDate: Date?, today: Date, calendar: Calendar = .current
    ) -> String? {
        guard let targetDate else { return nil }
        return dayDifference(from: today, to: targetDate, calendar: calendar) < 0
            ? "This date is in the past — the goal will show as overdue."
            : nil
    }

    // MARK: Private date helpers

    /// Whole-day difference between the start-of-day of `from` and `to`. Positive when `to`
    /// is later. Ignores the time component of either input.
    private static func dayDifference(from: Date, to: Date, calendar: Calendar) -> Int {
        let start = calendar.startOfDay(for: from)
        let end = calendar.startOfDay(for: to)
        return calendar.dateComponents([.day], from: start, to: end).day ?? 0
    }

    /// ISO `yyyy-MM-dd` rendering of a day, mirroring Android's `LocalDate.toString()`.
    private static func isoDay(_ date: Date, calendar: Calendar) -> String {
        let comps = calendar.dateComponents([.year, .month, .day], from: date)
        let year = comps.year ?? 0
        let month = comps.month ?? 1
        let day = comps.day ?? 1
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    private static func addMonths(_ months: Int, to date: Date, calendar: Calendar) -> Date {
        calendar.date(byAdding: .month, value: months, to: date) ?? date
    }
}
