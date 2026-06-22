import XCTest
@testable import ChronosCore

// Covers the pure block -> device-calendar export contract (CalendarExport.swift): field mapping
// and the save/update/delete/no-op intent decision, including the critical provenance guard that
// imported blocks NEVER write back. Mirrors Android CalendarEventRepositoryImplTest +
// DayDialBlockDelegateTest export/guard paths.

private let utcCal: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()
private func day(_ y: Int, _ mo: Int, _ d: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: mo, day: d))!
}
private func at(_ y: Int, _ mo: Int, _ d: Int, _ h: Int, _ mi: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: mo, day: d, hour: h, minute: mi))!
}

// MARK: - Field mapping

final class CalendarExportFieldsTests: XCTestCase {
    func testMapsStartEndFromDateAndMinutes() {
        let block = ExportableBlock(title: "Deep work", date: day(2025, 6, 9), startMinute: 540,
                                    durationMinutes: 90, timezone: "UTC", provenance: "manual")
        let fields = calendarEventFields(for: block)
        XCTAssertEqual(fields.title, "Deep work")
        XCTAssertEqual(fields.notes, calendarExportNote)
        XCTAssertEqual(fields.notes, "Exported from ChronosFlow")
        XCTAssertEqual(fields.start, at(2025, 6, 9, 9, 0))
        XCTAssertEqual(fields.end, at(2025, 6, 9, 10, 30))
        XCTAssertEqual(fields.timezoneID, "UTC")
        XCTAssertFalse(fields.isAllDay)
    }

    func testEndIsStartPlusDuration() {
        let block = ExportableBlock(title: "X", date: day(2025, 6, 9), startMinute: 0,
                                    durationMinutes: 25, timezone: "UTC", provenance: "task")
        let fields = calendarEventFields(for: block)
        XCTAssertEqual(fields.end.timeIntervalSince(fields.start), 25 * 60)
    }

    func testUnparseableTimezoneFallsBackToUTC() {
        XCTAssertEqual(resolvedTimeZoneID("Not/AZone"), "UTC")
        let block = ExportableBlock(title: "X", date: day(2025, 6, 9), startMinute: 60,
                                    durationMinutes: 30, timezone: "Not/AZone", provenance: "manual")
        let fields = calendarEventFields(for: block)
        XCTAssertEqual(fields.timezoneID, "UTC")
    }

    func testRealTimezoneHonored() {
        XCTAssertEqual(resolvedTimeZone("America/New_York").identifier, "America/New_York")
    }
}

// MARK: - Export intent (save / update / guard)

final class CalendarExportIntentTests: XCTestCase {
    func testNeverExportedBlockSaves() {
        let block = ExportableBlock(title: "New", date: day(2025, 6, 9), startMinute: 540,
                                    durationMinutes: 60, timezone: "UTC", provenance: "manual",
                                    eventID: nil)
        guard case let .save(fields) = exportIntent(for: block) else {
            return XCTFail("expected .save")
        }
        XCTAssertEqual(fields.title, "New")
        XCTAssertEqual(fields.start, at(2025, 6, 9, 9, 0))
    }

    func testAlreadyExportedBlockUpdates() {
        let block = ExportableBlock(title: "Moved", date: day(2025, 6, 9), startMinute: 600,
                                    durationMinutes: 60, timezone: "UTC", provenance: "manual",
                                    eventID: "evt-123")
        guard case let .update(eventID, fields) = exportIntent(for: block) else {
            return XCTFail("expected .update")
        }
        XCTAssertEqual(eventID, "evt-123")
        XCTAssertEqual(fields.start, at(2025, 6, 9, 10, 0))
    }

    // The critical guard: imported calendar blocks must NEVER write back to the device event.
    func testImportedBlockNeverWritesBackOnExport() {
        let imported = ExportableBlock(title: "Their event", date: day(2025, 6, 9), startMinute: 540,
                                       durationMinutes: 60, timezone: "UTC",
                                       provenance: calendarImportedProvenance, eventID: "user-evt")
        XCTAssertEqual(exportIntent(for: imported), .noOp(reason: .importedBlock))
    }

    func testImportedGuardIsCaseInsensitive() {
        let imported = ExportableBlock(title: "Their event", date: day(2025, 6, 9), startMinute: 540,
                                       durationMinutes: 60, timezone: "UTC",
                                       provenance: "CALENDAR", eventID: "user-evt")
        XCTAssertEqual(exportIntent(for: imported), .noOp(reason: .importedBlock))
    }
}

// MARK: - Delete intent (delete / guard / never-exported)

final class CalendarDeleteIntentTests: XCTestCase {
    func testExportedBlockDeletes() {
        let block = ExportableBlock(title: "Gone", date: day(2025, 6, 9), startMinute: 540,
                                    durationMinutes: 60, timezone: "UTC", provenance: "manual",
                                    eventID: "evt-9")
        XCTAssertEqual(deleteIntent(for: block), .delete(eventID: "evt-9"))
    }

    func testNeverExportedBlockIsNoOp() {
        let block = ExportableBlock(title: "Local", date: day(2025, 6, 9), startMinute: 540,
                                    durationMinutes: 60, timezone: "UTC", provenance: "manual",
                                    eventID: nil)
        XCTAssertEqual(deleteIntent(for: block), .noOp(reason: .neverExported))
    }

    func testImportedBlockNeverDeletesUsersEvent() {
        let imported = ExportableBlock(title: "Their event", date: day(2025, 6, 9), startMinute: 540,
                                       durationMinutes: 60, timezone: "UTC",
                                       provenance: calendarImportedProvenance, eventID: "user-evt")
        XCTAssertEqual(deleteIntent(for: imported), .noOp(reason: .importedBlock))
    }
}

// MARK: - Idempotence

final class CalendarExportIdempotenceTests: XCTestCase {
    // Re-running export on an already-exported, unchanged block yields the same update intent.
    func testRepeatedExportIsDeterministic() {
        let block = ExportableBlock(title: "Stable", date: day(2025, 6, 9), startMinute: 480,
                                    durationMinutes: 45, timezone: "UTC", provenance: "routine",
                                    eventID: "evt-1")
        XCTAssertEqual(exportIntent(for: block), exportIntent(for: block))
    }

    func testSaveThenUpdateAfterEventIdAssigned() {
        let beforeExport = ExportableBlock(title: "T", date: day(2025, 6, 9), startMinute: 480,
                                           durationMinutes: 45, timezone: "UTC", provenance: "routine",
                                           eventID: nil)
        guard case .save = exportIntent(for: beforeExport) else { return XCTFail("expected .save") }
        // After the app stores the returned eventID, the next sync should update, not re-insert.
        let afterExport = ExportableBlock(title: "T", date: day(2025, 6, 9), startMinute: 480,
                                          durationMinutes: 45, timezone: "UTC", provenance: "routine",
                                          eventID: "newly-assigned")
        guard case let .update(eventID, _) = exportIntent(for: afterExport) else {
            return XCTFail("expected .update")
        }
        XCTAssertEqual(eventID, "newly-assigned")
    }
}
