import Foundation
import SwiftData
import ChronosCore

// Device-to-device transfer snapshot (BK02) — the iOS analogue of Android's
// `ChronosPortableBackupRepository` (android_transfer/chronosflow_portable_backup.json: a full-data
// JSON written on install and restored at startup when the DB is empty).
//
// On iOS there is no install-time file drop, so the snapshot lives in a stable, well-known path inside
// the shared App-Group container ("DeviceTransfer/chronosflow_portable_backup.json"). A migration tool
// (or a future "prepare this device for transfer" action) can place a file there; on the next launch,
// `restoreIfNeeded` decodes it and — only if the live store is empty — merges it in via the shared
// `ChronosBackupService.restore(..., mode: .mergeIfEmpty)`, then archives the file so it never re-applies.
//
// All accept/skip decisions are delegated to the shared `BackupImportValidator` (mergeIfEmpty path),
// so a transfer never clobbers live data and the behaviour matches Android byte-for-byte. This type
// only does file I/O + SwiftData writes; the policy lives in ChronosCore.
enum ChronosDeviceTransfer {

    /// Fixed file name, parallel to Android's `chronosflow_portable_backup.json`.
    static let snapshotFileName = "chronosflow_portable_backup.json"
    private static let folderName = "DeviceTransfer"
    private static let archiveFolderName = "DeviceTransfer/Applied"

    private static var fileManager: FileManager { .default }

    private static func transferBase() throws -> URL {
        let fm = fileManager
        let base = fm.containerURL(forSecurityApplicationGroupIdentifier: ChronosStore.appGroup)
            ?? fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let folder = base.appendingPathComponent(folderName, isDirectory: true)
        if !fm.fileExists(atPath: folder.path) {
            try fm.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        return folder
    }

    /// Full path to the pending transfer snapshot (whether or not it exists).
    static func snapshotURL() throws -> URL {
        try transferBase().appendingPathComponent(snapshotFileName)
    }

    // MARK: - Write (prepare a device for transfer)

    /// Serialize the whole store to the portable snapshot path so it can be carried to a new device
    /// (e.g. via a Finder/Files copy of the App-Group container, or a migration helper). Reuses the
    /// shared export — no re-implemented serialization. Returns the file URL on success.
    @MainActor
    @discardableResult
    static func writeSnapshot(from context: ModelContext) throws -> URL {
        let backup = ChronosBackupService.export(from: context)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(backup)
        let url = try snapshotURL()
        try data.write(to: url, options: .atomic)
        return url
    }

    // MARK: - Restore at launch

    /// Outcome of the startup transfer probe, for logging / UI surfacing.
    enum Result: Equatable {
        /// No pending snapshot file was present.
        case noSnapshot
        /// A snapshot existed but the store already held data, so nothing was merged (file kept).
        case storeNotEmpty
        /// A snapshot existed but wasn't a readable ChronosFlow full-data export; it was quarantined.
        case unreadable(reason: String)
        /// The snapshot was merged into the empty store; `insertedRows` rows across `filledTables` tables.
        case restored(insertedRows: Int, filledTables: [String])
    }

    /// True when every user-data entity is empty — the Android "DB empty" gate that allows a startup
    /// restore to run. Built on the same per-table emptiness the validator's `LiveSchema` uses.
    @MainActor
    static func isStoreEmpty(_ context: ModelContext) -> Bool {
        ChronosBackupService.liveSchema(in: context).nonEmptyTables.isEmpty
    }

    /// On launch: if a pending transfer snapshot exists AND the store is empty, decode and merge it
    /// (mergeIfEmpty — never overwrites live data), then archive the file so it never re-applies.
    /// A snapshot that isn't a readable export is quarantined (moved to Applied/ with a `.bad` suffix)
    /// so it doesn't trip every launch. Safe to call once, early in `ChronosFlowApp`, BEFORE the
    /// first-run seed (otherwise the seeded data makes the store non-empty and the transfer is skipped).
    @MainActor
    @discardableResult
    static func restoreIfNeeded(into context: ModelContext) -> Result {
        guard let url = try? snapshotURL(), fileManager.fileExists(atPath: url.path) else {
            return .noSnapshot
        }
        // Gate on emptiness first so we never read/parse a large file when we'd skip it anyway.
        guard isStoreEmpty(context) else { return .storeNotEmpty }

        guard let data = try? Data(contentsOf: url), let json = String(data: data, encoding: .utf8) else {
            quarantine(url, reason: "unreadable-bytes")
            return .unreadable(reason: "The transfer file could not be read")
        }
        // Delegate the format gate to the shared validator (exportKind + version range).
        switch BackupImportValidator.detectFormat(json: json) {
        case .fullData:
            break
        case .unsupportedVersion(let v):
            quarantine(url, reason: "unsupported-v\(v)")
            return .unreadable(reason: "The transfer file uses format \(v), which this app cannot read")
        case .notChronosExport, .unreadable:
            quarantine(url, reason: "not-an-export")
            return .unreadable(reason: "The transfer file is not a ChronosFlow data export")
        }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let backup = try? decoder.decode(ChronosBackup.self, from: data) else {
            quarantine(url, reason: "decode-failed")
            return .unreadable(reason: "The transfer file could not be decoded")
        }

        let summary = ChronosBackupService.restore(backup, into: context, mode: .mergeIfEmpty)
        archive(url)
        return .restored(insertedRows: summary.insertedRows, filledTables: summary.filledTables)
    }

    // MARK: - Archiving / quarantine

    private static func archiveFolder() throws -> URL {
        let fm = fileManager
        let base = fm.containerURL(forSecurityApplicationGroupIdentifier: ChronosStore.appGroup)
            ?? fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let folder = base.appendingPathComponent(archiveFolderName, isDirectory: true)
        if !fm.fileExists(atPath: folder.path) {
            try fm.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        return folder
    }

    /// Move an applied snapshot out of the active path so it never re-applies on a later launch.
    private static func archive(_ url: URL) {
        guard let dest = try? archiveFolder().appendingPathComponent(
            "applied-\(Int(Date().timeIntervalSince1970))-\(url.lastPathComponent)") else {
            try? fileManager.removeItem(at: url)
            return
        }
        do { try fileManager.moveItem(at: url, to: dest) } catch { try? fileManager.removeItem(at: url) }
    }

    /// Move an unreadable snapshot aside (parity with Android's "quarantine unreadable files").
    private static func quarantine(_ url: URL, reason: String) {
        guard let dest = try? archiveFolder().appendingPathComponent(
            "\(url.lastPathComponent).\(reason).bad") else {
            try? fileManager.removeItem(at: url)
            return
        }
        do { try fileManager.moveItem(at: url, to: dest) } catch { try? fileManager.removeItem(at: url) }
    }
}
