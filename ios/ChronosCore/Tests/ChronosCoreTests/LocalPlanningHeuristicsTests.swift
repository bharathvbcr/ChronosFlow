import XCTest
@testable import ChronosCore

/// Tests for the offline day-planner heuristics. Pure logic, so they run on the Windows/Linux
/// ChronosCore CI without the model. Mirrors the cases in Android's `LocalPlanningHeuristicsTest`
/// (ideal-day defaults/recovery/study+exercise, due-today task ordering, review overlay, plan repair)
/// plus extra edge cases for the dedup, clamping, and id-injection behaviour.
final class LocalPlanningHeuristicsTests: XCTestCase {

    // Fixed UTC calendar so date labels and due-today comparisons are deterministic.
    private let utc: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        cal.locale = Locale(identifier: "en_US_POSIX")
        return cal
    }()

    private func date(_ y: Int, _ m: Int, _ d: Int, _ hh: Int = 0, _ mm: Int = 0) -> Date {
        var c = DateComponents()
        c.year = y; c.month = m; c.day = d; c.hour = hh; c.minute = mm
        return utc.date(from: c)!
    }

    /// A deterministic, monotonically-increasing id provider so block ids are stable in assertions.
    private func sequentialIDs() -> () -> String {
        var n = 0
        return {
            n += 1
            return "id-\(n)"
        }
    }

    // MARK: - generateIdealDayPlan

    func testIdealDayPlanDefaultPreferences() {
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "com.ChronosFlow.VBCR",
            userPreferences: "",
            date: date(2026, 5, 30),
            calendar: utc,
            currentTimeZone: "UTC"
        )
        XCTAssertEqual(result.proposedBlocks.count, 5)
        XCTAssertEqual(result.proposedBlocks[0].title, "Morning planning")
        XCTAssertEqual(result.proposedBlocks[0].startMinuteOfDay, 8 * 60)
        XCTAssertEqual(result.proposedBlocks[1].title, "Deep work block")
        XCTAssertEqual(result.proposedBlocks[1].category, "WORK")
        XCTAssertEqual(result.proposedBlocks[4].title, "Daily review")
        XCTAssertTrue(result.proposedBlocks[4].isProtected)
        XCTAssertTrue(result.proposedBlocks[4].isLocked) // FIXED → locked
        XCTAssertTrue(result.requireConfirmation)
    }

    func testIdealDayPlanRecoveryPreference() {
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "com.ChronosFlow.VBCR",
            userPreferences: "I am feeling very tired today, need recovery",
            date: date(2026, 5, 30),
            calendar: utc,
            currentTimeZone: "UTC"
        )
        XCTAssertEqual(result.proposedBlocks[0].title, "Slow start and planning")
        XCTAssertEqual(result.proposedBlocks[0].startMinuteOfDay, 9 * 60)
        XCTAssertEqual(result.proposedBlocks[0].durationMinutes, 45)
        // Recovery shifts the deep-work slot start by +60 from a 9:00 start.
        XCTAssertEqual(result.proposedBlocks[1].startMinuteOfDay, 9 * 60 + 60)
        XCTAssertEqual(result.proposedBlocks[1].durationMinutes, 75)
    }

    func testIdealDayPlanStudyAndExercisePreferences() {
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "com.ChronosFlow.VBCR",
            userPreferences: "exam study and workout",
            date: date(2026, 5, 30),
            calendar: utc,
            currentTimeZone: "UTC"
        )
        XCTAssertEqual(result.proposedBlocks[1].title, "Deep study block")
        XCTAssertEqual(result.proposedBlocks[1].category, "STUDY")
        XCTAssertEqual(result.proposedBlocks[3].title, "Workout window")
        XCTAssertEqual(result.proposedBlocks[3].category, "WORKOUT")
    }

    func testIdealDayPlanReasonIncludesDateLabelAndPackage() {
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "com.example.app",
            userPreferences: "",
            date: date(2026, 5, 30), // Saturday
            calendar: utc,
            currentTimeZone: "UTC"
        )
        XCTAssertEqual(result.reason, "Local structured suggestion generated for Saturday, May 30 in com.example.app.")
    }

    func testIdealDayPlanUsesInjectedIDs() {
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "p",
            userPreferences: "",
            date: date(2026, 5, 30),
            calendar: utc,
            currentTimeZone: "UTC",
            idProvider: sequentialIDs()
        )
        XCTAssertEqual(result.proposedBlocks.map { $0.id }, ["id-1", "id-2", "id-3", "id-4", "id-5"])
    }

    // MARK: - task selection (due-today beats higher-priority-due-later)

    func testDueTodayTaskBeatsHigherPriorityDueLater() {
        let d = date(2026, 5, 30)
        let dueToday = PlanTask(
            id: "Submit report", title: "Submit report",
            priority: 1, dueDate: date(2026, 5, 30, 12, 0)
        )
        let highPriorityLater = PlanTask(
            id: "Plan offsite", title: "Plan offsite",
            priority: 9, dueDate: date(2026, 6, 19) // 20 days later
        )
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "p",
            userPreferences: "",
            date: d,
            calendar: utc,
            currentTimeZone: "UTC",
            pendingTasks: [highPriorityLater, dueToday]
        )
        // Deep-work slot (index 1) takes the urgent due-today task over the higher-priority one.
        XCTAssertEqual(result.proposedBlocks[1].title, "Submit report")
    }

    func testTaskPreferredDurationOverridesDefault() {
        let task = PlanTask(id: "t", title: "Write spec", priority: 5, preferredDurationMinutes: 95, dueDate: nil)
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "p", userPreferences: "", date: date(2026, 5, 30),
            calendar: utc, currentTimeZone: "UTC", pendingTasks: [task]
        )
        XCTAssertEqual(result.proposedBlocks[1].title, "Write spec")
        XCTAssertEqual(result.proposedBlocks[1].durationMinutes, 95)
    }

    func testCompletedTasksAreIgnored() {
        let done = PlanTask(id: "x", title: "Done thing", isCompleted: true, priority: 9, dueDate: nil)
        let result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "p", userPreferences: "", date: date(2026, 5, 30),
            calendar: utc, currentTimeZone: "UTC", pendingTasks: [done]
        )
        XCTAssertEqual(result.proposedBlocks[1].title, "Deep work block")
    }

    func testSelectTopTasksOrdersByPriorityWhenNoneDueToday() {
        let d = date(2026, 5, 30)
        let later = date(2026, 6, 10)
        let low = PlanTask(id: "a", title: "a", priority: 1, dueDate: later)
        let high = PlanTask(id: "b", title: "b", priority: 8, dueDate: later)
        let top = LocalPlanningHeuristics.selectTopTasks([low, high], date: d, calendar: utc, timezone: "UTC", limit: 2)
        XCTAssertEqual(top.map { $0.id }, ["b", "a"])
    }

    func testSelectTopTasksEarliestDeadlineBreaksPriorityTie() {
        let d = date(2026, 5, 30)
        let earlier = PlanTask(id: "early", title: "early", priority: 5, dueDate: date(2026, 6, 2))
        let laterDue = PlanTask(id: "late", title: "late", priority: 5, dueDate: date(2026, 6, 9))
        let top = LocalPlanningHeuristics.selectTopTasks([laterDue, earlier], date: d, calendar: utc, timezone: "UTC", limit: 2)
        XCTAssertEqual(top.map { $0.id }, ["early", "late"])
    }

    // MARK: - generateReviewBackedDayPlan

    private func baselinePlan() -> StructuredDayPlanSuggestion {
        LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "p", userPreferences: "", date: date(2026, 5, 30),
            calendar: utc, currentTimeZone: "UTC", idProvider: sequentialIDs()
        )
    }

    func testReviewBackedAddsRecoveryForMissedWork() {
        let review = PlanReviewSummary(
            date: "2026-05-29",
            plannedMinutes: 480, actualMinutes: 300, missedMinutes: 180, driftMinutes: 30,
            completedBlockCount: 3, missedBlockCount: 2,
            insights: [
                PlanReviewInsight(id: "i1", type: .missedBlock, title: "Missed work",
                                  detail: "You missed your deep work block.", severity: .warning),
            ]
        )
        let baseline = baselinePlan()
        let result = LocalPlanningHeuristics.generateReviewBackedDayPlan(
            packageName: "p", userPreferences: "None", review: review,
            existingBlocks: [], currentTimeZone: "UTC", baseline: baseline,
            idProvider: { "rec" }
        )
        XCTAssertGreaterThanOrEqual(result.proposedBlocks.count, baseline.proposedBlocks.count)
        XCTAssertTrue(result.explanation.contains("ChronosFlow used structured daily review data"))
        XCTAssertTrue(result.proposedBlocks.contains { $0.title == "Recovery for missed work" })
        XCTAssertTrue(result.proposedBlocks.contains { $0.title == "Schedule compression buffer" })
        // Non-INFO insight surfaces in conflictsResolved with TYPE: title formatting.
        XCTAssertTrue(result.conflictsResolved.contains("MISSED_BLOCK: Missed work"))
    }

    func testReviewBackedNegativeDriftUsesUnderusedWindowTitle() {
        let review = PlanReviewSummary(
            date: "2026-05-29", plannedMinutes: 400, actualMinutes: 460,
            missedMinutes: 0, driftMinutes: -45, insights: []
        )
        let result = LocalPlanningHeuristics.generateReviewBackedDayPlan(
            packageName: "p", userPreferences: "", review: review,
            existingBlocks: [], currentTimeZone: "UTC", baseline: baselinePlan(),
            idProvider: { "x" }
        )
        XCTAssertTrue(result.proposedBlocks.contains { $0.title == "Fill underused focus window" })
        XCTAssertFalse(result.proposedBlocks.contains { $0.title == "Recovery for missed work" })
    }

    func testReviewBackedFocusUnderrunAddsProtectedFocusReset() {
        let review = PlanReviewSummary(
            date: "2026-05-29", plannedMinutes: 400, actualMinutes: 380,
            missedMinutes: 0, driftMinutes: 5,
            insights: [
                PlanReviewInsight(id: "i", type: .focusUnderrun, title: "Short focus",
                                  detail: "", severity: .warning),
            ]
        )
        let result = LocalPlanningHeuristics.generateReviewBackedDayPlan(
            packageName: "p", userPreferences: "", review: review,
            existingBlocks: [], currentTimeZone: "UTC", baseline: baselinePlan(),
            idProvider: { "f" }
        )
        let reset = result.proposedBlocks.first { $0.title == "Protected focus reset" }
        XCTAssertNotNil(reset)
        XCTAssertTrue(reset!.isProtected)
    }

    func testReviewBackedInfoInsightNotInConflictsResolved() {
        let review = PlanReviewSummary(
            date: "2026-05-29", plannedMinutes: 400, actualMinutes: 380,
            missedMinutes: 0, driftMinutes: 0,
            insights: [
                PlanReviewInsight(id: "i", type: .general, title: "FYI", detail: "", severity: .info),
            ]
        )
        let baseline = baselinePlan()
        let result = LocalPlanningHeuristics.generateReviewBackedDayPlan(
            packageName: "p", userPreferences: "", review: review,
            existingBlocks: [], currentTimeZone: "UTC", baseline: baseline,
            idProvider: { "n" }
        )
        XCTAssertEqual(result.conflictsResolved, baseline.conflictsResolved)
        // No recovery/drift/focus block added when there's nothing actionable.
        XCTAssertEqual(result.proposedBlocks.count, baseline.proposedBlocks.count)
    }

    func testReviewBackedDedupsByTitleAndStart() {
        // Craft a baseline whose first block collides with the recovery block's title+start so dedup
        // keeps the recovery (first) copy.
        let anchor = PlanExistingBlock(id: "e", startMinuteOfDay: 9 * 60, durationMinutes: 0)
        // start = nextQuarterHour(anchor.end + 15) = nextQuarterHour(9*60+15) = 555
        let collidingBaselineBlock = ProposedSuggestionBlock(
            id: "dup", title: "Recovery for missed work", category: "WORK",
            startMinuteOfDay: 555, durationMinutes: 30,
            provenance: .aiSuggested, flexibility: .movable, isLocked: false,
            isProtected: false, timezone: "UTC"
        )
        let baseline = StructuredDayPlanSuggestion(
            proposedBlocks: [collidingBaselineBlock],
            reason: "r", conflictsResolved: [], requireConfirmation: true, explanation: "e"
        )
        let review = PlanReviewSummary(
            date: "2026-05-29", plannedMinutes: 100, actualMinutes: 50,
            missedMinutes: 30, driftMinutes: 0, missedBlockCount: 1, insights: []
        )
        let result = LocalPlanningHeuristics.generateReviewBackedDayPlan(
            packageName: "p", userPreferences: "", review: review,
            existingBlocks: [anchor], currentTimeZone: "UTC", baseline: baseline,
            idProvider: { "rec" }
        )
        let recoveries = result.proposedBlocks.filter { $0.title == "Recovery for missed work" }
        XCTAssertEqual(recoveries.count, 1)
        // The kept copy is the recovery block (first), with the recovery's category/id.
        XCTAssertEqual(recoveries.first?.category, "RECOVERY_PLAN")
        XCTAssertEqual(recoveries.first?.id, "rec")
    }

    // MARK: - repairDayPlan

    func testRepairCoversAllConflictTypes() {
        let plan = "Gym at 7am, Work at 9am"
        XCTAssertTrue(LocalPlanningHeuristics.repairDayPlan(currentPlan: plan, conflictDescription: "overlap collision").uppercased().contains("OVERLAP"))
        XCTAssertTrue(LocalPlanningHeuristics.repairDayPlan(currentPlan: plan, conflictDescription: "overbook full").uppercased().contains("OVERLOAD"))
        XCTAssertTrue(LocalPlanningHeuristics.repairDayPlan(currentPlan: plan, conflictDescription: "missed late drift").uppercased().contains("MISSED_WORK"))
        XCTAssertTrue(LocalPlanningHeuristics.repairDayPlan(currentPlan: plan, conflictDescription: "deep work fragment").uppercased().contains("DEEP_WORK"))
        XCTAssertTrue(LocalPlanningHeuristics.repairDayPlan(currentPlan: plan, conflictDescription: "unknown").uppercased().contains("GENERAL"))
    }

    func testRepairEmptyPlan() {
        let result = LocalPlanningHeuristics.repairDayPlan(currentPlan: "", conflictDescription: "Overlap.")
        XCTAssertTrue(result.contains("On-device repair plan"))
        XCTAssertTrue(result.contains("load=unknown"))
        // Empty plan should not append a "Current plan signal" trailer.
        XCTAssertFalse(result.contains("Current plan signal"))
    }

    func testRepairBlankConflictUsesPlaceholder() {
        let result = LocalPlanningHeuristics.repairDayPlan(currentPlan: "busy day", conflictDescription: "")
        XCTAssertTrue(result.contains("No conflict details provided."))
        XCTAssertTrue(result.contains("load=overloaded"))
    }

    func testRepairTruncatesLongPlan() {
        let longPlan = String(repeating: "x", count: 400)
        let result = LocalPlanningHeuristics.repairDayPlan(currentPlan: longPlan, conflictDescription: "general")
        XCTAssertTrue(result.contains("..."))
        XCTAssertTrue(result.contains(String(repeating: "x", count: 260)))
        XCTAssertFalse(result.contains(String(repeating: "x", count: 261)))
    }

    // MARK: - distinctBy helper

    func testDistinctByKeepsFirst() {
        let items = [(1, "a"), (2, "b"), (3, "a")]
        let out = distinctBy(items) { $0.1 }
        XCTAssertEqual(out.map { $0.0 }, [1, 2])
    }

    // MARK: - PlanRegeneration (planning toggles)

    private func candidate(
        _ id: String, _ start: Int, _ duration: Int, category: String = "WORK", title: String = "Block"
    ) -> RegenCandidateBlock {
        RegenCandidateBlock(id: id, title: title, category: category, startMinute: start, durationMinutes: duration)
    }

    private func existing(
        _ id: String, _ start: Int, _ duration: Int, category: String = "WORK",
        isManual: Bool = false, isLocked: Bool = false, isProtected: Bool = false
    ) -> RegenExistingBlock {
        RegenExistingBlock(id: id, category: category, startMinute: start, durationMinutes: duration,
                           isManual: isManual, isLocked: isLocked, isProtected: isProtected)
    }

    func testProtectFocusDropsOverlappingCandidateWhenOn() {
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 60)],
            existing: [existing("focus", 540, 60, category: "FOCUS")],
            toggles: PlanningToggles(protectFocusBlocks: true, addBreaksAutomatically: false,
                                     preserveManualBlocks: false))
        XCTAssertTrue(outcome.acceptedCandidates.isEmpty)
        XCTAssertTrue(outcome.displacedExistingIDs.isEmpty)
    }

    func testProtectFocusOffDisplacesFocusBlock() {
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 60)],
            existing: [existing("focus", 540, 60, category: "FOCUS")],
            toggles: PlanningToggles(protectFocusBlocks: false, addBreaksAutomatically: false,
                                     preserveManualBlocks: false))
        XCTAssertEqual(outcome.acceptedCandidates.map(\.id), ["c1"])
        XCTAssertEqual(outcome.displacedExistingIDs, ["focus"])
    }

    func testPreserveManualKeepsHandPlacedBlock() {
        let toggles = PlanningToggles(protectFocusBlocks: false, addBreaksAutomatically: false,
                                      preserveManualBlocks: true)
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 600, 30)],
            existing: [existing("manual", 600, 45, category: "ADMIN", isManual: true)],
            toggles: toggles)
        XCTAssertTrue(outcome.acceptedCandidates.isEmpty)
        XCTAssertTrue(outcome.displacedExistingIDs.isEmpty)
    }

    func testPreserveManualOffReplacesHandPlacedBlock() {
        let toggles = PlanningToggles(protectFocusBlocks: false, addBreaksAutomatically: false,
                                      preserveManualBlocks: false)
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 600, 30)],
            existing: [existing("manual", 600, 45, category: "ADMIN", isManual: true)],
            toggles: toggles)
        XCTAssertEqual(outcome.acceptedCandidates.map(\.id), ["c1"])
        XCTAssertEqual(outcome.displacedExistingIDs, ["manual"])
    }

    func testLockedAndProtectedBlocksAreAlwaysImmovable() {
        let toggles = PlanningToggles(protectFocusBlocks: false, addBreaksAutomatically: false,
                                      preserveManualBlocks: false)
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 60), candidate("c2", 660, 60)],
            existing: [
                existing("locked", 540, 60, category: "ADMIN", isLocked: true),
                existing("protected", 660, 60, category: "ADMIN", isProtected: true),
            ],
            toggles: toggles)
        XCTAssertTrue(outcome.acceptedCandidates.isEmpty)
        XCTAssertTrue(outcome.displacedExistingIDs.isEmpty)
    }

    func testCandidateOverlapsAreFirstWinsInStartOrder() {
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("late", 570, 60), candidate("early", 540, 60)],
            existing: [],
            toggles: PlanningToggles(addBreaksAutomatically: false))
        // Sorted by start: "early" wins, the overlapping "late" drops.
        XCTAssertEqual(outcome.acceptedCandidates.map(\.id), ["early"])
    }

    func testNonOverlappingCandidateSurvivesUntouched() {
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 900, 45)],
            existing: [existing("focus", 540, 60, category: "FOCUS", isManual: true)],
            toggles: PlanningToggles())
        XCTAssertEqual(outcome.acceptedCandidates.map(\.id), ["c1"])
        XCTAssertTrue(outcome.displacedExistingIDs.isEmpty)
    }

    func testBreakInsertedAfterLongDemandingStretch() {
        // 9:00–10:30 accepted WORK candidate (90m) with free time after → one 20m recovery break.
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 90)],
            existing: [],
            toggles: PlanningToggles(addBreaksAutomatically: true),
            breakIDProvider: sequentialIDs())
        XCTAssertEqual(outcome.insertedBreaks.count, 1)
        let brk = outcome.insertedBreaks[0]
        XCTAssertEqual(brk.startMinute, 630)
        XCTAssertEqual(brk.durationMinutes, 20)
        XCTAssertEqual(brk.category, "RECOVERY")
        XCTAssertEqual(brk.title, "Recovery break")
    }

    func testBreakSpansContiguousExistingAndCandidateRun() {
        // Existing 60m FOCUS run flows into an adjacent 45m candidate: 105m total → break after.
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 600, 45)],
            existing: [existing("focus", 540, 60, category: "FOCUS")],
            toggles: PlanningToggles(addBreaksAutomatically: true),
            breakIDProvider: sequentialIDs())
        XCTAssertEqual(outcome.insertedBreaks.map(\.startMinute), [645])
    }

    func testNoBreakWhenToggleOff() {
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 120)],
            existing: [],
            toggles: PlanningToggles(addBreaksAutomatically: false))
        XCTAssertTrue(outcome.insertedBreaks.isEmpty)
    }

    func testNoBreakUnderStretchThresholdOrWithoutGap() {
        // 60m run is under the 90m stretch threshold.
        let short = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 60)],
            existing: [],
            toggles: PlanningToggles(addBreaksAutomatically: true))
        XCTAssertTrue(short.insertedBreaks.isEmpty)
        // 120m run but the next block starts 10m later — under the 15m minimum placement.
        let crowded = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 120)],
            existing: [existing("next", 670, 30, category: "MEAL", isManual: true)],
            toggles: PlanningToggles(addBreaksAutomatically: true))
        XCTAssertTrue(crowded.insertedBreaks.isEmpty)
    }

    func testBreakShrinksToFitTightGap(){
        // 90m run then a manual block 16m later: break fits but shrinks to the 16m gap.
        let outcome = PlanRegeneration.resolve(
            candidates: [candidate("c1", 540, 90)],
            existing: [existing("next", 646, 30, category: "MEAL", isManual: true)],
            toggles: PlanningToggles(addBreaksAutomatically: true))
        XCTAssertEqual(outcome.insertedBreaks.map(\.durationMinutes), [16])
    }

    func testNonDemandingBlockResetsTheRun() {
        // WORK 60m → MEAL 30m → WORK 60m: neither work stint reaches 90m on its own.
        let outcome = PlanRegeneration.resolve(
            candidates: [
                candidate("c1", 540, 60),
                candidate("meal", 600, 30, category: "MEAL"),
                candidate("c2", 630, 60),
            ],
            existing: [],
            toggles: PlanningToggles(addBreaksAutomatically: true))
        XCTAssertTrue(outcome.insertedBreaks.isEmpty)
    }
}
