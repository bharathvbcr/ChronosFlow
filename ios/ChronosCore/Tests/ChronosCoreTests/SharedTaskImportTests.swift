import XCTest
@testable import ChronosCore

/// Tests for the portable shared-task import decomposition (`splitSharedTaskLines`): plain-text
/// multi-line splitting, bullet stripping, ICS VTODO SUMMARY extraction, and the bulk cap. All
/// pure / deterministic so they run on the Windows/Linux CI that builds ChronosCore. Mirrors the
/// Android `SharedTaskImportTest` semantics.
final class SharedTaskImportTests: XCTestCase {

    // MARK: Plain text

    func testSingleLine() {
        XCTAssertEqual(splitSharedTaskLines("Buy milk"), ["Buy milk"])
    }

    func testMultiLineSplitsPerLine() {
        let text = "Buy milk\nCall dentist\nFinish report"
        XCTAssertEqual(splitSharedTaskLines(text), ["Buy milk", "Call dentist", "Finish report"])
    }

    func testBlankLinesAndWhitespaceDropped() {
        let text = "  Buy milk  \n\n   \nCall dentist\n"
        XCTAssertEqual(splitSharedTaskLines(text), ["Buy milk", "Call dentist"])
    }

    func testBulletPrefixesStripped() {
        let text = "- Buy milk\n* Call dentist\n+ Finish report"
        XCTAssertEqual(splitSharedTaskLines(text), ["Buy milk", "Call dentist", "Finish report"])
    }

    func testOnlyLeadingBulletStripped() {
        // A bullet mid-line must NOT be stripped; only the single leading marker.
        XCTAssertEqual(splitSharedTaskLines("- Buy milk - 2%"), ["Buy milk - 2%"])
    }

    func testBulletWithoutTrailingSpaceNotStripped() {
        // Android strips only "- ", "* ", "+ " (with trailing space), so "-Buy" stays intact.
        XCTAssertEqual(splitSharedTaskLines("-Buy milk"), ["-Buy milk"])
    }

    func testEmptyInputYieldsEmpty() {
        XCTAssertTrue(splitSharedTaskLines("").isEmpty)
        XCTAssertTrue(splitSharedTaskLines("   \n  \n").isEmpty)
    }

    func testCapAtMax() {
        let many = (1...(maxBulkImportTasks + 50)).map { "Task \($0)" }.joined(separator: "\n")
        XCTAssertEqual(splitSharedTaskLines(many).count, maxBulkImportTasks)
    }

    // MARK: ICS VTODO

    func testIcsExtractsVtodoSummariesOnly() {
        let ics = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        SUMMARY:A meeting (should be ignored)
        END:VEVENT
        BEGIN:VTODO
        SUMMARY:Buy milk
        END:VTODO
        BEGIN:VTODO
        SUMMARY:Call dentist
        END:VTODO
        END:VCALENDAR
        """
        XCTAssertEqual(splitSharedTaskLines(ics), ["Buy milk", "Call dentist"])
    }

    func testIcsSummaryWithParameters() {
        let ics = """
        BEGIN:VCALENDAR
        BEGIN:VTODO
        SUMMARY;LANGUAGE=en-US:Finish report
        END:VTODO
        END:VCALENDAR
        """
        XCTAssertEqual(splitSharedTaskLines(ics), ["Finish report"])
    }

    func testIcsNoVtodoYieldsEmpty() {
        let ics = """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        SUMMARY:Just an event
        END:VEVENT
        END:VCALENDAR
        """
        XCTAssertTrue(splitSharedTaskLines(ics).isEmpty)
    }

    func testIcsSummaryOutsideVtodoIgnored() {
        let ics = """
        BEGIN:VCALENDAR
        SUMMARY:Calendar-level summary
        BEGIN:VTODO
        SUMMARY:Real task
        END:VTODO
        END:VCALENDAR
        """
        XCTAssertEqual(splitSharedTaskLines(ics), ["Real task"])
    }
}
