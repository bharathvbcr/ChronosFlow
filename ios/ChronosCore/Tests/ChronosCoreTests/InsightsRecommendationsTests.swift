import XCTest
@testable import ChronosCore

// Mirrors Android's InsightsRecommendationsPlannerTest. The local heuristic is the source of truth;
// `suggest` layers an optional GenAI generation on top. Everything here is deterministic — no clock
// reads, fixed sample data.
final class InsightsRecommendationsTests: XCTestCase {

    // A scripted generator: returns a canned generation and captures the prompt it was handed.
    private final class StubGenerator: AssistTextGenerator, @unchecked Sendable {
        let generation: AssistTextGeneration
        private(set) var capturedPrompt: String?
        init(_ generation: AssistTextGeneration) { self.generation = generation }
        func generateAssistText(prompt: String) async -> AssistTextGeneration {
            capturedPrompt = prompt
            return generation
        }
    }

    // MARK: - Fixtures

    private func sampleSummary(
        driftMinutes: Int = 20,
        missedMinutes: Int = 30
    ) -> PeriodSummary {
        PeriodSummary(
            period: .day,
            plannedMinutes: 480,
            actualMinutes: 360,
            missedMinutes: missedMinutes,
            completionPercent: 75,
            driftMinutes: driftMinutes,
            loggedCount: 4,
            missedCount: 1,
            totalCount: 5,
            categoryRows: [])
    }

    private func sampleFinding(id: String = "insight-1", title: String = "Energy peak") -> ReviewFinding {
        ReviewFinding(
            id: id, severity: .info, title: title,
            detail: "Morning energy held steady.", source: "Execution")
    }

    // Reproduces Android's sampleTrends(): peak hour 9, last-week habits 3 vs prior-week 7
    // (delta -4), medication 10 taken / 4 missed.
    private func sampleTrends() -> CompanionTrendSections {
        let habits: [HabitDailyCompletion] = (0..<14).map { offset in
            let completed = offset < 7 ? 1 : (offset % 2 == 0 ? 1 : 0)
            return HabitDailyCompletion(completedCount: completed, missedCount: 0)
        }
        let meds: [MedicationDailyAdherence] = (0..<14).map { offset in
            let taken = offset % 7 < 5
            return MedicationDailyAdherence(takenCount: taken ? 1 : 0, missedCount: taken ? 0 : 1)
        }
        return CompanionTrendSections(peakEnergyHour: 9, habitCompletion: habits, medicationAdherence: meds)
    }

    // MARK: - suggest

    func testSuggestUsesAIRecommendationsWhenAvailable() async {
        let stub = StubGenerator(AssistTextGeneration(
            text: "Move admin after lunch|Reduces drift during low-energy hours",
            source: .geminiNano))
        let planner = InsightsRecommendationsPlanner(generator: stub)

        let recommendations = await planner.suggest(summary: sampleSummary(), insights: [])

        XCTAssertEqual(recommendations.first?.text, "Move admin after lunch")
        XCTAssertEqual(recommendations.first?.source, .geminiNano)
    }

    func testSuggestFallsBackToBaselineWhenGenerationIsNil() async {
        let stub = StubGenerator(AssistTextGeneration(text: nil, source: .local))
        let planner = InsightsRecommendationsPlanner(generator: stub)

        let recommendations = await planner.suggest(
            summary: sampleSummary(driftMinutes: 90), insights: [])

        XCTAssertTrue(recommendations.first?.text.contains("drift") ?? false)
        XCTAssertEqual(recommendations.first?.source, .local)
    }

    func testSuggestWithoutSummaryReturnsBaselineAndSkipsGenerator() async {
        let stub = StubGenerator(AssistTextGeneration(text: "ignored|reason", source: .geminiNano))
        let planner = InsightsRecommendationsPlanner(generator: stub)

        let recommendations = await planner.suggest(summary: nil, insights: [])

        XCTAssertNil(stub.capturedPrompt, "Generator must not run when there is no summary")
        XCTAssertEqual(recommendations.count, 1)
        XCTAssertTrue(recommendations.first?.text.contains("unlock recommendations") ?? false)
    }

    func testSuggestWithoutGeneratorReturnsLocalBaseline() async {
        let planner = InsightsRecommendationsPlanner()
        let recommendations = await planner.suggest(
            summary: sampleSummary(driftMinutes: 90), insights: [])
        XCTAssertTrue(recommendations.first?.text.contains("drift") ?? false)
    }

    func testSuggestIncludesTrendLinesInThePrompt() async {
        let stub = StubGenerator(AssistTextGeneration(text: nil, source: .local))
        let planner = InsightsRecommendationsPlanner(generator: stub)

        _ = await planner.suggest(summary: sampleSummary(), insights: [], trends: sampleTrends())

        let prompt = stub.capturedPrompt ?? ""
        XCTAssertTrue(prompt.contains("Peak energy hour over the last 14 days: 9:00"))
        XCTAssertTrue(prompt.contains("Habit completions last 7 days: 3; prior 7 days: 7"))
        XCTAssertTrue(prompt.contains("Medication doses last 14 days: 10 taken, 4 missed"))
    }

    func testSuggestCapsAtThree() async {
        let stub = StubGenerator(AssistTextGeneration(
            text: "one|a\ntwo|b\nthree|c\nfour|d\nfive|e", source: .geminiNano))
        let planner = InsightsRecommendationsPlanner(generator: stub)

        let recommendations = await planner.suggest(summary: sampleSummary(), insights: [])

        XCTAssertEqual(recommendations.count, 3)
        XCTAssertEqual(recommendations.map(\.text), ["one", "two", "three"])
    }

    // MARK: - localRecommendations

    func testLocalPrefersDriftGuidanceWhenSummaryShowsHighDrift() {
        let planner = InsightsRecommendationsPlanner()
        let recommendations = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 90), insights: [])
        XCTAssertTrue(recommendations.first?.text.range(of: "drift", options: .caseInsensitive) != nil)
        XCTAssertEqual(recommendations.first?.source, .local)
    }

    func testLocalPrefersExistingInsightsOverDerivation() {
        let planner = InsightsRecommendationsPlanner()
        let insights = [
            sampleFinding(id: "a", title: "First"),
            sampleFinding(id: "b", title: "Second"),
            sampleFinding(id: "c", title: "Third"),
            sampleFinding(id: "d", title: "Fourth"),
        ]
        let recommendations = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 90), insights: insights)

        XCTAssertEqual(recommendations.map(\.text), ["First", "Second", "Third"])
        XCTAssertTrue(recommendations.allSatisfy { $0.source == .local })
    }

    func testLocalWithoutSummaryReturnsUnlockHint() {
        let planner = InsightsRecommendationsPlanner()
        let recommendations = planner.localRecommendations(summary: nil, insights: [])
        XCTAssertEqual(recommendations.count, 1)
        XCTAssertTrue(recommendations.first?.text.contains("unlock recommendations") ?? false)
        XCTAssertEqual(recommendations.first?.source, .local)
    }

    func testLocalSurfacesTrendGuidanceWhenNoInsightsExist() {
        let planner = InsightsRecommendationsPlanner()
        // Drift 0 and missed 0 so only trend-driven lines fire.
        let recommendations = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 0),
            insights: [],
            trends: sampleTrends())

        let texts = recommendations.map(\.text)
        // Capped at three; the three trend lines fire in priority order: peak energy, med slip, habit dip.
        XCTAssertEqual(texts.count, 3)
        XCTAssertTrue(texts.contains { $0.contains("Energy usually peaks around 09:00") })
        XCTAssertTrue(texts.contains { $0.contains("4 medication doses slipped") })
        XCTAssertTrue(texts.contains { $0.contains("Habit completions dipped") })
    }

    func testLocalFallbackWhenNothingFires() {
        let planner = InsightsRecommendationsPlanner()
        let recommendations = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 0), insights: [])
        XCTAssertEqual(recommendations.count, 1)
        XCTAssertTrue(recommendations.first?.text.contains("Keep today's rhythm") ?? false)
    }

    func testEmptyTrendsLeaveLocalRecommendationsUnchanged() {
        let planner = InsightsRecommendationsPlanner()
        let withDefault = planner.localRecommendations(summary: sampleSummary(), insights: [])
        let withEmpty = planner.localRecommendations(
            summary: sampleSummary(), insights: [], trends: CompanionTrendSections())
        XCTAssertEqual(withDefault, withEmpty)
    }

    func testDriftThresholdIsInclusiveAt60() {
        let planner = InsightsRecommendationsPlanner()
        let at59 = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 59, missedMinutes: 0), insights: [])
        XCTAssertFalse(at59.contains { $0.text.contains("drift") })
        let at60 = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 60, missedMinutes: 0), insights: [])
        XCTAssertTrue(at60.contains { $0.text.contains("60m of drift") })
    }

    func testMissedThresholdIsInclusiveAt30() {
        let planner = InsightsRecommendationsPlanner()
        let at29 = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 29), insights: [])
        XCTAssertFalse(at29.contains { $0.text.contains("missed plan") })
        let at30 = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 30), insights: [])
        XCTAssertTrue(at30.contains { $0.text.contains("30m of missed plan") })
    }

    func testDriftAndMissedBothFireInOrder() {
        let planner = InsightsRecommendationsPlanner()
        let recommendations = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 90, missedMinutes: 45), insights: [])
        XCTAssertEqual(recommendations.count, 2)
        XCTAssertTrue(recommendations[0].text.contains("90m of drift"))
        XCTAssertTrue(recommendations[1].text.contains("45m of missed plan"))
    }

    func testDeterministicLocalOutput() {
        let planner = InsightsRecommendationsPlanner()
        let a = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 0), insights: [], trends: sampleTrends())
        let b = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 0), insights: [], trends: sampleTrends())
        XCTAssertEqual(a, b)
    }

    // MARK: - trend math (CompanionTrendSections)

    func testWeekOverWeekDeltaNeedsFullTwoWeekWindow() {
        let short = CompanionTrendSections(
            habitCompletion: (0..<10).map { _ in HabitDailyCompletion(completedCount: 1) })
        XCTAssertNil(short.habitWeekOverWeekDelta)
    }

    func testWeekOverWeekDeltaNilWhenPriorWeekZero() {
        // 14 days: prior week all 0, last week some completions -> baseline is zero, delta nil.
        let habits = (0..<14).map { offset in
            HabitDailyCompletion(completedCount: offset >= 7 ? 1 : 0)
        }
        let trends = CompanionTrendSections(habitCompletion: habits)
        XCTAssertEqual(trends.habitCompletedPriorWeek, 0)
        XCTAssertNil(trends.habitWeekOverWeekDelta)
    }

    func testWeekOverWeekDeltaComputed() {
        let trends = sampleTrends()
        XCTAssertEqual(trends.habitCompletedLastWeek, 3)
        XCTAssertEqual(trends.habitCompletedPriorWeek, 7)
        XCTAssertEqual(trends.habitWeekOverWeekDelta, -4)
    }

    func testMedicationTotals() {
        let trends = sampleTrends()
        XCTAssertEqual(trends.medicationTakenTotal, 10)
        XCTAssertEqual(trends.medicationMissedTotal, 4)
    }

    func testEmptyTrendsIsEmpty() {
        XCTAssertTrue(CompanionTrendSections().isEmpty)
        XCTAssertFalse(CompanionTrendSections(peakEnergyHour: 8).isEmpty)
    }

    // MARK: - source mapping & parsing

    func testAssistSourceStoredNameRoundTrip() {
        XCTAssertEqual(AssistGenAiSource(storedName: "GEMINI_NANO"), .geminiNano)
        XCTAssertEqual(AssistGenAiSource(storedName: "CLOUD_GEMINI"), .cloudGemini)
        XCTAssertEqual(AssistGenAiSource(storedName: "LOCAL"), .local)
        XCTAssertEqual(AssistGenAiSource(storedName: "bogus"), .local)
        XCTAssertEqual(AssistGenAiSource(storedName: nil), .local)
    }

    func testParseRecommendationsStripsBulletsAndBlanks() {
        let planner = InsightsRecommendationsPlanner()
        let parsed = planner.parseRecommendations(
            "- First|reason\n\n  * Second | other reason \n   \nThird",
            source: .cloudGemini)
        XCTAssertEqual(parsed.map(\.text), ["First", "Second", "Third"])
        XCTAssertTrue(parsed.allSatisfy { $0.source == .cloudGemini })
    }

    func testParseRecommendationsTakesRecommendationHalfOnly() {
        let planner = InsightsRecommendationsPlanner()
        let parsed = planner.parseRecommendations("Do the thing|because metrics", source: .local)
        XCTAssertEqual(parsed.map(\.text), ["Do the thing"])
    }

    func testTwoDigitHourPadding() {
        // Single-digit peak hour pads to two digits in the local recommendation text.
        let planner = InsightsRecommendationsPlanner()
        let recommendations = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 0),
            insights: [],
            trends: CompanionTrendSections(peakEnergyHour: 9))
        XCTAssertTrue(recommendations.contains { $0.text.contains("around 09:00") })

        let recommendations2 = planner.localRecommendations(
            summary: sampleSummary(driftMinutes: 0, missedMinutes: 0),
            insights: [],
            trends: CompanionTrendSections(peakEnergyHour: 14))
        XCTAssertTrue(recommendations2.contains { $0.text.contains("around 14:00") })
    }
}
