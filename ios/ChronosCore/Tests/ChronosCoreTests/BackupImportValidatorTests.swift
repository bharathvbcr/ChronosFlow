import XCTest
@testable import ChronosCore

/// Tests for the schema-aware backup import validator. Mirrors the Android
/// ChronosDataImportRepositoryTest cases (restore-all, skip-non-empty, ignore-unknown-tables/columns,
/// reject-non-exports, nothing-to-import) plus the AttachmentDTO Codable round-trip and the
/// mergeIfEmpty/destructive decision helper. Pure logic — no DB, no file I/O, no wall clock.
final class BackupImportValidatorTests: XCTestCase {

    // A live schema mirroring the three tables the Android test seeds, all currently empty.
    private func emptySchema() -> LiveSchema {
        LiveSchema(columnsByTable: [
            "tasks": ["id", "title", "description", "isCompleted", "priority",
                      "dueDate", "createdAt", "updatedAt"],
            "goals": ["id", "title", "description", "category", "targetValue",
                      "startDate", "targetDate", "progressValue", "isCompleted"],
            "sleep_tracks": ["id", "date", "plannedStartMinute", "plannedEndMinute",
                             "actualStartMinute", "actualEndMinute", "sleepQuality",
                             "windDownNotes", "interruptedCount", "source"]
        ])
    }

    private func fullExport(tablesJSON: String, version: Int = 2) -> String {
        """
        {"formatVersion": \(version), "exportKind": "chronosflow-full-data", "tables": \(tablesJSON)}
        """
    }

    // MARK: - Format detection

    func testDetectFullDataValidVersions() {
        XCTAssertEqual(
            BackupImportValidator.detectFormat(json: fullExport(tablesJSON: "{}", version: 1)),
            .fullData(version: 1)
        )
        XCTAssertEqual(
            BackupImportValidator.detectFormat(json: fullExport(tablesJSON: "{}", version: 2)),
            .fullData(version: 2)
        )
    }

    func testDetectUnsupportedVersion() {
        XCTAssertEqual(
            BackupImportValidator.detectFormat(json: fullExport(tablesJSON: "{}", version: 99)),
            .unsupportedVersion(version: 99)
        )
        // Absent formatVersion => 0 => out of range (Android optInt default 0).
        XCTAssertEqual(
            BackupImportValidator.detectFormat(json: #"{"exportKind": "chronosflow-full-data"}"#),
            .unsupportedVersion(version: 0)
        )
    }

    func testDetectNotChronosExport() {
        XCTAssertEqual(
            BackupImportValidator.detectFormat(json: #"{"exportKind": "some-other-app"}"#),
            .notChronosExport
        )
        XCTAssertEqual(
            BackupImportValidator.detectFormat(json: "{}"),
            .notChronosExport
        )
    }

    func testDetectUnreadable() {
        XCTAssertEqual(BackupImportValidator.detectFormat(json: "not json at all"), .unreadable)
        // A JSON array is valid JSON but not an object.
        XCTAssertEqual(BackupImportValidator.detectFormat(json: "[1,2,3]"), .unreadable)
    }

    func testIsFullDataExportProbe() {
        XCTAssertTrue(BackupImportValidator.isFullDataExport(json: fullExport(tablesJSON: "{}")))
        // Newer/unsupported version is still recognized as "our" export (legacy probe).
        XCTAssertTrue(
            BackupImportValidator.isFullDataExport(json: fullExport(tablesJSON: "{}", version: 99))
        )
        XCTAssertFalse(BackupImportValidator.isFullDataExport(json: #"{"exportKind": "other"}"#))
        XCTAssertFalse(BackupImportValidator.isFullDataExport(json: "garbage"))
    }

    // MARK: - Plan: restore all (mirror "import restores every exported table")

    func testRestoresEveryExportedTableIntoEmptyDatabase() {
        let json = fullExport(tablesJSON: """
        {
          "tasks": [{"id": "task-1", "title": "Plan transfer", "isCompleted": 0,
                     "priority": 2, "createdAt": 1, "updatedAt": 2}],
          "goals": [{"id": "goal-1", "title": "Run a 10k", "category": "HEALTH",
                     "targetValue": 10, "startDate": "2026-06-01", "progressValue": 3,
                     "isCompleted": 0}],
          "sleep_tracks": [{"id": "sleep-1", "date": "2026-06-11", "actualStartMinute": 1380,
                            "actualEndMinute": 420, "sleepQuality": 4, "interruptedCount": 1,
                            "source": "MANUAL"}]
        }
        """)

        guard case .imported(let s) = BackupImportValidator.plan(json: json, into: emptySchema()) else {
            return XCTFail("expected imported")
        }
        XCTAssertEqual(s.importedTableCount, 3)
        XCTAssertEqual(s.importedRowCount, 3)
        XCTAssertEqual(s.skippedRowCount, 0)
        XCTAssertTrue(s.skippedNonEmptyTables.isEmpty)
    }

    // MARK: - Plan: skip non-empty (mirror "leaves tables with existing data untouched")

    func testSkipsNonEmptyTablesAndFillsTheRest() {
        var schema = emptySchema()
        schema = LiveSchema(columnsByTable: schema.columnsByTable, nonEmptyTables: ["tasks"])

        let json = fullExport(tablesJSON: """
        {
          "tasks": [{"id": "task-1", "title": "Plan transfer", "isCompleted": 0,
                     "priority": 2, "createdAt": 1, "updatedAt": 2}],
          "goals": [{"id": "goal-1", "title": "Run a 10k", "category": "HEALTH",
                     "targetValue": 10, "startDate": "2026-06-01", "progressValue": 3,
                     "isCompleted": 0}],
          "sleep_tracks": [{"id": "sleep-1", "date": "2026-06-11", "source": "MANUAL"}]
        }
        """)

        guard case .imported(let s) = BackupImportValidator.plan(json: json, into: schema) else {
            return XCTFail("expected imported")
        }
        XCTAssertEqual(s.skippedNonEmptyTables, ["tasks"])
        XCTAssertEqual(s.importedTableCount, 2)  // goals + sleep_tracks
        XCTAssertEqual(s.importedRowCount, 2)
    }

    // MARK: - Plan: ignore unknown tables / columns (mirror cross-version tolerance)

    func testIgnoresUnknownTablesAndColumnsFromDifferentSchemaVersion() {
        let json = fullExport(tablesJSON: """
        {
          "tasks": [{"id": "task-1", "title": "From old export", "isCompleted": 0,
                     "priority": 1, "createdAt": 1, "updatedAt": 2,
                     "columnRemovedLongAgo": "ignored"}],
          "table_that_no_longer_exists": [{"anything": 1}]
        }
        """, version: 1)

        guard case .imported(let s) = BackupImportValidator.plan(json: json, into: emptySchema()) else {
            return XCTFail("expected imported")
        }
        XCTAssertEqual(s.importedTableCount, 1)
        XCTAssertEqual(s.importedRowCount, 1)

        // The removed column is intersected out; the surviving columns are the schema ∩ row.
        let decision = BackupImportValidator.decideTable(
            name: "tasks",
            rows: [["id": "task-1", "title": "x", "createdAt": 1, "columnRemovedLongAgo": "ignored"]],
            schema: emptySchema()
        )
        guard case .fill(let cols, let importable, let dropped) = decision else {
            return XCTFail("expected fill")
        }
        XCTAssertEqual(cols, ["createdAt", "id", "title"])
        XCTAssertFalse(cols.contains("columnRemovedLongAgo"))
        XCTAssertEqual(importable, 1)
        XCTAssertEqual(dropped, 0)
    }

    // MARK: - Plan: reject non-exports (mirror "rejects files that are not ChronosFlow exports")

    func testRejectsNonExports() {
        if case .unreadable = BackupImportValidator.plan(json: "not json at all", into: emptySchema()) {
        } else { XCTFail("expected unreadable for garbage") }

        if case .unreadable = BackupImportValidator.plan(
            json: #"{"exportKind": "some-other-app"}"#, into: emptySchema()) {
        } else { XCTFail("expected unreadable for foreign export") }

        if case .unreadable = BackupImportValidator.plan(
            json: fullExport(tablesJSON: "{}", version: 99), into: emptySchema()) {
        } else { XCTFail("expected unreadable for unsupported version") }
    }

    func testRejectsExportWithoutTablesSection() {
        let json = #"{"formatVersion": 2, "exportKind": "chronosflow-full-data"}"#
        if case .unreadable(let reason) = BackupImportValidator.plan(json: json, into: emptySchema()) {
            XCTAssertTrue(reason.contains("no table data"))
        } else { XCTFail("expected unreadable for missing tables") }
    }

    // MARK: - Plan: nothing to import (mirror "reports when the export holds no rows")

    func testNothingToImportWhenExportHoldsNoRows() {
        let json = fullExport(tablesJSON: #"{"tasks": []}"#)
        XCTAssertEqual(BackupImportValidator.plan(json: json, into: emptySchema()), .nothingToImport)
    }

    func testNothingToImportWhenTablesEmptyObject() {
        let json = fullExport(tablesJSON: "{}")
        XCTAssertEqual(BackupImportValidator.plan(json: json, into: emptySchema()), .nothingToImport)
    }

    // MARK: - Row with no usable columns is dropped and counted

    func testRowWithNoUsableColumnsIsSkippedAndCounted() {
        let json = fullExport(tablesJSON: """
        {
          "tasks": [
            {"id": "task-1", "title": "Keeper", "createdAt": 1, "updatedAt": 2},
            {"onlyGarbageColumns": "x", "alsoUnknown": 2}
          ]
        }
        """)
        guard case .imported(let s) = BackupImportValidator.plan(json: json, into: emptySchema()) else {
            return XCTFail("expected imported")
        }
        XCTAssertEqual(s.importedTableCount, 1)
        XCTAssertEqual(s.importedRowCount, 1)
        XCTAssertEqual(s.skippedRowCount, 1)
    }

    func testTableWhereEveryRowDroppedDoesNotCountAsImported() {
        // All rows share no column → table contributes 0 importable rows → not counted as a table,
        // but its dropped rows still register, and with no fills it's nothing-to-import.
        let json = fullExport(tablesJSON: #"{"tasks": [{"x": 1}, {"y": 2}]}"#)
        let result = BackupImportValidator.plan(json: json, into: emptySchema())
        // skippedRowCount > 0 but importedRowCount == 0 and no skippedNonEmpty → NothingToImport.
        XCTAssertEqual(result, .nothingToImport)
    }

    // MARK: - decideTable edge cases

    func testDecideUnknownTable() {
        XCTAssertEqual(
            BackupImportValidator.decideTable(name: "ghost", rows: [["a": 1]], schema: emptySchema()),
            .unknownTable
        )
    }

    func testDecideNoRowsForKnownTable() {
        XCTAssertEqual(
            BackupImportValidator.decideTable(name: "tasks", rows: [], schema: emptySchema()),
            .noRows
        )
        XCTAssertEqual(
            BackupImportValidator.decideTable(name: "tasks", rows: nil, schema: emptySchema()),
            .noRows
        )
    }

    func testDecideSkippedNonEmpty() {
        let schema = LiveSchema(
            columnsByTable: emptySchema().columnsByTable,
            nonEmptyTables: ["tasks"]
        )
        XCTAssertEqual(
            BackupImportValidator.decideTable(
                name: "tasks",
                rows: [["id": "1", "title": "x"]],
                schema: schema
            ),
            .skippedNonEmpty
        )
    }

    func testDecideNonObjectRowIsDropped() {
        let decision = BackupImportValidator.decideTable(
            name: "tasks",
            rows: [["id": "1", "title": "x"], "i-am-a-string", 42],
            schema: emptySchema()
        )
        guard case .fill(_, let importable, let dropped) = decision else {
            return XCTFail("expected fill")
        }
        XCTAssertEqual(importable, 1)
        XCTAssertEqual(dropped, 2)
    }

    // MARK: - shouldFill / RestoreMode helper

    func testShouldFillMergeIfEmptyRespectsEmptiness() {
        let schema = LiveSchema(
            columnsByTable: emptySchema().columnsByTable,
            nonEmptyTables: ["tasks"]
        )
        // Non-empty known table → not filled in merge mode.
        XCTAssertFalse(
            BackupImportValidator.shouldFill(table: "tasks", mode: .mergeIfEmpty, schema: schema)
        )
        // Empty known table → filled.
        XCTAssertTrue(
            BackupImportValidator.shouldFill(table: "goals", mode: .mergeIfEmpty, schema: schema)
        )
        // Unknown table → never filled.
        XCTAssertFalse(
            BackupImportValidator.shouldFill(table: "ghost", mode: .mergeIfEmpty, schema: schema)
        )
    }

    func testShouldFillDestructiveAlwaysTrue() {
        let schema = LiveSchema(
            columnsByTable: emptySchema().columnsByTable,
            nonEmptyTables: ["tasks", "goals"]
        )
        XCTAssertTrue(
            BackupImportValidator.shouldFill(table: "tasks", mode: .destructive, schema: schema)
        )
        // Even unknown tables return true in destructive mode (caller wipes everything).
        XCTAssertTrue(
            BackupImportValidator.shouldFill(table: "ghost", mode: .destructive, schema: schema)
        )
    }

    // MARK: - AttachmentDTO Codable round-trip

    func testAttachmentDTORoundTrip() throws {
        let original = AttachmentDTO(
            id: "att-1",
            displayName: "diagram.png",
            mimeType: "image/png",
            sizeBytes: 2048,
            kind: .image,
            storageMode: .imported,
            reference: "file:///x/diagram.png",
            persistedUriPermission: true,
            isFeaturedImage: true
        )
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(AttachmentDTO.self, from: data)
        XCTAssertEqual(decoded, original)
    }

    func testAttachmentDTODecodesAndroidStyleJSON() throws {
        // Field names + enum string values mirror the Kotlin data class so cross-platform JSON decodes.
        let json = """
        {
          "id": "att-2",
          "displayName": "notes.txt",
          "kind": "FILE",
          "storageMode": "LINKED",
          "reference": "content://docs/notes"
        }
        """
        let dto = try JSONDecoder().decode(AttachmentDTO.self, from: Data(json.utf8))
        XCTAssertEqual(dto.id, "att-2")
        XCTAssertEqual(dto.kind, .file)
        XCTAssertEqual(dto.storageMode, .linked)
        // Absent optionals fall back to Kotlin defaults.
        XCTAssertNil(dto.mimeType)
        XCTAssertNil(dto.sizeBytes)
        XCTAssertFalse(dto.persistedUriPermission)
        XCTAssertFalse(dto.isFeaturedImage)
    }

    func testAttachmentDTOArrayRoundTrip() throws {
        let attachments = [
            AttachmentDTO(id: "a", displayName: "1", kind: .image, storageMode: .imported,
                          reference: "r1"),
            AttachmentDTO(id: "b", displayName: "2", mimeType: "application/pdf", sizeBytes: 99,
                          kind: .file, storageMode: .linked, reference: "r2")
        ]
        let data = try JSONEncoder().encode(attachments)
        let decoded = try JSONDecoder().decode([AttachmentDTO].self, from: data)
        XCTAssertEqual(decoded, attachments)
    }

    func testAttachmentDTORejectsUnknownEnumValue() {
        let json = #"{"id":"x","displayName":"y","kind":"VIDEO","storageMode":"LINKED","reference":"r"}"#
        XCTAssertThrowsError(
            try JSONDecoder().decode(AttachmentDTO.self, from: Data(json.utf8))
        )
    }
}
