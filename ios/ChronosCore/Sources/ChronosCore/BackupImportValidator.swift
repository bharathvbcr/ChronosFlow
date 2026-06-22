import Foundation

// Schema-aware backup import validation — Foundation-only port of the Android restore pipeline
// (ChronosDataImportRepository.importIntoEmptyTables + ChronosDataExportFormat + the portable-backup
// format detection in ChronosPortableBackupRepository).
//
// This is PURE validation/decision logic: no file I/O, no database access. It decides, given a parsed
// backup payload and a description of the live schema, what would be imported, what would be skipped,
// and why — so the SwiftData restore layer can act on those decisions deterministically.
//
// Android parity notes:
//  - exportKind must equal "chronosflow-full-data" (ChronosDataExportFormat.EXPORT_KIND_FULL_DATA).
//  - formatVersion is tolerated in 1...2 (ChronosDataExportFormat.FORMAT_VERSION == 2); 0/absent or
//    anything outside the range is Unreadable.
//  - Columns are matched by NAME against the current schema: export columns the schema no longer
//    knows are dropped; schema columns absent from a row keep their declared defaults (the importer
//    simply omits them from the INSERT). Unknown tables are ignored.
//  - A table is filled only when currently empty; non-empty tables are left untouched and reported.
//  - A row contributing zero usable columns is skipped and counted (mirrors usableColumns.isEmpty()).

// MARK: - Format constants (mirror ChronosDataExportFormat)

public enum BackupFormat: Sendable {
    /// ChronosDataExportFormat.EXPORT_KIND_FULL_DATA
    public static let fullDataKind = "chronosflow-full-data"
    /// ChronosDataExportFormat.FORMAT_VERSION
    public static let currentVersion = 2
    /// Inclusive range of formatVersion values this app can read (Android: 1..FORMAT_VERSION).
    public static let supportedVersions: ClosedRange<Int> = 1...currentVersion

    static let keyExportKind = "exportKind"
    static let keyFormatVersion = "formatVersion"
    static let keyTables = "tables"
}

// MARK: - Attachment DTO (mirror domain TaskAttachment + TaskAttachmentKind/StorageMode)

/// Codable, Foundation-only mirror of Android `TaskAttachmentKind`.
public enum AttachmentKind: String, Codable, Sendable, CaseIterable {
    case image = "IMAGE"
    case file = "FILE"
}

/// Codable, Foundation-only mirror of Android `TaskAttachmentStorageMode`.
public enum AttachmentStorageMode: String, Codable, Sendable, CaseIterable {
    case linked = "LINKED"
    case imported = "IMPORTED"
}

/// Codable mirror of Android `TaskAttachment` for backup/restore round-tripping.
/// Field names and defaults match the Kotlin data class so JSON written by either platform decodes.
public struct AttachmentDTO: Codable, Sendable, Equatable, Identifiable {
    public let id: String
    public let displayName: String
    public let mimeType: String?
    public let sizeBytes: Int64?
    public let kind: AttachmentKind
    public let storageMode: AttachmentStorageMode
    public let reference: String
    public let persistedUriPermission: Bool
    public let isFeaturedImage: Bool

    public init(
        id: String,
        displayName: String,
        mimeType: String? = nil,
        sizeBytes: Int64? = nil,
        kind: AttachmentKind,
        storageMode: AttachmentStorageMode,
        reference: String,
        persistedUriPermission: Bool = false,
        isFeaturedImage: Bool = false
    ) {
        self.id = id
        self.displayName = displayName
        self.mimeType = mimeType
        self.sizeBytes = sizeBytes
        self.kind = kind
        self.storageMode = storageMode
        self.reference = reference
        self.persistedUriPermission = persistedUriPermission
        self.isFeaturedImage = isFeaturedImage
    }

    enum CodingKeys: String, CodingKey {
        case id, displayName, mimeType, sizeBytes, kind, storageMode, reference
        case persistedUriPermission, isFeaturedImage
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        displayName = try c.decode(String.self, forKey: .displayName)
        mimeType = try c.decodeIfPresent(String.self, forKey: .mimeType)
        sizeBytes = try c.decodeIfPresent(Int64.self, forKey: .sizeBytes)
        kind = try c.decode(AttachmentKind.self, forKey: .kind)
        storageMode = try c.decode(AttachmentStorageMode.self, forKey: .storageMode)
        reference = try c.decode(String.self, forKey: .reference)
        // Match Kotlin defaults when the key is absent in an older export.
        persistedUriPermission = try c.decodeIfPresent(Bool.self, forKey: .persistedUriPermission) ?? false
        isFeaturedImage = try c.decodeIfPresent(Bool.self, forKey: .isFeaturedImage) ?? false
    }
}

// MARK: - Detection result (mirror restoreFullFormat / Unreadable reasons)

/// What kind of file a candidate backup payload is. Mirrors the branch in
/// ChronosPortableBackupRepository.restoreFullFormat + the Unreadable reasons in
/// ChronosDataImportRepository.importIntoEmptyTables.
public enum BackupFormatKind: Sendable, Equatable {
    /// A valid full-data export with a readable format version.
    case fullData(version: Int)
    /// A ChronosFlow full-data export whose formatVersion this app cannot read.
    case unsupportedVersion(version: Int)
    /// Parsed JSON, but not a ChronosFlow data export (wrong/absent exportKind).
    case notChronosExport
    /// Not valid JSON / not a JSON object at all.
    case unreadable
}

// MARK: - Import decision model (mirror ChronosDataImportResult)

public enum ImportTableDecision: Sendable, Equatable {
    /// Table is unknown to the current schema and is ignored.
    case unknownTable
    /// Table exists in the schema but the export holds no rows for it.
    case noRows
    /// Table is non-empty in the live DB; it is skipped to avoid overwriting live data.
    case skippedNonEmpty
    /// Table will be filled. `usableColumns` is the per-row column intersection (in schema order),
    /// `importableRowCount` rows contribute at least one usable column, `skippedRowCount` rows
    /// contribute none and are dropped.
    case fill(usableColumns: [String], importableRowCount: Int, skippedRowCount: Int)
}

public struct ImportPlanSummary: Sendable, Equatable {
    /// Tables that would actually be filled.
    public let importedTableCount: Int
    /// Total rows that would be inserted across all filled tables.
    public let importedRowCount: Int
    /// Rows dropped because they shared no column with the live schema.
    public let skippedRowCount: Int
    /// Non-empty tables left untouched (sorted for determinism).
    public let skippedNonEmptyTables: [String]

    public init(
        importedTableCount: Int,
        importedRowCount: Int,
        skippedRowCount: Int,
        skippedNonEmptyTables: [String]
    ) {
        self.importedTableCount = importedTableCount
        self.importedRowCount = importedRowCount
        self.skippedRowCount = skippedRowCount
        self.skippedNonEmptyTables = skippedNonEmptyTables
    }

    public var isNothingToImport: Bool {
        importedRowCount == 0 && skippedNonEmptyTables.isEmpty
    }
}

public enum BackupImportResult: Sendable, Equatable {
    case imported(ImportPlanSummary)
    case nothingToImport
    case unreadable(reason: String)
}

// MARK: - Live schema description (mirror PRAGMA table_info, injected, no DB access)

/// Describes the live database the import would run against. The validator never touches a real
/// database; the SwiftData layer supplies the known user tables, each table's column names, and which
/// tables already hold data.
public struct LiveSchema: Sendable, Equatable {
    /// Column names per user table, by table name. Tables absent here are "unknown" and ignored.
    public let columnsByTable: [String: Set<String>]
    /// Names of tables that currently hold at least one row.
    public let nonEmptyTables: Set<String>

    public init(columnsByTable: [String: Set<String>], nonEmptyTables: Set<String> = []) {
        self.columnsByTable = columnsByTable
        self.nonEmptyTables = nonEmptyTables
    }

    public func columns(for table: String) -> Set<String>? { columnsByTable[table] }
    public func isKnown(_ table: String) -> Bool { columnsByTable[table] != nil }
    public func hasRows(_ table: String) -> Bool { nonEmptyTables.contains(table) }
}

// MARK: - Validator

public enum BackupImportValidator {

    /// Detect the format of a raw backup string without committing to import.
    /// Mirrors the Unreadable gates in importIntoEmptyTables plus restoreFullFormat's exportKind check.
    public static func detectFormat(json: String) -> BackupFormatKind {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data),
              let obj = root as? [String: Any] else {
            return .unreadable
        }
        return detectFormat(object: obj)
    }

    /// Detect format from an already-parsed JSON object (mirror of the same logic).
    public static func detectFormat(object obj: [String: Any]) -> BackupFormatKind {
        let kind = stringValue(obj[BackupFormat.keyExportKind])
        guard kind == BackupFormat.fullDataKind else {
            return .notChronosExport
        }
        // Android: optInt(KEY_FORMAT_VERSION, 0) — absent/non-numeric => 0 => out of range.
        let version = intValue(obj[BackupFormat.keyFormatVersion]) ?? 0
        guard BackupFormat.supportedVersions.contains(version) else {
            return .unsupportedVersion(version: version)
        }
        return .fullData(version: version)
    }

    /// True when the payload is a ChronosFlow full-data export (any exportKind match), regardless of
    /// whether the version is readable. Mirrors ChronosPortableBackupRepository.restoreFullFormat's
    /// "isFullExport" probe that decides whether legacy decoding should be attempted.
    public static func isFullDataExport(json: String) -> Bool {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data),
              let obj = root as? [String: Any] else {
            return false
        }
        return stringValue(obj[BackupFormat.keyExportKind]) == BackupFormat.fullDataKind
    }

    /// Plan a non-destructive "merge if empty" import against a described live schema.
    /// Pure mirror of ChronosDataImportRepository.importIntoEmptyTables, minus the SQL execution:
    /// every accept/skip/drop decision matches, so the caller can insert exactly the planned rows.
    public static func plan(json: String, into schema: LiveSchema) -> BackupImportResult {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data),
              let obj = root as? [String: Any] else {
            return .unreadable(reason: "The file is not a valid ChronosFlow backup")
        }
        switch detectFormat(object: obj) {
        case .unreadable:
            return .unreadable(reason: "The file is not a valid ChronosFlow backup")
        case .notChronosExport:
            return .unreadable(reason: "The file is not a ChronosFlow data export")
        case .unsupportedVersion(let version):
            return .unreadable(
                reason: "This backup uses format \(version), which this app version cannot read"
            )
        case .fullData:
            break
        }

        guard let tables = obj[BackupFormat.keyTables] as? [String: Any] else {
            return .unreadable(reason: "The export contains no table data")
        }

        var importedTableCount = 0
        var importedRowCount = 0
        var skippedRowCount = 0
        var skippedNonEmptyTables: [String] = []

        // Iterate tables deterministically (sorted) so the summary is reproducible; Android iterates
        // in JSON key order, but order does not affect the counts this validator reports.
        for tableName in tables.keys.sorted() {
            switch decideTable(name: tableName, rows: tables[tableName], schema: schema) {
            case .unknownTable, .noRows:
                continue
            case .skippedNonEmpty:
                skippedNonEmptyTables.append(tableName)
            case .fill(_, let importable, let dropped):
                skippedRowCount += dropped
                if importable > 0 {
                    importedTableCount += 1
                    importedRowCount += importable
                }
            }
        }

        let summary = ImportPlanSummary(
            importedTableCount: importedTableCount,
            importedRowCount: importedRowCount,
            skippedRowCount: skippedRowCount,
            skippedNonEmptyTables: skippedNonEmptyTables
        )
        return summary.isNothingToImport ? .nothingToImport : .imported(summary)
    }

    /// Per-table decision for a single table's exported rows against the live schema.
    /// Exposed for callers that drive the insert loop themselves and for fine-grained testing.
    public static func decideTable(
        name tableName: String,
        rows rowsValue: Any?,
        schema: LiveSchema
    ) -> ImportTableDecision {
        // Android: skip non-user tables. The schema's known-table set is the authority here — a table
        // the live schema doesn't know is "unknown" and ignored (covers both non-user and dropped).
        guard let columns = schema.columns(for: tableName), !columns.isEmpty else {
            return .unknownTable
        }
        guard let rows = rowsValue as? [Any], !rows.isEmpty else {
            return .noRows
        }
        if schema.hasRows(tableName) {
            return .skippedNonEmpty
        }

        // Per-row column intersection. Android uses the first usable row's columns implicitly per row;
        // here we report the union of usable columns (in schema-stable, sorted order) for visibility,
        // while counting importable/dropped rows exactly as Android does.
        var importable = 0
        var dropped = 0
        var usableUnion = Set<String>()
        for row in rows {
            guard let dict = row as? [String: Any] else {
                // A non-object row contributes no usable columns → dropped (parity with a row that
                // shares no column with the schema).
                dropped += 1
                continue
            }
            let usable = dict.keys.filter { columns.contains($0) }
            if usable.isEmpty {
                dropped += 1
            } else {
                importable += 1
                usableUnion.formUnion(usable)
            }
        }
        return .fill(
            usableColumns: usableUnion.sorted(),
            importableRowCount: importable,
            skippedRowCount: dropped
        )
    }

    /// Non-destructive merge decision for a single entity/table: should the restore fill it?
    /// `true` only when the live table is empty (Android's "fill only empty tables" guard). The
    /// `.destructive` mode always returns true (caller wipes first). Pure helper for call sites that
    /// pick a restore mode (device-transfer => mergeIfEmpty, factory-reset => destructive).
    public static func shouldFill(
        table tableName: String,
        mode: RestoreMode,
        schema: LiveSchema
    ) -> Bool {
        switch mode {
        case .destructive:
            return true
        case .mergeIfEmpty:
            // Unknown tables are never filled; known-and-empty tables are.
            return schema.isKnown(tableName) && !schema.hasRows(tableName)
        }
    }

    // MARK: - JSON coercion helpers (mirror org.json's loose typing)

    private static func stringValue(_ value: Any?) -> String {
        // org.json optString returns "" when absent; non-strings are stringified there but here we
        // only need exact equality against known kind strings, so return "" for anything non-string.
        (value as? String) ?? ""
    }

    private static func intValue(_ value: Any?) -> Int? {
        if let i = value as? Int { return i }
        if let n = value as? NSNumber { return n.intValue }
        if let d = value as? Double { return Int(d) }
        if let s = value as? String { return Int(s) }
        return nil
    }
}

/// Restore mode selector (mirror the .destructive vs .mergeIfEmpty gate called for in the parity work).
public enum RestoreMode: Sendable, Equatable {
    /// Wipe every entity then insert from backup (explicit factory-reset / replace flows).
    case destructive
    /// Per-table empty guard: only fill currently-empty tables, never overwrite live data
    /// (device-transfer / startup restore). Mirrors importIntoEmptyTables.
    case mergeIfEmpty
}
