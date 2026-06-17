import SwiftUI
import SwiftData
import UserNotifications

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
        // Bring up the watchOS Data Layer link (the iOS analogue of the Android Wear DataClient).
        PhoneWatchSync.shared.activate()
        // First-run seed (mirrors the Android first-run onboarding/seed pass), then enqueue backup
        // and push the initial snapshot to the watch.
        Task { @MainActor in
            SeedData.populate(container.mainContext)
            ChronosAutoBackup.schedule()
            PhoneWatchSync.shared.pushSnapshot()
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
