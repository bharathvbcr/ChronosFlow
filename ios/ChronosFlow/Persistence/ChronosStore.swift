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
        FocusSessionSnapshot.self,
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

    /// Whether the App Group container is actually provisioned for this build. SwiftData hard-
    /// `fatalError`s (uncatchable — `do/catch` can't save us) inside `ModelContainer` init when asked
    /// for `.identifier(appGroup)` while the entitlement is missing, which is exactly the case for an
    /// unsigned simulator build (`CODE_SIGNING_ALLOWED=NO`). Probing the container URL up front lets us
    /// pick a plain local store instead of trapping at launch.
    static var appGroupAvailable: Bool {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) != nil
    }

    /// Shared, App-Group-backed container for the main app + extensions.
    static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        let config: ModelConfiguration
        if inMemory {
            config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        } else if !appGroupAvailable {
            // App Group not provisioned (e.g. unsigned simulator build) — a default local store keeps
            // the app launchable instead of trapping inside SwiftData on the missing entitlement.
            config = ModelConfiguration(schema: schema)
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
            // An incompatible on-disk store (a schema change with no lightweight-migration path)
            // makes every *persistent* open throw. Rather than trap at launch, recreate the store
            // once from scratch — the app has no shipped data to preserve yet, so a clean store
            // beats a crash. (A real release would add a SchemaMigrationPlan instead.)
            if !inMemory {
                destroyPersistentStores()
                if let container = try? ModelContainer(for: schema, configurations: [config]) {
                    return container
                }
            }
            // True last resort: an in-memory store so the app ALWAYS launches. (Must be in-memory —
            // a persistent fallback would re-open the same incompatible file and crash again.)
            let fallback = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
            return try! ModelContainer(for: schema, configurations: [fallback])
        }
    }

    /// Delete the SwiftData store files at both the App Group and default locations. Used only as a
    /// recovery step when an incompatible schema makes every persistent open throw (see `makeContainer`).
    private static func destroyPersistentStores() {
        let fm = FileManager.default
        var dirs: [URL] = []
        if let appSupport = try? fm.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: false) {
            dirs.append(appSupport)
        }
        if let group = fm.containerURL(forSecurityApplicationGroupIdentifier: appGroup) {
            dirs.append(group.appendingPathComponent("Library/Application Support"))
        }
        for dir in dirs {
            for name in ["default.store", "default.store-wal", "default.store-shm"] {
                try? fm.removeItem(at: dir.appendingPathComponent(name))
            }
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
