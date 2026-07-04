import SwiftUI
import SwiftData
import UserNotifications
import BackgroundTasks

/// App entry point. The iOS-native analogue of `ChronosApplication` + `MainActivity`.
@main
struct ChronosFlowApp: App {
    let container: ModelContainer

    init() {
        // Use the process-wide shared container so App Intents / Shortcuts and the UI agree.
        container = ChronosStore.shared
        // Handle notification action buttons (Taken / Skip / Complete / Snooze) — mirrors the
        // Android BroadcastReceiver action handlers.
        UNUserNotificationCenter.current().delegate = ChronosNotificationDelegate.shared
        // Register the background auto-backup task before launch finishes (BGTaskScheduler rule).
        ChronosAutoBackup.register()
        // Register the background interop / calendar sync tasks (I02 / C03). No-op-safe scaffolding:
        // the handlers reschedule and complete cleanly until those items fill in the real sync work,
        // and registration silently skips any identifier not yet declared in Info.plist's
        // BGTaskSchedulerPermittedIdentifiers so a missing declaration never traps at launch.
        ChronosBackgroundSync.registerAll()
        // Bring up the watchOS Data Layer link (the iOS analogue of the Android Wear DataClient).
        PhoneWatchSync.shared.activate()
        // First-run seed (mirrors the Android first-run onboarding/seed pass), then enqueue backup
        // and push the initial snapshot to the watch.
        // Capture the container locally so this escaping `Task` closure doesn't capture the
        // still-initializing `self` value-type struct (which the compiler rejects).
        let container = container
        Task { @MainActor in
            SeedData.populate(container.mainContext)
            ChronosAutoBackup.schedule()
            ChronosBackgroundSync.scheduleAll()
            // Wire the cross-process focus command bridge as the UI comes up. This is the fix for the
            // #1 parity bug: Live Activity / focus-widget buttons posted commands to the App-Group
            // queue (FocusCommandBridge) but nothing ever drained them, so every control was a silent
            // no-op. startObserving() registers the Darwin observer (apply-while-running) and drain()
            // applies anything queued while the app was terminated, now that the shared timer is ready.
            ChronosFocusCommandRouter.startObserving()
            ChronosFocusCommandRouter.drain()
            PhoneWatchSync.shared.pushSnapshot()
            // Re-arm the evening log reminder each launch so it survives app reinstall / reboot.
            if ChronosSettings.shared.logReminderEnabled {
                await ChronosNotifications.shared.scheduleLogReminder()
            }
            await ChronosNotifications.shared.refreshFoldableReminders()
            BlockLiveActivityCoordinator.refreshToday()
            BlockLiveActivityCoordinator.startStaleObserver()
            ChronosBlockLiveRefreshRouter.startObserving()
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .tint(ChronosColors.brandPrimary)
        }
        .modelContainer(container)
    }
}

// MARK: - Focus command router (app-wide drain of the cross-process bridge)

/// App-wide owner of the `FocusCommandBridge` drain. The Focus UI drains on its own appear/scene
/// events, but those only fire while the Focus tab is mounted. This router makes the bridge live for
/// the WHOLE app lifetime so a Live Activity / focus-widget control applies even when the user is on
/// another tab or the app was just relaunched — the actual fix for the silent-no-op bug.
///
/// It reuses the existing Darwin→Foundation bridge (`FocusCommandObserver`) rather than adding a
/// second `CFNotificationCenter` observer, then forwards each queued command to the process-wide
/// `FocusTimerModel.shared` via the F-FOCUS `apply(command:)` contract. Draining is idempotent: the
/// bridge clears the queue on read, so a double-drain (router + FocusView) simply finds it empty.
@MainActor
enum ChronosFocusCommandRouter {
    private static var started = false
    private static var observer: NSObjectProtocol?

    /// Register the Darwin observer (once) and start forwarding queued commands to the shared timer.
    /// Safe to call repeatedly; idempotent.
    static func startObserving() {
        guard !started else { return }
        started = true
        // Ensure the Darwin → Foundation bridge is live app-wide, not just while FocusView is shown.
        FocusCommandObserver.startIfNeeded()
        // A command tapped while the app is running (any tab) drains immediately.
        observer = NotificationCenter.default.addObserver(
            forName: FocusCommandObserver.didReceive,
            object: nil,
            queue: .main) { _ in
                MainActor.assumeIsolated { drain() }
            }
    }

    /// Apply and clear every queued cross-process focus command, routing each into the shared timer.
    static func drain() {
        FocusCommandBridge.drain { command in
            switch command {
            case .start(let blockID):
                let context = ChronosStore.shared.mainContext
                let id = blockID
                let block = try? context.fetch(
                    FetchDescriptor<TimeBlock>(predicate: #Predicate { $0.id == id })
                ).first
                FocusTimerModel.shared.start(
                    blockTitle: block?.title ?? "Focus session",
                    blockID: blockID,
                    blockMinutes: block?.durationMinutes ?? 25)
            default:
                FocusTimerModel.shared.apply(command: command)
            }
        }
    }
}

// MARK: - Block live refresh router (widget chip actions → app)

/// Drains cross-process nudges from `LiveActivityRefreshBridge` so chip actions update folded
/// reminders immediately without waiting for the next stale-date wakeup.
@MainActor
enum ChronosBlockLiveRefreshRouter {
    private static var started = false

    static func startObserving() {
        guard !started else { return }
        started = true
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            nil,
            { _, _, _, _, _ in
                Task { @MainActor in
                    BlockLiveActivityCoordinator.refreshToday()
                }
            },
            LiveActivityRefreshBridge.darwinName,
            nil,
            .deliverImmediately)
    }
}

// MARK: - Background sync scaffolding (I02 interop / C03 calendar)

/// No-op-safe `BGTaskScheduler` scaffolding for the background DevTime interop (I02) and calendar
/// (C03) sync. This item only establishes registration + scheduling plumbing; the real sync work is
/// filled in by those items (which own `Interop/InteropSync.swift` and `CalendarBackgroundSync.swift`).
///
/// Registration is defensive by design: it skips any identifier not present in the bundle's
/// `BGTaskSchedulerPermittedIdentifiers`, so until the plist declares them this is a true no-op and a
/// missing declaration can never trap `BGTaskScheduler.register` at launch. When an owning item adds
/// its handler it can call `BGTaskScheduler.shared.register` itself; this scaffold steps aside for any
/// identifier it does not recognise.
enum ChronosBackgroundSync {
    /// 6h cadence to mirror the Android InteropSyncWorker periodic schedule.
    static let interopTaskIdentifier = "com.chronosflow.interop.sync"
    /// 7-day calendar sync window refresh (Android calendar background sync parity).
    static let calendarTaskIdentifier = "com.chronosflow.calendar.sync"
    /// Re-render the schedule Live Activity mid-block (Android boundary-alarm parity).
    static let blockLiveTaskIdentifier = "com.chronosflow.blocklive.refresh"

    private static let interopInterval: TimeInterval = 6 * 60 * 60
    private static let calendarInterval: TimeInterval = 24 * 60 * 60
    /// Fallback cadence when no live activity is active to anchor a boundary time.
    private static let blockLiveFallbackInterval: TimeInterval = 15 * 60

    /// Identifiers actually permitted by Info.plist — registering an unlisted identifier traps, so we
    /// gate on this set and silently no-op for anything not yet declared.
    private static var permittedIdentifiers: Set<String> {
        let raw = Bundle.main.object(forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers") as? [String]
        return Set(raw ?? [])
    }

    /// Register the interop + calendar background-sync handlers. Call once, early, at launch.
    static func registerAll() {
        let permitted = permittedIdentifiers
        if permitted.contains(interopTaskIdentifier) {
            // Route the interop identifier into the real DevTime import pipeline (I02) instead of the
            // reschedule-only stub. InteropSync.handleRefresh owns its own reschedule + completion.
            BGTaskScheduler.shared.register(forTaskWithIdentifier: interopTaskIdentifier, using: nil) { task in
                InteropSync.handleRefresh(task)
            }
        }
        if permitted.contains(calendarTaskIdentifier) {
            BGTaskScheduler.shared.register(forTaskWithIdentifier: calendarTaskIdentifier, using: nil) { task in
                handle(task, identifier: calendarTaskIdentifier, interval: calendarInterval)
            }
        }
        if permitted.contains(blockLiveTaskIdentifier) {
            BGTaskScheduler.shared.register(forTaskWithIdentifier: blockLiveTaskIdentifier, using: nil) { task in
                handleBlockLive(task)
            }
        }
    }

    /// Enqueue the next run for each permitted task. Safe to call repeatedly; the scheduler de-dupes
    /// by identifier. A failed submit (e.g. simulator without background support) is non-fatal.
    static func scheduleAll() {
        let permitted = permittedIdentifiers
        if permitted.contains(interopTaskIdentifier) { schedule(interopTaskIdentifier, after: interopInterval) }
        if permitted.contains(calendarTaskIdentifier) { schedule(calendarTaskIdentifier, after: calendarInterval) }
        if permitted.contains(blockLiveTaskIdentifier),
           ChronosSettings.shared.currentBlockLiveActivityEnabled {
            scheduleBlockLive()
        }
    }

    /// Enqueue a block-live refresh at the next stale/boundary time, or the fallback cadence.
    static func scheduleBlockLive(at date: Date? = nil) {
        guard permittedIdentifiers.contains(blockLiveTaskIdentifier),
              ChronosSettings.shared.currentBlockLiveActivityEnabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: blockLiveTaskIdentifier)
        if let date, date > .now {
            request.earliestBeginDate = date
        } else {
            request.earliestBeginDate = Date(timeIntervalSinceNow: blockLiveFallbackInterval)
        }
        try? BGTaskScheduler.shared.submit(request)
    }

    static func cancelBlockLive() {
        guard permittedIdentifiers.contains(blockLiveTaskIdentifier) else { return }
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: blockLiveTaskIdentifier)
    }

    private static func schedule(_ identifier: String, after interval: TimeInterval) {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// Stub handler: always reschedule the next cadence, then complete. Owning items (I02/C03) replace
    /// the body with their real sync once their sync entry points land; the reschedule keeps the
    /// cadence alive in the meantime.
    private static func handle(_ task: BGTask, identifier: String, interval: TimeInterval) {
        schedule(identifier, after: interval)
        task.setTaskCompleted(success: true)
    }

    private static func handleBlockLive(_ task: BGTask) {
        let work = Task { @MainActor in
            BlockLiveActivityCoordinator.refreshToday()
            scheduleBlockLive()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = { work.cancel() }
    }
}
