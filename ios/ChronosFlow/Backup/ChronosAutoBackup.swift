import Foundation
import SwiftData
import BackgroundTasks

// Scheduled automatic backup — the iOS-native analogue of Android's `ChronosAutoBackupManager`
// (daily WorkManager folder export with rolling retention). Instead of a user-chosen SAF tree URI,
// iOS writes timestamped JSON snapshots into an "AutoBackups" folder inside the App-Group container
// and keeps only the most recent `maxRetained` files. A `BGAppRefreshTask` drives the ~daily cadence;
// each run reuses `ChronosBackupService.export(from:)` (no re-implemented serialization) and reschedules
// the next run. The same files feed a restore list in DataManagementView via the existing restore path.

enum ChronosAutoBackup {
    /// Must also be declared in Info.plist `BGTaskSchedulerPermittedIdentifiers` (see NOTE below).
    static let taskIdentifier = "com.chronosflow.autobackup"
    static let maxRetained = 7
    private static let interval: TimeInterval = 24 * 60 * 60

    // App-Group UserDefaults keys (settings-readable).
    private static let lastBackupKey = "autoBackup.lastDate"
    private static let lastResultKey = "autoBackup.lastResult"

    private static var defaults: UserDefaults {
        UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
    }

    /// Whether scheduled backups are on. Sourced from `ChronosSettings.autoBackupEnabled` once that
    /// field exists (see NOTE); until then this reads the same App-Group key directly so the feature
    /// works without editing ChronosSettings.swift. Defaults to `true` (graduated on, Android parity).
    @MainActor static var isEnabled: Bool {
        get { defaults.object(forKey: "flag.autoBackup") == nil ? true : defaults.bool(forKey: "flag.autoBackup") }
        set {
            defaults.set(newValue, forKey: "flag.autoBackup")
            if newValue { schedule() } else { cancel() }
        }
    }

    /// Timestamp of the last successful auto-backup, for the settings UI. `nil` if none yet.
    static var lastBackupDate: Date? {
        let t = defaults.double(forKey: lastBackupKey)
        return t > 0 ? Date(timeIntervalSince1970: t) : nil
    }

    /// Human-readable result of the last run (success filename or failure reason).
    static var lastResult: String? { defaults.string(forKey: lastResultKey) }

    // MARK: - Registration / scheduling

    /// Register the background task handler. Call once, early, at launch (see ChronosFlowApp NOTE).
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            // BGAppRefreshTask is delivered on a background queue; bridge into an async run.
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            handle(refresh)
        }
    }

    /// Enqueue the next run (~24h out) if enabled. Safe to call repeatedly; the scheduler de-dupes
    /// by identifier. No-op when disabled.
    @MainActor static func schedule() {
        guard isEnabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        // A failed submit (e.g. simulator without background support) is non-fatal.
        try? BGTaskScheduler.shared.submit(request)
    }

    static func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
    }

    private static func handle(_ task: BGAppRefreshTask) {
        let work = Task {
            let outcome = await runBackup()
            // Always reschedule the next cadence regardless of this run's result.
            await MainActor.run { schedule() }
            task.setTaskCompleted(success: outcome.isSuccess)
        }
        // If the system needs the task back early, cancel our work and report not-completed.
        task.expirationHandler = { work.cancel() }
    }

    // MARK: - Backup run (shared by the scheduler and a manual "Back up now" trigger)

    enum Outcome {
        case success(fileName: String)
        case failure(message: String)
        var isSuccess: Bool { if case .success = self { return true } else { return false } }
    }

    /// Export the store and write a timestamped snapshot into the App-Group "AutoBackups" folder,
    /// then prune to `maxRetained`. Records last-run status for the UI.
    @discardableResult
    @MainActor static func runBackup() async -> Outcome {
        do {
            let backup = ChronosBackupService.export(from: ChronosStore.shared.mainContext)
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            encoder.outputFormatting = [.prettyPrinted]
            let data = try encoder.encode(backup)

            let folder = try backupsFolder()
            let fileName = "chronosflow-backup-\(stampFormatter.string(from: Date())).json"
            let url = folder.appendingPathComponent(fileName)
            try data.write(to: url, options: .atomic)

            prune(in: folder)
            recordSuccess(fileName: fileName)
            return .success(fileName: fileName)
        } catch {
            recordFailure(error.localizedDescription)
            return .failure(message: error.localizedDescription)
        }
    }

    // MARK: - Listing / restore (reuses the DataManagementView restore path)

    /// A stored auto-backup file, newest first.
    struct Entry: Identifiable, Hashable {
        let url: URL
        var id: URL { url }
        var fileName: String { url.lastPathComponent }
        let date: Date
    }

    /// All stored auto-backups, newest first. Empty if the folder doesn't exist yet.
    static func listBackups() -> [Entry] {
        guard let folder = try? backupsFolder() else { return [] }
        let urls = (try? FileManager.default.contentsOfDirectory(
            at: folder,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles])) ?? []
        return urls
            .filter { $0.pathExtension.lowercased() == "json" }
            .map { url in
                let date = (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate ?? .distantPast
                return Entry(url: url, date: date)
            }
            .sorted { $0.date > $1.date }
    }

    /// Decode a stored backup so the caller can confirm + restore via `ChronosBackupService.restore`.
    static func decode(_ entry: Entry) throws -> ChronosBackup {
        let data = try Data(contentsOf: entry.url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(ChronosBackup.self, from: data)
    }

    static func delete(_ entry: Entry) {
        try? FileManager.default.removeItem(at: entry.url)
    }

    // MARK: - Internals

    private static func backupsFolder() throws -> URL {
        let fm = FileManager.default
        let base = fm.containerURL(forSecurityApplicationGroupIdentifier: ChronosStore.appGroup)
            ?? fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let folder = base.appendingPathComponent("AutoBackups", isDirectory: true)
        if !fm.fileExists(atPath: folder.path) {
            try fm.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        return folder
    }

    /// Keep the most recent `maxRetained` JSON files (rolling retention); delete the rest.
    private static func prune(in folder: URL) {
        let entries = listBackups()
        guard entries.count > maxRetained else { return }
        for stale in entries.dropFirst(maxRetained) {
            try? FileManager.default.removeItem(at: stale.url)
        }
    }

    private static func recordSuccess(fileName: String) {
        defaults.set(Date().timeIntervalSince1970, forKey: lastBackupKey)
        defaults.set("Saved \(fileName)", forKey: lastResultKey)
    }

    private static func recordFailure(_ message: String) {
        defaults.set("Last backup failed: \(message)", forKey: lastResultKey)
    }

    /// Sortable UTC stamp so descending file order is newest-first (matches Android's formatter).
    private static let stampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyyMMdd-HHmmss'Z'"
        return f
    }()
}
