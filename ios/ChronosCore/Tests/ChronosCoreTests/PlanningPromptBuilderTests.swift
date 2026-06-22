import XCTest
@testable import ChronosCore

/// Tests for the deterministic day-plan prompt assembly. Pure logic — runs on the Windows/Linux
/// ChronosCore CI. Verifies the static prefix is constant (prefix-caching contract), the suffix folds
/// in constraints/preferences/review/tasks/habits, and the repair/JSON-repair prompts mirror Android.
final class PlanningPromptBuilderTests: XCTestCase {

    private let utc: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        cal.locale = Locale(identifier: "en_US_POSIX")
        return cal
    }()

    private func date(_ y: Int, _ m: Int, _ d: Int) -> Date {
        var c = DateComponents()
        c.year = y; c.month = m; c.day = d
        return utc.date(from: c)!
    }

    private func parts(
        prefs: String = "",
        review: PlanReviewSummary? = nil,
        existingLabels: [String] = [],
        tasks: [PlanTask] = [],
        habits: [String] = []
    ) -> (prefix: String, suffix: String) {
        PlanningPromptBuilder.dayPlanPromptParts(
            userPreferences: prefs,
            date: date(2026, 5, 30), // Saturday
            calendar: utc,
            timezone: "UTC",
            review: review,
            existingBlockLabels: existingLabels,
            pendingTasks: tasks,
            dueHabitTitles: habits
        )
    }

    func testPrefixIsConstantAcrossCalls() {
        let a = parts(prefs: "focus")
        let b = parts(prefs: "recovery", review: PlanReviewSummary(date: "x", plannedMinutes: 1, actualMinutes: 1, missedMinutes: 0, driftMinutes: 0))
        XCTAssertEqual(a.prefix, b.prefix)
        XCTAssertEqual(a.prefix, PlanningPromptBuilder.dayPlanSystemPrefix)
    }

    func testPrefixContainsSchemaAndRole() {
        let prefix = PlanningPromptBuilder.dayPlanSystemPrefix
        XCTAssertTrue(prefix.contains("You are ChronosFlow, a privacy-aware day planner."))
        XCTAssertTrue(prefix.contains("\"startMinuteOfDay\": 0-1439"))
        XCTAssertTrue(prefix.contains("FIXED|MOVABLE|RESIZABLE|OPTIONAL"))
        // trimIndent parity: first line is not indented.
        XCTAssertFalse(prefix.hasPrefix(" "))
    }

    func testSuffixUsesTimezoneAndDateLabel() {
        let suffix = parts().suffix
        XCTAssertTrue(suffix.contains("Use timezone UTC for Saturday, May 30."))
    }

    func testSuffixDefaultsBlankPreferencesToBalancedDay() {
        XCTAssertTrue(parts(prefs: "").suffix.contains("User preferences: balanced day"))
        XCTAssertTrue(parts(prefs: "deep focus").suffix.contains("User preferences: deep focus"))
    }

    func testSuffixReviewSummaryNoneWhenAbsent() {
        XCTAssertTrue(parts(review: nil).suffix.contains("Review summary: none"))
    }

    func testSuffixReviewSummaryFormatted() {
        let review = PlanReviewSummary(date: "2026-05-29", plannedMinutes: 480, actualMinutes: 300, missedMinutes: 180, driftMinutes: 30)
        let suffix = parts(review: review).suffix
        XCTAssertTrue(suffix.contains("Review summary: planned=480 actual=300 missed=180 drift=30"))
    }

    func testSuffixEmptySectionsRenderNone() {
        let suffix = parts().suffix
        XCTAssertTrue(suffix.contains("Existing blocks:\n- none"))
        XCTAssertTrue(suffix.contains("Pending tasks (highest priority first):\n- none"))
        XCTAssertTrue(suffix.contains("Habits due today:\n- none"))
    }

    func testSuffixIncludesHabits() {
        let suffix = parts(habits: ["Meditate", "Stretch"]).suffix
        XCTAssertTrue(suffix.contains("- Meditate"))
        XCTAssertTrue(suffix.contains("- Stretch"))
    }

    func testHabitsCappedAtSix() {
        let many = (1...10).map { "Habit\($0)" }
        let suffix = parts(habits: many).suffix
        XCTAssertTrue(suffix.contains("- Habit6"))
        XCTAssertFalse(suffix.contains("- Habit7"))
    }

    func testExistingBlockLabelsUsedWhenProvided() {
        let suffix = parts(existingLabels: ["- Standup (WORK) 540-570m"]).suffix
        XCTAssertTrue(suffix.contains("- Standup (WORK) 540-570m"))
    }

    // MARK: - pending task summary

    func testPendingTaskSummarySortsByPriorityDesc() {
        let tasks = [
            PlanTask(id: "a", title: "Low", priority: 1),
            PlanTask(id: "b", title: "High", priority: 9),
        ]
        let summary = PlanningPromptBuilder.pendingTaskSummary(tasks)
        let highIdx = summary.range(of: "High")!.lowerBound
        let lowIdx = summary.range(of: "Low")!.lowerBound
        XCTAssertLessThan(highIdx, lowIdx)
    }

    func testPendingTaskSummaryRendersDetails() {
        let task = PlanTask(id: "t", title: "Write", priority: 5, preferredDurationMinutes: 45, dueDate: Date(timeIntervalSince1970: 0))
        let summary = PlanningPromptBuilder.pendingTaskSummary([task])
        XCTAssertEqual(summary, "- Write (priority=5, prefers 45m, due)")
    }

    func testPendingTaskSummarySkipsCompleted() {
        let tasks = [PlanTask(id: "a", title: "Done", isCompleted: true, priority: 9)]
        XCTAssertEqual(PlanningPromptBuilder.pendingTaskSummary(tasks), "- none")
    }

    func testPendingTaskSummaryCapsAtTen() {
        let tasks = (1...15).map { PlanTask(id: "\($0)", title: "T\($0)", priority: 0) }
        let summary = PlanningPromptBuilder.pendingTaskSummary(tasks)
        let lines = summary.split(separator: "\n")
        XCTAssertEqual(lines.count, 10)
    }

    // MARK: - dayPlanPrompt combined

    func testCombinedPromptJoinsPrefixAndSuffixWithNewline() {
        let combined = PlanningPromptBuilder.dayPlanPrompt(
            userPreferences: "x", date: date(2026, 5, 30), calendar: utc,
            timezone: "UTC", review: nil
        )
        let p = parts(prefs: "x")
        XCTAssertEqual(combined, "\(p.prefix)\n\(p.suffix)")
    }

    // MARK: - repair prompts

    func testJSONRepairPromptEchoesAndTruncates() {
        let malformed = String(repeating: "z", count: 700)
        let prompt = PlanningPromptBuilder.jsonRepairPrompt(originalPrompt: "ORIGINAL", malformedResponse: malformed)
        XCTAssertTrue(prompt.contains("ORIGINAL"))
        XCTAssertTrue(prompt.contains("could not be parsed as JSON"))
        XCTAssertTrue(prompt.contains("No prose, no markdown fences, no trailing commas."))
        XCTAssertTrue(prompt.contains(String(repeating: "z", count: 600)))
        XCTAssertFalse(prompt.contains(String(repeating: "z", count: 601)))
    }

    func testRepairPromptUsesPlaceholderForBlankConflict() {
        let prompt = PlanningPromptBuilder.repairPrompt(currentPlan: "plan", conflictDescription: "")
        XCTAssertTrue(prompt.contains("Conflict: unspecified conflict"))
        XCTAssertTrue(prompt.contains("Do not mention cloud services."))
    }

    func testRepairPromptTruncatesPlanAt500() {
        let longPlan = String(repeating: "p", count: 600)
        let prompt = PlanningPromptBuilder.repairPrompt(currentPlan: longPlan, conflictDescription: "overlap")
        XCTAssertTrue(prompt.contains(String(repeating: "p", count: 500)))
        XCTAssertFalse(prompt.contains(String(repeating: "p", count: 501)))
        XCTAssertTrue(prompt.contains("Conflict: overlap"))
    }
}
