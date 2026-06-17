import Foundation
import SwiftData

/// The SwiftData stack — the iOS analogue of the Android Room database (core/data).
/// One shared schema, persisted to an App Group container so the WidgetKit extension and
/// the watchOS app read the same store.
enum ChronosStore {
    static let appGroup = "group.com.chronosflow.shared"

    static let schema = Schema([
        TimeBlock.self,
        TaskItem.self,
        Habit.self,
        Goal.self,
        MedicationPlan.self,
        FocusSession.self,
        JournalEntry.self,
        SleepTrack.self,
        MoodEnergyCheckIn.self,
        Routine.self,
    ])

    /// Process-wide shared container so App Intents / Shortcuts read the same store as the app.
    static let shared: ModelContainer = makeContainer()

    /// Enable iCloud/CloudKit mirroring (the iOS analogue of Android backup/device-transfer).
    /// User-toggleable from Settings → Data; persisted in the App-Group defaults and read once at
    /// container-build time, so a change takes effect on the next launch. Off by default so the app
    /// builds and runs with no signing team; requires the iCloud capability + `iCloud.com.chronosflow`
    /// container to be provisioned (see README). If they aren't, container creation falls back to the
    /// plain local store so user data is never lost.
    static let cloudSyncKey = "sync.cloudKit"
    static var cloudSyncEnabled: Bool {
        UserDefaults(suiteName: appGroup)?.bool(forKey: cloudSyncKey) ?? false
    }

    /// Shared, App-Group-backed container for the main app + extensions.
    static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        let config: ModelConfiguration
        if inMemory {
            config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        } else if cloudSyncEnabled {
            config = ModelConfiguration(
                schema: schema,
                groupContainer: .identifier(appGroup),
                cloudKitDatabase: .automatic)
        } else {
            config = ModelConfiguration(schema: schema, groupContainer: .identifier(appGroup))
        }
        do {
            return try ModelContainer(for: schema, configurations: [config])
        } catch {
            // CloudKit unavailable / entitlement missing: retry with a plain local group store
            // before giving up to in-memory, so enabling sync without provisioning never silently
            // drops the user's data.
            if !inMemory && cloudSyncEnabled {
                let local = ModelConfiguration(schema: schema, groupContainer: .identifier(appGroup))
                if let container = try? ModelContainer(for: schema, configurations: [local]) {
                    return container
                }
            }
            // Last resort: the App Group container is unavailable (e.g. a simulator without the
            // entitlement provisioned) — use a local store.
            let fallback = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
            return try! ModelContainer(for: schema, configurations: [fallback])
        }
    }

    /// A container pre-seeded with a believable day, for previews and first run.
    @MainActor
    static func previewContainer() -> ModelContainer {
        let container = makeContainer(inMemory: true)
        SeedData.populate(container.mainContext)
        return container
    }
}
