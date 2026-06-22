import Foundation

// WearFormat — portable, Foundation-only formatting helpers shared across the watch screens.
//
// Pure 1:1 port of Android's `wear/.../presentation/WearFormat.kt` so the phone (which builds the
// watch mirror) and the watchOS app render identical glance text from identical math. Every helper
// is deterministic — no wall-clock reads inside; `nowMinute` / `nowMillis` are injected — so the
// logic unit-tests off-device on the Windows/Linux CI that builds ChronosCore. No SwiftUI / UIKit.

/// Formatting helpers shared across the watch app screens. A namespace (caseless enum) so there is
/// no instance and no stored state — matches Android's `object WearFormat`.
public enum WearFormat {

    // MARK: - Clock labels

    /// Minute-of-day (may exceed 1439 across midnight, or be negative) → "HH:mm", wrapped into a
    /// single 24h day. Mirrors `WearFormat.minuteOfDay`.
    public static func minuteOfDay(_ minuteOfDay: Int) -> String {
        let safe = ((minuteOfDay % 1440) + 1440) % 1440
        return String(format: "%02d:%02d", safe / 60, safe % 60)
    }

    /// A block's full time window, e.g. "09:00–09:30" (24h, watch-local) — shows when the block runs
    /// at a glance. Uses an en-dash, matching Android. Mirrors `WearFormat.windowLabel`.
    public static func windowLabel(startMinute: Int, endMinute: Int) -> String {
        "\(minuteOfDay(startMinute))–\(minuteOfDay(endMinute))"
    }

    /// Seconds → "m:ss" (or "h:mm:ss" past an hour). Negative input clamps to 0. Mirrors
    /// `WearFormat.mmss`.
    public static func mmss(_ totalSeconds: Int) -> String {
        let safe = max(0, totalSeconds)
        let h = safe / 3600
        let m = (safe % 3600) / 60
        let s = safe % 60
        return h > 0
            ? String(format: "%d:%02d:%02d", h, m, s)
            : String(format: "%d:%02d", m, s)
    }

    // MARK: - Span words

    /// A whole-minute span as compact words: 45 → "45m", 90 → "1h 30m", 120 → "2h". Negative input
    /// clamps to 0 ("0m"). Mirrors `WearFormat.minutesWords`.
    public static func minutesWords(_ totalMinutes: Int) -> String {
        let safe = max(0, totalMinutes)
        let h = safe / 60
        let m = safe % 60
        switch (h, m) {
        case let (h, m) where h > 0 && m > 0: return "\(h)h \(m)m"
        case let (h, _) where h > 0:          return "\(h)h"
        default:                              return "\(m)m"
        }
    }

    /// How much longer the block ending at `endMinute` runs, relative to `nowMinute` — e.g.
    /// "23m left" / "1h 5m left". Reads "ending" once the end is reached so a glance never shows a
    /// non-positive countdown. Mirrors `WearFormat.remainingLabel`.
    public static func remainingLabel(endMinute: Int, nowMinute: Int) -> String {
        let remaining = endMinute - nowMinute
        return remaining <= 0 ? "ending" : "\(minutesWords(remaining)) left"
    }

    /// How soon the block starting at `startMinute` begins, relative to `nowMinute` — e.g.
    /// "in 15m" / "in 1h 5m", or "now" once it is due. Mirrors `WearFormat.startsInLabel`.
    public static func startsInLabel(startMinute: Int, nowMinute: Int) -> String {
        let delta = startMinute - nowMinute
        return delta <= 0 ? "now" : "in \(minutesWords(delta))"
    }

    // MARK: - Sync freshness

    /// A schedule synced more than this long ago is treated as possibly out of date (60 minutes).
    public static let staleThresholdMillis: Int64 = 60 * 60_000

    /// A "Synced Nm/Nh ago" hint to show when the mirrored day is stale enough that its
    /// time-relative claims may no longer hold, or `nil` when the data is fresh (`receivedAtMillis`
    /// within `staleThresholdMillis`) or was never synced on this device (`receivedAtMillis <= 0`,
    /// where the empty state already speaks for itself). `nowMillis` is the current wall clock.
    /// Mirrors `WearFormat.syncAgeLabel`.
    public static func syncAgeLabel(receivedAtMillis: Int64, nowMillis: Int64) -> String? {
        if receivedAtMillis <= 0 { return nil }
        let ageMillis = nowMillis - receivedAtMillis
        if ageMillis < staleThresholdMillis { return nil }
        let ageMinutes = ageMillis / 60_000
        let span = ageMinutes >= 120 ? "\(ageMinutes / 60)h" : "\(ageMinutes)m"
        return "Synced \(span) ago"
    }
}
