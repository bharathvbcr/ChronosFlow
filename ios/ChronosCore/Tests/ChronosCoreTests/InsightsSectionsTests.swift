import XCTest
@testable import ChronosCore

// Mirrors Android's InsightsSectionFilterTest: an empty selection shows every section, a non-empty
// selection narrows the page to exactly the chosen sections, and the pill order/labels stay stable.
final class InsightsSectionsTests: XCTestCase {

    func testEmptySelectionShowsEverySection() {
        for section in InsightsSection.allCases {
            XCTAssertTrue(
                insightsSectionVisible(section: section, selected: Set<String>()),
                "Expected \(section) visible with no pill selected")
            XCTAssertTrue(
                insightsSectionVisible(section: section, selected: Set<InsightsSection>()),
                "Typed overload should also show \(section) with no pill selected")
        }
    }

    func testSingleSelectedPillHidesTheOtherSections() {
        let selected: Set<String> = [InsightsSection.trends.rawValue]

        XCTAssertTrue(insightsSectionVisible(section: .trends, selected: selected))
        XCTAssertFalse(insightsSectionVisible(section: .execution, selected: selected))
        XCTAssertFalse(insightsSectionVisible(section: .categories, selected: selected))
        XCTAssertFalse(insightsSectionVisible(section: .insights, selected: selected))
        XCTAssertFalse(insightsSectionVisible(section: .screenTime, selected: selected))
    }

    func testMultipleSelectedPillsShowExactlyThoseSections() {
        let selected: Set<String> = [
            InsightsSection.execution.rawValue,
            InsightsSection.screenTime.rawValue,
        ]

        XCTAssertTrue(insightsSectionVisible(section: .execution, selected: selected))
        XCTAssertTrue(insightsSectionVisible(section: .screenTime, selected: selected))
        XCTAssertFalse(insightsSectionVisible(section: .trends, selected: selected))
        XCTAssertFalse(insightsSectionVisible(section: .categories, selected: selected))
        XCTAssertFalse(insightsSectionVisible(section: .insights, selected: selected))
    }

    func testTypedSelectionOverloadMatchesStringOverload() {
        let typed: Set<InsightsSection> = [.execution, .insights]
        for section in InsightsSection.allCases {
            let viaTyped = insightsSectionVisible(section: section, selected: typed)
            let viaString = insightsSectionVisible(
                section: section, selected: Set(typed.map { $0.rawValue }))
            XCTAssertEqual(viaTyped, viaString, "Overloads disagree for \(section)")
        }
    }

    func testUnknownSelectionNamesAreIgnored() {
        // A selection containing only unknown names is non-empty, so the filter is active but
        // matches nothing — every real section is hidden (mirrors Android `section.name in selected`).
        let selected: Set<String> = ["BOGUS", "habits"]
        for section in InsightsSection.allCases {
            XCTAssertFalse(insightsSectionVisible(section: section, selected: selected))
        }
    }

    func testRawValuesMatchAndroidEnumConstantNames() {
        XCTAssertEqual(InsightsSection.execution.rawValue, "EXECUTION")
        XCTAssertEqual(InsightsSection.categories.rawValue, "CATEGORIES")
        XCTAssertEqual(InsightsSection.insights.rawValue, "INSIGHTS")
        XCTAssertEqual(InsightsSection.screenTime.rawValue, "SCREEN_TIME")
        XCTAssertEqual(InsightsSection.trends.rawValue, "TRENDS")
    }

    func testSectionOrderAndLabelsStayStable() {
        XCTAssertEqual(
            InsightsSection.allCases,
            [.execution, .categories, .insights, .screenTime, .trends])
        XCTAssertEqual(
            InsightsSection.allCases.map(\.label),
            ["Execution", "Categories", "Insights", "Screen time", "Trends"])
    }

    func testIdentifiableIdMatchesRawValue() {
        for section in InsightsSection.allCases {
            XCTAssertEqual(section.id, section.rawValue)
        }
    }
}
