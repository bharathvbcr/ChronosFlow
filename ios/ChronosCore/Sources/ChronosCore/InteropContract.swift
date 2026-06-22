import Foundation

// Cross-app sharing contract between ChronosFlow and DevTime (Meridian) — Foundation-only port of
// app/interop/InteropContract.kt and the deterministic identity rules from InteropSyncManager.kt.
//
// This is the *byte-compatible* constant + payload surface: peer package id, the normalized task
// column projection, the deterministic external id ("interop:<peer>:<externalId>"), and the import
// caps/lead used by the dedup pass. iOS cross-app discovery uses App Groups rather than Android's
// signed content providers, so the signing-cert pinning machinery is intentionally NOT ported here
// (it has no iOS equivalent); only the data contract that both apps must agree on byte-for-byte.

public enum InteropContract {
    /// DevTime / Meridian package — must match the Android constant exactly.
    public static let devtimePackage = "com.Meridian.VBCR"
    /// This app's package.
    public static let chronosflowPackage = "com.ChronosFlow.VBCR"

    /// The peer we import from (DevTime / Meridian).
    public static let peer = devtimePackage

    // Share paths (one per shareable data type) — mirror InteropContract.PATH_*.
    public static let pathTasks = "tasks"
    public static let pathEvents = "events"
    public static let pathZones = "zones"
    public static let pathPeople = "people"
    public static let pathHabits = "habits"
    public static let pathMedications = "medications"
    public static let pathGoals = "goals"

    // Normalized task projection. Rows MUST be produced/consumed in exactly this order.
    public static let taskColumns: [String] = [
        "external_id", "title", "notes", "due_at", "is_completed", "priority", "timezone", "updated_at",
    ]
    public static let eventColumns: [String] = [
        "external_id", "title", "description", "start_at", "end_at", "timezone", "is_all_day", "location", "updated_at",
    ]
    public static let zoneColumns: [String] = ["external_id", "zone_id", "display_name", "is_home"]
    public static let peopleColumns: [String] = [
        "external_id", "name", "zone_id", "location_name", "work_start_hour", "work_end_hour",
    ]
    public static let habitColumns: [String] = ["external_id", "title", "cadence", "is_active", "streak_count"]
    public static let medicationColumns: [String] = ["external_id", "name", "dosage", "unit", "is_active"]
    public static let goalColumns: [String] = ["external_id", "title", "description", "category", "is_completed"]

    // Import limits / scheduling — mirror InteropSyncManager companion constants.
    /// Cap on rows imported per sync. Applied BEFORE dedup (matches Android: `take` then `distinctBy`).
    public static let maxInteropTasks = 500
    /// Max persisted title length when mirroring an imported task.
    public static let maxTitleLength = 500
    /// Max persisted external-id length when mirroring an imported task.
    public static let maxExternalIdLength = 128
    /// Lead time before a task's due instant at which ChronosFlow fires the reminder (10 minutes).
    public static let reminderLeadMillis: Int64 = 10 * 60 * 1000
    /// Fallback title used when an imported task arrives blank.
    public static let untitledTaskTitle = "(untitled)"

    /// Deterministic ChronosFlow task id for an imported peer row, so repeated syncs replace in place
    /// rather than duplicating. Mirrors `"interop:$origin:${t.externalId}"` exactly.
    public static func importedTaskID(externalID: String, peer: String = InteropContract.peer) -> String {
        "interop:\(peer):\(externalID)"
    }
}
