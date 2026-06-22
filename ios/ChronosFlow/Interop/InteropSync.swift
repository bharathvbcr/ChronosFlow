import Foundation
import SwiftData
import BackgroundTasks
import UserNotifications
import ChronosCore

// Background DevTime → ChronosFlow import pipeline — the iOS-native analogue of Android's
// `InteropSyncManager` + `InteropSyncWorker` (app/interop/). A `BGAppRefreshTask` on a ~6h cadence
// (mirroring `InteropSyncWorker.SYNC_INTERVAL_HOURS = 6`) reads the peer's shared payload, runs it
// through the deterministic ChronosCore `InteropDedup` pass, mirrors the surviving tasks into the
// SwiftData store with stable ids, prunes vanished imports, and schedules a reminder-lead alert per
// imported task. The whole pipeline is import-only (DevTime → ChronosFlow), matching the Android MVP.
//
// Trust / discovery model (Android-parity, with the iOS substitutions):
//   • Android reads a signature-pinned ContentProvider; iOS has no ContentProvider, so the peer
//     (Meridian / DevTime, bundle `com.Meridian.VBCR`) writes a JSON payload into a SHARED App-Group
//     container both apps are entitled to. The App-Group entitlement IS the trust boundary on iOS
//     (team-scoped sandbox) — there is no certificate pinning API, exactly as PARITY notes.
//   • Everything is gated on `ChronosSettings.interopConsentGranted`: when consent is off this is a
//     SILENT no-op and never touches existing imports — the same "leave the mirror untouched" rule
//     `InteropSyncManager.syncFromPeer()` applies when the peer isn't installed.
//
// This file is intentionally THIN over ChronosCore: all determinism (cap → collapse-by-(title,due) →
// deterministic id → title/externalId normalization → reminder-lead) lives in `InteropDedup`/
// `InteropContract` and is unit-tested in `InteropDedupTests`. This layer only does I/O: read the
// payload, upsert/prune the @Model rows, and hand reminder instants to `ChronosNotifications`.
enum InteropSync {

    /// BGTask identifier — MUST match the app's `ChronosBackgroundSync.interopTaskIdentifier` and the
    /// `BGTaskSchedulerPermittedIdentifiers` Info.plist declaration (see RECONCILIATION note below).
    static let taskIdentifier = "com.chronosflow.interop.sync"

    /// 6h cadence — Android parity with `InteropSyncWorker.SYNC_INTERVAL_HOURS = 6`.
    static let interval: TimeInterval = 6 * 60 * 60

    /// The peer we import from (Meridian / DevTime). Sourced from the shared contract.
    static let peer = InteropContract.peer

    private static var defaults: UserDefaults {
        UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
    }

    // App-Group keys recording last-run state (so a settings "Connected apps" card can surface it).
    private static let lastSyncKey = "interop.lastSyncDate"
    private static let lastResultKey = "interop.lastResult"

    /// Timestamp of the last completed interop sync, for the settings UI. `nil` if none yet.
    static var lastSyncDate: Date? {
        let t = defaults.double(forKey: lastSyncKey)
        return t > 0 ? Date(timeIntervalSince1970: t) : nil
    }

    /// Human-readable result of the last run (count mirrored, skipped reason, or failure).
    static var lastResult: String? { defaults.string(forKey: lastResultKey) }

    // MARK: - Registration / scheduling (mirrors ChronosAutoBackup)
    //
    // RECONCILIATION (do NOT edit ChronosFlowApp.swift from here): the app entry already declares a
    // `ChronosBackgroundSync` scaffold that registers `com.chronosflow.interop.sync` with a stub
    // handler and reschedules it. To activate the real pipeline, the app entry should route that
    // identifier into `InteropSync.handleRefresh(_:)` instead of the stub, e.g. in `registerAll()`:
    //
    //     BGTaskScheduler.shared.register(forTaskWithIdentifier: InteropSync.taskIdentifier, using: nil) { task in
    //         InteropSync.handleRefresh(task)
    //     }
    //
    // and add `com.chronosflow.interop.sync` to Info.plist `BGTaskSchedulerPermittedIdentifiers`
    // (currently only `com.chronosflow.autobackup` is declared, so the scaffold silently skips it).
    // Alternatively the entry can simply call `InteropSync.register()` + `InteropSync.schedule()`
    // alongside the existing `ChronosAutoBackup.register()` / `.schedule()` lines.

    /// Register the background task handler. Call once, early, at launch (BGTaskScheduler rule), the
    /// same way `ChronosAutoBackup.register()` is wired. No-op-safe to call only when the identifier
    /// is declared in Info.plist — registering an undeclared identifier traps, so the app entry should
    /// gate on `BGTaskSchedulerPermittedIdentifiers` (as `ChronosBackgroundSync` already does).
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            handleRefresh(task)
        }
    }

    /// Enqueue the next run (~6h out). Safe to call repeatedly; the scheduler de-dupes by identifier.
    /// A failed submit (e.g. simulator without background support) is non-fatal. Unlike auto-backup
    /// this is NOT gated on consent here — an enqueued run is cheap and `runSync()` itself is the
    /// consent gate, so toggling consent on between schedule and fire still works without a re-schedule.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
    }

    /// BGTask entry point: run the import, always reschedule the next cadence, then report completion.
    /// Mirrors `ChronosAutoBackup.handle(_:)` and the Android worker's "reschedule then finish" shape.
    static func handleRefresh(_ task: BGTask) {
        let work = Task {
            let outcome = await runSync()
            schedule()  // keep the cadence alive regardless of this run's result
            task.setTaskCompleted(success: outcome.isSuccess)
        }
        // If the system reclaims the task early, cancel our work and report not-completed.
        task.expirationHandler = { work.cancel() }
    }

    // MARK: - Sync run (shared by the scheduler and a manual "Sync now" trigger)

    enum Outcome: Equatable {
        /// Consent off, or the peer has no payload — nothing changed, existing imports kept.
        case skipped(reason: String)
        /// Mirrored `mirrored` task(s) and pruned `pruned` stale import(s).
        case success(mirrored: Int, pruned: Int)
        case failure(message: String)

        var isSuccess: Bool {
            switch self {
            case .success, .skipped: true
            case .failure: false
            }
        }
    }

    /// Read the peer payload, dedup via ChronosCore, mirror into SwiftData, prune stale imports, and
    /// (re)schedule reminders. Idempotent: deterministic ids mean repeated runs REPLACE in place.
    /// Records last-run status for the UI. Safe to call from the BGTask handler or a manual trigger.
    @discardableResult
    @MainActor
    static func runSync(now: Date = .now,
                        context: ModelContext = ChronosStore.shared.mainContext,
                        settings: ChronosSettings = .shared) async -> Outcome {
        // Gate 1 — consent. Off → silent no-op; deliberately leave existing imports untouched so a
        // transient consent toggle / missing payload doesn't churn the mirror (Android parity:
        // "skip, keeping existing imports").
        guard settings.interopConsentGranted else {
            return record(.skipped(reason: "Interop consent off"))
        }

        // Gate 2 — payload availability. No peer payload (peer not installed, or hasn't shared) →
        // no-op, keep existing imports (matches `!client.isPeerInstalled()` early return).
        guard let raw = readPeerTasks() else {
            return record(.skipped(reason: "No companion payload"))
        }

        // Deterministic pass entirely in ChronosCore: cap → collapse-by-(title,due) → ids → normalize.
        let plan = InteropDedup.plan(raw, peer: peer)

        do {
            let priorIDs = try existingImportedIDs(context: context)
            let staleIDs = plan.staleIDs(priorImportedIDs: priorIDs)

            // Upsert every planned task by its deterministic id (replace in place if present).
            for imported in plan.tasks {
                upsert(imported, now: now, context: context)
            }
            // Prune imports that vanished upstream (and cancel their reminders).
            for staleID in staleIDs {
                deleteImported(id: staleID, context: context)
                ChronosNotifications.shared.cancel(idPrefix: reminderIdentifier(for: staleID))
            }
            try context.save()

            // Schedule reminder-lead alerts for surviving tasks (outside the save, like Android which
            // schedules alarms outside the DB transaction). `now` is injected into the pure helper.
            let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
            let reminders = InteropDedup.reminders(for: plan, now: nowMillis)
            let firedIDs = Set(reminders.map(\.id))
            await scheduleReminders(reminders)
            // Cancel reminders for kept-but-no-longer-firing tasks (completed / undated / past due),
            // so a task that became complete upstream stops nagging.
            for imported in plan.tasks where !firedIDs.contains(imported.id) {
                ChronosNotifications.shared.cancel(idPrefix: reminderIdentifier(for: imported.id))
            }

            return record(.success(mirrored: plan.tasks.count, pruned: staleIDs.count))
        } catch {
            return record(.failure(message: error.localizedDescription))
        }
    }

    // MARK: - Peer payload (App-Group shared container)

    /// Read the peer's shared task payload, or `nil` when the peer hasn't written one (not installed /
    /// not yet shared). Returns `[]` (NOT `nil`) when the peer explicitly shared an empty list, so the
    /// caller still prunes stale imports — distinguishing "peer absent" from "peer shared nothing",
    /// the same distinction Android draws between `!isPeerInstalled()` (skip) and an empty cursor (prune).
    static func readPeerTasks() -> [RemoteInteropTask]? {
        guard let url = peerPayloadURL(), FileManager.default.fileExists(atPath: url.path) else {
            return nil
        }
        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .millisecondsSince1970  // peer writes epoch-millis instants
            let payload = try decoder.decode(InteropPeerPayload.self, from: data)
            // Only trust a payload that names US as the recipient and the expected peer as author.
            guard payload.peer == peer else { return nil }
            return payload.tasks.map(\.asRemoteInteropTask)
        } catch {
            // Corrupt / partially-written payload — treat as "no payload" and keep existing imports.
            return nil
        }
    }

    /// The shared-container URL the peer writes its task payload to. Lives in the App-Group container
    /// both apps are entitled to, under a stable filename + the peer's share path (`InteropContract`).
    private static func peerPayloadURL() -> URL? {
        let fm = FileManager.default
        guard let base = fm.containerURL(forSecurityApplicationGroupIdentifier: ChronosStore.appGroup) else {
            return nil
        }
        // e.g. <group>/Interop/com.Meridian.VBCR/tasks.json
        return base
            .appendingPathComponent("Interop", isDirectory: true)
            .appendingPathComponent(peer, isDirectory: true)
            .appendingPathComponent("\(InteropContract.pathTasks).json")
    }

    // MARK: - SwiftData mirroring

    /// All ids currently in the store that belong to this peer's import namespace
    /// (`interop:<peer>:` prefix), so the prune pass can compute stale ids via `plan.staleIDs(...)`.
    private static func existingImportedIDs(context: ModelContext) throws -> Set<String> {
        let prefix = "interop:\(peer):"
        let descriptor = FetchDescriptor<TaskItem>(
            predicate: #Predicate { $0.id.starts(with: prefix) })
        return Set(try context.fetch(descriptor).map(\.id))
    }

    /// Insert-or-update a single imported task by its deterministic id. Imported rows are treated as
    /// freshly mirrored each sync (Android sets createdAt = updatedAt = now), and the title/priority/
    /// completion/due come straight from the normalized `ImportedInteropTask`.
    private static func upsert(_ imported: ImportedInteropTask, now: Date, context: ModelContext) {
        let id = imported.id
        let due = imported.dueAtMillis.map { Date(timeIntervalSince1970: Double($0) / 1000) }
        let existing = (try? context.fetch(FetchDescriptor<TaskItem>(
            predicate: #Predicate { $0.id == id })))?.first

        if let task = existing {
            task.title = imported.title
            task.isCompleted = imported.isCompleted
            task.priority = imported.priority
            task.dueDate = due
            task.updatedAt = now
        } else {
            context.insert(TaskItem(
                id: id,
                title: imported.title,
                detail: nil,
                isCompleted: imported.isCompleted,
                priority: imported.priority,
                dueDate: due,
                createdAt: now,
                updatedAt: now))
        }
    }

    private static func deleteImported(id: String, context: ModelContext) {
        guard let task = (try? context.fetch(FetchDescriptor<TaskItem>(
            predicate: #Predicate { $0.id == id })))?.first else { return }
        context.delete(task)
    }

    // MARK: - Reminders (reminder-lead, via ChronosNotifications)

    /// Schedule a one-shot local notification for each interop reminder at its computed trigger
    /// instant. The trigger instant (lead-before-due, or due if the lead already passed) is decided by
    /// the pure `InteropDedup.reminder(...)` — this only renders the notification. Stable per-task
    /// identifier so a re-sync replaces rather than duplicates.
    ///
    /// Reuses the existing `ChronosNotifications` task category/thread so an imported-task reminder
    /// groups with the user's own task reminders and carries the Complete/Snooze actions + the
    /// `taskID`/`section` userInfo the shared `ChronosNotificationDelegate` already handles. Scheduling
    /// is done directly here (rather than `scheduleTask(_:)`) because the fire instant is the computed
    /// lead, not the task's raw due date, and an imported `TaskItem` is mirrored under a deterministic
    /// id the delegate's `taskID` fetch resolves unchanged.
    @MainActor
    private static func scheduleReminders(_ reminders: [InteropReminder]) async {
        let center = UNUserNotificationCenter.current()
        for reminder in reminders {
            let fireDate = Date(timeIntervalSince1970: Double(reminder.triggerAtMillis) / 1000)
            guard fireDate > .now else { continue }  // never schedule in the past

            let content = UNMutableNotificationContent()
            content.title = reminder.title
            content.body = "Shared from your companion app."
            content.sound = .default
            content.categoryIdentifier = ChronosNotifications.taskCategory
            content.threadIdentifier = ChronosNotifications.taskThread
            content.interruptionLevel = .active
            content.userInfo = ["taskID": reminder.id, "section": "tasks"]

            let trigger = UNCalendarNotificationTrigger(
                dateMatching: Calendar.current.dateComponents(
                    [.year, .month, .day, .hour, .minute], from: fireDate),
                repeats: false)
            try? await center.add(UNNotificationRequest(
                identifier: reminderIdentifier(for: reminder.id), content: content, trigger: trigger))
        }
    }

    /// Stable notification-id prefix for an imported task's reminder, so `cancel(idPrefix:)` can clear
    /// it and a re-add replaces it. Derived from the deterministic task id.
    private static func reminderIdentifier(for taskID: String) -> String {
        "interop-\(taskID)"
    }

    // MARK: - Status recording

    @discardableResult
    private static func record(_ outcome: Outcome) -> Outcome {
        defaults.set(Date().timeIntervalSince1970, forKey: lastSyncKey)
        switch outcome {
        case .skipped(let reason):
            defaults.set(reason, forKey: lastResultKey)
        case .success(let mirrored, let pruned):
            defaults.set("Mirrored \(mirrored) task(s), pruned \(pruned)", forKey: lastResultKey)
        case .failure(let message):
            defaults.set("Interop sync failed: \(message)", forKey: lastResultKey)
        }
        return outcome
    }
}
