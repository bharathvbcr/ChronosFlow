import Foundation

// PlanningPromptBuilder — deterministic prompt assembly for day-plan generation.
//
// Faithful port of `core/ai/.../genai/PlanningPromptBuilder.kt`. Splits the day-plan prompt into a
// static system prefix (role + JSON schema, byte-identical across calls so a model can prefix-cache
// it) and a per-request dynamic suffix (constraints + the user's data). Also builds the JSON-repair
// re-ask and the plain-text plan-repair prompt.
//
// Pure and deterministic — the date label is formatted via an injected `Calendar` (never the wall
// clock). Foundation only. Reuses the `PlanTask` / `PlanReviewSummary` / `PlanExistingBlock` /
// `PlannerDateFormatter` declared in LocalPlanningHeuristics.swift.

public enum PlanningPromptBuilder {

    /// Static system prefix shared across all day-plan calls. Byte-identical to the Android constant
    /// so prefix-caching behaviour matches. Trimmed of the leading indentation, like Kotlin's
    /// `trimIndent()`.
    public static let dayPlanSystemPrefix: String = """
    You are ChronosFlow, a privacy-aware day planner. Produce a JSON object only (no markdown fences).
    Schema:
    {
      "blocks": [
        {
          "title": "string",
          "category": "PLANNING|WORK|STUDY|RECOVERY|ADMIN|WORKOUT|REVIEW|FOCUS",
          "startMinuteOfDay": 0-1439,
          "durationMinutes": 5-240,
          "flexibility": "FIXED|MOVABLE|RESIZABLE|OPTIONAL",
          "isProtected": true|false
        }
      ],
      "reason": "string",
      "conflictsResolved": ["string"],
      "explanation": "string"
    }
    """

    /// Returns `(staticPrefix, dynamicSuffix)` for prefix caching. The prefix is the constant
    /// role+schema; the suffix carries the per-request constraints and user data. Combine with "\n"
    /// to reconstruct the full prompt. Faithful port of `dayPlanPromptParts`.
    public static func dayPlanPromptParts(
        userPreferences: String,
        date: Date,
        calendar: Calendar,
        timezone: String,
        review: PlanReviewSummary?,
        existingBlocks: [PlanExistingBlock] = [],
        existingBlockLabels: [String] = [],
        pendingTasks: [PlanTask] = [],
        dueHabitTitles: [String] = []
    ) -> (prefix: String, suffix: String) {
        let dateLabel = PlannerDateFormatter.fullWeekdayDate(date, calendar: calendar)

        // Android summarizes existing blocks as "- title (category, provenance) start-endm". On iOS
        // ChronosCore the existing blocks are reduced value types without those labels, so callers may
        // pass pre-rendered `existingBlockLabels`; otherwise fall back to a minute-span summary.
        let blockSummary = existingBlockSummary(existingBlocks, labels: existingBlockLabels)

        let reviewSummary: String
        if let review {
            reviewSummary = "planned=\(review.plannedMinutes) actual=\(review.actualMinutes) missed=\(review.missedMinutes) drift=\(review.driftMinutes)"
        } else {
            reviewSummary = "none"
        }

        let taskSummary = pendingTaskSummary(pendingTasks)
        let habitSummary = dueHabitTitles.prefix(6)
            .map { "- \($0)" }
            .joined(separator: "\n")
        let habitSummaryResolved = habitSummary.isEmpty ? "- none" : habitSummary

        let preferences = userPreferences.isEmpty ? "balanced day" : userPreferences

        let suffix = """
        Constraints:
        - Use timezone \(timezone) for \(dateLabel).
        - Respect locked or protected commitments in the existing plan.
        - Keep 3 to 6 blocks, non-overlapping, between 6:00 and 22:00 unless user asks otherwise.
        - Prefer recovery when review shows missed work or drift.
        - Schedule the user's pending tasks and due habits into free time before inventing generic blocks.
        User preferences: \(preferences)
        Review summary: \(reviewSummary)
        Existing blocks:
        \(blockSummary)
        Pending tasks (highest priority first):
        \(taskSummary)
        Habits due today:
        \(habitSummaryResolved)
        """

        return (dayPlanSystemPrefix, suffix)
    }

    /// Full combined prompt = "prefix\nsuffix". Faithful port of `dayPlanPrompt`.
    public static func dayPlanPrompt(
        userPreferences: String,
        date: Date,
        calendar: Calendar,
        timezone: String,
        review: PlanReviewSummary?,
        existingBlocks: [PlanExistingBlock] = [],
        existingBlockLabels: [String] = [],
        pendingTasks: [PlanTask] = [],
        dueHabitTitles: [String] = []
    ) -> String {
        let parts = dayPlanPromptParts(
            userPreferences: userPreferences,
            date: date,
            calendar: calendar,
            timezone: timezone,
            review: review,
            existingBlocks: existingBlocks,
            existingBlockLabels: existingBlockLabels,
            pendingTasks: pendingTasks,
            dueHabitTitles: dueHabitTitles
        )
        return "\(parts.prefix)\n\(parts.suffix)"
    }

    /// Summary of pending tasks (priority desc, top 10), one per line. Faithful port of
    /// `pendingTaskSummary`. Sort is stable to mirror Kotlin's `sortedByDescending`.
    static func pendingTaskSummary(_ pendingTasks: [PlanTask]) -> String {
        let summary = pendingTasks
            .filter { !$0.isCompleted }
            .enumerated()
            .sorted { lhs, rhs in
                if lhs.element.priority != rhs.element.priority {
                    return lhs.element.priority > rhs.element.priority
                }
                return lhs.offset < rhs.offset
            }
            .prefix(10)
            .map { entry -> String in
                let task = entry.element
                var line = "- \(task.title) (priority=\(task.priority)"
                if let prefers = task.preferredDurationMinutes {
                    line += ", prefers \(prefers)m"
                }
                if task.dueDate != nil {
                    line += ", due"
                }
                line += ")"
                return line
            }
            .joined(separator: "\n")
        return summary.isEmpty ? "- none" : summary
    }

    /// Corrective re-ask used when a day-plan response failed to parse as JSON. Echoes the original
    /// prompt plus a (truncated) malformed reply and demands a bare JSON object. Faithful port of
    /// `jsonRepairPrompt`.
    public static func jsonRepairPrompt(originalPrompt: String, malformedResponse: String) -> String {
        """
        \(originalPrompt)

        Your previous response could not be parsed as JSON:
        \(String(malformedResponse.prefix(600)))
        Return only the JSON object described above. No prose, no markdown fences, no trailing commas.
        """
    }

    /// Plain-text plan-repair prompt (numbered steps, on-device only). Faithful port of `repairPrompt`.
    public static func repairPrompt(currentPlan: String, conflictDescription: String) -> String {
        let conflict = conflictDescription.isEmpty ? "unspecified conflict" : conflictDescription
        return """
        You are ChronosFlow plan repair. Return plain text with numbered steps (max 6) to resolve the conflict.
        Keep advice on-device actionable. Do not mention cloud services.
        Conflict: \(conflict)
        Current plan excerpt: \(String(currentPlan.prefix(500)))
        """
    }

    // MARK: - Internals

    private static func existingBlockSummary(_ blocks: [PlanExistingBlock], labels: [String]) -> String {
        if !labels.isEmpty {
            let joined = labels.prefix(12).joined(separator: "\n")
            return joined.isEmpty ? "- none" : joined
        }
        let summary = blocks.prefix(12)
            .map { "- block \($0.startMinuteOfDay)-\($0.endMinuteOfDay)m" }
            .joined(separator: "\n")
        return summary.isEmpty ? "- none" : summary
    }
}
