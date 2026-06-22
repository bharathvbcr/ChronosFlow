import XCTest
@testable import ChronosCore

/// Tests for the tolerant day-plan JSON parser. Pure logic — runs on the Windows/Linux ChronosCore
/// CI. Mirrors Android's `DayPlanResponseParserTest` (valid JSON, markdown-wrapped, trailing commas,
/// string-internal commas, invalid input, no-blocks, default field values) plus extra coercion edges.
final class DayPlanResponseParserTests: XCTestCase {

    private func parse(_ raw: String) -> StructuredDayPlanSuggestion? {
        DayPlanResponseParser.parse(raw: raw, timezone: "UTC", fallbackExplanation: "Fallback",
                                    idProvider: { "fixed-id" })
    }

    func testParsesValidJSONWithBlocks() {
        let raw = """
        {
            "blocks": [
                { "title": "Gym", "startMinuteOfDay": 420, "durationMinutes": 60, "flexibility": "MOVABLE" },
                { "title": "Work", "startMinuteOfDay": 540, "durationMinutes": 240, "flexibility": "FIXED" }
            ],
            "reason": "Optimize morning energy",
            "explanation": "Moving gym to early morning.",
            "conflictsResolved": ["overlap-1"]
        }
        """
        let result = parse(raw)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.proposedBlocks.count, 2)
        XCTAssertEqual(result?.proposedBlocks[0].title, "Gym")
        XCTAssertEqual(result?.proposedBlocks[1].flexibility, .fixed)
        XCTAssertTrue(result?.proposedBlocks[1].isLocked ?? false) // FIXED → locked
        XCTAssertEqual(result?.reason, "Optimize morning energy")
        XCTAssertEqual(result?.explanation, "Moving gym to early morning.")
        XCTAssertEqual(result?.conflictsResolved, ["overlap-1"])
        XCTAssertTrue(result?.requireConfirmation ?? false)
    }

    func testParsesJSONWrappedInMarkdown() {
        let raw = """
        Here is your plan:
        ```json
        {
            "blocks": [
                { "title": "Test", "startMinuteOfDay": 600, "durationMinutes": 30 }
            ]
        }
        ```
        Hope this helps!
        """
        let result = parse(raw)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.proposedBlocks.count, 1)
        XCTAssertEqual(result?.proposedBlocks.first?.title, "Test")
    }

    func testToleratesTrailingCommas() {
        let raw = """
        {
            "blocks": [
                { "title": "Deep work", "startMinuteOfDay": 540, "durationMinutes": 90, },
            ],
            "reason": "r",
        }
        """
        let result = parse(raw)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.proposedBlocks.count, 1)
        XCTAssertEqual(result?.proposedBlocks.first?.title, "Deep work")
    }

    func testDoesNotStripCommasInsideStrings() {
        let raw = """
        {
            "blocks": [
                { "title": "Email Sam, then call", "startMinuteOfDay": 600, "durationMinutes": 30 }
            ]
        }
        """
        let result = parse(raw)
        XCTAssertEqual(result?.proposedBlocks.first?.title, "Email Sam, then call")
    }

    func testReturnsNilForInvalidJSON() {
        XCTAssertNil(parse("Not a json string"))
    }

    func testReturnsNilForJSONWithoutBlocks() {
        XCTAssertNil(parse("{ \"reason\": \"No blocks here\" }"))
    }

    func testReturnsNilForEmptyBlocksArray() {
        XCTAssertNil(parse("{ \"blocks\": [] }"))
    }

    func testUsesDefaultValuesForMissingFields() {
        let raw = "{ \"blocks\": [ { } ] }"
        let result = parse(raw)
        XCTAssertNotNil(result)
        let block = result?.proposedBlocks.first
        XCTAssertEqual(block?.title, "Suggested block")
        XCTAssertEqual(block?.category, "WORK")
        XCTAssertEqual(block?.startMinuteOfDay, 9 * 60)
        XCTAssertEqual(block?.durationMinutes, 45)
        XCTAssertEqual(block?.flexibility, .movable)
        XCTAssertFalse(block?.isProtected ?? true)
    }

    func testFallbackExplanationWhenMissing() {
        let raw = "{ \"blocks\": [ { \"title\": \"x\" } ] }"
        let result = parse(raw)
        XCTAssertEqual(result?.explanation, "Fallback")
        XCTAssertEqual(result?.reason, "AI-generated day plan")
    }

    func testClampsOutOfRangeMinutes() {
        let raw = """
        { "blocks": [ { "title": "x", "startMinuteOfDay": 5000, "durationMinutes": 9999 } ] }
        """
        let result = parse(raw)
        XCTAssertEqual(result?.proposedBlocks.first?.startMinuteOfDay, 1439)
        XCTAssertEqual(result?.proposedBlocks.first?.durationMinutes, 240)
    }

    func testClampsTooSmallDuration() {
        let raw = "{ \"blocks\": [ { \"title\": \"x\", \"durationMinutes\": 1 } ] }"
        let result = parse(raw)
        XCTAssertEqual(result?.proposedBlocks.first?.durationMinutes, 5)
    }

    func testUnknownFlexibilityFallsBackToMovable() {
        let raw = "{ \"blocks\": [ { \"title\": \"x\", \"flexibility\": \"WOBBLY\" } ] }"
        let result = parse(raw)
        XCTAssertEqual(result?.proposedBlocks.first?.flexibility, .movable)
    }

    func testProtectedFlagParsed() {
        let raw = "{ \"blocks\": [ { \"title\": \"x\", \"isProtected\": true } ] }"
        let result = parse(raw)
        XCTAssertTrue(result?.proposedBlocks.first?.isProtected ?? false)
    }

    func testIgnoresNonObjectBlockEntries() {
        let raw = """
        { "blocks": [ "garbage", 42, { "title": "real" } ] }
        """
        let result = parse(raw)
        XCTAssertEqual(result?.proposedBlocks.count, 1)
        XCTAssertEqual(result?.proposedBlocks.first?.title, "real")
    }

    func testConflictsResolvedDropsBlankEntries() {
        let raw = """
        { "blocks": [ { "title": "x" } ], "conflictsResolved": ["a", "", "  ", "b"] }
        """
        let result = parse(raw)
        XCTAssertEqual(result?.conflictsResolved, ["a", "b"])
    }

    func testNumericFieldAsStringIsCoerced() {
        // org.json optInt accepts numeric strings; the Swift port mirrors that.
        let raw = "{ \"blocks\": [ { \"title\": \"x\", \"startMinuteOfDay\": \"600\" } ] }"
        let result = parse(raw)
        XCTAssertEqual(result?.proposedBlocks.first?.startMinuteOfDay, 600)
    }

    func testTimezoneIsStamped() {
        let result = DayPlanResponseParser.parse(
            raw: "{ \"blocks\": [ { \"title\": \"x\" } ] }",
            timezone: "America/New_York", fallbackExplanation: "f", idProvider: { "i" }
        )
        XCTAssertEqual(result?.proposedBlocks.first?.timezone, "America/New_York")
    }

    // MARK: - direct helper coverage

    func testStripTrailingCommasLeavesValidJSONUntouched() {
        let valid = "{\"a\":[1,2,3]}"
        XCTAssertEqual(DayPlanResponseParser.stripTrailingCommas(valid), valid)
    }

    func testStripTrailingCommasHandlesEscapedQuoteInString() {
        let input = "{\"k\": \"a \\\" b,\" ,}"
        let out = DayPlanResponseParser.stripTrailingCommas(input)
        // The comma inside the string survives; the trailing comma before } is removed.
        XCTAssertTrue(out.contains("\"a \\\" b,\""))
        XCTAssertFalse(out.hasSuffix(",}"))
    }

    func testExtractJsonObjectReturnsNilWithoutBraces() {
        XCTAssertNil(DayPlanResponseParser.extractJsonObject("no braces here"))
    }

    func testExtractJsonObjectTrimsSurroundingProse() {
        let extracted = DayPlanResponseParser.extractJsonObject("prefix {\"a\":1} suffix")
        XCTAssertEqual(extracted, "{\"a\":1}")
    }
}
