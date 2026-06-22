import Foundation
import ChronosCore

// The iOS wire format the peer (Meridian / DevTime, `com.Meridian.VBCR`) writes into the shared
// App-Group container for ChronosFlow to import. This is the iOS substitute for Android's
// ContentProvider cursor: instead of querying `content://com.Meridian.VBCR.share/tasks`, the peer
// serialises a `InteropPeerPayload` JSON file into the shared group container and ChronosFlow reads
// it (see `InteropSync.readPeerTasks()`).
//
// The task row mirrors the normalized projection in `InteropContract.taskColumns`
// (external_id, title, …, due_at, is_completed, priority) — only the columns ChronosFlow actually
// imports are carried, since this is an import-only MVP. Instants are epoch-milliseconds (the
// decoder uses `.millisecondsSince1970`) so the format matches Android's `Long` due-at exactly and
// stays time-zone-agnostic across the wire.

/// Top-level payload the peer writes. `peer` self-identifies the author so ChronosFlow can reject a
/// payload that doesn't name the expected companion app (the App-Group entitlement is the real trust
/// boundary on iOS; this is a cheap sanity check, not a security control).
struct InteropPeerPayload: Codable, Equatable, Sendable {
    /// The author package id — must equal `InteropContract.peer` for the payload to be trusted.
    var peer: String
    /// Wall-clock instant (epoch millis) the peer wrote this payload, for diagnostics / staleness UI.
    var generatedAt: Date?
    /// Shared task rows (import-only MVP).
    var tasks: [InteropPeerTask]

    init(peer: String = InteropContract.peer, generatedAt: Date? = nil, tasks: [InteropPeerTask]) {
        self.peer = peer
        self.generatedAt = generatedAt
        self.tasks = tasks
    }
}

/// One shared task row, named to match `InteropContract.taskColumns` so the cross-platform contract
/// stays byte-aligned. Maps to the ChronosCore `RemoteInteropTask` the deterministic dedup pass consumes.
struct InteropPeerTask: Codable, Equatable, Sendable {
    var externalID: String
    var title: String
    /// Due instant (epoch millis), or nil if undated. Decoded via `.millisecondsSince1970`.
    var dueAt: Date?
    var isCompleted: Bool
    var priority: Int?

    /// Use the contract's column names on the wire (snake_case) so the JSON matches Android's projection.
    private enum CodingKeys: String, CodingKey {
        case externalID = "external_id"
        case title
        case dueAt = "due_at"
        case isCompleted = "is_completed"
        case priority
    }

    init(externalID: String, title: String, dueAt: Date? = nil,
         isCompleted: Bool = false, priority: Int? = nil) {
        self.externalID = externalID
        self.title = title
        self.dueAt = dueAt
        self.isCompleted = isCompleted
        self.priority = priority
    }

    /// Tolerant decode: a missing `is_completed`/`priority` defaults the same way Android's cursor
    /// helpers do (`longOrNull ?: 0` → false / nil), so a sparse peer row still imports cleanly.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        externalID = try c.decode(String.self, forKey: .externalID)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        dueAt = try c.decodeIfPresent(Date.self, forKey: .dueAt)
        isCompleted = try c.decodeIfPresent(Bool.self, forKey: .isCompleted) ?? false
        priority = try c.decodeIfPresent(Int.self, forKey: .priority)
    }

    /// Bridge to the ChronosCore dedup input type. The due instant is converted to epoch-millis so the
    /// pure `InteropDedup` pass (and its dedup key / reminder-lead math) stays integer-exact with Android.
    var asRemoteInteropTask: RemoteInteropTask {
        RemoteInteropTask(
            externalID: externalID,
            title: title,
            dueAtMillis: dueAt.map { Int64(($0.timeIntervalSince1970 * 1000).rounded()) },
            isCompleted: isCompleted,
            priority: priority)
    }
}
