import Foundation

// LocalPlanningHeuristics — pure, Foundation-only port of the Android offline day-planner.
//
// Faithful port of `core/ai/.../genai/LocalPlanningHeuristics.kt`. These are the heuristics the
// Android planner falls back to whenever Gemini (Nano or cloud) is unavailable, and on iOS they
// back the FoundationModels app layer the same way: a deterministic skeleton day, a review-backed
// recovery overlay, and a plain-text plan-repair generator.
//
// Everything is pure and deterministic. The Android code mints `UUID.randomUUID()` block ids; here
// ids are injected through an `idProvider` closure (default a fresh `UUID().uuidString`) so unit
// tests can pin them — the wall clock and the calendar/timezone are likewise injected. No SwiftUI /
// UIKit / HealthKit / EventKit. Reuses `BlockFlexibility` from Enums.swift.

// MARK: - Shared value types (mirrors of the Android domain models the planner touches)

/// Where a proposed block came from. Mirrors `core.domain.model.BlockProvenance`'s relevant case.
/// Only `aiSuggested` is produced here; the full enum lives app-side on iOS.
public enum BlockProvenance: String, Codable, CaseIterable, Sendable {
    case aiSuggested = "AI_SUGGESTED"
    case user = "USER"
    case calendar = "CALENDAR"
    case medication = "MEDICATION"
    case habit = "HABIT"
    case task = "TASK"
}

/// Which generator produced an explanation/plan. Mirrors `genai.AssistGenAiSource`.
public enum AssistGenAiSource: String, Codable, CaseIterable, Sendable {
    case local = "LOCAL"
    case geminiNano = "GEMINI_NANO"
    case cloudGemini = "CLOUD_GEMINI"
}

/// A single block the planner proposes. Mirrors `core.ai.ProposedSuggestionBlock`.
public struct ProposedSuggestionBlock: Sendable, Equatable, Identifiable {
    public let id: String
    public let title: String
    public let category: String
    public let startMinuteOfDay: Int
    public let durationMinutes: Int
    public let provenance: BlockProvenance
    public let flexibility: BlockFlexibility
    public let isLocked: Bool
    public let isProtected: Bool
    public let timezone: String

    public init(
        id: String,
        title: String,
        category: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        provenance: BlockProvenance,
        flexibility: BlockFlexibility,
        isLocked: Bool,
        isProtected: Bool,
        timezone: String
    ) {
        self.id = id
        self.title = title
        self.category = category
        self.startMinuteOfDay = startMinuteOfDay
        self.durationMinutes = durationMinutes
        self.provenance = provenance
        self.flexibility = flexibility
        self.isLocked = isLocked
        self.isProtected = isProtected
        self.timezone = timezone
    }
}

/// A complete structured suggestion. Mirrors `core.ai.StructuredDayPlanSuggestion`.
public struct StructuredDayPlanSuggestion: Sendable, Equatable {
    public var proposedBlocks: [ProposedSuggestionBlock]
    public var reason: String
    public var conflictsResolved: [String]
    public var requireConfirmation: Bool
    public var explanation: String
    public var explanationSource: AssistGenAiSource

    public init(
        proposedBlocks: [ProposedSuggestionBlock],
        reason: String,
        conflictsResolved: [String],
        requireConfirmation: Bool,
        explanation: String,
        explanationSource: AssistGenAiSource = .local
    ) {
        self.proposedBlocks = proposedBlocks
        self.reason = reason
        self.conflictsResolved = conflictsResolved
        self.requireConfirmation = requireConfirmation
        self.explanation = explanation
        self.explanationSource = explanationSource
    }
}

/// A pending task reduced to the fields the offline planner reasons about. Mirrors the parts of
/// `core.domain.model.Task` the heuristics read. `dueDate` is an absolute instant.
public struct PlanTask: Sendable, Equatable, Identifiable {
    public let id: String
    public let title: String
    public let isCompleted: Bool
    public let priority: Int
    public let preferredDurationMinutes: Int?
    public let dueDate: Date?

    public init(
        id: String,
        title: String,
        isCompleted: Bool = false,
        priority: Int = 0,
        preferredDurationMinutes: Int? = nil,
        dueDate: Date? = nil
    ) {
        self.id = id
        self.title = title
        self.isCompleted = isCompleted
        self.priority = priority
        self.preferredDurationMinutes = preferredDurationMinutes
        self.dueDate = dueDate
    }
}

/// Severity of a review insight. Mirrors `core.domain.model.ReviewInsightSeverity` (ordinal order
/// matters: `info < warning < critical`).
public enum ReviewInsightSeverity: String, Codable, CaseIterable, Sendable {
    case info = "INFO"
    case warning = "WARNING"
    case critical = "CRITICAL"

    /// Mirrors Kotlin enum `ordinal` so `maxByOrNull { it.severity.ordinal }` ports faithfully.
    public var ordinal: Int {
        switch self {
        case .info: return 0
        case .warning: return 1
        case .critical: return 2
        }
    }
}

/// Type of a review insight. Mirrors the `core.domain.model.ReviewInsightType` cases the planner
/// branches on. `rawValue` matches the Android enum name so `.name` formatting ports faithfully.
public enum ReviewInsightType: String, Codable, CaseIterable, Sendable {
    case missedBlock = "MISSED_BLOCK"
    case focusUnderrun = "FOCUS_UNDERRUN"
    case scheduleBalance = "SCHEDULE_BALANCE"
    case scheduleDrift = "SCHEDULE_DRIFT"
    case overload = "OVERLOAD"
    case general = "GENERAL"
}

/// A single review insight. Mirrors `core.domain.model.ReviewInsight`.
public struct PlanReviewInsight: Sendable, Equatable, Identifiable {
    public let id: String
    public let type: ReviewInsightType
    public let title: String
    public let detail: String
    public let severity: ReviewInsightSeverity

    public init(
        id: String,
        type: ReviewInsightType,
        title: String,
        detail: String,
        severity: ReviewInsightSeverity
    ) {
        self.id = id
        self.type = type
        self.title = title
        self.detail = detail
        self.severity = severity
    }
}

/// A day's review summary. Mirrors the parts of `core.domain.model.DailyReviewSummary` the planner
/// reads. `date` is an ISO `yyyy-MM-dd` label string (the planner only ever formats it into copy).
public struct PlanReviewSummary: Sendable, Equatable {
    public let date: String
    public let plannedMinutes: Int
    public let actualMinutes: Int
    public let missedMinutes: Int
    public let driftMinutes: Int
    public let completedBlockCount: Int
    public let missedBlockCount: Int
    public let insights: [PlanReviewInsight]

    public init(
        date: String,
        plannedMinutes: Int,
        actualMinutes: Int,
        missedMinutes: Int,
        driftMinutes: Int,
        completedBlockCount: Int = 0,
        missedBlockCount: Int = 0,
        insights: [PlanReviewInsight] = []
    ) {
        self.date = date
        self.plannedMinutes = plannedMinutes
        self.actualMinutes = actualMinutes
        self.missedMinutes = missedMinutes
        self.driftMinutes = driftMinutes
        self.completedBlockCount = completedBlockCount
        self.missedBlockCount = missedBlockCount
        self.insights = insights
    }
}

/// A scheduled block already on the day, reduced to the fields the review overlay anchors against.
/// Mirrors the parts of `core.domain.model.TimeBlock` the heuristics read.
public struct PlanExistingBlock: Sendable, Equatable, Identifiable {
    public let id: String
    public let startMinuteOfDay: Int
    public let durationMinutes: Int

    public init(id: String, startMinuteOfDay: Int, durationMinutes: Int) {
        self.id = id
        self.startMinuteOfDay = startMinuteOfDay
        self.durationMinutes = durationMinutes
    }

    public var endMinuteOfDay: Int { startMinuteOfDay + durationMinutes }
}

// MARK: - Date formatting helper

/// Formats a `DateComponents`-style (year/month/day) day into the "Monday, Jun 3" label the Android
/// planner uses in reason/explanation strings. The calendar is injected so tests pin a UTC calendar
/// and the result is locale-stable (POSIX) — Android uses `Locale.getDefault()` but the label is
/// only ever shown in English copy, and a fixed locale keeps the port deterministic across CI hosts.
public enum PlannerDateFormatter {
    /// "EEEE, MMM d" with a fixed POSIX locale. Deterministic across hosts.
    public static func fullWeekdayDate(_ date: Date, calendar: Calendar) -> String {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "EEEE, MMM d"
        return formatter.string(from: date)
    }
}

// MARK: - LocalPlanningHeuristics

/// Conflict classes the plan-repair generator recognises. Mirrors the private Kotlin `RepairConflict`.
private enum RepairConflict: String {
    case overlap = "OVERLAP"
    case overload = "OVERLOAD"
    case missedWork = "MISSED_WORK"
    case deepWork = "DEEP_WORK"
    case general = "GENERAL"
}

public enum LocalPlanningHeuristics {

    /// Builds a deterministic skeleton day from free-text preferences and (optionally) pending tasks.
    /// Port of `generateIdealDayPlan`. `idProvider` mints block ids (default `UUID().uuidString`);
    /// the `date`/`calendar` are injected so the label and the due-today comparison are deterministic.
    public static func generateIdealDayPlan(
        packageName: String,
        userPreferences: String,
        date: Date,
        calendar: Calendar,
        currentTimeZone: String,
        pendingTasks: [PlanTask] = [],
        idProvider: () -> String = { UUID().uuidString }
    ) -> StructuredDayPlanSuggestion {
        let todayLabel = PlannerDateFormatter.fullWeekdayDate(date, calendar: calendar)
        let normalized = userPreferences.lowercased()
        let wantsRecovery = ["recovery", "break", "low energy", "tired"].contains { normalized.contains($0) }
        let wantsStudy = ["study", "read", "exam", "learn"].contains { normalized.contains($0) }
        let wantsExercise = ["workout", "exercise", "walk", "run"].contains { normalized.contains($0) }
        let topTasks = selectTopTasks(pendingTasks, date: date, calendar: calendar, timezone: currentTimeZone, limit: 2)

        let startHour = wantsRecovery ? 9 : 8
        let startMinute = startHour * 60

        var blocks: [ProposedSuggestionBlock] = []

        blocks.append(suggestion(
            id: idProvider(),
            title: wantsRecovery ? "Slow start and planning" : "Morning planning",
            category: "PLANNING",
            startMinuteOfDay: startMinute,
            durationMinutes: wantsRecovery ? 45 : 30,
            flexibility: .movable,
            timezone: currentTimeZone
        ))

        // The user's own work beats canned placeholders: the deep-work and admin slots take the top
        // pending tasks when any exist.
        let task0 = topTasks.indices.contains(0) ? topTasks[0] : nil
        blocks.append(suggestion(
            id: idProvider(),
            title: task0?.title ?? (wantsStudy ? "Deep study block" : "Deep work block"),
            category: wantsStudy ? "STUDY" : "WORK",
            startMinuteOfDay: startMinute + (wantsRecovery ? 60 : 45),
            durationMinutes: task0?.preferredDurationMinutes ?? (wantsRecovery ? 75 : 110),
            flexibility: .resizable,
            timezone: currentTimeZone
        ))

        blocks.append(suggestion(
            id: idProvider(),
            title: "Recovery break",
            category: "RECOVERY",
            startMinuteOfDay: 12 * 60,
            durationMinutes: wantsRecovery ? 45 : 30,
            flexibility: .movable,
            timezone: currentTimeZone
        ))

        let task1 = topTasks.indices.contains(1) ? topTasks[1] : nil
        blocks.append(suggestion(
            id: idProvider(),
            title: task1?.title ?? (wantsExercise ? "Workout window" : "Admin and communication"),
            category: (wantsExercise && task1 == nil) ? "WORKOUT" : "ADMIN",
            startMinuteOfDay: 14 * 60,
            durationMinutes: task1?.preferredDurationMinutes ?? (wantsExercise ? 60 : 45),
            flexibility: .movable,
            timezone: currentTimeZone
        ))

        blocks.append(suggestion(
            id: idProvider(),
            title: "Daily review",
            category: "REVIEW",
            startMinuteOfDay: 17 * 60 + 30,
            durationMinutes: 25,
            flexibility: .fixed,
            timezone: currentTimeZone,
            isProtected: true
        ))

        return StructuredDayPlanSuggestion(
            proposedBlocks: blocks,
            reason: "Local structured suggestion generated for \(todayLabel) in \(packageName).",
            conflictsResolved: [
                "Separated deep work from recovery time",
                "Reserved review time before evening",
            ],
            requireConfirmation: true,
            explanation: "ChronosFlow used a local planning heuristic because Gemini was unavailable. Review every suggestion before applying it."
        )
    }

    /// Overlays review-driven recovery blocks onto a baseline plan. Port of
    /// `generateReviewBackedDayPlan`. Dedups the combined block list by `(title, startMinuteOfDay)`,
    /// keeping the FIRST occurrence (recovery blocks come first, exactly as Kotlin's
    /// `(reviewBlocks + baseline.proposedBlocks).distinctBy { ... }`).
    public static func generateReviewBackedDayPlan(
        packageName: String,
        userPreferences: String,
        review: PlanReviewSummary,
        existingBlocks: [PlanExistingBlock],
        currentTimeZone: String,
        baseline: StructuredDayPlanSuggestion,
        idProvider: () -> String = { UUID().uuidString }
    ) -> StructuredDayPlanSuggestion {
        let reviewBlocks = buildReviewRecoveryBlocks(
            review: review,
            existingBlocks: existingBlocks,
            timezone: currentTimeZone,
            idProvider: idProvider
        )
        let conflictsResolved = baseline.conflictsResolved + review.insights
            .filter { $0.severity != .info }
            .map { "\($0.type.rawValue): \($0.title)" }

        let combined = reviewBlocks + baseline.proposedBlocks
        let deduped = distinctBy(combined) { "\($0.title)\u{1F}\($0.startMinuteOfDay)" }

        var result = baseline
        result.proposedBlocks = deduped
        result.reason = "Review-backed plan for \(review.date): \(review.actualMinutes)/\(review.plannedMinutes) actual/planned minutes, \(review.missedBlockCount) missed block(s), drift \(review.driftMinutes)m."
        result.conflictsResolved = conflictsResolved
        result.explanation = buildReviewExplanation(review: review, baselineExplanation: baseline.explanation)
        return result
    }

    /// Generates a plain-text, on-device plan-repair walkthrough. Port of `repairDayPlan`.
    public static func repairDayPlan(currentPlan: String, conflictDescription: String) -> String {
        let conflict = classifyConflict(conflictDescription)
        let planLoad = classifyPlanLoad(currentPlan)
        let steps: [String]
        switch conflict {
        case .overlap:
            steps = [
                "Keep locked or protected commitments fixed.",
                "Move the least-protected flexible block to the next open 30-minute boundary.",
                "Resize optional work before displacing medication, calendar, or active focus commitments.",
            ]
        case .overload:
            steps = [
                "Cap the plan to the available day window before adding new work.",
                "Convert optional blocks under 30 minutes into a single admin batch.",
                "Reserve one recovery buffer after the longest focus block.",
            ]
        case .missedWork:
            steps = [
                "Create a recovery block for the missed commitment instead of silently deleting it.",
                "Place recovery after the next protected block with at least 15 minutes of transition time.",
                "Mark the original block as missed so daily review keeps the planned-vs-actual trace.",
            ]
        case .deepWork:
            steps = [
                "Find an uninterrupted window of at least 90 minutes.",
                "Prefer morning or early midday windows unless fixed commitments already occupy them.",
                "Move flexible admin, break, or communication blocks away from both sides of the window.",
            ]
        case .general:
            steps = [
                "Resolve fixed commitments first, then protected commitments, then flexible work.",
                "Preserve medication and calendar anchors before moving optional blocks.",
                "Leave an explicit review note explaining what changed and why.",
            ]
        }

        var out = ""
        out += "On-device repair plan"
        out += " (load=\(planLoad), conflict=\(conflict.rawValue)). "
        out += "Input conflict: "
        out += conflictDescription.isEmpty ? "No conflict details provided." : conflictDescription
        out += " Recommended steps: "
        out += steps.joined(separator: " ")
        if !currentPlan.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            out += " Current plan signal: "
            out += String(currentPlan.prefix(260))
            if currentPlan.count > 260 { out += "..." }
        }
        return out
    }

    // MARK: - Internals (faithful ports of the private Kotlin helpers)

    /// Picks the tasks the offline planner schedules into the deep-work and admin slots. Tasks due
    /// by the end of `date` (in `timezone`) are urgent and come first, then highest priority, then
    /// earliest deadline. Port of `selectTopTasks`. The sort is a stable port of Kotlin's
    /// `compareByDescending { dueByEod } .thenByDescending { priority } .thenBy { dueDate ?: MAX }`.
    static func selectTopTasks(
        _ pendingTasks: [PlanTask],
        date: Date,
        calendar: Calendar,
        timezone: String,
        limit: Int
    ) -> [PlanTask] {
        let zone = TimeZone(identifier: timezone) ?? calendar.timeZone
        var cal = calendar
        cal.timeZone = zone
        // End of the local day (23:59:59.999...) in the given zone. Mirrors `atTime(LocalTime.MAX)`.
        let startOfDay = cal.startOfDay(for: date)
        let endOfDay = cal.date(byAdding: DateComponents(day: 1, second: -1), to: startOfDay) ?? date

        let candidates = pendingTasks.filter { !$0.isCompleted }
        // Stable sort: compare on the three keys, falling back to original index to keep input order.
        return candidates.enumerated()
            .sorted { lhs, rhs in
                let a = lhs.element, b = rhs.element
                // Key 1: due by end of day (true sorts first → descending on Bool).
                let aDue = a.dueDate.map { $0 <= endOfDay } ?? false
                let bDue = b.dueDate.map { $0 <= endOfDay } ?? false
                if aDue != bDue { return aDue && !bDue }
                // Key 2: priority descending.
                if a.priority != b.priority { return a.priority > b.priority }
                // Key 3: earliest deadline (nil → distantFuture, the MAX analogue).
                let aDeadline = a.dueDate ?? Date.distantFuture
                let bDeadline = b.dueDate ?? Date.distantFuture
                if aDeadline != bDeadline { return aDeadline < bDeadline }
                // Stability: keep input order.
                return lhs.offset < rhs.offset
            }
            .map { $0.element }
            .prefix(limit)
            .map { $0 }
    }

    private static func suggestion(
        id: String,
        title: String,
        category: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        flexibility: BlockFlexibility,
        timezone: String,
        isProtected: Bool = false
    ) -> ProposedSuggestionBlock {
        ProposedSuggestionBlock(
            id: id,
            title: title,
            category: category,
            startMinuteOfDay: min(max(startMinuteOfDay, 0), 1439),
            durationMinutes: min(max(durationMinutes, 1), 1440),
            provenance: .aiSuggested,
            flexibility: flexibility,
            isLocked: flexibility == .fixed,
            isProtected: isProtected,
            timezone: timezone
        )
    }

    private static func buildReviewRecoveryBlocks(
        review: PlanReviewSummary,
        existingBlocks: [PlanExistingBlock],
        timezone: String,
        idProvider: () -> String
    ) -> [ProposedSuggestionBlock] {
        let anchor = existingBlocks.map { $0.startMinuteOfDay + $0.durationMinutes }.max() ?? (9 * 60)
        let start = min(nextQuarterHour(anchor + 15), 20 * 60)
        var result: [ProposedSuggestionBlock] = []

        if review.missedMinutes >= 20 || review.missedBlockCount > 0 {
            result.append(suggestion(
                id: idProvider(),
                title: "Recovery for missed work",
                category: "RECOVERY_PLAN",
                startMinuteOfDay: start,
                durationMinutes: min(max(review.missedMinutes, 20), 60),
                flexibility: .resizable,
                timezone: timezone
            ))
        }
        if abs(review.driftMinutes) >= 30 {
            result.append(suggestion(
                id: idProvider(),
                title: review.driftMinutes > 0 ? "Schedule compression buffer" : "Fill underused focus window",
                category: "DRIFT_REPAIR",
                startMinuteOfDay: min(nextQuarterHour(start + 75), 21 * 60),
                durationMinutes: 30,
                flexibility: .movable,
                timezone: timezone
            ))
        }
        if review.insights.contains(where: { $0.type == .focusUnderrun || $0.type == .scheduleBalance }) {
            result.append(suggestion(
                id: idProvider(),
                title: "Protected focus reset",
                category: "FOCUS",
                startMinuteOfDay: min(nextQuarterHour(start + 120), 21 * 60),
                durationMinutes: 45,
                flexibility: .resizable,
                timezone: timezone,
                isProtected: true
            ))
        }
        return result
    }

    private static func buildReviewExplanation(review: PlanReviewSummary, baselineExplanation: String) -> String {
        let insightText = review.insights.prefix(3)
            .map { "\($0.type.rawValue.lowercased()): \($0.title)" }
            .joined(separator: "; ")
        var out = ""
        out += "ChronosFlow used structured daily review data before proposing changes. "
        out += "Planned \(review.plannedMinutes)m, actual \(review.actualMinutes)m, missed \(review.missedMinutes)m, drift \(review.driftMinutes)m. "
        if !insightText.isEmpty { out += "Signals: \(insightText). " }
        out += baselineExplanation
        return out
    }

    private static func nextQuarterHour(_ minute: Int) -> Int {
        min(max(((minute + 14) / 15) * 15, 0), 1439)
    }

    private static func classifyPlanLoad(_ currentPlan: String) -> String {
        let normalized = currentPlan.lowercased()
        if ["overbook", "full", "busy", "no gap", "no free"].contains(where: { normalized.contains($0) }) {
            return "overloaded"
        }
        if ["missed", "late", "drift"].contains(where: { normalized.contains($0) }) {
            return "drifted"
        }
        if currentPlan.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "unknown"
        }
        return "normal"
    }

    private static func classifyConflict(_ conflictDescription: String) -> RepairConflict {
        let normalized = conflictDescription.lowercased()
        if ["overlap", "collision", "conflict", "same time"].contains(where: { normalized.contains($0) }) {
            return .overlap
        }
        if ["overbook", "too much", "no gap", "no free", "full"].contains(where: { normalized.contains($0) }) {
            return .overload
        }
        if ["missed", "late", "drift", "behind"].contains(where: { normalized.contains($0) }) {
            return .missedWork
        }
        if ["deep work", "focus", "fragment", "interruption"].contains(where: { normalized.contains($0) }) {
            return .deepWork
        }
        return .general
    }
}

// MARK: - distinctBy (Kotlin parity helper)

/// Returns the elements of `source` keeping only the FIRST element for each distinct key produced by
/// `key`, preserving order. Faithful port of Kotlin's `Iterable.distinctBy`.
func distinctBy<T, K: Hashable>(_ source: [T], _ key: (T) -> K) -> [T] {
    var seen = Set<K>()
    var result: [T] = []
    result.reserveCapacity(source.count)
    for element in source where seen.insert(key(element)).inserted {
        result.append(element)
    }
    return result
}
