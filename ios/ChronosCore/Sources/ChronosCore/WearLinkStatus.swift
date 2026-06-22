import Foundation

// WearLinkStatus — portable, Foundation-only model + helpers for the phone↔watch link surfaced on
// the Privacy & Sync settings, plus the sensitive-title redaction helper that gates what watch
// glance text is allowed to carry.
//
// Ports Android's `core/domain/.../wear/WearLinkStatus.kt` (the model), the
// `wearLinkConnectionSummary` label in `feature/daydial/.../PrivacySyncSections.kt`, the
// `formatLastSyncedLabel` relative label in `core/ui/.../RelativeTimeFormat.kt`, and the
// `REDACTED_BLOCK_TITLE` redaction rule in `app/.../widget/WearDaySummaryBridge.kt`. All pure and
// deterministic (clock injected) so they unit-test off-device. No SwiftUI / UIKit.

// MARK: - Link status model

/// Snapshot of the phone ↔ watch link, surfaced on the phone's Privacy & Sync settings so the user
/// can confirm a paired watch is actually receiving the day-summary mirror. Mirrors Android's
/// `WearLinkStatus` data class.
///
/// - `watchPaired`: whether any watch is paired with this phone at all.
/// - `watchConnected`: whether a paired watch is currently reachable. A watch can be paired but
///   momentarily disconnected.
/// - `watchAppInstalled`: whether a reachable watch actually has the ChronosFlow watch app
///   installed. A watch can be connected without the app, in which case the mirror has nowhere to
///   land.
/// - `connectedNodeName`: friendly name of the reachable watch, when one is connected.
/// - `lastPublishedAtMillis`: epoch-millis of the last successful day-summary push, or 0 if the
///   phone has never pushed to the watch on this install.
public struct WearLinkStatus: Sendable, Equatable {
    public let watchPaired: Bool
    public let watchConnected: Bool
    public let watchAppInstalled: Bool
    public let connectedNodeName: String?
    public let lastPublishedAtMillis: Int64

    public init(
        watchPaired: Bool,
        watchConnected: Bool,
        watchAppInstalled: Bool,
        connectedNodeName: String?,
        lastPublishedAtMillis: Int64
    ) {
        self.watchPaired = watchPaired
        self.watchConnected = watchConnected
        self.watchAppInstalled = watchAppInstalled
        self.connectedNodeName = connectedNodeName
        self.lastPublishedAtMillis = lastPublishedAtMillis
    }

    /// The "no watch support / nothing known yet" status; used when the query can't run. Mirrors
    /// Android's `WearLinkStatus.UNKNOWN`.
    public static let unknown = WearLinkStatus(
        watchPaired: false,
        watchConnected: false,
        watchAppInstalled: false,
        connectedNodeName: nil,
        lastPublishedAtMillis: 0
    )
}

// MARK: - Settings labels

/// The connection summary line for the Privacy & Sync "Watch" card. A `nil` status means the query
/// hasn't returned yet. Mirrors Android's `wearLinkConnectionSummary` (the feature-module variant,
/// which says "app not installed" without the redundant "Watch connected — " prefix). The friendly
/// node name is appended ("Connected: Pixel Watch") only when the app is actually installed.
public func wearLinkConnectionSummary(_ status: WearLinkStatus?) -> String {
    guard let status else { return "Checking watch…" }
    if status.watchAppInstalled {
        if let name = status.connectedNodeName { return "Connected: \(name)" }
        return "Connected"
    }
    if status.watchConnected { return "Watch connected — app not installed" }
    if status.watchPaired { return "Watch paired but not reachable" }
    return "No watch connected"
}

/// Compact "Synced X ago" hint for the last successful publish to the watch, used under the watch
/// card. Pure and clock-injected. A `nil` timestamp (never published) reads "Not synced yet"; clock
/// skew (future timestamp) clamps to "Synced just now". Mirrors Android's `formatLastSyncedLabel`.
///
/// Note: this differs from `WearFormat.syncAgeLabel`, which is the watch-side *staleness warning*
/// (60-minute gated, returns `nil` when fresh). This is the always-shown phone-side "last synced"
/// caption.
public func formatLastSyncedLabel(lastSyncAtMillis: Int64?, nowMillis: Int64) -> String {
    guard let lastSyncAtMillis else { return "Not synced yet" }
    let elapsedMillis = nowMillis - lastSyncAtMillis
    if elapsedMillis < 0 { return "Synced just now" } // guard against clock skew

    let minutes = elapsedMillis / 60_000
    if minutes < 1 { return "Synced just now" }
    if minutes < 60 { return "Synced \(minutes) min ago" }

    let hours = elapsedMillis / (60 * 60_000)
    if hours < 24 { return "Synced \(hours) hr ago" }

    let days = elapsedMillis / (24 * 60 * 60_000)
    return days == 1 ? "Synced yesterday" : "Synced \(days) days ago"
}

// MARK: - Redaction

/// The placeholder shown in place of a real block title when sensitive titles are hidden. Mirrors
/// Android's `REDACTED_BLOCK_TITLE`.
public let redactedBlockTitle = "Scheduled block"

/// A block title cleared for the watch glance: the real `title` when titles are visible, or the
/// generic `redactedBlockTitle` placeholder when `sensitiveTitlesRedacted` is on. Times, counts and
/// progress still flow to the watch — only the human-readable label is masked. Mirrors the
/// `KEY_NOW_TITLE` / `KEY_NEXT_TITLE` rule in `WearDaySummaryBridge`.
public func wearRedactedTitle(_ title: String, sensitiveTitlesRedacted: Bool) -> String {
    sensitiveTitlesRedacted ? redactedBlockTitle : title
}

/// A list of titles (task / habit / med entries) cleared for the watch glance: returned unchanged
/// when titles are visible, or dropped entirely (empty) when `sensitiveTitlesRedacted` is on — so
/// counts/controls still work but no labels leak. Mirrors the `KEY_TASK_ENTRIES` /
/// `KEY_HABIT_ENTRIES` / `KEY_MED_ENTRIES` redaction rule in `WearDaySummaryBridge`.
public func wearRedactedEntries(_ titles: [String], sensitiveTitlesRedacted: Bool) -> [String] {
    sensitiveTitlesRedacted ? [] : titles
}
